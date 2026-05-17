package net.kdt.pojavlaunch.prefs.screens;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.discord.DiscordAuthManager;

public class LauncherPreferenceDiscordFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_discord);
        Preference connect = requirePreference("discord_connect");
        connect.setOnPreferenceClickListener(preference -> {
            try {
                CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder().build();
                tabsIntent.launchUrl(requireContext(), Uri.parse(DiscordAuthManager.getAuthorizeUrl()));
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(DiscordAuthManager.getAuthorizeUrl())));
            }
            return true;
        });
    }
}
