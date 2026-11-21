package com.workbuddy.xiangqi.game;

/**
 * 走法生成与局面合法性判定。
 * 先生成"伪合法走法"，再用 {@link #isAttacked} 过滤掉走完后己方被将军的走法。
 */
public final class MoveGen {

    private static final int[] ORTHO_DR = {-1, 1, 0, 0};
    private static final int[] ORTHO_DC = {0, 0, -1, 1};

    private static final int[] HORSE_DR = {-2, -2, -1, 1, 2, 2, 1, -1};
    private static final int[] HORSE_DC = {-1, 1, 2, 2, 1, -1, -2, -2};

    private static final int[] DIAG_DR = {-1, -1, 1, 1};
    private static final int[] DIAG_DC = {-1, 1, -1, 1};

    private MoveGen() {
    }

    public static int forward(int side) {
        return side == Piece.RED ? -1 : 1;
    }

    // ------------------------------------------------------------ 生成

    /** 生成伪合法走法（未过滤自杀）。 */
    public static void generate(Board b, MoveList out) {
        out.clear();
        final byte[] cells = b.cells;
        final int side = b.redToMove ? Piece.RED : Piece.BLACK;

        for (int sq = 0; sq < Board.SIZE; sq++) {
            int p = cells[sq];
            if (p == 0 || Piece.side(p) != side) {
                continue;
            }
            int r = sq / Board.COLS;
            int c = sq % Board.COLS;
            switch (Piece.type(p)) {
                case Piece.KING:
                    genKing(b, out, side, r, c, sq);
                    break;
                case Piece.ADVISOR:
                    genAdvisor(b, out, side, r, c, sq);
                    break;
                case Piece.ELEPHANT:
                    genElephant(b, out, side, r, c, sq);
                    break;
                case Piece.HORSE:
                    genHorse(b, out, side, r, c, sq);
                    break;
                case Piece.ROOK:
                    genRook(b, out, side, r, c, sq);
                    break;
                case Piece.CANNON:
                    genCannon(b, out, side, r, c, sq);
                    break;
                case Piece.PAWN:
                    genPawn(b, out, side, r, c, sq);
                    break;
                default:
                    break;
            }
        }
    }

    private static void genKing(Board b, MoveList out, int side, int r, int c, int sq) {
        for (int d = 0; d < 4; d++) {
            int nr = r + ORTHO_DR[d];
            int nc = c + ORTHO_DC[d];
            if (!inPalace(side, nr, nc)) {
                continue;
            }
            int to = nr * Board.COLS + nc;
            int target = b.cells[to];
            if (target == 0) {
                out.addIfEmpty(sq, to, 0);
            } else if (Piece.side(target) != side) {
                out.addIfEmpty(sq, to, target);
            }
        }
    }

    private static void genAdvisor(Board b, MoveList out, int side, int r, int c, int sq) {
        for (int d = 0; d < 4; d++) {
            int nr = r + DIAG_DR[d];
            int nc = c + DIAG_DC[d];
            if (!inPalace(side, nr, nc)) {
                continue;
            }
            int to = nr * Board.COLS + nc;
            int target = b.cells[to];
            if (target == 0) {
                out.addIfEmpty(sq, to, 0);
            } else if (Piece.side(target) != side) {
                out.addIfEmpty(sq, to, target);
            }
        }
    }

    private static void genElephant(Board b, MoveList out, int side, int r, int c, int sq) {
        for (int d = 0; d < 4; d++) {
            int nr = r + DIAG_DR[d] * 2;
            int nc = c + DIAG_DC[d] * 2;
            if (nr < 0 || nr >= Board.ROWS || nc < 0 || nc >= Board.COLS) {
                continue;
            }
            // 不可过河
            if (side == Piece.RED ? nr < 5 : nr > 4) {
                continue;
            }
            // 塞象眼
            int eye = (r + DIAG_DR[d]) * Board.COLS + (c + DIAG_DC[d]);
            if (b.cells[eye] != 0) {
                continue;
            }
            int to = nr * Board.COLS + nc;
            int target = b.cells[to];
            if (target == 0) {
                out.addIfEmpty(sq, to, 0);
            } else if (Piece.side(target) != side) {
                out.addIfEmpty(sq, to, target);
            }
        }
    }

