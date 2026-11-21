package com.workbuddy.xiangqi.ai;

import com.workbuddy.xiangqi.game.Board;
import com.workbuddy.xiangqi.game.Piece;

/**
 * 静态局面评估。返回值以红方为正（红优分为正）。
 * 组成：子力价值 + 位置价值表(PST) + 兵型/结构项。
 */
public final class Evaluator {

    public static final int VALUE_KING = 6000;
    public static final int VALUE_ROOK = 900;
    public static final int VALUE_CANNON = 450;
    public static final int VALUE_HORSE = 400;
    public static final int VALUE_ADVISOR = 200;
    public static final int VALUE_ELEPHANT = 200;
    public static final int VALUE_PAWN = 100;

    private static final int[] VALUES = new int[8];

    // 位置表均以"红方视角、row 0 为对方底线"书写；黑方镜像取 [9-row][8-col]。
    private static final int[][] PST_PAWN = {
            {9, 9, 9, 11, 13, 11, 9, 9, 9},
            {19, 24, 34, 42, 44, 42, 34, 24, 19},
            {19, 24, 32, 37, 37, 37, 32, 24, 19},
            {19, 23, 27, 29, 30, 29, 27, 23, 19},
            {14, 18, 20, 27, 29, 27, 20, 18, 14},
            {7, 0, 13, 0, 16, 0, 13, 0, 7},
            {7, 0, 7, 0, 15, 0, 7, 0, 7},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0}
    };

    private static final int[][] PST_HORSE = {
            {0, -4, 0, 0, 0, 0, 0, -4, 0},
            {0, 2, 4, 4, -2, 4, 4, 2, 0},
            {4, 2, 8, 8, 4, 8, 8, 2, 4},
            {2, 6, 8, 6, 10, 6, 8, 6, 2},
            {4, 12, 16, 14, 12, 14, 16, 12, 4},
            {6, 16, 14, 18, 16, 18, 14, 16, 6},
            {8, 24, 18, 24, 20, 24, 18, 24, 8},
            {12, 14, 16, 20, 18, 20, 16, 14, 12},
            {4, 10, 28, 16, 8, 16, 28, 10, 4},
            {0, 0, 0, 0, 0, 0, 0, 0, 0}
    };

    private static final int[][] PST_ROOK = {
            {6, 14, 12, 18, 16, 18, 12, 14, 6},
            {6, 16, 12, 18, 16, 18, 12, 16, 6},
            {6, 12, 10, 16, 14, 16, 10, 12, 6},
            {6, 14, 12, 16, 14, 16, 12, 14, 6},
            {12, 16, 14, 20, 18, 20, 14, 16, 12},
            {12, 14, 12, 18, 16, 18, 12, 14, 12},
            {8, 10, 8, 14, 12, 14, 8, 10, 8},
            {4, 8, 6, 14, 12, 14, 6, 8, 4},
            {8, 4, 8, 16, 10, 16, 8, 4, 8},
            {-2, 10, 6, 14, 12, 14, 6, 10, -2}
    };

    private static final int[][] PST_CANNON = {
            {6, 4, 0, -10, -12, -10, 0, 4, 6},
            {2, 2, 0, -4, -14, -4, 0, 2, 2},
            {2, 2, 0, -10, -8, -10, 0, 2, 2},
            {0, 0, -2, 4, 10, 4, -2, 0, 0},
            {0, 0, 0, 2, 8, 2, 0, 0, 0},
            {-2, 0, 4, 2, 6, 2, 4, 0, -2},
            {0, 0, 0, 2, 6, 2, 0, 0, 0},
            {4, 0, 8, 6, 10, 6, 8, 0, 4},
            {0, 2, 4, 6, 6, 6, 4, 2, 0},
            {0, 0, 2, 6, 4, 6, 2, 0, 0}
    };

    private static final int[][] PST_ADVISOR = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 3, 0, 3, 0, 0, 0},
            {0, 0, 0, 0, 5, 0, 0, 0, 0},
            {0, 0, 0, 3, 0, 3, 0, 0, 0}
    };

    private static final int[][] PST_ELEPHANT = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 3, 0, 0, 0, 3, 0, 0},
            {0, 0, 0, 0, 5, 0, 0, 0, 0},
            {0, 0, 3, 0, 0, 0, 3, 0, 0}
    };

    private static final int[][] PST_KING = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 1, 5, 1, 0, 0, 0},
            {0, 0, 0, 3, 8, 3, 0, 0, 0},
            {0, 0, 0, 1, 3, 1, 0, 0, 0}
    };

    static {
        VALUES[Piece.KING] = VALUE_KING;
        VALUES[Piece.ROOK] = VALUE_ROOK;
        VALUES[Piece.CANNON] = VALUE_CANNON;
        VALUES[Piece.HORSE] = VALUE_HORSE;
        VALUES[Piece.ADVISOR] = VALUE_ADVISOR;
        VALUES[Piece.ELEPHANT] = VALUE_ELEPHANT;
        VALUES[Piece.PAWN] = VALUE_PAWN;
    }

    private Evaluator() {
    }

    public static int pieceValue(int type) {
        return type > 0 && type < VALUES.length ? VALUES[type] : 0;
    }

    public static int pstValue(int type, int row, int col) {
        switch (type) {
            case Piece.PAWN:
                return PST_PAWN[row][col];
            case Piece.HORSE:
                return PST_HORSE[row][col];
            case Piece.ROOK:
                return PST_ROOK[row][col];
            case Piece.CANNON:
                return PST_CANNON[row][col];
            case Piece.ADVISOR:
                return PST_ADVISOR[row][col];
            case Piece.ELEPHANT:
                return PST_ELEPHANT[row][col];
            case Piece.KING:
                return PST_KING[row][col];
            default:
                return 0;
        }
    }

    /** 红方视角分值（红优为正）。 */
    public static int evaluate(Board b) {
        int score = 0;
        final byte[] cells = b.cells;

        int redAdvisors = 0, blackAdvisors = 0;
        int redElephants = 0, blackElephants = 0;
        int redRooks = 0, blackRooks = 0;
        int[] redPawnCol = new int[Board.COLS];
        int[] blackPawnCol = new int[Board.COLS];

        for (int sq = 0; sq < Board.SIZE; sq++) {
            int p = cells[sq];
            if (p == 0) {
                continue;
            }
            int type = Piece.type(p);
            int row = sq / Board.COLS;
            int col = sq % Board.COLS;
            int value = VALUES[type];
            boolean red = Piece.isRed(p);

            if (red) {
                score += value + pstValue(type, row, col);
                switch (type) {
                    case Piece.ADVISOR:
                        redAdvisors++;
                        break;
                    case Piece.ELEPHANT:
                        redElephants++;
                        break;
                    case Piece.ROOK:
                        redRooks++;
                        break;
                    case Piece.PAWN:
                        redPawnCol[col]++;
                        if (row <= 4) {
                            score += 12;              // 过河兵
                        }
                        if (row == 0) {
                            score -= 30;              // 底兵价值骤降
                        }
                        break;
                    case Piece.HORSE:
                        if (row <= 4) {
                            score += 14;              // 马过河
                        }
                        break;
                    case Piece.CANNON:
                        if (col == 4) {
                            score += 10;              // 中炮
                        }
                        break;
                    default:
                        break;
                }
            } else {
                score -= value + pstValue(type, Board.ROWS - 1 - row, Board.COLS - 1 - col);
                switch (type) {
                    case Piece.ADVISOR:
                        blackAdvisors++;
                        break;
                    case Piece.ELEPHANT:
                        blackElephants++;
                        break;
                    case Piece.ROOK:
                        blackRooks++;
                        break;
                    case Piece.PAWN:
                        blackPawnCol[col]++;
                        if (row >= 5) {
                            score -= 12;
                        }
                        if (row == 9) {
                            score += 30;
                        }
                        break;
                    case Piece.HORSE:
                        if (row >= 5) {
                            score -= 14;
                        }
                        break;
                    case Piece.CANNON:
                        if (col == 4) {
                            score -= 10;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        score += advisorBonus(redAdvisors) - advisorBonus(blackAdvisors);
        score += elephantBonus(redElephants) - elephantBonus(blackElephants);

        if (redRooks >= 2) {
            score += 26;
        }
        if (blackRooks >= 2) {
            score -= 26;
        }

        // 车路：所在列无己方兵则通畅
        for (int sq = 0; sq < Board.SIZE; sq++) {
            int p = cells[sq];
            if (p == 0 || Piece.type(p) != Piece.ROOK) {
                continue;
            }
            int col = sq % Board.COLS;
            if (Piece.isRed(p)) {
                if (redPawnCol[col] == 0) {
                    score += blackPawnCol[col] == 0 ? 16 : 10;
                }
            } else {
                if (blackPawnCol[col] == 0) {
                    score -= redPawnCol[col] == 0 ? 16 : 10;
                }
            }
        }

        // 连兵
        for (int c = 0; c < Board.COLS; c++) {
            if (redPawnCol[c] > 0
                    && ((c > 0 && redPawnCol[c - 1] > 0) || (c < 8 && redPawnCol[c + 1] > 0))) {
                score += 10;
            }
            if (blackPawnCol[c] > 0
                    && ((c > 0 && blackPawnCol[c - 1] > 0) || (c < 8 && blackPawnCol[c + 1] > 0))) {
                score -= 10;
            }
        }

        return score;
    }

    private static int advisorBonus(int count) {
        if (count >= 2) {
            return 12;
        }
        return count == 1 ? -8 : -34;
    }

    private static int elephantBonus(int count) {
        if (count >= 2) {
            return 10;
        }
        return count == 1 ? -8 : -30;
    }

    /** 以当前走子方视角返回分值，供 negamax 使用。 */
    public static int evaluateForSide(Board b) {
        int e = evaluate(b);
        return b.redToMove ? e : -e;
    }
}
