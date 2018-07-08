package com.bumptech.glide.webpdecoder;

import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_BITSTREAM_ERROR;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_INVALID_PARAM;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_MISS_DATA;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_OK;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_PARSE_ERROR;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_TRUNCATED_DATA;

/**
 * A class responsible for creating {@link WebpHeader}s from data
 * representing animated WEBPs.
 * ------------------------------------------------------------------------------
 * RIFF layout is:
 * Offset  tag
 * 0...3   "RIFF" 4-byte tag
 * 4...7   size of image data (including metadata) starting at offset 8
 * 8...11  "WEBP"   our form-type signature
 * The RIFF container (12 bytes) is followed by appropriate chunks:
 * 12..15  "VP8 ": 4-bytes tags, signaling the use of VP8 video format
 * 16..19  size of the raw VP8 image data, starting at offset 20
 * 20....  the VP8 bytes
 * Or,
 * 12..15  "VP8L": 4-bytes tags, signaling the use of VP8L lossless format
 * 16..19  size of the raw VP8L image data, starting at offset 20
 * 20....  the VP8L bytes
 * Or,
 * 12..15  "VP8X": 4-bytes tags, describing the extended-VP8 chunk.
 * 16..19  size of the VP8X chunk starting at offset 20.
 * 20..23  VP8X flags bit-map corresponding to the chunk-types present.
 * 24..26  Width of the Canvas Image.
 * 27..29  Height of the Canvas Image.
 * There can be extra chunks after the "VP8X" chunk (ICCP, ANMF, VP8, VP8L,
 * XMP, EXIF  ...)
 * All sizes are in little-endian order.
 * Note: chunk data size must be padded to multiple of 2 when written.
 */
public class WebpHeaderParser {
  private static final String TAG = "WebpHeaderParser";

  // VP8 related constants.
  static final int VP8_SIGNATURE              = 0x9d012a;    // Signature in VP8 data.
  static final int VP8_MAX_PARTITION0_SIZE    = (1 << 19);   // max size of mode partition
  static final int VP8_MAX_PARTITION_SIZE     = (1 << 24);   // max size for token partition
  static final int VP8_FRAME_HEADER_SIZE      = 10;  // Size of the frame header within VP8 data.

  // VP8L related constants.
  // maximum number of bits (inclusive) the bit-reader can handle:
  static final int VP8L_MAX_NUM_BIT_READ      = 24;
  // Number of bits prefetched (= bit-size of vp8l_val_t).
  static final int VP8L_LBITS                 = 64;
  // Minimum number of bytes ready after VP8LFillBitWindow.
  static final int VP8L_WBITS                 = 32;
  static final int VP8L_SIGNATURE_SIZE        = 1;      // VP8L signature size.
  static final int VP8L_MAGIC_BYTE            = 0x2f;   // VP8L signature byte.
  static final int VP8L_IMAGE_SIZE_BITS       = 14;     // Number of bits used to store
  // width and height.
  static final int VP8L_VERSION_BITS          = 3;      // 3 bits reserved for version.
  static final int VP8L_VERSION               = 0;      // version 0
  static final int VP8L_FRAME_HEADER_SIZE     = 5;      // Size of the VP8L frame header.

  // Alpha related constants.
  static final int ALPHA_HEADER_LEN           = 1;
  static final int ALPHA_PREPROCESSED_LEVELS  = 1;

  // Mux related constants.
  static final int TAG_SIZE                   = 4;     // Size of a chunk tag (e.g. "VP8L").
  static final int CHUNK_SIZE_BYTES           = 4;     // Size needed to store chunk's size.
  static final int CHUNK_HEADER_SIZE          = 8;     // Size of a chunk header.
  static final int RIFF_HEADER_SIZE           = 12;    // Size of the RIFF header ("RIFFnnnnWEBP").
  static final int ANMF_CHUNK_SIZE            = 16;    // Size of an ANMF chunk.
  static final int ANIM_CHUNK_SIZE            = 6;     // Size of an ANIM chunk.
  static final int VP8X_CHUNK_SIZE            = 10;    // Size of a VP8X chunk.

