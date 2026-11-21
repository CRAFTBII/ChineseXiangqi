package com.workbuddy.xiangqi.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * 底部导航用的线性图标（Canvas 绘制，无位图依赖）。
 */
public class IconView extends View {

    public static final int MENU = 0;
    public static final int BOOK = 1;
    public static final int PREV = 2;
    public static final int NEXT = 3;
    public static final int CHART = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int type;
    private int color = Theme.GOLD;

    public IconView(Context context, int type) {
        super(context);
        this.type = type;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setType(int type) {
        this.type = type;
        invalidate();
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth(), getHeight());
        if (s <= 0) {
            return;
        }
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float u = s * 0.5f;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, s * 0.085f));
        path.reset();

        switch (type) {
            case MENU: {
                float half = u * 0.62f;
                for (int i = -1; i <= 1; i++) {
                    float y = cy + i * u * 0.42f;
                    canvas.drawLine(cx - half, y, cx + half, y, paint);
                }
                break;
            }
            case BOOK: {
                float w = u * 0.66f;
                float h = u * 0.56f;
                rect.set(cx - w, cy - h, cx, cy + h);
                canvas.drawRoundRect(rect, u * 0.10f, u * 0.10f, paint);
                rect.set(cx, cy - h, cx + w, cy + h);
                canvas.drawRoundRect(rect, u * 0.10f, u * 0.10f, paint);
                canvas.drawLine(cx, cy - h, cx, cy + h, paint);
                break;
            }
            case PREV:
            case NEXT: {
                boolean left = type == PREV;
                float dir = left ? -1f : 1f;
                rect.set(cx - u * 0.62f, cy - u * 0.62f, cx + u * 0.62f, cy + u * 0.62f);
                canvas.drawArc(rect, 0, 360, false, paint);
                path.moveTo(cx + dir * u * 0.26f, cy - u * 0.30f);
                path.lineTo(cx - dir * u * 0.20f, cy);
                path.lineTo(cx + dir * u * 0.26f, cy + u * 0.30f);
                canvas.drawPath(path, paint);
                break;
            }
            case CHART:
            default: {
                float base = cy + u * 0.52f;
                float bw = u * 0.20f;
                float[] hs = {0.36f, 0.66f, 0.50f};
                for (int i = 0; i < 3; i++) {
                    float x = cx - u * 0.50f + i * u * 0.36f;
                    rect.set(x, base - u * hs[i], x + bw, base);
                    canvas.drawRoundRect(rect, bw * 0.4f, bw * 0.4f, paint);
                }
                canvas.drawLine(cx - u * 0.62f, base + u * 0.10f,
                        cx + u * 0.62f, base + u * 0.10f, paint);
                break;
            }
        }
    }
}
