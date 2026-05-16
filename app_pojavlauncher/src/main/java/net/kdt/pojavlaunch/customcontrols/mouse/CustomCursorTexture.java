package net.kdt.pojavlaunch.customcontrols.mouse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class CustomCursorTexture {
    public static final String PREF_KEY = "custom_virtual_cursor_texture";
    private static final String CURSOR_FILE_NAME = "custom_virtual_cursor_texture";

    private CustomCursorTexture() {}

    public static File getCursorFile(Context context) {
        return new File(context.getFilesDir(), CURSOR_FILE_NAME);
    }

    public static void saveCursorTexture(Context context, Uri uri) throws IOException {
        File cursorFile = getCursorFile(context);
        File temporaryFile = new File(context.getFilesDir(), CURSOR_FILE_NAME + ".tmp");
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(temporaryFile, false)) {
            if (inputStream == null) throw new IOException("Unable to open selected image.");
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        }
        if (Drawable.createFromPath(temporaryFile.getAbsolutePath()) == null) {
            if (!temporaryFile.delete()) temporaryFile.deleteOnExit();
            throw new IOException("The selected file is not a supported image.");
        }
        if (!temporaryFile.renameTo(cursorFile)) {
            if (!temporaryFile.delete()) temporaryFile.deleteOnExit();
            throw new IOException("Unable to save selected image.");
        }
    }

    public static boolean hasCustomCursorTexture(Context context) {
        File cursorFile = getCursorFile(context);
        return cursorFile.isFile() && cursorFile.length() > 0;
    }

    public static Drawable loadCursorDrawable(Context context) {
        Drawable customDrawable = loadCustomCursorDrawable(context);
        if (customDrawable != null) return customDrawable;
        Drawable defaultDrawable = ResourcesCompat.getDrawable(
                context.getResources(),
                R.drawable.ic_mouse_pointer,
                context.getTheme());
        if (defaultDrawable == null) {
            throw new IllegalStateException("Default cursor drawable is missing.");
        }
        return defaultDrawable;
    }

    @Nullable
    private static Drawable loadCustomCursorDrawable(Context context) {
        if (!hasCustomCursorTexture(context)) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeFile(getCursorFile(context).getAbsolutePath(), options);
        if (bitmap == null) return null;
        bitmap.setHasAlpha(true);
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setDither(true);
        drawable.setFilterBitmap(false);
        return drawable;
    }
}
