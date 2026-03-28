package com.dvlo.vndbapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Random;

public class SplashActivity extends Activity {

    private static final int SPLASH_MS = 2600;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ParticleView pv;

    // 所有动画目标声明为 final 字段，避免匿名类中非 final 引用错误
    private ImageView ivLogo;
    private TextView  tvTitle;
    private TextView  tvSub;
    private View      goldLine;
    private TextView  tvVer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0D0A08);

        // 粒子背景
        pv = new ParticleView(this);
        root.addView(pv, new FrameLayout.LayoutParams(
						 FrameLayout.LayoutParams.MATCH_PARENT,
						 FrameLayout.LayoutParams.MATCH_PARENT));

        // Logo —— drawable/vndb.png, 显示为 192x192 px (设备独立单位约 64dp)
        ivLogo = new ImageView(this);
        try {
            int resId = getResources().getIdentifier("vndb", "drawable", getPackageName());
            if (resId != 0) ivLogo.setImageResource(resId);
        } catch (Exception ignored) {}
        ivLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // 192px ≈ 64dp on xxhdpi (3x)，保持真实像素 192x192
        FrameLayout.LayoutParams logoP = new FrameLayout.LayoutParams(192, 192);
        logoP.gravity = Gravity.CENTER;
        logoP.bottomMargin = dp(72);
        ivLogo.setLayoutParams(logoP);
        ivLogo.setAlpha(0f);
        root.addView(ivLogo);

        // 主标题
        tvTitle = new TextView(this);
        tvTitle.setText("VNDB");
        tvTitle.setTextColor(0xFFF0E8D8);
        tvTitle.setTextSize(38f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setLetterSpacing(0.25f);
        tvTitle.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleP = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        titleP.gravity = Gravity.CENTER;
        titleP.topMargin = dp(72);
        tvTitle.setLayoutParams(titleP);
        tvTitle.setAlpha(0f);
        root.addView(tvTitle);

        // 金分割线
        goldLine = new View(this);
        goldLine.setBackgroundColor(0xFFC8993A);
        FrameLayout.LayoutParams lineP = new FrameLayout.LayoutParams(dp(48), dp(2));
        lineP.gravity = Gravity.CENTER;
        lineP.topMargin = dp(118);
        goldLine.setLayoutParams(lineP);
        goldLine.setAlpha(0f);
        goldLine.setScaleX(0f);
        root.addView(goldLine);

        // 副标题
        tvSub = new TextView(this);
        tvSub.setText("视觉小说数据库");
        tvSub.setTextColor(0xFF6B4F1A);
        tvSub.setTextSize(13f);
        tvSub.setLetterSpacing(0.3f);
        tvSub.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams subP = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        subP.gravity = Gravity.CENTER;
        subP.topMargin = dp(136);
        tvSub.setLayoutParams(subP);
        tvSub.setAlpha(0f);
        root.addView(tvSub);

        // 版本号
        tvVer = new TextView(this);
        tvVer.setText("v3.0");
        tvVer.setTextColor(0xFF3A2A18);
        tvVer.setTextSize(11f);
        tvVer.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams verP = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT);
        verP.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        verP.bottomMargin = dp(30);
        tvVer.setLayoutParams(verP);
        root.addView(tvVer);

        setContentView(root);
        startAnimSequence();
    }

    private void startAnimSequence() {
        // 1. Logo 弹入
        h.postDelayed(new Runnable() {
				@Override public void run() {
					ivLogo.setScaleX(0.3f);
					ivLogo.setScaleY(0.3f);
					ivLogo.animate().alpha(1f).scaleX(1f).scaleY(1f)
						.setDuration(520)
						.setInterpolator(new OvershootInterpolator(1.3f))
						.start();
				}
			}, 80);

        // 2. 金线展开
        h.postDelayed(new Runnable() {
				@Override public void run() {
					goldLine.animate().alpha(1f).scaleX(1f)
						.setDuration(360)
						.setInterpolator(new DecelerateInterpolator())
						.start();
				}
			}, 340);

        // 3. 标题滑入
        h.postDelayed(new Runnable() {
				@Override public void run() {
					tvTitle.setTranslationY(dp(18));
					tvTitle.animate().alpha(1f).translationY(0f)
						.setDuration(400)
						.setInterpolator(new DecelerateInterpolator(1.4f))
						.start();
				}
			}, 460);

        // 4. 副标题
        h.postDelayed(new Runnable() {
				@Override public void run() {
					tvSub.setTranslationY(dp(14));
					tvSub.animate().alpha(1f).translationY(0f)
						.setDuration(360)
						.setInterpolator(new DecelerateInterpolator())
						.start();
				}
			}, 600);

        // 5. 跳转
        h.postDelayed(new Runnable() {
				@Override public void run() {
					ImageLoader.get().init(SplashActivity.this);
					startActivity(new Intent(SplashActivity.this, MainActivity.class));
					overridePendingTransition(R.anim.fade_in, R.anim.slide_down_exit);
					finish();
				}
			}, SPLASH_MS);
    }

    int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        if (pv != null) pv.stop();
    }

    // =====================================================================
    //  ParticleView — Canvas 粒子光线，全部变量 final 或局部
    // =====================================================================
    static class ParticleView extends SurfaceView implements SurfaceHolder.Callback {

        private Thread renderThread;
        private volatile boolean running = false;
        private final Random rnd = new Random();

        private static final int NP = 55, NL = 18;
        private float[] px, py, pvx, pvy, pa, pr;
        private float[] lx1, ly1, lx2, ly2, la, ls;
        private int[]   lc;
        private int W = 1080, H = 1920;

        public ParticleView(android.content.Context ctx) {
            super(ctx);
            getHolder().addCallback(this);
        }

        @Override public void surfaceCreated(SurfaceHolder holder) {
            W = Math.max(getWidth(), 1);
            H = Math.max(getHeight(), 1);
            initAll();
            running = true;
            renderThread = new Thread(new Runnable() {
					@Override public void run() { loop(); }
				});
            renderThread.start();
        }

        @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
            W = w; H = hh;
        }

        @Override public void surfaceDestroyed(SurfaceHolder h) { stop(); }

        void stop() {
            running = false;
            try { if (renderThread != null) renderThread.join(400); } catch (Exception ignored) {}
        }

        private void initAll() {
            px = new float[NP]; py = new float[NP]; pvx = new float[NP];
            pvy = new float[NP]; pa = new float[NP]; pr = new float[NP];
            for (int i = 0; i < NP; i++) resetP(i, true);

            lx1 = new float[NL]; ly1 = new float[NL]; lx2 = new float[NL];
            ly2 = new float[NL]; la = new float[NL]; ls = new float[NL];
            lc = new int[NL];
            for (int i = 0; i < NL; i++) resetL(i, true);
        }

        private void resetP(int i, boolean init) {
            px[i]  = rnd.nextFloat() * W;
            py[i]  = init ? rnd.nextFloat() * H : H + 10;
            pvx[i] = (rnd.nextFloat() - 0.5f) * 0.6f;
            pvy[i] = -(0.3f + rnd.nextFloat() * 0.8f);
            pa[i]  = 0.2f + rnd.nextFloat() * 0.6f;
            pr[i]  = 1.2f + rnd.nextFloat() * 2.8f;
        }

        private void resetL(int i, boolean init) {
            lx1[i] = rnd.nextFloat() * W;
            ly1[i] = init ? rnd.nextFloat() * H : H + rnd.nextInt(200);
            float len = 40 + rnd.nextFloat() * 120;
            float ang = (float)(Math.PI * (0.4 + rnd.nextFloat() * 0.2));
            lx2[i] = lx1[i] + (float)(Math.cos(ang) * len * (rnd.nextBoolean() ? 1 : -1));
            ly2[i] = ly1[i] - (float)(Math.sin(ang) * len);
            la[i]  = 0.05f + rnd.nextFloat() * 0.18f;
            ls[i]  = 0.4f + rnd.nextFloat() * 1.0f;
            int[] colors = {0xFFC8993A, 0xFF8B1A1A, 0xFF6B4F1A, 0xFFF0E8D8};
            lc[i] = colors[rnd.nextInt(colors.length)];
        }

        private void loop() {
            Paint bg = new Paint();
            Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
            lp.setStrokeWidth(1.2f); lp.setStyle(Paint.Style.STROKE);
            Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);

            while (running) {
                long t0 = System.currentTimeMillis();
                SurfaceHolder holder = getHolder();
                Canvas c = null;
                try {
                    c = holder.lockCanvas();
                    if (c == null) continue;
                    bg.setColor(0xDE0D0A08); c.drawRect(0, 0, W, H, bg);

                    RadialGradient glow = new RadialGradient(W / 2f, H / 2f, H * 0.55f,
															 new int[]{0x22C8993A, 0x008B1A1A, 0x000D0A08},
															 new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                    gp.setShader(glow);
                    c.drawRect(0, 0, W, H, gp);
                    gp.setShader(null);

                    for (int i = 0; i < NL; i++) {
                        ly1[i] -= ls[i]; ly2[i] -= ls[i];
                        la[i] -= 0.0008f;
                        if (ly2[i] < -50 || la[i] <= 0) resetL(i, false);
                        int a = Math.max(0, Math.min(255, (int)(la[i] * 255)));
                        lp.setColor((lc[i] & 0x00FFFFFF) | (a << 24));
                        c.drawLine(lx1[i], ly1[i], lx2[i], ly2[i], lp);
                    }

                    for (int i = 0; i < NP; i++) {
                        px[i] += pvx[i]; py[i] += pvy[i]; pa[i] -= 0.0015f;
                        if (py[i] < -10 || pa[i] <= 0) resetP(i, false);
                        int a = Math.max(0, Math.min(255, (int)(pa[i] * 255)));
                        int col = (i % 3 == 0) ? 0xFFC8993A : (i % 3 == 1) ? 0xFF6B4F1A : 0xFF8B2A2A;
                        pp.setColor((col & 0x00FFFFFF) | (a << 24));
                        c.drawCircle(px[i], py[i], pr[i], pp);
                    }
                } finally {
                    if (c != null) try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) {}
                }
                long sleep = 16 - (System.currentTimeMillis() - t0);
                if (sleep > 0) try { Thread.sleep(sleep); } catch (Exception ignored) {}
            }
        }
    }
}

