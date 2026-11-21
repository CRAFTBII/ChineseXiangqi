package com.workbuddy.xiangqi.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

import com.workbuddy.xiangqi.game.Board;
import com.workbuddy.xiangqi.game.Piece;

import java.util.HashMap;

/**
 * 实木棋盘视图：胡桃木外框 + 浅木纹棋盘面 + 车削木质棋子。
 * 所有几何量按格距 cell 推导，适配任意尺寸。
 */
public class BoardView extends View {

    public interface Listener {
        void onBoardTapped(int square);
    }

    /** 网格外沿到浅色木面的距离（单位：格）。 */
    private static final float PAD_SURFACE = 0.34f;
    /** 浅色木面到外框外沿的距离（单位：格）。 */
    private static final float PAD_FRAME = 0.38f;
    private static final float PIECE_R = 0.462f;

    private static final int COLOR_LINE = 0xC25A3A16;
    private static final int COLOR_LINE_LIGHT = 0x55FFF3D8;
    private static final int COLOR_RIVER = 0xC0805A2E;
    private static final int COLOR_MARK = 0xFFD9B26A;
    private static final int COLOR_LAST = 0xFF2F7A52;
    private static final int COLOR_CHECK = 0xFFE23B2A;

    private static final int TARGET_CAPACITY = 128;

    private Board board;
    private Listener listener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path = new Path();
    private final RectF frameRect = new RectF();
    private final RectF surfaceRect = new RectF();
    private final RectF gridRect = new RectF();

    private Bitmap pieceTex;

    private float cell;
    private float originX;
    private float originY;

    private int selected = -1;
    private final int[] targets = new int[TARGET_CAPACITY];
    private int targetCount;
    private int lastFrom = -1;
    private int lastTo = -1;
    private int hintFrom = -1;
    private int hintTo = -1;
    private boolean flipped;
    private int checkSide;
    private boolean interactive = true;

    private final HashMap<Integer, Bitmap> pieceCache = new HashMap<>();
    private int cacheCellPx;
    private int piecePad;

    // 落子动画
    private int animFrom = -1;
    private int animTo = -1;
    private int animPiece;
    private float animT = 1f;
    private long lastFrameNanos;

