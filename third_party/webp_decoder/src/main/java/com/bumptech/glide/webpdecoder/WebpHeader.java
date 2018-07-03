package com.bumptech.glide.webpdecoder;

import android.support.annotation.ColorInt;

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

  /** The "Netscape" loop count which means loop forever. */
  public static final int NETSCAPE_LOOP_COUNT_FOREVER = 0;
  /** Indicates that this header has no "Netscape" loop count. */
  public static final int NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST = -1;

  // current frame
  WebpFrame currentFrame = new WebpFrame();
  // frames container
  final List<WebpFrame> frames = new ArrayList<>();

  // decode status
  @WebpDecoder.WebpDecodeStatus
  int status = WebpDecoder.STATUS_OK;
  // riff chunk size
  int riffSize;
  // Alpha channel.
  boolean hasAlpha;
  // Chunk type ANMF
  boolean hasAnim;
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

  void newFrame() {
    this.frames.add(this.currentFrame = new WebpFrame());
  }

  /**
   * Global status code of WEBP data parsing.
   */
  @WebpDecoder.WebpDecodeStatus
  public int getStatus() {
    return status;
  }

  @Override
  public String toString() {
    return "WebpHeader{" +
            ", canvasWidth=" + canvasWidth +
            ", canvasHeight=" + canvasHeight +
            ", featureFlags=" + featureFlags +
            ", frameCount=" + frameCount +
            ", loopCount=" + loopCount +
            ", frames=" + frames +
            ", status=" + status +
            ", riffSize=" + riffSize +
            ", hasAlpha=" + hasAlpha +
            ", hasAnim=" + hasAnim +
            ", bgColor=" + bgColor +
            ", chunksMark=" + Arrays.toString(chunksMark) +
            '}';
  }
}
