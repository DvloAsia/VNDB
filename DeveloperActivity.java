package com.dvlo.vndbapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeveloperActivity extends Activity {

    // 颜色常量
    static final int C_BG       = 0xFF0D0D0D;
    static final int C_SURFACE  = 0xFF1A1A1A;
    static final int C_GOLD     = 0xFFD4AF37;
    static final int C_GOLD_DIM = 0x55D4AF37;
    static final int C_TEXT     = 0xFFEEEEEE;
    static final int C_TEXT_DIM = 0xFF888888;
    static final int C_RED      = 0xFFCC4444;

    private LinearLayout gameList;
    private ProgressBar  progress;
    private TextView     tvError;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final String API_VN = "https://api.vndb.org/kana/vn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String devId   = getIntent().getStringExtra("dev_id");
        final String devName = getIntent().getStringExtra("dev_name");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // 顶栏
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(C_SURFACE);
        toolbar.setPadding(0, dp(10), dp(16), dp(10));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("  <  "); btnBack.setTextColor(C_GOLD);
        btnBack.setTextSize(20f); btnBack.setPadding(dp(14), dp(8), dp(8), dp(8));
        btnBack.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});
        toolbar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText((devName != null ? devName : "开发商") + " 的作品");
        tvTitle.setTextColor(C_TEXT);
        tvTitle.setTextSize(14f); tvTitle.setTypeface(null, Typeface.BOLD); tvTitle.setMaxLines(1);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
															  LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        toolbar.addView(tvTitle);
        root.addView(toolbar);

        View gline = new View(this);
        gline.setBackgroundColor(C_GOLD_DIM);
        gline.setLayoutParams(new LinearLayout.LayoutParams(
								  LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        root.addView(gline);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(
						 LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        tvError = new TextView(this);
        tvError.setTextColor(0xFFCC4444); tvError.setTextSize(14f);
        tvError.setPadding(dp(20), dp(20), dp(20), dp(20)); tvError.setVisibility(View.GONE);
        root.addView(tvError);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        gameList = new LinearLayout(this);
        gameList.setOrientation(LinearLayout.VERTICAL);
        gameList.setPadding(0, dp(6), 0, dp(48));
        sv.addView(gameList);
        root.addView(sv, new LinearLayout.LayoutParams(
						 LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        if (devId != null) loadGames(devId);
    }

    private void loadGames(final String devId) {
        exec.execute(new Runnable() {
				@Override public void run() {
					try {
						JSONArray devFilter = new JSONArray();
						devFilter.put("id"); devFilter.put("="); devFilter.put(devId);

						JSONArray filter = new JSONArray();
						filter.put("developer"); filter.put("="); filter.put(devFilter);

						JSONObject body = new JSONObject();
						body.put("filters", filter);
						body.put("fields", "id,title,alttitle,released,rating,votecount,devstatus,languages,image.url,image.sexual,image.violence");
						body.put("sort", "released"); body.put("reverse", true); body.put("results", 50);

						final String resp = CharacterActivity.postWithAuth(API_VN, body.toString());
						main.post(new Runnable() {
								@Override public void run() { handleGames(resp); }
							});
					} catch (final Exception e) {
						main.post(new Runnable() {
								@Override public void run() { showError("网络错误: " + e.getMessage()); }
							});
					}
				}
			});
    }

    private void handleGames(String body) {
        progress.setVisibility(View.GONE);
        if (body == null) { showError("请求失败"); return; }
        try {
            JSONObject json = new JSONObject(body);
            if (json.has("message")) { showError("API: " + json.optString("message")); return; }
            JSONArray results = json.optJSONArray("results");
            if (results == null || results.length() == 0) { showError("暂无作品信息"); return; }

            TextView tvCount = new TextView(this);
            tvCount.setText("共 " + results.length() + " 部作品");
            tvCount.setTextColor(C_TEXT_DIM);
            tvCount.setTextSize(11f);
            tvCount.setPadding(dp(16), dp(8), dp(16), dp(4));
            gameList.addView(tvCount);

            for (int i = 0; i < results.length(); i++) addGameCard(results.getJSONObject(i), i);
        } catch (Exception e) { showError("解析错误: " + e.getMessage()); }
    }

    private void addGameCard(final JSONObject vn, final int idx) {
        try {
            final String id    = vn.optString("id", "");
            final String title = vn.optString("title", "");
            String alt         = vn.optString("alttitle", "");
            String released    = vn.optString("released", "");
            double rating      = vn.optDouble("rating", 0);
            int devstatus      = vn.optInt("devstatus", 0);

            final String displayTitle = chooseChinese(title, alt);
            String subTitle           = displayTitle.equals(title) ? alt : title;

            int barColor = rating >= 70 ? C_GOLD : C_RED;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundColor(C_SURFACE);
            card.setPadding(0, dp(11), dp(14), dp(11));
            card.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMargins(dp(12), dp(4), dp(12), dp(4));
            card.setLayoutParams(cp);

            View bar = new View(this);
            bar.setBackgroundColor(barColor);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                dp(3), LinearLayout.LayoutParams.MATCH_PARENT);
            bp.setMargins(0, 0, dp(12), 0);
            bar.setLayoutParams(bp);
            card.addView(bar);

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0,
															  LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvT = new TextView(this);
            tvT.setText(displayTitle); tvT.setTextColor(C_TEXT);
            tvT.setTextSize(14f); tvT.setTypeface(null, Typeface.BOLD);
            txt.addView(tvT);

            if (!subTitle.isEmpty() && !subTitle.equals(displayTitle)) {
                TextView tvS = new TextView(this);
                tvS.setText(subTitle); tvS.setTextColor(C_TEXT_DIM);
                tvS.setTextSize(11.5f); txt.addView(tvS);
            }

            if (!released.isEmpty()) {
                String statusStr = devstatus == 1 ? "  [开发中]" : devstatus == 2 ? "  [已取消]" : "";
                TextView tvR = new TextView(this);
                tvR.setText(released + statusStr);
                tvR.setTextColor(C_TEXT_DIM); tvR.setTextSize(11f);
                txt.addView(tvR);
            }
            card.addView(txt);

            if (rating > 0) {
                TextView tvRating = new TextView(this);
                tvRating.setText(String.format("%.1f", rating / 10.0));
                tvRating.setTextColor(barColor);
                tvRating.setTextSize(19f); tvRating.setTypeface(null, Typeface.BOLD);
                tvRating.setGravity(Gravity.CENTER);
                tvRating.setPadding(dp(10), 0, 0, 0);
                card.addView(tvRating);
            }

            card.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						Intent it = new Intent(DeveloperActivity.this, DetailActivity.class);
						it.putExtra("vn_id", id);
						it.putExtra("vn_title", displayTitle);
						startActivity(it);
						overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
					}
				});

            gameList.addView(card);
            card.setAlpha(0f); card.setTranslationX(dp(22));
            card.animate().alpha(1f).translationX(0f)
                .setDuration(230).setStartDelay(idx * 38L)
                .setInterpolator(new DecelerateInterpolator()).start();

        } catch (Exception ignored) {}
    }

    static String chooseChinese(String title, String alt) {
        if (isChinese(alt)) return alt;
        if (isChinese(title)) return title;
        return title;
    }

    static boolean isChinese(String s) {
        if (s == null || s.isEmpty()) return false;
        int count = 0;
        for (char c : s.toCharArray()) if (c >= 0x4E00 && c <= 0x9FFF) count++;
        return count >= 2;
    }

    private void showError(String msg) {
        progress.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}


