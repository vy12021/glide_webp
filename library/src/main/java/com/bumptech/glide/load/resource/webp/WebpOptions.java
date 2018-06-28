package com.bumptech.glide.load.resource.webp;

import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.Option;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;

/**
 * Options related to decoding WEBPs.
 */
public final class WebpOptions {

  /**
   * Indicates the {@link DecodeFormat} that will be used in conjunction
   * with the particular WEBP to determine the {@link android.graphics.Bitmap.Config} to use when
   * decoding frames of WEBPs.
   */
  public static final Option<DecodeFormat> DECODE_FORMAT = Option.memory(
      "com.bumptech.glide.load.resource.webp.WebpOptions.DecodeFormat", DecodeFormat.DEFAULT);

  /**
   * If set to {@code true}, disables the WEBP {@link ResourceDecoder}s
   * ({@link ResourceDecoder#handles(Object, Options)} will return {@code false}). Defaults to
   * {@code false}.
   */
  public static final Option<Boolean> DISABLE_ANIMATION = Option.memory(
      "com.bumptech.glide.load.resource.webp.WebpOptions.DisableAnimation", false);

  private WebpOptions() {
    // Utility class.
  }
}
