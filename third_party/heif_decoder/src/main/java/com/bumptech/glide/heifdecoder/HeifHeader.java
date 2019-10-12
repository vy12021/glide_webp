package com.bumptech.glide.heifdecoder;

import androidx.annotation.ColorInt;

import java.util.ArrayList;
import java.util.List;


/**
 * A header object containing the number of frames in an animated WEBP image as well as basic
 * metadata like width and height that can be used to decode each individual frame of the HEIF. Can
 * be shared by one or more {@link com.bumptech.glide.heifdecoder.HeifDecoder}s to play the same
 * animated HEIF in multiple views.
 */
public class HeifHeader {

  /**
   * The "Netscape" loop count which means loop forever.
   */
  public static final int NETSCAPE_LOOP_COUNT_FOREVER         = 0;
  /**
   * Indicates that this header has no "Netscape" loop count.
   */
  public static final int NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST  = -1;

  // current frame
  HeifFrame currentFrame = new HeifFrame(-1);
  // frames container
  final List<HeifFrame> frames = new ArrayList<>();

  // decode status
  @HeifDecoder.WebpDecodeStatus
  int status = HeifDecoder.STATUS_OK;

  boolean hasAlpha;
  // global background color
  @ColorInt
  int bgColor;
  // frame count
  int frameCount;
  // loop repeat times
  int loopCount = NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST;
  //
  int canvasWidth, canvasHeight;

  public int getHeight() {
    return canvasHeight;
  }

  public int getWidth() {
    return canvasWidth;
  }

  public int getNumFrames() {
    return frameCount;
  }

  public HeifFrame next() {
    return this.frames.get(0);
  }

  void newFrame() {
    this.frames.add(this.currentFrame = new HeifFrame(frameCount));
  }

  /**
   * Global status code of WEBP data parsing.
   */
  @HeifDecoder.WebpDecodeStatus
  public int getStatus() {
    return status;
  }

  @Override
  public String toString() {
    return "WebpHeader{" +
            "\n status=" + status +
            ",\n canvasWidth=" + canvasWidth +
            ",\n canvasHeight=" + canvasHeight +
            ",\n frameCount=" + frameCount +
            ",\n loopCount=" + loopCount +
            ",\n frames=" + frames +
            ",\n bgColor=" + bgColor +
            '}';
  }
}