    private static void genHorse(Board b, MoveList out, int side, int r, int c, int sq) {
        for (int d = 0; d < 8; d++) {
            int dr = HORSE_DR[d];
            int dc = HORSE_DC[d];
            int nr = r + dr;
            int nc = c + dc;
            if (nr < 0 || nr >= Board.ROWS || nc < 0 || nc >= Board.COLS) {
                continue;
            }
            // 马腿：向目标方向迈出的第一步正交格子
            int legR = r + (dr > 0 ? 1 : dr < 0 ? -1 : 0) * (Math.abs(dr) == 2 ? 1 : 0);
            int legC = c + (dc > 0 ? 1 : dc < 0 ? -1 : 0) * (Math.abs(dc) == 2 ? 1 : 0);
            if (b.cells[legR * Board.COLS + legC] != 0) {
                continue;
            }
            int to = nr * Board.COLS + nc;
            int target = b.cells[to];
            if (target == 0) {
                out.addIfEmpty(sq, to, 0);
            } else if (Piece.side(target) != side) {
                out.addIfEmpty(sq, to, target);
            }
        }
    }

    private static void genRook(Board b, MoveList out, int side, int r, int c, int sq) {
        for (int d = 0; d < 4; d++) {
            int nr = r + ORTHO_DR[d];
            int nc = c + ORTHO_DC[d];
            while (nr >= 0 && nr < Board.ROWS && nc >= 0 && nc < Board.COLS) {
                int to = nr * Board.COLS + nc;
                int target = b.cells[to];
                if (target == 0) {
                    out.addIfEmpty(sq, to, 0);
                } else {
                    if (Piece.side(target) != side) {
                        out.addIfEmpty(sq, to, target);
                    }
                    break;
                }
                nr += ORTHO_DR[d];
                nc += ORTHO_DC[d];
            }
        }
    }

    private static void genCannon(Board b, MoveList out, int side, int r, int c, int sq) {
        for (int d = 0; d < 4; d++) {
            int nr = r + ORTHO_DR[d];
            int nc = c + ORTHO_DC[d];
            boolean jumped = false;
            while (nr >= 0 && nr < Board.ROWS && nc >= 0 && nc < Board.COLS) {
                int to = nr * Board.COLS + nc;
                int target = b.cells[to];
                if (!jumped) {
                    if (target == 0) {
                        out.addIfEmpty(sq, to, 0);
                    } else {
                        jumped = true;
                    }
                } else {
                    if (target != 0) {
                        if (Piece.side(target) != side) {
                            out.addIfEmpty(sq, to, target);
                        }
                        break;
                    }
                }
                nr += ORTHO_DR[d];
                nc += ORTHO_DC[d];
            }
        }
    }

    private static void genPawn(Board b, MoveList out, int side, int r, int c, int sq) {
        int dir = forward(side);
        // 向前
        int nr = r + dir;
        if (nr >= 0 && nr < Board.ROWS) {
            int to = nr * Board.COLS + c;
            int target = b.cells[to];
            if (target == 0) {
                out.addIfEmpty(sq, to, 0);
            } else if (Piece.side(target) != side) {
                out.addIfEmpty(sq, to, target);
            }
        }
        // 过河后可横走
        boolean crossed = side == Piece.RED ? r <= 4 : r >= 5;
        if (crossed) {
            for (int dc = -1; dc <= 1; dc += 2) {
                int nc2 = c + dc;
                if (nc2 < 0 || nc2 >= Board.COLS) {
                    continue;
                }
                int to = r * Board.COLS + nc2;
                int target = b.cells[to];
                if (target == 0) {
                    out.addIfEmpty(sq, to, 0);
                } else if (Piece.side(target) != side) {
                    out.addIfEmpty(sq, to, target);
                }
            }
        }
    }

    // ------------------------------------------------------------ 合法性

    private static boolean inPalace(int side, int r, int c) {
        if (c < 3 || c > 5) {
            return false;
        }
        return side == Piece.RED ? (r >= 7 && r <= 9) : (r >= 0 && r <= 2);
    }

    /** 生成完全合法走法。 */
    public static void generateLegal(Board b, MoveList out) {
        MoveList pseudo = new MoveList();
        generate(b, pseudo);
        out.clear();
        int side = b.redToMove ? Piece.RED : Piece.BLACK;
        for (int i = 0; i < pseudo.size; i++) {
            int mv = pseudo.moves[i];
            b.makeMove(mv);
            boolean safe = !isAttacked(b, b.kingPos(side), Piece.opposite(side));
            b.undoMove();
            if (safe) {
                out.add(mv);
            }
        }
    }

    public static boolean isLegalMove(Board b, int move) {
        MoveList legal = new MoveList();
        generateLegal(b, legal);
        for (int i = 0; i < legal.size; i++) {
            if (Move.same(legal.moves[i], move)) {
                return true;
            }
        }
        return false;
    }

    public static boolean inCheck(Board b, int side) {
        int king = b.kingPos(side);
        if (king < 0) {
            return true;
        }
        return isAttacked(b, king, Piece.opposite(side));
    }

    public static boolean inCheck(Board b) {
        return inCheck(b, b.redToMove ? Piece.RED : Piece.BLACK);
    }

