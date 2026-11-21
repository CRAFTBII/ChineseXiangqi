package com.workbuddy.xiangqi.game;

import java.util.Arrays;

/**
 * 棋盘局面。格子索引 = row * 9 + col，row 0 为黑方底线（屏幕上方），row 9 为红方底线。
 */
public final class Board {

    public static final int COLS = 9;
    public static final int ROWS = 10;
    public static final int SIZE = COLS * ROWS;

    /** 初始局面 FEN。 */
    public static final String START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1";

    public final byte[] cells = new byte[SIZE];
    public boolean redToMove = true;
    public int redKingPos = -1;
    public int blackKingPos = -1;
    public long hash;

    private int halfMoveClock;
    private int fullMoveNumber = 1;

    private final int[] undoMoves = new int[1024];
    private final boolean[] undoRedToMove = new boolean[1024];
    private final int[] undoHalfClock = new int[1024];
    private int undoCount;

    public Board() {
        reset();
    }

    public static int index(int row, int col) {
        return row * COLS + col;
    }

    public static int rowOf(int sq) {
        return sq / COLS;
    }

    public static int colOf(int sq) {
        return sq % COLS;
    }

    public Board copy() {
        Board b = new Board();
        b.setFrom(this);
        return b;
    }

    public void setFrom(Board other) {
        System.arraycopy(other.cells, 0, cells, 0, SIZE);
        redToMove = other.redToMove;
        redKingPos = other.redKingPos;
        blackKingPos = other.blackKingPos;
        hash = other.hash;
        halfMoveClock = other.halfMoveClock;
        fullMoveNumber = other.fullMoveNumber;
        undoCount = 0;
    }

    public void reset() {
        parseFen(START_FEN);
    }

    public void clear() {
        Arrays.fill(cells, (byte) 0);
        redKingPos = -1;
        blackKingPos = -1;
        hash = 0L;
        undoCount = 0;
        halfMoveClock = 0;
        fullMoveNumber = 1;
        redToMove = true;
    }

    public int pieceAt(int sq) {
        return cells[sq];
    }

    public void setPiece(int sq, int piece) {
        int old = cells[sq];
        if (old != 0) {
            hash ^= Zobrist.piece(old, sq);
        }
        cells[sq] = (byte) piece;
        if (piece != 0) {
            hash ^= Zobrist.piece(piece, sq);
        }
        if (Piece.type(piece) == Piece.KING) {
            if (Piece.isRed(piece)) {
                redKingPos = sq;
            } else if (Piece.isBlack(piece)) {
                blackKingPos = sq;
            }
        }
    }

    public int kingPos(int side) {
        return side == Piece.RED ? redKingPos : blackKingPos;
    }

