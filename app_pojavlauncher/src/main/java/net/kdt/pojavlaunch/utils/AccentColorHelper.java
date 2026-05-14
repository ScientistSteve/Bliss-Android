package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.core.graphics.drawable.DrawableCompat;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public final class AccentColorHelper {
    private static final String PREF_ACCENT_COLOR = "pref_ui_accent_color";
    private static final int DEFAULT_ACCENT = Color.parseColor("#9649b8");

    private AccentColorHelper() {}

    public static int getAccentColor(Context context) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF != null
                ? LauncherPreferences.DEFAULT_PREF
                : context.getSharedPreferences("pojav_extract", Context.MODE_PRIVATE);
        return prefs.getInt(PREF_ACCENT_COLOR, DEFAULT_ACCENT);
    }

    public static void setAccentColor(Context context, int color) {
        SharedPreferences prefs = LauncherPreferences.DEFAULT_PREF != null
                ? LauncherPreferences.DEFAULT_PREF
                : context.getSharedPreferences("pojav_extract", Context.MODE_PRIVATE);
        prefs.edit().putInt(PREF_ACCENT_COLOR, color).apply();
    }

    public static void tintProgressBar(ProgressBar progressBar, int color) {
        if (progressBar == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(color));
        }
        Drawable d = progressBar.getProgressDrawable();
        if (d != null) {
            d = DrawableCompat.wrap(d.mutate());
            DrawableCompat.setTint(d, color);
            progressBar.setProgressDrawable(d);
        }
    }

    public static void tintImageView(ImageView imageView, int color) {
        if (imageView != null) imageView.setColorFilter(color);
    }
}
