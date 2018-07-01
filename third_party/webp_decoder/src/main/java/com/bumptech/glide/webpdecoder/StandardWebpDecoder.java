package com.bumptech.glide.webpdecoder;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.webp.libwebp;
import com.google.webp.libwebpJNI;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.bumptech.glide.webpdecoder.WebpFrame.DISPOSAL_NONE;
import static com.bumptech.glide.webpdecoder.WebpFrame.DISPOSAL_PREVIOUS;
import static com.bumptech.glide.webpdecoder.WebpFrame.DISPOSAL_UNSPECIFIED;

/**
 * Reads frame data from a WEBP image source and decodes it into individual frames for animation
 * purposes.  Image data can be read from either and InputStream source or a byte[].
 *
 * <p>This class is optimized for running animations with the frames, there are no methods to get
 * individual frame images, only to decode the next frame in the animation sequence.  Instead, it
 * lowers its memory footprint by only housing the minimum data necessary to decode the next frame
 * in the animation sequence.
 *
 * <p>The animation must be manually moved forward using {@link #advance()} before requesting the
 * next frame.  This method must also be called before you request the first frame or an error
 * will occur.
 *
 * <p>Implementation adapted from sample code published in Lyons. (2004). <em>Java for
 * Programmers</em>, republished under the MIT Open Source License
 */
public class StandardWebpDecoder implements WebpDecoder {
  private static final String TAG = StandardWebpDecoder.class.getSimpleName();

  /** Maximum pixel stack size for decoding LZW compressed data. */
  private static final int MAX_STACK_SIZE = 4 * 1024;

  private static final int NULL_CODE = -1;

  private static final int INITIAL_FRAME_POINTER = -1;

  private static final int BYTES_PER_INTEGER = Integer.SIZE / 8;

  @ColorInt
  private static final int COLOR_TRANSPARENT_BLACK = 0x00000000;

  /** Private color table that can be modified if needed. */
  @ColorInt
  private final int[] pct = new int[256];

  private final WebpDecoder.BitmapProvider bitmapProvider;

  /** Raw WEBP data from input source. */
  private ByteBuffer rawData;

  /** Raw data read working array. */
  private byte[] block;

  private WebpHeaderParser parser;

  private byte[] mainPixels;
  @ColorInt
  private int[] mainScratch;

  private int framePointer;
  private WebpHeader header;
  private Bitmap previousImage;
  private boolean savePrevious;
  @WebpDecodeStatus
  private int status;
  private int sampleSize;
  private int downsampledHeight;
  private int downsampledWidth;
  @Nullable
  private Boolean isFirstFrameTransparent;
  @NonNull
  private Bitmap.Config bitmapConfig = Config.ARGB_8888;

  // Public API.
  @SuppressWarnings("unused")
  public StandardWebpDecoder(@NonNull WebpDecoder.BitmapProvider provider,
                             WebpHeader webpHeader, ByteBuffer rawData) {
    this(provider, webpHeader, rawData, 1);
    com.google.webp.libwebp libwebp = new libwebp();
    com.google.webp.libwebpJNI libwebpJNI = new libwebpJNI();
    // com.google.webp.libwebp.WebPGetInfo()
  }

  public StandardWebpDecoder(@NonNull WebpDecoder.BitmapProvider provider,
                             WebpHeader webpHeader, ByteBuffer rawData, int sampleSize) {
    this(provider);
    setData(webpHeader, rawData, sampleSize);
  }

  public StandardWebpDecoder(@NonNull WebpDecoder.BitmapProvider provider) {
    this.bitmapProvider = provider;
    this.header = new WebpHeader();
  }

  @Override
  public int getWidth() {
    return header.width;
  }

  @Override
  public int getHeight() {
    return header.height;
  }

  @NonNull
  @Override
  public ByteBuffer getData() {
    return rawData;
  }

  @Override
  public int getStatus() {
    return status;
  }

  @Override
  public void advance() {
    framePointer = (framePointer + 1) % header.frameCount;
  }

