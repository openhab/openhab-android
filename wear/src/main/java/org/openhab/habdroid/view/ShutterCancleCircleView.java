package org.openhab.habdroid.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * TODO: document your custom view class.
 */
public class ShutterCancleCircleView extends View {

    private Paint mPaint;

    private Paint mTextPaint;

    public ShutterCancleCircleView(Context context) {
        super(context);
        init(null, 0);
    }

    public ShutterCancleCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ShutterCancleCircleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    private void init(AttributeSet attrs, int defStyle) {
        mPaint = new Paint();
        mPaint.setColor(Color.BLUE);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setAntiAlias(true);

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(38);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(canvas.getWidth() / 2, canvas.getHeight() / 2, 75, mPaint);
        canvas.drawText("X", (canvas.getWidth() / 2) - 15, (canvas.getHeight() / 2) + 15, mTextPaint);
    }
}
