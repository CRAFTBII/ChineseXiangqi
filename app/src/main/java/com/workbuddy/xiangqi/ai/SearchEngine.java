package com.workbuddy.xiangqi.ai;

import com.workbuddy.xiangqi.game.Board;
import com.workbuddy.xiangqi.game.Move;
import com.workbuddy.xiangqi.game.MoveGen;
import com.workbuddy.xiangqi.game.MoveList;
import com.workbuddy.xiangqi.game.Piece;

import java.util.Arrays;

/**
 * Alpha-Beta 搜索（Negamax 形式）。
 *
 * <p>特性：迭代加深 / 置换表 / MVV-LVA + 杀手 + 历史启发 / PVS / 静态搜索 /
 * 空着裁剪(null move) / 后期走法缩减(LMR) / 将军延伸 / 重复局面检测。
 * 所有走法缓冲区按层预分配，搜索期间不产生垃圾对象。</p>
 */
public final class SearchEngine {

    public static final int MATE = 30000;
    public static final int MATE_BOUND = 29000;
    private static final int INF = 40000;
    private static final int MAX_PLY = 64;
    private static final int TT_BITS = 16;
    private static final int TT_SIZE = 1 << TT_BITS;
    private static final int TT_MASK = TT_SIZE - 1;

    public static final class Result {
        public int bestMove = Move.NONE;
        public int score;
        public int depth;
        public long nodes;
        public long timeMs;
        public boolean completed;
        /** 搜索到的变例（bestMove 起），用于调试/提示。 */
        public int pvLength;
    }

    private static final class Entry {
        long key;
        int move = Move.NONE;
        int score;
        int depth = -1;
        int flag;
    }

    private static final int FLAG_EXACT = 0;
    private static final int FLAG_LOWER = 1;
    private static final int FLAG_UPPER = 2;
    private static final int NO_MOVE_SCORE = Integer.MIN_VALUE / 2;

    private final Entry[] table = new Entry[TT_SIZE];
    private final int[] killers = new int[MAX_PLY * 2];
    private final int[][] history = new int[2][Board.SIZE * Board.SIZE];
    private final long[] pathHash = new long[MAX_PLY + 4];

    private final MoveList[] lists = new MoveList[MAX_PLY];
    private final int[][] scoreStack = new int[MAX_PLY][MoveList.CAPACITY];

    private volatile boolean stopped;
    private long deadline;
    private long nodes;
    private long startTime;
    private int rootDepth;

    public SearchEngine() {
        for (int i = 0; i < TT_SIZE; i++) {
            table[i] = new Entry();
        }
        for (int i = 0; i < MAX_PLY; i++) {
            lists[i] = new MoveList();
        }
    }

    public void cancel() {
        stopped = true;
    }

    public void clearTable() {
        for (Entry e : table) {
            e.key = 0L;
            e.depth = -1;
            e.move = Move.NONE;
            e.flag = FLAG_EXACT;
            e.score = 0;
        }
    }

    public long nodesSearched() {
        return nodes;
    }

    /**
     * 迭代加深搜索。
     *
     * @param maxDepth    最大深度上限（时间优先，通常先到时间）
     * @param timeLimitMs 时间上限（毫秒）
     */
    public Result search(Board board, int maxDepth, long timeLimitMs) {
        stopped = false;
        nodes = 0;
        startTime = System.currentTimeMillis();
        deadline = startTime + timeLimitMs;
        clearTable();
        for (int[] row : history) {
            Arrays.fill(row, 0);
        }
        Arrays.fill(killers, 0);
        Arrays.fill(pathHash, 0L);

        Result result = new Result();
        final int side = board.redToMove ? Piece.RED : Piece.BLACK;

        MoveList root = lists[0];
        MoveGen.generate(board, root);
        int[] rootScores = new int[root.size];
        int legalCount = 0;
        for (int i = 0; i < root.size; i++) {
            int mv = root.moves[i];
            board.makeMove(mv);
            boolean legal = !MoveGen.isAttacked(board, board.kingPos(side), Piece.opposite(side));
            board.undoMove();
            if (!legal) {
                rootScores[i] = NO_MOVE_SCORE;
                continue;
            }
            legalCount++;
            rootScores[i] = orderScore(board, mv, Move.NONE, 0, false);
        }
        if (legalCount == 0) {
            result.completed = true;
            result.timeMs = System.currentTimeMillis() - startTime;
            return result;
        }

        for (int depth = 1; depth <= maxDepth; depth++) {
            rootDepth = depth;
            int alpha = -INF;
            int beta = INF;
            int bestScore = -INF;
            int bestIndex = -1;
            int[] depthScores = scoreStack[0];
            boolean aborted = false;

            for (int i = 0; i < root.size; i++) {
                int mv = root.moves[i];
                if (rootScores[i] == NO_MOVE_SCORE) {
                    depthScores[i] = NO_MOVE_SCORE;
                    continue;
                }
                board.makeMove(mv);
                int score;
                if (bestIndex < 0) {
                    score = -negamax(board, depth - 1, -beta, -alpha, 1);
                } else {
                    score = -negamax(board, depth - 1, -alpha - 1, -alpha, 1);
                    if (score > alpha && !stopped) {
                        score = -negamax(board, depth - 1, -beta, -alpha, 1);
                    }
                }
                board.undoMove();
                if (stopped) {
                    aborted = true;
                    break;
                }
                depthScores[i] = score;
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
                if (score > alpha) {
                    alpha = score;
                }
            }

            if (aborted || bestIndex < 0) {
                break;
            }

            // 本轮最优走法置顶，稳定下一轮排序
            int bestMove = root.moves[bestIndex];
            int bestSc = depthScores[bestIndex];
            System.arraycopy(root.moves, 0, root.moves, 1, bestIndex);
            System.arraycopy(depthScores, 0, depthScores, 1, bestIndex);
            root.moves[0] = bestMove;
            depthScores[0] = bestSc;
            System.arraycopy(depthScores, 0, rootScores, 0, root.size);

            result.bestMove = bestMove;
            result.score = bestSc;
            result.depth = depth;
            result.completed = true;

            if (bestSc > MATE_BOUND || bestSc < -MATE_BOUND) {
                break; // 已算出必杀
            }

            // 时间分配：已用掉一半预算就不再加深，保证有完整结果返回
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed * 2 >= timeLimitMs) {
                break;
            }
            // 历史启发衰减，避免旧深度数据主导排序
            for (int[] row : history) {
                for (int k = 0; k < row.length; k++) {
                    row[k] >>= 1;
                }
            }
        }

