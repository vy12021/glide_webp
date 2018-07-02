package com.bumptech.glide.webpdecoder;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static com.bumptech.glide.webpdecoder.WebpDecoder.*;

/**
 * A class responsible for creating {@link WebpHeader}s from data
 * representing animated WEBPs.
 * ------------------------------------------------------------------------------
 * RIFF layout is:
 *   Offset  tag
 *   0...3   "RIFF" 4-byte tag
 *   4...7   size of image data (including metadata) starting at offset 8
 *   8...11  "WEBP"   our form-type signature
 * The RIFF container (12 bytes) is followed by appropriate chunks:
 *   12..15  "VP8 ": 4-bytes tags, signaling the use of VP8 video format
 *   16..19  size of the raw VP8 image data, starting at offset 20
 *   20....  the VP8 bytes
 * Or,
 *   12..15  "VP8L": 4-bytes tags, signaling the use of VP8L lossless format
 *   16..19  size of the raw VP8L image data, starting at offset 20
 *   20....  the VP8L bytes
 * Or,
 *   12..15  "VP8X": 4-bytes tags, describing the extended-VP8 chunk.
 *   16..19  size of the VP8X chunk starting at offset 20.
 *   20..23  VP8X flags bit-map corresponding to the chunk-types present.
 *   24..26  Width of the Canvas Image.
 *   27..29  Height of the Canvas Image.
 * There can be extra chunks after the "VP8X" chunk (ICCP, ANMF, VP8, VP8L,
 * XMP, EXIF  ...)
 * All sizes are in little-endian order.
 * Note: chunk data size must be padded to multiple of 2 when written.
 */
public class WebpHeaderParser {
  private static final String TAG = "WebpHeaderParser";

  // Alpha related constants.
  static final int ALPHA_HEADER_LEN           = 1;
  static final int ALPHA_NO_COMPRESSION       = 0;
  static final int ALPHA_LOSSLESS_COMPRESSION = 1;
  static final int ALPHA_PREPROCESSED_LEVELS  = 1;

  // Mux related constants.
  static final int TAG_SIZE             = 4;     // Size of a chunk tag (e.g. "VP8L").
  static final int CHUNK_SIZE_BYTES     = 4;     // Size needed to store chunk's size.
  static final int CHUNK_HEADER_SIZE    = 8;     // Size of a chunk header.
  static final int RIFF_HEADER_SIZE     = 12;    // Size of the RIFF header ("RIFFnnnnWEBP").
  static final int ANMF_CHUNK_SIZE      = 16;    // Size of an ANMF chunk.
  static final int ANIM_CHUNK_SIZE      = 6;     // Size of an ANIM chunk.
  static final int VP8X_CHUNK_SIZE      = 10;    // Size of a VP8X chunk.

  static final int MAX_CANVAS_SIZE      = (1 << 24);     // 24-bit max for VP8X width/height.
  static final long MAX_IMAGE_AREA      = (1L << 32);    // 32-bit max for width x height.
  static final int MAX_LOOP_COUNT       = (1 << 16);     // maximum value for loop-count
  static final int MAX_DURATION         = (1 << 24);     // maximum duration
  static final int MAX_POSITION_OFFSET  = (1 << 24);     // maximum frame x/y offset

  // Maximum chunk payload is such that adding the header and padding won't
  // overflow a uint32_t.
  static final long MAX_CHUNK_PAYLOAD   = (MAX_IMAGE_AREA - CHUNK_HEADER_SIZE - 1);

