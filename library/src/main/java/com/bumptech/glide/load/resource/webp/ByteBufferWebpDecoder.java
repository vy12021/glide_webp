package com.bumptech.glide.load.resource.webp;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.ImageHeaderParser;
import com.bumptech.glide.load.ImageHeaderParser.ImageType;
import com.bumptech.glide.load.ImageHeaderParserUtils;
import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.bitmap_recycle.ArrayPool;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.UnitTransformation;
import com.bumptech.glide.util.LogTime;
import com.bumptech.glide.util.Util;
import com.bumptech.glide.webpdecoder.StandardWebpDecoder;
import com.bumptech.glide.webpdecoder.WebpDecoder;
import com.bumptech.glide.webpdecoder.WebpHeader;
import com.bumptech.glide.webpdecoder.WebpHeaderParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Queue;

/**
 * An {@link ResourceDecoder} that decodes {@link
 * WebpDrawable} from {@link java.io.InputStream} data.
 */
public class ByteBufferWebpDecoder implements ResourceDecoder<ByteBuffer, WebpDrawable> {

  private static final String TAG = "ByteBufferWebpDecoder";
  private static final WebpDecoderFactory WEBP_DECODER_FACTORY = new WebpDecoderFactory();
  private static final WebpHeaderParserPool PARSER_POOL = new WebpHeaderParserPool();

  private final Context context;
  private final List<ImageHeaderParser> parsers;
  private final WebpHeaderParserPool parserPool;
  private final WebpDecoderFactory webpDecoderFactory;
  private final WebpBitmapProvider provider;

  // Public API.
  @SuppressWarnings("unused")
  public ByteBufferWebpDecoder(Context context) {
    this(context, Glide.get(context).getRegistry().getImageHeaderParsers(),
        Glide.get(context).getBitmapPool(), Glide.get(context).getArrayPool());
  }

  public ByteBufferWebpDecoder(
      Context context, List<ImageHeaderParser> parsers, BitmapPool bitmapPool,
      ArrayPool arrayPool) {
    this(context, parsers, bitmapPool, arrayPool, PARSER_POOL, WEBP_DECODER_FACTORY);
  }

  @VisibleForTesting
  ByteBufferWebpDecoder(
      Context context,
      List<ImageHeaderParser> parsers,
      BitmapPool bitmapPool,
      ArrayPool arrayPool,
      WebpHeaderParserPool parserPool,
      WebpDecoderFactory webpDecoderFactory) {
    this.context = context.getApplicationContext();
    this.parsers = parsers;
    this.webpDecoderFactory = webpDecoderFactory;
    this.provider = new WebpBitmapProvider(bitmapPool, arrayPool);
    this.parserPool = parserPool;
  }

  @Override
  public boolean handles(@NonNull ByteBuffer source, @NonNull Options options) throws IOException {
    ImageType type;
    return !options.get(WebpOptions.DISABLE_ANIMATION)
        && ((type = ImageHeaderParserUtils.getType(parsers, source))
            == ImageType.WEBP || type == ImageType.WEBP_A);
  }

  @Override
  public WebpDrawableResource decode(@NonNull ByteBuffer source, int width, int height,
                                     @NonNull Options options) {
    if (!source.isDirect()) {
      source.mark();
      source.position(0);
      ByteBuffer oldSource = source;
      source = ByteBuffer.allocateDirect(source.capacity());
      source = source.put(oldSource).asReadOnlyBuffer();
      oldSource.reset();
      source.position(oldSource.position());
    }
    final WebpHeaderParser parser = parserPool.obtain(source);
    try {
      return decode(source, width, height, parser, options);
    } finally {
      parserPool.release(parser);
    }
  }

  @Nullable
  private WebpDrawableResource decode(
      ByteBuffer byteBuffer, int width, int height, WebpHeaderParser parser, Options options) {
    long startTime = LogTime.getLogTime();
    try {
      final WebpHeader header = parser.parseHeader();
      if (header.getNumFrames() <= 0 || header.getStatus() != WebpDecoder.STATUS_OK) {
        // If we couldn't decode the WEBP, we will end up with a frame count of 0.
        return null;
      }

      Bitmap.Config config = options.get(WebpOptions.DECODE_FORMAT) == DecodeFormat.PREFER_RGB_565
          ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;

      int sampleSize = getSampleSize(header, width, height);
      WebpDecoder webpDecoder = webpDecoderFactory.build(provider, header, byteBuffer, sampleSize);
      webpDecoder.setDefaultBitmapConfig(config);
      webpDecoder.advance();
      Bitmap firstFrame = webpDecoder.getNextFrame();
      if (firstFrame == null) {
        return null;
      }

      Transformation<Bitmap> unitTransformation = UnitTransformation.get();

      WebpDrawable webpDrawable =
          new WebpDrawable(context, webpDecoder, unitTransformation, width, height, firstFrame);

      return new WebpDrawableResource(webpDrawable);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    } finally {
      if (Log.isLoggable(TAG, Log.VERBOSE)) {
        Log.v(TAG, "Decoded WEBP from stream in " + LogTime.getElapsedMillis(startTime));
      }
    }
  }

  private static int getSampleSize(WebpHeader webpHeader, int targetWidth, int targetHeight) {
    int exactSampleSize = Math.min(webpHeader.getHeight() / targetHeight,
        webpHeader.getWidth() / targetWidth);
    int powerOfTwoSampleSize = exactSampleSize == 0 ? 0 : Integer.highestOneBit(exactSampleSize);
    // Although functionally equivalent to 0 for BitmapFactory, 1 is a safer default for our code
    // than 0.
    int sampleSize = Math.max(1, powerOfTwoSampleSize);
    if (Log.isLoggable(TAG, Log.VERBOSE) && sampleSize > 1) {
      Log.v(TAG, "Downsampling WEBP"
          + ", sampleSize: " + sampleSize
          + ", target dimens: [" + targetWidth + "x" + targetHeight + "]"
          + ", actual dimens: [" + webpHeader.getWidth() + "x" + webpHeader.getHeight() + "]");
    }
    return sampleSize;
  }

  @VisibleForTesting
  static class WebpDecoderFactory {
    WebpDecoder build(WebpDecoder.BitmapProvider provider, WebpHeader header,
                      ByteBuffer buffer, int sampleSize) {
      return new StandardWebpDecoder(provider, header, buffer, sampleSize);
    }
  }

  @VisibleForTesting
  static class WebpHeaderParserPool {
    private final Queue<WebpHeaderParser> pool = Util.createQueue(0);

    synchronized WebpHeaderParser obtain(ByteBuffer buffer) {
      WebpHeaderParser result = pool.poll();
      if (result == null) {
        result = new WebpHeaderParser();
      }
      return result.setData(buffer);
    }

    synchronized void release(WebpHeaderParser parser) {
      parser.clear();
      pool.offer(parser);
    }
  }
}