  static final int MAX_CANVAS_SIZE            = (1 << 24);     // 24-bit max for VP8X width/height.
  static final long MAX_IMAGE_AREA            = (1L << 32);    // 32-bit max for width x height.
  static final int MAX_LOOP_COUNT             = (1 << 16);     // maximum value for loop-count
  static final int MAX_DURATION               = (1 << 24);     // maximum duration
  static final int MAX_POSITION_OFFSET        = (1 << 24);     // maximum frame x/y offset

  // Maximum chunk payload is such that adding the header and padding won't
  // overflow a uint32_t.
  static final long MAX_CHUNK_PAYLOAD         = (MAX_IMAGE_AREA - CHUNK_HEADER_SIZE - 1);

  /**
   * has animation, frame count > 1
   *
   * @see ChunkId#ANIM
   */
  static final int ANIMATION_FLAG     = 0x00000002;
  /**
   * has xmp trunk
   *
   * @see ChunkId#XMP
   */
  static final int XMP_FLAG           = 0x00000004;
  /**
   * has exif meta info
   *
   * @see ChunkId#EXIF
   */
  static final int EXIF_FLAG          = 0x00000008;
  /**
   * has alpha channel
   *
   * @see ChunkId#ALPHA
   */
  static final int ALPHA_FLAG         = 0x00000010;
  /**
   * has iccp
   *
   * @see ChunkId#ICCP
   */
  static final int ICCP_FLAG          = 0x00000020;
  /**
   * all flags
   */
  static final int ALL_VALID_FLAGS    = 0x0000003e;

  /**
   * VP8X Feature Flags.
   * Android Lint annotation for feature flag that can be used with a WEBP head parser.
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {ANIMATION_FLAG, XMP_FLAG, EXIF_FLAG, ALPHA_FLAG, ICCP_FLAG, ALL_VALID_FLAGS})
  @interface WebpFeatureFlag {
  }

  /**
   * The minimum frame delay in ms.
   */
  static final int MIN_FRAME_DELAY = 20;
  /**
   * The default frame delay in ms.
   * This is used for WEBPs with frame delays less than the minimum.
   */
  static final int DEFAULT_FRAME_DELAY = 100;

  private ByteBufferReader bufferReader;
  private ByteBuffer rawData;
  private WebpHeader header;

  public WebpHeaderParser setData(@NonNull ByteBuffer data) {
    reset();
    if (!data.isDirect()) {
      throw new IllegalArgumentException("ByteBuffer must be direct allocated");
    }
    rawData = data.asReadOnlyBuffer();
    rawData.position(0);
    rawData.order(ByteOrder.LITTLE_ENDIAN);
    bufferReader = new ByteBufferReader(rawData);
    return this;
  }

  public WebpHeaderParser setData(@Nullable byte[] data) {
    if (data != null) {
      ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
      buffer.put(data);
      setData(buffer);
    } else {
      rawData = null;
      header.status = WebpDecoder.STATUS_OPEN_ERROR;
    }
    return this;
  }

  ByteBuffer getRawData() {
    return rawData;
  }

  public void clear() {
    rawData.clear();
    rawData = null;
    header = null;
  }

  private void reset() {
    rawData = null;
    header = new WebpHeader();
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
    return header;
  }

  /**
   * Determines if the WEBP is animated by trying to read in the first 2 frames
   * This method re-parses the data even if the header has already been read.
   */
  public boolean isAnimated() {
    return header.frameCount > 1;
  }

  /**
   * Reads WEBP file header information.
   */
  private void readHeader() {
    parseRIFFHeader();
    while (STATUS_OK == this.header.status && this.rawData.remaining() > 0) {
      ChunkData chunkData = parseChunk();
      int mark = chunkData.rawBuffer.position();
      if (STATUS_OK == this.header.status) {
        processChunk(chunkData);
      }
      rawData.position(mark);
    }
    validate();
    loge("webp header info: " + this.header.toString());
  }

