package com.bumptech.glide.webpdecoder;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
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
public class StandardWebpDecoder implements WebpDecoder {
  private static final String TAG = StandardWebpDecoder.class.getSimpleName();

  private static final int INITIAL_FRAME_POINTER = -1;

  private static final int BYTES_PER_INTEGER = Integer.SIZE / 8;

  @ColorInt
  private static final int COLOR_TRANSPARENT_BLACK = 0x00000000;

  private final BitmapProvider bitmapProvider;

  /**
   * Raw WEBP data from input source.
   */
  private ByteBuffer rawData;
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
   * The webp header parsed.
   */
  private WebpHeader header;
  /**
   * Decode status
   */
  @WebpDecodeStatus
  private int status;
  private int sampleSize;
  private int downsampledHeight;
  private int downsampledWidth;
  @NonNull
  private Config bitmapConfig = Config.ARGB_8888;

  // Public API.
  @SuppressWarnings("unused")
  public StandardWebpDecoder(@NonNull BitmapProvider provider,
                             WebpHeader header, ByteBuffer byteBuffer) {
    this(provider, header, byteBuffer, 1);
  }

  public StandardWebpDecoder(@NonNull BitmapProvider provider,
                             WebpHeader header, ByteBuffer byteBuffer, int sampleSize) {
    rawData = byteBuffer;
    bitmapProvider = provider;
    setData(header, byteBuffer, sampleSize);
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
      delay = header.getFrame(index).duration;
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
    if (null != scratchBitmap) {
      return rawData.limit() + bitmapProvider.getSize(scratchBitmap) +
              (scratchPixels.length * BYTES_PER_INTEGER);
    } else {
      return rawData.limit();
    }
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

    WebpFrame currentFrame = header.getFrame(framePointer);
    WebpFrame previousFrame = null;
    int previousIndex = framePointer - 1;
    if (previousIndex >= 0) {
      previousFrame = header.getFrame(previousIndex);
    }
    WebpFrame nextFrame = null;
    if (framePointer < getFrameCount() - 1) {
      nextFrame = header.getFrame(framePointer + 1);
    }
    // Transfer pixel data to image.
    return setPixels(currentFrame, previousFrame, nextFrame);
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
    if (null != scratchCanvas) {
      scratchCanvas.setBitmap(null);
    }
    scratchCanvas = null;
    loge("nativeReleaseParser: " + nativeWebpParserPointer);
    nativeReleaseParser(nativeWebpParserPointer);
    nativeWebpParserPointer = 0;
    rawData.clear();
    rawData = null;
    header = null;
  }

  @Override
  public synchronized void setData(@NonNull WebpHeader header, @NonNull ByteBuffer byteBuffer) {
    setData(header, byteBuffer, 1);
  }

  @Override
  public synchronized void setData(@NonNull WebpHeader header,
                                   @NonNull ByteBuffer byteBuffer, int sampleSize) {
    if (sampleSize <= 0) {
      throw new IllegalArgumentException("Sample size must be >0, not: " + sampleSize);
    }
    this.header = header;
    // Make sure sample size is a power of 2.
    this.sampleSize = Integer.highestOneBit(sampleSize);
    status = STATUS_OK;
    framePointer = INITIAL_FRAME_POINTER;
    // Initialize the raw data buffer.
    if (0 == (nativeWebpParserPointer = nativeInitWebpParser(byteBuffer))) {
      throw new RuntimeException("nativeInitWebpParser failed");
    }
    // No point in specially saving an old frame if we're never going to use it.
    boolean savePrevious = false;
    WebpFrame frame;
    for (int index = 0; index < header.frameCount; index++) {
      frame = header.getFrame(index);
      if (frame.dispose == WebpFrame.DISPOSAL_BACKGROUND || frame.blend == WebpFrame.BLEND_MUX) {
        savePrevious = true;
        break;
      }
    }
    downsampledWidth = header.getWidth() / this.sampleSize;
    downsampledHeight = header.getHeight() / this.sampleSize;
    if (savePrevious) {
      scratchPixels = bitmapProvider.obtainIntArray(downsampledWidth * downsampledHeight);
      scratchBitmap = getNextBitmap();
      scratchCanvas = new Canvas(scratchBitmap);
    }
  }

