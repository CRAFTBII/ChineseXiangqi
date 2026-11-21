package com.workbuddy.xiangqi.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseArray;

import com.workbuddy.xiangqi.R;

/**
 * 木质主题：贴图加载（带缓存）、色板、圆角面板工厂。
 * 贴图来源 ambientCG（CC0 公共领域），经 tools/TexPrep.java 做色板映射后打包。
 */
public final class Theme {

    /** 深胡桃木桌面。 */
    public static final int WOOD_DARK = 0xFF2A1A0C;
    /** 面板底色（半透明深木）。 */
    public static final int PANEL = 0xE01F1308;
    public static final int PANEL_SOFT = 0xB31B1006;
    /** 金色描边与高亮。 */
    public static final int GOLD = 0xFFE9C07A;
    public static final int GOLD_DEEP = 0xFFC2913F;
    /** 文本。 */
    public static final int TEXT = 0xFFF5E7CE;
    public static final int TEXT_DIM = 0xFFC0A57C;
    public static final int TEXT_FAINT = 0xFF8B7355;
    /** 红方。 */
    public static final int RED = 0xFFB5271B;
    public static final int RED_LIGHT = 0xFFE05A46;

    private static final SparseArray<Bitmap> TEX = new SparseArray<>();
    private static Typeface serif;
    private static Typeface serifBold;

    private Theme() {
    }

    public static Typeface serif() {
        if (serif == null) {
            serif = Typeface.create("serif", Typeface.NORMAL);
        }
        return serif;
    }

    public static Typeface serifBold() {
        if (serifBold == null) {
            serifBold = Typeface.create("serif", Typeface.BOLD);
        }
        return serifBold;
    }

    /** 加载 drawable-nodpi 贴图，不做密度缩放。 */
    public static Bitmap tex(Context c, int resId) {
        Bitmap cached = TEX.get(resId);
        if (cached != null) {
            return cached;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeResource(c.getResources(), resId, o);
        if (bmp != null) {
            TEX.put(resId, bmp);
        }
        return bmp;
    }

    public static Bitmap boardTex(Context c) {
        return tex(c, R.drawable.tex_board);
    }

    public static Bitmap frameTex(Context c) {
        return tex(c, R.drawable.tex_frame);
    }

    public static Bitmap pieceTex(Context c) {
        return tex(c, R.drawable.tex_piece);
    }

    /** 铺满整屏的深木背景。 */
    public static BitmapDrawable tableBackground(Context c) {
        Bitmap bmp = tex(c, R.drawable.tex_table);
        BitmapDrawable d = new BitmapDrawable(c.getResources(), bmp);
        d.setTileModeXY(Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        return d;
    }

    /** 用贴图填充一个矩形（等比铺满，可指定明暗叠加）。 */
    public static Shader fillShader(Bitmap bmp, RectF dst, int overlay) {
        if (bmp == null) {
            return new LinearGradient(dst.left, dst.top, dst.right, dst.bottom,
                    Color.TRANSPARENT, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        }
        BitmapShader shader = new BitmapShader(bmp, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRectToRect(new RectF(0, 0, bmp.getWidth(), bmp.getHeight()), dst,
                android.graphics.Matrix.ScaleToFit.FILL);
        shader.setLocalMatrix(m);
        return shader;
    }

    public static GradientDrawable round(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) {
            d.setStroke(Math.max(1, Math.round(dp(c, 1f))), stroke);
        }
        return d;
    }

    public static GradientDrawable roundAll(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = round(c, fill, stroke, radiusDp);
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    public static GradientDrawable oval(Context c, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        if (stroke != 0) {
            d.setStroke(Math.max(1, Math.round(dp(c, 1f))), stroke);
        }
        return d;
    }

    public static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }

    public static int dpi(Context c, float v) {
        return Math.round(dp(c, v));
    }

    /** 在画布上铺一层木纹（用于自绘背景）。 */
    public static void paintWood(Canvas canvas, Rect dst, Bitmap tex, int tint, int alpha) {
        if (tex == null) {
            return;
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(fillShader(tex, new RectF(dst), 0));
        p.setAlpha(alpha);
        canvas.drawRect(dst, p);
        if (Color.alpha(tint) > 0) {
            p.setShader(null);
            p.setColor(tint);
            canvas.drawRect(dst, p);
        }
    }
}
