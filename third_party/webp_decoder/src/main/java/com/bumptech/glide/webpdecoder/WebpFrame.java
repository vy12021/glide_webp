package com.bumptech.glide.webpdecoder;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Inner model class housing metadata for each frame.
 */
class WebpFrame {
  /**
   * WEBP Disposal Method meaning take no action.
   * The decoder is not required to take any action.</i></p>
   */
  static final int DISPOSAL_UNSPECIFIED = 0;
  /**
   * WEBP Disposal Method meaning leave canvas from previous frame.
   * The graphic is to be left in place.</i></p>
   */
  static final int DISPOSAL_NONE = 1;
  /**
   * WEBP Disposal Method meaning clear canvas to background color.
   * The area used by the graphic must be restored to the background color.</i></p>
   */
  static final int DISPOSAL_BACKGROUND = 2;
  /**
   * WEBP Disposal Method meaning clear canvas to frame before last.
   * The decoder is required to restore the area overwritten by the graphic
   * with what was there prior to rendering the graphic.</i></p>
   */
  static final int DISPOSAL_PREVIOUS = 3;

  /**
   * <i>Indicates the way in which the graphic is to be treated after being displayed.</i></p>
   * Disposal methods 0-3 are defined, 4-7 are reserved for future use.
   *
   * @see #DISPOSAL_UNSPECIFIED
   * @see #DISPOSAL_NONE
   * @see #DISPOSAL_BACKGROUND
   * @see #DISPOSAL_PREVIOUS
   */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {DISPOSAL_UNSPECIFIED, DISPOSAL_NONE, DISPOSAL_BACKGROUND, DISPOSAL_PREVIOUS})
  private @interface WebpDisposalMethod {
  }

  /**
   * size, position
   */
  int ix, iy, iw, ih;
  /**
   * Control Flag.
   */
  boolean transparency;
  /**
   * Disposal Method.
   */
  @WebpDisposalMethod
  int dispose;
  /**
   * Transparency Index.
   */
  int transIndex;
  /**
   * Delay, in milliseconds, to next frame.
   */
  int delay;
  /**
   * Index in the raw buffer where we need to start reading to decode.
   */
  int bufferFrameStart;
}
