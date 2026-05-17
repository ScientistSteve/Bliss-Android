package net.kdt.pojavlaunch.discord;

import androidx.annotation.Keep;

import net.kdt.pojavlaunch.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class DiscordApiClient {
    private static final String OAUTH_TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String HEADLESS_SESSIONS_URL = "https://discord.com/api/v10/users/@me/headless-sessions";
    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";

    @Keep
    public static class DiscordTokenResponse {
        public String access_token;
        public String refresh_token;
        public long expires_in;
        public String token_type;
    }

    public DiscordTokenResponse exchangeAuthorizationCode(String code) throws Exception {
        return postTokenForm("authorization_code", code, null);
    }

    public DiscordTokenResponse refreshToken(String refreshToken) throws Exception {
        return postTokenForm("refresh_token", null, refreshToken);
    }

    public int updateRichPresence(String accessToken, String details, String state, long startEpochMs) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(HEADLESS_SESSIONS_URL).openConnection();
        connection.setRequestMethod("PATCH");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json");

        JSONObject payload = new JSONObject();
        JSONObject activity = new JSONObject();
        activity.put("type", 0);
        activity.put("name", "Minecraft");
        activity.put("details", details == null ? "" : details);
        activity.put("state", state == null ? "" : state);
        activity.put("timestamps", new JSONObject().put("start", startEpochMs));
        payload.put("activities", new JSONArray().put(activity));

        try(OutputStream out = connection.getOutputStream()) {
            out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        if(code == HttpURLConnection.HTTP_NOT_FOUND) {
            startGatewayFallback(accessToken, details, state, startEpochMs);
        }
        if(code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new SecurityException("Discord access token unauthorized");
        }
        return code;
    }

    private DiscordTokenResponse postTokenForm(String grantType, String code, String refreshToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(OAUTH_TOKEN_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        StringBuilder form = new StringBuilder();
        appendForm(form, "client_id", BuildConfig.DISCORD_CLIENT_ID);
        appendForm(form, "client_secret", BuildConfig.DISCORD_CLIENT_SECRET);
        appendForm(form, "grant_type", grantType);
        if(code != null) appendForm(form, "code", code);
        if(refreshToken != null) appendForm(form, "refresh_token", refreshToken);
        appendForm(form, "redirect_uri", BuildConfig.DISCORD_REDIRECT_URI);

        try(OutputStream out = connection.getOutputStream()) {
            out.write(form.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        String raw = readFully(responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
        if(responseCode < 200 || responseCode >= 300) {
            throw new IOException("Discord token request failed: " + responseCode + " " + raw);
        }

        JSONObject jsonObject = new JSONObject(raw);
        DiscordTokenResponse response = new DiscordTokenResponse();
        response.access_token = jsonObject.optString("access_token", "");
        response.refresh_token = jsonObject.optString("refresh_token", "");
        response.expires_in = jsonObject.optLong("expires_in", 0);
        response.token_type = jsonObject.optString("token_type", "Bearer");
        return response;
    }

    private void startGatewayFallback(String accessToken, String details, String state, long startEpochMs) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(GATEWAY_URL).build();
        client.newWebSocket(request, new WebSocketListener() {
            ScheduledExecutorService heartbeat;

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject msg = new JSONObject(text);
                    int op = msg.optInt("op", -1);
                    if(op == 10) {
                        long heartbeatInterval = msg.optJSONObject("d").optLong("heartbeat_interval", 30000L);
                        heartbeat = Executors.newSingleThreadScheduledExecutor();
                        heartbeat.scheduleAtFixedRate(() -> webSocket.send("{\"op\":1,\"d\":null}"), heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);

                        JSONObject identify = new JSONObject();
                        identify.put("op", 2);
                        identify.put("d", new JSONObject()
                                .put("token", "Bearer " + accessToken)
                                .put("properties", new JSONObject().put("os", "Android").put("browser", "BlissLauncher").put("device", "BlissLauncher")));
                        webSocket.send(identify.toString());

                        JSONObject presence = new JSONObject();
                        presence.put("op", 3);
                        JSONObject d = new JSONObject();
                        d.put("since", JSONObject.NULL);
                        d.put("activities", new JSONArray().put(new JSONObject()
                                .put("name", "Minecraft")
                                .put("type", 0)
                                .put("details", details == null ? "" : details)
                                .put("state", state == null ? "" : state)
                                .put("timestamps", new JSONObject().put("start", startEpochMs))));
                        d.put("status", "online");
                        d.put("afk", false);
                        presence.put("d", d);
                        webSocket.send(presence.toString());
                    }
                } catch (Exception ignored) { }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if(heartbeat != null) heartbeat.shutdownNow();
            }
        });
    }

    private static void appendForm(StringBuilder form, String key, String value) throws Exception {
        if(form.length() > 0) form.append('&');
        form.append(URLEncoder.encode(key, "UTF-8")).append('=').append(URLEncoder.encode(value == null ? "" : value, "UTF-8"));
    }

    private static String readFully(InputStream stream) throws IOException {
        if(stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}
