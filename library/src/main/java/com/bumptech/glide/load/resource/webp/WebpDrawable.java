package com.bumptech.glide.load.resource.webp;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import android.view.Gravity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.util.Preconditions;
import com.bumptech.glide.webpdecoder.WebpDecoder;

import java.nio.ByteBuffer;

import static com.bumptech.glide.webpdecoder.WebpDecoder.TOTAL_ITERATION_COUNT_FOREVER;

/**
 * An animated {@link Drawable} that plays the frames of an animated WEBP.
 */
public class WebpDrawable extends Drawable implements WebpFrameLoader.FrameCallback, Animatable {
  /**
   * A constant indicating that an animated drawable should loop continuously.
   */
  // Public API.
  @SuppressWarnings("WeakerAccess")
  public static final int LOOP_FOREVER = -1;
  /**
   * A constant indicating that an animated drawable should loop for its default number of times.
   * For animated WEBPs, this constant indicates the WEBP should use the netscape loop count if
   * present.
   */
  // Public API.
  @SuppressWarnings("WeakerAccess")
  public static final int LOOP_INTRINSIC = 0;
  private static final int GRAVITY = Gravity.FILL;

  private final WebpState state;
  /**
   * True if the drawable is currently animating.
   */
  private boolean isRunning;
  /**
   * True if the drawable should animate while visible.
   */
  private boolean isStarted;
  /**
   * True if the drawable's resources have been recycled.
   */
  private boolean isRecycled;
  /**
   * True if the drawable is currently visible. Default to true because on certain platforms (at
   * least 4.1.1), setVisible is not called on {@link Drawable Drawables}
   * during {@link android.widget.ImageView#setImageDrawable(Drawable)}.
   * See issue #130.
   */
  private boolean isVisible = true;
  /**
   * The number of times we've looped over all the frames in the WEBP.
   */
  private int loopCount;
  /**
   * The number of times to loop through the WEBP animation.
   */
  private int maxLoopCount = LOOP_FOREVER;

  private boolean applyGravity;
  private Paint paint;
  private Rect destRect;

  /**
   * Constructor for WebpDrawable.
   *
   * @param context             A context.
   * @param bitmapPool          Ignored, see deprecation note.
   * @param frameTransformation An {@link Transformation} that can be
   *                            applied to each frame.
   * @param targetFrameWidth    The desired width of the frames displayed by this drawable (the
   *                            width of the view or
   *                            {@link com.bumptech.glide.request.target.Target}
   *                            this drawable is being loaded into).
   * @param targetFrameHeight   The desired height of the frames displayed by this drawable (the
   *                            height of the view or
   *                            {@link com.bumptech.glide.request.target.Target}
   *                            this drawable is being loaded into).
   * @param webpDecoder         The decoder to use to decode WEBP data.
   * @param firstFrame          The decoded and transformed first frame of this WEBP.
   * @see #setFrameTransformation(Transformation, Bitmap)
   *
   * @deprecated Use {@link #WebpDrawable(Context, WebpDecoder, Transformation, int, int, Bitmap)}
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public WebpDrawable(
      Context context,
      WebpDecoder webpDecoder,
      @SuppressWarnings("unused") BitmapPool bitmapPool,
      Transformation<Bitmap> frameTransformation,
      int targetFrameWidth,
      int targetFrameHeight,
      Bitmap firstFrame) {
    this(context, webpDecoder, frameTransformation, targetFrameWidth, targetFrameHeight, firstFrame);
  }

   /**
   * Constructor for WebpDrawable.
   *
   * @param context             A context.
   * @param frameTransformation An {@link Transformation} that can be
   *                            applied to each frame.
   * @param targetFrameWidth    The desired width of the frames displayed by this drawable (the
   *                            width of the view or
   *                            {@link com.bumptech.glide.request.target.Target}
   *                            this drawable is being loaded into).
   * @param targetFrameHeight   The desired height of the frames displayed by this drawable (the
   *                            height of the view or
   *                            {@link com.bumptech.glide.request.target.Target}
   *                            this drawable is being loaded into).
   * @param webpDecoder         The decoder to use to decode WEBP data.
   * @param firstFrame          The decoded and transformed first frame of this WEBP.
   * @see #setFrameTransformation(Transformation, Bitmap)
   */
  public WebpDrawable(
      Context context,
      WebpDecoder webpDecoder,
      Transformation<Bitmap> frameTransformation,
      int targetFrameWidth,
      int targetFrameHeight,
      Bitmap firstFrame) {
    this(
        new WebpState(
            new WebpFrameLoader(
                // TODO(b/27524013): Factor out this call to Glide.get()
                Glide.get(context),
                webpDecoder,
                targetFrameWidth,
                targetFrameHeight,
                frameTransformation,
                firstFrame)));
  }

  WebpDrawable(WebpState state) {
    this.state = Preconditions.checkNotNull(state);
  }

  @VisibleForTesting
  WebpDrawable(WebpFrameLoader frameLoader, Paint paint) {
    this(new WebpState(frameLoader));
    this.paint = paint;
  }

  public int getSize() {
    return state.frameLoader.getSize();
  }

  public Bitmap getFirstFrame() {
    return state.frameLoader.getFirstFrame();
  }

  // Public API.
  @SuppressWarnings("WeakerAccess")
  public void setFrameTransformation(Transformation<Bitmap> frameTransformation,
      Bitmap firstFrame) {
    state.frameLoader.setFrameTransformation(frameTransformation, firstFrame);
  }

  public Transformation<Bitmap> getFrameTransformation() {
    return state.frameLoader.getFrameTransformation();
  }

  public ByteBuffer getBuffer() {
    return state.frameLoader.getBuffer();
  }

