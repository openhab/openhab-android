/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Shader.TileMode;
import android.graphics.SweepGradient;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import org.openhab.habdroid.R;

/**
 * Displays a holo-themed color picker.
 *
 * <p>
 * Use {@link #getColor()} to retrieve the selected color.
 * </p>
 */
public class ColorPicker extends View {

	private static final String TAG = ColorPicker.class.getSimpleName();
	/*
	 * Constants used to save/restore the instance state.
	 */
	private static final String STATE_PARENT = "parent";
	private static final String STATE_ANGLE = "angle";

	/**
	 * Colors to construct the color wheel using {@link android.graphics.SweepGradient}.
	 *
	 * <p>
	 * Note: The algorithm in {@link #normalizeColor(int)} highly depends on these exact values. Be
	 * aware that {@link #setColor(int)} might break if you change this array.
	 * </p>
	 */
	private static final int[] COLORS = new int[] { 0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF,
		0xFF00FF00, 0xFFFFFF00, 0xFFFF0000 };

	int[] BCOLORS = new int[] {0, 0, 0};

	/**
	 * {@code Paint} instance used to draw the color wheel.
	 */
	private Paint mColorWheelPaint;

	/**
	 * {@code Paint} instance used to draw the brightness slider.
	 */
	private Paint mBrightnessSliderPaint;

	/**
	 * {@code Paint} instance used to draw the saturation slider.
	 */
	private Paint mSaturationSliderPaint;

	/**
	 * {@code Paint} instance used to draw the color wheel pointer's "halo".
	 */
	private Paint mPointerHaloPaint;

	/**
	 * {@code Paint} instance used to draw the color wheel pointer (the selected color).
	 */
	private Paint mPointerColor;

	/**
	 * {@code Paint} instance used to draw the brightness pointer's "halo".
	 */
	private Paint mBrightnessPointerHaloPaint;

	/**
	 * {@code Paint} instance used to draw the border of brightness pointer.
	 */
	private Paint mBrightnessPointerBorderPaint;

	/**
	 * {@code Paint} instance used to draw the brightness pointer (the selected brightness).
	 */
	private Paint mBrightnessPointerColor;

	/**
	 * {@code Paint} instance used to draw the brightness pointer's "halo".
	 */
	private Paint mSaturationPointerHaloPaint;

	/**
	 * {@code Paint} instance used to draw the brightness pointer (the selected brightness).
	 */
	private Paint mSaturationPointerColor;

	/**
	 * The stroke width used to paint the color wheel (in pixels).
	 */
	private int mColorWheelStrokeWidth;

	/**
	 * The radius of the pointer (in pixels).
	 */
	private int mPointerRadius;

	/**
	 * The rectangle enclosing the color wheel.
	 */
	private RectF mColorWheelRectangle = new RectF();

	/**
	 * {@code true} if the user clicked on the color wheel pointer to start the move mode. {@code false} once
	 * the user stops touching the screen.
	 *
	 * @see #onTouchEvent( android.view.MotionEvent )
	 */
	private boolean mUserIsMovingPointer = false;

	/**
	 * {@code true} if the user clicked on the brightness pointer to start the move mode. {@code false} once
	 * the user stops touching the screen.
	 *
	 * @see ( android.view.MotionEvent )
	 */
	private boolean mUserIsMovingBrightnessPointer = false;

	/**
	 * {@code true} if the user clicked on the brightness pointer to start the move mode. {@code false} once
	 * the user stops touching the screen.
	 *
	 * @see ( android.view.MotionEvent )
	 */
	private boolean mUserIsMovingSaturationPointer = false;

	/**
	 * The ARGB value of the currently selected color.
	 */
	private int mColor;

	/**
	 *  The value of currently selected brightness.
	 */
	private float mBrightness=1;

	/**
	 *  The value of currently selected brightness.
	 */
	private float mSaturation=1;

	/**
	 *  The Y position of currently selected brightness.
	 */
	private float mBrightnessY;

	/**
	 * The current final color calculated after brightness and saturation
	 */

	private int mBrightnessColor;

	/**
	 * The current HSV color selected by ColorPicker
	 */
	private float[] mHSVColor = {0,0,0};

	/**
	 *  The Y position of currently selected brightness.
	 */
	private float mSaturationY;

	/**
	 *  The current color calculated after saturation
	 */
	private int mSaturationColor;

