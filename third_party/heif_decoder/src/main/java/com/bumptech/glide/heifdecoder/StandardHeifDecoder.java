package com.bumptech.glide.heifdecoder;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.support.annotation.ColorInt;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Reads frame data from a WEBP image source and decodes it into individual frames for animation
 * purposes.  Image data can be read from either and InputStream source or a byte[].
 * <p>
 * <p>This class is optimized for running animations with the frames, there are no methods to get
 * individual frame images, only to decode the next frame in the animation sequence.  Instead, it
 * lowers its memory footprint by only housing the minimum data necessary to decode the next frame
 * in the animation sequence.
 * <p>
 * <p>The animation must be manually moved forward using {@link #advance()} before requesting the
 * next frame.  This method must also be called before you request the first frame or an error
 * will occur.
 */
public class StandardHeifDecoder implements HeifDecoder {
  private static final String TAG = StandardHeifDecoder.class.getSimpleName();

  private static final int INITIAL_FRAME_POINTER = -1;

  private static final int BYTES_PER_INTEGER = Integer.SIZE / 8;

  @ColorInt
  private static final int COLOR_TRANSPARENT_BLACK = 0x00000000;

  private final HeifDecoder.BitmapProvider bitmapProvider;

  /**
   * Raw WEBP data from input source.
   */
  private ByteBuffer rawData;
  /**
   * Webp header parser.
   */
  private HeifHeaderParser parser;
  /**
   * Bitmap main pixels array
   */
  private int[] scratchPixels;
  /**
   * Base frames Bitmap
   */
  private Bitmap scratchBitmap;
  /**
   * Base canvas for scratch frames blend to {@link #scratchBitmap}.
   */
  private Canvas scratchCanvas;
  /**
   * Rects for blend frames to {@link #scratchCanvas}.
   */
  private Rect src = new Rect(), dst = new Rect();
  /**
   * Current frame index;
   */
  @IntRange(from = 0)
  private int framePointer;
  /**
   * Parsed Webp header.
   */
  private HeifHeader header;
  /**
   * Decode status;
   */
  @WebpDecodeStatus
  private int status;
  private int sampleSize;
  private int downsampledHeight;
  private int downsampledWidth;
  @NonNull
  private Bitmap.Config bitmapConfig = Config.ARGB_8888;

  // Public API.
  @SuppressWarnings("unused")
  public StandardHeifDecoder(@NonNull HeifDecoder.BitmapProvider provider,
                             HeifHeader heifHeader, ByteBuffer byteBuffer) {
    this(provider, heifHeader, byteBuffer, 1);
  }

  public StandardHeifDecoder(@NonNull HeifDecoder.BitmapProvider provider,
                             HeifHeader heifHeader, ByteBuffer byteBuffer, int sampleSize) {
    this(provider);
    setData(heifHeader, byteBuffer, sampleSize);
  }

  public StandardHeifDecoder(@NonNull HeifDecoder.BitmapProvider provider) {
    this.bitmapProvider = provider;
    this.header = new HeifHeader();
  }

  @Override
  public int getWidth() {
    return header.getWidth();
  }

  @Override
  public int getHeight() {
    return header.getHeight();
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
  public int getDuration(@IntRange(from = 0) int index) {
    int delay = -1;
    if (index >= 0 && index < header.frameCount) {
      delay = header.frames.get(index).duration;
    }
    return delay;
  }

  @Override
  public int getNextDelay() {
    if (header.frameCount <= 0 || framePointer < 0) {
      return 0;
    }
    return getDuration(framePointer);
  }

  @Override
  public int getFrameCount() {
    return header.frameCount;
  }

  @Override
  @IntRange(from = 0)
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
    if (header.loopCount == HeifHeader.NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST) {
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
    if (header.loopCount == HeifHeader.NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST) {
      return 1;
    }
    if (header.loopCount == HeifHeader.NETSCAPE_LOOP_COUNT_FOREVER) {
      return TOTAL_ITERATION_COUNT_FOREVER;
    }
    return header.loopCount + 1;
  }

  @Override
  public int getByteSize() {
    return rawData.limit() + bitmapProvider.getSize(scratchBitmap) +
            (scratchPixels.length * BYTES_PER_INTEGER);
  }

  @Nullable
  @Override
  public synchronized Bitmap getNextFrame() {
    if (header.frameCount <= 0 || framePointer < 0) {
      loge("Unable to decode frame"
              + ", frameCount=" + header.frameCount
              + ", framePointer=" + framePointer
      );
      status = STATUS_PARSE_ERROR;
    }
    if (status == STATUS_PARSE_ERROR || status == STATUS_OPEN_ERROR) {
      loge("Unable to decode frame, status=" + status);
      return null;
    }
    status = STATUS_OK;

    HeifFrame currentFrame = header.frames.get(framePointer);
    HeifFrame previousFrame = null;
    int previousIndex = framePointer - 1;
    if (previousIndex >= 0) {
      previousFrame = header.frames.get(previousIndex);
    }
    HeifFrame nextFrame = null;
    if (framePointer < getFrameCount() - 1) {
      nextFrame = header.frames.get(framePointer + 1);
    }
    // Transfer pixel data to image.
    return setPixels(currentFrame, previousFrame, nextFrame);
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
        logw("Error reading data from stream" + e.getLocalizedMessage());
      }
    } else {
      status = STATUS_OPEN_ERROR;
    }

    try {
      if (is != null) {
        is.close();
      }
    } catch (IOException e) {
      logw("Error closing stream: " + e.getLocalizedMessage());
    }

    return status;
  }

  @Override
  public void clear() {
    if (null != scratchPixels) {
      bitmapProvider.release(scratchPixels);
    }
    scratchPixels = null;
    if (scratchBitmap != null) {
      bitmapProvider.release(scratchBitmap);
    }
    scratchBitmap = null;
    scratchCanvas.setBitmap(null);
    scratchCanvas = null;
    loge("nativeReleaseParser: " + nativeHeifParserPointer);
    nativeReleaseParser(nativeHeifParserPointer);
    nativeHeifParserPointer = 0;
    rawData.clear();
    rawData = null;
    parser = null;
    header = null;
  }

  @Override
  public synchronized void setData(@NonNull HeifHeader header, @NonNull ByteBuffer byteBuffer) {
    setData(header, byteBuffer, 1);
  }

  @Override
  public synchronized void setData(@NonNull HeifHeader header,
                                   @NonNull ByteBuffer byteBuffer, int sampleSize) {
    if (sampleSize <= 0) {
      throw new IllegalArgumentException("Sample size must be >0, not: " + sampleSize);
    }
    // Make sure sample size is a power of 2.
    sampleSize = Integer.highestOneBit(sampleSize);
    this.status = STATUS_OK;
    this.header = header;
    framePointer = INITIAL_FRAME_POINTER;
    // Initialize the raw data buffer.
    rawData = byteBuffer;
    rawData.position(0);
    rawData.order(ByteOrder.LITTLE_ENDIAN);
    if (0 == (this.nativeHeifParserPointer = nativeInitHeifParser(rawData))) {
      throw new RuntimeException("nativeInitWebpParser failed");
    }
    Log.d(TAG, "nativeInitWebpParser: " + nativeHeifParserPointer);
    // No point in specially saving an old frame if we're never going to use it.
    boolean savePrevious = false;
    this.sampleSize = sampleSize;
    downsampledWidth = header.getWidth() / this.sampleSize;
    downsampledHeight = header.getHeight() / this.sampleSize;
    scratchPixels = bitmapProvider.obtainIntArray(downsampledWidth * downsampledHeight);
    if (savePrevious) {
      scratchBitmap = getNextBitmap();
      scratchCanvas = new Canvas(scratchBitmap);
    }
  }

  @NonNull
  private HeifHeaderParser getHeaderParser() {
    if (parser == null) {
      parser = new HeifHeaderParser();
    }
    return parser;
  }

  @Override
  @WebpDecodeStatus
  public synchronized int read(@Nullable byte[] data) {
    this.header = getHeaderParser().setData(data).parseHeader();
    if (data != null) {
      setData(header, getHeaderParser().getRawData());
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
  private Bitmap setPixels(HeifFrame currentFrame, HeifFrame previousFrame, HeifFrame nextFrame) {
    // Clear all pixels when meet first frame and drop prev image from last loop
    if (previousFrame == null) {
      Arrays.fill(scratchPixels, COLOR_TRANSPARENT_BLACK);
      scratchCanvas.drawColor(COLOR_TRANSPARENT_BLACK, PorterDuff.Mode.CLEAR);
    }

    Bitmap result = getNextBitmap();
    long before = System.nanoTime();
    nativeGetHeifFrame(this.nativeHeifParserPointer, result, getCurrentFrameIndex() + 1);
    logw("nativeGetWebpFrame cost: " + ((System.nanoTime() - before) / 1000000f) + " ms");

    if (((null != previousFrame && previousFrame.dispose == HeifFrame.DISPOSAL_BACKGROUND) ||
            currentFrame.blend == HeifFrame.BLEND_NONE)) {
      int windowX, windowY;
      int frameW, frameH;
      if (null != previousFrame && previousFrame.dispose == HeifFrame.DISPOSAL_BACKGROUND) {
        // Clear the previous frame rectangle.
        windowX = previousFrame.offsetX;
        windowY = previousFrame.offsetY;
        frameW = previousFrame.width;
        frameH = previousFrame.height;
      } else {  // curr->blend_method == WEBP_MUX_NO_BLEND.
        // We simulate no-blending behavior by first clearing the current frame
        // rectangle (to a checker-board) and then alpha-blending against it.
        windowX = currentFrame.offsetX;
        windowY = currentFrame.offsetY;
        frameW = currentFrame.width;
        frameH = currentFrame.height;
      }
      // Only update the requested area, not the whole canvas.
      scratchCanvas.setBitmap(scratchBitmap);
      // scratchCanvas.drawColor(this.header.bgColor, PorterDuff.Mode.CLEAR);
      scratchCanvas.clipRect(windowX / sampleSize, windowY / sampleSize,
              (windowX + frameW) / sampleSize, (windowY + frameH) / sampleSize);
      scratchCanvas.drawBitmap(result, 0, 0, null);
    } else {
      src.set(0, 0, result.getWidth(), result.getHeight());
      dst.set(currentFrame.offsetX / sampleSize, currentFrame.offsetY / sampleSize,
              (currentFrame.offsetX + currentFrame.width) / sampleSize,
              (currentFrame.offsetY + currentFrame.height) / sampleSize);
      scratchCanvas.drawBitmap(result, src, dst, null);
      scratchBitmap.getPixels(scratchPixels, 0, downsampledWidth,
              0, 0, downsampledWidth, downsampledHeight);
      result.setPixels(scratchPixels, 0, downsampledWidth,
              0, 0, downsampledWidth, downsampledHeight);
    }

    return result;
  }

  private Bitmap getNextBitmap() {
    Bitmap.Config config = header.hasAlpha ? Bitmap.Config.ARGB_8888 : bitmapConfig;
    Bitmap result = bitmapProvider.obtain(downsampledWidth, downsampledHeight, config);
    result.setHasAlpha(true);
    return result;
  }

  private void loge(String msg) {
    if (Log.isLoggable(TAG, Log.ERROR)) {
      Log.e(TAG, msg);
    }
  }

  private void logw(String msg) {
    if (Log.isLoggable(TAG, Log.WARN)) {
      Log.w(TAG, msg);
    }
  }

  static {
    System.loadLibrary("webpparser");
  }

  private long nativeHeifParserPointer;

  private native static long nativeInitHeifParser(ByteBuffer buffer);

  private native static int nativeGetHeifFrame(long nativeHeifParserPointer,
                                               @NonNull Bitmap dst, @IntRange(from = 1) int index);

  private native static void nativeReleaseParser(long nativeHeifParserPointer);

}
