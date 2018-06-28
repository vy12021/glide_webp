package com.bumptech.glide.load.resource.webp;

import android.graphics.Bitmap;
import android.support.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;
import com.bumptech.glide.webpdecoder.WebpDecoder;

/**
 * Decodes {@link Bitmap}s from {@link WebpDecoder}s representing a particular frame of a particular
 * WEBP image.
 */
public final class WebpFrameResourceDecoder implements ResourceDecoder<WebpDecoder, Bitmap> {
  private final BitmapPool bitmapPool;

  public WebpFrameResourceDecoder(BitmapPool bitmapPool) {
    this.bitmapPool = bitmapPool;
  }

  @Override
  public boolean handles(@NonNull WebpDecoder source, @NonNull Options options) {
    return true;
  }

  @Override
  public Resource<Bitmap> decode(@NonNull WebpDecoder source, int width, int height,
      @NonNull Options options) {
    Bitmap bitmap = source.getNextFrame();
    return BitmapResource.obtain(bitmap, bitmapPool);
  }
}
