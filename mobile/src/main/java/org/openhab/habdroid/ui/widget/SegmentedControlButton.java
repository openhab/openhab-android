/**
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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import org.openhab.habdroid.R;

/** @author benjamin ferrari */
public class SegmentedControlButton extends android.support.v7.widget.AppCompatRadioButton {
    private int mLineHeight;

    private float mX;

    private int mTextDistanceFromLine;

    private Paint mTextPaint;
    private Paint mLinePaint;
    
    public SegmentedControlButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SegmentedControlButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setButtonDrawable(null);
        if (attrs != null) {
            TypedArray attributes = this.getContext().obtainStyledAttributes(attrs, R.styleable.SegmentedControlButton);

            int lineColor = attributes.getColor(R.styleable.SegmentedControlButton_lineColor, 0);
            mLineHeight = attributes.getDimensionPixelSize(R.styleable.SegmentedControlButton_lineHeight, 0);
            mTextDistanceFromLine = attributes.getDimensionPixelSize(R.styleable.SegmentedControlButton_textDistanceFromLine, 0);

            mTextPaint = new Paint();
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextSize(getTextSize());
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mLinePaint = new Paint();
            mLinePaint.setColor(lineColor);
            mLinePaint.setStyle(Style.FILL);
        }
    }

    @Override
    public void toggle() {
        // Prevent super method: we rely solely on our parent to update the checked state
    }

    @Override
    public void onDraw(Canvas canvas) {
        String text = getText().toString();
        int textHeightPos = getHeight() - mLineHeight - mTextDistanceFromLine;
        float x = mX;

        Drawable background = getBackground();
        background.setBounds(0, 0, getWidth(), getHeight());
        background.draw(canvas);

        mTextPaint.setColor(getCurrentTextColor());
        canvas.drawText(text, x, textHeightPos, mTextPaint);

        if (mLineHeight > 0) {
            canvas.drawRect(0, getHeight() - mLineHeight, getWidth(), getHeight(), mLinePaint);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mX = w * 0.5f; // remember the center of the screen
    }
}