  @Override
  public void setDefaultBitmapConfig(@NonNull Config config) {
    if (config != Config.ARGB_8888 && config != Config.RGB_565) {
      throw new IllegalArgumentException("Unsupported format: " + config
              + ", must be one of " + Config.ARGB_8888 + " or " + Config.RGB_565);
    }
    bitmapConfig = config;
  }

  /**
   * Creates new frame image from current data (and previous frames as specified by their
   * disposition codes).
   */
  private Bitmap setPixels(WebpFrame currentFrame, WebpFrame previousFrame, WebpFrame nextFrame) {
    // Clear all pixels when meet first frame and drop prev image from last loop
    if (null != scratchBitmap && previousFrame == null) {
      Arrays.fill(scratchPixels, COLOR_TRANSPARENT_BLACK);
      scratchCanvas.drawColor(COLOR_TRANSPARENT_BLACK, PorterDuff.Mode.CLEAR);
    }

    Bitmap result = getNextBitmap();
    long before = System.nanoTime();
    nativeGetWebpFrame(nativeWebpParserPointer, result, getCurrentFrameIndex() + 1);
    logw("nativeGetWebpFrame cost: " + ((System.nanoTime() - before) / 1000000f) + " ms");

    if (null == scratchBitmap) {
      return result;
    }

    boolean blendFrame = currentFrame.blend == WebpFrame.BLEND_MUX;
    boolean backgroundFrame = null != previousFrame
            && previousFrame.dispose == WebpFrame.DISPOSAL_BACKGROUND;
    int windowX, windowY;
    int frameW, frameH;
    if (backgroundFrame) {
      // Clear the previous frame rectangle.
      windowX = previousFrame.offsetX;
      windowY = previousFrame.offsetY;
      frameW = previousFrame.width;
      frameH = previousFrame.height;
    } else {
      // curr->blend_method == WEBP_MUX_NO_BLEND.
      // We simulate no-blending behavior by first clearing the current frame
      // rectangle (to a checker-board) and then alpha-blending against it.
      windowX = currentFrame.offsetX;
      windowY = currentFrame.offsetY;
      frameW = currentFrame.width;
      frameH = currentFrame.height;
    }
    // Only update the requested area, not the whole canvas.
    scratchCanvas.setBitmap(scratchBitmap);
    scratchCanvas.clipRect(windowX / sampleSize, windowY / sampleSize,
            (windowX + frameW) / sampleSize, (windowY + frameH) / sampleSize);
    if (backgroundFrame) {
      scratchCanvas.drawColor(header.bgColor, PorterDuff.Mode.CLEAR);
    } else if (!blendFrame) {
      scratchCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }
    src.set(0, 0, result.getWidth(), result.getHeight());
    dst.set(currentFrame.offsetX / sampleSize, currentFrame.offsetY / sampleSize,
            (currentFrame.offsetX + currentFrame.width) / sampleSize,
            (currentFrame.offsetY + currentFrame.height) / sampleSize);
    scratchCanvas.drawBitmap(result, src, dst, null);
    scratchBitmap.getPixels(scratchPixels, 0, downsampledWidth,
            0, 0, downsampledWidth, downsampledHeight);
    result.setPixels(scratchPixels, 0, downsampledWidth,
            0, 0, downsampledWidth, downsampledHeight);
    return result;
  }

  private Bitmap getNextBitmap() {
    Config config = header.hasAlpha ? Config.ARGB_8888 : bitmapConfig;
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

  private long nativeWebpParserPointer;

  native static long nativeInitWebpParser(ByteBuffer buffer);

  native static int nativeGetWebpFrame(long nativeWebpParserPointer,
                                       @NonNull Bitmap dst, @IntRange(from = 1) int index);

  native static void nativeReleaseParser(long nativeWebpParserPointer);

}