	/**
	 * Number of pixels the origin of this view is moved in X- and Y-direction.
	 *
	 * <p>
	 * We use the center of this (quadratic) View as origin of our internal coordinate system.
	 * Android uses the upper left corner as origin for the View-specific coordinate system. So this
	 * is the value we use to translate from one coordinate system to the other.
	 * </p>
	 *
	 * <p>Note: (Re)calculated in {@link #onMeasure(int, int)}.</p>
	 *
	 * @see #onDraw( android.graphics.Canvas )
	 */
	private float mTranslationOffset;

	/**
	 * Radius of the color wheel in pixels.
	 *
	 * <p>Note: (Re)calculated in {@link #onMeasure(int, int)}.</p>
	 */
	private float mColorWheelRadius;

	/**
	 * The pointer's position expressed as angle (in rad).
	 */
	private float mAngle;

	/**
	 * The brightness slider X position
	 */
	private float mBrightnessSliderX;

	/**
	 * The saturation slider X position
	 */
	private float mSaturationSliderX;

	/**
	 *
	 */
	private float mBrightnessSliderStartY;

	/**
	 *
	 */
	private float mBrightnessSliderEndY;

	/**
	 *
	 */
	private float mSaturationSliderStartY;

	/**
	 *
	 */
	private float mSaturationSliderEndY;

	/**
	 * Listener to send color change events to
	 */
    private OnColorChangedListener mListener;

	public ColorPicker(Context context) {
		super(context);
		init(null, 0);
	}

	public ColorPicker(Context context, OnColorChangedListener l) {
		super(context);
		this.mListener = l;
		init(null, 0);
	}

