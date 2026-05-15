package net.kdt.pojavlaunch.servers;

import net.kdt.pojavlaunch.Tools;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ServerDataManager {
    public static File getServersDatFile(File gameDir) { return new File(gameDir, "servers.dat"); }

    public static List<ServerModels.ServerEntry> readServers(File gameDir) {
        List<ServerModels.ServerEntry> out = new ArrayList<>();
        File file = getServersDatFile(gameDir);
        if (!file.exists()) return out;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            if (in.readByte() != 10) return out;
            readString(in);
            int tag;
            while ((tag = in.readByte()) != 0) {
                String name = readString(in);
                if ("servers".equals(name) && tag == 9) {
                    int elemType = in.readByte();
                    int size = in.readInt();
                    if (elemType != 10) return out;
                    for (int i = 0; i < size; i++) {
                        out.add(readServerCompound(in));
                    }
                } else skipTag(in, tag);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public static void writeServers(File gameDir, List<ServerModels.ServerEntry> entries) {
        File file = getServersDatFile(gameDir);
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            out.writeByte(10); writeString(out, "");
            out.writeByte(9); writeString(out, "servers");
            out.writeByte(10); out.writeInt(entries.size());
            for (ServerModels.ServerEntry e : entries) writeServerCompound(out, e);
            out.writeByte(0);
        } catch (Throwable ignored) {}
    }

    public static void pingServer(ServerModels.ServerEntry server) {
        server.state = ServerModels.PingState.CONNECTING;
        try (Socket socket = new Socket()) {
            long start = System.currentTimeMillis();
            socket.connect(new InetSocketAddress(server.ip, 25565), 2500);
            socket.setSoTimeout(3000);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream hsOut = new DataOutputStream(hs);
            writeVarInt(hsOut, 0);
            writeVarInt(hsOut, 47);
            writeStringVar(hsOut, server.ip);
            hsOut.writeShort(25565);
            writeVarInt(hsOut, 1);
            writePacket(out, hs.toByteArray());
            writePacket(out, new byte[]{0x00});

            readVarInt(in); // packet len
            int id = readVarInt(in);
            if (id != 0) throw new IOException();
            int strLen = readVarInt(in);
            byte[] data = new byte[strLen]; in.readFully(data);
            JSONObject json = new JSONObject(new String(data, StandardCharsets.UTF_8));
            JSONObject players = json.optJSONObject("players");
            if (players != null) {
                server.onlinePlayers = players.optInt("online", 0);
                server.maxPlayers = players.optInt("max", 0);
            }
            server.iconBase64 = json.optString("favicon", server.iconBase64);
            server.pingMs = System.currentTimeMillis() - start;
            server.state = ServerModels.PingState.ONLINE;
        } catch (Throwable t) {
            server.state = ServerModels.PingState.OFFLINE;
            server.pingMs = -1;
        }
    }

    private static ServerModels.ServerEntry readServerCompound(DataInputStream in) throws IOException {
        String name = ""; String ip = ""; String icon = null;
        int tag;
        while ((tag = in.readByte()) != 0) {
            String key = readString(in);
            if (tag == 8) {
                String val = readString(in);
                if ("name".equals(key)) name = val;
                else if ("ip".equals(key)) ip = val;
                else if ("icon".equals(key)) icon = val;
            } else skipTag(in, tag);
        }
        return new ServerModels.ServerEntry(name, ip, icon);
    }

    private static void writeServerCompound(DataOutputStream out, ServerModels.ServerEntry e) throws IOException {
        out.writeByte(8); writeString(out, "name"); writeString(out, e.name == null ? "" : e.name);
        out.writeByte(8); writeString(out, "ip"); writeString(out, e.ip == null ? "" : e.ip);
        if (Tools.isValidString(e.iconBase64)) { out.writeByte(8); writeString(out, "icon"); writeString(out, e.iconBase64); }
        out.writeByte(0);
    }

    private static void skipTag(DataInputStream in, int t) throws IOException {
        if (t == 1) in.readByte(); else if (t == 2) in.readShort(); else if (t == 3) in.readInt(); else if (t == 4) in.readLong();
        else if (t == 5) in.readFloat(); else if (t == 6) in.readDouble(); else if (t == 7) in.skipBytes(in.readInt());
        else if (t == 8) readString(in); else if (t == 9) {int et=in.readByte();int n=in.readInt();for(int i=0;i<n;i++) skipTag(in, et);} 
        else if (t == 10) {while (in.readByte()!=0) {readString(in); skipTag(in, in.readByte());}}
        else if (t == 11) in.skipBytes(in.readInt()*4); else if (t == 12) in.skipBytes(in.readInt()*8);
    }

    private static String readString(DataInputStream in) throws IOException { int len = in.readUnsignedShort(); byte[] b = new byte[len]; in.readFully(b); return new String(b, StandardCharsets.UTF_8); }
    private static void writeString(DataOutputStream out, String s) throws IOException { byte[] b=s.getBytes(StandardCharsets.UTF_8); out.writeShort(b.length); out.write(b); }
    private static void writeStringVar(DataOutputStream out, String s) throws IOException { byte[] b=s.getBytes(StandardCharsets.UTF_8); writeVarInt(out,b.length); out.write(b); }
    private static void writePacket(DataOutputStream out, byte[] payload) throws IOException { writeVarInt(out, payload.length); out.write(payload); out.flush(); }
    private static void writeVarInt(DataOutputStream out, int value) throws IOException { while(true){ if((value & ~0x7F)==0){ out.writeByte(value); return;} out.writeByte((value&0x7F)|0x80); value>>>=7; } }
    private static int readVarInt(DataInputStream in) throws IOException { int numRead=0,result=0,read; do {read=in.readByte(); int v=(read&0x7F); result|=(v << (7*numRead)); numRead++; if(numRead>5) throw new IOException();} while((read&0x80)!=0); return result; }
}
