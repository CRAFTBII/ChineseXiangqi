import com.workbuddy.xiangqi.ai.SearchEngine;
import com.workbuddy.xiangqi.game.Board;
import com.workbuddy.xiangqi.game.Move;
import com.workbuddy.xiangqi.game.MoveGen;

import java.util.ArrayList;
import java.util.List;

/** 自对弈到终局，输出走法序列（用于种子数据与回归测试）。 */
public final class SelfPlay {

    public static void main(String[] args) {
        int depth = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        long timeMs = args.length > 1 ? Long.parseLong(args[1]) : 120L;

        Board board = new Board();
        SearchEngine engine = new SearchEngine();
        List<Integer> moves = new ArrayList<>();
        int result = MoveGen.ONGOING;

        int ply = 0;
        for (; ply < 400; ply++) {
            result = MoveGen.gameResult(board);
            if (result != MoveGen.ONGOING) {
                break;
            }
            SearchEngine.Result r = engine.search(board, depth, timeMs);
            if (r.bestMove == Move.NONE) {
                break;
            }
            moves.add(r.bestMove);
            board.makeMove(r.bestMove);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(moves.get(i));
        }
        String title = result == MoveGen.RED_WIN ? "红方胜"
                : result == MoveGen.BLACK_WIN ? "黑方胜"
                : result == MoveGen.DRAW ? "和棋" : "未终局";
        String sub = result == MoveGen.ONGOING ? ""
                : (MoveGen.inCheck(board) ? "将死" : "无子可动");
        System.out.println("LIMIT " + ply + " plies");
        System.out.println("RESULT " + title + " " + sub);
        System.out.println("MOVES " + sb);
    }
}