	public ColorPicker(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs, 0);
	}

	public ColorPicker(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(attrs, defStyle);
	}

	private void init(AttributeSet attrs, int defStyle) {
		final TypedArray a = getContext().obtainStyledAttributes(attrs,
				R.styleable.ColorPicker, defStyle, 0);

		mColorWheelStrokeWidth = a.getInteger(R.styleable.ColorPicker_wheel_size, 16);
		mPointerRadius = a.getInteger(R.styleable.ColorPicker_pointer_size, 32);

		// initialize X positions of brightness and saturation sliders
		mBrightnessSliderX = mPointerRadius*2;
		mSaturationSliderX = -mPointerRadius*2;

		Log.d(TAG, String.format("init %f %f %f %f", mBrightnessSliderStartY, mBrightnessSliderEndY,
				mSaturationSliderStartY, mSaturationSliderEndY));

		a.recycle();

		Shader s = new SweepGradient(0, 0, COLORS, null);

		mColorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mColorWheelPaint.setShader(s);
		mColorWheelPaint.setStyle(Paint.Style.STROKE);
		mColorWheelPaint.setStrokeWidth(mColorWheelStrokeWidth);

		mBrightnessSliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		mPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPointerHaloPaint.setColor(Color.BLACK);
		mPointerHaloPaint.setStrokeWidth(5);
		mPointerHaloPaint.setAlpha(0x60);

		mPointerColor = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPointerColor.setStrokeWidth(5);

		mBrightnessPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mBrightnessPointerHaloPaint.setColor(Color.BLACK);
		mBrightnessPointerHaloPaint.setStrokeWidth(5);
		mBrightnessPointerHaloPaint.setAlpha(0x60);

		mBrightnessPointerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mBrightnessPointerBorderPaint.setColor(Color.WHITE);
		mBrightnessPointerBorderPaint.setStrokeWidth(2);
		mBrightnessPointerBorderPaint.setAlpha(0x60);

		mBrightnessPointerColor = new Paint(Paint.ANTI_ALIAS_FLAG);
		mBrightnessPointerColor.setStrokeWidth(5);

		mSaturationSliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

		mSaturationPointerHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mSaturationPointerHaloPaint.setColor(Color.BLACK);
		mSaturationPointerHaloPaint.setStrokeWidth(5);
		mSaturationPointerHaloPaint.setAlpha(0x60);

		mSaturationPointerColor = new Paint(Paint.ANTI_ALIAS_FLAG);
		mSaturationPointerColor.setStrokeWidth(5);

		mAngle = (float) (-Math.PI / 2);
		mPointerColor.setColor(calculateColor(mAngle));
//		mBrightnessPointerColor.setColor(calculateColor(mAngle));
		mBrightnessPointerColor.setColor(Color.BLACK);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Log.d(TAG, "onDraw");
		// All of our positions are using our internal coordinate system. Instead of translating
		// them we let Canvas do the work for us.
		canvas.translate(mTranslationOffset, mTranslationOffset);

		// Draw the color wheel.
		canvas.drawOval(mColorWheelRectangle, mColorWheelPaint);

		updateBrightnessAndSaturation();

		// Draw the brightness slider
		Shader brightnessShader = new LinearGradient(mBrightnessSliderX, mBrightnessSliderStartY,
				mBrightnessSliderX, mBrightnessSliderEndY, mSaturationColor, Color.BLACK,
				TileMode.CLAMP);
		mBrightnessSliderPaint.setShader(brightnessShader);
		mBrightnessSliderPaint.setStyle(Paint.Style.STROKE);
		mBrightnessSliderPaint.setStrokeWidth(mColorWheelStrokeWidth);
		canvas.drawLine(mBrightnessSliderX, mBrightnessSliderStartY,
						mBrightnessSliderX, mBrightnessSliderEndY, mBrightnessSliderPaint);
		// Draw the saturation slider
		Shader saturationShader = new LinearGradient(mSaturationSliderX, mSaturationSliderStartY,
				mSaturationSliderX, mBrightnessSliderEndY, Color.WHITE, mPointerColor.getColor(),
				TileMode.CLAMP);
		mSaturationSliderPaint.setShader(saturationShader);
		mSaturationSliderPaint.setStyle(Paint.Style.STROKE);
		mSaturationSliderPaint.setStrokeWidth(mColorWheelStrokeWidth);
		canvas.drawLine(mSaturationSliderX, mSaturationSliderStartY,
				mSaturationSliderX, mSaturationSliderEndY, mSaturationSliderPaint);

		float[] pointerPosition = calculatePointerPosition(mAngle);

		// Draw the pointer's "halo"
		canvas.drawCircle(pointerPosition[0], pointerPosition[1], mPointerRadius, mPointerHaloPaint);

		// Draw the pointer (the currently selected color) slightly smaller on top.
		canvas.drawCircle(pointerPosition[0], pointerPosition[1],
				(float) (mPointerRadius / 1.2), mPointerColor);

		// Draw the brightness pointer's "halo"
		canvas.drawCircle(mBrightnessSliderX, mBrightnessY, mPointerRadius, mBrightnessPointerHaloPaint);
		// Draw the brightness pointer's border
		canvas.drawCircle(mBrightnessSliderX, mBrightnessY, mPointerRadius-2, mBrightnessPointerBorderPaint);
		// Draw the pointer (the currently selected brightness) slightly smaller on top.
		mBrightnessPointerColor.setColor(mBrightnessColor);
		canvas.drawCircle(mBrightnessSliderX, mBrightnessY, (float) (mPointerRadius / 1.2), mBrightnessPointerColor);

		// Draw the saturation pointer's "halo"
		canvas.drawCircle(mSaturationSliderX, mSaturationY, mPointerRadius, mSaturationPointerHaloPaint);
		// Draw the pointer (the currently selected saturation) slightly smaller on top.
		mSaturationPointerColor.setColor(mSaturationColor);
		canvas.drawCircle(mSaturationSliderX, mSaturationY, (float) (mPointerRadius / 1.2), mSaturationPointerColor);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int height = getDefaultSize(getSuggestedMinimumHeight(),
				heightMeasureSpec);
		int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		int min = Math.min(width, height);
//		Log.i("ColorDemo", String.format("w = %d, h = %d", width, height));
		setMeasuredDimension(min, min);

		mTranslationOffset = min * 0.5f;
		mColorWheelRadius = mTranslationOffset - mPointerRadius;

		mColorWheelRectangle.set(-mColorWheelRadius, -mColorWheelRadius, mColorWheelRadius,
				mColorWheelRadius);

		mBrightnessSliderEndY = (float)(mColorWheelRadius*0.5);
		mBrightnessSliderStartY = (float)(-mColorWheelRadius*0.5);
		mSaturationSliderEndY = (float)(mColorWheelRadius*0.5);
		mSaturationSliderStartY = (float)(-mColorWheelRadius*0.5);
	}

	private int ave(int s, int d, float p) {
		return s + Math.round(p * (d - s));
	}

	/**
	 * Calculate the color using the supplied angle.
	 *
	 * @param angle
	 *         The selected color's position expressed as angle (in rad).
	 *
	 * @return The ARGB value of the color on the color wheel at the specified angle.
	 */
	private int calculateColor(float angle) {
		float unit = (float) (angle / (2 * Math.PI));
		if (unit < 0) {
			unit += 1;
		}

		if (unit <= 0) {
			return COLORS[0];
		}
		if (unit >= 1) {
			return COLORS[COLORS.length - 1];
		}

		float p = unit * (COLORS.length - 1);
		int i = (int) p;
		p -= i;

		int c0 = COLORS[i];
		int c1 = COLORS[i + 1];
		int a = ave(Color.alpha(c0), Color.alpha(c1), p);
		int r = ave(Color.red(c0), Color.red(c1), p);
		int g = ave(Color.green(c0), Color.green(c1), p);
		int b = ave(Color.blue(c0), Color.blue(c1), p);

		mColor = Color.argb(a, r, g, b);
		return Color.argb(a, r, g, b);
	}

	/**
	 * Get the currently selected color.
	 *
	 * @return The ARGB value of the currently selected color.
	 */
	public int getColor() {
		return mColor;
	}

	/**
	 * Set the color to be highlighted by the pointer.
	 *
	 * @param color
	 *         The RGB value of the color to highlight. If this is not a color displayed on the
	 *         color wheel a very simple algorithm is used to map it to the color wheel. The
	 *         resulting color often won't look close to the original color. This is especially true
	 *         for shades of grey. You have been warned!
	 */
	public void setColor(int color) {
		mAngle = colorToAngle(color);
		mPointerColor.setColor(calculateColor(mAngle));
		invalidate();
	}

	public void setHSVColor(float[] hsv) {
		Log.d(TAG, String.format("Setting color to %f %f %f", hsv[0], hsv[1], hsv[2]));
		mAngle = this.colorToAngle(Color.HSVToColor(hsv));
		mSaturation = hsv[1];
		mBrightness = 1 - hsv[2];
		mPointerColor.setColor(calculateColor(mAngle));
		updateBrightnessAndSaturation();
		postInvalidate();
	}

	/**
	 * Convert a color to an angle.
	 *
	 * @param color
	 *         The RGB value of the color to "find" on the color wheel. {@link #normalizeColor(int)}
	 *         will be used to map this color to one on the color wheel if necessary.
	 *
	 * @return The angle (in rad) the "normalized" color is displayed on the color wheel.
	 */
	private float colorToAngle(int color) {
		int[] colorInfo = normalizeColor(color);
		int normColor = colorInfo[0];
		int colorMask = colorInfo[1];
		int shiftValue = colorInfo[2];

		int anchorColor = (normColor & ~colorMask);

		// Find the "anchor" color in the COLORS array
		for (int i = 0; i < COLORS.length - 1; i++) {
			if (COLORS[i] == anchorColor) {
				int nextValue = COLORS[i + 1];

				double value;
				double decimals = ((normColor >> shiftValue) & 0xFF) / 255D;

				// Find out if the gradient our color belongs to goes from the element just found to
				// the next element in the array.
				if ((nextValue & colorMask) != (anchorColor & colorMask)) {
					// Compute value depending of the gradient direction
					if (nextValue < anchorColor) {
						value = i + 1 - decimals;
					} else {
						value = i + decimals;
					}
				} else {
					// It's a gradient from this element to the previous element in the array.

					// Wrap to the end of the array if the "anchor" color is the first element.
					int index = (i == 0) ? COLORS.length - 1 : i;
					int prevValue = COLORS[index - 1];

					// Compute value depending of the gradient direction
					if (prevValue < anchorColor) {
						value = index - 1 + decimals;
					} else {
						value = index - decimals;
					}
				}

				// Calculate the angle in rad (from -PI to PI)
				float angle = (float) (2 * Math.PI * value / (COLORS.length - 1));
				if (angle > Math.PI) {
					angle -= 2 * Math.PI;
				}

				return angle;
			}
		}

		// This shouldn't happen
		return 0;
	}

	/**
	 * "Normalize" the supplied color.
	 *
	 * <p>
	 * This will set the lowest value of R,G,B to 0, the highest to 255, and will keep the middle
	 * value.<br>
	 * For values close to those on the color wheel this will result in close matches. For other
	 * values, especially shades of grey this will produce funny results.
	 * </p>
	 *
	 * @param color
	 *         The color to "normalize".
	 *
	 * @return An {@code int} array with the following contents:
	 *         <ol>
	 *           <li>The ARGB value of the "normalized" color.</li>
	 *           <li>A mask with all bits {@code 0} but those for the byte representing the
	 *               "middle value" that remains unchanged in the "normalized" color.</li>
	 *           <li>The number of bits the "normalized" color has to be shifted to the right so the
	 *               "middle value" is in the lower 8 bits.</li>
	 *         </ol>
	 */
	private int[] normalizeColor(int color) {
		int red = Color.red(color);
		int green = Color.green(color);
		int blue = Color.blue(color);

		int newRed = red;
		int newGreen = green;
		int newBlue = blue;

		int maskRed = 0;
		int maskGreen = 0;
		int maskBlue = 0;
		int shiftValue;

		if (red < green && red < blue) {
			// Red is the smallest component
			newRed = 0;
			if (green > blue) {
				// Green is the largest component
				shiftValue = 0;
				maskBlue = 0xFF;
				newGreen = 0xFF;
			} else {
				// We make blue the largest component
				shiftValue = 8;
				maskGreen = 0xFF;
				newBlue = 0xFF;
			}
		} else if (green < red && green < blue) {
			// Green is the smallest component
			newGreen = 0;
			if (red > blue) {
				// Red is the largest component
				shiftValue = 0;
				maskBlue = 0xFF;
				newRed = 0xFF;
			} else {
				// We make blue the largest component
				shiftValue = 16;
				maskRed = 0xFF;
				newBlue = 0xFF;
			}
		} else {
			// We make blue the smallest component
			newBlue = 0;
			if (red > green) {
				// Red is the largest component
				shiftValue = 8;
				maskGreen = 0xFF;
				newRed = 0xFF;
			} else {
				// We make green the largest component
				shiftValue = 16;
				maskRed = 0xFF;
				newGreen = 0xFF;
			}
		}

		int normColor = Color.argb(255, newRed, newGreen, newBlue);
		int colorMask = Color.argb(0, maskRed, maskGreen, maskBlue);

		return new int[] { normColor, colorMask, shiftValue };
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Convert coordinates to our internal coordinate system
		float x = event.getX() - mTranslationOffset;
		float y = event.getY() - mTranslationOffset;

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			// Check whether the user pressed on (or near) the pointer
			float[] pointerPosition = calculatePointerPosition(mAngle);
			if (x >= (pointerPosition[0] - 48) && x <= (pointerPosition[0] + 48)
					&& y >= (pointerPosition[1] - 48) && y <= (pointerPosition[1] + 48)) {
//				Log.i("MotionEvent", String.format("down inside color x = %f, y = %f", x, y));
				mUserIsMovingPointer = true;
				invalidate();
			} else if ((x >= mBrightnessSliderX-mPointerRadius && x <= mBrightnessSliderX+mPointerRadius) &&
					(y >= mBrightnessSliderStartY-mPointerRadius/2 && y <= mBrightnessSliderEndY+mPointerRadius/2)) {
//				Log.i("MotionEvent", String.format("down inside brightness x = %f, y = %f", x, y));
				mUserIsMovingBrightnessPointer = true;
			} else if ((x >= mSaturationSliderX-mPointerRadius/2 && x <= mSaturationSliderX+mPointerRadius/2) &&
					(y >= mSaturationSliderStartY-mPointerRadius/2 && y <= mSaturationSliderEndY+mPointerRadius/2)) {
//				Log.i("MotionEvent", String.format("down inside saturation x = %f, y = %f", x, y));
				mUserIsMovingSaturationPointer = true;
			}
			break;
		case MotionEvent.ACTION_MOVE:
			if (mUserIsMovingPointer) {
				mAngle = (float) Math.atan2(y, x);
				mPointerColor.setColor(calculateColor(mAngle));
//				updateBrightnessAndSaturation();
//				Log.i("MotionEvent", String.format("move inside color x = %f,  y = %f", x, y));
				invalidate();
			} else if (mUserIsMovingBrightnessPointer) {
				if (y < mBrightnessSliderStartY)
					y = mBrightnessSliderStartY;
				if (y > mBrightnessSliderEndY)
					y = mBrightnessSliderEndY;
//				Log.i("MotionEvent", String.format("move inside brightness x = %f,  y = %f", x, y));
				mBrightness = calculateSliderValue(mBrightnessSliderEndY,
						mBrightnessSliderStartY, y);
//				Log.d("MotionEvent", String.format("New brightness = %f", mBrightness));
				invalidate();
			} else if (mUserIsMovingSaturationPointer) {
				if (y < mSaturationSliderStartY)
					y = mSaturationSliderStartY;
				if (y > mSaturationSliderEndY)
					y = mSaturationSliderEndY;
//				Log.i("MotionEvent", String.format("move inside saturation x = %f,  y = %f", x, y));
				mSaturation = calculateSliderValue(mSaturationSliderEndY,
						mSaturationSliderStartY, y);
//				Log.d("MotionEvent", String.format("New saturation = %f", mSaturation));
				invalidate();
			}
			break;
		case MotionEvent.ACTION_UP:
//			Log.i("MotionEvent", "up");
			mUserIsMovingPointer = false;
			mUserIsMovingBrightnessPointer = false;
			mUserIsMovingSaturationPointer = false;
			if (mListener != null) {
				mListener.colorChanged(mHSVColor, this);
			}
			break;
		}
		return true;
	}
	
	/**
	 * Calculate a 0-255 value from minimum/maximum and position between them
	 */
	private float calculateSliderValue(float sliderMinimum, float sliderMaximum, float sliderPosition) {
		float sliderSize = 0;
		float sliderStart = 0;
		if (sliderMinimum < sliderMaximum) {
			sliderSize = sliderMaximum - sliderMinimum;
			sliderStart = sliderMinimum;
		} else {
			sliderSize = sliderMinimum - sliderMaximum;
			sliderStart = sliderMaximum;
		}
		sliderPosition = sliderPosition - sliderStart;
		return sliderPosition/sliderSize;
	}
	
	/**
	 * Calculate color in between two colors based on a 0-1 position
	 */
	private int calculateSaturationColor(int saturationColor, float saturationValue) {
		float baseHSV[] = {0, 0, 0};
		Color.RGBToHSV(Color.red(saturationColor), Color.green(saturationColor), Color.blue(saturationColor), baseHSV);
		baseHSV[1] = saturationValue;
		return Color.HSVToColor(baseHSV);
	}
	
	/**
	 * Calculate final color based on on brightness 0-1
	 */
	
	private int calculateFinalColor(int brightnessColor, float brightnessValue) {
		float baseHSV[] = {0, 0, 0};
		Color.RGBToHSV(Color.red(brightnessColor), Color.green(brightnessColor), Color.blue(brightnessColor), baseHSV);
		baseHSV[1] = mSaturation;
		baseHSV[2] = 1 - brightnessValue;
		return Color.HSVToColor(baseHSV);
	}

	/**
	 * Update brightness and saturation
	 */

	private void updateBrightnessAndSaturation() {
		mBrightnessY = mBrightnessSliderStartY+(mBrightnessSliderEndY-mBrightnessSliderStartY)*mBrightness;
		mSaturationY = mSaturationSliderStartY+(mSaturationSliderEndY-mSaturationSliderStartY)*mSaturation;
		mSaturationColor = calculateSaturationColor(mPointerColor.getColor(), mSaturation);
		mBrightnessColor = calculateFinalColor(mSaturationColor, mBrightness);
		Color.colorToHSV(mBrightnessColor, mHSVColor);
	}
	
	/**
	 * Calculate the pointer's coordinates on the color wheel using the supplied angle.
	 *
	 * @param angle
	 *         The position of the pointer expressed as angle (in rad).
	 *
	 * @return The coordinates of the pointer's center in our internal coordinate system.
	 */
	private float[] calculatePointerPosition(float angle) {
		float x = (float) (mColorWheelRadius * Math.cos(angle));
		float y = (float) (mColorWheelRadius * Math.sin(angle));

		return new float[] { x, y };
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();

		Bundle state = new Bundle();
		state.putParcelable(STATE_PARENT, superState);
		state.putFloat(STATE_ANGLE, mAngle);

		return state;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		Bundle savedState = (Bundle) state;

		Parcelable superState = savedState.getParcelable(STATE_PARENT);
		super.onRestoreInstanceState(superState);

		mAngle = savedState.getFloat(STATE_ANGLE);
		mPointerColor.setColor(calculateColor(mAngle));
	}
}
