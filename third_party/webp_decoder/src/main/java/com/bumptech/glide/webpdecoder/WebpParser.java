package com.bumptech.glide.webpdecoder;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_BITSTREAM_ERROR;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_INVALID_PARAM;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_MISS_DATA;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_OK;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_PARSE_ERROR;
import static com.bumptech.glide.webpdecoder.WebpDecoder.STATUS_TRUNCATED_DATA;

/**
 * A class responsible for creating {@link WebpHeader}s from data
 * representing animated WEBPs.
 * Specifics: https://developers.google.com/speed/webp/docs/riff_container
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
public class WebpParser {
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

  private ByteBufferReader reader;
  private WebpHeader header;

  public WebpParser(@NonNull ByteBuffer buffer) {
    setData(buffer);
  }

  public WebpParser(@NonNull byte[] data) {
    setData(data);
  }

  public WebpParser setData(byte[] data) {
    ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
    buffer.put(data);
    return setData(buffer);
  }

  public WebpParser setData(ByteBuffer buffer) {
    if (!buffer.isDirect()) {
      throw new IllegalArgumentException("ByteBuffer must be direct allocated");
    }
    header = new WebpHeader();
    reader = new ByteBufferReader(buffer, ByteOrder.LITTLE_ENDIAN);
    return this;
  }

  public void clear() {
    reader.clear();
    header = null;
  }

  /**
   * Determines if the WEBP is animated by trying to read in the first 2 frames
   * This method re-parses the data even if the header has already been read.
   */
  public boolean isAnimated() {
    return header.frameCount > 1;
  }

  @NonNull
  public WebpHeader parse() {
    if (header == null) {
      throw new IllegalStateException("You must call setData() before parseHeader()");
    }
    if (header.status != WebpDecoder.STATUS_OK) {
      return header;
    }
    readHeader();
    return header;
  }

  /**
   * Reads WEBP file header information.
   */
  private void readHeader() {
    parseRIFFHeader();
    while (STATUS_OK == header.status && reader.remaining() > 0) {
      ChunkData chunkData = parseChunk();
      if (null == chunkData) {
        break;
      }
      if (!header.hasAnimation && (chunkData.id == ChunkId.VP8
              || chunkData.id == ChunkId.VP8L || chunkData.id == ChunkId.ALPHA)) {
        header.status = WebpDecoder.STATUS_MISS_DATA;
        break;
      }
      chunkData.save();
      if (STATUS_OK == header.status) {
        processChunk(chunkData);
      }
      chunkData.restore();
    }
    validate();
    logd("webp header info: " + header.toString());
  }

  private void validate() {
    if (header.frameCount < 1) {
      loge("No frame detected，may be a static image.");
      header.status = STATUS_MISS_DATA;
      return;
    }
    if (header.getChunkMark(ChunkId.VP8X)) {
      boolean iccp = (header.featureFlags & ICCP_FLAG) != 0;
      boolean exif = (header.featureFlags & EXIF_FLAG) != 0;
      boolean xmp = (header.featureFlags & XMP_FLAG) != 0;
      boolean animation = (header.featureFlags & ANIMATION_FLAG) != 0;
      boolean alpha = (header.featureFlags & ALPHA_FLAG) != 0;
      if (!alpha && header.hasAlpha) {
        loge("Unexpected alpha data detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (alpha && !header.hasAlpha) {
        logw("Alpha flag is set with no alpha data present.");
      }
      if (exif && !header.getChunkMark(ChunkId.EXIF)) {
        loge("Missing EXIF chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (xmp && !header.getChunkMark(ChunkId.XMP)) {
        loge("Missing XMP chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!iccp && header.getChunkMark(ChunkId.ICCP)) {
        loge("Unexpected ICCP chunk detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!exif && header.getChunkMark(ChunkId.EXIF)) {
        loge("Unexpected EXIF chunk detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!xmp && header.getChunkMark(ChunkId.XMP)) {
        loge("Unexpected XMP chunk detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      // Incomplete animation frame
      if (header.isProcessingAnimFrame) {
        header.status = STATUS_MISS_DATA;
        return;
      }
      if (!animation && header.frameCount > 1) {
        loge("More than 1 frame detected in non-animation file.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (animation && (!header.getChunkMark(ChunkId.ANIM) ||
              !header.getChunkMark(ChunkId.ANMF))) {
        loge("No ANIM/ANMF chunk detected in animation file.");
        header.status = STATUS_PARSE_ERROR;
      }
    }
  }

  /**
   * parse riff container tag
   */
  private void parseRIFFHeader() {
    int minSize = RIFF_HEADER_SIZE + CHUNK_HEADER_SIZE;
    if (reader.remaining() < minSize) {
      loge("Need more data when parsing RIFF header.");
      header.status = WebpDecoder.STATUS_NEED_MORE_DATA;
      return;
    }

    if (!reader.getEquals("RIFF") ||
            !reader.getEquals(CHUNK_HEADER_SIZE, "WEBP")) {
      loge("Corrupted RIFF header.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }

    // riff byte count after header
    long riffSize = reader.getUnsignedIntFrom(TAG_SIZE);
    if (riffSize < CHUNK_HEADER_SIZE) {
      loge("RIFF size is too small.");
      header.status = STATUS_PARSE_ERROR;
    }
    if (riffSize > MAX_CHUNK_PAYLOAD) {
      loge("RIFF size is over limit.");
      header.status = STATUS_PARSE_ERROR;
    }
    // should be equals file size
    header.riffSize = (riffSize += CHUNK_HEADER_SIZE);
    if (riffSize < reader.size()) {
      logw("RIFF size is smaller than the file size.");
    } else if (riffSize > reader.size()) {
      loge("Need more data when parsing RIFF payload.");
      header.status = WebpDecoder.STATUS_NEED_MORE_DATA;
    }
    reader.skip(RIFF_HEADER_SIZE);
  }

  /**
   * parse several chunks
   */
  private ChunkData parseChunk() {
    if (reader.remaining() < CHUNK_HEADER_SIZE) {
      loge("Truncated data detected when parsing chunk header.");
      header.status = STATUS_TRUNCATED_DATA;
      return null;
    }
    ChunkData chunkData = new ChunkData(reader);
    // chunk id
    String chunkTag = reader.readString(4);
    long payloadSize = reader.readUnsignedInt();
    // even format, trim bytes
    long payloadSizePadded = payloadSize + (payloadSize & 1);
    chunkData.id = ChunkId.getByTag(chunkTag);
    chunkData.size = (int) (CHUNK_HEADER_SIZE + payloadSizePadded);
    if (payloadSize > MAX_CHUNK_PAYLOAD) {
      loge("Size of chunk payload is over limit.");
      header.status = STATUS_INVALID_PARAM;
      return chunkData;
    }
    if (payloadSizePadded > chunkData.remaining()) {
      loge("Truncated data detected when parsing chunk payload.");
      header.status = STATUS_TRUNCATED_DATA;
      return chunkData;
    }
    if (chunkData.id == ChunkId.ANMF) {
      // formatted
      if (payloadSize != payloadSizePadded) {
        loge("ANMF chunk size should always be even.");
        header.status = STATUS_PARSE_ERROR;
        return chunkData;
      }
      // There are sub-chunks to be parsed in an ANMF chunk.
      reader.skip(ANMF_CHUNK_SIZE);
    } else {
      reader.skip((int) payloadSizePadded);
    }
    return chunkData;
  }

  private void processChunk(ChunkData chunkData) {
    if (chunkData.id == ChunkId.UNKNOWN) {
      logw("Unknown chunk at offset " + chunkData.start + ", length " + chunkData.size);
    } else {
      logd("Chunk " + chunkData.id + " at offset " + chunkData.start + " length " + chunkData.size);
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
        header.markChunk(ChunkId.XMP, true);
        break;
      case UNKNOWN:
      default:
        break;
    }
    if (header.isProcessingAnimFrame &&
            (chunkData.id == ChunkId.VP8 || chunkData.id == ChunkId.VP8L)) {
      if (!header.foundImageSubchunk) {
        loge("No VP8/VP8L chunk detected in an ANMF chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      header.isProcessingAnimFrame = false;
    }
  }

  private void processVP8XChunk(ChunkData chunkData) {
    chunkData.skip2Start();
    if (header.getChunkMark(ChunkId.VP8) ||
            header.getChunkMark(ChunkId.VP8L) ||
            header.getChunkMark(ChunkId.VP8X)) {
      loge("Already seen a VP8/VP8L/VP8X chunk when parsing VP8X chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (chunkData.size != VP8X_CHUNK_SIZE + CHUNK_HEADER_SIZE) {
      loge("Corrupted VP8X chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    // mark parsed
    header.markChunk(ChunkId.VP8X, true);
    chunkData.skip2Data();
    header.featureFlags = reader.readInt();
    header.canvasWidth = 1 + reader.readInt(3);
    header.canvasHeight = 1 + reader.readInt(3);
    header.hasAlpha = (header.featureFlags & ALPHA_FLAG) != 0;
    header.hasAnimation = (header.featureFlags & ANIMATION_FLAG) != 0;
    header.hasIccp = (header.featureFlags & ICCP_FLAG) != 0;
    header.hasExif = (header.featureFlags & EXIF_FLAG) != 0;
    header.hasXmp = (header.featureFlags & XMP_FLAG) != 0;
    if (header.canvasWidth > MAX_CANVAS_SIZE) {
      logw("Canvas width is out of range in VP8X chunk.");
    }
    if (header.canvasHeight > MAX_CANVAS_SIZE) {
      logw("Canvas height is out of range in VP8X chunk.");
    }
    if (header.canvasHeight * header.canvasWidth > MAX_IMAGE_AREA) {
      logw("Canvas area is out of range in VP8X chunk.");
    }
    logd("processVP8XChunk: \n" + header.printVp8XInfo());
  }

  private void processANIMChunk(ChunkData chunkData) {
    // reset buffer to start position
    chunkData.skip2Start();
    if (!header.getChunkMark(ChunkId.VP8X)) {
      loge("ANIM chunk detected before VP8X chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (chunkData.size != ANIM_CHUNK_SIZE + CHUNK_HEADER_SIZE) {
      loge("Corrupted ANIM chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    chunkData.skip2Data();
    header.bgColor = reader.readInt();
    header.loopCount = reader.readInt(2);
    header.markChunk(ChunkId.ANIM, true);
    if (header.loopCount > MAX_LOOP_COUNT) {
      logw("Loop count is out of range in ANIM chunk.");
    }
  }

  private void processANMFChunk(ChunkData chunkData) {
    int offsetX, offsetY, width, height, duration, blend, dispose;
    if (header.isProcessingAnimFrame) {
      loge("ANMF chunk detected within another ANMF chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (!header.getChunkMark(ChunkId.ANIM)) {
      loge("ANMF chunk detected before ANIM chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (chunkData.size <= CHUNK_HEADER_SIZE + ANMF_CHUNK_SIZE) {
      loge("Truncated data detected when parsing ANMF chunk.");
      header.status = STATUS_TRUNCATED_DATA;
      return;
    }
    chunkData.skip2Data();
    offsetX = 2 * reader.readInt(3);
    offsetY = 2 * reader.readInt(3);
    width = 1 + reader.readInt(3);
    height = 1 + reader.readInt(3);
    duration = reader.readInt(3);
    dispose = reader.getByte() & 1;
    blend = (reader.getByte() >> 1) & 1;
    header.markChunk(ChunkId.ANMF, true);
    if (duration > MAX_DURATION) {
      loge("Invalid duration parameter in ANMF chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (offsetX > MAX_POSITION_OFFSET || offsetY > MAX_POSITION_OFFSET) {
      loge("Invalid offset parameters in ANMF chunk.");
      header.status = STATUS_INVALID_PARAM;
      return;
    }
    if (offsetX + width > header.canvasWidth ||
            offsetY + height > header.canvasHeight) {
      loge("Frame exceeds canvas in ANMF chunk.");
      header.status = STATUS_INVALID_PARAM;
      return;
    }
    WebpFrame frame = header.newFrame();
    header.isProcessingAnimFrame = true;
    header.foundAlphaSubchunk = false;
    header.foundImageSubchunk = false;
    frame.duration = duration < MIN_FRAME_DELAY ? DEFAULT_FRAME_DELAY : duration;
    frame.dispose = dispose;
    frame.blend = blend;
    frame.offsetX = offsetX;
    frame.offsetY = offsetY;
    frame.width = width;
    frame.height = height;
    frame.bufferSize = chunkData.size - CHUNK_HEADER_SIZE - ANMF_CHUNK_SIZE;
  }

  private void processImageChunk(ChunkData chunkData) {
    WebpFrame frame = header.current;
    Vp8Info vp8Info = frame.vp8Info = parseVp8Bitstream(chunkData);
    if (vp8Info.status != Vp8Info.VP8_STATUS_OK) {
      loge("VP8/VP8L bitstream error.");
      header.status = STATUS_BITSTREAM_ERROR;
    }
    if (header.isProcessingAnimFrame) {
      header.markANMFSubchunk(chunkData.id, true);
      if (chunkData.id == ChunkId.VP8L && header.foundAlphaSubchunk) {
        loge("Both VP8L and ALPH sub-chunks are present in an ANMF chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (frame.width != vp8Info.width || frame.height != vp8Info.height) {
        loge("Frame size in VP8/VP8L sub-chunk differs from ANMF header.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (header.foundImageSubchunk) {
        loge("Consecutive VP8/VP8L sub-chunks in an ANMF chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      header.foundImageSubchunk = true;
    } else {
      if (header.getChunkMark(ChunkId.VP8) ||
              header.getChunkMark(ChunkId.VP8L)) {
        loge("Multiple VP8/VP8L chunks detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (chunkData.id == ChunkId.VP8L && header.getChunkMark(ChunkId.ALPHA)) {
        logw("Both VP8L and ALPH chunks are detected.");
      }
      if (header.getChunkMark(ChunkId.ANIM) || header.getChunkMark(ChunkId.ANMF)) {
        loge("VP8/VP8L chunk and ANIM/ANMF chunk are both detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (header.getChunkMark(ChunkId.VP8X)) {
        if (header.canvasWidth != vp8Info.width ||
                header.canvasHeight != vp8Info.height) {
          loge("Image size in VP8/VP8L chunk differs from VP8X chunk.");
          header.status = STATUS_PARSE_ERROR;
        }
      } else {
        header.canvasWidth = vp8Info.width;
        header.canvasHeight = vp8Info.height;
        if (header.canvasWidth < 1 || header.canvasHeight < 1 ||
                header.canvasWidth > MAX_CANVAS_SIZE ||
                header.canvasHeight > MAX_CANVAS_SIZE ||
                header.canvasWidth * header.canvasHeight > MAX_IMAGE_AREA) {
          logw("Invalid parameters in VP8/VP8L chunk. Out range of image size");
        }
      }
      header.markChunk(chunkData.id, true);
    }
    header.frameCount++;
    header.hasAlpha |= vp8Info.hasAlpha;
    if (ChunkId.VP8 == chunkData.id) {
      // lossy
      vp8Info.lossyInfo = parseLossyHeader(chunkData);
    } else {
      // lossless
      vp8Info.losslessInfo = parseLosslessHeader(chunkData);
    }
  }

  private Vp8Info.LossyInfo parseLossyHeader(ChunkData chunkData) {
    chunkData.skip2Data();
    Vp8Info.LossyInfo lossyInfo = new Vp8Info.LossyInfo();
    byte[] bytes;
    chunkData.get(bytes = new byte[3]);
    int bits = (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8) | ((bytes[2] & 0xff) << 16);
    int dataSize = chunkData.size - CHUNK_HEADER_SIZE;
    boolean keyFrame = (bits & 1) == 0;
    int profile = (bits >> 1) & 7;
    boolean display = ((bits >> 4) & 1) != 0;
    int partition0Length = bits >> 5;
    Position bitPosition = new Position();
    int colorSpace, clampType;
    logd("  Parsing lossy bitstream...");
    if (profile > 3) {
      loge("Unknown profile.");
      lossyInfo.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return lossyInfo;
    }
    if (!display) {
      loge("Frame is not displayable.");
      lossyInfo.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return lossyInfo;
    }
    logd(String.format(Locale.getDefault(),
            "  Key frame:        %s\n" +
            "  Profile:          %d\n" +
            "  Display:          %s\n" +
            "  Part. 0 length:   %d",
            keyFrame ? "Yes" : "No", profile,
            display ? "Yes" : "No", partition0Length));

    chunkData.skip(3);
    dataSize -= 3;
    if (keyFrame) {
      chunkData.get(bytes = new byte[7]);
      if (!((bytes[0] & 0xff) == 0x9d && (bytes[1] & 0xff) == 0x01 && (bytes[2] & 0xff) == 0x2a)) {
        loge("Invalid lossy bitstream signature.");
        lossyInfo.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
        return lossyInfo;
      }
      logd(String.format(Locale.getDefault(),
              "  Width:            %d\n" +
              "  X scale:          %d\n" +
              "  Height:           %d\n" +
              "  Y scale:          %d",
              (((bytes[4] & 0xff) << 8) | (bytes[3] & 0xff)) & 0x3fff, (bytes[4] & 0xff) >> 6,
              (((bytes[6] & 0xff) << 8) | (bytes[5] & 0xff)) & 0x3fff, (bytes[6] & 0xff) >> 6));
      chunkData.skip(7);
      dataSize -= 7;
    } else {
      loge("Non-keyframe detected in lossy bitstream.");
      lossyInfo.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return lossyInfo;
    }
    if (partition0Length >= dataSize) {
      loge("Bad partition length.");
      lossyInfo.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return lossyInfo;
    }
    colorSpace = getBits(chunkData, dataSize, 1, bitPosition);
    clampType = getBits(chunkData, dataSize,  1, bitPosition);
    logd("  Color space:      " + colorSpace);
    logd("  Clamp type:       " + clampType);
    Vp8Info.LossyInfo.LossySegment segment = lossyInfo.segment
            = parseLossySegmentHeader(chunkData, dataSize, bitPosition);
    if (segment.status != Vp8Info.VP8_STATUS_OK) {
      return lossyInfo;
    }
    Vp8Info.LossyInfo.LossyFilter filter = lossyInfo.filter
            = parseLossyFilterHeader(chunkData, dataSize, bitPosition);
    if (filter.status != Vp8Info.VP8_STATUS_OK) {
      return lossyInfo;
    }
    // Partition number and size.
    chunkData.save();
    chunkData.skip(partition0Length);
    int numParts = getBits(chunkData, dataSize, 2, bitPosition);
    numParts = 1 << numParts;
    logd("  Total partitions: " + numParts);
    int partDataSize = dataSize - partition0Length - (numParts - 1) * 3;
    if (partDataSize < 0) {
      loge("Truncated lossy bitstream.");
      lossyInfo.status = STATUS_TRUNCATED_DATA;
      return lossyInfo;
    }
    bytes = new byte[3];
    for (int i = 1, psize; i < numParts; ++i) {
      chunkData.get(bytes);
      // fixme 2020/07/26, webpinfo.c line 353
      psize = (bytes[0] & 0xff) | ((bytes[1] & 0xff) << 8) | ((bytes[2] & 0xff) << 16);
      if (psize > partDataSize) {
        chunkData.restore();
        loge("Truncated partition.");
        lossyInfo.status = STATUS_TRUNCATED_DATA;
        return lossyInfo;
      }
      logd(String.format(Locale.getDefault(), "  Part. %d length:   %d", i, psize));
      partDataSize -= psize;
      chunkData.skip(3);
    }
    chunkData.restore();
    // Quantizer.
    int base_q = getBits(chunkData, dataSize, 7, bitPosition),
            bit = getBits(chunkData, dataSize, 1, bitPosition);
    int dq_y1_dc = 0, dq_y2_dc = 0, dq_y2_ac = 0, dq_uv_dc = 0, dq_uv_ac = 0;
    if (bit != 0) dq_y1_dc = getSignedBits(chunkData, dataSize, 4, bitPosition);
    bit = getBits(chunkData, dataSize, 1, bitPosition);
    if (bit != 0) dq_y2_dc = getSignedBits(chunkData, dataSize, 4, bitPosition);
    bit = getBits(chunkData, dataSize, 1, bitPosition);
    if (bit != 0) dq_y2_ac = getSignedBits(chunkData, dataSize, 4, bitPosition);
    bit = getBits(chunkData, dataSize, 1, bitPosition);
    if (bit != 0) dq_uv_dc = getSignedBits(chunkData, dataSize, 4, bitPosition);
    bit = getBits(chunkData, dataSize, 1, bitPosition);
    if (bit != 0) dq_uv_ac = getSignedBits(chunkData, dataSize, 4, bitPosition);
    logd("  Base Q:           " + base_q);
    logd("  DQ Y1 DC:         " + dq_y1_dc);
    logd("  DQ Y2 DC:         " + dq_y2_dc);
    logd("  DQ Y2 AC:         " + dq_y2_ac);
    logd("  DQ UV DC:         " + dq_uv_dc);
    logd("  DQ UV AC:         " + dq_uv_ac);
    if ((bitPosition.index >> 3) >= partition0Length) {
      loge("Truncated lossy bitstream.");
      lossyInfo.status = STATUS_TRUNCATED_DATA;
      return lossyInfo;
    }
    return lossyInfo;
  }

  private Vp8Info.LosslessInfo parseLosslessHeader(ChunkData chunkData) {
    Vp8Info.LosslessInfo losslessInfo = new Vp8Info.LosslessInfo();
    chunkData.save();
    chunkData.skip2Data();
    int dataSize = chunkData.size - CHUNK_HEADER_SIZE;
    Position bitPosition = new Position();
    logd("  Parsing lossless bitstream...");
    if (dataSize < VP8L_FRAME_HEADER_SIZE) {
      loge("Truncated lossless bitstream.");
      losslessInfo.status = STATUS_TRUNCATED_DATA;
      chunkData.restore();
      return losslessInfo;
    }
    if ((chunkData.get() & 0xff) != VP8L_MAGIC_BYTE) {
      loge("Invalid lossless bitstream signature.");
      losslessInfo.status = STATUS_BITSTREAM_ERROR;
      chunkData.restore();
      return losslessInfo;
    }
    chunkData.skip(1);
    dataSize -= 1;
    int width = LLGetBits(chunkData, dataSize, 14, bitPosition),
            height = LLGetBits(chunkData, dataSize, 14, bitPosition),
            hasAlpha = LLGetBits(chunkData, dataSize, 1, bitPosition),
            version = LLGetBits(chunkData, dataSize, 3, bitPosition);
    width += 1;
    height += 1;
    logd("  Width:            " + width);
    logd("  Height:           " + height);
    logd("  Alpha:            " + hasAlpha);
    logd("  Version:          " + version);
    Vp8Info.LosslessInfo.LosslessTransform transform = losslessInfo.transform
            = parseLosslessTransform(chunkData, dataSize, bitPosition);
    if (transform == Vp8Info.LosslessInfo.LosslessTransform.Unknown) {
      loge(" Unknown LosslessTransform.");
      losslessInfo.status = STATUS_BITSTREAM_ERROR;
      return losslessInfo;
    }
    return losslessInfo;
  }

  private Vp8Info.LosslessInfo.LosslessTransform parseLosslessTransform
          (ChunkData chunkData, int dataSize, Position bitPosition) {
    boolean useTransform = LLGetBits(chunkData, dataSize, 1, bitPosition) != 0;
    logd("  Use transform:    " + (useTransform ? "Yes" : "No"));
    if (!useTransform) {
      return null;
    }

    int type = LLGetBits(chunkData, dataSize, 2, bitPosition);
    Vp8Info.LosslessInfo.LosslessTransform transform
            = Vp8Info.LosslessInfo.LosslessTransform.values()[type];
    logd(String.format(Locale.getDefault(),
            "  1st transform:    %s (%d)", transform.name(), type));
    switch (transform) {
      case Predictor:
      case CrossColor:
        int blockSize = LLGetBits(chunkData, dataSize, 3, bitPosition);
        blockSize = 1 << (blockSize + 2);
        logd("  Tran. block size: " + blockSize);
        break;
      case ColorIndexing:
        int nColors = LLGetBits(chunkData, dataSize, 8, bitPosition);
        nColors += 1;
        logd("  No. of colors:    " + nColors);
        break;
      default:
        return Vp8Info.LosslessInfo.LosslessTransform.Unknown;
    }
    return transform;
  }

  private Vp8Info.LossyInfo.LossySegment parseLossySegmentHeader(
          ChunkData chunkData, int dataSize, Position bitPosition) {
    Vp8Info.LossyInfo.LossySegment lossySegment = new Vp8Info.LossyInfo.LossySegment();
    boolean useSegment = getBits(chunkData, dataSize, 1, bitPosition) != 0;
    logd("  Use segment:      " + (useSegment ? "Yes" : "No"));
    if (!useSegment) {
      return lossySegment;
    }

    boolean updateMap = getBits(chunkData, dataSize, 1, bitPosition) != 0;
    boolean updateData = getBits(chunkData, dataSize, 1, bitPosition) != 0;
    logd(String.format(
            "  Update map:       %s\n" +
            "  Update data:      %s",
            updateMap, updateData));
    if (updateData) {
      int[] quantizer = new int[4];
      int[] filterStrength = new int[4];
      int aDelta = getBits(chunkData, dataSize, 1, bitPosition);
      logd("  Absolute delta:   " + aDelta);
      for (int i = 0; i < 4; ++i) {
        if (getBits(chunkData, dataSize, 1, bitPosition) != 0) {
          quantizer[i] = getSignedBits(chunkData, dataSize, 7, bitPosition);
        }
      }
      for (int i = 0; i < 4; ++i) {
        if (getBits(chunkData, dataSize, 1, bitPosition) != 0) {
          filterStrength[i] = getSignedBits(chunkData, dataSize, 6, bitPosition);
        }
      }
      logd(String.format(Locale.getDefault(),
              "  Quantizer:        %d %d %d %d",
              quantizer[0], quantizer[1], quantizer[2], quantizer[3]));
      logd(String.format(Locale.getDefault(),
              "  Filter strength:  %d %d %d %d",
              filterStrength[0], filterStrength[1], filterStrength[2], filterStrength[3]));
    }
    if (updateMap) {
      int[] probSegment = {255, 255, 255};
      for (int i = 0; i < 3; ++i) {
        if (getBits(chunkData, dataSize, 1, bitPosition) != 0) {
          probSegment[i] = getSignedBits(chunkData, dataSize, 8, bitPosition);
        }
      }
      logd(String.format(Locale.getDefault(),
              "  Prob segment:     %d %d %d",
              probSegment[0], probSegment[1], probSegment[2]));
    }
    return lossySegment;
  }

  private Vp8Info.LossyInfo.LossyFilter parseLossyFilterHeader(
          ChunkData chunkData, int dataSize, Position bitPosition) {
    Vp8Info.LossyInfo.LossyFilter lossyFilter = new Vp8Info.LossyInfo.LossyFilter();
    int simpleFilter = getBits(chunkData, dataSize, 1, bitPosition),
            level = getBits(chunkData, dataSize, 6, bitPosition),
            sharpness = getBits(chunkData, dataSize, 3, bitPosition);
    boolean useLFDelta = getBits(chunkData, dataSize, 1, bitPosition) != 0;
    logd("  Simple filter:    " + simpleFilter);
    logd("  Level:            " + level);
    logd("  Sharpness:        " + sharpness);
    logd("  Use lf delta:     " + useLFDelta);
    if (useLFDelta) {
      boolean update = getBits(chunkData, dataSize, 1, bitPosition) != 0;
      logd("  Update lf delta:  " + update);
      if (update) {
        for (int i = 0; i < 4 + 4; ++i) {
          if (getBits(chunkData, dataSize, 1, bitPosition) != 0) {
            getBits(chunkData, dataSize, 7, bitPosition);
          }
        }
      }
    }
    return lossyFilter;
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
  private Vp8Info parseVp8Bitstream(ChunkData chunkData) {
    WebpFrame frame = header.current;
    chunkData.skip2Start();
    Vp8Info info = new Vp8Info();
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
      int size = reader.getIntFrom(chunkData.start + TAG_SIZE);
      if (header.riffSize > size && size > header.riffSize - minSize) {
        loge("processVp8Bitstream: Inconsistent size information.");
        info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
        return info;
      }
      if (size > chunkData.remaining() - CHUNK_HEADER_SIZE) {
        loge("processVp8Bitstream: Truncated bitstream.");
        info.status = Vp8Info.VP8_STATUS_NOT_ENOUGH_DATA;
        return info;
      }
      info.format = ChunkId.VP8L == chunkData.id ? Vp8Info.Format.Lossless : Vp8Info.Format.Lossy;
      chunkData.skip2Data();
    } else {
      // Raw VP8/VP8L bitstream (no header).
      byte[] bytes = reader.getBytes(5);
      if (bytes[0] == VP8L_MAGIC_BYTE && (bytes[4] >> 5) == 0) {
        info.format = Vp8Info.Format.Lossless;
      }
    }

    if (chunkData.size > MAX_CHUNK_PAYLOAD) {
      loge("processVp8Bitstream: Chunk size large than max chunk payload");
      info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return info;
    }
    if (info.format != Vp8Info.Format.Lossless) {
      // vp8 chunk
      if (chunkData.size < VP8_FRAME_HEADER_SIZE) {
        loge("processVp8Bitstream: Not enough data");
        info.status = Vp8Info.VP8_STATUS_NOT_ENOUGH_DATA;
        return info;
      }
      // Validates raw VP8 data.
      byte[] bytes = reader.getBytesFrom(chunkData.position() + 3, 3);
      if (!(bytes[0] == (byte) 0x9d && bytes[1] == (byte) 0x01 && bytes[2] == (byte) 0x2a)) {
        loge("processVp8Bitstream: Bad VP8 signature");
        info.status = Vp8Info.VP8_STATUS_INVALID_PARAM;
        return info;
      }
      bytes = reader.getBytes(10);
      width = (((bytes[7] & 0xff) << 8) | (bytes[6] & 0xff)) & 0x3fff;
      height = (((bytes[9] & 0xff) << 8) | (bytes[8] & 0xff)) & 0x3fff;
      int bits = reader.getIntWithLen(bytes, 3);
      boolean keyFrame = (bits & 1) == 0;
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
      if ((bits >> 5) >= chunkData.size) {
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
      byte[] bytes = reader.getBytes(5);
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
    if (frame.width != width || frame.height != height) {
      loge("processVp8Bitstream:  Validates image size coherency failed!");
      info.status = Vp8Info.VP8_STATUS_BITSTREAM_ERROR;
      return info;
    }
    info.width = width;
    info.height = height;
    info.hasAlpha = hasAlpha && header.hasAlpha;
    frame.bufferStart = chunkData.dataStart();
    frame.bufferSize = chunkData.size;
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
      int start = chunkData.position();
      for (int i = 0; i < size; ++i) {
        val = val.or(reader.readUnsignedLongFrom(start + i).shiftLeft(8 * i));
      }
      pos = size;
      chunkData.skip2Data();
      byte[] bs = new byte[VP8L_MAX_NUM_BIT_READ];
      chunkData.get(bs);
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
                this.reader.unsignedLong(reader.bytes.getLong(reader.pos))
                        .shiftLeft(VP8L_LBITS - 8));
        reader.pos++;
        reader.offset -= 8;
      }
      return val;
    }
    reader.eos = true;
    return 0;
  }

  private void processALPHChunk(ChunkData chunkData) {
    chunkData.skip2Start();
    WebpFrame frame = header.current;
    if (header.isProcessingAnimFrame) {
      header.markANMFSubchunk(chunkData.id, true);
      if (header.foundAlphaSubchunk) {
        loge("Consecutive ALPH sub-chunks in an ANMF chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      header.foundAlphaSubchunk = true;
      if (header.foundImageSubchunk) {
        loge("ALPHA sub-chunk detected after VP8 sub-chunk in an ANMF chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
    } else {
      if (header.getChunkMark(ChunkId.ANIM) || header.getChunkMark(ChunkId.ANMF)) {
        loge("ALPHA chunk and ANIM/ANMF chunk are both detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (!header.getChunkMark(ChunkId.VP8X)) {
        loge("ALPHA chunk detected before VP8X chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (header.getChunkMark(ChunkId.VP8)) {
        loge("ALPHA chunk detected after VP8 chunk.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      if (header.getChunkMark(ChunkId.ALPHA)) {
        loge("Multiple ALPHA chunks detected.");
        header.status = STATUS_PARSE_ERROR;
        return;
      }
      header.markChunk(chunkData.id, true);
    }
    if (null != frame) {
      frame.hasAlpha = true;
      frame.alphaInfo = parseAlphaHeader(chunkData);
    }
  }

  private AlphaInfo parseAlphaHeader(ChunkData chunkData) {
    chunkData.skip2Start();
    AlphaInfo alphaInfo = new AlphaInfo();
    int dataSize = chunkData.size - CHUNK_HEADER_SIZE;
    if (dataSize <= ALPHA_HEADER_LEN) {
      loge("Truncated ALPH chunk.");
      alphaInfo.status = STATUS_TRUNCATED_DATA;
      return alphaInfo;
    }
    logd(" Parsing ALPH chunk...");

    int nextByte = reader.getByteFrom(chunkData.dataStart()) & 0xff;
    // alpha compression method, diff from image compression method.
    int compressionMethod = nextByte & 0x03;
    int alphaFilter = (nextByte >>> 2) & 0x03;
    int preProcessingMethod = (nextByte >>> 4) & 0x03;
    int reservedBits = (nextByte >>> 6) & 0x03;
    AlphaInfo.Format format = AlphaInfo.Format.getFormat(compressionMethod);
    AlphaInfo.Filter filter = AlphaInfo.Filter.values()[alphaFilter];
    logd(" \tCompression:           " + format.name());
    logd(" \tFilter:                " + filter.name());
    logd(" \tPre-processing:        " + preProcessingMethod);
    alphaInfo.format = format;
    alphaInfo.filter = filter;
    alphaInfo.preProcessingMethod = preProcessingMethod;
    if (format == AlphaInfo.Format.Invalid) {
      loge("Invalid Alpha compression method.");
      alphaInfo.status = STATUS_BITSTREAM_ERROR;
      return alphaInfo;
    }
    if (preProcessingMethod > ALPHA_PREPROCESSED_LEVELS) {
      loge("Invalid Alpha pre-processing method");
      alphaInfo.status = STATUS_BITSTREAM_ERROR;
      return alphaInfo;
    }
    if (reservedBits != 0) {
      logw("Reserved bits in ALPH chunk header are not all 0.");
    }
    if (format == AlphaInfo.Format.Lossless) {
      chunkData.skip(ALPHA_HEADER_LEN);
      dataSize -= ALPHA_HEADER_LEN;
      Position bitPosition = new Position();
      alphaInfo.transform = parseLosslessTransform(chunkData, dataSize, bitPosition);
    }
    return alphaInfo;
  }

  private void processICCPChunk(ChunkData chunkData) {
    chunkData.skip2Start();
    if (!header.getChunkMark(ChunkId.VP8X)) {
      loge("ICCP chunk detected before VP8X chunk.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    if (header.getChunkMark(ChunkId.VP8) ||
            header.getChunkMark(ChunkId.VP8L) ||
            header.getChunkMark(ChunkId.ANIM)) {
      loge("ICCP chunk detected after image data.");
      header.status = STATUS_PARSE_ERROR;
      return;
    }
    header.markChunk(ChunkId.ICCP, true);
  }

  private void loge(String msg) {
    System.err.println(msg);
    /*if (Log.isLoggable(TAG, Log.ERROR)) {
      Log.e(TAG, msg);
    }*/
  }

  private void logd(String msg) {
    System.out.println(msg);
    /*if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, msg);
    }*/
  }

  private void logw(String msg) {
    System.out.println(msg);
    /*if (Log.isLoggable(TAG, Log.WARN)) {
      Log.w(TAG, msg);
    }*/
  }

  private static int LLGetBits(ChunkData data, int size, int nb, Position position) {
    int val = 0, i = 0, p, bit;
    while (i < nb) {
      p = position.index ++;
      if ((p >> 3) >= size) {
        return 0;
      } else {
        bit = ((data.getBy(p >> 3) & 0xff) & (1 << ((p & 7)))) != 0 ? 1 : 0;
        val = val | (bit << i);
        ++i;
      }
    }
    return 1;
  }

  private static int getBits(ChunkData data, int size, int nb, Position position) {
    int val = 0, p, bit;
    while (nb-- > 0) {
      p = position.index ++;
      if ((p >> 3) >= size) {
        return 0;
      } else {
        bit = ((data.getBy(p >> 3) & 0xff) & (128 >> (p & 7))) != 0 ? 1 : 0;
        val = (val << 1) | bit;
      }
    }
    return val;
  }

  private static int getSignedBits(ChunkData data, int size, int nb, Position position) {
    int val = getBits(data, size, nb, position);
    if (val == 0) {
      return 0;
    }
    int sign = getBits(data, size, 1, position);
    if (sign == 0) {
      return 0;
    }
    return -val;
  }

}
