package net.kdt.pojavlaunch.discord;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class DiscordOAuthCallbackActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String code = getIntent() != null && getIntent().getData() != null ? getIntent().getData().getQueryParameter("code") : null;
        if (code != null && !code.isEmpty()) {
            Tools.runAsync(() -> {
                try {
                    DiscordAuthManager.exchangeCode(this, code);
                    runOnUiThread(() -> Toast.makeText(this, R.string.global_done, Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                } finally {
                    finish();
                }
            });
            return;
        }
        finish();
    }
}
