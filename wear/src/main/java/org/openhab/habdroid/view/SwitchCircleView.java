package org.openhab.habdroid.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.openhab.habdroid.R;

/**
 * TODO: document your custom view class.
 */
public class SwitchCircleView extends View {

    private Paint mPaint;

    private Paint mBackPaint;

    private Paint mPaintActive;

    private Paint mTextPaint;

    private String mString;

    private boolean mOn;

    public SwitchCircleView(Context context) {
        super(context);
        init(null, 0);
    }

    public SwitchCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public SwitchCircleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public void setCurrentState(boolean currentState) {
        mOn = currentState;
    }

    public void setText(String text) { mString = text; }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    private void init(AttributeSet attrs, int defStyle) {
        mPaint = new Paint();
        mPaint.setColor(getResources().getColor(R.color.light_gray));
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setAntiAlias(true);

        mPaintActive = new Paint();
        mPaintActive.setColor(getResources().getColor(R.color.cancel_blue));
        mPaintActive.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaintActive.setAntiAlias(true);

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setStrokeWidth(2);
        mTextPaint.setTextSize(34);

        mBackPaint = new Paint();
        mBackPaint.setColor(Color.BLACK);

        final TypedArray attributes = getContext().obtainStyledAttributes(attrs, R.styleable.SwitchCircleView, defStyle, 0);
        mString = attributes.getString(0);
        mOn = attributes.getBoolean(1, false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int x = getMeasuredWidth() / 2;
        int y = getMeasuredHeight() / 2;
        Paint p;
        if (mOn) {
            p = mPaintActive;
        } else {
            p = mPaint;
        }

        Rect textArea = new Rect(0, 0, getMeasuredWidth(), getMeasuredHeight());
        canvas.drawRect(textArea, mBackPaint);
        RectF bounds = new RectF(textArea);
        bounds.right = mTextPaint.measureText(mString, 0, mString.length());
        bounds.bottom = mTextPaint.descent() - mTextPaint.ascent();
        bounds.left += (textArea.width() - bounds.right) / 2;
        bounds.top += (textArea.height() - bounds.bottom) / 2;
        canvas.drawCircle(x, y, 85, p);
        canvas.drawText(mString, bounds.left, bounds.top - mTextPaint.ascent(), mTextPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int desiredWidth = 190;
        int desiredHeight = 190;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        //Measure Width
        if (widthMode == MeasureSpec.EXACTLY) {
            //Must be this size
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            width = Math.min(desiredWidth, widthSize);
        } else {
            //Be whatever you want
            width = desiredWidth;
        }

        //Measure Height
        if (heightMode == MeasureSpec.EXACTLY) {
            //Must be this size
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            //Can't be bigger than...
            height = Math.min(desiredHeight, heightSize);
        } else {
            //Be whatever you want
            height = desiredHeight;
        }

        //MUST CALL THIS
        setMeasuredDimension(width, height);
    }
}
