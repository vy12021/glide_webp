package com.bumptech.glide.heifdecoder;

import android.support.annotation.IntDef;
import android.support.annotation.IntRange;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Inner model class housing metadata for each frame.
 */
public class HeifFrame {

  /**
   * WEBP Disposal Method meaning leave canvas from previous frame.
   * The graphic is to be left in place.</i></p>
   */
  static final int DISPOSAL_NONE        = 0;
  /**
   * WEBP Disposal Method meaning clear canvas to background color.
   * The area used by the graphic must be restored to the background color.</i></p>
   */
  static final int DISPOSAL_BACKGROUND  = 1;

  /**
   * Dispose method (animation only). Indicates how the area used by the current
   * frame is to be treated before rendering the next frame on the canvas.
   *
   * @see #DISPOSAL_NONE
   * @see #DISPOSAL_BACKGROUND
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {DISPOSAL_NONE, DISPOSAL_BACKGROUND})
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
   *
   * @see #BLEND_MUX
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {BLEND_MUX, BLEND_NONE})
  private @interface WebPMuxAnimBlend {
  }

  HeifFrame(int index) {
    this.index = index;
  }

  /**
   * frame index
   */
  @IntRange(from = 0)
  final int index;
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

  // Flags while parsing webp header.
  // anim chunk process flag
  boolean isProcessingAnimFrame;
  boolean foundAlphaSubchunk;
  boolean foundImageSubchunk;
  // anmf subchunk flag for mark tag parsed
  final boolean anmfSubchunksMark[] = new boolean[3];  // 0 VP8; 1 VP8L; 2 ALPH.

  @Override
  public String toString() {
    return "WebpFrame{" +
            "frameIndex=" + index +
            ", offsetX=" + offsetX +
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
