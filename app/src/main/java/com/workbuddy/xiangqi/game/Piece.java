package com.workbuddy.xiangqi.game;

/**
 * 棋子编码：一个格子用一个 byte 表示。
 * 低 3 位为类型，高位为阵营标记（RED = 8, BLACK = 16）。
 * 0 表示空格。
 */
public final class Piece {

    public static final int EMPTY = 0;

    public static final int RED = 8;
    public static final int BLACK = 16;

    public static final int KING = 1;
    public static final int ADVISOR = 2;
    public static final int ELEPHANT = 3;
    public static final int HORSE = 4;
    public static final int ROOK = 5;
    public static final int CANNON = 6;
    public static final int PAWN = 7;

    private static final int TYPE_MASK = 0x7;
    private static final int SIDE_MASK = 0x18;

    private Piece() {
    }

    public static int make(int side, int type) {
        return side | type;
    }

    public static int side(int piece) {
        return piece & SIDE_MASK;
    }

    public static int type(int piece) {
        return piece & TYPE_MASK;
    }

    public static boolean isEmpty(int piece) {
        return piece == 0;
    }

    public static boolean isRed(int piece) {
        return (piece & SIDE_MASK) == RED;
    }

    public static boolean isBlack(int piece) {
        return (piece & SIDE_MASK) == BLACK;
    }

    public static int opposite(int side) {
        return side == RED ? BLACK : RED;
    }

    /** 棋子中文名（红方 / 黑方）。 */
    public static String name(int piece) {
        if (piece == 0) {
            return "";
        }
        boolean red = isRed(piece);
        switch (type(piece)) {
            case KING:
                return red ? "帅" : "将";
            case ADVISOR:
                return red ? "仕" : "士";
            case ELEPHANT:
                return red ? "相" : "象";
            case HORSE:
                return "马";
            case ROOK:
                return "车";
            case CANNON:
                return "炮";
            case PAWN:
                return red ? "兵" : "卒";
            default:
                return "";
        }
    }

    /** 棋子刻字用字（繁体，贴近实体棋）。 */
    public static String glyph(int piece) {
        if (piece == 0) {
            return "";
        }
        boolean red = isRed(piece);
        switch (type(piece)) {
            case KING:
                return red ? "帥" : "將";
            case ADVISOR:
                return red ? "仕" : "士";
            case ELEPHANT:
                return red ? "相" : "象";
            case HORSE:
                return "馬";
            case ROOK:
                return "車";
            case CANNON:
                return red ? "炮" : "砲";
            case PAWN:
                return red ? "兵" : "卒";
            default:
                return "";
        }
    }

    /** 用于日志/FEN 的字母（红大写，黑小写）。 */
    public static char letter(int piece) {
        boolean red = isRed(piece);
        char c;
        switch (type(piece)) {
            case KING:
                c = 'k';
                break;
            case ADVISOR:
                c = 'a';
                break;
            case ELEPHANT:
                c = 'b';
                break;
            case HORSE:
                c = 'n';
                break;
            case ROOK:
                c = 'r';
                break;
            case CANNON:
                c = 'c';
                break;
            case PAWN:
                c = 'p';
                break;
            default:
                c = '?';
                break;
        }
        return red ? Character.toUpperCase(c) : c;
    }
}
