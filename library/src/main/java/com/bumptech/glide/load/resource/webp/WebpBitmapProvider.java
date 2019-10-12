package com.bumptech.glide.load.resource.webp;


import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.gifdecoder.GifDecoder;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.util.Util;
import com.bumptech.glide.webpdecoder.WebpDecoder;

import java.nio.ByteBuffer;

/**
 * Implements {@link WebpDecoder.BitmapProvider} by wrapping Glide's
 * {@link BitmapPool}.
 */
public final class WebpBitmapProvider implements WebpDecoder.BitmapProvider {
  private final BitmapPool bitmapPool;
  @Nullable private final ArrayPool arrayPool;

  /**
   * Constructs an instance without a shared byte array pool. Byte arrays will be always constructed
   * when requested.
   */
  public WebpBitmapProvider(BitmapPool bitmapPool) {
    this(bitmapPool, /*arrayPool=*/ null);
  }

  /**
   * Constructs an instance with a shared array pool. Arrays will be reused where
   * possible.
   */
  // Public API.
  @SuppressWarnings("WeakerAccess")
  public WebpBitmapProvider(BitmapPool bitmapPool, @Nullable ArrayPool arrayPool) {
    this.bitmapPool = bitmapPool;
    this.arrayPool = arrayPool;
  }

  @NonNull
  @Override
  public Bitmap obtain(int width, int height, @NonNull Bitmap.Config config) {
    return bitmapPool.getDirty(width, height, config);
  }

  @Override
  public int getSize(@NonNull Bitmap bitmap) {
    return Util.getBitmapByteSize(bitmap);
  }

  @Override
  public void release(@NonNull Bitmap bitmap) {
    bitmapPool.put(bitmap);
  }

  @NonNull
  @Override
  public byte[] obtainByteArray(int size) {
    if (arrayPool == null) {
      return new byte[size];
    }
    return arrayPool.get(size, byte[].class);
  }

  @Override
  public void release(@NonNull byte[] bytes) {
    if (arrayPool == null) {
      return;
    }
    arrayPool.put(bytes);
  }

  @NonNull
  @Override
  public int[] obtainIntArray(int size) {
    if (arrayPool == null) {
      return new int[size];
    }
    return arrayPool.get(size, int[].class);
  }

  @SuppressWarnings("PMD.UseVarargs")
  @Override
  public void release(@NonNull int[] array) {
    if (arrayPool == null) {
      return;
    }
    arrayPool.put(array);
  }
}