    public BoardView(Context context) {
        super(context);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Theme.serifBold());
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Theme.serif());
    }

    public void setBoard(Board board) {
        this.board = board;
        invalidate();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        invalidate();
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void setCheckSide(int side) {
        this.checkSide = side;
        invalidate();
    }

    public void setLastMove(int from, int to) {
        this.lastFrom = from;
        this.lastTo = to;
        invalidate();
    }

    /** 引擎建议着法标记（金色角标）。 */
    public void setHint(int from, int to) {
        this.hintFrom = from;
        this.hintTo = to;
        invalidate();
    }

    public void clearSelection() {
        selected = -1;
        targetCount = 0;
        invalidate();
    }

    public void showSelection(int square, int[] legalTargets, int count) {
        selected = square;
        targetCount = Math.min(count, targets.length);
        System.arraycopy(legalTargets, 0, targets, 0, targetCount);
        invalidate();
    }

    public int getSelected() {
        return selected;
    }

    public boolean isTarget(int square) {
        for (int i = 0; i < targetCount; i++) {
            if (targets[i] == square) {
                return true;
            }
        }
        return false;
    }

    /** 触发落子动画（棋盘状态需已更新）。 */
    public void animateMove(int from, int to) {
        animFrom = from;
        animTo = to;
        animPiece = board != null ? board.pieceAt(to) : 0;
        animT = 0f;
        lastFrameNanos = 0L;
        postInvalidateOnAnimation();
    }

    // ------------------------------------------------------------ 几何

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float span = PAD_SURFACE + PAD_FRAME;
        float cellW = w / (8f + 2f * span);
        float cellH = h / (9f + 2f * span);
        cell = Math.min(cellW, cellH);
        originX = (w - cell * 8f) / 2f;
        originY = (h - cell * 9f) / 2f;

        gridRect.set(originX, originY, originX + cell * 8f, originY + cell * 9f);
        surfaceRect.set(gridRect);
        surfaceRect.inset(-PAD_SURFACE * cell, -PAD_SURFACE * cell);
        frameRect.set(surfaceRect);
        frameRect.inset(-PAD_FRAME * cell, -PAD_FRAME * cell);

        pieceTex = Theme.pieceTex(getContext());

        pieceCache.clear();
        cacheCellPx = 0;
        buildPieceCache();
    }

    private float pxF(float col) {
        return originX + (flipped ? (8f - col) : col) * cell;
    }

    private float pyF(float row) {
        return originY + (flipped ? (9f - row) : row) * cell;
    }

    private float cx(int square) {
        return pxF(square % Board.COLS);
    }

    private float cy(int square) {
        return pyF(square / Board.COLS);
    }

    private float pieceRadius() {
        return cell * PIECE_R;
    }

    // ------------------------------------------------------------ 绘制

    @Override
    protected void onDraw(Canvas canvas) {
        if (board == null || cell <= 0) {
            return;
        }
        advanceAnimation();
        resetPaint();
        drawTable(canvas);
        drawFrame(canvas);
        drawSurface(canvas);
        drawGrid(canvas);
        drawPositionMarks(canvas);
        drawRiverText(canvas);
        drawLastMove(canvas);
        drawHint(canvas);
        drawPieces(canvas);
        drawSelection(canvas);
    }

    private void resetPaint() {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setColor(0xFF000000);
    }

    private void advanceAnimation() {
        if (animT >= 1f) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            postInvalidateOnAnimation();
            return;
        }
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        animT = Math.min(1f, animT + dt * 4.2f);
        if (animT >= 1f) {
            animFrom = -1;
            animTo = -1;
        } else {
            postInvalidateOnAnimation();
        }
    }

    private void drawTable(Canvas canvas) {
        paint.setShader(null);
        paint.setAlpha(255);
        Bitmap tex = Theme.tex(getContext(), com.workbuddy.xiangqi.R.drawable.tex_table);
        if (tex != null) {
            canvas.drawBitmap(tex, null, new RectF(0, 0, getWidth(), getHeight()), paint);
        }
        paint.setColor(0x2A000000);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }

    private void drawFrame(Canvas canvas) {
        float radius = cell * 0.07f;

        // 投影
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 3; i >= 1; i--) {
            float off = cell * 0.05f * i;
            RectF r = new RectF(frameRect);
            r.offset(0, off * 0.8f);
            r.inset(-off, -off);
            paint.setColor(0x1E000000);
            canvas.drawRoundRect(r, radius + off, radius + off, paint);
        }

        // 框体木纹（裁剪到圆角矩形后直接贴图）
        Bitmap frameBmp = Theme.frameTex(getContext());
        int sc = canvas.save();
        path.reset();
        path.addRoundRect(frameRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(path);
        if (frameBmp != null) {
            paint.setShader(null);
            paint.setAlpha(255);
            canvas.drawBitmap(frameBmp, null, frameRect, paint);
        }
        canvas.restoreToCount(sc);

        // 斜向明暗，做出倒角
        paint.setShader(new LinearGradient(frameRect.left, frameRect.top,
                frameRect.right, frameRect.bottom,
                new int[]{0x33FFFFFF, 0x00FFFFFF, 0x44000000, 0x77000000},
                new float[]{0f, 0.42f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(frameRect, radius, radius, paint);
        paint.setShader(null);

        // 外沿描边
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, cell * 0.016f));
        paint.setColor(0x66000000);
        canvas.drawRoundRect(frameRect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    private void drawSurface(Canvas canvas) {
        Bitmap boardBmp = Theme.boardTex(getContext());
        paint.setShader(null);
        paint.setAlpha(255);
        if (boardBmp != null) {
            canvas.drawBitmap(boardBmp, null, surfaceRect, paint);
        }
        paint.setColor(0xFFE2C79A);
        if (boardBmp == null) {
            canvas.drawRect(surfaceRect, paint);
        }

        // 内凹阴影：四条边向内渐隐
        float depth = cell * 0.24f;
        int[] edge = {0x36000000, 0x00000000};
        paint.setShader(new LinearGradient(surfaceRect.left, surfaceRect.top,
                surfaceRect.left, surfaceRect.top + depth,
                edge[0], edge[1], Shader.TileMode.CLAMP));
        canvas.drawRect(surfaceRect.left, surfaceRect.top,
                surfaceRect.right, surfaceRect.top + depth, paint);
        paint.setShader(new LinearGradient(surfaceRect.left, surfaceRect.bottom,
                surfaceRect.left, surfaceRect.bottom - depth * 0.6f,
                edge[0], edge[1], Shader.TileMode.CLAMP));
        canvas.drawRect(surfaceRect.left, surfaceRect.bottom - depth * 0.6f,
                surfaceRect.right, surfaceRect.bottom, paint);
        paint.setShader(new LinearGradient(surfaceRect.left, surfaceRect.top,
                surfaceRect.left + depth, surfaceRect.top,
                edge[0], edge[1], Shader.TileMode.CLAMP));
        canvas.drawRect(surfaceRect.left, surfaceRect.top,
                surfaceRect.left + depth, surfaceRect.bottom, paint);
        paint.setShader(new LinearGradient(surfaceRect.right, surfaceRect.top,
                surfaceRect.right - depth * 0.6f, surfaceRect.top,
                edge[0], edge[1], Shader.TileMode.CLAMP));
        canvas.drawRect(surfaceRect.right - depth * 0.6f, surfaceRect.top,
                surfaceRect.right, surfaceRect.bottom, paint);
        paint.setShader(null);
    }

    private void drawGrid(Canvas canvas) {
        float w = Math.max(1.2f, cell * 0.020f);
        linePaint.setStyle(Paint.Style.STROKE);

        // 先画浅色高光，形成"刻线"的凹陷感
        linePaint.setStrokeWidth(w);
        linePaint.setColor(COLOR_LINE_LIGHT);
        linePaint.setAlpha(150);
        drawGridLines(canvas, 0.7f);

        linePaint.setColor(COLOR_LINE);
        linePaint.setAlpha(255);
        drawGridLines(canvas, 0f);

        // 外框双线
        linePaint.setStrokeWidth(Math.max(1.6f, cell * 0.028f));
        linePaint.setColor(COLOR_LINE);
        linePaint.setAlpha(255);
        RectF outer = new RectF(gridRect);
        outer.inset(-cell * 0.20f, -cell * 0.20f);
        canvas.drawRect(outer, linePaint);
        linePaint.setStrokeWidth(Math.max(1.1f, cell * 0.016f));
        canvas.drawRect(gridRect, linePaint);

        // 九宫斜线
        linePaint.setStrokeWidth(Math.max(1.1f, cell * 0.018f));
        linePaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(pxF(3), pyF(0), pxF(5), pyF(2), linePaint);
        canvas.drawLine(pxF(5), pyF(0), pxF(3), pyF(2), linePaint);
        canvas.drawLine(pxF(3), pyF(7), pxF(5), pyF(9), linePaint);
        canvas.drawLine(pxF(5), pyF(7), pxF(3), pyF(9), linePaint);
        linePaint.setStyle(Paint.Style.FILL);
    }

    private void drawGridLines(Canvas canvas, float dy) {
        for (int r = 0; r < Board.ROWS; r++) {
            float y = pyF(r) + dy;
            canvas.drawLine(pxF(0), y, pxF(8), y, linePaint);
        }
        for (int c = 0; c < Board.COLS; c++) {
            float x = pxF(c) + dy;
            if (c == 0 || c == 8) {
                canvas.drawLine(x, pyF(0) + dy, x, pyF(9) + dy, linePaint);
            } else {
                canvas.drawLine(x, pyF(0) + dy, x, pyF(4) + dy, linePaint);
                canvas.drawLine(x, pyF(5) + dy, x, pyF(9) + dy, linePaint);
            }
        }
    }

    /** 炮位与兵位的传统角标。 */
    private void drawPositionMarks(Canvas canvas) {
        int[][] marks = {
                {2, 1}, {2, 7}, {7, 1}, {7, 7},
                {3, 0}, {3, 2}, {3, 4}, {3, 6}, {3, 8},
                {6, 0}, {6, 2}, {6, 4}, {6, 6}, {6, 8}
        };
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(COLOR_LINE);
        linePaint.setAlpha(215);
        linePaint.setStrokeWidth(Math.max(1.1f, cell * 0.018f));
        float gap = cell * 0.095f;
        float len = cell * 0.155f;
        for (int[] m : marks) {
            float x = pxF(m[1]);
            float y = pyF(m[0]);
            if (m[1] > 0) {
                canvas.drawLine(x - gap, y - gap, x - gap - len, y - gap, linePaint);
                canvas.drawLine(x - gap, y - gap, x - gap, y - gap - len, linePaint);
                canvas.drawLine(x - gap, y + gap, x - gap - len, y + gap, linePaint);
                canvas.drawLine(x - gap, y + gap, x - gap, y + gap + len, linePaint);
            }
            if (m[1] < 8) {
                canvas.drawLine(x + gap, y - gap, x + gap + len, y - gap, linePaint);
                canvas.drawLine(x + gap, y - gap, x + gap, y - gap - len, linePaint);
                canvas.drawLine(x + gap, y + gap, x + gap + len, y + gap, linePaint);
                canvas.drawLine(x + gap, y + gap, x + gap, y + gap + len, linePaint);
            }
        }
        linePaint.setAlpha(255);
        linePaint.setStyle(Paint.Style.FILL);
    }

    private void drawRiverText(Canvas canvas) {
        textPaint.setColor(COLOR_RIVER);
        textPaint.setTextSize(cell * 0.56f);
        textPaint.setAlpha(255);
        float y = (pyF(4) + pyF(5)) / 2f;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = y - (fm.ascent + fm.descent) / 2f;

        // 刻字高光
        textPaint.setColor(0x66FFF6DF);
        textPaint.setTextSize(cell * 0.56f);
        canvas.drawText("楚", pxF(1.6f), baseline + cell * 0.014f, textPaint);
        canvas.drawText("河", pxF(2.6f), baseline + cell * 0.014f, textPaint);
        canvas.drawText("漢", pxF(5.4f), baseline + cell * 0.014f, textPaint);
        canvas.drawText("界", pxF(6.4f), baseline + cell * 0.014f, textPaint);

        textPaint.setColor(COLOR_RIVER);
        canvas.drawText("楚", pxF(1.6f), baseline, textPaint);
        canvas.drawText("河", pxF(2.6f), baseline, textPaint);
        canvas.drawText("漢", pxF(5.4f), baseline, textPaint);
        canvas.drawText("界", pxF(6.4f), baseline, textPaint);
    }

    private void drawLastMove(Canvas canvas) {
        if (lastFrom < 0 || lastTo < 0) {
            return;
        }
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1.8f, cell * 0.033f));
        linePaint.setColor(COLOR_LAST);
        linePaint.setAlpha(210);
        float s = cell * 0.42f;
        drawCornerBox(canvas, cx(lastFrom), cy(lastFrom), s);
        drawCornerBox(canvas, cx(lastTo), cy(lastTo), s);
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setAlpha(255);
    }

    private void drawHint(Canvas canvas) {
        if (hintFrom < 0 || hintTo < 0) {
            return;
        }
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(2f, cell * 0.038f));
        linePaint.setColor(0xE6F2B438);
        linePaint.setAlpha(255);
        float s = cell * 0.46f;
        drawCornerBox(canvas, cx(hintFrom), cy(hintFrom), s);
        drawCornerBox(canvas, cx(hintTo), cy(hintTo), s);
        linePaint.setStyle(Paint.Style.FILL);
        linePaint.setAlpha(255);
    }

    private void drawCornerBox(Canvas canvas, float x, float y, float s) {
        float l = s * 0.44f;
        canvas.drawLine(x - s, y - s, x - s + l, y - s, linePaint);
        canvas.drawLine(x - s, y - s, x - s, y - s + l, linePaint);
        canvas.drawLine(x + s, y - s, x + s - l, y - s, linePaint);
        canvas.drawLine(x + s, y - s, x + s, y - s + l, linePaint);
        canvas.drawLine(x - s, y + s, x - s + l, y + s, linePaint);
        canvas.drawLine(x - s, y + s, x - s, y + s - l, linePaint);
        canvas.drawLine(x + s, y + s, x + s - l, y + s, linePaint);
        canvas.drawLine(x + s, y + s, x + s, y + s - l, linePaint);
    }

    private void drawPieces(Canvas canvas) {
        int r = Math.round(pieceRadius());
        for (int sq = 0; sq < Board.SIZE; sq++) {
            int piece = board.pieceAt(sq);
            if (piece == 0) {
                continue;
            }
            if (animT < 1f && sq == animTo) {
                continue;
            }
            Bitmap bmp = pieceCache.get(piece);
            if (bmp == null) {
                continue;
            }
            canvas.drawBitmap(bmp, cx(sq) - r - piecePad, cy(sq) - r - piecePad, null);
        }
        if (animT < 1f && animPiece != 0) {
            Bitmap bmp = pieceCache.get(animPiece);
            if (bmp != null) {
                float t = easeOut(animT);
                float x = cx(animFrom) + (cx(animTo) - cx(animFrom)) * t;
                float y = cy(animFrom) + (cy(animTo) - cy(animFrom)) * t;
                float lift = (float) Math.sin(t * Math.PI) * cell * 0.10f;
                canvas.drawBitmap(bmp, x - r - piecePad, y - lift - r - piecePad, null);
            }
        }
        if (selected >= 0) {
            drawSelectRing(canvas, cx(selected), cy(selected));
        }
    }

    private static float easeOut(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private void drawSelectRing(Canvas canvas, float x, float y) {
        float r = pieceRadius();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, r * 0.10f));
        paint.setColor(0x66FFD98A);
        paint.setAlpha(255);
        canvas.drawCircle(x, y, r * 1.06f, paint);
        paint.setStrokeWidth(Math.max(1.4f, r * 0.055f));
        paint.setColor(0xFFFFD061);
        canvas.drawCircle(x, y, r * 1.06f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------ 棋子位图

    /** 预渲染 14 种棋子（含投影与刻字），避免每帧重复绘制。 */
    private void buildPieceCache() {
        int r = Math.round(pieceRadius());
        if (r <= 0 || r == cacheCellPx) {
            return;
        }
        cacheCellPx = r;
        piecePad = Math.round(r * 0.34f);
        pieceCache.clear();
        for (int side : new int[]{Piece.RED, Piece.BLACK}) {
            for (int type = Piece.KING; type <= Piece.PAWN; type++) {
                int piece = Piece.make(side, type);
                pieceCache.put(piece, buildPiece(piece, r));
            }
        }
    }

    private Bitmap buildPiece(int piece, int r) {
        int pad = piecePad;
        int size = r * 2 + pad * 2;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = size / 2f;
        float cy = size / 2f;
        boolean red = Piece.isRed(piece);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 落影
        p.setShader(new RadialGradient(cx, cy + r * 0.14f, r * 1.24f,
                new int[]{0x62000000, 0x38000000, 0x00000000},
                new float[]{0.60f, 0.82f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx + r * 0.02f, cy + r * 0.15f, r * 1.24f, p);
        p.setShader(null);

        // 车削侧壁
        p.setColor(0xFF4A2E13);
        c.drawCircle(cx, cy + r * 0.115f, r * 0.995f, p);
        p.setColor(0xFF6B4724);
        c.drawCircle(cx, cy + r * 0.068f, r * 0.995f, p);

        // 棋面
        RectF dst = new RectF(cx - r * 0.955f, cy - r * 0.955f,
                cx + r * 0.955f, cy + r * 0.955f);
        if (pieceTex != null) {
            int sc = c.save();
            Path clip = new Path();
            clip.addCircle(cx, cy, r * 0.955f, Path.Direction.CW);
            c.clipPath(clip);
            p.setShader(null);
            p.setAlpha(255);
            c.drawBitmap(pieceTex, null, dst, p);
            c.restoreToCount(sc);
        } else {
            p.setColor(0xFFEFD9B2);
            c.drawCircle(cx, cy, r * 0.955f, p);
        }

        // 边缘暗环（倒角）
        p.setShader(new RadialGradient(cx, cy, r * 0.955f,
                new int[]{0x00000000, 0x00000000, 0x38000000, 0x6A000000},
                new float[]{0f, 0.76f, 0.91f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.955f, p);
        p.setShader(null);

        // 刻圈
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1.2f, r * 0.045f));
        p.setColor(0x5A000000);
        c.drawCircle(cx, cy, r * 0.945f, p);
        p.setStrokeWidth(Math.max(1f, r * 0.026f));
        p.setColor(0x3CFFF6E0);
        c.drawCircle(cx, cy, r * 0.900f, p);
        p.setStrokeWidth(Math.max(1f, r * 0.028f));
        p.setColor(red ? 0x38A6321C : 0x3833241A);
        c.drawCircle(cx, cy, r * 0.795f, p);
        p.setStyle(Paint.Style.FILL);

        // 顶部高光
        p.setShader(new RadialGradient(cx - r * 0.30f, cy - r * 0.34f, r * 0.95f,
                new int[]{0x46FFFFFF, 0x10FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.50f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 0.955f, p);
        p.setShader(null);

        // 刻字
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTypeface(Theme.serifBold());
        tp.setTextSize(r * 1.12f);
        Paint.FontMetrics fm = tp.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        String glyph = Piece.glyph(piece);
        tp.setColor(0x74FFF7E4);
        c.drawText(glyph, cx, baseline + r * 0.045f, tp);
        tp.setColor(red ? 0xFF8E1A0C : 0xFF2A1E13);
        c.drawText(glyph, cx, baseline, tp);

        return bmp;
    }

    private void drawSelection(Canvas canvas) {
        float r = pieceRadius();
        resetPaint();
        for (int i = 0; i < targetCount; i++) {
            int sq = targets[i];
            float x = cx(sq);
            float y = cy(sq);
            if (board.pieceAt(sq) != 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, cell * 0.045f));
                paint.setColor(0xCC33C46B);
                canvas.drawCircle(x, y, r * 1.02f, paint);
                paint.setStyle(Paint.Style.FILL);
            } else {
                paint.setColor(0xCC2E9C5C);
                canvas.drawCircle(x, y, cell * 0.105f, paint);
                paint.setColor(0x55FFFFFF);
                canvas.drawCircle(x - cell * 0.02f, y - cell * 0.028f,
                        cell * 0.045f, paint);
            }
        }

        if (checkSide != 0) {
            int king = board.kingPos(checkSide);
            if (king >= 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, cell * 0.05f));
                paint.setColor(COLOR_CHECK);
                paint.setAlpha(220);
                canvas.drawCircle(cx(king), cy(king), r * 1.10f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(255);
            }
        }
    }

    // ------------------------------------------------------------ 交互

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactive || board == null) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int sq = squareAt(event.getX(), event.getY());
            if (sq >= 0 && listener != null) {
                listener.onBoardTapped(sq);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private int squareAt(float x, float y) {
        float bestDist = Float.MAX_VALUE;
        int best = -1;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                float dx = x - pxF(c);
                float dy = y - pyF(r);
                float d = dx * dx + dy * dy;
                if (d < bestDist) {
                    bestDist = d;
                    best = r * Board.COLS + c;
                }
            }
        }
        float limit = cell * 0.60f;
        if (best >= 0 && bestDist <= limit * limit) {
            return best;
        }
        return -1;
    }

    public boolean isAnimating() {
        return animT < 1f;
    }

    public void finishAnimation() {
        animT = 1f;
        animFrom = -1;
        animTo = -1;
    }
}
