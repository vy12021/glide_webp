package com.bumptech.glide.webpdecoder;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Inner model class housing metadata for each frame.
 */
public class WebpFrame {

  /**
   * WEBP Disposal Method meaning leave canvas from previous frame.
   * The graphic is to be left in place.</i></p>
   */
  static final int DISPOSAL_NONE = 0;
  /**
   * WEBP Disposal Method meaning clear canvas to background color.
   * The area used by the graphic must be restored to the background color.</i></p>
   */
  static final int DISPOSAL_BACKGROUND = 1;
  /**
   * WEBP Disposal Method meaning clear canvas to frame before last.
   * The decoder is required to restore the area overwritten by the graphic
   * with what was there prior to rendering the graphic.</i></p>
   */
  static final int DISPOSAL_PREVIOUS = 2;

  /**
   * Dispose method (animation only). Indicates how the area used by the current
   * frame is to be treated before rendering the next frame on the canvas.
   *
   * @see #DISPOSAL_NONE
   * @see #DISPOSAL_BACKGROUND
   * @see #DISPOSAL_PREVIOUS
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {DISPOSAL_NONE, DISPOSAL_BACKGROUND, DISPOSAL_PREVIOUS})
  private @interface WebPMuxAnimDispose {
  }

  /**
   * WEBP Blend Mode meaning blend current frame with previous frame before rending.
   */
  static final int BLEND_MUX = 0;

  /**
   * WEBP Blend Mode meaning no action.
   */
  static final int BLEND_NONE = 1;

  /**
   * Blend operation (animation only). Indicates how transparent pixels of the
   * current frame are blended with those of the previous canvas.
   * @see #BLEND_MUX
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {BLEND_MUX, BLEND_NONE})
  private @interface WebPMuxAnimBlend {
  }

  /**
   * offset
   */
  public int offsetX, offsetY;
  /**
   * size
   */
  public int width, height;
  /**
   * alpha subchunk
   */
  public boolean hasAlpha;
  /**
   * Control Flag.
   */
  public boolean transparency;
  /**
   * Disposal Method.
   */
  @WebPMuxAnimDispose
  public int dispose;
  /**
   * Blend Mode.
   */
  @WebPMuxAnimBlend
  public int blend = BLEND_NONE;
  /**
   * Transparency Index.
   */
  public int transIndex;
  /**
   * duration, in milliseconds, to next frame.
   */
  public int duration = 40;
  /**
   * Index in the raw buffer where we need to start reading to decode.
   */
  public int bufferFrameStart;
  /**
   * frame data size
   */
  public int frameSize;

  // anim chunk process flag
  boolean isProcessingAnimFrame;
  boolean foundAlphaSubchunk;
  boolean foundImageSubchunk;
  // anmf subchunk flag for mark tag parsed
  final boolean anmfSubchunksMark[] = new boolean[3];  // 0 VP8; 1 VP8L; 2 ALPH.

  @Override
  public String toString() {
    return "WebpFrame{" +
            "offsetX=" + offsetX +
            ", offsetY=" + offsetY +
            ", width=" + width +
            ", height=" + height +
            ", dispose=" + dispose +
            ", blend=" + blend +
            ", duration=" + duration +
            ", bufferFrameStart=" + bufferFrameStart +
            '}';
  }
}
