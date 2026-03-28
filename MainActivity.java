package com.dvlo.vndbapp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private EditText     etSearch;
    private TextView     btnSearch;
    private ProgressBar  progressBar;
    private LinearLayout resultContainer;
    private ScrollView   scrollView;
    private TextView     tvEmpty;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private static final String API_VN = "https://api.vndb.org/kana/vn";
    private static final String SEARCH_FIELDS =
	"id,title,alttitle,released,rating,votecount,devstatus,languages,platforms,image";

    // 用于保存状态
    private static final String KEY_QUERY = "query";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化图片加载器（确保磁盘缓存目录存在）
        ImageLoader.get().init(this);

        buildUI();

        // 恢复搜索框内容（如果有保存）
        if (savedInstanceState != null) {
            String savedQuery = savedInstanceState.getString(KEY_QUERY);
            if (savedQuery != null && !savedQuery.isEmpty()) {
                etSearch.setText(savedQuery);
                // 可选：自动重新搜索，让用户决定
                // 如需自动搜索，取消下面注释
                // triggerSearch();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        String query = etSearch.getText().toString().trim();
        if (!query.isEmpty()) {
            outState.putString(KEY_QUERY, query);
        }
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.bg(this));

        // ── Top App Bar ─────────────────────────────
        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setBackgroundColor(Theme.surface(this));
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(4), dp(0), dp(4), dp(0));
        appBar.setMinimumHeight(dp(64));

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.CENTER);
        titleBlock.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("VNDB");
        tvTitle.setTextColor(Theme.onSurface(this));
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        titleBlock.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Visual Novel Database");
        tvSubtitle.setTextColor(Theme.onSurfaceVar(this));
        tvSubtitle.setTextSize(11f);
        tvSubtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stP.topMargin = dp(1);
        tvSubtitle.setLayoutParams(stP);
        titleBlock.addView(tvSubtitle);

        // 设置按钮
        TextView btnSettings = new TextView(this);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(22f);
        btnSettings.setTextColor(Theme.onSurface(this));
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        appBar.addView(spacer);
        appBar.addView(titleBlock);
        appBar.addView(btnSettings);
        root.addView(appBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        // AppBar 底部分隔线
        View divider = new View(this);
        divider.setBackgroundColor(Theme.outlineVar(this));
        root.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // ── 搜索条 ─────────────────────────────
        LinearLayout searchOuter = new LinearLayout(this);
        searchOuter.setOrientation(LinearLayout.VERTICAL);
        searchOuter.setBackgroundColor(Theme.surface(this));
        searchOuter.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackgroundColor(Theme.container(this));
        searchRow.setMinimumHeight(dp(56));
        searchRow.setPadding(dp(4), dp(0), dp(4), dp(0));

        // 搜索图标
        TextView ivSearchIcon = new TextView(this);
        ivSearchIcon.setText("🔍");
        ivSearchIcon.setTextSize(18f);
        ivSearchIcon.setGravity(Gravity.CENTER);
        ivSearchIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(56)));

        // 输入框
        etSearch = new EditText(this);
        etSearch.setHint("搜索游戏名称或 ID（如 v17）");
        etSearch.setHintTextColor(Theme.onSurfaceVar(this));
        etSearch.setTextColor(Theme.onSurface(this));
        etSearch.setTextSize(16f);
        etSearch.setBackgroundColor(0x00000000);
        etSearch.setPadding(0, dp(4), dp(4), dp(4));
        etSearch.setSingleLine(true);
        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        etSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                triggerSearch();
                return true;
            }
            return false;
        });

        // 搜索按钮
        btnSearch = new TextView(this);
        btnSearch.setText("搜索");
        btnSearch.setTextColor(Theme.onPrimary(this));
        btnSearch.setTextSize(14f);
        btnSearch.setTypeface(null, Typeface.BOLD);
        btnSearch.setBackgroundColor(Theme.primary(this));
        btnSearch.setPadding(dp(20), dp(0), dp(20), dp(0));
        btnSearch.setGravity(Gravity.CENTER);
        btnSearch.setMinimumHeight(dp(40));
        LinearLayout.LayoutParams sbP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        sbP.rightMargin = dp(6);
        btnSearch.setLayoutParams(sbP);
        btnSearch.setOnClickListener(v -> {
            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70)
				.withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
			.start();
            triggerSearch();
        });

        searchRow.addView(ivSearchIcon);
        searchRow.addView(etSearch);
        searchRow.addView(btnSearch);
        searchOuter.addView(searchRow);
        root.addView(searchOuter);

        // 搜索条底部分隔线
        View searchDiv = new View(this);
        searchDiv.setBackgroundColor(Theme.outlineVar(this));
        root.addView(searchDiv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // ── 进度条 ─────────────────────────────
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));

        // ── 空状态 ─────────────────────────────
        tvEmpty = new TextView(this);
        tvEmpty.setVisibility(View.GONE);
        tvEmpty.setTextColor(Theme.onSurfaceVar(this));
        tvEmpty.setTextSize(14f);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(dp(32), dp(80), dp(32), dp(24));
        root.addView(tvEmpty);

        // ── 搜索结果列表 ─────────────────────────────
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Theme.bg(this));
        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setPadding(dp(16), dp(8), dp(16), dp(32));
        scrollView.addView(resultContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void triggerSearch() {
        final String q = etSearch.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "请输入游戏名称或 ID（如 v17）", Toast.LENGTH_SHORT).show();
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        searchVN(q);
    }

    private void searchVN(final String query) {
        progressBar.setVisibility(View.VISIBLE);
        btnSearch.setEnabled(false);
        resultContainer.removeAllViews();
        tvEmpty.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                boolean isId = query.matches("(?i)v\\d+");
                JSONArray filter = new JSONArray();
                filter.put(isId ? "id" : "search");
                filter.put("=");
                filter.put(isId ? query.toLowerCase() : query);

                JSONObject body = new JSONObject();
                body.put("filters", filter);
                body.put("fields", SEARCH_FIELDS);
                body.put("sort", "rating");
                body.put("reverse", true);
                body.put("results", 15);

                final String resp = postJson(API_VN, body.toString());
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSearch.setEnabled(true);
                    handleResult(resp);
                });
            } catch (final Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSearch.setEnabled(true);
                    showEmpty("网络错误: " + e.getMessage());
                });
            }
        });
    }

    private void handleResult(String body) {
        if (body == null) {
            showEmpty("请求失败");
            return;
        }
        try {
            JSONObject json = new JSONObject(body);
            if (json.has("message")) {
                showEmpty("API: " + json.optString("message"));
                return;
            }
            JSONArray results = json.optJSONArray("results");
            if (results == null || results.length() == 0) {
                showEmpty("未找到相关视觉小说");
                return;
            }
            for (int i = 0; i < results.length(); i++) {
                addCard(results.getJSONObject(i), i);
            }
            scrollView.post(() -> scrollView.smoothScrollTo(0, 0));
        } catch (Exception e) {
            showEmpty("解析错误: " + e.getMessage());
        }
    }

    private void addCard(final JSONObject vn, final int idx) {
        try {
            final String id = vn.optString("id", "");
            final String title = vn.optString("title", "");
            String alt = vn.optString("alttitle", "");
            String cover = vn.optString("image", "");
            double rating = vn.optDouble("rating", 0);
            int devstatus = vn.optInt("devstatus", 0);

            final String displayTitle = DeveloperActivity.chooseChinese(title, alt);

            // 状态（备用，当前未在卡片中显示）
            // 评分颜色
            int ratingColor = Theme.ratingColor(this, rating);

            // ── Card ─────────────────────────────
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setBackgroundColor(Theme.containerHigh(this));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.bottomMargin = dp(8);
            card.setLayoutParams(cp);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));

            // 左侧封面
            ImageView ivCover = new ImageView(this);
            LinearLayout.LayoutParams imgP = new LinearLayout.LayoutParams(dp(64), dp(90));
            imgP.rightMargin = dp(12);
            ivCover.setLayoutParams(imgP);
            ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (!cover.isEmpty()) {
                ImageLoader.get().load(cover, ivCover, Theme.container(this));
            } else {
                ivCover.setBackgroundColor(Theme.container(this));
            }
            card.addView(ivCover);

            // 右侧文字
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvTitle = new TextView(this);
            tvTitle.setText(displayTitle);
            tvTitle.setTextColor(Theme.onSurface(this));
            tvTitle.setTextSize(16f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvTitle);

            if (!alt.isEmpty() && !alt.equals(title)) {
                TextView tvAlt = new TextView(this);
                tvAlt.setText(alt);
                tvAlt.setTextColor(Theme.onSurfaceVar(this));
                tvAlt.setTextSize(12f);
                textCol.addView(tvAlt);
            }

            TextView tvRating = new TextView(this);
            tvRating.setText(rating > 0 ? String.format("%.1f", rating / 10.0) : "—");
            tvRating.setTextColor(ratingColor);
            tvRating.setTextSize(14f);
            textCol.addView(tvRating);

            card.addView(textCol);

            final String finalId = id;
            final String finalTitle = displayTitle;
            card.setOnClickListener(v -> {
                Intent it = new Intent(MainActivity.this, DetailActivity.class);
                it.putExtra("vn_id", finalId);
                it.putExtra("vn_title", finalTitle);
                startActivity(it);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            });

            resultContainer.addView(card);

            // 动画
            card.setAlpha(0f);
            card.setTranslationY(dp(16));
            card.animate()
				.alpha(1f)
				.translationY(0f)
				.setDuration(220)
				.setStartDelay(idx * 40L)
				.setInterpolator(new android.view.animation.DecelerateInterpolator())
				.start();

        } catch (Exception ignored) {
        }
    }

    private void showEmpty(String msg) {
        resultContainer.removeAllViews();
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(msg);
    }

    // ================================================================
    //  分享 VN
    // ================================================================
    static void shareVN(Context ctx, String id, String title, String desc) {
        String text = title + "\nhttps://vndb.org/" + id;  // 修复为完整 URL
        if (desc != null && !desc.isEmpty()) {
            String shortDesc = desc.length() > 120 ? desc.substring(0, 120) + "…" : desc;
            text += "\n\n" + shortDesc;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        ctx.startActivity(Intent.createChooser(intent, "分享"));
    }

    // ================================================================
    //  BBCode 清理
    // ================================================================
    static String cleanBBCode(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
			.replaceAll("\\[spoiler\\].*?\\[/spoiler\\]", "[剧透内容]")
			.replaceAll("\\[url=[^\\]]*\\]([^\\[]*)\\[/url\\]", "$1")
			.replaceAll("\\[url\\]([^\\[]*)\\[/url\\]", "$1")
			.replaceAll("\\[b\\]|\\[/b\\]", "")
			.replaceAll("\\[i\\]|\\[/i\\]", "")
			.replaceAll("\\[u\\]|\\[/u\\]", "")
			.replaceAll("\\[s\\]|\\[/s\\]", "")
			.replaceAll("\\[code\\]|\\[/code\\]", "")
			.replaceAll("\\[quote\\]|\\[/quote\\]", "")
			.replaceAll("\\[img\\][^\\[]*\\[/img\\]", "[图片]")
			.replaceAll("\\[[^\\]]*\\]", "")
			.trim();
    }

    // ================================================================
    //  语言代码 → 简短标签
    // ================================================================
    static String mapLang(String code) {
        if (code == null) return "";
        switch (code) {
            case "zh-Hans": case "zh-hans": return "简中";
            case "zh-Hant": case "zh-hant": return "繁中";
            case "zh":      return "中文";
            case "ja":      return "日语";
            case "en":      return "英语";
            case "ko":      return "韩语";
            case "fr":      return "法语";
            case "de":      return "德语";
            case "es":      return "西语";
            case "it":      return "意语";
            case "ru":      return "俄语";
            case "pt-br":   return "葡(巴)";
            case "pt-pt":   return "葡语";
            case "nl":      return "荷语";
            case "pl":      return "波语";
            case "cs":      return "捷语";
            case "hu":      return "匈语";
            case "ro":      return "罗语";
            case "sk":      return "斯洛";
            case "tr":      return "土语";
            case "uk":      return "乌语";
            case "vi":      return "越语";
            case "id":      return "印尼";
            case "th":      return "泰语";
            case "ar":      return "阿语";
            case "he":      return "希伯";
            case "fi":      return "芬语";
            case "sv":      return "瑞语";
            case "no":      return "挪语";
            case "da":      return "丹语";
            default:        return code.toUpperCase();
        }
    }

    // ================================================================
    //  语言代码 → 完整名称
    // ================================================================
    static String mapLangFull(String code) {
        if (code == null) return "";
        switch (code) {
            case "zh-Hans": case "zh-hans": return "中文（简体）";
            case "zh-Hant": case "zh-hant": return "中文（繁体）";
            case "zh":      return "中文";
            case "ja":      return "日语";
            case "en":      return "英语";
            case "ko":      return "韩语";
            case "fr":      return "法语";
            case "de":      return "德语";
            case "es":      return "西班牙语";
            case "it":      return "意大利语";
            case "ru":      return "俄语";
            case "pt-br":   return "葡萄牙语（巴西）";
            case "pt-pt":   return "葡萄牙语";
            case "nl":      return "荷兰语";
            case "pl":      return "波兰语";
            case "cs":      return "捷克语";
            case "hu":      return "匈牙利语";
            case "ro":      return "罗马尼亚语";
            case "sk":      return "斯洛伐克语";
            case "tr":      return "土耳其语";
            case "uk":      return "乌克兰语";
            case "vi":      return "越南语";
            case "id":      return "印度尼西亚语";
            case "th":      return "泰语";
            case "ar":      return "阿拉伯语";
            case "he":      return "希伯来语";
            case "fi":      return "芬兰语";
            case "sv":      return "瑞典语";
            case "no":      return "挪威语";
            case "da":      return "丹麦语";
            case "hr":      return "克罗地亚语";
            case "bg":      return "保加利亚语";
            case "ca":      return "加泰罗尼亚语";
            case "el":      return "希腊语";
            case "eo":      return "世界语";
            case "fa":      return "波斯语";
            case "ga":      return "爱尔兰语";
            case "gd":      return "苏格兰盖尔语";
            case "hi":      return "印地语";
            case "la":      return "拉丁语";
            case "ms":      return "马来语";
            case "ta":      return "泰米尔语";
            default:        return code;
        }
    }

    // ================================================================
    //  平台代码 → 显示名称
    // ================================================================
    static String mapPlatform(String code) {
        if (code == null) return "";
        switch (code) {
            case "win":   return "Windows";
            case "lin":   return "Linux";
            case "mac":   return "macOS";
            case "web":   return "网页";
            case "tdo":   return "3DO";
            case "ios":   return "iOS";
            case "and":   return "Android";
            case "dvd":   return "DVD Player";
            case "drc":   return "Dreamcast";
            case "nes":   return "NES";
            case "sfc":   return "Super Famicom";
            case "p88":   return "PC-88";
            case "p98":   return "PC-98";
            case "pce":   return "PC Engine";
            case "pcf":   return "PC-FX";
            case "psp":   return "PSP";
            case "ps1":   return "PS1";
            case "ps2":   return "PS2";
            case "ps3":   return "PS3";
            case "ps4":   return "PS4";
            case "ps5":   return "PS5";
            case "psv":   return "PS Vita";
            case "gba":   return "GBA";
            case "nds":   return "NDS";
            case "n3d":   return "3DS";
            case "swi":   return "Switch";
            case "wii":   return "Wii";
            case "wiu":   return "Wii U";
            case "xb1":   return "Xbox";
            case "xb3":   return "Xbox 360";
            case "xbo":   return "Xbox One";
            case "xxs":   return "Xbox Series";
            case "sat":   return "Sega Saturn";
            case "smd":   return "Mega Drive";
            case "mob":   return "手机";
            case "oth":   return "其他";
            default:      return code.toUpperCase();
        }
    }

    // ================================================================
    //  HTTP POST（无认证，用于主搜索）
    // ================================================================
    static String postJson(String urlStr, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(18000);
            conn.setDoOutput(true);
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

            int code = conn.getResponseCode();
            InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return "{\"message\":\"HTTP " + code + "\"}";
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        ImageLoader.get().shutdown(); // 释放图片加载线程
    }
}
