package com.dvlo.vndbapp;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CharacterActivity extends Activity {

    private LinearLayout charList;
    private ProgressBar  progress;
    private TextView     tvError;
    private FrameLayout  detailOverlay;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    static final String API_CHAR   = "https://api.vndb.org/kana/character";
    static final String VNDB_TOKEN = "19co-bcqti-6zom4-hqu8-i1h7m-peg9n-3iep";

    private static final String CHAR_FIELDS =
        "id,name,original,aliases,description,blood_type,height,weight,bust,waist,hips," +
        "cup_size,age,sex,image.url,image.sexual,image.violence," +
        "vns.id,vns.title,vns.role," +
        "traits.name,traits.group_name,traits.spoiler";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String vnId    = getIntent().getStringExtra("vn_id");
        final String vnTitle = getIntent().getStringExtra("vn_title");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.bg(this));

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.addView(makeToolbar(vnTitle != null ? vnTitle + " — 角色" : "角色"));

        View gline = new View(this);
        gline.setBackgroundColor(Theme.outlineVar(this));
        gline.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        mainLayout.addView(gline);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        mainLayout.addView(progress, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        tvError = new TextView(this);
        tvError.setTextColor(Theme.error(this));
        tvError.setTextSize(14f);
        tvError.setPadding(dp(20), dp(20), dp(20), dp(20));
        tvError.setVisibility(View.GONE);
        mainLayout.addView(tvError);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        charList = new LinearLayout(this);
        charList.setOrientation(LinearLayout.VERTICAL);
        charList.setPadding(0, dp(4), 0, dp(48));
        sv.addView(charList);
        mainLayout.addView(sv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(mainLayout, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        detailOverlay = new FrameLayout(this);
        detailOverlay.setBackgroundColor(0xCC000000);
        detailOverlay.setVisibility(View.GONE);
        detailOverlay.setAlpha(0f);
        root.addView(detailOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        detailOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDetail(); }
        });

        setContentView(root);
        if (vnId != null) loadChars(vnId);
    }

    private void loadChars(final String vnId) {
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    // ✅ 正确格式: ["vn","=",["id","=","v17"]]
                    JSONArray idFilter = new JSONArray();
                    idFilter.put("id");
                    idFilter.put("=");
                    idFilter.put(vnId);

                    JSONArray filter = new JSONArray();
                    filter.put("vn");
                    filter.put("=");
                    filter.put(idFilter);

                    JSONObject body = new JSONObject();
                    body.put("filters", filter);
                    body.put("fields", CHAR_FIELDS);
                    body.put("results", 25);
                    body.put("sort", "name");

                    final String resp = postWithAuth(API_CHAR, body.toString());
                    main.post(new Runnable() {
                        @Override public void run() { handleChars(resp); }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { showError("网络错误: " + e.getMessage()); }
                    });
                }
            }
        });
    }

    private void handleChars(String body) {
        progress.setVisibility(View.GONE);
        if (body == null) { showError("请求失败"); return; }
        try {
            JSONObject json = new JSONObject(body);
            if (json.has("message")) { showError("API: " + json.optString("message")); return; }
            JSONArray results = json.optJSONArray("results");
            if (results == null || results.length() == 0) {
                showError("该作品暂无角色信息"); return;
            }
            for (int i = 0; i < results.length(); i++) addCharCard(results.getJSONObject(i), i);
        } catch (Exception e) { showError("解析错误: " + e.getMessage()); }
    }

    private void addCharCard(final JSONObject ch, final int idx) {
        try {
            String name     = ch.optString("name", "");
            String original = ch.optString("original", "");
            String sex      = ch.optString("sex", "");
            JSONArray vnsArr = ch.optJSONArray("vns");
            String role = (vnsArr != null && vnsArr.length() > 0)
                ? vnsArr.getJSONObject(0).optString("role", "") : "";

            JSONObject imgObj = ch.optJSONObject("image");
            String imgUrl     = imgObj != null ? imgObj.optString("url", "") : "";
            boolean imgNsfw   = imgObj != null &&
                (imgObj.optDouble("sexual", 0) >= 1.0 || imgObj.optDouble("violence", 0) >= 1.0);

            int barColor = sex.contains("f") ? 0xFFE91E63
                : sex.contains("m") ? 0xFF2196F3 : Theme.outline(this);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundColor(Theme.surface(this));
            card.setPadding(0, dp(12), dp(14), dp(12));
            card.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMargins(dp(12), dp(4), dp(12), dp(4));
            card.setLayoutParams(cp);

            View bar = new View(this);
            bar.setBackgroundColor(barColor);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                dp(4), LinearLayout.LayoutParams.MATCH_PARENT);
            bp.setMargins(0, 0, dp(12), 0);
            bar.setLayoutParams(bp);
            card.addView(bar);

            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(Theme.container(this));
            LinearLayout.LayoutParams ivp = new LinearLayout.LayoutParams(dp(52), dp(52));
            ivp.setMargins(0, 0, dp(12), 0);
            iv.setLayoutParams(ivp);
            if (!imgUrl.isEmpty() && !imgNsfw)
                ImageLoader.get().load(imgUrl, iv, Theme.container(this));
            card.addView(iv);

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            String displayName = original.isEmpty() ? name : original;
            String subName     = original.isEmpty() ? "" : name;

            TextView tvName = new TextView(this);
            tvName.setText(displayName);
            tvName.setTextColor(Theme.onSurface(this));
            tvName.setTextSize(14f); tvName.setTypeface(null, Typeface.BOLD);
            txt.addView(tvName);

            if (!subName.isEmpty()) {
                TextView tvSub = new TextView(this);
                tvSub.setText(subName);
                tvSub.setTextColor(Theme.onSurfaceVar(this));
                tvSub.setTextSize(11.5f);
                txt.addView(tvSub);
            }

            String meta = mapRole(role);
            String sexStr = mapSex(sex);
            if (!sexStr.isEmpty()) meta += (meta.isEmpty() ? "" : "  ") + sexStr;
            if (!meta.trim().isEmpty()) {
                TextView tvRole = new TextView(this);
                tvRole.setText(meta.trim());
                tvRole.setTextColor(barColor);
                tvRole.setTextSize(11f);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.setMargins(0, dp(3), 0, 0);
                tvRole.setLayoutParams(rp);
                txt.addView(tvRole);
            }
            card.addView(txt);

            TextView tvArrow = new TextView(this);
            tvArrow.setText("›");
            tvArrow.setTextColor(Theme.outline(this));
            tvArrow.setTextSize(20f);
            tvArrow.setPadding(dp(8), 0, 0, 0);
            card.addView(tvArrow);

            final JSONObject chFinal = ch;
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { openDetail(chFinal); }
            });

            charList.addView(card);
            card.setAlpha(0f); card.setTranslationX(dp(24));
            card.animate().alpha(1f).translationX(0f)
                .setDuration(260).setStartDelay(idx * 50L)
                .setInterpolator(new DecelerateInterpolator()).start();

        } catch (Exception ignored) {}
    }

    private void openDetail(JSONObject ch) {
        try {
            String name      = ch.optString("name", "");
            String original  = ch.optString("original", "");
            String desc      = ch.optString("description", "");
            String bloodType = ch.optString("blood_type", "");
            int    height    = ch.optInt("height", 0);
            int    weight    = ch.optInt("weight", 0);
            int    bust      = ch.optInt("bust", 0);
            int    waist     = ch.optInt("waist", 0);
            int    hips      = ch.optInt("hips", 0);
            String cup       = ch.optString("cup_size", "");
            String sex       = ch.optString("sex", "");
            int    age       = ch.optInt("age", 0);
            JSONArray traits = ch.optJSONArray("traits");
            JSONObject imgObj= ch.optJSONObject("image");
            String imgUrl    = imgObj != null ? imgObj.optString("url", "") : "";
            boolean imgNsfw  = imgObj != null &&
                (imgObj.optDouble("sexual", 0) >= 1.0 || imgObj.optDouble("violence", 0) >= 1.0);

            detailOverlay.removeAllViews();
            ScrollView sv = new ScrollView(this);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Theme.surface(this));
            card.setPadding(dp(20), dp(22), dp(20), dp(28));

            // 顶部色条
            View gl = new View(this);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(36), dp(3));
            glp.setMargins(0, 0, 0, dp(12)); gl.setLayoutParams(glp);
            gl.setBackgroundColor(Theme.primary(this));
            card.addView(gl);

            // 头像 + 姓名
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            trp.setMargins(0, 0, 0, dp(16)); topRow.setLayoutParams(trp);

            if (!imgUrl.isEmpty() && !imgNsfw) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundColor(Theme.container(this));
                LinearLayout.LayoutParams ivp = new LinearLayout.LayoutParams(dp(72), dp(72));
                ivp.setMargins(0, 0, dp(14), 0);
                iv.setLayoutParams(ivp);
                ImageLoader.get().load(imgUrl, iv, Theme.container(this));
                topRow.addView(iv);
            }

            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            nameCol.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            String displayName = original.isEmpty() ? name : original;
            String subName     = original.isEmpty() ? "" : name;

            TextView tvName = new TextView(this);
            tvName.setText(displayName);
            tvName.setTextColor(Theme.onSurface(this));
            tvName.setTextSize(18f); tvName.setTypeface(null, Typeface.BOLD);
            nameCol.addView(tvName);

            if (!subName.isEmpty()) {
                TextView tvSub = new TextView(this);
                tvSub.setText(subName);
                tvSub.setTextColor(Theme.onSurfaceVar(this));
                tvSub.setTextSize(12f);
                nameCol.addView(tvSub);
            }

            int barColor = sex.contains("f") ? 0xFFE91E63
                : sex.contains("m") ? 0xFF2196F3 : Theme.outline(this);
            TextView tvSex = new TextView(this);
            tvSex.setText(mapSex(sex));
            tvSex.setTextColor(barColor); tvSex.setTextSize(12f);
            nameCol.addView(tvSex);
            topRow.addView(nameCol);
            card.addView(topRow);

            // 数据
            if (age > 0 || height > 0 || weight > 0 || !bloodType.isEmpty()
                    || bust > 0 || waist > 0 || hips > 0) {
                addSection(card, "基本资料");
                if (age > 0)    addRow(card, "年龄", age + " 岁");
                if (height > 0) addRow(card, "身高", height + " cm");
                if (weight > 0) addRow(card, "体重", weight + " kg");
                if (!bloodType.isEmpty()) addRow(card, "血型", bloodType.toUpperCase());
                if (bust > 0 || waist > 0 || hips > 0) {
                    String bwh = (bust > 0 ? String.valueOf(bust) : "?") + " / "
                        + (waist > 0 ? String.valueOf(waist) : "?") + " / "
                        + (hips > 0 ? String.valueOf(hips) : "?");
                    if (!cup.isEmpty()) bwh += "  (" + cup.toUpperCase() + ")";
                    addRow(card, "三围", bwh);
                }
            }

            if (!desc.isEmpty()) {
                addSection(card, "简介");
                TextView tvDesc = new TextView(this);
                tvDesc.setText(MainActivity.cleanBBCode(desc));
                tvDesc.setTextColor(Theme.onSurfaceVar(this)); tvDesc.setTextSize(13f);
                LinearLayout.LayoutParams dp_ = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                dp_.setMargins(0, dp(4), 0, 0); tvDesc.setLayoutParams(dp_);
                card.addView(tvDesc);
            }

            if (traits != null && traits.length() > 0) {
                addSection(card, "特质");
                java.util.LinkedHashMap<String, StringBuilder> groups = new java.util.LinkedHashMap<>();
                for (int i = 0; i < traits.length(); i++) {
                    JSONObject t = traits.getJSONObject(i);
                    if (t.optInt("spoiler", 0) >= 2) continue;
                    String gname = t.optString("group_name", "其他");
                    String tname = t.optString("name", "");
                    if (!groups.containsKey(gname)) groups.put(gname, new StringBuilder());
                    groups.get(gname).append(tname).append("  ");
                }
                for (java.util.Map.Entry<String, StringBuilder> entry : groups.entrySet()) {
                    addRow(card, entry.getKey(), entry.getValue().toString().trim());
                }
            }

            View gap = new View(this);
            gap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));
            card.addView(gap);

            TextView tvClose = new TextView(this);
            tvClose.setText("关  闭");
            tvClose.setTextColor(Theme.onPrimary(this));
            tvClose.setTextSize(14f); tvClose.setTypeface(null, Typeface.BOLD);
            tvClose.setBackgroundColor(Theme.primary(this));
            tvClose.setGravity(Gravity.CENTER);
            tvClose.setPadding(dp(24), dp(12), dp(24), dp(12));
            tvClose.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            tvClose.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { closeDetail(); }
            });
            card.addView(tvClose);
            sv.addView(card);

            FrameLayout.LayoutParams svp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            svp.gravity = Gravity.BOTTOM;
            svp.setMargins(dp(12), dp(80), dp(12), dp(12));
            detailOverlay.addView(sv, svp);

            detailOverlay.setVisibility(View.VISIBLE);
            detailOverlay.animate().alpha(1f).setDuration(200).start();
            card.setTranslationY(dp(120)); card.setAlpha(0f);
            card.animate().translationY(0f).alpha(1f)
                .setDuration(340).setInterpolator(new OvershootInterpolator(0.7f)).start();

        } catch (Exception e) { showError("解析错误: " + e.getMessage()); }
    }

    private void closeDetail() {
        final View card = (detailOverlay.getChildCount() > 0
            && detailOverlay.getChildAt(0) instanceof ScrollView)
            ? ((ScrollView) detailOverlay.getChildAt(0)).getChildAt(0) : null;
        if (card != null)
            card.animate().translationY(dp(120)).alpha(0f).setDuration(200).start();
        detailOverlay.animate().alpha(0f).setDuration(240)
            .withEndAction(new Runnable() {
                @Override public void run() {
                    detailOverlay.setVisibility(View.GONE);
                    detailOverlay.removeAllViews();
                }
            }).start();
    }

    private void addSection(LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(14), 0, dp(4)); row.setLayoutParams(rp);
        View bar = new View(this); bar.setBackgroundColor(Theme.primary(this));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(3), dp(13));
        bp.setMargins(0, 0, dp(8), 0); bar.setLayoutParams(bp); row.addView(bar);
        TextView tv = new TextView(this); tv.setText(label);
        tv.setTextColor(Theme.primary(this)); tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        row.addView(tv); parent.addView(row);
    }

    private void addRow(LinearLayout parent, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(4), 0, 0); row.setLayoutParams(rp);
        TextView tvL = new TextView(this); tvL.setText(label);
        tvL.setTextColor(Theme.outline(this)); tvL.setTextSize(12f);
        tvL.setLayoutParams(new LinearLayout.LayoutParams(dp(72),
            LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView tvV = new TextView(this); tvV.setText(value);
        tvV.setTextColor(Theme.onSurfaceVar(this)); tvV.setTextSize(13f);
        tvV.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tvL); row.addView(tvV); parent.addView(row);
    }

    private LinearLayout makeToolbar(String title) {
        LinearLayout tb = new LinearLayout(this);
        tb.setOrientation(LinearLayout.HORIZONTAL);
        tb.setBackgroundColor(Theme.surface(this));
        tb.setPadding(0, dp(12), dp(16), dp(12));
        tb.setGravity(Gravity.CENTER_VERTICAL);
        TextView btnBack = new TextView(this);
        btnBack.setText("  ←  "); btnBack.setTextColor(Theme.primary(this));
        btnBack.setTextSize(20f); btnBack.setPadding(dp(12), dp(8), dp(8), dp(8));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        tb.addView(btnBack);
        TextView tvT = new TextView(this); tvT.setText(title != null ? title : "角色");
        tvT.setTextColor(Theme.onSurface(this)); tvT.setTextSize(16f);
        tvT.setTypeface(null, Typeface.BOLD); tvT.setMaxLines(1);
        tvT.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tb.addView(tvT);
        return tb;
    }

    private void showError(String msg) {
        progress.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    static String postWithAuth(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", "Token " + VNDB_TOKEN);
        conn.setConnectTimeout(12000); conn.setReadTimeout(18000); conn.setDoOutput(true);
        conn.getOutputStream().write(body.getBytes("UTF-8"));
        int code = conn.getResponseCode();
        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return "{\"message\":\"HTTP " + code + "\"}";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        conn.disconnect(); return sb.toString();
    }

    static String mapRole(String r) {
        switch (r) {
            case "main": return "主角"; case "primary": return "主要角色";
            case "side": return "配角"; case "appears": return "登场";
            default: return "";
        }
    }

    static String mapSex(String s) {
        if (s == null || s.isEmpty()) return "";
        switch (s) {
            case "f": return "女性"; case "m": return "男性";
            case "b": return "双性"; case "n": return "无性";
            default: return "";
        }
    }

    int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onBackPressed() {
        if (detailOverlay.getVisibility() == View.VISIBLE) closeDetail();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}