  @Override
  public int getDelay(int index) {
    int delay = -1;
    if (index >= 0 && index < header.frameCount) {
      delay = header.frames.get(index).delay;
    }
    return delay;
  }

  @Override
  public int getNextDelay() {
    if (header.frameCount <= 0 || framePointer < 0) {
      return 0;
    }
    return getDelay(framePointer);
  }

  @Override
  public int getFrameCount() {
    return header.frameCount;
  }

  @Override
  public int getCurrentFrameIndex() {
    return framePointer;
  }

  @Override
  public void resetFrameIndex() {
    framePointer = INITIAL_FRAME_POINTER;
  }

  @Deprecated
  @Override
  public int getLoopCount() {
    if (header.loopCount == WebpHeader.NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST) {
      return 1;
    }
    return header.loopCount;
  }

  @Override
  public int getNetscapeLoopCount() {
    return header.loopCount;
  }

  @Override
  public int getTotalIterationCount() {
    if (header.loopCount == WebpHeader.NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST) {
      return 1;
    }
    if (header.loopCount == WebpHeader.NETSCAPE_LOOP_COUNT_FOREVER) {
      return TOTAL_ITERATION_COUNT_FOREVER;
    }
    return header.loopCount + 1;
  }

  @Override
  public int getByteSize() {
    return rawData.limit() + mainPixels.length + (mainScratch.length * BYTES_PER_INTEGER);
  }

