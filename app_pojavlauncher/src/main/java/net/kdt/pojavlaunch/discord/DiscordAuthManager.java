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
                "&scope=" + Uri.encode("identify rpc.activities.write") +
                "&redirect_uri=" + Uri.encode(BuildConfig.DISCORD_REDIRECT_URI);
    }

    public static SharedPreferences securePrefs(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(context, PREF_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }
}
