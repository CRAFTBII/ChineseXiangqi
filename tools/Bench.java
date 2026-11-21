import com.workbuddy.xiangqi.ai.Evaluator;
import com.workbuddy.xiangqi.ai.SearchEngine;
import com.workbuddy.xiangqi.game.Board;
import com.workbuddy.xiangqi.game.Move;
import com.workbuddy.xiangqi.game.MoveGen;

/** 搜索引擎性能/棋力基准。 */
public final class Bench {

    public static void main(String[] args) {
        System.out.println("=== 引擎基准 ===");

        bench("开局", Board.START_FEN, 3000);

        // 中炮开局若干回合后的典型中局
        String mid = "r1bakab1r/9/1cn3nc1/p1p1p1p1p/9/2P3P2/P3P3P/1CN3NC1/9/R1BAKAB1R w - - 0 1";
        bench("中局", mid, 3000);

        // 战术：红车可白吃黑车
        String tactic = "3k5/9/9/9/9/4r4/9/9/9/4RK3 w - - 0 1";
        benchTactic("白吃黑车", tactic, 900);

        // 一步杀
        String mate = "3k5/9/9/9/9/9/9/9/9/3RK4 w - - 0 1";
        benchTactic("一步杀", mate, 900);
    }

    private static void bench(String name, String fen, long ms) {
        Board b = new Board();
        b.parseFen(fen);
        SearchEngine e = new SearchEngine();
        long t0 = System.currentTimeMillis();
        SearchEngine.Result r = e.search(b, 64, ms);
        long cost = System.currentTimeMillis() - t0;
        double nps = r.nodes * 1000.0 / Math.max(1, cost);
        System.out.printf("%-8s 深度 %2d  评分 %6d  节点 %8d  %5dms  %,.0f nps  走法 %s%n",
                name, r.depth, r.score, r.nodes, cost, nps, Move.describe(r.bestMove));
    }

    private static void benchTactic(String name, String fen, long ms) {
        Board b = new Board();
        b.parseFen(fen);
        SearchEngine e = new SearchEngine();
        SearchEngine.Result r = e.search(b, 64, ms);
        boolean legal = MoveGen.isLegalMove(b, r.bestMove);
        System.out.printf("%-8s 深度 %2d  评分 %6d  走法 %s  合法=%s  红方评估=%d%n",
                name, r.depth, r.score, Move.describe(r.bestMove), legal,
                Evaluator.evaluate(b));
    }
}
