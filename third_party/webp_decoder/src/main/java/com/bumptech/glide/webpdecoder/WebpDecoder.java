package com.bumptech.glide.webpdecoder;

import android.graphics.Bitmap;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

/**
 * Shared interface for WEBP decoders.
 */
public interface WebpDecoder {

  /** File read status: No errors. */
  int STATUS_OK                 = 0;
  /** File read status: Unable to open source. */
  int STATUS_OPEN_ERROR         = 1;
  /** File read status: Error decoding file (may be partially decoded). */
  int STATUS_PARSE_ERROR        = 2;
  /** File read status: Unable to open source. */
  int STATUS_INVALID_PARAM      = 3;
  /** Unable to fully decode the current frame. */
  int STATUS_TRUNCATED_DATA     = 4;
  /** File read status: Unable to open source. */
  int STATUS_BITSTREAM_ERROR    = 5;
  /** MISS DATA */
  int STATUS_MISS_DATA          = 6;
  /**
   * need more data
   */
  int STATUS_NEED_MORE_DATA     = 7;

  /** Android Lint annotation for status codes that can be used with a WEBP decoder. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(value = {STATUS_OK, STATUS_OPEN_ERROR, STATUS_PARSE_ERROR, STATUS_MISS_DATA,
          STATUS_INVALID_PARAM, STATUS_TRUNCATED_DATA, STATUS_BITSTREAM_ERROR, STATUS_NEED_MORE_DATA})
  @interface WebpDecodeStatus {
  }

  /** The total iteration count which means repeat forever. */
  int TOTAL_ITERATION_COUNT_FOREVER = 0;

  /**
   * An interface that can be used to provide reused {@link Bitmap}s to avoid GCs
   * from constantly allocating {@link Bitmap}s for every frame.
   */
  interface BitmapProvider {
    /**
     * Returns an {@link Bitmap} with exactly the given dimensions and config.
     *
     * @param width  The width in pixels of the desired {@link Bitmap}.
     * @param height The height in pixels of the desired {@link Bitmap}.
     * @param config The {@link Bitmap.Config} of the desired {@link
     *               Bitmap}.
     */
    @NonNull
    Bitmap obtain(int width, int height, @NonNull Bitmap.Config config);

    /**
     * get given Bitmap byte size.
     */
    int getSize(@NonNull Bitmap bitmap);

    /**
     * Releases the given Bitmap back to the pool.
     */
    void release(@NonNull Bitmap bitmap);

    /**
     * Returns a byte array used for decoding and generating the frame bitmap.
     *
     * @param size the size of the byte array to obtain
     */
    @NonNull
    byte[] obtainByteArray(int size);

    /**
     * Releases the given byte array back to the pool.
     */
    void release(@NonNull byte[] bytes);

    /**
     * Returns an int array used for decoding/generating the frame bitmaps.
     */
    @NonNull
    int[] obtainIntArray(int size);

    /**
     * Release the given array back to the pool.
     */
    void release(@NonNull int[] array);
  }

  int getWidth();

  int getHeight();

  @NonNull
  ByteBuffer getData();

  /**
   * Returns the current status of the decoder.
   *
   * <p> Status will update per frame to allow the caller to tell whether or not the current frame
   * was decoded successfully and/or completely. Format and open failures persist across frames.
   * </p>
   */
  @WebpDecodeStatus
  int getStatus();

  /**
   * Move the animation frame counter forward.
   */
  void advance();

  /**
   * Gets display duration for specified frame.
   *
   * @param index int index of frame.
   * @return delay in milliseconds.
   */
  int getDuration(@IntRange(from = 0) int index);

  /**
   * Gets display duration for the upcoming frame in ms.
   */
  int getNextDelay();

  /**
   * Gets the number of frames read from file.
   *
   * @return frame count.
   */
  int getFrameCount();

