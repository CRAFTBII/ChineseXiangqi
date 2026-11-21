package com.workbuddy.xiangqi.game;

/**
 * 走法用 int 压缩编码，避免搜索过程中产生大量对象。
 *
 * <pre>
 *  bit  0..6   from  (0..89)
 *  bit  7..13  to    (0..89)
 *  bit 14..20  captured piece code
 * </pre>
 */
public final class Move {

    public static final int NONE = 0;

    private Move() {
    }

    public static int encode(int from, int to, int captured) {
        return (from & 0x7F) | ((to & 0x7F) << 7) | ((captured & 0x7F) << 14);
    }

    public static int from(int move) {
        return move & 0x7F;
    }

    public static int to(int move) {
        return (move >> 7) & 0x7F;
    }

    public static int captured(int move) {
        return (move >> 14) & 0x7F;
    }

    public static boolean isCapture(int move) {
        return ((move >> 14) & 0x7F) != 0;
    }

    public static boolean same(int a, int b) {
        return (a & 0x3FFF) == (b & 0x3FFF);
    }

    /** 供 UI 使用的可读文本，如 "炮二平五" 的简化形式 (h2-e2)。 */
    public static String describe(int move) {
        int from = from(move);
        int to = to(move);
        return squareName(from) + "-" + squareName(to);
    }

    public static String squareName(int sq) {
        int row = sq / Board.COLS;
        int col = sq % Board.COLS;
        char file = (char) ('a' + col);
        return "" + file + (Board.ROWS - row);
    }
}
