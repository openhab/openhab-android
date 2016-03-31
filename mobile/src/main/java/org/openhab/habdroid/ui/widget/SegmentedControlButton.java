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
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;

import org.openhab.habdroid.R;

/** @author benjamin ferrari */
public class SegmentedControlButton extends RadioButton {

    private Drawable backgroundSelected;

    private int lineColor;

    private int mLineHeightSelected;

    private int lineHeightUnselected;

    private float mX;

    private int mTextColorSelected;

    private int mTextColorUnselected;

    private int mTextDistanceFromLine;

    private Drawable backgroundUnselected;
    private Paint textPaint;

    private Paint linePaint;
    
    private int buttonIndex;

    private OnClickListener mOnClickListener;

    public SegmentedControlButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public SegmentedControlButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs);
    }

    public Drawable getBackgroundSelected() {
        return backgroundSelected;
    }

    public int getLineColor() {
        return lineColor;
    }

    public int getLineHeightUnselected() {
        return lineHeightUnselected;
    }

    public void init(AttributeSet attrs) {

        if (attrs != null) {
            TypedArray attributes = this.getContext().obtainStyledAttributes(attrs, R.styleable.SegmentedControlButton);
            if (backgroundSelected == null) {
                Drawable d = attributes.getDrawable(R.styleable.SegmentedControlButton_backgroundSelected);
                backgroundSelected = d == null ? getBackground() : d;
            }

            if (backgroundUnselected == null) {
                backgroundUnselected = this.getBackground();
            }

            this.lineColor = attributes.getColor(R.styleable.SegmentedControlButton_lineColor, 0);
            this.mTextColorUnselected = attributes.getColor(R.styleable.SegmentedControlButton_textColorUnselected, 0);
            this.mTextColorSelected = attributes.getColor(R.styleable.SegmentedControlButton_textColorSelected, 0);
            this.lineHeightUnselected = attributes.getDimensionPixelSize(R.styleable.SegmentedControlButton_lineHeightUnselected, 0);
            this.mLineHeightSelected = attributes.getDimensionPixelSize(R.styleable.SegmentedControlButton_lineHeightSelected, 0);
            this.mTextDistanceFromLine = attributes.getDimensionPixelSize(R.styleable.SegmentedControlButton_textDistanceFromLine, 0);

            textPaint = new Paint();
            textPaint.setAntiAlias(true);
            textPaint.setTextSize(this.getTextSize());
            textPaint.setTextAlign(Paint.Align.CENTER);
            linePaint = new Paint();
            linePaint.setColor(this.getLineColor());
            linePaint.setStyle(Style.FILL);
        }

        this.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    setBackgroundDrawable(backgroundSelected);
                } else {
                    setBackgroundDrawable(backgroundUnselected);
                }
            }
        });

        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                Log.d("SegmentedControlButton", motionEvent.toString());
                if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    Log.d("SegmentedControlButton", "Button released");
                    if (!isChecked())
                        SegmentedControlButton.this.setBackgroundDrawable(backgroundUnselected);
                    if (mOnClickListener != null)
                        mOnClickListener.onClick(SegmentedControlButton.this);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.d("SegmentedControlButton", "Button pressed");
                    SegmentedControlButton.this.setBackgroundDrawable(backgroundSelected);
                } else if (motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    SegmentedControlButton.this.setBackgroundDrawable(backgroundUnselected);
                }
                return true;
            }
        });
    }

    @Override
    public void setOnClickListener(OnClickListener onClickListener) {
        mOnClickListener = onClickListener;
    }

    @Override
    public void onDraw(Canvas canvas) {

        String text = this.getText().toString();
        int lineHeight;
        if (isChecked()) {
            lineHeight = mLineHeightSelected;
            textPaint.setColor(mTextColorSelected);
        } else {
            lineHeight = this.getLineHeightUnselected();
            textPaint.setColor(mTextColorUnselected);
        }

        int textHeightPos = this.getHeight() - mLineHeightSelected - mTextDistanceFromLine;

        float x = mX;

        Drawable background = getBackground();
        background.setBounds(0, 0, getWidth(), getHeight());
        background.draw(canvas);

        canvas.drawText(text, x, textHeightPos, textPaint);

        if (lineHeight > 0) {
            Rect rect = new Rect(0, this.getHeight() - lineHeight, getWidth(), this.getHeight());
            canvas.drawRect(rect, linePaint);
        }

    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        mX = w * 0.5f; // remember the center of the screen
    }

    public void setLineColor(int lineColor) {
        this.lineColor = lineColor;
    }

    public void setTextColorSelected(int textColorSelected) {
        this.mTextColorSelected = textColorSelected;
    }

    public void setTextColorUnselected(int textColor) {
        this.mTextColorUnselected = textColor;
    }

    public void setTextDistanceFromLine(int textDistanceFromLine) {
        mTextDistanceFromLine = textDistanceFromLine;
    }

	public int getButtonIndex() {
		return buttonIndex;
	}

	public void setButtonIndex(int buttonIndex) {
		this.buttonIndex = buttonIndex;
	}

}
