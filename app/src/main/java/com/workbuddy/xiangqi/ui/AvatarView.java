package com.workbuddy.xiangqi.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** 圆形头像占位：木质底盘 + 金色描边 + 汉字。 */
public class AvatarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String glyph = "帅";
    private boolean redSide = true;
    private boolean active = true;

    public AvatarView(Context context) {
        super(context);
    }

    public void setGlyph(String glyph) {
        this.glyph = glyph;
        invalidate();
    }

    public void setRedSide(boolean redSide) {
        this.redSide = redSide;
        invalidate();
    }

    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        float ring = Math.max(2f, r * 0.10f);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        canvas.drawCircle(cx, cy + r * 0.05f, r - ring * 0.4f, paint);

        paint.setShader(new RadialGradient(cx - r * 0.25f, cy - r * 0.30f, r * 1.15f,
                new int[]{0xFF6E4A24, 0xFF432A12, 0xFF231407},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r - ring * 0.5f, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ring * 0.75f);
        paint.setColor(active ? Theme.GOLD : 0xFF8A7355);
        canvas.drawCircle(cx, cy, r - ring * 0.5f, paint);

        paint.setStyle(Paint.Style.FILL);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Theme.serifBold());
        tp.setTextSize(r * 0.95f);
        tp.setColor(redSide ? 0xFFF0C08A : 0xFFE6D6B8);
        Paint.FontMetrics fm = tp.getFontMetrics();
        canvas.drawText(glyph, cx, cy - (fm.ascent + fm.descent) / 2f, tp);
    }
}