        result.nodes = nodes;
        result.timeMs = System.currentTimeMillis() - startTime;
        return result;
    }

    private int negamax(Board b, int depth, int alpha, int beta, int ply) {
        nodes++;
        if ((nodes & 511) == 0 && System.currentTimeMillis() > deadline) {
            stopped = true;
            return 0;
        }
        if (ply >= MAX_PLY) {
            return Evaluator.evaluateForSide(b);
        }

        final int side = b.redToMove ? Piece.RED : Piece.BLACK;

        // 重复局面按和棋处理，避免搜索陷入长将循环
        long key = b.positionKey();
        for (int i = ply - 2; i >= 0; i -= 2) {
            if (pathHash[i] == key) {
                return 0;
            }
        }
        pathHash[ply] = key;

        final boolean inChk = MoveGen.inCheck(b, side);
        if (depth <= 0) {
            return quiesce(b, alpha, beta, ply);
        }

        int ttMove = Move.NONE;
        Entry e = table[(int) (key & TT_MASK)];
        if (e.key == key && e.depth >= 0) {
            ttMove = e.move;
            if (e.depth >= depth) {
                int s = e.score;
                if (s > MATE_BOUND) {
                    s -= ply;
                } else if (s < -MATE_BOUND) {
                    s += ply;
                }
                if (e.flag == FLAG_EXACT) {
                    return s;
                }
                if (e.flag == FLAG_LOWER && s >= beta) {
                    return s;
                }
                if (e.flag == FLAG_UPPER && s <= alpha) {
                    return s;
                }
            }
        }

        // 空着裁剪：非将军、有足够子力、不在必杀分区间时尝试
        if (!inChk && depth >= 3 && ply > 0 && beta < MATE_BOUND && !b.lastWasNull()
                && hasHeavyMaterial(b, side)) {
            int reduction = 2 + (depth > 6 ? 1 : 0);
            b.makeNullMove();
            int nullScore = -negamax(b, depth - 1 - reduction, -beta, -beta + 1, ply + 1);
            b.undoMove();
            if (stopped) {
                return 0;
            }
            if (nullScore >= beta) {
                return beta;
            }
        }

        MoveList list = lists[ply];
        MoveGen.generate(b, list);
        int[] scores = scoreStack[ply];
        for (int i = 0; i < list.size; i++) {
            scores[i] = orderScore(b, list.moves[i], ttMove, ply, true);
        }

        int bestScore = -INF;
        int bestMove = Move.NONE;
        int legal = 0;
        final int alphaOrig = alpha;

        for (int i = 0; i < list.size; i++) {
            pickBest(list, scores, i);
            int mv = list.moves[i];
            b.makeMove(mv);
            if (MoveGen.isAttacked(b, b.kingPos(side), Piece.opposite(side))) {
                b.undoMove();
                continue;
            }
            legal++;

            int score;
            if (legal == 1) {
                score = -negamax(b, depth - 1, -beta, -alpha, ply + 1);
            } else {
                int newDepth = depth - 1;
                // 后期走法缩减：靠后的安静走法先降深度试探
                boolean quiet = !Move.isCapture(mv);
                int reduction = 0;
                if (quiet && depth >= 3 && legal > 3 && !inChk) {
                    reduction = 1 + (legal > 6 && depth >= 5 ? 1 : 0);
                }
                score = -negamax(b, newDepth - reduction, -alpha - 1, -alpha, ply + 1);
                if (score > alpha && reduction > 0 && !stopped) {
                    score = -negamax(b, newDepth, -alpha - 1, -alpha, ply + 1);
                }
                if (score > alpha && score < beta && !stopped) {
                    score = -negamax(b, newDepth, -beta, -alpha, ply + 1);
                }
            }
            b.undoMove();

            if (stopped) {
                return 0;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMove = mv;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                if (!Move.isCapture(mv)) {
                    int k = ply * 2;
                    if (killers[k] != mv) {
                        killers[k + 1] = killers[k];
                        killers[k] = mv;
                    }
                    history[b.redToMove ? 0 : 1][Move.from(mv) * Board.SIZE + Move.to(mv)]
                            += depth * depth;
                }
                break;
            }
        }

        if (legal == 0) {
            return -MATE + ply; // 将死或困毙
        }

        int storeScore = bestScore;
        if (storeScore > MATE_BOUND) {
            storeScore += ply;
        } else if (storeScore < -MATE_BOUND) {
            storeScore -= ply;
        }
        Entry slot = table[(int) (key & TT_MASK)];
        slot.key = key;
        slot.move = bestMove;
        slot.score = storeScore;
        slot.depth = depth;
        if (bestScore <= alphaOrig) {
            slot.flag = FLAG_UPPER;
        } else if (bestScore >= beta) {
            slot.flag = FLAG_LOWER;
        } else {
            slot.flag = FLAG_EXACT;
        }
        return bestScore;
    }

    private static boolean hasHeavyMaterial(Board b, int side) {
        for (int sq = 0; sq < Board.SIZE; sq++) {
            int p = b.cells[sq];
            if (p == 0 || Piece.side(p) != side) {
                continue;
            }
            int t = Piece.type(p);
            if (t == Piece.ROOK || t == Piece.HORSE || t == Piece.CANNON) {
                return true;
            }
        }
        return false;
    }

    /** 静态搜索：只展开吃子；被将军时展开全部应将走法。 */
    private int quiesce(Board b, int alpha, int beta, int ply) {
        nodes++;
        if ((nodes & 511) == 0 && System.currentTimeMillis() > deadline) {
            stopped = true;
            return 0;
        }
        if (ply >= MAX_PLY) {
            return Evaluator.evaluateForSide(b);
        }

        final int side = b.redToMove ? Piece.RED : Piece.BLACK;
        final boolean inChk = MoveGen.inCheck(b, side);

        int best;
        if (!inChk) {
            best = Evaluator.evaluateForSide(b);
            if (best >= beta) {
                return best;
            }
            if (best > alpha) {
                alpha = best;
            }
        } else {
            best = -INF;
        }

        MoveList list = lists[ply];
        MoveGen.generate(b, list);
        int[] scores = scoreStack[ply];
        for (int i = 0; i < list.size; i++) {
            int mv = list.moves[i];
            if (Move.isCapture(mv)) {
                scores[i] = 100000 + Evaluator.pieceValue(Piece.type(Move.captured(mv))) * 10
                        - Evaluator.pieceValue(Piece.type(b.cells[Move.from(mv)]));
            } else {
                scores[i] = inChk ? 0 : -1;
            }
        }

        int legal = 0;
        for (int i = 0; i < list.size; i++) {
            pickBest(list, scores, i);
            int mv = list.moves[i];
            if (!inChk && !Move.isCapture(mv)) {
                continue;
            }
            b.makeMove(mv);
            if (MoveGen.isAttacked(b, b.kingPos(side), Piece.opposite(side))) {
                b.undoMove();
                continue;
            }
            legal++;
            int score = -quiesce(b, -beta, -alpha, ply + 1);
            b.undoMove();
            if (stopped) {
                return 0;
            }
            if (score > best) {
                best = score;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break;
            }
        }

        if (inChk && legal == 0) {
            return -MATE + ply;
        }
        return best;
    }

    private int orderScore(Board b, int mv, int ttMove, int ply, boolean useHistory) {
        if (mv == ttMove) {
            return 1_000_000;
        }
        if (Move.isCapture(mv)) {
            int victim = Move.captured(mv);
            int attacker = b.cells[Move.from(mv)];
            return 200_000 + Evaluator.pieceValue(Piece.type(victim)) * 10
                    - Evaluator.pieceValue(Piece.type(attacker));
        }
        int k = ply * 2;
        if (mv == killers[k] || mv == killers[k + 1]) {
            return 150_000;
        }
        if (useHistory) {
            return history[b.redToMove ? 0 : 1][Move.from(mv) * Board.SIZE + Move.to(mv)];
        }
        return 0;
    }

    private static void pickBest(MoveList list, int[] scores, int from) {
        int best = from;
        for (int i = from + 1; i < list.size; i++) {
            if (scores[i] > scores[best]) {
                best = i;
            }
        }
        if (best != from) {
            int tmpMove = list.moves[from];
            list.moves[from] = list.moves[best];
            list.moves[best] = tmpMove;
            int tmpScore = scores[from];
            scores[from] = scores[best];
            scores[best] = tmpScore;
        }
    }
}
