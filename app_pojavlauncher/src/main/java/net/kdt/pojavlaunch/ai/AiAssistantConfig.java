package net.kdt.pojavlaunch.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class AiAssistantConfig {
    public static final String PROVIDER_OPENAI = "OpenAI";
    public static final String PROVIDER_GEMINI = "Gemini";
    public static final String PROVIDER_GROQ = "Groq";

    private static final String PREF_NAME = "ai_assistant_secure";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_API_KEY = "api_key";

    private AiAssistantConfig() {}

    @NonNull
    public static SharedPreferences preferences(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                MasterKey masterKey = new MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();
                return EncryptedSharedPreferences.create(
                        appContext,
                        PREF_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            } catch (Exception ignored) {
                // Fall through to regular app-private preferences rather than crashing the launcher.
            }
        }
        return appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void save(@NonNull Context context, @NonNull String provider, @NonNull String apiKey) {
        preferences(context).edit()
                .putString(KEY_PROVIDER, provider)
                .putString(KEY_API_KEY, apiKey)
                .apply();
    }

    @NonNull
    public static String getProvider(@NonNull Context context) {
        return preferences(context).getString(KEY_PROVIDER, PROVIDER_OPENAI);
    }

    @NonNull
    public static String getApiKey(@NonNull Context context) {
        return preferences(context).getString(KEY_API_KEY, "");
    }

    public static boolean hasApiKey(@NonNull Context context) {
        return !getApiKey(context).trim().isEmpty();
    }
}
