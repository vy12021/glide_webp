package com.bumptech.glide.load.resource.transcode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.resource.bytes.BytesResource;
import com.bumptech.glide.load.resource.webp.WebpDrawable;
import com.bumptech.glide.util.ByteBufferUtil;

import java.nio.ByteBuffer;

/**
 * An {@link ResourceTranscoder} that converts {@link
 * WebpDrawable} into bytes by obtaining the original bytes of
 * the WEBP from the {@link WebpDrawable}.
 */
public class WebpDrawableBytesTranscoder implements ResourceTranscoder<WebpDrawable, byte[]> {
  @Nullable
  @Override
  public Resource<byte[]> transcode(@NonNull Resource<WebpDrawable> toTranscode,
      @NonNull Options options) {
    WebpDrawable webpDrawable = toTranscode.get();
    ByteBuffer byteBuffer = webpDrawable.getBuffer();
    return new BytesResource(ByteBufferUtil.toBytes(byteBuffer));
  }
}
