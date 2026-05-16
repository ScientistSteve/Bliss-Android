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
    private static final String KEY_MODEL = "model";

    private static final String[] OPENAI_MODELS = {"gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo"};
    private static final String[] GEMINI_MODELS = {"gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash"};
    private static final String[] GROQ_MODELS = {"llama3-70b-8192", "mixtral-8x7b-32768", "gemma2-9b-it"};

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

    public static void save(@NonNull Context context, @NonNull String provider, @NonNull String apiKey, @NonNull String model) {
        preferences(context).edit()
                .putString(KEY_PROVIDER, provider)
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_MODEL, model)
                .apply();
    }

    public static void save(@NonNull Context context, @NonNull String provider, @NonNull String apiKey) {
        save(context, provider, apiKey, "");
    }

    @NonNull
    public static String getProvider(@NonNull Context context) {
        String provider = preferences(context).getString(KEY_PROVIDER, PROVIDER_OPENAI);
        if (PROVIDER_GEMINI.equals(provider) || PROVIDER_GROQ.equals(provider) || PROVIDER_OPENAI.equals(provider)) return provider;
        return PROVIDER_OPENAI;
    }

    @NonNull
    public static String getApiKey(@NonNull Context context) {
        String apiKey = preferences(context).getString(KEY_API_KEY, "");
        return apiKey == null ? "" : apiKey;
    }

    @NonNull
    public static String getModel(@NonNull Context context) {
        String model = preferences(context).getString(KEY_MODEL, "");
        return model == null ? "" : model;
    }

    @NonNull
    public static String[] getModelsForProvider(@NonNull String provider) {
        String[] source;
        if (PROVIDER_GEMINI.equals(provider)) source = GEMINI_MODELS;
        else if (PROVIDER_GROQ.equals(provider)) source = GROQ_MODELS;
        else source = OPENAI_MODELS;
        String[] copy = new String[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }

    @NonNull
    public static String getDefaultModel(@NonNull String provider) {
        String[] models = getModelsForProvider(provider);
        return models.length == 0 ? "" : models[0];
    }

    @NonNull
    public static String normalizeModel(@NonNull String provider, String model) {
        return model == null ? "" : model;
    }

    public static boolean hasApiKey(@NonNull Context context) {
        return !getApiKey(context).trim().isEmpty();
    }
}
