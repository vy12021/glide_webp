package com.bumptech.glide.load.resource.webp;

import android.support.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.ImageHeaderParser.ImageType;
import com.bumptech.glide.load.ImageHeaderParserUtils;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * A relatively inefficient decoder for {@link WebpDrawable}
 * that converts {@link InputStream}s to {@link ByteBuffer}s and then passes
 * the buffer to a wrapped decoder.
 */
public class StreamWebpDecoder implements ResourceDecoder<InputStream, WebpDrawable> {
  private static final String TAG = "StreamWebpDecoder";

  private final List<ImageHeaderParser> parsers;
  private final ResourceDecoder<ByteBuffer, WebpDrawable> byteBufferDecoder;
  private final ArrayPool byteArrayPool;

  public StreamWebpDecoder(List<ImageHeaderParser> parsers, ResourceDecoder<ByteBuffer,
          WebpDrawable> byteBufferDecoder, ArrayPool byteArrayPool) {
    this.parsers = parsers;
    this.byteBufferDecoder = byteBufferDecoder;
    this.byteArrayPool = byteArrayPool;
  }

  @Override
  public boolean handles(@NonNull InputStream source, @NonNull Options options) throws IOException {
    return !options.get(WebpOptions.DISABLE_ANIMATION)
        && ImageHeaderParserUtils.getType(parsers, source, byteArrayPool) == ImageType.WEBP;
  }

  @Override
  public Resource<WebpDrawable> decode(@NonNull InputStream source, int width, int height,
                                      @NonNull Options options) throws IOException {
    byte[] data = inputStreamToBytes(source);
    if (data == null) {
      return null;
    }
    ByteBuffer byteBuffer = ByteBuffer.wrap(data);
    return byteBufferDecoder.decode(byteBuffer, width, height, options);
  }

  private static byte[] inputStreamToBytes(InputStream is) {
    final int bufferSize = 16384;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream(bufferSize);
    try {
      int nRead;
      byte[] data = new byte[bufferSize];
      while ((nRead = is.read(data)) != -1) {
        buffer.write(data, 0, nRead);
      }
      buffer.flush();
    } catch (IOException e) {
      if (Log.isLoggable(TAG, Log.WARN)) {
        Log.w(TAG, "Error reading data from stream", e);
      }
      return null;
    }
    return buffer.toByteArray();
  }
}