    /**
     * 判断 sq 是否被 bySide 一方攻击。
     * 覆盖：车、炮（隔一子）、马（蹩腿）、兵/卒（含过河横走）、将帅照面。
     */
    public static boolean isAttacked(Board b, int sq, int bySide) {
        if (sq < 0) {
            return false;
        }
        final byte[] cells = b.cells;
        final int r0 = sq / Board.COLS;
        final int c0 = sq % Board.COLS;

        // 车 / 炮 / 将帅（直线）
        for (int d = 0; d < 4; d++) {
            int nr = r0 + ORTHO_DR[d];
            int nc = c0 + ORTHO_DC[d];
            int blockers = 0;
            while (nr >= 0 && nr < Board.ROWS && nc >= 0 && nc < Board.COLS) {
                int p = cells[nr * Board.COLS + nc];
                if (p != 0) {
                    if (blockers == 0) {
                        if (Piece.side(p) == bySide) {
                            int t = Piece.type(p);
                            if (t == Piece.ROOK) {
                                return true;
                            }
                            if (t == Piece.KING) {
                                // 同列无遮挡 = 照面；贴脸相邻也算
                                if (d < 2) {
                                    return true;
                                }
                                if (Math.abs(nr - r0) + Math.abs(nc - c0) == 1) {
                                    return true;
                                }
                            }
                        }
                        blockers = 1;
                    } else {
                        if (Piece.side(p) == bySide && Piece.type(p) == Piece.CANNON) {
                            return true;
                        }
                        break;
                    }
                }
                nr += ORTHO_DR[d];
                nc += ORTHO_DC[d];
            }
        }

        // 马
        for (int d = 0; d < 8; d++) {
            int mr = r0 + HORSE_DR[d];
            int mc = c0 + HORSE_DC[d];
            if (mr < 0 || mr >= Board.ROWS || mc < 0 || mc >= Board.COLS) {
                continue;
            }
            int p = cells[mr * Board.COLS + mc];
            if (p == 0 || Piece.side(p) != bySide || Piece.type(p) != Piece.HORSE) {
                continue;
            }
            // 马腿：从马出发朝目标方向的第一格
            int legR2;
            int legC2;
            if (Math.abs(HORSE_DR[d]) == 2) {
                legR2 = mr + (r0 - mr) / 2;
                legC2 = mc;
            } else {
                legR2 = mr;
                legC2 = mc + (c0 - mc) / 2;
            }
            if (cells[legR2 * Board.COLS + legC2] == 0) {
                return true;
            }
        }

        // 兵 / 卒
        if (bySide == Piece.RED) {
            // 红兵向上走：能攻击 sq 的红兵在 (r0+1, c0)
            if (r0 + 1 < Board.ROWS) {
                int p = cells[(r0 + 1) * Board.COLS + c0];
                if (p != 0 && Piece.side(p) == Piece.RED && Piece.type(p) == Piece.PAWN) {
                    return true;
                }
            }
            // 已过河的红兵可横走攻击
            if (r0 <= 4) {
                for (int dc = -1; dc <= 1; dc += 2) {
                    int nc = c0 + dc;
                    if (nc < 0 || nc >= Board.COLS) {
                        continue;
                    }
                    int p = cells[r0 * Board.COLS + nc];
                    if (p != 0 && Piece.side(p) == Piece.RED && Piece.type(p) == Piece.PAWN) {
                        return true;
                    }
                }
            }
        } else {
            if (r0 - 1 >= 0) {
                int p = cells[(r0 - 1) * Board.COLS + c0];
                if (p != 0 && Piece.side(p) == Piece.BLACK && Piece.type(p) == Piece.PAWN) {
                    return true;
                }
            }
            if (r0 >= 5) {
                for (int dc = -1; dc <= 1; dc += 2) {
                    int nc = c0 + dc;
                    if (nc < 0 || nc >= Board.COLS) {
                        continue;
                    }
                    int p = cells[r0 * Board.COLS + nc];
                    if (p != 0 && Piece.side(p) == Piece.BLACK && Piece.type(p) == Piece.PAWN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------ 终局

    public static final int ONGOING = 0;
    public static final int RED_WIN = 1;
    public static final int BLACK_WIN = 2;
    public static final int DRAW = 3;

    /**
     * 判断当前局面结果。
     * 无合法走法：被将军为将死（负），不被将军为困毙（同样判负，符合中国象棋规则）。
     */
    public static int gameResult(Board b) {
        MoveList legal = new MoveList();
        generateLegal(b, legal);
        if (legal.size == 0) {
            return b.redToMove ? BLACK_WIN : RED_WIN;
        }
        if (b.halfMoveClock() >= 120) {
            return DRAW; // 60 回合无吃子
        }
        return ONGOING;
    }
}
