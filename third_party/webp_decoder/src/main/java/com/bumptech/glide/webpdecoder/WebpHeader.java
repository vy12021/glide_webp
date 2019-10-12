package com.bumptech.glide.webpdecoder;

import androidx.annotation.ColorInt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bumptech.glide.webpdecoder.WebpHeaderParser.ALL_VALID_FLAGS;

/**
 * A header object containing the number of frames in an animated WEBP image as well as basic
 * metadata like width and height that can be used to decode each individual frame of the WEBP. Can
 * be shared by one or more {@link com.bumptech.glide.webpdecoder.WebpDecoder}s to play the same
 * animated WEBP in multiple views.
 */
public class WebpHeader {

  /**
   * The "Netscape" loop count which means loop forever.
   */
  public static final int NETSCAPE_LOOP_COUNT_FOREVER         = 0;
  /**
   * Indicates that this header has no "Netscape" loop count.
   */
  public static final int NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST  = -1;

  // current frame
  WebpFrame currentFrame = new WebpFrame(-1);
  // frames container
  final List<WebpFrame> frames = new ArrayList<>();

  // decode status
  @WebpDecoder.WebpDecodeStatus
  int status = WebpDecoder.STATUS_OK;
  // riff chunk size
  int riffSize;
  // ALPH Chunk.
  boolean hasAlpha;
  // ANMF Chunk.
  boolean hasAnimation;
  // EXIF Chunk.
  boolean hasExif;
  // ICCP Chunk.
  boolean hasIccp;
  // XMP Chunk.
  boolean hasXmp;

  // global background color
  @ColorInt
  int bgColor;
  // frame count
  int frameCount;
  // loop repeat times
  int loopCount = NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST;
  //
  int canvasWidth, canvasHeight;
  // flags for vp8x chunk
  @WebpHeaderParser.WebpFeatureFlag
  int featureFlags = ALL_VALID_FLAGS;

  // chunk flag for mark tag parsed
  boolean chunksMark[] = new boolean[ChunkId.CHUNK_ID_TYPES];

  public int getHeight() {
    return canvasHeight;
  }

  public int getWidth() {
    return canvasWidth;
  }

  public int getNumFrames() {
    return frameCount;
  }

  public WebpFrame next() {
    return this.frames.get(0);
  }

  void newFrame() {
    this.frames.add(this.currentFrame = new WebpFrame(frameCount));
  }

  /**
   * Global status code of WEBP data parsing.
   */
  @WebpDecoder.WebpDecodeStatus
  public int getStatus() {
    return status;
  }

  String printVp8XInfo() {
    return "Vp8XInfo{" +
            "\n riffSize=" + riffSize +
            ",\n featureFlags=" + featureFlags +
            ",\n hasAlpha=" + hasAlpha +
            ",\n hasAnimation=" + hasAnimation +
            ",\n hasExif=" + hasExif +
            ",\n hasIccp=" + hasIccp +
            ",\n hasXmp=" + hasXmp +
            ",\n canvasWidth=" + canvasWidth +
            ",\n canvasHeight=" + canvasHeight +
            '}';
  }

  @Override
  public String toString() {
    return "WebpHeader{" +
            "\n status=" + status +
            ",\n riffSize=" + riffSize +
            ",\n featureFlags=" + featureFlags +
            ",\n hasAlpha=" + hasAlpha +
            ",\n hasAnimation=" + hasAnimation +
            ",\n hasExif=" + hasExif +
            ",\n hasIccp=" + hasIccp +
            ",\n hasXmp=" + hasXmp +
            ",\n canvasWidth=" + canvasWidth +
            ",\n canvasHeight=" + canvasHeight +
            ",\n frameCount=" + frameCount +
            ",\n loopCount=" + loopCount +
            ",\n frames=" + frames +
            ",\n bgColor=" + bgColor +
            ",\n chunksMark=" + Arrays.toString(chunksMark) +
            '}';
  }
}
