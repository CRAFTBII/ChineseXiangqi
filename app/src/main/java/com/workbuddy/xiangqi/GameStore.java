package com.workbuddy.xiangqi;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.workbuddy.xiangqi.game.Board;

/**
 * 单槽自动存档。以"起始 FEN + 走法序列"保存整局，可完整恢复悔棋栈。
 */
public final class GameStore {

    private static final String PREF = "xiangqi_save";
    private static final String K_MOVES = "moves";
    private static final String K_DIFF = "difficulty";
    private static final String K_SIDE = "playerSide";
    private static final String K_TIME = "elapsed";
    private static final String K_AT = "savedAt";
    private static final String K_DONE = "finished";
    private static final String K_RESULT = "resultTitle";

    private GameStore() {
    }

    public static final class Saved {
        public int[] moves = new int[0];
        public int difficulty = 1;
        public int playerSide = 0;
        public long elapsedMs;
        public long savedAt;
        public boolean finished;
        public String resultTitle = "";
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context c, Board board, int difficulty, int playerSide,
                            long elapsedMs, boolean finished, String resultTitle) {
        int[] moves = board.historyMoves();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moves.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(moves[i]);
        }
        prefs(c).edit()
                .putString(K_MOVES, sb.toString())
                .putInt(K_DIFF, difficulty)
                .putInt(K_SIDE, playerSide)
                .putLong(K_TIME, elapsedMs)
                .putLong(K_AT, System.currentTimeMillis())
                .putBoolean(K_DONE, finished)
                .putString(K_RESULT, resultTitle == null ? "" : resultTitle)
                .apply();
    }

    public static boolean hasSave(Context c) {
        String s = prefs(c).getString(K_MOVES, "");
        return !TextUtils.isEmpty(s);
    }

    public static Saved load(Context c) {
        SharedPreferences p = prefs(c);
        Saved out = new Saved();
        out.difficulty = p.getInt(K_DIFF, 1);
        out.playerSide = p.getInt(K_SIDE, 0);
        out.elapsedMs = p.getLong(K_TIME, 0L);
        out.savedAt = p.getLong(K_AT, 0L);
        out.finished = p.getBoolean(K_DONE, false);
        out.resultTitle = p.getString(K_RESULT, "");
        String s = p.getString(K_MOVES, "");
        if (TextUtils.isEmpty(s)) {
            out.moves = new int[0];
            return out;
        }
        String[] parts = s.split(",");
        int[] moves = new int[parts.length];
        int n = 0;
        for (String part : parts) {
            try {
                moves[n++] = Integer.parseInt(part.trim());
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        out.moves = new int[n];
        System.arraycopy(moves, 0, out.moves, 0, n);
        return out;
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    /** 主菜单用的摘要文本。 */
    public static String summary(Context c) {
        Saved s = load(c);
        if (s.moves.length == 0) {
            return "空";
        }
        int rounds = (s.moves.length + 1) / 2;
        return String.format("%s · 第 %d 回合 · %s",
                s.playerSide == 0 ? "执红" : "执黑", rounds, ago(s.savedAt));
    }

    private static String ago(long time) {
        if (time <= 0) {
            return "刚刚";
        }
        long diff = System.currentTimeMillis() - time;
        if (diff < 60_000L) {
            return "刚刚";
        }
        if (diff < 3_600_000L) {
            return (diff / 60_000L) + " 分钟前";
        }
        if (diff < 86_400_000L) {
            return (diff / 3_600_000L) + " 小时前";
        }
        return (diff / 86_400_000L) + " 天前";
    }
}
