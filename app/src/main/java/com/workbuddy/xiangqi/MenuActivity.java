package com.workbuddy.xiangqi;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.workbuddy.xiangqi.ui.Theme;

/** 主菜单：继续对局 / 选择难度开新局。 */
public class MenuActivity extends Activity {

    private static final int PANEL = 0xCC1E1308;
    private static final int PANEL_STROKE = 0x33E9C07A;
    private static final int TEXT_MAIN = Theme.TEXT;
    private static final int TEXT_DIM = Theme.TEXT_DIM;
    private static final int GOLD = Theme.GOLD;

    private static final String[] DIFF_NAME = {"简单", "普通", "困难"};
    private static final String[] DIFF_DESC = {"轻松上手", "旗鼓相当", "全力应战"};
    private static final int[] DIFF_DEPTH = {3, 6, 10};
    private static final int[] DIFF_TIME = {400, 1800, 5000};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersive();
        setContentView(build());
    }

    private void applyImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersive();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setContentView(build());
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Theme.tableBackground(this));
        root.setPadding(dp(28), 0, dp(28), dp(24));

        View topSpace = new View(this);
        root.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 0.72f));

        TextView title = new TextView(this);
        title.setText("中国象棋");
        title.setTextColor(GOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f);
        title.setTypeface(Theme.serifBold());
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.12f);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("CHINESE  CHESS");
        sub.setTextColor(TEXT_DIM);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        sub.setLetterSpacing(0.35f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(30));
        root.addView(sub);

        if (GameStore.hasSave(this)) {
            GameStore.Saved saved = GameStore.load(this);
            boolean finished = saved.finished;
            LinearLayout resume = card();
            resume.setOrientation(LinearLayout.HORIZONTAL);
            resume.setGravity(Gravity.CENTER_VERTICAL);
            resume.setPadding(dp(20), dp(16), dp(18), dp(16));
            resume.setBackground(rounded(0xE62A1707, GOLD, 14f));

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(this);
            t1.setText(finished ? "查看上局结果" : "继续对局");
            t1.setTextColor(TEXT_MAIN);
            t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
            t1.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            texts.addView(t1);

            TextView t2 = new TextView(this);
            String detail = finished
                    ? (saved.resultTitle.isEmpty() ? "对局已结束" : saved.resultTitle)
                    : GameStore.summary(this);
            t2.setText(detail);
            t2.setTextColor(TEXT_DIM);
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            t2.setPadding(0, dp(3), 0, 0);
            texts.addView(t2);

            resume.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(GOLD);
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
            resume.addView(arrow);

            resume.setOnClickListener(v -> {
                Intent i = new Intent(this, MainActivity.class);
                i.putExtra(MainActivity.EXTRA_RESUME, true);
                startActivity(i);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(26);
            root.addView(resume, lp);
        }

        TextView label = new TextView(this);
        label.setText("新对局");
        label.setTextColor(TEXT_DIM);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        label.setLetterSpacing(0.12f);
        label.setPadding(dp(2), 0, 0, dp(10));
        root.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout item = card();
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(18), 0, dp(18));

            TextView name = new TextView(this);
            name.setText(DIFF_NAME[i]);
            name.setTextColor(TEXT_MAIN);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            name.setGravity(Gravity.CENTER);
            item.addView(name);

            TextView desc = new TextView(this);
            desc.setText(DIFF_DESC[i]);
            desc.setTextColor(TEXT_DIM);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            desc.setGravity(Gravity.CENTER);
            desc.setPadding(0, dp(5), 0, 0);
            item.addView(desc);

            TextView meta = new TextView(this);
            meta.setText("深度 " + DIFF_DEPTH[i] + " · " + (DIFF_TIME[i] / 1000f) + "s");
            meta.setTextColor(0xFF8B7355);
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            meta.setGravity(Gravity.CENTER);
            meta.setPadding(0, dp(8), 0, 0);
            item.addView(meta);

            item.setOnClickListener(v -> startGame(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = i < 2 ? dp(10) : 0;
            row.addView(item, lp);
        }
        root.addView(row);

        addHistory(root);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView version = new TextView(this);
        version.setText("v1.2.0 · 实木贴图 · 自研 Alpha-Beta 引擎");
        version.setTextColor(0xFF7A6446);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, 0, 0, dp(10));
        root.addView(version);

        TextView tip = new TextView(this);
        tip.setText("点击棋子选中，再点目标落子 · 对局自动保存");
        tip.setTextColor(0xFF8B7355);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        return root;
    }

    /** 历史对局：本地保留已结束的棋局，可点开复盘。 */
    private void addHistory(LinearLayout root) {
        final java.util.List<GameArchive.Record> history = GameArchive.load(this);
        if (history.isEmpty()) {
            return;
        }

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(2), dp(20), 0, dp(8));

        TextView label = new TextView(this);
        label.setText("历史对局");
        label.setTextColor(TEXT_DIM);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        label.setLetterSpacing(0.12f);
        head.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(this);
        count.setText(history.size() + " 局 · 可复盘");
        count.setTextColor(0xFF7A6446);
        count.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        head.addView(count);
        root.addView(head);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < history.size(); i++) {
            final int index = i;
            final GameArchive.Record r = history.get(i);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setBackground(rounded(PANEL, PANEL_STROKE, 12f));
            item.setPadding(dp(12), dp(9), dp(10), dp(9));
            item.setClickable(true);

            TextView tag = new TextView(this);
            boolean win = r.title.contains("红方胜");
            boolean lose = r.title.contains("黑方胜");
            tag.setText(win ? "胜" : lose ? "负" : "和");
            tag.setTextColor(win ? 0xFF2A1A0C : Theme.TEXT);
            tag.setGravity(Gravity.CENTER);
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            tag.setTypeface(Theme.serifBold());
            tag.setBackground(rounded(win ? Theme.GOLD : (lose ? 0x33FFFFFF : 0x33E9C07A),
                    0x00000000, 16f));
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(dp(30), dp(30));
            tagLp.rightMargin = dp(10);
            item.addView(tag, tagLp);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(this);
            t1.setText((r.title.isEmpty() ? "对局结束" : r.title)
                    + " · " + (r.subtitle.isEmpty() ? "" : r.subtitle));
            t1.setTextColor(TEXT_MAIN);
            t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            t1.setSingleLine(true);
            texts.addView(t1);
            TextView t2 = new TextView(this);
            t2.setText(String.format("%s · %d 手 · %s",
                    DIFF_NAME[Math.max(0, Math.min(2, r.difficulty))],
                    r.moves.length, ago(r.time)));
            t2.setTextColor(0xFF8B7355);
            t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
            t2.setPadding(0, dp(2), 0, 0);
            texts.addView(t2);
            item.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(Theme.GOLD);
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            item.addView(arrow);

            item.setOnClickListener(v -> {
                Intent it = new Intent(this, MainActivity.class);
                it.putExtra(MainActivity.EXTRA_ARCHIVE, index);
                startActivity(it);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            list.addView(item, lp);
        }

        // 最多显示 3 行，超过则区域滚动
        int rows = Math.min(history.size(), 3);
        root.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54) * rows));
    }

    private static String ago(long time) {
        if (time <= 0) {
            return "刚刚";
        }
        long diff = System.currentTimeMillis() - time;
        if (diff < 60_000L) {
            return "刚刚";
        }
        if (diff < 3_600_000L) {
            return (diff / 60_000L) + " 分钟前";
        }
        if (diff < 86_400_000L) {
            return (diff / 3_600_000L) + " 小时前";
        }
        return (diff / 86_400_000L) + " 天前";
    }

    private void startGame(int difficulty) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_DIFFICULTY, difficulty);
        i.putExtra(MainActivity.EXTRA_RESUME, false);
        startActivity(i);
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(rounded(PANEL, PANEL_STROKE, 14f));
        v.setClickable(true);
        return v;
    }

    private GradientDrawable rounded(int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1f), stroke);
        return d;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
