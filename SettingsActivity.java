package com.dvlo.vndbapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {

    public static final String PREFS           = "vndb_prefs";
    public static final String KEY_AI_ENABLE   = "ai_enable";
    public static final String KEY_AI_PROVIDER = "ai_provider"; // "deepseek" | "groq"
    public static final String KEY_AI_KEY      = "ai_key";

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView tvCacheSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // 根据系统深色模式设置背景
        boolean dark = Theme.isDark(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Theme.bg(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
								 LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- 顶栏 ----
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setBackgroundColor(Theme.surface(this));
        toolbar.setPadding(0, dp(12), dp(16), dp(12));
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("  ←  ");
        btnBack.setTextColor(Theme.primary(this));
        btnBack.setTextSize(20f);
        btnBack.setPadding(dp(12), dp(8), dp(8), dp(8));
        btnBack.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { finish(); }
			});
        toolbar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("设置");
        tvTitle.setTextColor(Theme.onSurface(this));
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
									0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        toolbar.addView(tvTitle);

        TextView tvVer = new TextView(this);
        tvVer.setText("v3.0");
        tvVer.setTextColor(Theme.outline(this));
        tvVer.setTextSize(11f);
        tvVer.setGravity(Gravity.END);
        toolbar.addView(tvVer);

        root.addView(toolbar);
        root.addView(divider());

        // ====== 外观 ======
        root.addView(sectionHeader("外观"));

        // 深色模式说明（只读，跟随系统）
        LinearLayout darkRow = makeRow();
        darkRow.addView(rowLabel("深色模式"));
        TextView tvDarkVal = new TextView(this);
        tvDarkVal.setText(dark ? "已启用（跟随系统）" : "已关闭（跟随系统）");
        tvDarkVal.setTextColor(Theme.outline(this));
        tvDarkVal.setTextSize(13f);
        darkRow.addView(tvDarkVal);
        root.addView(darkRow);
        root.addView(divider());

        // ====== AI 总结 ======
        root.addView(sectionHeader("AI 总结"));

        // 启用开关
        LinearLayout aiEnRow = makeRow();
        aiEnRow.addView(rowLabel("启用 AI 总结"));
        final Switch swAI = new Switch(this);
        swAI.setChecked(prefs.getBoolean(KEY_AI_ENABLE, false));
        swAI.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override public void onCheckedChanged(CompoundButton b, boolean checked) {
					prefs.edit().putBoolean(KEY_AI_ENABLE, checked).apply();
				}
			});
        aiEnRow.addView(swAI);
        root.addView(aiEnRow);
        root.addView(divider());

        // AI 服务商
        final String[] providers = {"DeepSeek", "Groq"};
        final String curProvider = prefs.getString(KEY_AI_PROVIDER, "deepseek");
        final int[] selProvider = {curProvider.equals("groq") ? 1 : 0};

        LinearLayout provRow = makeRow();
        provRow.addView(rowLabel("AI 服务商"));
        final TextView tvProv = new TextView(this);
        tvProv.setText(providers[selProvider[0]]);
        tvProv.setTextColor(Theme.primary(this));
        tvProv.setTextSize(13f);
        provRow.addView(tvProv);
        provRow.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					new AlertDialog.Builder(SettingsActivity.this)
						.setTitle("选择 AI 服务商")
						.setSingleChoiceItems(providers, selProvider[0],
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                selProvider[0] = which;
                                prefs.edit().putString(KEY_AI_PROVIDER,
													   which == 1 ? "groq" : "deepseek").apply();
                                tvProv.setText(providers[which]);
                                d.dismiss();
                            }
                        })
						.show();
				}
			});
        root.addView(provRow);
        root.addView(divider());

        // API Key
        LinearLayout keyRow = makeRow();
        keyRow.addView(rowLabel("API Key"));
        final TextView tvKey = new TextView(this);
        tvKey.setText(maskedKey(prefs.getString(KEY_AI_KEY, "")));
        tvKey.setTextColor(Theme.outline(this));
        tvKey.setTextSize(12f);
        keyRow.addView(tvKey);
        keyRow.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					final EditText et = new EditText(SettingsActivity.this);
					et.setHint("粘贴你的 API Key");
					et.setText(prefs.getString(KEY_AI_KEY, ""));
					et.setTextColor(Theme.onSurface(SettingsActivity.this));
					et.setHintTextColor(Theme.outline(SettingsActivity.this));
					et.setPadding(dp(12), dp(8), dp(12), dp(8));
					new AlertDialog.Builder(SettingsActivity.this)
						.setTitle("设置 API Key")
						.setMessage("DeepSeek: platform.deepseek.com\nGroq: console.groq.com")
						.setView(et)
						.setPositiveButton("保存", new DialogInterface.OnClickListener() {
							@Override public void onClick(DialogInterface d, int w) {
								String key = et.getText().toString().trim();
								prefs.edit().putString(KEY_AI_KEY, key).apply();
								tvKey.setText(maskedKey(key));
							}
						})
						.setNegativeButton("取消", null).show();
				}
			});
        root.addView(keyRow);
        root.addView(divider());

        // ====== 缓存 ======
        root.addView(sectionHeader("缓存"));

        LinearLayout cacheRow = makeRow();
        cacheRow.addView(rowLabel("图片缓存"));
        tvCacheSize = new TextView(this);
        tvCacheSize.setText("计算中...");
        tvCacheSize.setTextColor(Theme.outline(this));
        tvCacheSize.setTextSize(13f);
        cacheRow.addView(tvCacheSize);
        root.addView(cacheRow);
        refreshCacheSize();
        root.addView(divider());

        LinearLayout clearRow = makeRow();
        TextView tvClear = rowLabel("清除图片缓存");
        tvClear.setTextColor(Theme.error(this));
        clearRow.addView(tvClear);
        clearRow.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { confirmClear(); }
			});
        root.addView(clearRow);
        root.addView(divider());

        // ====== 关于 ======
        root.addView(sectionHeader("关于"));
        addSimpleRow(root, "版本", "3.0");
        addLinkRow(root, "数据来源", "VNDB.org", "https://vndb.org");
        addLinkRow(root, "API 文档", "api.vndb.org/kana", "https://api.vndb.org/kana");
        addLinkRow(root, "DeepSeek", "platform.deepseek.com", "https://platform.deepseek.com");
        addLinkRow(root, "Groq", "console.groq.com", "https://console.groq.com");
        root.addView(divider());

        // ====== 联系 ======
        root.addView(sectionHeader("联系 / 反馈"));
        LinearLayout qqRow = makeRow();
        qqRow.addView(rowLabel("加入 QQ 群"));
        TextView tvQQ = new TextView(this);
        tvQQ.setText("1057709529");
        tvQQ.setTextColor(Theme.primary(this));
        tvQQ.setTextSize(13f);
        qqRow.addView(tvQQ);
        qqRow.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { joinQQ(); }
			});
        root.addView(qqRow);
        root.addView(divider());

        // 底部
        TextView tvCopy = new TextView(this);
        tvCopy.setText("数据来源: VNDB.org  |  CC BY-NC-SA 4.0");
        tvCopy.setTextColor(Theme.outlineVar(this));
        tvCopy.setTextSize(10.5f);
        tvCopy.setGravity(Gravity.CENTER);
        tvCopy.setPadding(0, dp(24), 0, dp(40));
        root.addView(tvCopy);

        sv.addView(root);
        setContentView(sv);
    }

    // ================================================================
    //  AI 总结（静态方法，DetailActivity 调用）
    // ================================================================
    public static void summarizeWithAI(final Context ctx, final String vnTitle,
									   final String description, final SummaryCallback cb) {

        final SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_AI_ENABLE, false)) {
            cb.onResult(null, "AI 总结未启用，请在设置中开启并填写 API Key");
            return;
        }
        final String key      = prefs.getString(KEY_AI_KEY, "");
        final String provider = prefs.getString(KEY_AI_PROVIDER, "deepseek");
        if (key.isEmpty()) { cb.onResult(null, "请先在设置中填写 API Key"); return; }

        new Thread(new Runnable() {
				@Override public void run() {
					try {
						String endpoint, model;
						if ("groq".equals(provider)) {
							endpoint = "https://api.groq.com/openai/v1/chat/completions";
							model    = "openai/gpt-oss-120b";
						} else {
							endpoint = "https://api.deepseek.com/chat/completions";
							model    = "deepseek-chat";
						}
						String prompt = "请用中文200字以内简明概括视觉小说「" + vnTitle + "」的核心内容：\n\n" + description;
						String reqBody = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":"
							+ org.json.JSONObject.quote(prompt) + "}],\"max_tokens\":400}";

						java.net.URL url = new java.net.URL(endpoint);
						java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
						conn.setRequestMethod("POST");
						conn.setRequestProperty("Content-Type", "application/json");
						conn.setRequestProperty("Authorization", "Bearer " + key);
						conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
						conn.setDoOutput(true);
						conn.getOutputStream().write(reqBody.getBytes("UTF-8"));

						int code = conn.getResponseCode();
						java.io.InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
						java.io.BufferedReader br = new java.io.BufferedReader(
							new java.io.InputStreamReader(is, "UTF-8"));
						StringBuilder sb = new StringBuilder(); String line;
						while ((line = br.readLine()) != null) sb.append(line);
						conn.disconnect();

						org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
						if (resp.has("error")) {
							final String err = resp.getJSONObject("error").optString("message", "未知错误");
							new Handler(Looper.getMainLooper()).post(new Runnable() {
									@Override public void run() { cb.onResult(null, "AI 错误: " + err); }
								});
						} else {
							final String result = resp.getJSONArray("choices")
								.getJSONObject(0).getJSONObject("message").getString("content");
							new Handler(Looper.getMainLooper()).post(new Runnable() {
									@Override public void run() { cb.onResult(result, null); }
								});
						}
					} catch (final Exception e) {
						new Handler(Looper.getMainLooper()).post(new Runnable() {
								@Override public void run() { cb.onResult(null, "请求失败: " + e.getMessage()); }
							});
					}
				}
			}).start();
    }

    public interface SummaryCallback {
        void onResult(String summary, String error);
    }

    // ================================================================
    //  辅助方法
    // ================================================================
    private void refreshCacheSize() {
        exec.execute(new Runnable() {
				@Override public void run() {
					final long bytes = ImageLoader.get().getDiskCacheSize();
					main.post(new Runnable() {
							@Override public void run() {
								if (tvCacheSize != null) tvCacheSize.setText(formatSize(bytes));
							}
						});
				}
			});
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle("清除缓存")
            .setMessage("确定清除所有图片缓存？下次查看时将重新下载。")
            .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    exec.execute(new Runnable() {
							@Override public void run() {
								ImageLoader.get().clearAll();
								main.post(new Runnable() {
										@Override public void run() {
											Toast.makeText(SettingsActivity.this, "缓存已清除", Toast.LENGTH_SHORT).show();
											if (tvCacheSize != null) tvCacheSize.setText("0 B");
										}
									});
							}
						});
                }
            })
            .setNegativeButton("取消", null).show();
    }

    private void joinQQ() {
        final String qq = "1057709529";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
									 Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Fk%3D" + qq)));
        } catch (Exception e) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("QQ群号", qq));
            Toast.makeText(this, "群号 " + qq + " 已复制到剪贴板", Toast.LENGTH_LONG).show();
        }
    }

    private void addSimpleRow(LinearLayout root, String label, String value) {
        LinearLayout row = makeRow();
        row.addView(rowLabel(label));
        TextView tv = new TextView(this);
        tv.setText(value); tv.setTextColor(Theme.outline(this)); tv.setTextSize(13f);
        row.addView(tv);
        root.addView(row); root.addView(divider());
    }

    private void addLinkRow(LinearLayout root, String label, String linkText, final String url) {
        LinearLayout row = makeRow();
        row.addView(rowLabel(label));
        TextView tv = new TextView(this);
        tv.setText(linkText); tv.setTextColor(Theme.primary(this)); tv.setTextSize(13f);
        row.addView(tv);
        row.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) {
					try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
					catch (Exception ignored) {}
				}
			});
        root.addView(row); root.addView(divider());
    }

    private LinearLayout sectionHeader(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(rp);
        row.setBackgroundColor(Theme.container(this));
        row.setPadding(dp(16), dp(10), dp(16), dp(8));

        View bar = new View(this);
        bar.setBackgroundColor(Theme.primary(this));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(3), dp(14));
        bp.setMargins(0, 0, dp(8), 0); bar.setLayoutParams(bp);
        row.addView(bar);

        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Theme.primary(this));
        tv.setTextSize(11f); tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.06f);
        row.addView(tv);
        return row;
    }

    private LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Theme.surface(this));
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setLayoutParams(new LinearLayout.LayoutParams(
								LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView rowLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Theme.onSurface(this)); tv.setTextSize(14f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,
														 LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return tv;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Theme.outlineVar(this));
        v.setLayoutParams(new LinearLayout.LayoutParams(
							  LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private static String maskedKey(String key) {
        if (key == null || key.isEmpty()) return "未设置";
        return "••••••" + key.substring(Math.max(0, key.length() - 4));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024f);
        return String.format("%.1f MB", bytes / (1024f * 1024f));
    }

    int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }
}