  /**
   * has animation, frame count > 1
   * @see ChunkId#ANIM
   */
  static final int ANIMATION_FLAG  = 0x00000002;
  /**
   * has xmp trunk
   * @see ChunkId#XMP
   */
  static final int XMP_FLAG        = 0x00000004;
  /**
   * has exif meta info
   * @see ChunkId#EXIF
   */
  static final int EXIF_FLAG       = 0x00000008;
  /**
   * has alpha channel
   * @see ChunkId#ALPHA
   */
  static final int ALPHA_FLAG      = 0x00000010;
  /**
   * has iccp
   * @see ChunkId#ICCP
   */
  static final int ICCP_FLAG       = 0x00000020;
  /**
   * all flags
   */
  static final int ALL_VALID_FLAGS = 0x0000003e;
  /**
   * VP8X Feature Flags.
   * Android Lint annotation for feature flag that can be used with a WEBP head parser.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {ANIMATION_FLAG, XMP_FLAG, EXIF_FLAG, ALPHA_FLAG, ICCP_FLAG, ALL_VALID_FLAGS})
  @interface WebpFeatureFlag {
  }

  /** The minimum frame delay in hundredths of a second. */
  static final int MIN_FRAME_DELAY = 2;
  /**
   * The default frame delay in hundredths of a second.
   * This is used for WEBPs with frame delays less than the minimum.
   */
  static final int DEFAULT_FRAME_DELAY = 10;

  private static final int MAX_BLOCK_SIZE = 256;
  // Raw data read working array.
  private final byte[] block = new byte[MAX_BLOCK_SIZE];

  private ByteBuffer rawData;
  private WebpHeader header;
  private int blockSize = 0;

  public WebpHeaderParser setData(@NonNull ByteBuffer data) {
    reset();
    rawData = data.asReadOnlyBuffer();
    rawData.position(0);
    rawData.order(ByteOrder.LITTLE_ENDIAN);
    return this;
  }

  public WebpHeaderParser setData(@Nullable byte[] data) {
    if (data != null) {
      setData(ByteBuffer.wrap(data));
    } else {
      rawData = null;
      header.status = WebpDecoder.STATUS_OPEN_ERROR;
    }
    return this;
  }

  public void clear() {
    rawData = null;
    header = null;
  }

  private void reset() {
    rawData = null;
    Arrays.fill(block, (byte) 0);
    header = new WebpHeader();
    blockSize = 0;
  }

  @NonNull
  public WebpHeader parseHeader() {
    if (rawData == null) {
      throw new IllegalStateException("You must call setData() before parseHeader()");
    }
    if (err()) {
      return header;
    }
    readHeader();
    if (!err()) {
      readContents();
    }
    return header;
  }

  /**
   * Determines if the WEBP is animated by trying to read in the first 2 frames
   * This method re-parses the data even if the header has already been read.
   */
  public boolean isAnimated() {
    readHeader();
    if (!err()) {
      readContents(2 /* maxFrames */);
    }
    return header.frameCount > 1;
  }

  /**
   * Main file parser. Reads WEBP content blocks.
   */
  private void readContents() {
    readContents(Integer.MAX_VALUE /* maxFrames */);
  }

  /**
   * Main file parser. Reads WEBP content blocks. Stops after reading maxFrames
   */
  private void readContents(int maxFrames) {
    // Read WEBP file content blocks.
    boolean done = false;
    while (!(done || err() || header.frameCount > maxFrames)) {
      int code = read();
      switch (code) {
        case 0:
          // The Graphic Control Extension is optional, but will always come first if it exists.
          // If one did exist, there will be a non-null current frame which we should use.
          // However if one did not exist, the current frame will be null
          // and we must create it here. See issue #134.
          if (header.currentFrame == null) {
            header.currentFrame = new WebpFrame();
          }
          readBitmap();
          break;
        default:
          header.status = STATUS_PARSE_ERROR;
          done = true;
      }
    }
  }

  /**
   * Reads next frame image.
   */
  private void readBitmap() {
    // (sub)image position & size.
    header.currentFrame.ix = readShort();
    header.currentFrame.iy = readShort();
    header.currentFrame.iw = readShort();
    header.currentFrame.ih = readShort();

    // Save this as the decoding position pointer.
    header.currentFrame.bufferFrameStart = rawData.position();

    // False decode pixel data to advance buffer.
    skipImageData();

    if (err()) {
      return;
    }

    header.frameCount++;
    // Add image to frame.
    header.frames.add(header.currentFrame);
  }

  /**
   * Reads WEBP file header information.
   */
  private void readHeader() {
    reset();
    parseRIFFHeader();
  }

