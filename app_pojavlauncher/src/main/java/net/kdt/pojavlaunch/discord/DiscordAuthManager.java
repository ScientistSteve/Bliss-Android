package net.kdt.pojavlaunch.discord;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import net.kdt.pojavlaunch.BuildConfig;

public class DiscordAuthManager {
    public static final String PREF_NAME = "discord_secure";
    public static final String KEY_ACCESS = "access";
    public static final String KEY_REFRESH = "refresh";
    public static final String KEY_EXPIRY = "expiry";

    public static String getAuthorizeUrl() {
        return "https://discord.com/oauth2/authorize?response_type=code&client_id=" + BuildConfig.DISCORD_CLIENT_ID +
                "&scope=identify%20rpc.activities.write" +
                "&redirect_uri=" + Uri.encode(BuildConfig.DISCORD_REDIRECT_URI);
    }

    public static SharedPreferences securePrefs(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(context, PREF_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    public static void saveTokens(Context context, DiscordApiClient.DiscordTokenResponse tokenResponse) throws Exception {
        securePrefs(context).edit()
                .putString(KEY_ACCESS, tokenResponse.access_token)
                .putString(KEY_REFRESH, tokenResponse.refresh_token)
                .putLong(KEY_EXPIRY, System.currentTimeMillis() + (tokenResponse.expires_in * 1000L))
                .apply();
    }

    public static String getValidAccessToken(Context context) throws Exception {
        SharedPreferences prefs = securePrefs(context);
        String access = prefs.getString(KEY_ACCESS, null);
        long expiry = prefs.getLong(KEY_EXPIRY, 0L);
        if(access != null && System.currentTimeMillis() < expiry) return access;

        String refresh = prefs.getString(KEY_REFRESH, null);
        if(refresh == null || refresh.isEmpty()) return null;
        DiscordApiClient.DiscordTokenResponse refreshed = new DiscordApiClient().refreshToken(refresh);
        saveTokens(context, refreshed);
        return refreshed.access_token;
    }

}
