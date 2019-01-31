/*
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *
 * This class is based on SegmentedControlButton by Benjamin Ferrari
 * http://bookworm.at
 * https://github.com/bookwormat/segcontrol
 * And extended to meet openHAB needs
 */

package org.openhab.habdroid.ui.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.annotation.IdRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatRadioButton;

import org.openhab.habdroid.R;

/** @author benjamin ferrari */
public class SegmentedControlButton extends AppCompatRadioButton {
    private int mLineHeight;
    private int mTextDistanceFromLine;

    private Paint mTextPaint;
    private Paint mLinePaint;

    private ColorStateList mBackgroundColorList;
    private Paint mBackgroundPaint;

    public SegmentedControlButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentedControlButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setButtonDrawable(null);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs,
                    R.styleable.SegmentedControlButton);

            mLineHeight = a.getDimensionPixelSize(
                    R.styleable.SegmentedControlButton_underlineHeight, 0);
            mTextDistanceFromLine = a.getDimensionPixelSize(
                    R.styleable.SegmentedControlButton_textDistanceFromLine, 0);

            mTextPaint = new Paint();
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextSize(getTextSize());
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            @ColorInt int lineColor =
                    a.getColor(R.styleable.SegmentedControlButton_underlineColor, 0);
            mLinePaint = new Paint();
            mLinePaint.setColor(lineColor);
            mLinePaint.setStyle(Style.FILL);

            @IdRes int bgColorResId = a.getResourceId(
                    R.styleable.SegmentedControlButton_backgroundColor, 0);
            if (bgColorResId != 0) {
                mBackgroundColorList =
                        AppCompatResources.getColorStateList(getContext(), bgColorResId);
                mBackgroundPaint = new Paint();
            }

            a.recycle();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (mBackgroundColorList != null) {
            @ColorInt int newColor = mBackgroundColorList.getColorForState(getDrawableState(), 0);
            if (newColor != mBackgroundPaint.getColor()) {
                mBackgroundPaint.setColor(newColor);
                invalidate();
            }
        }
    }

    @Override
    public void toggle() {
        // Prevent super method: we rely solely on our parent to update the checked state
    }

    @Override
    public int getCompoundPaddingBottom() {
        return Math.max(super.getCompoundPaddingBottom(), mLineHeight + mTextDistanceFromLine);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (mBackgroundPaint != null) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), mBackgroundPaint);
        }

        Drawable background = getBackground();
        if (background != null) {
            background.setBounds(0, 0, getWidth(), getHeight());
            background.draw(canvas);
        }

        String text = getText().toString();
        int textHeightPos = getHeight() - getCompoundPaddingBottom();

        mTextPaint.setColor(getCurrentTextColor());
        canvas.drawText(text, getWidth() / 2, textHeightPos, mTextPaint);

        if (mLineHeight > 0) {
            canvas.drawRect(0, getHeight() - mLineHeight, getWidth(), getHeight(), mLinePaint);
        }
    }
}
