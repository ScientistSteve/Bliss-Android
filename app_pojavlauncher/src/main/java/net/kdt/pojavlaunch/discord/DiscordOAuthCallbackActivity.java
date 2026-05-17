package net.kdt.pojavlaunch.discord;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import net.kdt.pojavlaunch.PojavApplication;

public class DiscordOAuthCallbackActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri data = getIntent() == null ? null : getIntent().getData();
        if(data != null) {
            String code = data.getQueryParameter("code");
            if(code != null && !code.isEmpty()) {
                PojavApplication.sExecutorService.execute(() -> {
                    try {
                        DiscordApiClient.DiscordTokenResponse tokenResponse = new DiscordApiClient().exchangeAuthorizationCode(code);
                        DiscordAuthManager.saveTokens(this, tokenResponse);
                    } catch (Exception ignored) { }
                });
            }
        }
        startActivity(new Intent(this, net.kdt.pojavlaunch.LauncherActivity.class));
        finish();
    }
}