  /**
   * Gets the current index of the animation frame, or -1 if animation hasn't not yet started.
   *
   * @return frame index.
   */
  @IntRange(from = 0)
  int getCurrentFrameIndex();

  /**
   * Resets the frame pointer to before the 0th frame, as if we'd never used this decoder to
   * decode any frames.
   */
  void resetFrameIndex();

  /**
   * Gets the "Netscape" loop count, if any. A count of 0 means repeat indefinitely.
   *
   * @deprecated Use {@link #getNetscapeLoopCount()} instead.
   *             This method cannot distinguish whether the loop count is 1 or doesn't exist.
   * @return loop count if one was specified, else 1.
   */
  @Deprecated
  int getLoopCount();

  /**
   * Gets the "Netscape" loop count, if any.
   * A count of 0 ({@link WebpHeader#NETSCAPE_LOOP_COUNT_FOREVER}) means repeat indefinitely.
   * It must not be a negative value.
   * <br>
   * Use {@link #getTotalIterationCount()}
   * to know how many times the animation sequence should be displayed.
   *
   * @return loop count if one was specified,
   *         else -1 ({@link WebpHeader#NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST}).
   */
  int getNetscapeLoopCount();

  /**
   * Gets the total count
   * which represents how many times the animation sequence should be displayed.
   * A count of 0 ({@link #TOTAL_ITERATION_COUNT_FOREVER}) means repeat indefinitely.
   * It must not be a negative value.
   * <p>
   *     The total count is calculated as follows by using {@link #getNetscapeLoopCount()}.
   *     This behavior is the same as most web browsers.
   *     <table border='1'>
   *         <tr class='tableSubHeadingColor'><th>{@code getNetscapeLoopCount()}</th>
   *             <th>The total count</th></tr>
   *         <tr><td>{@link WebpHeader#NETSCAPE_LOOP_COUNT_FOREVER}</td>
   *             <td>{@link #TOTAL_ITERATION_COUNT_FOREVER}</td></tr>
   *         <tr><td>{@link WebpHeader#NETSCAPE_LOOP_COUNT_DOES_NOT_EXIST}</td>
   *             <td>{@code 1}</td></tr>
   *         <tr><td>{@code n (n > 0)}</td>
   *             <td>{@code n + 1}</td></tr>
   *     </table>
   * </p>
   *
   * @see <a href="https://bugs.chromium.org/p/chromium/issues/detail?id=592735#c5">Discussion about
   *      the iteration count of animated WEBPs (Chromium Issue 592735)</a>
   *
   * @return total iteration count calculated from "Netscape" loop count.
   */
  int getTotalIterationCount();

  /**
   * Returns an estimated byte size for this decoder based on the data provided to
   * {@link #setData(WebpHeader, ByteBuffer)}, as well as internal buffers.
   */
  int getByteSize();

  /**
   * Get the next frame in the animation sequence.
   *
   * @return Bitmap representation of frame.
   */
  @Nullable
  Bitmap getNextFrame();

  void clear();

  void setData(@NonNull WebpHeader header, ByteBuffer byteBuffer);

  void setData(@NonNull WebpHeader header, ByteBuffer byteBuffer, int sampleSize);

  /**
   * Sets the default {@link Bitmap.Config} to use when decoding frames of a WEBP.
   *
   * <p>Valid options are {@link Bitmap.Config#ARGB_8888} and
   * {@link Bitmap.Config#RGB_565}.
   * {@link Bitmap.Config#ARGB_8888} will produce higher quality frames, but will
   * also use 2x the memory of {@link Bitmap.Config#RGB_565}.
   *
   * <p>Defaults to {@link Bitmap.Config#ARGB_8888}
   *
   * <p>This value is not a guarantee. For example if set to
   * {@link Bitmap.Config#RGB_565} and the WEBP contains transparent pixels,
   * {@link Bitmap.Config#ARGB_8888} will be used anyway to support the
   * transparency.
   */
  void setDefaultBitmapConfig(@NonNull Bitmap.Config format);
}
