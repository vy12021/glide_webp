package com.bumptech.glide.load.resource.webp;

import androidx.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.load.EncodeStrategy;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceEncoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.util.ByteBufferUtil;

import java.io.File;
import java.io.IOException;

/**
 * Writes the original bytes of a {@link WebpDrawable} to an
 * {@link java.io.OutputStream}.
 */
public class WebpDrawableEncoder implements ResourceEncoder<WebpDrawable> {
  private static final String TAG = "WebpDrawableEncoder";

  @NonNull
  @Override
  public EncodeStrategy getEncodeStrategy(@NonNull Options options) {
    return EncodeStrategy.SOURCE;
  }

  @Override
  public boolean encode(@NonNull Resource<WebpDrawable> data, @NonNull File file,
                        @NonNull Options options) {
    WebpDrawable drawable = data.get();
    boolean success = false;
    try {
      ByteBufferUtil.toFile(drawable.getBuffer(), file);
      success = true;
    } catch (IOException e) {
      if (Log.isLoggable(TAG, Log.WARN)) {
        Log.w(TAG, "Failed to encode WEBP drawable data", e);
      }
    }
    return success;
  }
}
