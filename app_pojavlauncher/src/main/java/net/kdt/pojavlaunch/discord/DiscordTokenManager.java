package net.kdt.pojavlaunch.discord;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class DiscordTokenManager {
    public static final String PREF_NAME = "discord_secure";
    public static final String KEY_TOKEN = "discord_token";
    public static final String KEY_USERNAME = "discord_username";
    public static final String KEY_AVATAR_URL = "discord_avatar_url";

    public static SharedPreferences securePrefs(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
        return EncryptedSharedPreferences.create(context, PREF_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }
}