    public boolean inPalace(int side, int sq) {
        int r = rowOf(sq);
        int c = colOf(sq);
        if (c < 3 || c > 5) {
            return false;
        }
        return side == Piece.RED ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    // ---------------------------------------------------------------- 走子

    public void makeMove(int move) {
        int from = Move.from(move);
        int to = Move.to(move);
        int moving = cells[from];
        int captured = cells[to];

        if (undoCount < undoMoves.length) {
            undoMoves[undoCount] = move;
            undoRedToMove[undoCount] = redToMove;
            undoHalfClock[undoCount] = halfMoveClock;
        }
        undoCount++;

        hash ^= Zobrist.piece(moving, from);
        hash ^= Zobrist.piece(moving, to);
        if (captured != 0) {
            hash ^= Zobrist.piece(captured, to);
        }

        cells[to] = (byte) moving;
        cells[from] = 0;

        if (Piece.type(moving) == Piece.KING) {
            if (Piece.isRed(moving)) {
                redKingPos = to;
            } else {
                blackKingPos = to;
            }
        }

        if (captured != 0) {
            halfMoveClock = 0;
        } else {
            halfMoveClock++;
        }
        if (!redToMove) {
            fullMoveNumber++;
        }
        redToMove = !redToMove;
    }

    public void undoMove() {
        if (undoCount <= 0) {
            return;
        }
        undoCount--;
        int move = undoMoves[undoCount];
        redToMove = undoRedToMove[undoCount];
        halfMoveClock = undoHalfClock[undoCount];

        if (move == Move.NONE) {
            // 空着（null move）：只翻转了走子方
            return;
        }

        int from = Move.from(move);
        int to = Move.to(move);
        int moving = cells[to];
        int captured = Move.captured(move);

        hash ^= Zobrist.piece(moving, from);
        hash ^= Zobrist.piece(moving, to);
        if (captured != 0) {
            hash ^= Zobrist.piece(captured, to);
        }

        cells[from] = (byte) moving;
        cells[to] = (byte) captured;

        if (Piece.type(moving) == Piece.KING) {
            if (Piece.isRed(moving)) {
                redKingPos = from;
            } else {
                blackKingPos = from;
            }
        }
    }

    public int moveCount() {
        return undoCount;
    }

    /** 空着：仅翻转走子方，用于空着裁剪。 */
    public void makeNullMove() {
        if (undoCount < undoMoves.length) {
            undoMoves[undoCount] = Move.NONE;
            undoRedToMove[undoCount] = redToMove;
            undoHalfClock[undoCount] = halfMoveClock;
        }
        undoCount++;
        redToMove = !redToMove;
        halfMoveClock++;
    }

    public boolean lastWasNull() {
        return undoCount > 0 && undoMoves[undoCount - 1] == Move.NONE;
    }

    /** 导出完整走法序列（用于存档 / 重放）。 */
    public int[] historyMoves() {
        int[] out = new int[undoCount];
        System.arraycopy(undoMoves, 0, out, 0, undoCount);
        return out;
    }

    /** 按走法序列重建局面（从指定 FEN 开始）。 */
    public void replay(String startFen, int[] moves) {
        parseFen(startFen);
        if (moves == null) {
            return;
        }
        for (int mv : moves) {
            if (mv == Move.NONE) {
                continue;
            }
            makeMove(mv);
        }
    }

    public int lastMove() {
        return undoCount > 0 ? undoMoves[undoCount - 1] : Move.NONE;
    }

    public int halfMoveClock() {
        return halfMoveClock;
    }

    /** 用于置换表 / 重复检测的局面键（含走子方）。 */
    public long positionKey() {
        return hash ^ (redToMove ? Zobrist.RED_TO_MOVE : 0L);
    }

    // ---------------------------------------------------------------- FEN

    public void parseFen(String fen) {
        clear();
        String[] parts = fen.trim().split("\\s+");
        String[] rows = parts[0].split("/");
        if (rows.length != ROWS) {
            throw new IllegalArgumentException("非法 FEN: " + fen);
        }
        for (int r = 0; r < ROWS; r++) {
            int col = 0;
            String line = rows[r];
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (ch >= '1' && ch <= '9') {
                    col += ch - '0';
                } else {
                    int piece = pieceFromChar(ch);
                    if (piece != 0 && col < COLS) {
                        setPiece(index(r, col), piece);
                    }
                    col++;
                }
            }
        }
        if (parts.length > 1) {
            redToMove = !parts[1].startsWith("b");
        }
        if (parts.length > 4) {
            try {
                halfMoveClock = Integer.parseInt(parts[4]);
            } catch (NumberFormatException ignored) {
                halfMoveClock = 0;
            }
        }
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            int empty = 0;
            for (int c = 0; c < COLS; c++) {
                int p = cells[index(r, c)];
                if (p == 0) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(Piece.letter(p));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (r != ROWS - 1) {
                sb.append('/');
            }
        }
        sb.append(redToMove ? " w" : " b");
        sb.append(" - - ").append(halfMoveClock).append(' ').append(fullMoveNumber);
        return sb.toString();
    }

    private static int pieceFromChar(char ch) {
        int side = Character.isUpperCase(ch) ? Piece.RED : Piece.BLACK;
        switch (Character.toLowerCase(ch)) {
            case 'k':
                return Piece.make(side, Piece.KING);
            case 'a':
                return Piece.make(side, Piece.ADVISOR);
            case 'b':
            case 'e':
                return Piece.make(side, Piece.ELEPHANT);
            case 'n':
            case 'h':
                return Piece.make(side, Piece.HORSE);
            case 'r':
                return Piece.make(side, Piece.ROOK);
            case 'c':
                return Piece.make(side, Piece.CANNON);
            case 'p':
                return Piece.make(side, Piece.PAWN);
            default:
                return 0;
        }
    }
}
