package com.workbuddy.xiangqi;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.workbuddy.xiangqi.ai.Evaluator;
import com.workbuddy.xiangqi.ai.SearchEngine;
import com.workbuddy.xiangqi.game.Board;
import com.workbuddy.xiangqi.game.Move;
import com.workbuddy.xiangqi.game.MoveGen;
import com.workbuddy.xiangqi.game.MoveList;
import com.workbuddy.xiangqi.game.Notation;
import com.workbuddy.xiangqi.game.Piece;
import com.workbuddy.xiangqi.ui.AvatarView;
import com.workbuddy.xiangqi.ui.BoardView;
import com.workbuddy.xiangqi.ui.EvalBar;
import com.workbuddy.xiangqi.ui.IconView;
import com.workbuddy.xiangqi.ui.Theme;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 对局界面：木质主题 + 天天象棋式上下信息条与底部导航栏。
 * 玩家执红（下方），AI 执黑。
 */
public class MainActivity extends Activity implements BoardView.Listener {

    public static final String EXTRA_DIFFICULTY = "difficulty";
    public static final String EXTRA_RESUME = "resume";
    public static final String EXTRA_ARCHIVE = "archiveIndex";

    private static final int SIDE_PLAYER = Piece.RED;
    private static final int PLAYER_HUMAN = 0;
    private static final int PLAYER_AI = 1;

    private static final String[] DIFF_NAME = {"简单", "普通", "困难"};
    private static final int[] DIFF_DEPTH = {3, 6, 10};
    private static final long[] DIFF_TIME_MS = {400L, 1800L, 5000L};

    private final Board board = new Board();
    private BoardView boardView;
    private EvalBar evalBar;
    private SearchEngine engine;
    private final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private AvatarView topAvatar;
    private AvatarView bottomAvatar;
    private TextView topName;
    private TextView topSub;
    private TextView topA;
    private TextView topB;
    private TextView bottomName;
    private TextView bottomSub;
    private TextView bottomA;
    private TextView bottomB;
    private TextView infoText;
    private TextView infoRight;
    private TextView reviewChip;
    private IconView[] navIcons = new IconView[5];
    private TextView[] navLabels = new TextView[5];
    private LinearLayout navBar;

    private int phase = PLAYER_HUMAN;
    private int difficulty = 1;
    private boolean busy;
    private boolean gameOver;
    private boolean analysisOn;
    private int aiGeneration;
    private int lastScore;

    /** 完整走法序列（存档、复盘基准）。 */
    private final ArrayList<Integer> live = new ArrayList<>();
    /** 悔棋后待重做的走法，末尾为下一手。 */
    private final ArrayList<Integer> redo = new ArrayList<>();
    /** -1 表示实时对局；>=0 表示复盘到第 N 手。 */
    private int reviewPly = -1;
    /** 是否来自历史对局（只读复盘）。 */
    private boolean archiveMode;
    private int archiveIndex = -1;
    /** 本局是否已写入归档，避免重复。 */
    private boolean savedToArchive;

    private long gameStartAt;
    private long elapsedBase;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tickTask = new Runnable() {
        @Override
        public void run() {
            updateMeta();
            ticker.postDelayed(this, 1000L);
        }
    };

    private FrameLayout pauseOverlay;
    private FrameLayout sheetOverlay;
    private FrameLayout resultOverlay;
    private View analysisCard;
    /** 终局结论，供 HUD 与结果面板复用。 */
    private String resultTitle = "";
    private String resultSub = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersive();
        setContentView(buildLayout());

        Intent intent = getIntent();
        difficulty = clampDifficulty(intent.getIntExtra(EXTRA_DIFFICULTY, 1));
        boolean resume = intent.getBooleanExtra(EXTRA_RESUME, false);
        archiveIndex = intent.getIntExtra(EXTRA_ARCHIVE, -1);

