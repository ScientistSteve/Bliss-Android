package net.kdt.pojavlaunch.minecraft;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class MinecraftServerPinger {
    private static final String TAG = "MinecraftPinger";
    private static final int DEFAULT_PORT = 25565;
    private static final int PROTOCOL_VERSION = 763;
    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_PACKET_SIZE = 2 * 1024 * 1024;

    private MinecraftServerPinger() {}

    @NonNull
    public static PingResult ping(@Nullable String address) {
        ParsedAddress parsed = ParsedAddress.parse(address);
        if (parsed.host.isEmpty()) return PingResult.offline();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parsed.host, parsed.port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            DataInputStream input = new DataInputStream(socket.getInputStream());
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());

            sendStatusHandshake(output, parsed);
            sendStatusRequest(output);

            Packet statusPacket = readPacket(input);
            if (statusPacket.id != 0x00) return PingResult.offline();
            String json = readString(statusPacket.input);

            long pingPayload = System.currentTimeMillis();
            sendPing(output, pingPayload);
            Packet pongPacket = readPacket(input);
            if (pongPacket.id != 0x01) return PingResult.offline();
            long pongPayload = pongPacket.input.readLong();
            long latency = Math.max(0, System.currentTimeMillis() - pongPayload);
            return parseStatus(json, latency);
        } catch (IOException | RuntimeException e) {
            Log.d(TAG, "Server ping failed for " + address, e);
            return PingResult.offline();
        }
    }

    private static void sendStatusHandshake(@NonNull DataOutputStream output, @NonNull ParsedAddress parsed) throws IOException {
        ByteArrayOutputStream handshakePayload = new ByteArrayOutputStream();
        DataOutputStream handshake = new DataOutputStream(handshakePayload);
        writeVarInt(handshake, 0x00);
        writeVarInt(handshake, PROTOCOL_VERSION);
        writeString(handshake, parsed.host);
        handshake.writeShort(parsed.port);
        writeVarInt(handshake, 1);
        writePacket(output, handshakePayload.toByteArray());
    }

    private static void sendStatusRequest(@NonNull DataOutputStream output) throws IOException {
        ByteArrayOutputStream requestPayload = new ByteArrayOutputStream();
        DataOutputStream request = new DataOutputStream(requestPayload);
        writeVarInt(request, 0x00);
        writePacket(output, requestPayload.toByteArray());
    }

    private static void sendPing(@NonNull DataOutputStream output, long payload) throws IOException {
        ByteArrayOutputStream pingPayload = new ByteArrayOutputStream();
        DataOutputStream ping = new DataOutputStream(pingPayload);
        writeVarInt(ping, 0x01);
        ping.writeLong(payload);
        writePacket(output, pingPayload.toByteArray());
    }

    @NonNull
    private static Packet readPacket(@NonNull DataInputStream input) throws IOException {
        int packetLength = readVarInt(input);
        if (packetLength <= 0 || packetLength > MAX_PACKET_SIZE) throw new IOException("Invalid packet length");
        byte[] packetData = new byte[packetLength];
        input.readFully(packetData);
        DataInputStream packetInput = new DataInputStream(new java.io.ByteArrayInputStream(packetData));
        int packetId = readVarInt(packetInput);
        return new Packet(packetId, packetInput);
    }

    @NonNull
    private static PingResult parseStatus(@NonNull String json, long latency) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject players = root.optJSONObject("players");
            int online = players == null ? -1 : players.optInt("online", -1);
            int max = players == null ? -1 : players.optInt("max", -1);
            String favicon = root.optString("favicon", null);
            String description = parseDescription(root.opt("description"));
            return new PingResult(true, latency, online, max, favicon == null || favicon.isEmpty() ? null : favicon, description);
        } catch (JSONException | RuntimeException e) {
            return new PingResult(true, latency, -1, -1, null, null);
        }
    }

    @Nullable
    private static String parseDescription(@Nullable Object description) {
        if (description == null || JSONObject.NULL.equals(description)) return null;
        if (description instanceof String) return (String) description;
        if (description instanceof JSONObject) {
            JSONObject object = (JSONObject) description;
            String text = object.optString("text", "");
            return text.isEmpty() ? object.toString() : text;
        }
        return String.valueOf(description);
    }

    private static void writePacket(@NonNull DataOutputStream output, @NonNull byte[] payload) throws IOException {
        writeVarInt(output, payload.length);
        output.write(payload);
        output.flush();
    }

    private static void writeString(@NonNull DataOutputStream output, @NonNull String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, data.length);
        output.write(data);
    }

    @NonNull
    private static String readString(@NonNull DataInputStream input) throws IOException {
        int length = readVarInt(input);
        if (length < 0 || length > 32767 * 4) throw new IOException("Invalid string length");
        byte[] data = new byte[length];
        input.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(@NonNull DataOutputStream output, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            output.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value & 0x7F);
    }

    private static int readVarInt(@NonNull DataInputStream input) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = input.readByte();
            int value = read & 0x7F;
            result |= value << (7 * numRead);
            numRead++;
            if (numRead > 5) throw new IOException("VarInt too big");
        } while ((read & 0x80) != 0);
        return result;
    }

    private static final class Packet {
        final int id;
        final DataInputStream input;

        private Packet(int id, DataInputStream input) {
            this.id = id;
            this.input = input;
        }
    }

    public static final class PingResult {
        public final boolean online;
        public final long latencyMs;
        public final int playersOnline;
        public final int playersMax;
        @Nullable public final String favicon;
        @Nullable public final String description;

        private PingResult(boolean online, long latencyMs, int playersOnline, int playersMax, @Nullable String favicon, @Nullable String description) {
            this.online = online;
            this.latencyMs = latencyMs;
            this.playersOnline = playersOnline;
            this.playersMax = playersMax;
            this.favicon = favicon;
            this.description = description;
        }

        @NonNull
        public static PingResult offline() {
            return new PingResult(false, -1, -1, -1, null, null);
        }
    }

    private static final class ParsedAddress {
        final String host;
        final int port;

        private ParsedAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @NonNull
        static ParsedAddress parse(@Nullable String address) {
            if (address == null) return new ParsedAddress("", DEFAULT_PORT);
            String value = address.trim();
            if (value.isEmpty()) return new ParsedAddress("", DEFAULT_PORT);
            if (value.startsWith("[") && value.contains("]")) {
                int end = value.indexOf(']');
                String host = value.substring(1, end);
                int port = DEFAULT_PORT;
                if (end + 2 <= value.length() && value.charAt(end + 1) == ':') port = parsePort(value.substring(end + 2));
                return new ParsedAddress(host, port);
            }
            int colon = value.lastIndexOf(':');
            if (colon > 0 && value.indexOf(':') == colon) return new ParsedAddress(value.substring(0, colon), parsePort(value.substring(colon + 1)));
            return new ParsedAddress(value, DEFAULT_PORT);
        }

        private static int parsePort(@Nullable String value) {
            try {
                int port = Integer.parseInt(value);
                return port > 0 && port <= 65535 ? port : DEFAULT_PORT;
            } catch (RuntimeException e) {
                return DEFAULT_PORT;
            }
        }
    }
}
