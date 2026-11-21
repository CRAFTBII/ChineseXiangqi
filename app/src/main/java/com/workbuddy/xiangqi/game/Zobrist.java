package com.workbuddy.xiangqi.game;

/** Zobrist 随机数表。使用固定种子，保证同一进程内以及跨进程可复现。 */
public final class Zobrist {

    public static final long RED_TO_MOVE;

    /** PIECE[pieceCode][square]，pieceCode 取值 0..31。 */
    public static final long[][] PIECE = new long[32][Board.SIZE];

    static {
        long seed = 0x9E3779B97F4A7C15L;
        seed = mix(seed);
        for (int p = 0; p < 32; p++) {
            for (int s = 0; s < Board.SIZE; s++) {
                seed += 0x9E3779B97F4A7C15L;
                PIECE[p][s] = mix(seed);
            }
        }
        seed += 0x9E3779B97F4A7C15L;
        RED_TO_MOVE = mix(seed);
    }

    private Zobrist() {
    }

    public static long piece(int piece, int square) {
        return PIECE[piece & 0x1F][square];
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