        if (archiveIndex >= 0) {
            loadArchive(archiveIndex);
        } else if (resume) {
            resumeGame();
        } else {
            newGame();
        }
        ticker.postDelayed(tickTask, 1000L);
    }

    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    private int clampDifficulty(int value) {
        return Math.max(0, Math.min(2, value));
    }

    // ------------------------------------------------------------ 布局

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Theme.tableBackground(this));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(14), dp(10), dp(10));
        root.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col.addView(buildPlayerStrip(false), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        evalBar = new EvalBar(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(7));
        barLp.topMargin = dp(5);
        barLp.bottomMargin = dp(4);
        barLp.leftMargin = dp(2);
        barLp.rightMargin = dp(2);
        col.addView(evalBar, barLp);

        FrameLayout boardBox = new FrameLayout(this);
        boardView = new BoardView(this);
        boardView.setListener(this);
        boardView.setBoard(board);
        boardBox.addView(boardView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        reviewChip = new TextView(this);
        reviewChip.setText("复盘中 · 点此返回");
        reviewChip.setTextColor(0xFF2A1A0C);
        reviewChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        reviewChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        reviewChip.setGravity(Gravity.CENTER);
        reviewChip.setPadding(dp(12), dp(5), dp(12), dp(5));
        reviewChip.setBackground(Theme.round(this, 0xFFE9C07A, 0x00000000, 14f));
        reviewChip.setVisibility(View.GONE);
        reviewChip.setOnClickListener(v -> {
            if (archiveMode) {
                enterReview(live.size());
            } else {
                exitReview();
            }
        });
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.gravity = Gravity.TOP | Gravity.END;
        chipLp.topMargin = dp(6);
        chipLp.rightMargin = dp(8);
        boardBox.addView(reviewChip, chipLp);

        col.addView(boardBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        col.addView(buildInfoLine(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(buildPlayerStrip(true), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        col.addView(buildNav(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        return root;
    }

    /** 玩家信息条：头像 + 名称 + 右侧统计。me=true 为玩家（下方）。 */
    private View buildPlayerStrip(boolean me) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Theme.round(this, 0x99180D05, 0x30E9C07A, 12f));
        row.setPadding(dp(9), 0, dp(12), 0);

        AvatarView avatar = new AvatarView(this);
        avatar.setRedSide(me);
        avatar.setGlyph(me ? "帅" : "将");
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(33), dp(33));
        avLp.rightMargin = dp(10);
        row.addView(avatar, avLp);
        if (me) {
            bottomAvatar = avatar;
        } else {
            topAvatar = avatar;
        }

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setTextColor(Theme.TEXT);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        TextView sub = new TextView(this);
        sub.setTextColor(Theme.TEXT_FAINT);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        sub.setPadding(0, dp(1), 0, 0);
        names.addView(name);
        names.addView(sub);
        row.addView(names, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        TextView a = new TextView(this);
        a.setTextColor(Theme.TEXT_DIM);
        a.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        a.setGravity(Gravity.END);
        TextView b = new TextView(this);
        b.setTextColor(Theme.TEXT_FAINT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        b.setGravity(Gravity.END);
        b.setPadding(0, dp(1), 0, 0);
        right.addView(a);
        right.addView(b);
        row.addView(right, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (me) {
            bottomName = name;
            bottomSub = sub;
            bottomA = a;
            bottomB = b;
        } else {
            topName = name;
            topSub = sub;
            topA = a;
            topB = b;
        }
        return row;
    }

    private View buildInfoLine() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(5), dp(4), dp(4));

        infoText = new TextView(this);
        infoText.setTextColor(Theme.TEXT_DIM);
        infoText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        infoText.setSingleLine(true);
        row.addView(infoText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        infoRight = new TextView(this);
        infoRight.setTextColor(Theme.TEXT_FAINT);
        infoRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        infoRight.setGravity(Gravity.END);
        infoRight.setSingleLine(true);
        row.addView(infoRight);
        return row;
    }

    private View buildNav() {
        navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setGravity(Gravity.CENTER_VERTICAL);
        navBar.setBackground(Theme.round(this, 0xD9160D05, 0x2EE9C07A, 14f));

        navIcons[0] = navItem(0, IconView.MENU, "菜单");
        navIcons[1] = navItem(1, IconView.BOOK, "棋谱");
        navIcons[2] = navItem(2, IconView.PREV, "上一步");
        navIcons[3] = navItem(3, IconView.NEXT, "下一步");
        navIcons[4] = navItem(4, IconView.CHART, "分析");
        return navBar;
    }

    private IconView navItem(int index, int icon, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setBackground(Theme.round(this, 0x00000000, 0x00000000, 10f));
        item.setClickable(true);

        IconView ic = new IconView(this, icon);
        ic.setColor(Theme.TEXT_DIM);
        item.addView(ic, new LinearLayout.LayoutParams(dp(21), dp(21)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        tv.setTextColor(Theme.TEXT_DIM);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(2), 0, 0);
        item.addView(tv);

        navIcons[index] = ic;
        navLabels[index] = tv;

        switch (index) {
            case 0:
                item.setOnClickListener(v -> showPauseMenu());
                break;
            case 1:
                item.setOnClickListener(v -> showMoveList());
                break;
            case 2:
                item.setOnClickListener(v -> stepBack());
                break;
            case 3:
                item.setOnClickListener(v -> stepForward());
                break;
            default:
                item.setOnClickListener(v -> toggleAnalysis());
                break;
        }

        navBar.addView(item, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return ic;
    }

    private int dp(float value) {
        return Theme.dpi(this, value);
    }

    // ------------------------------------------------------------ 对局流程

    private SearchEngine engine() {
        if (engine == null) {
            engine = new SearchEngine();
        }
        return engine;
    }

    private void newGame() {
        aiGeneration++;
        if (engine != null) {
            engine.cancel();
        }
        if (resultOverlay != null) {
            dismissResult(resultOverlay);
        }
        resultTitle = "";
        resultSub = "";
        board.reset();
        live.clear();
        redo.clear();
        reviewPly = -1;
        archiveMode = false;
        archiveIndex = -1;
        savedToArchive = false;
        gameOver = false;
        busy = false;
        analysisOn = false;
        phase = PLAYER_HUMAN;
        lastScore = 0;
        elapsedBase = 0L;
        gameStartAt = SystemClock.elapsedRealtime();
        boardView.setBoard(board);
        boardView.clearSelection();
        boardView.setLastMove(-1, -1);
        boardView.setHint(-1, -1);
        boardView.setCheckSide(0);
        boardView.setInteractive(true);
        evalBar.reset();
        infoText.setText("你执红先行 · 点棋子选中，再点目标落子");
        updateHud();
        updateMeta();
        updateNav();
        persist();
    }

    /** 打开历史对局的只读复盘。 */
    private void loadArchive(int index) {
        GameArchive.Record record = GameArchive.get(this, index);
        if (record == null || record.moves.length == 0) {
            archiveIndex = -1;
            newGame();
            return;
        }
        archiveMode = true;
        savedToArchive = true;
        gameOver = true;
        busy = false;
        phase = PLAYER_HUMAN;
        difficulty = clampDifficulty(record.difficulty);
        resultTitle = record.title;
        resultSub = record.subtitle;
        live.clear();
        for (int m : record.moves) {
            live.add(m);
        }
        redo.clear();
        board.replay(Board.START_FEN, toArray(live));
        elapsedBase = record.elapsedMs;
        gameStartAt = 0L;
        boardView.setBoard(board);
        boardView.clearSelection();
        boardView.setInteractive(false);
        boardView.setHint(-1, -1);
        boardView.setCheckSide(0);
        lastScore = Evaluator.evaluate(board);
        evalBar.setScore(lastScore);
        enterReview(live.size());
        infoText.setText(record.title + " · " + record.subtitle
                + " · 共 " + live.size() + " 手 · 可翻页复盘");
        updateHud();
        updateMeta();
    }

    private void resumeGame() {
        GameStore.Saved saved = GameStore.load(this);
        aiGeneration++;
        if (engine != null) {
            engine.cancel();
        }
        difficulty = clampDifficulty(saved.difficulty);
        board.replay(Board.START_FEN, saved.moves);
        live.clear();
        for (int m : saved.moves) {
            live.add(m);
        }
        redo.clear();
        reviewPly = -1;
        archiveMode = false;
        archiveIndex = -1;
        savedToArchive = saved.finished;
        elapsedBase = saved.elapsedMs;
        gameStartAt = SystemClock.elapsedRealtime();
        gameOver = false;
        busy = false;
        analysisOn = false;
        phase = PLAYER_HUMAN;

        boardView.setBoard(board);
        boardView.clearSelection();
        boardView.setHint(-1, -1);
        int last = board.lastMove();
        if (last != Move.NONE) {
            boardView.setLastMove(Move.from(last), Move.to(last));
        } else {
            boardView.setLastMove(-1, -1);
        }
        boardView.setCheckSide(MoveGen.inCheck(board, Piece.RED) ? Piece.RED
                : MoveGen.inCheck(board, Piece.BLACK) ? Piece.BLACK : 0);
        boardView.setInteractive(true);
        lastScore = Evaluator.evaluate(board);
        evalBar.setScore(lastScore);
        infoText.setText(saved.moves.length == 0
                ? "新对局 · 点击棋子开始" : "已恢复上次进度");
        updateHud();
        updateMeta();
        updateNav();

        // 恢复后先判定终局：存档里可能停在一个已分胜负的局面
        if (finishTurn()) {
            return;
        }
        if (!board.redToMove) {
            startAiTurn();
        }
    }

    private void updateHud() {
        if (archiveMode) {
            topName.setText("电脑 · " + DIFF_NAME[difficulty]);
            topSub.setText("历史对局 · 已结束");
            bottomName.setText("我 · 红方");
            bottomSub.setText("复盘中");
            topA.setText("黑损 " + lossSummary()[1]);
            bottomA.setText("红损 " + lossSummary()[0]);
            topB.setText("用时 " + formatClock(currentElapsed()));
            bottomB.setText(DIFF_NAME[difficulty] + " · 只读");
            updateNav();
            return;
        }
        if (gameOver) {
            topName.setText("电脑 · " + DIFF_NAME[difficulty]);
            topSub.setText("对局结束");
            bottomName.setText("我 · 红方");
            bottomSub.setText(resultTitle.length() == 0 ? "对局结束" : resultTitle);
            if (topAvatar != null) {
                topAvatar.setActive(false);
            }
            if (bottomAvatar != null) {
                bottomAvatar.setActive(false);
            }
            topA.setText("黑损 " + lossSummary()[1]);
            bottomA.setText("红损 " + lossSummary()[0]);
            topB.setText("用时 " + formatClock(currentElapsed()));
            bottomB.setText(DIFF_NAME[difficulty] + " · 已入棋谱");
            updateNav();
            return;
        }
        boolean redTurn = board.redToMove;
        topName.setText("电脑 · " + DIFF_NAME[difficulty]);
        topSub.setText(busy ? "黑方 · 正在思考"
                : redTurn ? "黑方 · 等待你落子" : "黑方 · 行棋中");
        if (topAvatar != null) {
            topAvatar.setActive(!redTurn);
        }
        bottomName.setText("我 · 红方");
        bottomSub.setText(redTurn ? (busy ? "等待应手" : "轮到你走") : "等待黑方");
        if (bottomAvatar != null) {
            bottomAvatar.setActive(redTurn);
        }
        topA.setText("黑损 " + lossSummary()[1]);
        bottomA.setText("红损 " + lossSummary()[0]);
        topB.setText("用时 " + formatClock(currentElapsed()));
        bottomB.setText(DIFF_NAME[difficulty] + " · 你执红");
        updateNav();
    }

    private void updateMeta() {
        if (infoRight != null) {
            int rounds = (live.size() + 1) / 2;
            infoRight.setText(String.format("第 %d 回合 · %s",
                    Math.max(rounds, 0), formatClock(currentElapsed())));
        }
        if (topB != null) {
            topB.setText("用时 " + formatClock(currentElapsed()));
        }
    }

    private String formatClock(long ms) {
        long totalSec = ms / 1000L;
        long m = totalSec / 60;
        long s = totalSec % 60;
        return String.format("%02d:%02d", m, s);
    }

    /** 双方阵亡棋子，如 "车炮"；无损失返回 "无"。 */
    private String[] lossSummary() {
        int[] full = new int[8];
        full[Piece.ADVISOR] = 2;
        full[Piece.ELEPHANT] = 2;
        full[Piece.HORSE] = 2;
        full[Piece.ROOK] = 2;
        full[Piece.CANNON] = 2;
        full[Piece.PAWN] = 5;

        int[] redNow = new int[8];
        int[] blackNow = new int[8];
        for (int i = 0; i < Board.SIZE; i++) {
            int p = board.pieceAt(i);
            if (p == 0) {
                continue;
            }
            int type = Piece.type(p);
            if (type == Piece.KING) {
                continue;
            }
            if (Piece.isRed(p)) {
                redNow[type]++;
            } else {
                blackNow[type]++;
            }
        }
        int[] order = {Piece.ROOK, Piece.CANNON, Piece.HORSE,
                Piece.ADVISOR, Piece.ELEPHANT, Piece.PAWN};
        StringBuilder red = new StringBuilder();
        StringBuilder black = new StringBuilder();
        for (int type : order) {
            for (int k = redNow[type]; k < full[type]; k++) {
                red.append(typeChar(type));
            }
            for (int k = blackNow[type]; k < full[type]; k++) {
                black.append(typeChar(type));
            }
        }
        return new String[]{red.length() == 0 ? "无" : red.toString(),
                black.length() == 0 ? "无" : black.toString()};
    }

    private static char typeChar(int type) {
        switch (type) {
            case Piece.ROOK:
                return '车';
            case Piece.HORSE:
                return '马';
            case Piece.CANNON:
                return '炮';
            case Piece.ADVISOR:
                return '士';
            case Piece.ELEPHANT:
                return '象';
            case Piece.PAWN:
                return '兵';
            default:
                return '子';
        }
    }

    private void persist() {
        if (reviewPly >= 0 || archiveMode) {
            return;
        }
        // 终局已写入历史归档，不再占用「继续对局」的存档位
        if (gameOver) {
            GameStore.clear(this);
            return;
        }
        GameStore.save(this, board, difficulty, SIDE_PLAYER == Piece.RED ? 0 : 1,
                currentElapsed(), false, "");
    }

    private long currentElapsed() {
        return elapsedBase + (gameStartAt == 0 ? 0
                : SystemClock.elapsedRealtime() - gameStartAt);
    }

    @Override
    public void onBoardTapped(int square) {
        if (gameOver || busy || phase != PLAYER_HUMAN || boardView.isAnimating()) {
            return;
        }
        if (reviewPly >= 0 || !board.redToMove) {
            return;
        }
        int piece = board.pieceAt(square);
        if (boardView.getSelected() >= 0 && boardView.isTarget(square)) {
            int from = boardView.getSelected();
            applyPlayerMove(from, square);
            return;
        }
        if (piece != 0 && Piece.side(piece) == SIDE_PLAYER) {
            showLegalTargets(square);
        } else {
            boardView.clearSelection();
        }
    }

    private void showLegalTargets(int square) {
        MoveList legal = new MoveList();
        MoveGen.generateLegal(board, legal);
        int[] targets = new int[legal.size];
        int count = 0;
        for (int i = 0; i < legal.size; i++) {
            if (Move.from(legal.moves[i]) == square) {
                targets[count++] = Move.to(legal.moves[i]);
            }
        }
        boardView.showSelection(square, targets, count);
    }

    private void applyPlayerMove(int from, int to) {
        int piece = board.pieceAt(from);
        int move = Move.encode(from, to, board.pieceAt(to));
        if (piece == 0 || !MoveGen.isLegalMove(board, move)) {
            boardView.clearSelection();
            return;
        }
        doMove(move);
        boardView.clearSelection();
        lastScore = Evaluator.evaluate(board);
        evalBar.setScore(lastScore);
        updateMeta();
        if (finishTurn()) {
            return;
        }
        startAiTurn();
    }

    private void doMove(int move) {
        board.makeMove(move);
        live.add(move);
        redo.clear();
        boardView.setHint(-1, -1);
        boardView.setLastMove(Move.from(move), Move.to(move));
        boardView.animateMove(Move.from(move), Move.to(move));
        persist();
    }

    /** 检查终局与将军状态；返回 true 表示对局结束。 */
    private boolean finishTurn() {
        int result = MoveGen.gameResult(board);
        if (result != MoveGen.ONGOING) {
            gameOver = true;
            // 终局停表，之后不再走动
            elapsedBase = currentElapsed();
            gameStartAt = 0L;
            boardView.setCheckSide(0);
            boardView.setInteractive(false);
            String title;
            String sub;
            if (result == MoveGen.RED_WIN) {
                title = "红方胜";
                sub = MoveGen.inCheck(board) ? "将死" : "无子可动";
            } else if (result == MoveGen.BLACK_WIN) {
                title = "黑方胜";
                sub = MoveGen.inCheck(board) ? "将死" : "无子可动";
            } else {
                title = "和棋";
                sub = "60 回合无吃子";
            }
            resultTitle = title;
            resultSub = sub;
            infoText.setText(title + " · " + sub);
            updateHud();
            updateMeta();
            persist();
            if (!savedToArchive) {
                GameArchive.Record record = new GameArchive.Record();
                record.moves = toArray(live);
                record.difficulty = difficulty;
                record.title = title;
                record.subtitle = sub;
                record.elapsedMs = currentElapsed();
                record.time = System.currentTimeMillis();
                GameArchive.add(this, record);
                savedToArchive = true;
            }
            // 先让棋盘停在将死局面，再从底部弹出结果
            main.postDelayed(() -> {
                if (gameOver) {
                    showResultSheet(resultTitle, resultSub);
                }
            }, 1300L);
            return true;
        }
        int checkSide = 0;
        if (MoveGen.inCheck(board, Piece.RED)) {
            checkSide = Piece.RED;
        } else if (MoveGen.inCheck(board, Piece.BLACK)) {
            checkSide = Piece.BLACK;
        }
        boardView.setCheckSide(checkSide);
        updateHud();
        updateMeta();
        return false;
    }

    private void startAiTurn() {
        if (gameOver) {
            return;
        }
        busy = true;
        phase = PLAYER_AI;
        boardView.setInteractive(false);
        updateHud();
        infoText.setText("正在搜索…");

        final int gen = ++aiGeneration;
        final Board snapshot = board.copy();
        final int depth = DIFF_DEPTH[difficulty];
        final long timeLimit = DIFF_TIME_MS[difficulty];

        executor.execute(() -> {
            SearchEngine.Result result = engine().search(snapshot, depth, timeLimit);
            main.post(() -> {
                if (gen != aiGeneration) {
                    return;
                }
                onAiResult(result);
            });
        });
    }

    private void onAiResult(SearchEngine.Result result) {
        busy = false;
        phase = PLAYER_HUMAN;
        if (result.bestMove == Move.NONE) {
            finishTurn();
            return;
        }
        if (!MoveGen.isLegalMove(board, result.bestMove)) {
            boardView.setInteractive(true);
            updateHud();
            return;
        }
        doMove(result.bestMove);
        boardView.setInteractive(true);

        lastScore = -result.score;
        evalBar.setScore(lastScore);
        infoText.setText(String.format("深度 %d · 评分 %s · %s 节点 · %d ms",
                result.depth, formatScore(lastScore), formatNodes(result.nodes), result.timeMs));

        if (finishTurn()) {
            return;
        }
        updateHud();
    }

    private String formatScore(int centipawns) {
        if (centipawns > SearchEngine.MATE_BOUND) {
            return "绝杀";
        }
        if (centipawns < -SearchEngine.MATE_BOUND) {
            return "被绝杀";
        }
        return String.format("%+.2f", centipawns / 100f);
    }

    private String formatNodes(long nodes) {
        if (nodes >= 1_000_000) {
            return String.format("%.1fM", nodes / 1_000_000f);
        }
        if (nodes >= 1000) {
            return String.format("%.1fk", nodes / 1000f);
        }
        return Long.toString(nodes);
    }

    // ------------------------------------------------------------ 悔棋 / 重做 / 复盘

    private void stepBack() {
        if (reviewPly >= 0) {
            enterReview(Math.max(0, reviewPly - 1));
            return;
        }
        if (gameOver) {
            enterReview(Math.max(0, live.size() - 1));
            return;
        }
        undoMove();
    }

    private void stepForward() {
        if (reviewPly >= 0) {
            if (reviewPly >= live.size()) {
                exitReview();
            } else {
                enterReview(reviewPly + 1);
            }
            return;
        }
        if (redo.isEmpty()) {
            return;
        }
        applyRedo();
    }

    private void undoMove() {
        if (busy || gameOver || live.isEmpty()) {
            return;
        }
        int cut = live.size();
        if (cut >= 2 && cut % 2 == 0) {
            cut -= 2;
        } else {
            cut -= 1;
        }
        if (cut < 0) {
            cut = 0;
        }
        for (int i = live.size() - 1; i >= cut; i--) {
            redo.add(live.remove(i));
        }
        board.replay(Board.START_FEN, toArray(live));
        afterPositionChanged();
        infoText.setText("已悔棋 · 可点「下一步」恢复");
        persist();
    }

    private void applyRedo() {
        if (redo.isEmpty() || busy) {
            return;
        }
        int move = redo.remove(redo.size() - 1);
        board.makeMove(move);
        live.add(move);
        boardView.setHint(-1, -1);
        boardView.setLastMove(Move.from(move), Move.to(move));
        boardView.animateMove(Move.from(move), Move.to(move));
        afterPositionChanged();
        infoText.setText("已恢复一手");
        if (redo.isEmpty() && !board.redToMove && !gameOver) {
            startAiTurn();
            return;
        }
        persist();
    }

    private void afterPositionChanged() {
        boardView.clearSelection();
        boardView.setHint(-1, -1);
        analysisOn = false;
        boardView.setCheckSide(MoveGen.inCheck(board, Piece.RED) ? Piece.RED
                : MoveGen.inCheck(board, Piece.BLACK) ? Piece.BLACK : 0);
        int last = board.lastMove();
        if (last != Move.NONE) {
            boardView.setLastMove(Move.from(last), Move.to(last));
        } else {
            boardView.setLastMove(-1, -1);
        }
        lastScore = Evaluator.evaluate(board);
        evalBar.setScore(lastScore);
        updateHud();
        updateMeta();
    }

    private void enterReview(int ply) {
        int target = Math.max(0, Math.min(live.size(), ply));
        reviewPly = target;
        int[] prefix = new int[target];
        for (int i = 0; i < target; i++) {
            prefix[i] = live.get(i);
        }
        board.replay(Board.START_FEN, prefix);
        boardView.setInteractive(false);
        boardView.clearSelection();
        boardView.setHint(-1, -1);
        int last = board.lastMove();
        if (last != Move.NONE) {
            boardView.setLastMove(Move.from(last), Move.to(last));
        } else {
            boardView.setLastMove(-1, -1);
        }
        boardView.setCheckSide(0);
        reviewChip.setVisibility(View.VISIBLE);
        reviewChip.setText(archiveMode
                ? String.format("复盘 %d/%d · 点此到终局", target, live.size())
                : String.format("复盘 %d/%d · 点此返回", target, live.size()));
        String note = target == 0 ? "开局" : moveTextAt(target - 1);
        infoText.setText("复盘 · " + note);
        updateNav();
    }

    private void exitReview() {
        if (reviewPly < 0) {
            return;
        }
        if (archiveMode) {
            enterReview(live.size());
            return;
        }
        reviewPly = -1;
        board.replay(Board.START_FEN, toArray(live));
        boardView.setInteractive(!gameOver);
        boardView.setHint(-1, -1);
        reviewChip.setVisibility(View.GONE);
        afterPositionChanged();
        infoText.setText(gameOver ? "已到终局" : "已返回当前局面");
        updateNav();
    }

    private static int[] toArray(ArrayList<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** 第 index 手的中文记法（从开局推演）。 */
    private String moveTextAt(int index) {
        Board temp = new Board();
        for (int i = 0; i < index; i++) {
            temp.makeMove(live.get(i));
        }
        return Notation.of(temp, live.get(index));
    }

    private void updateNav() {
        boolean inReview = reviewPly >= 0;
        setNavState(0, true);
        setNavState(1, true);
        setNavState(2, inReview ? reviewPly > 0 : !live.isEmpty());
        setNavState(3, inReview ? reviewPly < live.size() : !redo.isEmpty());
        setNavState(4, analysisOn);
    }

    private void setNavState(int index, boolean enabled) {
        int color = enabled ? Theme.GOLD : 0xFF6B5940;
        if (navIcons[index] != null) {
            navIcons[index].setColor(color);
        }
        if (navLabels[index] != null) {
            navLabels[index].setTextColor(color);
        }
    }

    // ------------------------------------------------------------ 分析

    private void toggleAnalysis() {
        if (reviewPly >= 0) {
            infoText.setText("复盘中不支持分析");
            return;
        }
        if (analysisOn) {
            analysisOn = false;
            boardView.setHint(-1, -1);
            if (analysisCard != null) {
                analysisCard.setVisibility(View.GONE);
            }
            updateNav();
            return;
        }
        if (busy || gameOver) {
            return;
        }
        analysisOn = true;
        updateNav();
        runAnalysis();
    }

    private void runAnalysis() {
        busy = true;
        boardView.setInteractive(false);
        infoText.setText("正在分析当前局面…");
        final int gen = ++aiGeneration;
        final Board snapshot = board.copy();
        executor.execute(() -> {
            SearchEngine.Result result = engine().search(snapshot,
                    Math.min(DIFF_DEPTH[difficulty], 8), 2200L);
            main.post(() -> {
                if (gen != aiGeneration) {
                    return;
                }
                busy = false;
                if (reviewPly < 0) {
                    boardView.setInteractive(true);
                }
                if (result.bestMove == Move.NONE) {
                    infoText.setText("无可行着法");
                    return;
                }
                int side = board.redToMove ? 1 : -1;
                int score = result.score * side;
                lastScore = score;
                evalBar.setScore(board.redToMove ? score : -score);
                boardView.setHint(Move.from(result.bestMove), Move.to(result.bestMove));
                Board temp = board.copy();
                String text = Notation.of(temp, result.bestMove);
                infoText.setText(String.format("分析 · 建议 %s · 评分 %s · 深度 %d · %s 节点",
                        text, formatScore(score), result.depth, formatNodes(result.nodes)));
                updateHud();
            });
        });
    }

    // ------------------------------------------------------------ 棋谱

    private void showMoveList() {
        if (sheetOverlay != null) {
            return;
        }
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xC4000000);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> dismissSheet());
        sheetOverlay = overlay;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.round(this, 0xFF1E1308, 0x4DE9C07A, 16f));
        card.setPadding(dp(16), dp(14), dp(16), dp(12));
        card.setClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headCol = new LinearLayout(this);
        headCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("棋谱");
        title.setTextColor(Theme.GOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTypeface(Theme.serifBold());
        headCol.addView(title);
        TextView subTitle = new TextView(this);
        String tag = archiveMode ? resultTitle.length() == 0 ? "历史对局" : resultTitle
                : gameOver ? resultTitle : "进行中";
        subTitle.setText(tag + " · 共 " + live.size() + " 手 · 点任一手可复盘");
        subTitle.setTextColor(Theme.TEXT_FAINT);
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        headCol.addView(subTitle);
        head.addView(headCol, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("关闭");
        close.setTextColor(Theme.TEXT_DIM);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        close.setPadding(dp(10), dp(4), dp(2), dp(4));
        close.setOnClickListener(v -> dismissSheet());
        head.addView(close);
        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headLp.bottomMargin = dp(8);
        card.addView(head, headLp);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);

        if (live.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("尚无着法");
            empty.setTextColor(Theme.TEXT_FAINT);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            empty.setPadding(0, dp(18), 0, dp(18));
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
        } else {
            for (int i = 0; i < live.size(); i += 2) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);

                TextView idx = new TextView(this);
                idx.setText((i / 2 + 1) + ".");
                idx.setTextColor(Theme.TEXT_FAINT);
                idx.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
                idx.setGravity(Gravity.END);
                idx.setPadding(0, 0, dp(8), 0);
                row.addView(idx, new LinearLayout.LayoutParams(dp(30),
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                row.addView(moveCell(i, Theme.RED_LIGHT), new LinearLayout.LayoutParams(
                        0, dp(30), 1f));
                if (i + 1 < live.size()) {
                    row.addView(moveCell(i + 1, Theme.TEXT), new LinearLayout.LayoutParams(
                            0, dp(30), 1f));
                } else {
                    TextView pad = new TextView(this);
                    row.addView(pad, new LinearLayout.LayoutParams(0, dp(30), 1f));
                }
                list.addView(row);
            }
        }

        card.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 打开棋谱时直接定位到当前手，避免长棋谱要手动翻
        final int focusPly = currentPly();
        sv.post(() -> {
            int row = Math.max(0, (focusPly - 1) / 2);
            sv.scrollTo(0, Math.max(0, (row - 2) * dp(30)));
        });

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.80f),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.52f));
        cardLp.gravity = Gravity.CENTER;
        overlay.addView(card, cardLp);

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 当前展示到第几手（复盘时为复盘手数，否则为已走手数）。 */
    private int currentPly() {
        return reviewPly >= 0 ? reviewPly : live.size();
    }

    private TextView moveCell(final int ply, int color) {
        TextView tv = new TextView(this);
        tv.setText(moveTextAt(ply));
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(8), 0, dp(8), 0);
        tv.setBackground(Theme.round(this, 0x00000000, 0x00000000, 8f));
        boolean on = currentPly() == ply + 1;
        if (on) {
            tv.setBackground(Theme.round(this, 0x33E9C07A, 0x66E9C07A, 8f));
        }
        tv.setOnClickListener(v -> {
            dismissSheet();
            enterReview(ply + 1);
        });
        return tv;
    }

    private void dismissSheet() {
        if (sheetOverlay == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) sheetOverlay.getParent();
        if (parent != null) {
            parent.removeView(sheetOverlay);
        }
        sheetOverlay = null;
    }

    // ------------------------------------------------------------ 菜单

    private void showPauseMenu() {
        if (pauseOverlay != null) {
            return;
        }
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xC4000000);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> dismissPauseMenu());
        pauseOverlay = overlay;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.round(this, 0xFF20140A, 0x4DE9C07A, 18f));
        card.setPadding(dp(22), dp(20), dp(22), dp(18));
        card.setClickable(true);

        TextView title = new TextView(this);
        title.setText("对局菜单");
        title.setTextColor(Theme.GOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setTypeface(Theme.serifBold());
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText(String.format("%s · 第 %d 回合 · %s", DIFF_NAME[difficulty],
                (live.size() + 1) / 2, formatClock(currentElapsed())));
        sub.setTextColor(Theme.TEXT_FAINT);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(14));
        card.addView(sub);

        if (!archiveMode && !gameOver) {
            LinearLayout diffRow = new LinearLayout(this);
            diffRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < 3; i++) {
                final int index = i;
                TextView chip = new TextView(this);
                chip.setText(DIFF_NAME[i]);
                chip.setGravity(Gravity.CENTER);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
                chip.setTextColor(i == difficulty ? 0xFF2A1A0C : Theme.TEXT_DIM);
                chip.setBackground(Theme.round(this,
                        i == difficulty ? Theme.GOLD : 0x00000000,
                        i == difficulty ? Theme.GOLD : 0x40E9C07A, 16f));
                chip.setOnClickListener(v -> {
                    difficulty = clampDifficulty(index);
                    dismissPauseMenu();
                    updateHud();
                    updateNav();
                    persist();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1f);
                lp.rightMargin = i < 2 ? dp(8) : 0;
                diffRow.addView(chip, lp);
            }
            LinearLayout.LayoutParams diffLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            diffLp.bottomMargin = dp(14);
            card.addView(diffRow, diffLp);
        }

        if (archiveMode) {
            card.addView(menuRow("回到终局", true, v -> {
                dismissPauseMenu();
                enterReview(live.size());
            }), rowLp());
            card.addView(menuRow("翻转棋盘", false, v -> {
                dismissPauseMenu();
                boardView.setFlipped(!boardView.isFlipped());
            }), rowLp());
            card.addView(menuRow("返回主菜单", false, v -> {
                dismissPauseMenu();
                startActivity(new Intent(this, MenuActivity.class));
                finish();
            }), rowLp());
        } else if (gameOver) {
            card.addView(menuRow("查看结果", true, v -> {
                dismissPauseMenu();
                showResultSheet(resultTitle.length() == 0 ? "对局结束" : resultTitle, resultSub);
            }), rowLp());
            card.addView(menuRow("复盘棋谱", false, v -> {
                dismissPauseMenu();
                showMoveList();
            }), rowLp());
            card.addView(menuRow("翻转棋盘", false, v -> {
                dismissPauseMenu();
                boardView.setFlipped(!boardView.isFlipped());
            }), rowLp());
            card.addView(menuRow("再来一局", false, v -> {
                dismissPauseMenu();
                newGame();
            }), rowLp());
            card.addView(menuRow("返回主菜单", false, v -> {
                dismissPauseMenu();
                startActivity(new Intent(this, MenuActivity.class));
                finish();
            }), rowLp());
        } else {
            card.addView(menuRow("继续对局", true, v -> dismissPauseMenu()), rowLp());
            card.addView(menuRow("翻转棋盘", false, v -> {
                dismissPauseMenu();
                boardView.setFlipped(!boardView.isFlipped());
            }), rowLp());
            card.addView(menuRow("提示一手", false, v -> {
                dismissPauseMenu();
                requestHint();
            }), rowLp());
            card.addView(menuRow("重新开始", false, v -> {
                dismissPauseMenu();
                newGame();
            }), rowLp());
            card.addView(menuRow("返回主菜单", false, v -> {
                dismissPauseMenu();
                exitReview();
                persist();
                startActivity(new Intent(this, MenuActivity.class));
                finish();
            }), rowLp());
        }

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.78f),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        overlay.addView(card, cardLp);

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lp.bottomMargin = dp(7);
        return lp;
    }

    private TextView menuRow(String text, boolean primary, View.OnClickListener click) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(primary ? 0xFF2A1A0C : Theme.TEXT);
        tv.setBackground(Theme.round(this,
                primary ? Theme.GOLD : 0x22FFFFFF,
                primary ? Theme.GOLD : 0x40E9C07A, 21f));
        tv.setOnClickListener(click);
        return tv;
    }

    private void dismissPauseMenu() {
        if (pauseOverlay == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) pauseOverlay.getParent();
        if (parent != null) {
            parent.removeView(pauseOverlay);
        }
        pauseOverlay = null;
    }

    private void requestHint() {
        if (busy || gameOver || !board.redToMove || reviewPly >= 0) {
            return;
        }
        busy = true;
        boardView.setInteractive(false);
        infoText.setText("正在计算建议…");
        final int gen = ++aiGeneration;
        final Board snapshot = board.copy();
        executor.execute(() -> {
            SearchEngine.Result result = engine().search(snapshot,
                    Math.min(DIFF_DEPTH[difficulty], 6), 1200L);
            main.post(() -> {
                if (gen != aiGeneration) {
                    return;
                }
                busy = false;
                boardView.setInteractive(true);
                updateHud();
                if (result.bestMove != Move.NONE) {
                    int from = Move.from(result.bestMove);
                    int to = Move.to(result.bestMove);
                    showLegalTargets(from);
                    boardView.setHint(from, to);
                    infoText.setText("建议：" + Notation.of(board.copy(), result.bestMove)
                            + " · 评分 " + formatScore(-result.score));
                }
            });
        });
    }

    /** 结果面板：底部上滑，先让棋盘停留片刻。 */
    private void showResultSheet(String title, String subtitle) {
        if (resultOverlay != null) {
            return;
        }
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0x66000000);
        overlay.setClickable(true);
        overlay.setOnClickListener(v -> dismissResult(overlay));
        resultOverlay = overlay;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.round(this, 0xFF20140A, 0x4DE9C07A, 18f));
        card.setPadding(dp(26), dp(20), dp(26), dp(18));
        card.setClickable(true);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Theme.GOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        t.setTypeface(Theme.serifBold());
        t.setGravity(Gravity.CENTER);
        card.addView(t);

        TextView s = new TextView(this);
        s.setText(subtitle + " · 用时 " + formatClock(currentElapsed())
                + " · 共 " + live.size() + " 手");
        s.setTextColor(Theme.TEXT_DIM);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(6), 0, dp(16));
        card.addView(s);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);

        TextView replay = actionButton("复盘", false);
        replay.setOnClickListener(v -> {
            dismissResult(overlay);
            showMoveList();
        });
        btns.addView(replay, weightLp(0));

        TextView again = actionButton("再来一局", true);
        again.setOnClickListener(v -> {
            dismissResult(overlay);
            newGame();
        });
        btns.addView(again, weightLp(8));
        card.addView(btns);

        TextView back = actionButton("返回主菜单", false);
        back.setOnClickListener(v -> {
            dismissResult(overlay);
            startActivity(new Intent(this, MenuActivity.class));
            finish();
        });
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        backLp.topMargin = dp(8);
        card.addView(back, backLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.78f),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        cardLp.bottomMargin = dp(20);
        overlay.addView(card, cardLp);
        card.setTranslationY(dp(120));
        card.setAlpha(0f);
        card.animate().translationY(0f).alpha(1f).setDuration(240L).start();

        ViewGroup root = findViewById(android.R.id.content);
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void dismissResult(View overlay) {
        ViewGroup parent = (ViewGroup) overlay.getParent();
        if (parent != null) {
            parent.removeView(overlay);
        }
        if (resultOverlay == overlay) {
            resultOverlay = null;
        }
    }

    private LinearLayout.LayoutParams weightLp(int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.leftMargin = dp(leftMargin);
        return lp;
    }

    private TextView actionButton(String text, boolean primary) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(primary ? 0xFF2A1A0C : Theme.TEXT);
        tv.setBackground(Theme.round(this,
                primary ? Theme.GOLD : 0x22FFFFFF,
                primary ? Theme.GOLD : 0x40E9C07A, 21f));
        tv.setClickable(true);
        return tv;
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    protected void onPause() {
        super.onPause();
        if (board.moveCount() > 0 || gameOver) {
            persist();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ticker.removeCallbacks(tickTask);
        aiGeneration++;
        if (engine != null) {
            engine.cancel();
        }
        executor.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (resultOverlay != null) {
            dismissResult(resultOverlay);
            return;
        }
        if (pauseOverlay != null) {
            dismissPauseMenu();
            return;
        }
        if (sheetOverlay != null) {
            dismissSheet();
            return;
        }
        if (reviewPly >= 0) {
            exitReview();
            return;
        }
        showPauseMenu();
    }
}
