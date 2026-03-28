package com.dvlo.vndbapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {

    private static ImageLoader instance;
    private final ExecutorService exec = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    private final Map<String, Bitmap> memCache = Collections.synchronizedMap(
        new LinkedHashMap<String, Bitmap>(40, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
                return size() > 40;
            }
        });

    private final Map<String, Bitmap> hdMem = Collections.synchronizedMap(
        new LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
                return size() > 8;
            }
        });

    private File diskDir;

    public interface Callback {
        void onLoaded(Bitmap bmp);
    }

    public static ImageLoader get() {
        if (instance == null) instance = new ImageLoader();
        return instance;
    }

    public void init(Context ctx) {
        diskDir = new File(ctx.getCacheDir(), "img_v3");
        if (!diskDir.exists()) diskDir.mkdirs();
    }

    /** 异步加载缩略图（内存→磁盘→网络），自动淡入 */
    public void load(final String url, final ImageView iv, final int placeholder) {
        if (url == null || url.isEmpty()) return;
        iv.setTag(url);

        Bitmap mem = memCache.get(url);
        if (mem != null) {
            iv.setImageBitmap(mem);
            iv.setBackgroundColor(0);
            return;
        }

        iv.setBackgroundColor(placeholder);
        iv.setImageBitmap(null);

        final WeakReference<ImageView> ref = new WeakReference<>(iv);
        exec.execute(new Runnable() {
				@Override public void run() {
					// 缩略图用最长边 900px，RGB_565 省内存
					final Bitmap bmp = fetchSampled(url, 900, false);
					if (bmp != null) memCache.put(url, bmp);
					main.post(new Runnable() {
							@Override public void run() {
								ImageView v = ref.get();
								if (v == null || !url.equals(v.getTag())) return;
								if (bmp != null) {
									v.setBackgroundColor(0);
									v.setImageBitmap(bmp);
									v.setAlpha(0f);
									v.animate().alpha(1f).setDuration(260).start();
								}
							}
						});
				}
			});
    }

    /** 异步加载高清原图，ARGB_8888，最长边 2048px，有磁盘缓存不模糊 */
    public void loadHDAsync(final String url, final Callback cb) {
        if (url == null || url.isEmpty()) { if (cb != null) cb.onLoaded(null); return; }

        final String hdKey = "hd:" + url;
        Bitmap mem = hdMem.get(hdKey);
        if (mem != null) { if (cb != null) cb.onLoaded(mem); return; }

        exec.execute(new Runnable() {
				@Override public void run() {
					final Bitmap bmp = fetchSampled(url, 2048, true);
					if (bmp != null) hdMem.put(hdKey, bmp);
					main.post(new Runnable() {
							@Override public void run() {
								if (cb != null) cb.onLoaded(bmp);
							}
						});
				}
			});
    }

    /** 同步下载原始字节（下载保存/壁纸用） */
    public byte[] downloadRaw(String url) {
        try {
            HttpURLConnection conn = openConn(new URL(url));
            if (conn.getResponseCode() != 200) { conn.disconnect(); return null; }
            byte[] data = readBytes(conn.getInputStream());
            conn.disconnect();
            return data;
        } catch (Exception e) { return null; }
    }

    public long getDiskCacheSize() {
        if (diskDir == null || !diskDir.exists()) return 0;
        long total = 0;
        File[] files = diskDir.listFiles();
        if (files != null) for (File f : files) total += f.length();
        return total;
    }

    public void clearAll() {
        memCache.clear();
        hdMem.clear();
        if (diskDir != null && diskDir.exists()) {
            File[] files = diskDir.listFiles();
            if (files != null) for (File f : files) f.delete();
        }
    }

    // ----------------------------------------------------------------
    //  两阶段采样解码：先读尺寸算 inSampleSize，再正式解码
    //  hd=true → ARGB_8888（高清），hd=false → RGB_565（省内存）
    // ----------------------------------------------------------------
    private Bitmap fetchSampled(String urlStr, int maxEdge, boolean hd) {
        byte[] data = readDisk(urlStr);
        if (data == null) {
            try {
                HttpURLConnection conn = openConn(new URL(urlStr));
                if (conn.getResponseCode() != 200) { conn.disconnect(); return null; }
                data = readBytes(conn.getInputStream());
                conn.disconnect();
                if (data != null) writeDisk(urlStr, data);
            } catch (Exception e) { return null; }
        }
        if (data == null || data.length == 0) return null;

        // 阶段1：只读尺寸
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);

        // 计算 inSampleSize
        int sample = 1;
        if (maxEdge > 0 && o.outWidth > 0 && o.outHeight > 0) {
            int longest = Math.max(o.outWidth, o.outHeight);
            while (longest / (sample * 2) > maxEdge) sample *= 2;
        }

        // 阶段2：正式解码
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = hd ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    private String diskKey(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(url.getBytes("UTF-8"));
            return new BigInteger(1, md.digest()).toString(16);
        } catch (Exception e) { return String.valueOf(url.hashCode()); }
    }

    private byte[] readDisk(String url) {
        if (diskDir == null) return null;
        File f = new File(diskDir, diskKey(url));
        if (!f.exists()) return null;
        try {
            FileInputStream fis = new FileInputStream(f);
            byte[] data = readBytes(fis);
            fis.close();
            return data;
        } catch (Exception e) { return null; }
    }

    private void writeDisk(String url, byte[] data) {
        if (diskDir == null || data == null) return;
        try {
            FileOutputStream fos = new FileOutputStream(new File(diskDir, diskKey(url)));
            fos.write(data);
            fos.close();
        } catch (Exception ignored) {}
    }

    private HttpURLConnection openConn(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("User-Agent", "VNDBApp/3.0");
        conn.connect();
        return conn;
    }

    private byte[] readBytes(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) { return null; }
    }

    public void shutdown() { exec.shutdown(); }
}