  /**
   * 解析riff头部区
   */
  private void parseRIFFHeader() {
    int minSize = RIFF_HEADER_SIZE + CHUNK_HEADER_SIZE;
    int riffSize = 0;
    if (rawData.remaining() < minSize) {
      loge("Truncated data detected when parsing RIFF header.");
      this.header.status = STATUS_TRUNCATED_DATA;
      return;
    }
    if (!getEquals("RIFF") || !getEquals(CHUNK_HEADER_SIZE, "WEBP")) {
      loge("Corrupted RIFF header.");
      this.header.status = STATUS_PARSE_ERROR;
    }
    // riff byte count after header
    riffSize = getIntFrom(TAG_SIZE);
    if (riffSize < CHUNK_HEADER_SIZE) {
      loge("RIFF size is too small.");
      this.header.status = STATUS_PARSE_ERROR;
    }
    if ((long) riffSize > MAX_CHUNK_PAYLOAD) {
      loge("RIFF size is over limit.");
      this.header.status = STATUS_PARSE_ERROR;
    }
    // should be equals file size
    riffSize += CHUNK_HEADER_SIZE;
    if (riffSize < rawData.limit()) {
      logw("RIFF size is smaller than the file size.");
    } else if (riffSize > rawData.limit()) {
      loge("Truncated data detected when parsing RIFF payload.");
      this.header.status = STATUS_TRUNCATED_DATA;
    }
    this.rawData.position(rawData.position() + RIFF_HEADER_SIZE);
  }

  /**
   * parse several chunks
   */
  private ChunkData parseChunk() {
    if (rawData.remaining() < CHUNK_HEADER_SIZE) {
      loge("Truncated data detected when parsing chunk header.");
      this.header.status = STATUS_TRUNCATED_DATA;
      return null;
    } else {
      ChunkData chunkData = new ChunkData();
      int chunkStartOffset = rawData.position();
      //
      String tag = readString(4);
      int payloadSize = readInt();
      // even format, trim bytes
      int payloadSizePadded = payloadSize + (payloadSize & 1);
      int chunkSize = CHUNK_HEADER_SIZE + payloadSizePadded;
      if ((long) payloadSize > MAX_CHUNK_PAYLOAD) {
        loge("Size of chunk payload is over limit.");
        this.header.status = STATUS_INVALID_PARAM;
        return chunkData;
      }
      if (payloadSizePadded > rawData.remaining()) {
        loge("Truncated data detected when parsing chunk payload.");
        this.header.status = STATUS_TRUNCATED_DATA;
        return chunkData;
      }
      chunkData.id = ChunkId.getByName(tag);
      chunkData.start = chunkStartOffset;
      chunkData.size = chunkSize;
      chunkData.payloadOffset = this.rawData.position() - chunkStartOffset;
      chunkData.rawBuffer = this.rawData;
      if (chunkData.id == ChunkId.ANMF) {
        // formatted
        if (payloadSize != payloadSizePadded) {
          loge("ANMF chunk size should always be even.");
          this.header.status = STATUS_PARSE_ERROR;
          return chunkData;
        }
        // There are sub-chunks to be parsed in an ANMF chunk.
        skip(ANIM_CHUNK_SIZE);
      } else {
        skip(payloadSizePadded);
      }
      return chunkData;
    }
  }

