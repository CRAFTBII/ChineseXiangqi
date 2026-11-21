package com.workbuddy.xiangqi.game;

/**
 * 中文棋谱记法（炮二平五 / 马8进7）。
 * 仅用于界面展示，不参与搜索。
 */
public final class Notation {

    private static final char[] CN = {'一', '二', '三', '四', '五', '六', '七', '八', '九'};

    private Notation() {
    }

    /** @param before 走这一步之前的局面 */
    public static String of(Board before, int move) {
        int from = Move.from(move);
        int to = Move.to(move);
        int piece = before.pieceAt(from);
        if (piece == 0) {
            return Move.describe(move);
        }
        int side = Piece.side(piece);
        int type = Piece.type(piece);
        boolean red = side == Piece.RED;
        int fromRow = from / Board.COLS;
        int fromCol = from % Board.COLS;
        int toRow = to / Board.COLS;
        int toCol = to % Board.COLS;
        int fromFile = file(fromCol, red);
        int toFile = file(toCol, red);

        String name = Piece.name(piece);
        String prefix = frontBack(before, from, piece);

        StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(name);
        if (prefix.isEmpty()) {
            sb.append(num(fromFile, red));
        }

        if (fromRow == toRow) {
            sb.append('平').append(num(toFile, red));
        } else {
            boolean forward;
            if (type == Piece.HORSE || type == Piece.ELEPHANT || type == Piece.ADVISOR) {
                forward = red ? toRow < fromRow : toRow > fromRow;
                sb.append(forward ? '进' : '退').append(num(toFile, red));
            } else {
                forward = red ? toRow < fromRow : toRow > fromRow;
                sb.append(forward ? '进' : '退').append(num(Math.abs(toRow - fromRow), red));
            }
        }
        return sb.toString();
    }

    private static int file(int col, boolean red) {
        return red ? Board.COLS - col : col + 1;
    }

    private static String num(int n, boolean red) {
        if (n < 1 || n > 9) {
            return String.valueOf(n);
        }
        return red ? String.valueOf(CN[n - 1]) : String.valueOf(n);
    }

    /** 同一纵线两个同种棋子时用 前/后 区分。 */
    private static String frontBack(Board b, int from, int piece) {
        int type = Piece.type(piece);
        int side = Piece.side(piece);
        int col = from % Board.COLS;
        int count = 0;
        int first = -1;
        int second = -1;
        for (int r = 0; r < Board.ROWS; r++) {
            int sq = r * Board.COLS + col;
            int p = b.pieceAt(sq);
            if (p == 0 || Piece.type(p) != type || Piece.side(p) != side) {
                continue;
            }
            count++;
            if (count == 1) {
                first = sq;
            } else if (count == 2) {
                second = sq;
            }
        }
        if (count != 2) {
            return "";
        }
        // 红方行号小的一侧为前，黑方相反
        int front = side == Piece.RED ? Math.min(first, second) : Math.max(first, second);
        return from == front ? "前" : "后";
    }
}
