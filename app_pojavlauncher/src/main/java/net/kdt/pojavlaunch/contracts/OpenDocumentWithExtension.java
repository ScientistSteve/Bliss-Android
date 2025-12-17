package net.kdt.pojavlaunch.contracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Objects;

// Android's OpenDocument contract is the basicmost crap that doesn't allow
// you to specify practically anything. So i made this instead.
public class OpenDocumentWithExtension extends ActivityResultContract<Object, Uri> {
    private final String mimeType;
    private final String[] mimeTypes;

    /**
     * Create a new OpenDocumentWithExtension contract.
     * If the extension provided to the constructor is not available in the device's MIME
     * type database, the filter will default to "all types"
     * @param extension the extension to filter by
     */
    public OpenDocumentWithExtension(String extension) {
        String extensionMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if(extensionMimeType == null) extensionMimeType = "*/*";
        mimeType = extensionMimeType;
        mimeTypes = null;
    }
    public OpenDocumentWithExtension(String[] extensions) {
        ArrayList<String> extensionsMimeType = new ArrayList<>();
        int count = 0;
        for (String extension: extensions) {
            String extensionMimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            // This somehow works despite finding no code showing where modrinth was defined.
            // Guessing that just DocumentsUI supports this, if someone files a bug report saying
            // they can't select mrpacks with DocumentsUI, remove this and just don't filter at all.
            if (Objects.equals(extension, "mrpack")) extensionMimetype = "application/x-modrinth-modpack+zip";
            if(extensionMimetype == null) continue; // If null is passed, it matches all files
            extensionsMimeType.add(extensionMimetype);
            count++;
        }
        mimeType = "*/*";
        mimeTypes = extensionsMimeType.toArray(new String[0]);
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull Context context, @NonNull Object input) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        if(mimeTypes != null) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        return intent;
    }

    @Nullable
    @Override
    public final SynchronousResult<Uri> getSynchronousResult(@NonNull Context context,
                                                             @NonNull Object input) {
        return null;
    }

    @Nullable
    @Override
    public final Uri parseResult(int resultCode, @Nullable Intent intent) {
        if (intent == null || resultCode != Activity.RESULT_OK) return null;
        return intent.getData();
    }
}
