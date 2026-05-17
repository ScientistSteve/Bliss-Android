package net.kdt.pojavlaunch.discord;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import net.kdt.pojavlaunch.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DiscordAuthManager {
    public static final String PREF_NAME = "discord_secure";
    public static final String KEY_ACCESS = "access";
    public static final String KEY_REFRESH = "refresh";
    public static final String KEY_EXPIRY = "expiry";

    private static final String TAG = "DiscordAuthManager";

    public static String getAuthorizeUrl() {
        return "https://discord.com/api/oauth2/authorize?client_id=" + BuildConfig.DISCORD_CLIENT_ID +
                "&redirect_uri=" + Uri.encode(BuildConfig.DISCORD_REDIRECT_URI) +
                "&response_type=code" +
                "&scope=" + Uri.encode("identify rpc.activities.write") +
                "";
    }

    public static SharedPreferences securePrefs(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(context, PREF_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public static void exchangeCode(Context context, String code) throws Exception {
        String form = "client_id=" + enc(BuildConfig.DISCORD_CLIENT_ID) +
                "&client_secret=" + enc(BuildConfig.DISCORD_CLIENT_SECRET) +
                "&grant_type=authorization_code" +
                "&code=" + enc(code) +
                "&redirect_uri=" + enc(BuildConfig.DISCORD_REDIRECT_URI);
        JSONObject tokenObject = postTokenForm(form);
        saveTokenResponse(context, tokenObject);
    }

    public static boolean refreshAccessTokenIfRequired(Context context) throws Exception {
        SharedPreferences prefs = securePrefs(context);
        long expiresAt = prefs.getLong(KEY_EXPIRY, 0L);
        String refreshToken = prefs.getString(KEY_REFRESH, null);
        if (refreshToken == null) return false;
        if (System.currentTimeMillis() < expiresAt - 30_000L) return true;
        String form = "client_id=" + enc(BuildConfig.DISCORD_CLIENT_ID) +
                "&client_secret=" + enc(BuildConfig.DISCORD_CLIENT_SECRET) +
                "&grant_type=refresh_token" +
                "&refresh_token=" + enc(refreshToken) +
                "&redirect_uri=" + enc(BuildConfig.DISCORD_REDIRECT_URI);
        JSONObject tokenObject = postTokenForm(form);
        saveTokenResponse(context, tokenObject);
        return true;
    }

    public static void updateHeadlessPresence(Context context, String details, String state, long startEpochMs) throws Exception {
        if (!refreshAccessTokenIfRequired(context)) return;
        String access = securePrefs(context).getString(KEY_ACCESS, null);
        if (access == null) return;
        JSONObject body = new JSONObject()
                .put("activities", new JSONArray()
                        .put(new JSONObject()
                                .put("type", 0)
                                .put("name", "Minecraft")
                                .put("details", details)
                                .put("state", state)
                                .put("timestamps", new JSONObject().put("start", startEpochMs))));
        int code = patchHeadless(access, body.toString());
        if (code == 404) {
            Log.w(TAG, "Headless sessions endpoint returned 404; gateway fallback required but unavailable in this build.");
        }
    }

    private static JSONObject postTokenForm(String formBody) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://discord.com/api/oauth2/token").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(formBody.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        String body = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code < 200 || code >= 300) throw new IllegalStateException("Discord token exchange failed: HTTP " + code + " body=" + body);
        return new JSONObject(body);
    }

    private static int patchHeadless(String accessToken, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://discord.com/api/v10/users/@me/headless-sessions").openConnection();
        conn.setRequestMethod("PATCH");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return conn.getResponseCode();
    }

    private static void saveTokenResponse(Context context, JSONObject tokenObject) throws Exception {
        long expiresIn = tokenObject.optLong("expires_in", 0L);
        securePrefs(context).edit()
                .putString(KEY_ACCESS, tokenObject.optString("access_token", null))
                .putString(KEY_REFRESH, tokenObject.optString("refresh_token", null))
                .putLong(KEY_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000L))
                .apply();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