  public int getFrameCount() {
    return state.frameLoader.getFrameCount();
  }

  /**
   * Returns the current frame index in the range 0..{@link #getFrameCount()} - 1, or -1 if no frame
   * is displayed.
   */
  // Public API.
  @SuppressWarnings("WeakerAccess")
  public int getFrameIndex() {
    return state.frameLoader.getCurrentIndex();
  }

  private void resetLoopCount() {
    loopCount = 0;
  }

  /**
   * Starts the animation from the first frame. Can only be called while animation is not running.
   */
  // Public API.
  @SuppressWarnings("unused")
  public void startFromFirstFrame() {
    Preconditions.checkArgument(!isRunning, "You cannot restart a currently running animation.");
    state.frameLoader.setNextStartFromFirstFrame();
    start();
  }

  @Override
  public void start() {
    isStarted = true;
    resetLoopCount();
    if (isVisible) {
      startRunning();
    }
  }

  @Override
  public void stop() {
    isStarted = false;
    stopRunning();
  }

  private void startRunning() {
    Preconditions.checkArgument(!isRecycled, "You cannot start a recycled Drawable. Ensure that"
        + "you clear any references to the Drawable when clearing the corresponding request.");
    // If we have only a single frame, we don't want to decode it endlessly.
    if (state.frameLoader.getFrameCount() == 1) {
      invalidateSelf();
    } else if (!isRunning) {
      isRunning = true;
      state.frameLoader.subscribe(this);
      invalidateSelf();
    }
  }

  private void stopRunning() {
    isRunning = false;
    state.frameLoader.unsubscribe(this);
  }

  @Override
  public boolean setVisible(boolean visible, boolean restart) {
    Preconditions.checkArgument(!isRecycled, "Cannot change the visibility of a recycled resource."
        + " Ensure that you unset the Drawable from your View before changing the View's"
        + " visibility.");
    isVisible = visible;
    if (!visible) {
      stopRunning();
    } else if (isStarted) {
      startRunning();
    }
    return super.setVisible(visible, restart);
  }

  @Override
  public int getIntrinsicWidth() {
    return state.frameLoader.getWidth();
  }

  @Override
  public int getIntrinsicHeight() {
    return state.frameLoader.getHeight();
  }

  @Override
  public boolean isRunning() {
    return isRunning;
  }

  // For testing.
  void setIsRunning(boolean isRunning) {
    this.isRunning = isRunning;
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    super.onBoundsChange(bounds);
    applyGravity = true;
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    if (isRecycled) {
      return;
    }

    if (applyGravity) {
      Gravity.apply(GRAVITY, getIntrinsicWidth(), getIntrinsicHeight(), getBounds(), getDestRect());
      applyGravity = false;
    }

    Bitmap currentFrame = state.frameLoader.getCurrentFrame();
    canvas.drawBitmap(currentFrame, null, getDestRect(), getPaint());
  }

  @Override
  public void setAlpha(int i) {
    getPaint().setAlpha(i);
  }

  @Override
  public void setColorFilter(ColorFilter colorFilter) {
    getPaint().setColorFilter(colorFilter);
  }

  private Rect getDestRect() {
    if (destRect == null) {
      destRect = new Rect();
    }
    return destRect;
  }

  private Paint getPaint() {
    if (paint == null) {
      paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    }
    return paint;
  }

  @Override
  public int getOpacity() {
    // We can't tell, so default to transparent to be safe.
    return PixelFormat.TRANSPARENT;
  }

  // See #1087.
  private Callback findCallback() {
    Callback callback = getCallback();
    while (callback instanceof Drawable) {
      callback = ((Drawable) callback).getCallback();
    }
    return callback;
  }

  @Override
  public void onFrameReady() {
    if (findCallback() == null) {
      stop();
      invalidateSelf();
      return;
    }

    invalidateSelf();

    if (getFrameIndex() == getFrameCount() - 1) {
      loopCount++;
    }

    if (maxLoopCount != LOOP_FOREVER && loopCount >= maxLoopCount) {
      stop();
    }
  }

  @Override
  public ConstantState getConstantState() {
    return state;
  }

  /**
   * Clears any resources for loading frames that are currently held on to by this object.
   */
  public void recycle() {
    isRecycled = true;
    state.frameLoader.clear();
  }

  // For testing.
  boolean isRecycled() {
    return isRecycled;
  }

  // Public API.
  @SuppressWarnings("WeakerAccess")
  public void setLoopCount(int loopCount) {
    if (loopCount <= 0 && loopCount != LOOP_FOREVER && loopCount != LOOP_INTRINSIC) {
      throw new IllegalArgumentException("Loop count must be greater than 0, or equal to "
          + "GlideDrawable.LOOP_FOREVER, or equal to GlideDrawable.LOOP_INTRINSIC");
    }

    if (loopCount == LOOP_INTRINSIC) {
      int intrinsicCount = state.frameLoader.getLoopCount();
      maxLoopCount =
          (intrinsicCount == TOTAL_ITERATION_COUNT_FOREVER) ? LOOP_FOREVER : intrinsicCount;
    } else {
      maxLoopCount = loopCount;
    }
  }

  static final class WebpState extends ConstantState {
    @VisibleForTesting
    final WebpFrameLoader frameLoader;

    WebpState(WebpFrameLoader frameLoader) {
      this.frameLoader = frameLoader;
    }

    @NonNull
    @Override
    public Drawable newDrawable(Resources res) {
      return newDrawable();
    }

    @NonNull
    @Override
    public Drawable newDrawable() {
      return new WebpDrawable(this);
    }

    @Override
    public int getChangingConfigurations() {
      return 0;
    }
  }
}
