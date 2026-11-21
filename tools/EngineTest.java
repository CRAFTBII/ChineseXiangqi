import com.workbuddy.xiangqi.ai.Evaluator;
import com.workbuddy.xiangqi.ai.SearchEngine;
import com.workbuddy.xiangqi.game.Board;
import com.workbuddy.xiangqi.game.Move;
import com.workbuddy.xiangqi.game.MoveGen;
import com.workbuddy.xiangqi.game.MoveList;
import com.workbuddy.xiangqi.game.Piece;

/**
 * 离线引擎自检：perft 走法计数、规则特例、AI 搜索。
 * 不依赖 Android，直接用 javac/java 运行。
 */
public final class EngineTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        System.out.println("=== 中国象棋引擎自检 ===");
        System.out.println("初始 FEN: " + new Board().toFen());

        testStartPosition();
        testPerft();
        testHorseLeg();
        testElephantEye();
        testCannonScreen();
        testFlyingGeneral();
        testPawnRules();
        testCheckmate();
        testSearch();

        System.out.println();
        System.out.println("结果: " + passed + " 通过, " + failed + " 失败");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- 基础

    private static void testStartPosition() {
        section("初始局面");
        Board b = new Board();
        int red = 0, black = 0;
        for (int i = 0; i < Board.SIZE; i++) {
            int p = b.pieceAt(i);
            if (p == 0) continue;
            if (Piece.isRed(p)) red++; else black++;
        }
        check("红子 16", red == 16, "实际 " + red);
        check("黑子 16", black == 16, "实际 " + black);
        check("红先", b.redToMove, "redToMove=" + b.redToMove);

        MoveList legal = new MoveList();
        MoveGen.generateLegal(b, legal);
        System.out.println("  初始合法走法数 = " + legal.size);
        check("初始走法数 44", legal.size == 44, "实际 " + legal.size);
    }

    private static void testPerft() {
        section("perft 走法计数");
        Board b = new Board();
        long t0 = System.currentTimeMillis();
        long d1 = perft(b, 1);
        long d2 = perft(b, 2);
        long d3 = perft(b, 3);
        long cost = System.currentTimeMillis() - t0;
        System.out.println("  perft(1) = " + d1);
        System.out.println("  perft(2) = " + d2);
        System.out.println("  perft(3) = " + d3 + "  (" + cost + " ms)");
        check("perft(1) == 44", d1 == 44, "实际 " + d1);
        check("perft(2) == 1920", d2 == 1920, "实际 " + d2);
        check("perft(3) == 79666", d3 == 79666, "实际 " + d3);
    }

    private static long perft(Board b, int depth) {
        if (depth == 0) {
            return 1;
        }
        MoveList list = new MoveList();
        MoveGen.generate(b, list);
        long nodes = 0;
        int side = b.redToMove ? Piece.RED : Piece.BLACK;
        for (int i = 0; i < list.size; i++) {
            int mv = list.moves[i];
            b.makeMove(mv);
            if (!MoveGen.isAttacked(b, b.kingPos(side), Piece.opposite(side))) {
                nodes += perft(b, depth - 1);
            }
            b.undoMove();
        }
        return nodes;
    }

    // ---------------------------------------------------------------- 规则

    private static void testHorseLeg() {
        section("马腿规则");
        // 红马在 (9,1)，(8,1) 有子时不能跳 (7,0)/(7,2)
        Board b = new Board();
        // 空盘放马与王
        b.parseFen("3k5/9/9/9/9/9/9/9/9/1N2K4 w - - 0 1");
        int horseSq = Board.index(9, 1);
        int moves = countMovesFrom(b, horseSq);
        check("空盘马 3 步", moves == 3, "实际 " + moves);

        // 在 (8,1) 放一个兵挡马腿
        b.parseFen("3k5/9/9/9/9/9/9/9/1P7/1N2K4 w - - 0 1");
        moves = countMovesFrom(b, Board.index(9, 1));
        check("马腿被挡剩 1 步", moves == 1, "实际 " + moves);
    }

    private static void testElephantEye() {
        section("塞象眼");
        Board b = new Board();
        b.parseFen("3k5/9/9/9/9/9/9/9/9/2B1K4 w - - 0 1");
        int moves = countMovesFrom(b, Board.index(9, 2));
        check("空盘象 2 步", moves == 2, "实际 " + moves);

        b.parseFen("3k5/9/9/9/9/9/9/9/1P7/2B1K4 w - - 0 1");
        moves = countMovesFrom(b, Board.index(9, 2));
        check("象眼被塞 1 步", moves == 1, "实际 " + moves);
    }

    private static void testCannonScreen() {
        section("炮翻山");
        Board b = new Board();
        // 红炮 (7,1)，同行 (7,4) 有黑卒，(7,6) 有黑马 → 炮可吃马
        b.parseFen("3k5/9/9/9/9/9/9/1C2p1n2/9/4K4 w - - 0 1");
        MoveList legal = new MoveList();
        MoveGen.generateLegal(b, legal);
        boolean canCaptureHorse = false;
        for (int i = 0; i < legal.size; i++) {
            int mv = legal.moves[i];
            if (Move.from(mv) == Board.index(7, 1) && Move.to(mv) == Board.index(7, 6)) {
                canCaptureHorse = true;
            }
        }
        check("炮隔子吃马", canCaptureHorse, "未找到该走法");

        // 中间无炮架时不能吃
        b.parseFen("3k5/9/9/9/9/9/9/1C5n1/9/4K4 w - - 0 1");
        legal.clear();
        MoveGen.generateLegal(b, legal);
        boolean badCapture = false;
        for (int i = 0; i < legal.size; i++) {
            int mv = legal.moves[i];
            if (Move.from(mv) == Board.index(7, 1) && Move.to(mv) == Board.index(7, 7)) {
                badCapture = true;
            }
        }
        check("无炮架不能吃", !badCapture, "错误地生成了吃子走法");
    }

    private static void testFlyingGeneral() {
        section("将帅照面");
        // 红帅 (9,4)，黑将 (0,4)，中间无子 → 红帅不能"吃"黑将，但该局面本身非法
        Board b = new Board();
        b.parseFen("4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1");
        MoveList legal = new MoveList();
        MoveGen.generateLegal(b, legal);
        boolean illegal = false;
        for (int i = 0; i < legal.size; i++) {
            if (Move.to(legal.moves[i]) == Board.index(0, 4)) {
                illegal = true;
            }
        }
        check("照面时帅不能吃将", !illegal, "生成了穿过中线的走法");

        // 中间有子时照面解除
        b.parseFen("4k4/9/9/9/9/9/9/9/9/4K4 w - - 0 1");
        b.setPiece(Board.index(5, 4), Piece.make(Piece.RED, Piece.PAWN));
        legal.clear();
        MoveGen.generateLegal(b, legal);
        check("有遮挡时局面合法", legal.size > 0, "走法数 " + legal.size);
    }

    private static void testPawnRules() {
        section("兵卒规则");
        Board b = new Board();
        // 红兵未过河只能向前
        b.parseFen("3k5/9/9/9/9/9/4P4/9/9/4K4 w - - 0 1");
        int moves = countMovesFrom(b, Board.index(6, 4));
        check("未过河兵 1 步", moves == 1, "实际 " + moves);

        // 过河后可横走
        b.parseFen("3k5/9/9/9/4P4/9/9/9/9/4K4 w - - 0 1");
        moves = countMovesFrom(b, Board.index(4, 4));
        check("过河兵 3 步", moves == 3, "实际 " + moves);

        // 到底线仍可横走，不能前进
        b.parseFen("3k5/4P4/9/9/9/9/9/9/9/4K4 w - - 0 1");
        moves = countMovesFrom(b, Board.index(1, 4));
        check("底线兵 3 步", moves == 3, "实际 " + moves);
    }

    private static void testCheckmate() {
        section("将死判定");
        // 黑将 (0,4) 被红车 (1,4) 将军，黑方无法解 → 红胜
        Board b = new Board();
        b.parseFen("R3k4/4R4/9/9/9/9/9/9/9/4K4 b - - 0 1");
        int result = MoveGen.gameResult(b);
        check("单车将死", result == MoveGen.RED_WIN, "结果 " + result);
        check("将军检测", MoveGen.inCheck(b, Piece.BLACK), "应处于被将军状态");

        // 正常局面
        Board start = new Board();
        check("开局未将军", !MoveGen.inCheck(start), "不应将军");
        check("开局进行中", MoveGen.gameResult(start) == MoveGen.ONGOING, "应继续进行");
    }

    // ---------------------------------------------------------------- AI

    private static void testSearch() {
        section("AI 搜索");
        Board b = new Board();
        SearchEngine engine = new SearchEngine();
        long t0 = System.currentTimeMillis();
        SearchEngine.Result r = engine.search(b, 4, 3000);
        long cost = System.currentTimeMillis() - t0;
        System.out.println("  深度 " + r.depth + " 评分 " + r.score
                + " 节点 " + r.nodes + " 用时 " + cost + "ms");
        System.out.println("  最佳走法 " + Move.describe(r.bestMove));
        check("返回走法", r.bestMove != Move.NONE, "bestMove=" + r.bestMove);
        check("走法合法", MoveGen.isLegalMove(b, r.bestMove), "非法走法");
        check("引擎评估对称(开局≈0)",
                Math.abs(Evaluator.evaluate(b)) < 40, "评估 " + Evaluator.evaluate(b));

        // 一步杀：红车在 (1,0)，黑将 (0,4)，红车走到 (1,4) 即将军… 用简单残局验证 AI 会选择吃将/绝杀
        Board m = new Board();
        m.parseFen("3k5/9/9/9/9/9/9/9/9/3RK4 w - - 0 1");
        SearchEngine.Result mr = new SearchEngine().search(m, 4, 3000);
        System.out.println("  残局最佳走法 " + Move.describe(mr.bestMove) + " 评分 " + mr.score);
        check("残局能找到绝杀", mr.score > SearchEngine.MATE_BOUND - 10, "评分 " + mr.score);
    }

    // ---------------------------------------------------------------- 工具

    private static int countMovesFrom(Board b, int square) {
        MoveList legal = new MoveList();
        MoveGen.generateLegal(b, legal);
        int count = 0;
        for (int i = 0; i < legal.size; i++) {
            if (Move.from(legal.moves[i]) == square) {
                count++;
            }
        }
        return count;
    }

    private static void section(String name) {
        System.out.println();
        System.out.println("[" + name + "]");
    }

    private static void check(String label, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + label);
        } else {
            failed++;
            System.out.println("  FAIL  " + label + "  (" + detail + ")");
        }
    }
}
