package com.dvlo.vndbapp;

import android.content.Context;
import android.content.res.Configuration;

/**
 * Material Design 3 主题工具
 * 完全跟随系统深色 / 亮色模式，minSdk 21 兼容
 */
public class Theme {

    public static boolean isDark(Context ctx) {
        int night = ctx.getResources().getConfiguration().uiMode
			& Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    // ── Background / Surface ─────────────────────────────────────────────
    public static int bg(Context ctx) {
        return isDark(ctx) ? 0xFF1C1B1F : 0xFFFFFBFE;
    }
    public static int surface(Context ctx) {
        return isDark(ctx) ? 0xFF1C1B1F : 0xFFFFFBFE;
    }
    public static int surfaceVar(Context ctx) {
        return isDark(ctx) ? 0xFF49454F : 0xFFE7E0EC;
    }
    /** Surface Container — 卡片、对话框 */
    public static int container(Context ctx) {
        return isDark(ctx) ? 0xFF211F26 : 0xFFF3EDF7;
    }
    /** Surface Container High — 略高层级卡片 */
    public static int containerHigh(Context ctx) {
        return isDark(ctx) ? 0xFF2B2930 : 0xFFECE6F0;
    }
    /** Surface Container Highest */
    public static int containerHighest(Context ctx) {
        return isDark(ctx) ? 0xFF36343B : 0xFFE6E0E9;
    }

    // ── Primary ──────────────────────────────────────────────────────────
    public static int primary(Context ctx) {
        return isDark(ctx) ? 0xFFD0BCFF : 0xFF6650A4;
    }
    public static int onPrimary(Context ctx) {
        return isDark(ctx) ? 0xFF381E72 : 0xFFFFFFFF;
    }
    public static int primaryCont(Context ctx) {
        return isDark(ctx) ? 0xFF4F378B : 0xFFEADDFF;
    }
    public static int onPrimaryCont(Context ctx) {
        return isDark(ctx) ? 0xFFEADDFF : 0xFF21005D;
    }

    // ── Secondary ────────────────────────────────────────────────────────
    public static int secondary(Context ctx) {
        return isDark(ctx) ? 0xFFCCC2DC : 0xFF625B71;
    }
    public static int onSecondary(Context ctx) {
        return isDark(ctx) ? 0xFF332D41 : 0xFFFFFFFF;
    }
    public static int secondaryCont(Context ctx) {
        return isDark(ctx) ? 0xFF4A4458 : 0xFFE8DEF8;
    }
    public static int onSecondaryCont(Context ctx) {
        return isDark(ctx) ? 0xFFE8DEF8 : 0xFF1D192B;
    }

    // ── Tertiary ─────────────────────────────────────────────────────────
    public static int tertiary(Context ctx) {
        return isDark(ctx) ? 0xFFEFB8C8 : 0xFF7D5260;
    }
    public static int tertiaryCont(Context ctx) {
        return isDark(ctx) ? 0xFF633B48 : 0xFFFFD8E4;
    }
    public static int onTertiaryCont(Context ctx) {
        return isDark(ctx) ? 0xFFFFD8E4 : 0xFF31111D;
    }

    // ── Text / On-surface ────────────────────────────────────────────────
    public static int onBg(Context ctx) {
        return isDark(ctx) ? 0xFFE6E1E5 : 0xFF1C1B1F;
    }
    public static int onSurface(Context ctx) {
        return isDark(ctx) ? 0xFFE6E1E5 : 0xFF1C1B1F;
    }
    public static int onSurfaceVar(Context ctx) {
        return isDark(ctx) ? 0xFFCAC4D0 : 0xFF49454F;
    }
    public static int outline(Context ctx) {
        return isDark(ctx) ? 0xFF938F99 : 0xFF79747E;
    }
    public static int outlineVar(Context ctx) {
        return isDark(ctx) ? 0xFF49454F : 0xFFCAC4D0;
    }

    // ── Error ────────────────────────────────────────────────────────────
    public static int error(Context ctx) {
        return isDark(ctx) ? 0xFFFFB4AB : 0xFFB3261E;
    }
    public static int onError(Context ctx) {
        return isDark(ctx) ? 0xFF690005 : 0xFFFFFFFF;
    }
    public static int errorCont(Context ctx) {
        return isDark(ctx) ? 0xFF93000A : 0xFFF9DEDC;
    }
    public static int onErrorCont(Context ctx) {
        return isDark(ctx) ? 0xFFFFDAD6 : 0xFF410E0B;
    }

    // ── 评分颜色（跟随主题） ──────────────────────────────────────────────
    public static int ratingColor(Context ctx, double rating) {
        if (rating >= 80) return isDark(ctx) ? 0xFFFFD700 : 0xFFB8860B; // 金色
        if (rating >= 65) return isDark(ctx) ? 0xFF81C995 : 0xFF2E7D32; // 绿色
        if (rating >= 50) return primary(ctx);                           // 主色
        return error(ctx);                                               // 红色
    }
}