  @Nullable
  @Override
  public synchronized Bitmap getNextFrame() {
    if (header.frameCount <= 0 || framePointer < 0) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Unable to decode frame"
            + ", frameCount=" + header.frameCount
            + ", framePointer=" + framePointer
        );
      }
      status = STATUS_FORMAT_ERROR;
    }
    if (status == STATUS_FORMAT_ERROR || status == STATUS_OPEN_ERROR) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Unable to decode frame, status=" + status);
      }
      return null;
    }
    status = STATUS_OK;

    if (block == null) {
      block = bitmapProvider.obtainByteArray(255);
    }

    WebpFrame currentFrame = header.frames.get(framePointer);
    WebpFrame previousFrame = null;
    int previousIndex = framePointer - 1;
    if (previousIndex >= 0) {
      previousFrame = header.frames.get(previousIndex);
    }
    // Transfer pixel data to image.
    return setPixels(currentFrame, previousFrame);
  }

  @Override
  public int read(@Nullable InputStream is, int contentLength) {
    if (is != null) {
      try {
        int capacity = (contentLength > 0) ? (contentLength + 4 * 1024) : 16 * 1024;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(capacity);
        int nRead;
        byte[] data = new byte[16 * 1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, nRead);
        }
        buffer.flush();

        read(buffer.toByteArray());
      } catch (IOException e) {
        Log.w(TAG, "Error reading data from stream", e);
      }
    } else {
      status = STATUS_OPEN_ERROR;
    }

    try {
      if (is != null) {
        is.close();
      }
    } catch (IOException e) {
      Log.w(TAG, "Error closing stream", e);
    }

    return status;
  }

  @Override
  public void clear() {
    header = null;
    if (mainPixels != null) {
      bitmapProvider.release(mainPixels);
    }
    if (mainScratch != null) {
      bitmapProvider.release(mainScratch);
    }
    if (previousImage != null) {
      bitmapProvider.release(previousImage);
    }
    previousImage = null;
    rawData = null;
    isFirstFrameTransparent = null;
    if (block != null) {
      bitmapProvider.release(block);
    }
  }

  @Override
  public synchronized void setData(@NonNull WebpHeader header, @NonNull byte[] data) {
    setData(header, ByteBuffer.wrap(data));
  }

  @Override
  public synchronized void setData(@NonNull WebpHeader header, @NonNull ByteBuffer buffer) {
    setData(header, buffer, 1);
  }

  @Override
  public synchronized void setData(@NonNull WebpHeader header, @NonNull ByteBuffer buffer,
      int sampleSize) {
    if (sampleSize <= 0) {
      throw new IllegalArgumentException("Sample size must be >0, not: " + sampleSize);
    }
    // Make sure sample size is a power of 2.
    sampleSize = Integer.highestOneBit(sampleSize);
    this.status = STATUS_OK;
    this.header = header;
    framePointer = INITIAL_FRAME_POINTER;
    // Initialize the raw data buffer.
    rawData = buffer.asReadOnlyBuffer();
    rawData.position(0);
    rawData.order(ByteOrder.LITTLE_ENDIAN);

    // No point in specially saving an old frame if we're never going to use it.
    savePrevious = false;
    for (WebpFrame frame : header.frames) {
      if (frame.dispose == DISPOSAL_PREVIOUS) {
        savePrevious = true;
        break;
      }
    }

    this.sampleSize = sampleSize;
    downsampledWidth = header.width / sampleSize;
    downsampledHeight = header.height / sampleSize;
    // Now that we know the size, init scratch arrays.
    // TODO Find a way to avoid this entirely or at least downsample it (either should be possible).
    mainPixels = bitmapProvider.obtainByteArray(header.width * header.height);
    mainScratch = bitmapProvider.obtainIntArray(downsampledWidth * downsampledHeight);
  }

  @NonNull
  private WebpHeaderParser getHeaderParser() {
    if (parser == null) {
      parser = new WebpHeaderParser();
    }
    return parser;
  }

  @Override
  @WebpDecodeStatus
  public synchronized int read(@Nullable byte[] data) {
    this.header = getHeaderParser().setData(data).parseHeader();
    if (data != null) {
      setData(header, data);
    }
    return status;
  }

  @Override
  public void setDefaultBitmapConfig(@NonNull Bitmap.Config config) {
    if (config != Bitmap.Config.ARGB_8888 && config != Bitmap.Config.RGB_565) {
      throw new IllegalArgumentException("Unsupported format: " + config
          + ", must be one of " + Bitmap.Config.ARGB_8888 + " or " + Bitmap.Config.RGB_565);
    }
    bitmapConfig = config;
  }

  /**
   * Creates new frame image from current data (and previous frames as specified by their
   * disposition codes).
   */
  private Bitmap setPixels(WebpFrame currentFrame, WebpFrame previousFrame) {
    // Final location of blended pixels.
    final int[] dest = mainScratch;

    // clear all pixels when meet first frame and drop prev image from last loop
    if (previousFrame == null) {
      if (previousImage != null) {
        bitmapProvider.release(previousImage);
      }
      previousImage = null;
      Arrays.fill(dest, COLOR_TRANSPARENT_BLACK);
    }

    // Decode pixels for this frame into the global pixels[] scratch.
    decodeBitmapData(currentFrame);
    // Copy pixels into previous image
    if (savePrevious && (currentFrame.dispose == DISPOSAL_UNSPECIFIED
        || currentFrame.dispose == DISPOSAL_NONE)) {
      if (previousImage == null) {
        previousImage = getNextBitmap();
      }
      previousImage.setPixels(dest, 0, downsampledWidth, 0, 0, downsampledWidth,
          downsampledHeight);
    }
    // Set pixels for current image.
    Bitmap result = getNextBitmap();
    result.setPixels(dest, 0, downsampledWidth, 0, 0, downsampledWidth, downsampledHeight);
    return result;
  }

  /**
   * Decodes LZW image data into pixel array. Adapted from John Cristy's BitmapMagick.
   */
  private void decodeBitmapData(WebpFrame frame) {
    if (frame != null) {
      // Jump to the frame start position.
      rawData.position(frame.bufferFrameStart);
    }
    int npix = (frame == null) ? header.width * header.height : frame.iw * frame.ih;
  }

  private Bitmap getNextBitmap() {
    Bitmap.Config config = isFirstFrameTransparent == null || isFirstFrameTransparent
        ? Bitmap.Config.ARGB_8888 : bitmapConfig;
    Bitmap result = bitmapProvider.obtain(downsampledWidth, downsampledHeight, config);
    result.setHasAlpha(true);
    return result;
  }

  static {
    System.loadLibrary("webpparser");
  }

  public native static WebpHeader getWebpInfo(String file);

}
