package com.workbuddy.xiangqi.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * 局面评估条：中点为均势，红方优势向左扩展、黑方优势向右扩展。
 * 分值按 tanh 压缩，避免中局小幅波动造成视觉跳动。
 */
public class EvalBar extends View {

    private static final int TRACK = 0xFF2A1B0C;
    private static final int RED_EDGE = 0xFFB23A26;
    private static final int BLACK_EDGE = 0xFF57636E;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float ratio = 0.5f;

    public EvalBar(Context context) {
        super(context);
    }

    /** @param centipawns 红方视角分值（红优为正） */
    public void setScore(int centipawns) {
        double clamped = Math.max(-1500, Math.min(1500, centipawns));
        ratio = (float) (0.5 + 0.5 * Math.tanh(clamped / 700.0));
        invalidate();
    }

    public void reset() {
        ratio = 0.5f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float radius = h / 2f;
        rect.set(0, 0, w, h);
        paint.setShader(null);
        paint.setColor(TRACK);
        canvas.drawRoundRect(rect, radius, radius, paint);

        float split = Math.max(radius, Math.min(w - radius, w * ratio));

        rect.set(0, 0, split, h);
        paint.setShader(new LinearGradient(0, 0, split, 0,
                new int[]{0xFF6E1A10, RED_EDGE}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, paint);

        rect.set(split, 0, w, h);
        paint.setShader(new LinearGradient(split, 0, w, 0,
                new int[]{BLACK_EDGE, 0xFF3D4A56}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);

        // 中线刻度
        paint.setColor(0x66FFE6B8);
        canvas.drawRect(w / 2f - 1f, h * 0.28f, w / 2f + 1f, h * 0.72f, paint);

        // 分界
        paint.setColor(0xCCFFF3DA);
        canvas.drawRect(split - 1.2f, h * 0.14f, split + 1.2f, h * 0.86f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0x33E9C07A);
        rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
