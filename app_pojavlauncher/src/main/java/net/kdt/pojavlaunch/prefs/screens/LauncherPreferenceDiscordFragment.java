package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.discord.DiscordTokenManager;
import net.kdt.pojavlaunch.discord.DiscordTokenValidator;

public class LauncherPreferenceDiscordFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_discord);
        Preference connect = requirePreference("discord_connect");
        updateConnectSummary(connect);

        connect.setOnPreferenceClickListener(preference -> {
            showTokenDialog(connect);
            return true;
        });

        Preference howTo = requirePreference("discord_how_to_get_token");
        howTo.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.discord_how_to_get_token)
                    .setMessage(getString(R.string.discord_token_help_content))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        });
    }

    private void showTokenDialog(Preference connectPreference) {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.discord_token_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        TextView warning = new TextView(requireContext());
        warning.setText(R.string.discord_token_warning);
        warning.setTextColor(0xFFFFA000);
        container.addView(input);
        container.addView(warning);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.discord_token)
                .setView(container)
                .setPositiveButton(R.string.save, (d, w) -> saveToken(input.getText().toString().trim(), connectPreference))
                .setNeutralButton(R.string.disconnect, (d, w) -> clearToken(connectPreference))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveToken(String token, Preference connectPreference) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                SharedPreferences prefs = DiscordTokenManager.securePrefs(requireContext());
                DiscordTokenValidator.Result result = new DiscordTokenValidator().validate(token);
                if (result.valid) {
                    prefs.edit().putString(DiscordTokenManager.KEY_TOKEN, token)
                            .putString(DiscordTokenManager.KEY_USERNAME, result.username)
                            .putString(DiscordTokenManager.KEY_AVATAR_URL, result.avatarUrl)
                            .apply();
                    requireActivity().runOnUiThread(() -> {
                        updateConnectSummary(connectPreference);
                    });
                } else {
                    prefs.edit().remove(DiscordTokenManager.KEY_TOKEN)
                            .remove(DiscordTokenManager.KEY_USERNAME)
                            .remove(DiscordTokenManager.KEY_AVATAR_URL)
                            .apply();
                    requireActivity().runOnUiThread(() -> connectPreference.setSummary(R.string.discord_token_invalid));
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> connectPreference.setSummary(R.string.discord_token_invalid));
            }
        });
    }

    private void clearToken(Preference connectPreference) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                DiscordTokenManager.securePrefs(requireContext()).edit()
                        .remove(DiscordTokenManager.KEY_TOKEN)
                        .remove(DiscordTokenManager.KEY_USERNAME)
                        .remove(DiscordTokenManager.KEY_AVATAR_URL)
                        .apply();
            } catch (Exception ignored) { }
            requireActivity().runOnUiThread(() -> updateConnectSummary(connectPreference));
        });
    }

    private void updateConnectSummary(Preference connectPreference) {
        try {
            SharedPreferences prefs = DiscordTokenManager.securePrefs(requireContext());
            String username = prefs.getString(DiscordTokenManager.KEY_USERNAME, "");
            String avatar = prefs.getString(DiscordTokenManager.KEY_AVATAR_URL, "");
            if (username == null || username.isEmpty()) {
                connectPreference.setSummary(getString(R.string.discord_connect_summary));
            } else {
                connectPreference.setSummary(getString(R.string.discord_connected_as, username, avatar));
            }
        } catch (Exception e) {
            connectPreference.setSummary(getString(R.string.discord_connect_summary));
        }
    }
}
