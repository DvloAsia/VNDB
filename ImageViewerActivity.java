package com.dvlo.vndbapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageViewerActivity extends Activity {

    public static final String EXTRA_URLS  = "img_urls";
    public static final String EXTRA_INDEX = "img_index";

    private final List<String> urls = new ArrayList<>();
    private int cur = 0;

    private FrameLayout   root;
    private ZoomImageView ivMain;
    private ProgressBar   pbLoad;
    private TextView      tvCounter;
    private LinearLayout  topBar;
    private boolean       uiVisible = true;

    // 编辑模式
    private boolean   editMode = false;
    private DrawView  drawView;
    private View      editToolbar;

    private final ExecutorService exec = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final int REQ_WRITE = 1001;
    private String pendingDlUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
							 | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawableResource(android.R.color.black);

        String[] arr = getIntent().getStringArrayExtra(EXTRA_URLS);
        cur = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (arr != null) for (String u : arr) urls.add(u);
        if (urls.isEmpty()) { finish(); return; }
        cur = Math.max(0, Math.min(cur, urls.size() - 1));

        buildUI();
        enterAnim();
        loadImage(cur);
    }

    // ================================================================
    //  UI 构建
    // ================================================================
    private void buildUI() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // 主图
        ivMain = new ZoomImageView(this);
        root.addView(ivMain, matchParent());

        // 进度
        pbLoad = new ProgressBar(this);
        FrameLayout.LayoutParams pbp = new FrameLayout.LayoutParams(dp(48), dp(48));
        pbp.gravity = Gravity.CENTER;
        root.addView(pbLoad, pbp);

        // 顶部栏
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xCC000000);
        topBar.setPadding(dp(4), dp(0), dp(4), dp(0));
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams tbp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        tbp.gravity = Gravity.TOP;
        tbp.topMargin = dp(24);
        root.addView(topBar, tbp);

        // 关闭按钮
        TextView tvClose = makeTopBtn("✕");
        tvClose.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { exitAnim(); }
			});
        topBar.addView(tvClose);

        // 计数器（居中）
        tvCounter = new TextView(this);
        tvCounter.setTextColor(0xCCFFFFFF);
        tvCounter.setTextSize(13f);
        tvCounter.setGravity(Gravity.CENTER);
        topBar.addView(tvCounter, new LinearLayout.LayoutParams(0,
																LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 保存按钮
        TextView tvSave = makeTopBtn("↓ 保存");
        tvSave.setTextColor(0xFF90CAF9);
        tvSave.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { requestDownload(); }
			});
        topBar.addView(tvSave);

        // 更多按钮（壁纸/编辑）
        TextView tvMore = makeTopBtn("⋯");
        tvMore.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showMoreMenu(); }
			});
        topBar.addView(tvMore);

        // 底部提示
        final TextView tvHint = new TextView(this);
        tvHint.setText("左右滑动切换  •  双击放大  •  长按更多");
        tvHint.setTextColor(0x88FFFFFF);
        tvHint.setTextSize(11f);
        tvHint.setGravity(Gravity.CENTER);
        tvHint.setBackgroundColor(0x66000000);
        tvHint.setPadding(dp(16), dp(8), dp(16), dp(8));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.BOTTOM;
        hp.bottomMargin = dp(20);
        root.addView(tvHint, hp);
        main.postDelayed(new Runnable() {
				@Override public void run() {
					tvHint.animate().alpha(0f).setDuration(500)
						.withEndAction(new Runnable() {
							@Override public void run() { tvHint.setVisibility(View.GONE); }
						}).start();
				}
			}, 3000);

        setContentView(root);
        updateCounter();

        // 手势
        final GestureDetector gd = new GestureDetector(this,
            new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                    toggleUI(); return true;
                }
                @Override public void onLongPress(MotionEvent e) {
                    if (!editMode) showMoreMenu();
                }
                @Override public boolean onFling(MotionEvent e1, MotionEvent e2,
												 float vx, float vy) {
                    if (!ivMain.isZoomed() && Math.abs(vx) > Math.abs(vy) * 1.5f) {
                        if (vx < -400) { goTo(cur + 1); return true; }
                        if (vx >  400) { goTo(cur - 1); return true; }
                    }
                    return false;
                }
            });
        ivMain.setExtGesture(gd);
    }

    private TextView makeTopBtn(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xEEFFFFFF);
        tv.setTextSize(14f);
        tv.setPadding(dp(14), dp(14), dp(14), dp(14));
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    // ================================================================
    //  图片加载
    // ================================================================
    private void loadImage(final int idx) {
        if (idx < 0 || idx >= urls.size()) return;
        final String url = urls.get(idx);
        pbLoad.setVisibility(View.VISIBLE);
        ImageLoader.get().loadHDAsync(url, new ImageLoader.Callback() {
				@Override public void onLoaded(final Bitmap bmp) {
					pbLoad.setVisibility(View.GONE);
					if (bmp != null && idx == cur) {
						ivMain.setImageBitmap(bmp);
						ivMain.setAlpha(0f);
						ivMain.animate().alpha(1f).setDuration(260).start();
					}
				}
			});
    }

    // ================================================================
    //  翻页
    // ================================================================
    void goTo(final int idx) {
        if (idx < 0 || idx >= urls.size() || idx == cur) return;
        final int dir = idx > cur ? 1 : -1;
        cur = idx; updateCounter();
        final float slideX = ivMain.getWidth() * 0.30f;
        ivMain.animate().translationX(-dir * slideX).alpha(0f)
            .setDuration(160).setInterpolator(new DecelerateInterpolator())
            .withEndAction(new Runnable() {
                @Override public void run() {
                    ivMain.setImageBitmap(null); ivMain.resetZoom();
                    ivMain.setTranslationX(dir * slideX); ivMain.setAlpha(0f);
                    ivMain.animate().translationX(0f).alpha(1f)
                        .setDuration(200).setInterpolator(new DecelerateInterpolator()).start();
                    loadImage(cur);
                }
            }).start();
    }

    // ================================================================
    //  进入/退出动画
    // ================================================================
    private void enterAnim() {
        ivMain.setScaleX(0.82f); ivMain.setScaleY(0.82f); ivMain.setAlpha(0f);
        ivMain.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(340).setInterpolator(new OvershootInterpolator(0.9f)).start();
    }

    private void exitAnim() {
        ivMain.animate().scaleX(0.85f).scaleY(0.85f).alpha(0f)
            .setDuration(200).setInterpolator(new DecelerateInterpolator())
            .withEndAction(new Runnable() {
                @Override public void run() { finish(); }
            }).start();
    }

    private void toggleUI() {
        uiVisible = !uiVisible;
        topBar.animate().alpha(uiVisible ? 1f : 0f).setDuration(200).start();
    }

    private void updateCounter() {
        if (tvCounter != null) tvCounter.setText((cur + 1) + " / " + urls.size());
    }

    // ================================================================
    //  更多菜单（壁纸/编辑）
    // ================================================================
    private void showMoreMenu() {
        String[] items = {"设为壁纸", "编辑图片"};
        new AlertDialog.Builder(this)
            .setItems(items, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) setAsWallpaper();
                    else openEditor();
                }
            }).show();
    }

    // ================================================================
    //  保存图片
    // ================================================================
    private void requestDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
                pendingDlUrl = urls.get(cur);
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
                return;
            }
        }
        doDownload(urls.get(cur));
    }

    @Override public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        if (req == REQ_WRITE && grants.length > 0
			&& grants[0] == PackageManager.PERMISSION_GRANTED && pendingDlUrl != null) {
            doDownload(pendingDlUrl); pendingDlUrl = null;
        } else {
            Toast.makeText(this, "需要存储权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void doDownload(final String url) {
        Toast.makeText(this, "保存中...", Toast.LENGTH_SHORT).show();
        exec.execute(new Runnable() {
				@Override public void run() {
					boolean ok = false;
					try {
						byte[] data = ImageLoader.get().downloadRaw(url);
						if (data != null) {
							String fname = "VNDB_" + new SimpleDateFormat(
								"yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
							if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
								ContentValues cv = new ContentValues();
								cv.put(MediaStore.Images.Media.DISPLAY_NAME, fname);
								cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
								cv.put(MediaStore.Images.Media.RELATIVE_PATH,
									   Environment.DIRECTORY_PICTURES + "/VNDB");
								Uri uri = getContentResolver().insert(
									MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
								if (uri != null) {
									OutputStream os = getContentResolver().openOutputStream(uri);
									if (os != null) { os.write(data); os.close(); ok = true; }
								}
							} else {
								File dir = new File(Environment.getExternalStoragePublicDirectory(
														Environment.DIRECTORY_PICTURES), "VNDB");
								dir.mkdirs();
								FileOutputStream fos = new FileOutputStream(new File(dir, fname));
								fos.write(data); fos.close(); ok = true;
								sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
														 Uri.fromFile(new File(dir, fname))));
							}
						}
					} catch (Exception ignored) {}
					final boolean success = ok;
					main.post(new Runnable() {
							@Override public void run() {
								Toast.makeText(ImageViewerActivity.this,
											   success ? "已保存到相册 VNDB" : "保存失败", Toast.LENGTH_SHORT).show();
							}
						});
				}
			});
    }

    // ================================================================
    //  设为壁纸 — 需要 SET_WALLPAPER 权限
    // ================================================================
    private void setAsWallpaper() {
        // 检查权限（SET_WALLPAPER是normal权限，不需要运行时申请，但需要Manifest声明）
        final String url = urls.get(cur);
        Toast.makeText(this, "正在设置壁纸...", Toast.LENGTH_SHORT).show();
        exec.execute(new Runnable() {
				@Override public void run() {
					try {
						byte[] data = ImageLoader.get().downloadRaw(url);
						if (data == null) throw new Exception("下载失败");
						Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
						if (bmp == null) throw new Exception("图片解码失败");

						// 裁剪为屏幕比例
						int sw = getResources().getDisplayMetrics().widthPixels;
						int sh = getResources().getDisplayMetrics().heightPixels;
						float bmpRatio = (float) bmp.getWidth() / bmp.getHeight();
						float scrRatio = (float) sw / sh;
						Bitmap cropped;
						if (bmpRatio > scrRatio) {
							int newW = (int)(bmp.getHeight() * scrRatio);
							int x = (bmp.getWidth() - newW) / 2;
							cropped = Bitmap.createBitmap(bmp, x, 0, newW, bmp.getHeight());
						} else {
							int newH = (int)(bmp.getWidth() / scrRatio);
							int y = (bmp.getHeight() - newH) / 2;
							cropped = Bitmap.createBitmap(bmp, 0, y, bmp.getWidth(), newH);
						}

						WallpaperManager wm = WallpaperManager.getInstance(ImageViewerActivity.this);
						wm.setBitmap(cropped);

						main.post(new Runnable() {
								@Override public void run() {
									Toast.makeText(ImageViewerActivity.this,
												   "壁纸设置成功", Toast.LENGTH_SHORT).show();
								}
							});
					} catch (final Exception e) {
						main.post(new Runnable() {
								@Override public void run() {
									Toast.makeText(ImageViewerActivity.this,
												   "设置失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
								}
							});
					}
				}
			});
    }

    // ================================================================
    //  编辑模式
    // ================================================================
    private void openEditor() {
        Bitmap orig = ivMain.getCurrentBitmap();
        if (orig == null) {
            Toast.makeText(this, "图片加载中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        editMode = true;
        Bitmap editBmp = orig.copy(Bitmap.Config.ARGB_8888, true);

        drawView = new DrawView(this, editBmp);
        root.addView(drawView, matchParent());
        drawView.setAlpha(0f);
        drawView.animate().alpha(1f).setDuration(220).start();

        editToolbar = buildEditToolbar(drawView);
        FrameLayout.LayoutParams etp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        etp.gravity = Gravity.BOTTOM;
        root.addView(editToolbar, etp);
        editToolbar.setTranslationY(dp(250));
        editToolbar.animate().translationY(0f).setDuration(320)
            .setInterpolator(new OvershootInterpolator(0.7f)).start();

        if (topBar != null) topBar.animate().alpha(0f).setDuration(200).start();
    }

    private View buildEditToolbar(final DrawView dv) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(0xF0111111);

        // 工具行
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        toolRow.setPadding(dp(8), dp(10), dp(8), dp(4));

        String[] toolNames = {"画笔", "曲线", "直线", "矩形", "裁剪", "文字"};
        int[] toolModes = {DrawView.BRUSH, DrawView.CURVE, DrawView.LINE,
			DrawView.RECT, DrawView.CROP, DrawView.TEXT};
        final int[] activeTool = {0};
        final TextView[] toolBtns = new TextView[toolNames.length];

        for (int i = 0; i < toolNames.length; i++) {
            final int mode = toolModes[i];
            final int idx = i;
            final TextView btn = new TextView(this);
            btn.setText(toolNames[i]);
            btn.setTextColor(i == 0 ? 0xFF90CAF9 : 0xAAFFFFFF);
            btn.setTextSize(12f);
            btn.setBackgroundColor(i == 0 ? 0x33FFFFFF : 0x00000000);
            btn.setPadding(dp(10), dp(7), dp(10), dp(7));
            btn.setGravity(Gravity.CENTER);
            btn.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) {
						dv.setMode(mode);
						for (int j = 0; j < toolBtns.length; j++) {
							toolBtns[j].setTextColor(j == idx ? 0xFF90CAF9 : 0xAAFFFFFF);
							toolBtns[j].setBackgroundColor(j == idx ? 0x33FFFFFF : 0x00000000);
						}
					}
				});
            toolBtns[i] = btn;
            toolRow.addView(btn);
        }
        // 撤销
        TextView tvUndo = new TextView(this);
        tvUndo.setText("↩");
        tvUndo.setTextColor(0xFFFFCC80);
        tvUndo.setTextSize(18f);
        tvUndo.setPadding(dp(12), dp(7), dp(12), dp(7));
        tvUndo.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { dv.undo(); }
			});
        toolRow.addView(tvUndo);
        bar.addView(toolRow);

        // 颜色行
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(dp(12), dp(4), dp(12), dp(4));
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        int[] colors = {0xFFFFFFFF, 0xFFFF5252, 0xFFFF9800, 0xFFFFEB3B,
			0xFF4CAF50, 0xFF2196F3, 0xFF9C27B0, 0xFF000000};
        for (final int color : colors) {
            View dot = new View(this);
            dot.setBackgroundColor(color);
            LinearLayout.LayoutParams dp_ = new LinearLayout.LayoutParams(dp(26), dp(26));
            dp_.setMargins(dp(4), 0, dp(4), 0);
            dot.setLayoutParams(dp_);
            dot.setOnClickListener(new View.OnClickListener() {
					@Override public void onClick(View v) { dv.setColor(color); }
				});
            colorRow.addView(dot);
        }
        bar.addView(colorRow);

        // 粗细 slider
        LinearLayout sliderRow = new LinearLayout(this);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);
        sliderRow.setPadding(dp(16), dp(2), dp(16), dp(4));
        TextView tvSliderLabel = new TextView(this);
        tvSliderLabel.setText("粗细");
        tvSliderLabel.setTextColor(0xAAFFFFFF);
        tvSliderLabel.setTextSize(11f);
        tvSliderLabel.setPadding(0, 0, dp(8), 0);
        sliderRow.addView(tvSliderLabel);
        SeekBar sb = new SeekBar(this);
        sb.setMax(50); sb.setProgress(8);
        sb.setLayoutParams(new LinearLayout.LayoutParams(0,
														 LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override public void onProgressChanged(SeekBar s, int prog, boolean fu) {
					dv.setStrokeWidth(prog + 3);
				}
				@Override public void onStartTrackingTouch(SeekBar s) {}
				@Override public void onStopTrackingTouch(SeekBar s) {}
			});
        sliderRow.addView(sb);
        bar.addView(sliderRow);

        // 操作行
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(dp(8), dp(8), dp(8), dp(20));

        String[] actNames = {"文字字体", "保存", "取消"};
        int[] actColors = {0xFF90CAF9, 0xFF81C784, 0xFFEF9A9A};
        final String[] fonts = {"默认", "衬线体", "等宽体"};
        final int[] selFont = {0};

        View.OnClickListener[] actListeners = new View.OnClickListener[] {
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new AlertDialog.Builder(ImageViewerActivity.this)
                        .setTitle("选择字体")
                        .setSingleChoiceItems(fonts, selFont[0], new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int which) {
                                selFont[0] = which;
                                switch (which) {
                                    case 1: dv.setTextTypeface(Typeface.SERIF); break;
                                    case 2: dv.setTextTypeface(Typeface.MONOSPACE); break;
                                    default: dv.setTextTypeface(Typeface.DEFAULT_BOLD);
                                }
                                d.dismiss();
                            }
                        }).show();
                }
            },
            new View.OnClickListener() {
                @Override public void onClick(View v) {
                    // 如果是裁剪模式，先执行裁剪
                    if (dv.getMode() == DrawView.CROP) {
                        dv.applyCrop();
                        return;
                    }
                    saveEdited(dv);
                }
            },
            new View.OnClickListener() {
                @Override public void onClick(View v) { closeEditor(); }
            }
        };

        for (int i = 0; i < actNames.length; i++) {
            final View.OnClickListener listener = actListeners[i];
            TextView btn = new TextView(this);
            btn.setText(actNames[i]);
            btn.setTextColor(actColors[i]);
            btn.setTextSize(13f);
            btn.setBackgroundColor(0x22FFFFFF);
            btn.setPadding(dp(16), dp(9), dp(16), dp(9));
            btn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams btnp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnp.setMargins(dp(6), 0, dp(6), 0);
            btn.setLayoutParams(btnp);
            btn.setOnClickListener(listener);
            actionRow.addView(btn);
        }

        // 添加文字按钮（在文字模式时点击画面才触发，这里提供快捷入口）
        TextView tvAddText = new TextView(this);
        tvAddText.setText("+ 文字");
        tvAddText.setTextColor(0xFFFFCC80);
        tvAddText.setTextSize(13f);
        tvAddText.setBackgroundColor(0x22FFFFFF);
        tvAddText.setPadding(dp(14), dp(9), dp(14), dp(9));
        tvAddText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams atp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        atp.setMargins(dp(6), 0, dp(6), 0);
        tvAddText.setLayoutParams(atp);
        tvAddText.setOnClickListener(new View.OnClickListener() {
				@Override public void onClick(View v) { showAddTextDialog(dv, selFont); }
			});
        actionRow.addView(tvAddText);
        bar.addView(actionRow);

        return bar;
    }

    private void showAddTextDialog(final DrawView dv, final int[] selFont) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("输入文字");
        et.setTextColor(0xFFEEEEEE);
        et.setHintTextColor(0xFF888888);
        et.setPadding(dp(12), dp(8), dp(12), dp(8));
        new AlertDialog.Builder(this)
            .setTitle("添加文字")
            .setView(et)
            .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) {
                    String txt = et.getText().toString().trim();
                    if (!txt.isEmpty()) {
                        Typeface tf;
                        switch (selFont[0]) {
                            case 1: tf = Typeface.SERIF; break;
                            case 2: tf = Typeface.MONOSPACE; break;
                            default: tf = Typeface.DEFAULT_BOLD;
                        }
                        dv.addText(txt, tf);
                    }
                }
            })
            .setNegativeButton("取消", null).show();
    }

    private void saveEdited(final DrawView dv) {
        exec.execute(new Runnable() {
				@Override public void run() {
					boolean ok = false;
					try {
						Bitmap result = dv.getResult();
						if (result == null) return;
						java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
						result.compress(Bitmap.CompressFormat.JPEG, 95, bos);
						byte[] data = bos.toByteArray();
						String fname = "VNDB_edit_" + new SimpleDateFormat(
							"yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							ContentValues cv = new ContentValues();
							cv.put(MediaStore.Images.Media.DISPLAY_NAME, fname);
							cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
							cv.put(MediaStore.Images.Media.RELATIVE_PATH,
								   Environment.DIRECTORY_PICTURES + "/VNDB");
							Uri uri = getContentResolver().insert(
								MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
							if (uri != null) {
								OutputStream os = getContentResolver().openOutputStream(uri);
								if (os != null) { os.write(data); os.close(); ok = true; }
							}
						} else {
							File dir = new File(Environment.getExternalStoragePublicDirectory(
													Environment.DIRECTORY_PICTURES), "VNDB");
							dir.mkdirs();
							FileOutputStream fos = new FileOutputStream(new File(dir, fname));
							fos.write(data); fos.close(); ok = true;
						}
					} catch (Exception ignored) {}
					final boolean success = ok;
					main.post(new Runnable() {
							@Override public void run() {
								Toast.makeText(ImageViewerActivity.this,
											   success ? "编辑图已保存" : "保存失败", Toast.LENGTH_SHORT).show();
								if (success) closeEditor();
							}
						});
				}
			});
    }

    private void closeEditor() {
        editMode = false;
        if (editToolbar != null) {
            final View et2 = editToolbar;
            et2.animate().translationY(dp(250)).setDuration(240)
                .withEndAction(new Runnable() {
                    @Override public void run() { root.removeView(et2); }
                }).start();
        }
        if (drawView != null) {
            final DrawView dv2 = drawView;
            dv2.animate().alpha(0f).setDuration(200)
                .withEndAction(new Runnable() {
                    @Override public void run() { root.removeView(dv2); }
                }).start();
        }
        if (topBar != null) topBar.animate().alpha(1f).setDuration(200).start();
    }

    // ================================================================
    //  辅助
    // ================================================================
    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onBackPressed() {
        if (editMode) { closeEditor(); return; }
        exitAnim();
    }

    @Override protected void onDestroy() { super.onDestroy(); exec.shutdown(); }

    // ================================================================
    //  ZoomImageView — 无限缩放, 双击, 拖动
    // ================================================================
    public class ZoomImageView extends ImageView {
        private final Matrix matrix = new Matrix();
        private final float[] vals = new float[9];
        private ScaleGestureDetector scaleDet;
        private GestureDetector gestureDet;
        private GestureDetector extDet;
        private float scaleFactor = 1f;
        private float lastX, lastY;
        private boolean scaling = false;
        private Bitmap currentBmp;

        public ZoomImageView(android.content.Context ctx) {
            super(ctx);
            setScaleType(ScaleType.FIT_CENTER);
            initGestures();
        }

        void setExtGesture(GestureDetector gd) { this.extDet = gd; }
        Bitmap getCurrentBitmap() { return currentBmp; }

        @Override public void setImageBitmap(Bitmap bmp) {
            super.setImageBitmap(bmp); currentBmp = bmp;
        }

        private void initGestures() {
            scaleDet = new ScaleGestureDetector(getContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScale(ScaleGestureDetector d) {
                        scaling = true;
                        float newS = Math.max(0.5f, scaleFactor * d.getScaleFactor());
                        float ratio = newS / scaleFactor; scaleFactor = newS;
                        if (getScaleType() != ScaleType.MATRIX) { applyFit(); setScaleType(ScaleType.MATRIX); }
                        matrix.postScale(ratio, ratio, d.getFocusX(), d.getFocusY());
                        clamp(); setImageMatrix(matrix); return true;
                    }
                    @Override public void onScaleEnd(ScaleGestureDetector d) {
                        scaling = false;
                        if (scaleFactor <= 0.55f) exitAnim();
                        else if (scaleFactor < 0.95f) resetZoom();
                    }
                });

            gestureDet = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDoubleTap(MotionEvent e) {
                        if (scaleFactor > 1.2f) resetZoom();
                        else {
                            applyFit(); setScaleType(ScaleType.MATRIX);
                            matrix.postScale(2.6f, 2.6f, e.getX(), e.getY());
                            scaleFactor = 2.6f; clamp(); setImageMatrix(matrix);
                        }
                        return true;
                    }
                });
        }

        boolean isZoomed() { return scaleFactor > 1.08f; }

        void resetZoom() {
            scaleFactor = 1f; setScaleType(ScaleType.FIT_CENTER);
        }

        private void applyFit() {
            if (getDrawable() == null) return;
            float dw = getDrawable().getIntrinsicWidth();
            float dh = getDrawable().getIntrinsicHeight();
            float vw = getWidth(), vh = getHeight();
            if (dw <= 0 || dh <= 0 || vw <= 0 || vh <= 0) return;
            float s = Math.min(vw/dw, vh/dh);
            matrix.reset(); matrix.postScale(s, s);
            matrix.postTranslate((vw - dw*s)/2f, (vh - dh*s)/2f);
        }

        private void clamp() {
            if (getDrawable() == null) return;
            float dw = getDrawable().getIntrinsicWidth();
            float dh = getDrawable().getIntrinsicHeight();
            float vw = getWidth(), vh = getHeight();
            matrix.getValues(vals);
            float cs = vals[Matrix.MSCALE_X];
            float sw = dw*cs, sh = dh*cs;
            float tx = vals[Matrix.MTRANS_X], ty = vals[Matrix.MTRANS_Y];
            vals[Matrix.MTRANS_X] = sw<=vw ? (vw-sw)/2f : Math.min(0f, Math.max(vw-sw, tx));
            vals[Matrix.MTRANS_Y] = sh<=vh ? (vh-sh)/2f : Math.min(0f, Math.max(vh-sh, ty));
            matrix.setValues(vals);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            scaleDet.onTouchEvent(e);
            gestureDet.onTouchEvent(e);
            if (extDet != null) extDet.onTouchEvent(e);

            if (!scaling && scaleFactor > 1.05f) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: lastX = e.getX(); lastY = e.getY(); break;
                    case MotionEvent.ACTION_MOVE:
                        if (e.getPointerCount() == 1) {
                            matrix.postTranslate(e.getX()-lastX, e.getY()-lastY);
                            clamp(); setImageMatrix(matrix); setScaleType(ScaleType.MATRIX);
                        }
                        lastX = e.getX(); lastY = e.getY(); break;
                }
            }
            return true;
        }
    }

    // ================================================================
    //  DrawView — 完整绘图层
    // ================================================================
    static class DrawView extends android.view.View {
        static final int BRUSH = 0, CURVE = 1, LINE = 2, RECT = 3, CROP = 4, TEXT = 5;

        private int mode = BRUSH;
        private final Bitmap base;
        private final Paint brushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 绘制历史
        private final java.util.List<Object[]> history = new java.util.ArrayList<>();
        // [{type, path/text/rect, paint/color/typeface, ...}]

        private Path   currentPath;
        private float  startX, startY, lastX, lastY;

        // 裁剪框
        private float cropX1, cropY1, cropX2, cropY2;
        private boolean cropActive = false;

        // 文字
        private Typeface textTypeface = Typeface.DEFAULT_BOLD;

        private final Matrix viewMatrix = new Matrix();

        DrawView(android.content.Context ctx, Bitmap bmp) {
            super(ctx);
            this.base = bmp;
            brushPaint.setColor(0xFFFFFFFF);
            brushPaint.setStrokeWidth(10f);
            brushPaint.setStyle(Paint.Style.STROKE);
            brushPaint.setStrokeCap(Paint.Cap.ROUND);
            brushPaint.setStrokeJoin(Paint.Join.ROUND);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(48f);
        }

        void setMode(int m) { mode = m; if (m != CROP) cropActive = false; invalidate(); }
        int  getMode()      { return mode; }
        void setColor(int c) { brushPaint.setColor(c); textPaint.setColor(c); }
        void setStrokeWidth(float w) { brushPaint.setStrokeWidth(w); }
        void setTextTypeface(Typeface tf) { textTypeface = tf; textPaint.setTypeface(tf); }

        void addText(String text, Typeface tf) {
            textPaint.setTypeface(tf);
            Paint p = new Paint(textPaint);
            history.add(new Object[]{"text", text, p, getWidth()/2f, getHeight()/2f});
            invalidate();
        }

        void undo() {
            if (!history.isEmpty()) {
                history.remove(history.size() - 1);
                invalidate();
            }
        }

        void applyCrop() {
            if (!cropActive) return;
            float sx = (float) base.getWidth() / getWidth();
            float sy = (float) base.getHeight() / getHeight();
            float x1 = Math.min(cropX1, cropX2) * sx;
            float y1 = Math.min(cropY1, cropY2) * sy;
            float x2 = Math.max(cropX1, cropX2) * sx;
            float y2 = Math.max(cropY1, cropY2) * sy;
            int w = (int)(x2 - x1), h = (int)(y2 - y1);
            if (w > 10 && h > 10) {
                // 应用裁剪到 base
                Bitmap cropped = Bitmap.createBitmap(base, (int)x1, (int)y1, w, h);
                Canvas c = new Canvas(base);
                c.drawColor(0xFF000000);
                c.drawBitmap(cropped, 0, 0, null);
                cropActive = false; history.clear();
                invalidate();
            }
        }

        Bitmap getResult() {
            Bitmap result = base.copy(Bitmap.Config.ARGB_8888, true);
            Canvas c = new Canvas(result);
            float sx = (float) result.getWidth() / getWidth();
            float sy = (float) result.getHeight() / getHeight();

            for (Object[] item : history) {
                String type = (String) item[0];
                if ("path".equals(type) || "line".equals(type)) {
                    Path orig = (Path) item[1];
                    Paint p = new Paint((Paint) item[2]);
                    p.setStrokeWidth(p.getStrokeWidth() * sx);
                    Matrix m = new Matrix(); m.setScale(sx, sy);
                    Path scaled = new Path(); orig.transform(m, scaled);
                    c.drawPath(scaled, p);
                } else if ("rect_shape".equals(type)) {
                    float[] coords = (float[]) item[1];
                    Paint p = new Paint((Paint) item[2]);
                    p.setStrokeWidth(p.getStrokeWidth() * sx);
                    c.drawRect(coords[0]*sx, coords[1]*sy, coords[2]*sx, coords[3]*sy, p);
                } else if ("text".equals(type)) {
                    String text = (String) item[1];
                    Paint p = new Paint((Paint) item[2]);
                    p.setTextSize(p.getTextSize() * sx);
                    float tx = (float) item[3] * sx;
                    float ty = (float) item[4] * sy;
                    c.drawText(text, tx, ty, p);
                }
            }
            return result;
        }

        @Override protected void onDraw(Canvas c) {
            // 绘制底图
            float scale = Math.min((float)getWidth()/base.getWidth(),
                                   (float)getHeight()/base.getHeight());
            float dx = (getWidth()  - base.getWidth()  * scale) / 2f;
            float dy = (getHeight() - base.getHeight() * scale) / 2f;
            viewMatrix.setScale(scale, scale);
            viewMatrix.postTranslate(dx, dy);
            c.drawBitmap(base, viewMatrix, null);

            // 绘制历史
            for (Object[] item : history) {
                String type = (String) item[0];
                if ("path".equals(type) || "line".equals(type)) {
                    c.drawPath((Path) item[1], (Paint) item[2]);
                } else if ("rect_shape".equals(type)) {
                    float[] coords = (float[]) item[1];
                    c.drawRect(coords[0], coords[1], coords[2], coords[3], (Paint) item[2]);
                } else if ("text".equals(type)) {
                    Paint p = (Paint) item[2];
                    c.drawText((String) item[1], (float) item[3], (float) item[4], p);
                }
            }

            // 当前路径
            if (currentPath != null) c.drawPath(currentPath, brushPaint);

            // 裁剪框
            if (mode == CROP && cropActive) {
                Paint cp = new Paint();
                cp.setColor(0x88FFFFFF); cp.setStyle(Paint.Style.STROKE);
                cp.setStrokeWidth(2f); cp.setPathEffect(new android.graphics.DashPathEffect(
															new float[]{10, 5}, 0));
                c.drawRect(Math.min(cropX1,cropX2), Math.min(cropY1,cropY2),
                           Math.max(cropX1,cropX2), Math.max(cropY1,cropY2), cp);
                // 半透明遮罩
                Paint mask = new Paint(); mask.setColor(0x44000000);
                c.drawRect(0, 0, getWidth(), Math.min(cropY1,cropY2), mask);
                c.drawRect(0, Math.max(cropY1,cropY2), getWidth(), getHeight(), mask);
                c.drawRect(0, Math.min(cropY1,cropY2), Math.min(cropX1,cropX2), Math.max(cropY1,cropY2), mask);
                c.drawRect(Math.max(cropX1,cropX2), Math.min(cropY1,cropY2), getWidth(), Math.max(cropY1,cropY2), mask);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = x; startY = y; lastX = x; lastY = y;
                    if (mode == BRUSH || mode == CURVE) {
                        currentPath = new Path(); currentPath.moveTo(x, y);
                    } else if (mode == CROP) {
                        cropX1 = x; cropY1 = y; cropActive = true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mode == BRUSH && currentPath != null) {
                        currentPath.lineTo(x, y);
                    } else if (mode == CURVE && currentPath != null) {
                        currentPath.quadTo(lastX, lastY, (x+lastX)/2, (y+lastY)/2);
                    } else if (mode == CROP) {
                        cropX2 = x; cropY2 = y;
                    } else if (mode == LINE || mode == RECT) {
                        // 实时预览在 draw 里处理
                    }
                    lastX = x; lastY = y;
                    invalidate(); break;
                case MotionEvent.ACTION_UP:
                    if (mode == BRUSH || mode == CURVE) {
                        if (currentPath != null) {
                            history.add(new Object[]{"path", currentPath, new Paint(brushPaint)});
                            currentPath = null;
                        }
                    } else if (mode == LINE) {
                        Path lp = new Path();
                        lp.moveTo(startX, startY); lp.lineTo(x, y);
                        history.add(new Object[]{"line", lp, new Paint(brushPaint)});
                    } else if (mode == RECT) {
                        Paint rp = new Paint(brushPaint); rp.setStyle(Paint.Style.STROKE);
                        history.add(new Object[]{"rect_shape",
										new float[]{startX, startY, x, y}, rp});
                    } else if (mode == TEXT) {
                        // 在触摸位置添加文字（使用对话框）
                        final float tx = x, ty = y;
                        final android.widget.EditText et = new android.widget.EditText(getContext());
                        et.setHint("输入文字");
                        new android.app.AlertDialog.Builder(getContext())
                            .setTitle("添加文字").setView(et)
                            .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface d, int w) {
                                    String txt = et.getText().toString().trim();
                                    if (!txt.isEmpty()) {
                                        textPaint.setTypeface(textTypeface);
                                        history.add(new Object[]{"text", txt,
														new Paint(textPaint), tx, ty});
                                        invalidate();
                                    }
                                }
                            }).setNegativeButton("取消", null).show();
                    }
                    invalidate(); break;
            }
            return true;
        }
    }
}


