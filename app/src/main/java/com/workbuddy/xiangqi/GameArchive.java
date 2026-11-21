package com.workbuddy.xiangqi;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * 已结束对局的归档（多局保留，供复盘）。
 * 存储格式：每条记录一行，字段以 ';' 分隔，最后一段是逗号分隔的走法。
 */
public final class GameArchive {

    private static final String PREF = "xiangqi_archive";
    private static final String K_LIST = "games";
    /** 最多保留的对局数，超出后丢弃最早的一局。 */
    private static final int MAX_RECORDS = 100;

    private GameArchive() {
    }

    public static final class Record {
        public int[] moves = new int[0];
        public int difficulty = 1;
        public String title = "";
        public String subtitle = "";
        public long elapsedMs;
        public long time;
    }

    public static void add(Context c, Record record) {
        if (record == null || record.moves == null || record.moves.length == 0) {
            return;
        }
        List<Record> list = load(c);
        // 同一局不重复记录（按走法序列 + 时间近似判重）
        for (Record r : list) {
            if (r.moves.length == record.moves.length && r.time == record.time) {
                return;
            }
        }
        list.add(0, record);
        while (list.size() > MAX_RECORDS) {
            list.remove(list.size() - 1);
        }
        save(c, list);
    }

    public static List<Record> load(Context c) {
        List<Record> out = new ArrayList<>();
        String raw = prefs(c).getString(K_LIST, "");
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String[] lines = raw.split("\n");
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(";", -1);
            if (f.length < 6) {
                continue;
            }
            Record r = new Record();
            try {
                r.time = Long.parseLong(f[0]);
                r.difficulty = Integer.parseInt(f[1]);
                r.elapsedMs = Long.parseLong(f[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            r.title = f[3];
            r.subtitle = f[4];
            r.moves = parseMoves(f[5]);
            if (r.moves.length > 0) {
                out.add(r);
            }
        }
        return out;
    }

    public static Record get(Context c, int index) {
        List<Record> list = load(c);
        if (index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }

    public static int count(Context c) {
        return load(c).size();
    }

    public static void clear(Context c) {
        prefs(c).edit().remove(K_LIST).apply();
    }

    private static void save(Context c, List<Record> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Record r = list.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(r.time).append(';')
                    .append(r.difficulty).append(';')
                    .append(r.elapsedMs).append(';')
                    .append(clean(r.title)).append(';')
                    .append(clean(r.subtitle)).append(';');
            for (int k = 0; k < r.moves.length; k++) {
                if (k > 0) {
                    sb.append(',');
                }
                sb.append(r.moves[k]);
            }
        }
        prefs(c).edit().putString(K_LIST, sb.toString()).apply();
    }

    private static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('\n', ' ').replace(';', ' ');
    }

    private static int[] parseMoves(String s) {
        if (s == null || s.isEmpty()) {
            return new int[0];
        }
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        int n = 0;
        for (String p : parts) {
            try {
                out[n++] = Integer.parseInt(p.trim());
            } catch (NumberFormatException e) {
                break;
            }
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
