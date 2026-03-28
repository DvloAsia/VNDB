package com.dvlo.vndbapp;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
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

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetailActivity extends Activity {

    private LinearLayout content;
    private ProgressBar  progress;
    private TextView     tvError;
    private ImageView    ivCover;
    private String       currentId  = "";
    private String       currentTitle = "";
    private String       currentDesc  = "";

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final String API_VN  = "https://api.vndb.org/kana/vn";
    private static final String API_REL = "https://api.vndb.org/kana/release";

    private static final String FIELDS =
        "id,title,alttitle,titles.lang,titles.title,titles.latin,titles.official,titles.main," +
        "aliases,olang,devstatus,released,languages,platforms," +
        "image.url,image.sexual,image.violence," +
        "length,length_minutes,length_votes," +
        "description,rating,votecount,popularity," +
        "relations.id,relations.title,relations.relation,relations.relation_official," +
        "tags.id,tags.name,tags.rating,tags.spoiler,tags.lie," +
        "developers.id,developers.name,developers.original," +
        "staff.eid,staff.role,staff.name,staff.original," +
        "va.character.id,va.character.name,va.staff.id,va.staff.name,va.staff.original," +
        "screenshots.thumbnail,screenshots.url,screenshots.sexual,screenshots.violence";

    // ✅ 只用肯定存在的字段
    private static final String REL_FIELDS =
        "id,title,released,languages,platforms,minage,patch,freeware";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Theme.bg(this));
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 顶栏
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(Theme.surface(this));
        toolbar.setPadding(0, dp(12), dp(4), dp(12));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("  ←  "); btnBack.setTextColor(Theme.primary(this));
        btnBack.setTextSize(20f); btnBack.setPadding(dp(12), dp(8), dp(8), dp(8));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        toolbar.addView(btnBack);

        String vnTitle = getIntent().getStringExtra("vn_title");
        if (vnTitle == null) vnTitle = "详情";
        final TextView tvBar = new TextView(this);
        tvBar.setText(vnTitle); tvBar.setTextColor(Theme.onSurface(this));
        tvBar.setTextSize(15f); tvBar.setTypeface(null, Typeface.BOLD); tvBar.setMaxLines(1);
        tvBar.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        toolbar.addView(tvBar);

        // 分享按钮 — 点击调用系统分享面板
        TextView btnShare = new TextView(this);
        btnShare.setText("分享");
        btnShare.setTextColor(Theme.primary(this));
        btnShare.setTextSize(13f);
        btnShare.setPadding(dp(12), dp(10), dp(16), dp(10));
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                MainActivity.shareVN(DetailActivity.this, currentId, currentTitle, currentDesc);
            }
        });
        toolbar.addView(btnShare);
        root.addView(toolbar);

        root.addView(makeDivider(Theme.outlineVar(this), 1));

        // 封面
        ivCover = new ImageView(this);
        ivCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivCover.setBackgroundColor(Theme.container(this));
        ivCover.setVisibility(View.GONE);
        root.addView(ivCover, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(300)));

        // 进度
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        // 错误
        tvError = new TextView(this);
        tvError.setTextColor(Theme.error(this)); tvError.setTextSize(14f);
        tvError.setPadding(dp(20), dp(20), dp(20), dp(20));
        tvError.setVisibility(View.GONE);
        root.addView(tvError);

        // 内容
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(56));
        root.addView(content);

        sv.addView(root);
        setContentView(sv);

        // 入场动画
        root.setAlpha(0f); root.setTranslationY(dp(20));
        root.animate().alpha(1f).translationY(0f)
            .setDuration(300).setInterpolator(new DecelerateInterpolator(1.3f)).start();

        String vnId = getIntent().getStringExtra("vn_id");
        if (vnId != null) loadDetail(vnId);
    }

    private void loadDetail(final String id) {
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    JSONArray f = new JSONArray();
                    f.put("id"); f.put("="); f.put(id);
                    JSONObject body = new JSONObject();
                    body.put("filters", f);
                    body.put("fields", FIELDS);
                    body.put("results", 1);
                    final String resp = CharacterActivity.postWithAuth(API_VN, body.toString());
                    main.post(new Runnable() {
                        @Override public void run() { handleDetail(resp); }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { showError("网络错误: " + e.getMessage()); }
                    });
                }
            }
        });
    }

    private void handleDetail(String body) {
        progress.setVisibility(View.GONE);
        if (body == null) { showError("请求失败"); return; }
        try {
            JSONObject json = new JSONObject(body);
            if (json.has("message")) { showError("API: " + json.optString("message")); return; }
            JSONArray results = json.optJSONArray("results");
            if (results == null || results.length() == 0) { showError("找不到该作品"); return; }
            buildDetail(results.getJSONObject(0));
        } catch (Exception e) { showError("解析错误: " + e.getMessage()); }
    }

    private void buildDetail(final JSONObject vn) throws Exception {
        currentId    = vn.optString("id", "");
        String title     = vn.optString("title", "");
        String altTitle  = vn.optString("alttitle", "");
        String olang     = vn.optString("olang", "");
        String released  = vn.optString("released", "");
        final double rating  = vn.optDouble("rating", 0);
        int votecount    = vn.optInt("votecount", 0);
        double popularity= vn.optDouble("popularity", 0);
        int devstatus    = vn.optInt("devstatus", 0);
        int lenMin       = vn.optInt("length_minutes", 0);
        int lenVotes     = vn.optInt("length_votes", 0);
        String desc      = vn.optString("description", "");
        JSONObject image = vn.optJSONObject("image");
        JSONArray langs  = vn.optJSONArray("languages");
        JSONArray platforms = vn.optJSONArray("platforms");
        JSONArray aliases= vn.optJSONArray("aliases");
        JSONArray tags   = vn.optJSONArray("tags");
        JSONArray relations = vn.optJSONArray("relations");
        JSONArray developers= vn.optJSONArray("developers");
        JSONArray staff  = vn.optJSONArray("staff");
        JSONArray va     = vn.optJSONArray("va");
        JSONArray screenshots = vn.optJSONArray("screenshots");

        final String displayTitle = DeveloperActivity.chooseChinese(title, altTitle);
        String subTitle           = displayTitle.equals(title) ? altTitle : title;
        currentTitle = displayTitle;
        currentDesc  = MainActivity.cleanBBCode(desc);

        // 截图收集
        final ArrayList<String> ssUrls   = new ArrayList<>();
        final ArrayList<String> ssThumb  = new ArrayList<>();
        final ArrayList<String> ssLevels = new ArrayList<>();
        if (screenshots != null) {
            for (int i = 0; i < screenshots.length(); i++) {
                JSONObject ss = screenshots.getJSONObject(i);
                double sx = ss.optDouble("sexual", 0);
                double vx = ss.optDouble("violence", 0);
                String u  = ss.optString("url", "");
                String th = ss.optString("thumbnail", u);
                if (u.isEmpty()) continue;
                ssUrls.add(u); ssThumb.add(th);
                ssLevels.add(sx >= 2 ? "Explicit" : sx >= 1 ? "Suggestive" : "Safe");
            }
        }

        // ---- 封面 ----
        if (image != null) {
            final String imgUrl = image.optString("url", "");
            double sexual = image.optDouble("sexual", 0);
            double violence = image.optDouble("violence", 0);
            if (!imgUrl.isEmpty()) {
                ivCover.setVisibility(View.VISIBLE);
                final ArrayList<String> allImgs = new ArrayList<>();
                allImgs.add(imgUrl); allImgs.addAll(ssUrls);
                if (sexual >= 1.0 || violence >= 1.0) {
                    new AlertDialog.Builder(this)
                        .setTitle("成人内容提示")
                        .setMessage("该封面含 " + (sexual >= 2 ? "Explicit" : "Suggestive")
                            + " 内容，是否显示？")
                        .setPositiveButton("显示", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                ImageLoader.get().load(imgUrl, ivCover, Theme.container(DetailActivity.this));
                                setupCoverClick(ivCover, allImgs);
                            }
                        })
                        .setNegativeButton("隐藏", null).show();
                } else {
                    ImageLoader.get().load(imgUrl, ivCover, Theme.container(this));
                    setupCoverClick(ivCover, allImgs);
                }
            }
        }

        // ---- 标题卡 ----
        pad(10);
        LinearLayout titleCard = card();
        View gl = new View(this);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(36), dp(3));
        glp.setMargins(0, 0, 0, dp(12)); gl.setLayoutParams(glp);
        gl.setBackgroundColor(Theme.primary(this));
        titleCard.addView(gl);

        textTo(titleCard, displayTitle, Theme.onSurface(this), 20f, Typeface.BOLD, 0);
        if (!subTitle.isEmpty() && !subTitle.equals(displayTitle))
            textTo(titleCard, subTitle, Theme.onSurfaceVar(this), 12.5f, Typeface.NORMAL, dp(4));

        String statusTxt; int statusColor;
        switch (devstatus) {
            case 1: statusTxt = "开发中"; statusColor = Theme.primary(this); break;
            case 2: statusTxt = "已取消"; statusColor = Theme.error(this); break;
            default: statusTxt = "已完成"; statusColor = 0xFF4CAF50;
        }
        LinearLayout idRow = new LinearLayout(this);
        idRow.setOrientation(LinearLayout.HORIZONTAL);
        idRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams irp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        irp.setMargins(0, dp(10), 0, 0); idRow.setLayoutParams(irp);
        idRow.addView(chip(currentId.toUpperCase(), Theme.primaryCont(this), Theme.onPrimaryCont(this)));
        idRow.addView(spacer(8));
        idRow.addView(chip(statusTxt, Theme.container(this), statusColor));
        titleCard.addView(idRow);
        content.addView(titleCard);

        // ---- 壮观评分区 ----
        pad(4);
        RatingView rv = new RatingView(this, rating, votecount, popularity);
        LinearLayout.LayoutParams rvp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(130));
        rvp.setMargins(dp(12), dp(4), dp(12), dp(4));
        rv.setLayoutParams(rvp);
        content.addView(rv);
        rv.setAlpha(0f); rv.setScaleX(0.85f); rv.setScaleY(0.85f);
        rv.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(500).setStartDelay(250)
            .setInterpolator(new OvershootInterpolator(0.9f)).start();

        // ---- AI 总结按钮 ----
        pad(4);
        final LinearLayout aiCard = card();
        aiCard.setOrientation(LinearLayout.HORIZONTAL);
        aiCard.setGravity(Gravity.CENTER_VERTICAL);
        View aiBar = new View(this);
        aiBar.setBackgroundColor(Theme.secondary(this));
        aiBar.setLayoutParams(new LinearLayout.LayoutParams(dp(4), dp(20)));
        aiCard.addView(aiBar);
        aiCard.addView(spacer(10));
        final TextView tvAiLabel = new TextView(this);
        tvAiLabel.setText("AI 智能总结");
        tvAiLabel.setTextColor(Theme.onSurface(this)); tvAiLabel.setTextSize(14f);
        tvAiLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        aiCard.addView(tvAiLabel);
        TextView tvAiArrow = new TextView(this);
        tvAiArrow.setText("生成 ›"); tvAiArrow.setTextColor(Theme.secondary(this));
        tvAiArrow.setTextSize(13f);
        aiCard.addView(tvAiArrow);
        aiCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!aiCard.isEnabled()) return;
                tvAiLabel.setText("生成中...");
                aiCard.setEnabled(false);
                SettingsActivity.summarizeWithAI(DetailActivity.this, displayTitle, currentDesc,
                    new SettingsActivity.SummaryCallback() {
                        @Override public void onResult(String summary, String error) {
                            aiCard.setEnabled(true);
                            if (summary != null) {
                                tvAiLabel.setText("AI 总结");
                                // 插入结果卡片
                                int pos = content.indexOfChild(aiCard);
                                LinearLayout sumCard = card();
                                textTo(sumCard, summary, Theme.onSurfaceVar(DetailActivity.this),
                                    13.5f, Typeface.NORMAL, 0);
                                sumCard.setAlpha(0f);
                                content.addView(sumCard, pos + 1);
                                sumCard.animate().alpha(1f).setDuration(300).start();
                            } else {
                                tvAiLabel.setText("AI 总结（" + error + "）");
                            }
                        }
                    });
            }
        });
        content.addView(aiCard);

        // ---- 基本信息 ----
        sectionLabel("基本信息");
        LinearLayout info = card();
        if (!released.isEmpty()) row(info, "发行日期", released);
        if (!olang.isEmpty())    row(info, "原始语言", MainActivity.mapLangFull(olang));
        if (langs != null && langs.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < langs.length(); i++) {
                if (i > 0) sb.append("、");
                sb.append(MainActivity.mapLangFull(langs.getString(i)));
            }
            row(info, "支持语言", sb.toString());
        }
        if (platforms != null && platforms.length() > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < platforms.length(); i++) {
                if (i > 0) sb.append("、");
                sb.append(MainActivity.mapPlatform(platforms.getString(i)));
            }
            row(info, "平台", sb.toString());
        }
        if (lenMin > 0) {
            int h = lenMin / 60, m = lenMin % 60;
            String ls = h > 0 ? h + " 小时" + (m > 0 ? " " + m + " 分" : "") : m + " 分钟";
            if (lenVotes > 0) ls += "  (" + lenVotes + " 人投票)";
            row(info, "游戏时长", ls);
        } else {
            int le = vn.optInt("length", 0);
            String[] ln = {"","极短(<2h)","短(2~10h)","中等(10~30h)","长(30~50h)","极长(>50h)"};
            if (le > 0 && le < ln.length) row(info, "游戏时长", ln[le]);
        }
        content.addView(info);

        // ---- 开发商（可跳转）----
        if (developers != null && developers.length() > 0) {
            sectionLabel("开发商");
            LinearLayout dc = card();
            for (int i = 0; i < developers.length(); i++) {
                JSONObject d = developers.getJSONObject(i);
                final String devId   = d.optString("id", "");
                final String devName = d.optString("name", "");
                String devOrig       = d.optString("original", "");
                String display = devName + (!devOrig.isEmpty() && !devOrig.equals(devName)
                    ? "  (" + devOrig + ")" : "");

                LinearLayout devRow = new LinearLayout(this);
                devRow.setOrientation(LinearLayout.HORIZONTAL);
                devRow.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams drp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                drp.setMargins(0, dp(5), 0, 0); devRow.setLayoutParams(drp);

                TextView tvL = new TextView(this);
                tvL.setText(devId.toUpperCase()); tvL.setTextColor(Theme.outline(this));
                tvL.setTextSize(12f);
                tvL.setLayoutParams(new LinearLayout.LayoutParams(dp(88),
                    LinearLayout.LayoutParams.WRAP_CONTENT));
                TextView tvV = new TextView(this);
                tvV.setText(display + "  ›"); tvV.setTextColor(Theme.primary(this));
                tvV.setTextSize(13f);
                tvV.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                devRow.addView(tvL); devRow.addView(tvV);
                dc.addView(devRow);
                devRow.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Intent it = new Intent(DetailActivity.this, DeveloperActivity.class);
                        it.putExtra("dev_id", devId); it.putExtra("dev_name", devName);
                        startActivity(it);
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    }
                });
            }
            content.addView(dc);
        }

        // ---- 角色入口 ----
        sectionLabel("角色");
        LinearLayout charBtn = card();
        charBtn.setOrientation(LinearLayout.HORIZONTAL);
        charBtn.setGravity(Gravity.CENTER_VERTICAL);
        View charBar = new View(this);
        charBar.setBackgroundColor(0xFFE91E63);
        charBar.setLayoutParams(new LinearLayout.LayoutParams(dp(4), dp(20)));
        charBtn.addView(charBar); charBtn.addView(spacer(10));
        TextView tvCharLabel = new TextView(this);
        tvCharLabel.setText("查看全部角色");
        tvCharLabel.setTextColor(Theme.onSurface(this)); tvCharLabel.setTextSize(14f);
        tvCharLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        charBtn.addView(tvCharLabel);
        TextView tvCharArrow = new TextView(this);
        tvCharArrow.setText("  ›"); tvCharArrow.setTextColor(Theme.primary(this));
        tvCharArrow.setTextSize(20f); charBtn.addView(tvCharArrow);
        final String finalId = currentId;
        final String finalTitle = displayTitle;
        charBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent it = new Intent(DetailActivity.this, CharacterActivity.class);
                it.putExtra("vn_id", finalId); it.putExtra("vn_title", finalTitle);
                startActivity(it);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            }
        });
        content.addView(charBtn);

        // ---- 简介 ----
        if (!desc.isEmpty()) {
            sectionLabel("简介");
            LinearLayout descCard = card();
            textTo(descCard, MainActivity.cleanBBCode(desc),
                Theme.onSurfaceVar(this), 13.5f, Typeface.NORMAL, 0);
            content.addView(descCard);
        }

        // ---- 别名 ----
        if (aliases != null && aliases.length() > 0) {
            sectionLabel("别名");
            LinearLayout ac = card();
            for (int i = 0; i < aliases.length(); i++)
                textTo(ac, aliases.getString(i), Theme.onSurfaceVar(this), 13f,
                    Typeface.NORMAL, i > 0 ? dp(3) : 0);
            content.addView(ac);
        }

        // ---- 标签 ----
        if (tags != null && tags.length() > 0) {
            sectionLabel("标签");
            LinearLayout tc = card();
            StringBuilder cont = new StringBuilder();
            StringBuilder tech = new StringBuilder();
            StringBuilder sex  = new StringBuilder();
            for (int i = 0; i < tags.length(); i++) {
                JSONObject tag = tags.getJSONObject(i);
                if (tag.optBoolean("lie")) continue;
                int sp = tag.optInt("spoiler", 0);
                if (sp >= 2) continue;
                String tn = tag.optString("name", "") + (sp == 1 ? " [轻微剧透]" : "");
                String tid = tag.optString("id", "");
                if (tid.startsWith("s"))      sex.append("  ").append(tn).append("\n");
                else if (tid.startsWith("t")) tech.append("  ").append(tn).append("\n");
                else                          cont.append("  ").append(tn).append("\n");
            }
            if (cont.length() > 0) tagGroup(tc, "内容", cont.toString().trim());
            if (tech.length() > 0) tagGroup(tc, "技术", tech.toString().trim());
            if (sex.length()  > 0) tagGroup(tc, "性描写", sex.toString().trim());
            content.addView(tc);
        }

        // ---- 截图 ----
        if (!ssUrls.isEmpty()) {
            sectionLabel("截图  (" + ssUrls.size() + " 张)");
            buildScreenshots(ssUrls, ssThumb, ssLevels);
        }

        // ---- 发行版本（异步）----
        sectionLabel("发行版本");
        final LinearLayout relCard = card();
        final TextView tvRelLoad = new TextView(this);
        tvRelLoad.setText("加载中...");
        tvRelLoad.setTextColor(Theme.outline(this)); tvRelLoad.setTextSize(12f);
        relCard.addView(tvRelLoad);
        content.addView(relCard);
        loadReleases(currentId, relCard, tvRelLoad);

        // ---- 关联作品 ----
        if (relations != null && relations.length() > 0) {
            sectionLabel("关联作品");
            LinearLayout rc = card();
            for (int i = 0; i < relations.length(); i++) {
                JSONObject r = relations.getJSONObject(i);
                row(rc, mapRelation(r.optString("relation", "")),
                    r.optString("id", "").toUpperCase() + "  " + r.optString("title", ""));
            }
            content.addView(rc);
        }

        // ---- 制作人员 ----
        if (staff != null && staff.length() > 0) {
            sectionLabel("制作人员");
            LinearLayout sc = card();
            for (int i = 0; i < staff.length(); i++) {
                JSONObject s = staff.getJSONObject(i);
                String sn = s.optString("name", "");
                String so = s.optString("original", "");
                row(sc, mapStaffRole(s.optString("role", "")),
                    sn + (!so.isEmpty() && !so.equals(sn) ? " / " + so : ""));
            }
            content.addView(sc);
        }

        // ---- 配音演员 ----
        if (va != null && va.length() > 0) {
            sectionLabel("配音演员");
            LinearLayout vc = card();
            for (int i = 0; i < va.length(); i++) {
                JSONObject v = va.getJSONObject(i);
                JSONObject ch = v.optJSONObject("character");
                JSONObject sf = v.optJSONObject("staff");
                String cn = ch != null ? ch.optString("name", "") : "";
                String sn = sf != null ? sf.optString("name", "") : "";
                String so = sf != null ? sf.optString("original", "") : "";
                row(vc, cn, sn + (!so.isEmpty() && !so.equals(sn) ? " / " + so : ""));
            }
            content.addView(vc);
        }

        // ---- VNDB 链接 ----
        pad(8);
        LinearLayout linkCard = card();
        textTo(linkCard, "vndb.org/" + currentId, Theme.outline(this), 12f, Typeface.NORMAL, 0);
        content.addView(linkCard);

        // 整体淡入
        content.setAlpha(0f);
        content.animate().alpha(1f).setDuration(350).start();
    }

    // ================================================================
    //  发行版本 — ✅ 修复filter格式
    // ================================================================
    private void loadReleases(final String vnId, final LinearLayout relCard,
            final TextView tvLoad) {
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    // ✅ 正确格式: ["vn","=","v17"] 直接传id字符串
                    JSONArray filter = new JSONArray();
                    filter.put("vn");
                    filter.put("=");
                    filter.put(vnId);

                    JSONObject body = new JSONObject();
                    body.put("filters", filter);
                    body.put("fields", REL_FIELDS);
                    body.put("sort", "released");
                    body.put("results", 25);

                    final String resp = CharacterActivity.postWithAuth(API_REL, body.toString());
                    main.post(new Runnable() {
                        @Override public void run() { buildReleases(resp, relCard, tvLoad); }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override public void run() { tvLoad.setText("发行信息加载失败"); }
                    });
                }
            }
        });
    }

    private void buildReleases(String body, LinearLayout relCard, TextView tvLoad) {
        relCard.removeView(tvLoad);
        if (body == null || body.isEmpty()) {
            tvLoad.setText("请求失败"); relCard.addView(tvLoad); return;
        }
        try {
            // ✅ 先检查是否有错误消息
            JSONObject json = new JSONObject(body);
            if (json.has("message")) {
                tvLoad.setText("API: " + json.optString("message"));
                relCard.addView(tvLoad); return;
            }
            JSONArray results = json.optJSONArray("results");
            if (results == null || results.length() == 0) {
                tvLoad.setText("暂无发行信息"); relCard.addView(tvLoad); return;
            }
            for (int i = 0; i < results.length(); i++) {
                // ✅ 每个item单独try-catch，防止一条数据解析失败导致全部崩溃
                try {
                    JSONObject rel = results.getJSONObject(i);
                    String relTitle = rel.optString("title", "");
                    if (relTitle.isEmpty()) relTitle = rel.optString("id", "");
                    String relDate  = rel.optString("released", "");
                    boolean patch   = rel.optBoolean("patch", false);
                    boolean free    = rel.optBoolean("freeware", false);
                    int minage      = rel.optInt("minage", 0);
                    JSONArray rl    = rel.optJSONArray("languages");
                    JSONArray rp    = rel.optJSONArray("platforms");

                    LinearLayout rrow = new LinearLayout(this);
                    rrow.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams rrp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    rrp.setMargins(0, i > 0 ? dp(10) : 0, 0, 0);
                    rrow.setLayoutParams(rrp);

                    LinearLayout titleRow = new LinearLayout(this);
                    titleRow.setOrientation(LinearLayout.HORIZONTAL);
                    titleRow.setGravity(Gravity.CENTER_VERTICAL);

                    int ageColor = minage >= 18 ? Theme.error(this)
                        : minage >= 15 ? Theme.primary(this) : 0xFF4CAF50;
                    View dot = new View(this);
                    dot.setBackgroundColor(ageColor);
                    LinearLayout.LayoutParams dp_ = new LinearLayout.LayoutParams(dp(6), dp(6));
                    dp_.setMargins(0, 0, dp(8), 0); dot.setLayoutParams(dp_);
                    titleRow.addView(dot);

                    TextView tvRTitle = new TextView(this);
                    tvRTitle.setText(relTitle);
                    tvRTitle.setTextColor(Theme.onSurface(this));
                    tvRTitle.setTextSize(13f); tvRTitle.setTypeface(null, Typeface.BOLD);
                    tvRTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    titleRow.addView(tvRTitle);

                    if (!relDate.isEmpty()) {
                        TextView tvDate = new TextView(this);
                        tvDate.setText(relDate);
                        tvDate.setTextColor(Theme.outline(this));
                        tvDate.setTextSize(11f);
                        titleRow.addView(tvDate);
                    }
                    rrow.addView(titleRow);

                    // 标签行
                    LinearLayout tagRow = new LinearLayout(this);
                    tagRow.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tp.setMargins(dp(14), dp(3), 0, 0); tagRow.setLayoutParams(tp);

                    if (rl != null) for (int j = 0; j < Math.min(rl.length(), 4); j++)
                        tagRow.addView(miniTag(MainActivity.mapLang(rl.getString(j)),
                            Theme.secondaryCont(this), Theme.secondary(this)));
                    if (rp != null) for (int j = 0; j < Math.min(rp.length(), 3); j++)
                        tagRow.addView(miniTag(MainActivity.mapPlatform(rp.getString(j)),
                            Theme.primaryCont(this), Theme.primary(this)));
                    if (minage >= 18) tagRow.addView(miniTag("18+", Theme.errorCont(this), Theme.error(this)));
                    if (patch)   tagRow.addView(miniTag("补丁", Theme.container(this), Theme.outline(this)));
                    if (free)    tagRow.addView(miniTag("免费", Theme.container(this), 0xFF4CAF50));
                    rrow.addView(tagRow);
                    relCard.addView(rrow);
                } catch (Exception ignored) {} // 跳过单条解析失败
            }
        } catch (Exception e) {
            tvLoad.setText("解析错误: " + e.getMessage());
            relCard.addView(tvLoad);
        }
    }

    // ================================================================
    //  截图区
    // ================================================================
    private void buildScreenshots(final ArrayList<String> urls,
            final ArrayList<String> thumbs, final ArrayList<String> levels) {

        int safe = 0, sugg = 0, expl = 0;
        for (String l : levels) {
            if ("Safe".equals(l)) safe++;
            else if ("Suggestive".equals(l)) sugg++;
            else expl++;
        }

        LinearLayout outerCard = card();
        outerCard.setPadding(dp(6), dp(6), dp(6), dp(6));

        // 分类标签
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(0, 0, 0, dp(8));
        if (safe > 0) tabs.addView(levelTag("Safe " + safe, 0xFF4CAF50));
        if (sugg > 0) tabs.addView(levelTag("Suggestive " + sugg, Theme.primary(this)));
        if (expl > 0) tabs.addView(levelTag("Explicit " + expl, Theme.error(this)));
        outerCard.addView(tabs);

        // Safe 截图
        int shown = 0;
        for (int i = 0; i < thumbs.size() && shown < 6; i++) {
            if (!"Safe".equals(levels.get(i))) continue;
            addSsThumb(outerCard, thumbs.get(i), urls, i, shown);
            shown++;
        }

        // NSFW 询问
        if (sugg > 0 || expl > 0) {
            final LinearLayout nsfwHolder = new LinearLayout(this);
            nsfwHolder.setOrientation(LinearLayout.VERTICAL);
            final int nsfwCount = sugg + expl;
            TextView tvNsfwBtn = new TextView(this);
            tvNsfwBtn.setText("查看成人截图  (" + nsfwCount + " 张)");
            tvNsfwBtn.setTextColor(Theme.primary(this)); tvNsfwBtn.setTextSize(13f);
            tvNsfwBtn.setBackgroundColor(Theme.container(this));
            tvNsfwBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
            tvNsfwBtn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams btnp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnp.setMargins(0, dp(6), 0, 0); tvNsfwBtn.setLayoutParams(btnp);
            tvNsfwBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(DetailActivity.this)
                        .setTitle("成人内容")
                        .setMessage("这些截图含成人内容，确定显示？")
                        .setPositiveButton("显示", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                nsfwHolder.removeAllViews();
                                int n = 0;
                                for (int i = 0; i < thumbs.size() && n < 8; i++) {
                                    if ("Safe".equals(levels.get(i))) continue;
                                    addSsThumb(nsfwHolder, thumbs.get(i), urls, i, n);
                                    n++;
                                }
                            }
                        })
                        .setNegativeButton("取消", null).show();
                }
            });
            outerCard.addView(tvNsfwBtn);
            outerCard.addView(nsfwHolder);
        }
        content.addView(outerCard);
    }

    private void addSsThumb(LinearLayout parent, String thumb,
            final ArrayList<String> allUrls, final int index, int order) {
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Theme.container(this));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(160));
        p.setMargins(0, order > 0 ? dp(6) : 0, 0, 0);
        iv.setLayoutParams(p);
        parent.addView(iv);
        ImageLoader.get().load(thumb, iv, Theme.container(this));
        iv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                iv.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            iv.animate().scaleX(1f).scaleY(1f).setDuration(80)
                                .withEndAction(new Runnable() {
                                    @Override public void run() { openViewer(allUrls, index); }
                                }).start();
                        }
                    }).start();
            }
        });
    }

    private void setupCoverClick(final ImageView iv, final ArrayList<String> imgs) {
        iv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                iv.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            iv.animate().scaleX(1f).scaleY(1f).setDuration(90)
                                .withEndAction(new Runnable() {
                                    @Override public void run() { openViewer(imgs, 0); }
                                }).start();
                        }
                    }).start();
            }
        });
    }

    private void openViewer(ArrayList<String> urls, int startIdx) {
        Intent it = new Intent(this, ImageViewerActivity.class);
        it.putExtra(ImageViewerActivity.EXTRA_URLS, urls.toArray(new String[0]));
        it.putExtra(ImageViewerActivity.EXTRA_INDEX, startIdx);
        startActivity(it);
        overridePendingTransition(R.anim.scale_fade_in, R.anim.slide_out_left);
    }

    // ================================================================
    //  壮观评分视图
    // ================================================================
    static class RatingView extends android.view.View {
        private final double rating, popularity;
        private final int votecount;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float animProg = 0f;
        private final android.content.Context ctx;

        RatingView(android.content.Context ctx, double rating, int votecount, double popularity) {
            super(ctx);
            this.ctx = ctx; this.rating = rating;
            this.votecount = votecount; this.popularity = popularity;
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(900); va.setStartDelay(350);
            va.setInterpolator(new DecelerateInterpolator(1.5f));
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    animProg = (float) a.getAnimatedValue(); invalidate();
                }
            });
            va.start();
        }

        @Override protected void onDraw(Canvas c) {
            int W = getWidth(), H = getHeight();
            float cx = W * 0.28f, cy = H * 0.52f, radius = H * 0.38f;

            // 轨道
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(14f); p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(Theme.container(ctx));
            c.drawArc(new RectF(cx-radius, cy-radius, cx+radius, cy+radius),
                140f, 260f, false, p);

            // 评分弧
            if (rating > 0) {
                float sweep = 260f * (float)(rating / 100.0) * animProg;
                int arcColor = Theme.ratingColor(ctx, rating);
                p.setColor(arcColor);
                c.drawArc(new RectF(cx-radius, cy-radius, cx+radius, cy+radius),
                    140f, sweep, false, p);
                p.setStyle(Paint.Style.FILL);
                double ea = Math.toRadians(140 + sweep);
                float ex = cx + (float)(Math.cos(ea) * radius);
                float ey = cy + (float)(Math.sin(ea) * radius);
                p.setColor(arcColor); c.drawCircle(ex, ey, 10f, p);
                p.setColor(0x99FFFFFF); c.drawCircle(ex-2, ey-2, 4f, p);
            }

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);

            int textColor = rating > 0 ? Theme.ratingColor(ctx, rating) : Theme.outline(ctx);
            String scoreStr = rating > 0 ? String.format("%.1f", (rating/10.0) * animProg) : "—";
            p.setColor(textColor); p.setTextSize(H * 0.36f);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            c.drawText(scoreStr, cx, cy + H * 0.14f, p);

            p.setColor(Theme.outline(ctx)); p.setTextSize(H * 0.11f);
            p.setTypeface(Typeface.DEFAULT);
            c.drawText("/ 10", cx, cy + H * 0.29f, p);
            c.drawText(formatV(votecount) + " 票", cx, cy - H * 0.27f, p);

            // 右侧数据
            float rx = W * 0.60f, lh = H * 0.22f, y0 = cy - lh * 1.1f;
            String[] labels = {"评分", "人气", "评级"};
            p.setColor(Theme.outline(ctx)); p.setTextSize(H * 0.10f);
            p.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < labels.length; i++)
                c.drawText(labels[i], rx, y0 + i * lh, p);

            String[] vals = {
                rating > 0 ? String.format("%.2f", rating/10.0) : "N/A",
                popularity > 0 ? String.format("%.1f", popularity) : "N/A",
                rating >= 80 ? "精品" : rating >= 65 ? "良作" : rating >= 50 ? "普通" : "待评"
            };
            p.setTextSize(H * 0.14f);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            for (int i = 0; i < vals.length; i++) {
                p.setColor(i == 0 ? textColor : i == 1 ? Theme.secondary(ctx) : textColor);
                c.drawText(vals[i], rx, y0 + i * lh + H * 0.13f, p);
            }
        }

        private String formatV(int v) {
            if (v >= 10000) return (v/1000) + "k";
            if (v >= 1000) return String.format("%.1fk", v/1000f);
            return String.valueOf(v);
        }
    }

    // ================================================================
    //  UI 辅助
    // ================================================================
    private void sectionLabel(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(dp(12), dp(16), dp(12), dp(4)); row.setLayoutParams(rp);
        View bar = new View(this); bar.setBackgroundColor(Theme.primary(this));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(4), dp(16));
        bp.setMargins(0, 0, dp(8), 0); bar.setLayoutParams(bp); row.addView(bar);
        TextView tv = new TextView(this); tv.setText(text);
        tv.setTextColor(Theme.primary(this)); tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD); tv.setLetterSpacing(0.04f);
        row.addView(tv); content.addView(row);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Theme.surface(this));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(12), dp(2), dp(12), dp(2)); c.setLayoutParams(p);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        return c;
    }

    private void textTo(LinearLayout p, String t, int color, float size, int style, int topM) {
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextColor(color); tv.setTextSize(size);
        tv.setTypeface(null, style);
        if (topM > 0) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, topM, 0, 0); tv.setLayoutParams(lp);
        }
        p.addView(tv);
    }

    private void row(LinearLayout parent, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(5), 0, 0); r.setLayoutParams(rp);
        TextView tvL = new TextView(this); tvL.setText(label);
        tvL.setTextColor(Theme.outline(this)); tvL.setTextSize(12f);
        tvL.setLayoutParams(new LinearLayout.LayoutParams(dp(88),
            LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView tvV = new TextView(this); tvV.setText(value);
        tvV.setTextColor(Theme.onSurfaceVar(this)); tvV.setTextSize(13f);
        tvV.setLayoutParams(new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(tvL); r.addView(tvV); parent.addView(r);
    }

    private void tagGroup(LinearLayout parent, String groupName, String content_) {
        TextView tvG = new TextView(this); tvG.setText(groupName);
        tvG.setTextColor(Theme.secondary(this)); tvG.setTextSize(11f);
        tvG.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gp.setMargins(0, dp(8), 0, dp(2)); tvG.setLayoutParams(gp);
        parent.addView(tvG);
        TextView tvC = new TextView(this); tvC.setText(content_);
        tvC.setTextColor(Theme.onSurfaceVar(this)); tvC.setTextSize(12.5f);
        parent.addView(tvC);
    }

    private TextView chip(String text, int bg, int fg) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(fg); tv.setTextSize(10.5f);
        tv.setBackgroundColor(bg); tv.setPadding(dp(8), dp(3), dp(8), dp(3));
        return tv;
    }

    private TextView miniTag(String text, int bg, int fg) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(fg); tv.setTextSize(10f);
        tv.setBackgroundColor(bg); tv.setPadding(dp(5), dp(2), dp(5), dp(2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(5), 0); tv.setLayoutParams(p);
        return tv;
    }

    private TextView levelTag(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(color); tv.setTextSize(10.5f);
        tv.setBackgroundColor(Theme.container(this));
        tv.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(6), 0); tv.setLayoutParams(p);
        return tv;
    }

    private View spacer(int dpW) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(dpW), 1));
        return v;
    }

    private void pad(int dpH) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(dpH)));
        content.addView(v);
    }

    private View makeDivider(int color, int h) {
        View v = new View(this);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(h)));
        return v;
    }

    private void showError(String msg) {
        progress.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(msg);
    }

    private String mapRelation(String r) {
        switch (r) {
            case "seq": return "续作"; case "preq": return "前传";
            case "set": return "同世界"; case "alt": return "替代版";
            case "char": return "共享角色"; case "side": return "外传";
            case "par": return "原作"; case "ser": return "同系列";
            case "fan": return "衍生"; case "orig": return "原版";
            default: return r;
        }
    }

    private String mapStaffRole(String r) {
        switch (r) {
            case "director": return "导演"; case "scenario": return "剧本";
            case "chardesign": return "人设"; case "art": return "美术";
            case "music": return "音乐"; case "songs": return "歌曲";
            case "translator": return "翻译"; case "editor": return "编辑";
            case "qa": return "QA"; case "staff": return "制作";
            default: return r;
        }
    }

    int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}

