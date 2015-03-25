package org.openhab.habdroid.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;

import org.openhab.habdroid.R;

/**
 * TODO: document your custom view class.
 */
public class ShutterDownTriangleView extends View {

    private Paint mPaint;

    private Paint mTextPaint;

    public ShutterDownTriangleView(Context context) {
        super(context);
        init(null, 0);
    }

    public ShutterDownTriangleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ShutterDownTriangleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    @Override
    public boolean isInEditMode() {
        return true;
    }

    private void init(AttributeSet attrs, int defStyle) {
        mPaint = new Paint();
        mPaint.setColor(getResources().getColor(R.color.downTriangleGreen));
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setAntiAlias(true);

        mTextPaint = new Paint();
        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setStrokeWidth(2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Point left = new Point(10, (canvas.getHeight() / 2) + 10);
        Point right = new Point(canvas.getWidth() - 10, (canvas.getHeight() / 2) + 10);
        Point bottom = new Point(canvas.getWidth() / 2, canvas.getHeight() - 10);
        Path trianglePath = new Path();
        trianglePath.setFillType(Path.FillType.EVEN_ODD);
        trianglePath.moveTo(left.x, left.y);
        trianglePath.lineTo(right.x, right.y);
        trianglePath.lineTo(bottom.x, bottom.y);
        trianglePath.lineTo(left.x, left.y);
        trianglePath.close();
        canvas.drawPath(trianglePath, mPaint);

        canvas.drawLine((canvas.getWidth() / 2) - 32, canvas.getHeight() - 74, canvas.getWidth() / 2, canvas.getHeight() - 42, mTextPaint);
        canvas.drawLine(canvas.getWidth() / 2, canvas.getHeight() - 42, (canvas.getWidth() / 2) + 32, canvas.getHeight() - 74, mTextPaint);
    }
}