  private void processVP8XChunk(ChunkData chunkData) {
    // reset buffer to start position
    chunkData.reset();
    if (this.header.chunkCounts[ChunkId.VP8.ordinal()] ||
            this.header.chunkCounts[ChunkId.VP8L.ordinal()] ||
            this.header.chunkCounts[ChunkId.VP8X.ordinal()]) {
      loge("Already seen a VP8/VP8L/VP8X chunk when parsing VP8X chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (chunkData.size != VP8X_CHUNK_SIZE + CHUNK_HEADER_SIZE) {
      loge("Corrupted VP8X chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    // mark parsed
    this.header.chunkCounts[ChunkId.VP8X.ordinal()] = true;
    this.header.featureFlags = readIntFrom(chunkData.start + chunkData.payloadOffset);
    this.header.canvasWidth = 1 + readInt(3);
    this.header.canvasHeight = 1 + readInt(3);
    if (this.header.canvasWidth > MAX_CANVAS_SIZE) {
      logw("");
    }
  }

  /**
   * Skips LZW image data for a single frame to advance buffer.
   */
  private void skipImageData() {
    // lzwMinCodeSize
    read();
    // data sub-blocks
    skip();
  }

  /**
   * Skips variable length blocks up to and including next zero length block.
   */
  private void skip() {
    int blockSize;
    do {
      blockSize = read();
      int newPosition = Math.min(rawData.position() + blockSize, rawData.limit());
      rawData.position(newPosition);
    } while (blockSize > 0);
  }

  /**
   * Reads next variable length block from input.
   */
  private void readBlock() {
    blockSize = read();
    int n = 0;
    if (blockSize > 0) {
      int count = 0;
      try {
        while (n < blockSize) {
          count = blockSize - n;
          rawData.get(block, n, count);
          n += count;
        }
      } catch (Exception e) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
          Log.d(TAG,
              "Error Reading Block n: " + n + " count: " + count + " blockSize: " + blockSize, e);
        }
        header.status = STATUS_BITSTREAM_ERROR;
      }
    }
  }

  /**
   * Reads a single byte from the input stream.
   */
  private int read() {
    int currByte = 0;
    try {
      currByte = rawData.get();
    } catch (Exception e) {
      header.status = STATUS_BITSTREAM_ERROR;
    }
    return currByte;
  }

  /**
   * Reads next 16-bit value, LSB first.
   */
  private int readShort() {
    // Read 16-bit value.
    return rawData.getShort();
  }

  private int skip(int offset) {
    this.rawData.position(this.rawData.position() + offset);
    return this.rawData.position();
  }

  private int getInt() {
    return getInt(4);
  }

  private int getInt(int len) {
    rawData.mark();
    int ret = readIntFrom(rawData.position(), len);
    rawData.reset();
    return ret;
  }

  private int getIntFrom(int index) {
    rawData.mark();
    int ret = rawData.getInt(index);
    rawData.reset();
    return ret;
  }

  private int getIntFrom(int index, int len) {
    rawData.mark();
    int ret = readIntFrom(index, len);
    rawData.reset();
    return ret;
  }

  private int readInt() {
    return readInt(4);
  }

  private int readInt(int len) {
    return readIntFrom(rawData.position(), len);
  }

  private int readIntFrom(int index) {
    return rawData.get(index);
  }

  private int readIntFrom(int index, int len) {
    rawData.position(index);
    rawData.get(block, 0, len);
    return getIntWithLen(block, len);
  }

  private String getString(int len) {
    rawData.mark();
    String ret = readString(len);
    rawData.reset();
    return ret;
  }

  private String readString(int len) {
    rawData.get(block, 0, len);
    return new String(block, 0, len);
  }

  private boolean readEquals(String tag) {
    return readEquals(rawData.position(), tag);
  }

  private boolean readEquals(int index, String tag) {
    char[] chars = tag.toCharArray();
    rawData.position(index);
    rawData.get(block, 0, chars.length);
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] != block[i]) return false;
    }
    return true;
  }

  private int getIntWithLen(byte[] bytes, int len) {
    int ret = 0;
    for (; len > 0; --len) {
      ret |= (bytes[len - 1] << (len * 8));
    }
    return ret;
  }

  private boolean getEquals(String tag) {
    return getEquals(rawData.position(), tag);
  }

  private boolean getEquals(int index, String tag) {
    rawData.mark();
    boolean ret = readEquals(index, tag);
    rawData.reset();
    return ret;
  }

  private void loge(String msg) {
    System.out.println("WebpHeaderParser: " + msg);
  }

  private void logw(String msg) {
    System.out.println("WebpHeaderParser: " + msg);
  }

  private boolean err() {
    return header.status != WebpDecoder.STATUS_OK;
  }

}