  private void validate() {
    if (this.header.frameCount < 1) {
      loge("No image/frame detected.");
      this.header.status = STATUS_MISS_DATA;
      return;
    }
    if (this.header.chunksMark[ChunkId.VP8X.ordinal()]) {
      boolean iccp = (this.header.featureFlags & ICCP_FLAG) != 0;
      boolean exif = (this.header.featureFlags & EXIF_FLAG) != 0;
      boolean xmp = (this.header.featureFlags & XMP_FLAG) != 0;
      boolean animation = (this.header.featureFlags & ANIMATION_FLAG) != 0;
      boolean alpha = (this.header.featureFlags & ALPHA_FLAG) != 0;
      if (!alpha && this.header.hasAlpha) {
        loge("Unexpected alpha data detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (alpha && !this.header.hasAlpha) {
        logw("Alpha flag is set with no alpha data present.");
      }
      if (exif && !this.header.chunksMark[ChunkId.EXIF.ordinal()]) {
        loge("Missing EXIF chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (xmp && !this.header.chunksMark[ChunkId.XMP.ordinal()]) {
        loge("Missing XMP chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!iccp && this.header.chunksMark[ChunkId.ICCP.ordinal()]) {
        loge("Unexpected ICCP chunk detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!exif && this.header.chunksMark[ChunkId.EXIF.ordinal()]) {
        loge("Unexpected EXIF chunk detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!xmp && this.header.chunksMark[ChunkId.XMP.ordinal()]) {
        loge("Unexpected XMP chunk detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      // Incomplete animation frame
      if (this.header.currentFrame.isProcessingAnimFrame) {
        this.header.status = STATUS_MISS_DATA;
        return;
      }
      if (!animation && this.header.frameCount > 1) {
        loge("More than 1 frame detected in non-animation file.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (animation && (!this.header.chunksMark[ChunkId.ANIM.ordinal()] ||
              !this.header.chunksMark[ChunkId.ANMF.ordinal()])) {
        loge("No ANIM/ANMF chunk detected in animation file.");
        this.header.status = STATUS_PARSE_ERROR;
      }
    }
  }

  /**
   * parse riff container tag
   */
  private void parseRIFFHeader() {
    int minSize = RIFF_HEADER_SIZE + CHUNK_HEADER_SIZE;
    int riffSize;
    if (rawData.remaining() < minSize) {
      loge("Truncated data detected when parsing RIFF header.");
      this.header.status = STATUS_TRUNCATED_DATA;
      return;
    }
    if (!bufferReader.getEquals("RIFF") ||
            !bufferReader.getEquals(CHUNK_HEADER_SIZE, "WEBP")) {
      loge("Corrupted RIFF header.");
      this.header.status = STATUS_PARSE_ERROR;
    }
    // riff byte count after header
    riffSize = bufferReader.getIntFrom(TAG_SIZE);
    if (riffSize < CHUNK_HEADER_SIZE) {
      loge("RIFF size is too small.");
      this.header.status = STATUS_PARSE_ERROR;
    }
    if ((long) riffSize > MAX_CHUNK_PAYLOAD) {
      loge("RIFF size is over limit.");
      this.header.status = STATUS_PARSE_ERROR;
    }
    // should be equals file size
    this.header.riffSize = (riffSize += CHUNK_HEADER_SIZE);
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
      // chunk id
      String chunkTag = bufferReader.readString(4);
      int payloadSize = bufferReader.readInt();
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
      chunkData.id = ChunkId.getByName(chunkTag);
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
        bufferReader.skip(ANMF_CHUNK_SIZE);
      } else {
        bufferReader.skip(payloadSizePadded);
      }
      return chunkData;
    }
  }

  private void processChunk(ChunkData chunkData) {
    if (chunkData.id == ChunkId.UNKNOWN) {
      logw("Unknown chunk at offset " + chunkData.start + ", length " + chunkData.size);
    } else {
      loge("Chunk " + chunkData.id + " at offset " + chunkData.start + " length " + chunkData.size);
    }
    switch (chunkData.id) {
      case VP8:
      case VP8L:
        processImageChunk(chunkData);
        break;
      case VP8X:
        processVP8XChunk(chunkData);
        break;
      case ALPHA:
        processALPHChunk(chunkData);
        break;
      case ANIM:
        processANIMChunk(chunkData);
        break;
      case ANMF:
        processANMFChunk(chunkData);
        break;
      case ICCP:
        processICCPChunk(chunkData);
        break;
      case EXIF:
      case XMP:
        this.header.chunksMark[chunkData.id.ordinal()] = true;
        break;
      case UNKNOWN:
      default:
        break;
    }
    if (this.header.currentFrame.isProcessingAnimFrame &&
            chunkData.id != ChunkId.ANMF && chunkData.id != ChunkId.ALPHA) {
      if (this.header.currentFrame.frameSize == chunkData.size) {
        if (!this.header.currentFrame.foundImageSubchunk) {
          loge("No VP8/VP8L chunk detected in an ANMF chunk.");
          this.header.status = STATUS_PARSE_ERROR;
          return;
        }
        this.header.currentFrame.isProcessingAnimFrame = false;
      } else if (this.header.currentFrame.frameSize > chunkData.size) {
        this.header.currentFrame.frameSize = chunkData.size;
        this.header.currentFrame.isProcessingAnimFrame = false;
      } else {
        loge("Truncated data detected when parsing ANMF chunk.");
        this.header.status = STATUS_TRUNCATED_DATA;
      }
    }
  }

  private void processVP8XChunk(ChunkData chunkData) {
    // reset buffer to start position
    chunkData.reset();
    if (this.header.chunksMark[ChunkId.VP8.ordinal()] ||
            this.header.chunksMark[ChunkId.VP8L.ordinal()] ||
            this.header.chunksMark[ChunkId.VP8X.ordinal()]) {
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
    this.header.chunksMark[ChunkId.VP8X.ordinal()] = true;
    this.header.featureFlags = bufferReader.readIntFrom(chunkData.start + chunkData.payloadOffset);
    this.header.canvasWidth = 1 + bufferReader.readInt(3);
    this.header.canvasHeight = 1 + bufferReader.readInt(3);
    this.header.hasAlpha = (this.header.featureFlags & ALPHA_FLAG) != 0;
    this.header.hasAnimation = (this.header.featureFlags & ANIMATION_FLAG) != 0;
    this.header.hasIccp = (this.header.featureFlags & ICCP_FLAG) != 0;
    this.header.hasExif = (this.header.featureFlags & EXIF_FLAG) != 0;
    this.header.hasXmp = (this.header.featureFlags & XMP_FLAG) != 0;
    if (this.header.canvasWidth > MAX_CANVAS_SIZE) {
      logw("Canvas width is out of range in VP8X chunk.");
    }
    if (this.header.canvasHeight > MAX_CANVAS_SIZE) {
      logw("Canvas height is out of range in VP8X chunk.");
    }
    if (this.header.canvasHeight * this.header.canvasWidth > MAX_IMAGE_AREA) {
      logw("Canvas area is out of range in VP8X chunk.");
    }
    loge("processVP8XChunk: \n" + this.header.printVp8XInfo());
  }

  private void processANIMChunk(ChunkData chunkData) {
    // reset buffer to start position
    chunkData.reset();
    if (!this.header.chunksMark[ChunkId.VP8X.ordinal()]) {
      loge("ANIM chunk detected before VP8X chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (chunkData.size != ANIM_CHUNK_SIZE + CHUNK_HEADER_SIZE) {
      loge("Corrupted ANIM chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    chunkData.resetData();
    this.header.bgColor = bufferReader.readInt();
    this.header.loopCount = bufferReader.readInt(2);
    this.header.chunksMark[ChunkId.ANIM.ordinal()] = true;
    if (this.header.loopCount > MAX_LOOP_COUNT) {
      logw("Loop count is out of range in ANIM chunk.");
    }
  }

  private void processANMFChunk(ChunkData chunkData) {
    chunkData.reset();
    this.header.currentFrame = new WebpFrame(-1);
    int offsetX, offsetY, width, height, duration, blend, dispose;
    if (this.header.currentFrame.isProcessingAnimFrame) {
      loge("ANMF chunk detected within another ANMF chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (!this.header.chunksMark[ChunkId.ANIM.ordinal()]) {
      loge("ANMF chunk detected before ANIM chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (chunkData.size <= CHUNK_HEADER_SIZE + ANMF_CHUNK_SIZE) {
      loge("Truncated data detected when parsing ANMF chunk.");
      this.header.status = STATUS_TRUNCATED_DATA;
      return;
    }
    chunkData.resetData();
    offsetX = 2 * bufferReader.readInt(3);
    offsetY = 2 * bufferReader.readInt(3);
    width = 1 + bufferReader.readInt(3);
    height = 1 + bufferReader.readInt(3);
    duration = bufferReader.readInt(3);
    dispose = bufferReader.getInt() & 1;
    blend = (bufferReader.getInt() >> 1) & 1;
    this.header.chunksMark[ChunkId.ANMF.ordinal()] = true;
    if (duration > MAX_DURATION) {
      loge("Invalid duration parameter in ANMF chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (offsetX > MAX_POSITION_OFFSET || offsetY > MAX_POSITION_OFFSET) {
      loge("Invalid offset parameters in ANMF chunk.");
      this.header.status = STATUS_INVALID_PARAM;
      return;
    }
    if (offsetX + width > this.header.canvasWidth ||
            offsetY + height > this.header.canvasHeight) {
      loge("Frame exceeds canvas in ANMF chunk.");
      this.header.status = STATUS_INVALID_PARAM;
      return;
    }
    this.header.newFrame();
    this.header.currentFrame.isProcessingAnimFrame = true;
    this.header.currentFrame.foundAlphaSubchunk = false;
    this.header.currentFrame.foundImageSubchunk = false;
    this.header.currentFrame.duration = duration < MIN_FRAME_DELAY ? DEFAULT_FRAME_DELAY : duration;
    this.header.currentFrame.dispose = dispose;
    this.header.currentFrame.blend = blend;
    this.header.currentFrame.offsetX = offsetX;
    this.header.currentFrame.offsetY = offsetY;
    this.header.currentFrame.width = width;
    this.header.currentFrame.height = height;
    this.header.currentFrame.frameSize = chunkData.size - CHUNK_HEADER_SIZE - ANMF_CHUNK_SIZE;
  }

  private void processImageChunk(ChunkData chunkData) {
    Vp8Info vp8Info = processVp8Bitstream(chunkData);
    if (vp8Info.status != Vp8Info.VP8_STATUS_OK) {
      loge("VP8/VP8L bitstream error.");
      this.header.status = STATUS_BITSTREAM_ERROR;
    }
    if (this.header.currentFrame.isProcessingAnimFrame) {
      this.header.currentFrame.anmfSubchunksMark[chunkData.id == ChunkId.VP8 ? 0 : 1] = true;
      if (chunkData.id == ChunkId.VP8L && this.header.currentFrame.foundAlphaSubchunk) {
        loge("Both VP8L and ALPH sub-chunks are present in an ANMF chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (this.header.currentFrame.width != vp8Info.width ||
              this.header.currentFrame.height != vp8Info.height) {
        loge("Frame size in VP8/VP8L sub-chunk differs from ANMF header.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (this.header.currentFrame.foundImageSubchunk) {
        loge("Consecutive VP8/VP8L sub-chunks in an ANMF chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      this.header.currentFrame.foundImageSubchunk = true;
    } else {
      if (this.header.chunksMark[ChunkId.VP8.ordinal()] ||
              this.header.chunksMark[ChunkId.VP8L.ordinal()]) {
        loge("Multiple VP8/VP8L chunks detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (chunkData.id == ChunkId.VP8L && this.header.chunksMark[ChunkId.ALPHA.ordinal()]) {
        logw("Both VP8L and ALPH chunks are detected.");
      }
      if (this.header.chunksMark[ChunkId.ANIM.ordinal()] ||
              this.header.chunksMark[ChunkId.ANMF.ordinal()]) {
        loge("VP8/VP8L chunk and ANIM/ANMF chunk are both detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (this.header.chunksMark[ChunkId.VP8X.ordinal()]) {
        // FIXME: 2018/7/7 correct valid conditions of size?
        if (false && (this.header.canvasWidth != vp8Info.width ||
                this.header.canvasHeight != vp8Info.height)) {
          loge("Image size in VP8/VP8L chunk differs from VP8X chunk.");
          this.header.status = STATUS_PARSE_ERROR;
        }
      } else {
        this.header.canvasWidth = vp8Info.width;
        this.header.canvasHeight = vp8Info.height;
        if (this.header.canvasWidth < 1 || this.header.canvasHeight < 1 ||
                this.header.canvasWidth > MAX_CANVAS_SIZE ||
                this.header.canvasHeight > MAX_CANVAS_SIZE ||
                this.header.canvasWidth * this.header.canvasHeight > MAX_IMAGE_AREA) {
          logw("Invalid parameters in VP8/VP8L chunk. Out range of image size");
        }
      }
      this.header.chunksMark[chunkData.id.ordinal()] = true;
    }
    this.header.frameCount++;
    this.header.hasAlpha |= vp8Info.hasAlpha;
    // FIXME: 2018/7/2 parse lossy or lossless header
  }

  /**
   * Only parse enough of the data to retrieve the features.
   * line 222 in file webp_dec.c
   * Fetch '*width', '*height', '*has_alpha' and fill out 'headers' based on
   * 'data'. All the output parameters may be NULL. If 'headers' is NULL only the
   * minimal amount will be read to fetch the remaining parameters.
   * If 'headers' is non-NULL this function will attempt to locate both alpha
   * data (with or without a VP8X chunk) and the bitstream chunk (VP8/VP8L).
   * Note: The following chunk sequences (before the raw VP8/VP8L data) are
   * considered valid by this function:
   * RIFF + VP8(L)
   * RIFF + VP8X + (optional chunks) + VP8(L)
   * ALPH + VP8 <-- Not a valid WebP format: only allowed for internal purpose.
   * VP8(L)     <-- Not a valid WebP format: only allowed for internal purpose.
   * <p>
   * Validates the VP8/VP8L Header ("VP8 nnnn" or "VP8L nnnn") and skips over it.
   * Returns VP8_STATUS_BITSTREAM_ERROR for invalid (chunk larger than
   * riff_size) VP8/VP8L header,
   * VP8_STATUS_NOT_ENOUGH_DATA in case of insufficient data, and
   * VP8_STATUS_OK otherwise.
   * If a VP8/VP8L chunk is found, *chunk_size is set to the total number of bytes
   * extracted from the VP8/VP8L chunk header.
   * The flag '*is_lossless' is set to 1 in case of VP8L chunk / raw VP8L data.
   */
  private Vp8Info processVp8Bitstream(ChunkData chunkData) {
    Vp8Info info = new Vp8Info();
    chunkData.reset();
    int width = 0, height = 0;
    boolean hasAlpha = false;

    // ParseVP8Header
    int minSize = TAG_SIZE + CHUNK_HEADER_SIZE;
    if (chunkData.size < CHUNK_HEADER_SIZE) {
      loge("processVp8Bitstream: Not enough data.");
      info.status = Vp8Info.VP8_STATUS_NOT_ENOUGH_DATA;
      return info;
    }
    if (chunkData.id == ChunkId.VP8 || chunkData.id == ChunkId.VP8L) {
      int size = bufferReader.getIntFrom(chunkData.start + TAG_SIZE);
      if (this.header.riffSize > size && size > this.header.riffSize - minSize) {
        loge("processVp8Bitstream: Inconsistent size information.");
        info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
        return info;
      }
      if (size > chunkData.rawBuffer.remaining() - CHUNK_HEADER_SIZE) {
        loge("processVp8Bitstream: Truncated bitstream.");
        info.status = Vp8Info.VP8_STATUS_NOT_ENOUGH_DATA;
        return info;
      }
      info.format = ChunkId.VP8L == chunkData.id ? Vp8Format.Lossless : Vp8Format.Lossy;
      chunkData.resetData();
    } else {
      // Raw VP8/VP8L bitstream (no header).
      byte[] bytes = bufferReader.getBytes(5);
      if (bytes[0] == VP8L_MAGIC_BYTE && (bytes[4] >> 5) == 0) {
        info.format = Vp8Format.Lossless;
      }
    }

    if (chunkData.size > MAX_CHUNK_PAYLOAD) {
      loge("processVp8Bitstream: Chunk size large than max chunk payload");
      info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return info;
    }
    if (info.format != Vp8Format.Lossless) {
      // vp8 chunk
      if (chunkData.size < VP8_FRAME_HEADER_SIZE) {
        loge("processVp8Bitstream: Not enough data");
        info.status = Vp8Info.VP8_STATUS_NOT_ENOUGH_DATA;
        return info;
      }
      // Validates raw VP8 data.
      byte[] bytes = bufferReader.getBytesFrom(chunkData.rawBuffer.position() + 3, 3);
      if (!(bytes[0] == (byte) 0x9d && bytes[1] == (byte) 0x01 && bytes[2] == (byte) 0x2a)) {
        loge("processVp8Bitstream: Bad VP8 signature");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
      bytes = bufferReader.getBytes(10);
      int bits = bufferReader.getIntWithLen(bytes, 3);
      boolean keyFrame = (bits & 1) == 0;
      width = (((bytes[7] & 0xff) << 8) | (bytes[6] & 0xff)) & 0x3fff;
      height = (((bytes[9] & 0xff) << 8) | (bytes[8] & 0xff)) & 0x3fff;
      if (!keyFrame) {
        loge("processVp8Bitstream: Not a keyframe.");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
      if (((bits >> 1) & 7) > 3) {
        loge("processVp8Bitstream: unknown profile.");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
      if (((bits >> 4) & 1) == 0) {
        loge("processVp8Bitstream: first frame is invisible!");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
      if (((bits >> 5) >= chunkData.size)) {
        loge("processVp8Bitstream: inconsistent size information.");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
      if (width * height == 0) {
        loge("processVp8Bitstream: Don't support both width and height to be zero.");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
    } else {
      if (chunkData.size < VP8L_FRAME_HEADER_SIZE) {
        loge("processVp8Bitstream: No enough data");
        info.status = Vp8Info.VP8_STATUS_NOT_ENOUGH_DATA;
        return info;
      }
      // Validates raw VP8L data.
      byte[] bytes = bufferReader.getBytes(5);
      if (!(bytes[0] == VP8L_MAGIC_BYTE && (bytes[4] >> 5) == 0)) {
        loge("processVp8Bitstream: Bad VP8L signature");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
      VP8LBitsReader br = new VP8LBitsReader(chunkData);
      if (readVP8LBits(br, 8) == VP8L_MAGIC_BYTE) {
        width = readVP8LBits(br, VP8L_IMAGE_SIZE_BITS) + 1;
        height = readVP8LBits(br, VP8L_IMAGE_SIZE_BITS) + 1;
        hasAlpha = readVP8LBits(br, 1) != 0;
        if (readVP8LBits(br, VP8L_VERSION_BITS) != 0) {
          loge("processVp8Bitstream: Incompat version.");
          info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
          return info;
        }
        if (br.eos) {
          loge("processVp8Bitstream: Invalid VP8LBitsReader eos state.");
          info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
          return info;
        }
      }
    }
    // FIXME: 2018/7/7 correct valid conditions of size?
    if (false && (this.header.canvasWidth != width || this.header.canvasHeight != height)) {
      loge("processVp8Bitstream:  Validates image size coherency failed!");
      info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return info;
    }
    info.width = width;
    info.height = height;
    info.hasAlpha = hasAlpha && this.header.hasAlpha;
    this.header.currentFrame.bufferFrameStart = chunkData.start + chunkData.payloadOffset;
    return info;
  }

  private class VP8LBitsReader {

    BigInteger val = BigInteger.valueOf(0);
    int len;
    int offset;
    int pos;
    boolean eos;
    ByteBuffer bytes;

    private VP8LBitsReader(ChunkData chunkData) {
      len = chunkData.size;
      int size = Math.min(8, chunkData.size);
      int start = chunkData.rawBuffer.position();
      for (int i = 0; i < size; ++i) {
        val = val.or(bufferReader.readUnsignedLongFrom(start + i).shiftLeft(8 * i));
      }
      pos = size;
      chunkData.resetData();
      byte[] bs = new byte[VP8L_MAX_NUM_BIT_READ];
      chunkData.rawBuffer.get(bs);
      bytes = ByteBuffer.wrap(bs).order(ByteOrder.LITTLE_ENDIAN);
    }

  }

  private int readVP8LBits(VP8LBitsReader reader, int len) {
    if (!reader.eos && len <= VP8L_MAX_NUM_BIT_READ) {
      int val = (reader.val
              .shiftRight(reader.offset & (VP8L_LBITS - 1))
              .and(BigInteger.valueOf(Vp8Info.Vp8BitMask[len])))
              .intValue();
      reader.offset += len;
      while (reader.offset >= 8 && reader.pos < reader.len) {
        reader.val = reader.val.shiftRight(8);
        reader.val = reader.val.or(
                bufferReader.unsignedLong(reader.bytes.getLong(reader.pos))
                        .shiftLeft(VP8L_LBITS - 8));
        reader.pos++;
        reader.offset -= 8;
      }
      return val;
    } else {
      reader.eos = true;
      return 0;
    }
  }

  private void processALPHChunk(ChunkData chunkData) {
    chunkData.reset();
    if (this.header.currentFrame.isProcessingAnimFrame) {
      this.header.currentFrame.anmfSubchunksMark[2] = true;
      if (this.header.currentFrame.foundAlphaSubchunk) {
        loge("Consecutive ALPH sub-chunks in an ANMF chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      this.header.currentFrame.foundAlphaSubchunk = true;
      if (this.header.currentFrame.foundImageSubchunk) {
        loge("ALPHA sub-chunk detected after VP8 sub-chunk in an ANMF chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
    } else {
      if (this.header.chunksMark[ChunkId.ANIM.ordinal()] ||
              this.header.chunksMark[ChunkId.ANMF.ordinal()]) {
        loge("ALPHA chunk and ANIM/ANMF chunk are both detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!this.header.chunksMark[ChunkId.VP8X.ordinal()]) {
        loge("ALPHA chunk detected before VP8X chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (this.header.chunksMark[ChunkId.VP8.ordinal()]) {
        loge("ALPHA chunk detected after VP8 chunk.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (this.header.chunksMark[ChunkId.ALPHA.ordinal()]) {
        loge("Multiple ALPHA chunks detected.");
        this.header.status = STATUS_PARSE_ERROR;
        return;
      }
      this.header.chunksMark[chunkData.id.ordinal()] = true;
    }
    this.header.currentFrame.hasAlpha = true;
    // FIXME: 2018/7/2 parse alpha subtrunk
    parseAlphaHeader(chunkData);
  }

  private void parseAlphaHeader(ChunkData chunkData) {
    chunkData.reset();
    int dataSize = chunkData.size - CHUNK_HEADER_SIZE;
    if (dataSize <= ALPHA_HEADER_LEN) {
      loge("Truncated ALPH chunk.");
      this.header.status = STATUS_TRUNCATED_DATA;
      return;
    }
    loge(" Parsing ALPH chunk...");
    {
      int next = bufferReader.getIntFrom(chunkData.start + chunkData.payloadOffset, 1);
      // alpha compression method, diff from image compression method.
      int compressionMethod = (next >> 0) & 0x03;
      int alphaFilter = (next >> 2) & 0x03;
      int preProcessingMethod = (next >> 4) & 0x03;
      int reservedBits = (next >> 6) & 0x03;
      loge(" \tCompression format:    " + Vp8AlphaFormat.values()[compressionMethod].name());
      loge(" \tFilter:                " + Vp8AlphaFilter.values()[alphaFilter].name());
      loge(" \tPre-processing:        " + preProcessingMethod);
      if (compressionMethod >= Vp8AlphaFormat.Invalid.ordinal()) {
        loge("Invalid Alpha compression method.");
        this.header.status = STATUS_BITSTREAM_ERROR;
        return;
      }
      if (preProcessingMethod > ALPHA_PREPROCESSED_LEVELS) {
        loge("Invalid Alpha pre-processing method");
        this.header.status = STATUS_BITSTREAM_ERROR;
        return;
      }
      if (reservedBits != 0) {
        logw("Reserved bits in ALPH chunk header are not all 0.");
      }
      if (compressionMethod == Vp8AlphaFormat.Lossless.ordinal()) {
        // FIXME: 7/3/2018 parse lossless transform
      }
    }
  }

  private void processICCPChunk(ChunkData chunkData) {
    chunkData.reset();
    if (!this.header.chunksMark[ChunkId.VP8X.ordinal()]) {
      loge("ICCP chunk detected before VP8X chunk.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (this.header.chunksMark[ChunkId.VP8.ordinal()] ||
            this.header.chunksMark[ChunkId.VP8L.ordinal()] ||
            this.header.chunksMark[ChunkId.ANIM.ordinal()]) {
      loge("ICCP chunk detected after image data.");
      this.header.status = STATUS_PARSE_ERROR;
      return;
    }
    this.header.chunksMark[ChunkId.ICCP.ordinal()] = true;
  }

  private void loge(String msg) {
    if (Log.isLoggable(TAG, Log.ERROR)) {
      Log.e(TAG, msg);
    }
  }

  private void logw(String msg) {
    if (Log.isLoggable(TAG, Log.WARN)) {
      Log.w(TAG, msg);
    }
  }
  private boolean err() {
    return header.status != WebpDecoder.STATUS_OK;
  }

}
