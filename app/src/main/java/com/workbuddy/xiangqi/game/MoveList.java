package com.workbuddy.xiangqi.game;

/** 走法列表，搜索过程中复用同一个实例以避免产生垃圾对象。 */
public final class MoveList {

    public static final int CAPACITY = 160;

    public final int[] moves = new int[CAPACITY];
    public int size;

    public void clear() {
        size = 0;
    }

    public void add(int move) {
        if (size < CAPACITY) {
            moves[size++] = move;
        }
    }

    public void addIfEmpty(int from, int to, int captured) {
        if (size < CAPACITY) {
            moves[size++] = Move.encode(from, to, captured);
        }
    }
}
