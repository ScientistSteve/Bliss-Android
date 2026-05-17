package net.kdt.pojavlaunch.discord;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class DiscordTokenValidator {
    public static class Result {
        public final boolean valid;
        public final String username;
        public final String avatarUrl;

        public Result(boolean valid, String username, String avatarUrl) {
            this.valid = valid;
            this.username = username;
            this.avatarUrl = avatarUrl;
        }
    }

    public Result validate(String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://discord.com/api/v9/users/@me").openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", token);
        int code = connection.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) {
            try (InputStream stream = connection.getInputStream(); Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                String body = scanner.hasNext() ? scanner.next() : "{}";
                JSONObject json = new JSONObject(body);
                String username = json.optString("username", "") + "#" + json.optString("discriminator", "0");
                String avatar = json.optString("avatar", null);
                String id = json.optString("id", "");
                String avatarUrl = (avatar == null || id.isEmpty()) ? "" : "https://cdn.discordapp.com/avatars/" + id + "/" + avatar + ".png";
                return new Result(true, username, avatarUrl);
            }
        }
        return new Result(false, "", "");
    }
}
