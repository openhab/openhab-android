/*
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Style
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatRadioButton
import org.openhab.habdroid.R

/**
 * This class is based on SegmentedControlButton by Benjamin Ferrari
 * http://bookworm.at
 * https://github.com/bookwormat/segcontrol
 * And extended to meet openHAB needs
 *
 * @author benjamin ferrari
 */
class SegmentedControlButton constructor(context: Context, attrs: AttributeSet?) :
    AppCompatRadioButton(context, attrs) {
    private val underlineHeight: Int
    private val textDistanceFromLine: Int

    private val textPaint: Paint
    private val linePaint: Paint

    private val backgroundColorList: ColorStateList?
    private val backgroundPaint: Paint?

    init {
        buttonDrawable = null

        textPaint = Paint()
        textPaint.isAntiAlias = true
        textPaint.textSize = textSize
        textPaint.textAlign = Paint.Align.CENTER

        linePaint = Paint()
        linePaint.style = Style.FILL

        context.obtainStyledAttributes(attrs, R.styleable.SegmentedControlButton).apply {
            underlineHeight = getDimensionPixelSize(R.styleable.SegmentedControlButton_underlineHeight, 0)
            textDistanceFromLine = getDimensionPixelSize(R.styleable.SegmentedControlButton_textDistanceFromLine, 0)
            linePaint.color = getColor(R.styleable.SegmentedControlButton_underlineColor, 0)

            @IdRes val bgColorResId = getResourceId(R.styleable.SegmentedControlButton_backgroundColor, 0)
            if (bgColorResId != 0) {
                backgroundColorList = AppCompatResources.getColorStateList(getContext(), bgColorResId)
                backgroundPaint = Paint()
            } else {
                backgroundColorList = null
                backgroundPaint = null
            }

            recycle()
        }
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

    override fun onDraw(canvas: Canvas) {
        if (backgroundPaint != null) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        }

        background?.setBounds(0, 0, width, height)
        background?.draw(canvas)

        val textHeightPos = height - compoundPaddingBottom

        textPaint.color = currentTextColor
        canvas.drawText(text.toString(), (width / 2).toFloat(), textHeightPos.toFloat(), textPaint)

        if (underlineHeight > 0) {
            canvas.drawRect(0f, (height - underlineHeight).toFloat(), width.toFloat(), height.toFloat(), linePaint)
        }
    }
}
