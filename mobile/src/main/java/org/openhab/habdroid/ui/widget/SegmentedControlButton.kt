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

package org.openhab.habdroid.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.drawable.Drawable
import android.util.AttributeSet

import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatRadioButton

import org.openhab.habdroid.R

/** @author benjamin ferrari
 */
class SegmentedControlButton constructor(context: Context, attrs: AttributeSet?, defStyle: Int = 0) :
        AppCompatRadioButton(context, attrs, defStyle) {
    private val underlineHeight: Int
    private val textDistanceFromLine: Int

    private val textPaint: Paint
    private val linePaint: Paint

    private val backgroundColorList: ColorStateList?
    private val backgroundPaint: Paint?

    init {
        buttonDrawable = null

        val a = if (attrs != null) context.obtainStyledAttributes(attrs, R.styleable.SegmentedControlButton) else null

        underlineHeight = a?.getDimensionPixelSize(R.styleable.SegmentedControlButton_underlineHeight, 0) ?: 0
        textDistanceFromLine = a?.getDimensionPixelSize(R.styleable.SegmentedControlButton_textDistanceFromLine, 0) ?: 0

        textPaint = Paint()
        textPaint.isAntiAlias = true
        textPaint.textSize = textSize
        textPaint.textAlign = Paint.Align.CENTER

        @ColorInt val lineColor = a?.getColor(R.styleable.SegmentedControlButton_underlineColor, 0) ?: Color.TRANSPARENT
        linePaint = Paint()
        linePaint.color = lineColor
        linePaint.style = Style.FILL

        @IdRes val bgColorResId = a?.getResourceId(
                R.styleable.SegmentedControlButton_backgroundColor, 0) ?: 0
        if (bgColorResId != 0) {
            backgroundColorList = AppCompatResources.getColorStateList(getContext(), bgColorResId)
            backgroundPaint = Paint()
        } else {
            backgroundColorList = null
            backgroundPaint = null
        }

        a?.recycle()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (backgroundColorList != null && backgroundPaint != null) {
            @ColorInt val newColor = backgroundColorList.getColorForState(drawableState, 0)
            if (newColor != backgroundPaint.color) {
                backgroundPaint.color = newColor
                invalidate()
            }
        }
    }

    override fun toggle() {
        // Prevent super method: we rely solely on our parent to update the checked state
    }

    override fun getCompoundPaddingBottom(): Int {
        return Math.max(super.getCompoundPaddingBottom(), underlineHeight + textDistanceFromLine)
    }

    public override fun onDraw(canvas: Canvas) {
        if (backgroundPaint != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        }

        val background = background
        if (background != null) {
            background.setBounds(0, 0, width, height)
            background.draw(canvas)
        }

        val text = text.toString()
        val textHeightPos = height - compoundPaddingBottom

        textPaint.color = currentTextColor
        canvas.drawText(text, (width / 2).toFloat(), textHeightPos.toFloat(), textPaint)

        if (underlineHeight > 0) {
            canvas.drawRect(0f, (height - underlineHeight).toFloat(), width.toFloat(), height.toFloat(), linePaint)
        }
    }
}
