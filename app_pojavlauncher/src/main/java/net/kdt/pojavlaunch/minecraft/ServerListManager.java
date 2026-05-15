package net.kdt.pojavlaunch.minecraft;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ServerListManager {
    private static final String TAG = "ServerListManager";

    private ServerListManager() {}

    @NonNull
    public static ServerList load(@NonNull File gameDir) {
        File file = getServersFile(gameDir);
        if (!file.isFile()) return new ServerList(file, new NbtCompound(""), new ArrayList<>());
        try {
            NbtCompound root = readRoot(file);
            return fromRoot(file, root);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Unable to read servers.dat", e);
            return new ServerList(file, new NbtCompound(""), new ArrayList<>());
        }
    }

    public static boolean save(@NonNull ServerList serverList) {
        try {
            File parent = serverList.file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            NbtList list = new NbtList("servers", NbtCompound.ID);
            for (ServerEntry entry : serverList.servers) list.value.add(entry.toNbt());
            serverList.root.value.put("servers", list);
            writeRoot(serverList.file, serverList.root);
            return true;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Unable to write servers.dat", e);
            return false;
        }
    }

    @NonNull
    public static File getServersFile(@NonNull File gameDir) {
        return new File(gameDir, "servers.dat");
    }

    @NonNull
    private static ServerList fromRoot(@NonNull File file, @NonNull NbtCompound root) {
        List<ServerEntry> servers = new ArrayList<>();
        NbtTag serversTag = root.value.get("servers");
        if (serversTag instanceof NbtList) {
            for (NbtTag tag : ((NbtList) serversTag).value) {
                if (tag instanceof NbtCompound) servers.add(ServerEntry.fromNbt((NbtCompound) tag));
            }
        }
        return new ServerList(file, root, servers);
    }

    @NonNull
    private static NbtCompound readRoot(@NonNull File file) throws IOException {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            return readNamedRoot(input);
        } catch (IOException compressedError) {
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                return readNamedRoot(input);
            } catch (IOException plainError) {
                compressedError.addSuppressed(plainError);
                throw compressedError;
            }
        }
    }

    @NonNull
    private static NbtCompound readNamedRoot(@NonNull DataInputStream input) throws IOException {
        byte id = input.readByte();
        if (id != NbtCompound.ID) throw new IOException("Root tag is not a compound");
        String name = input.readUTF();
        return readCompoundPayload(input, name);
    }

    private static void writeRoot(@NonNull File file, @NonNull NbtCompound root) throws IOException {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(temp))))) {
            output.writeByte(NbtCompound.ID);
            output.writeUTF(root.name == null ? "" : root.name);
            writeCompoundPayload(output, root);
        }
        if (file.exists() && !file.delete()) throw new IOException("Unable to replace old servers.dat");
        if (!temp.renameTo(file)) throw new IOException("Unable to move new servers.dat into place");
    }

    @NonNull
    private static NbtTag readNamedTag(@NonNull DataInputStream input) throws IOException {
        byte id = input.readByte();
        if (id == NbtEnd.ID) return new NbtEnd();
        String name = input.readUTF();
        return readPayload(input, id, name);
    }

    @NonNull
    private static NbtTag readPayload(@NonNull DataInputStream input, byte id, @Nullable String name) throws IOException {
        switch (id) {
            case NbtByte.ID: return new NbtByte(name, input.readByte());
            case NbtShort.ID: return new NbtShort(name, input.readShort());
            case NbtInt.ID: return new NbtInt(name, input.readInt());
            case NbtLong.ID: return new NbtLong(name, input.readLong());
            case NbtFloat.ID: return new NbtFloat(name, input.readFloat());
            case NbtDouble.ID: return new NbtDouble(name, input.readDouble());
            case NbtByteArray.ID: {
                int length = input.readInt();
                byte[] data = new byte[Math.max(0, length)];
                input.readFully(data);
                return new NbtByteArray(name, data);
            }
            case NbtString.ID: return new NbtString(name, input.readUTF());
            case NbtList.ID: {
                byte childId = input.readByte();
                int length = input.readInt();
                NbtList list = new NbtList(name, childId);
                for (int i = 0; i < length; i++) list.value.add(readPayload(input, childId, null));
                return list;
            }
            case NbtCompound.ID: return readCompoundPayload(input, name);
            case NbtIntArray.ID: {
                int length = input.readInt();
                int[] data = new int[Math.max(0, length)];
                for (int i = 0; i < data.length; i++) data[i] = input.readInt();
                return new NbtIntArray(name, data);
            }
            case NbtLongArray.ID: {
                int length = input.readInt();
                long[] data = new long[Math.max(0, length)];
                for (int i = 0; i < data.length; i++) data[i] = input.readLong();
                return new NbtLongArray(name, data);
            }
            default: throw new IOException("Unsupported NBT tag " + id);
        }
    }

    @NonNull
    private static NbtCompound readCompoundPayload(@NonNull DataInputStream input, @Nullable String name) throws IOException {
        NbtCompound compound = new NbtCompound(name);
        while (true) {
            NbtTag tag = readNamedTag(input);
            if (tag instanceof NbtEnd) break;
            compound.value.put(tag.name, tag);
        }
        return compound;
    }

    private static void writeNamedTag(@NonNull DataOutputStream output, @NonNull NbtTag tag) throws IOException {
        output.writeByte(tag.id());
        if (tag.id() == NbtEnd.ID) return;
        output.writeUTF(tag.name == null ? "" : tag.name);
        writePayload(output, tag);
    }

    private static void writePayload(@NonNull DataOutputStream output, @NonNull NbtTag tag) throws IOException {
        switch (tag.id()) {
            case NbtByte.ID: output.writeByte(((NbtByte) tag).value); break;
            case NbtShort.ID: output.writeShort(((NbtShort) tag).value); break;
            case NbtInt.ID: output.writeInt(((NbtInt) tag).value); break;
            case NbtLong.ID: output.writeLong(((NbtLong) tag).value); break;
            case NbtFloat.ID: output.writeFloat(((NbtFloat) tag).value); break;
            case NbtDouble.ID: output.writeDouble(((NbtDouble) tag).value); break;
            case NbtByteArray.ID:
                output.writeInt(((NbtByteArray) tag).value.length);
                output.write(((NbtByteArray) tag).value);
                break;
            case NbtString.ID: output.writeUTF(((NbtString) tag).value == null ? "" : ((NbtString) tag).value); break;
            case NbtList.ID:
                NbtList list = (NbtList) tag;
                output.writeByte(list.childId);
                output.writeInt(list.value.size());
                for (NbtTag child : list.value) writePayload(output, child);
                break;
            case NbtCompound.ID: writeCompoundPayload(output, (NbtCompound) tag); break;
            case NbtIntArray.ID:
                output.writeInt(((NbtIntArray) tag).value.length);
                for (int value : ((NbtIntArray) tag).value) output.writeInt(value);
                break;
            case NbtLongArray.ID:
                output.writeInt(((NbtLongArray) tag).value.length);
                for (long value : ((NbtLongArray) tag).value) output.writeLong(value);
                break;
            default: throw new IOException("Unsupported NBT tag " + tag.id());
        }
    }

    private static void writeCompoundPayload(@NonNull DataOutputStream output, @NonNull NbtCompound compound) throws IOException {
        for (NbtTag tag : compound.value.values()) writeNamedTag(output, tag);
        output.writeByte(NbtEnd.ID);
    }

    @NonNull
    private static String getString(@NonNull NbtCompound compound, @NonNull String key) {
        NbtTag tag = compound.value.get(key);
        return tag instanceof NbtString ? ((NbtString) tag).value : "";
    }

    public static final class ServerList {
        public final File file;
        private final NbtCompound root;
        public final List<ServerEntry> servers;

        private ServerList(File file, NbtCompound root, List<ServerEntry> servers) {
            this.file = file;
            this.root = root;
            this.servers = servers;
        }
    }

    public static final class ServerEntry {
        public String name;
        public String address;
        @Nullable public String icon;
        private NbtCompound backingTag;

        public ServerEntry(@NonNull String name, @NonNull String address) {
            this.name = name;
            this.address = address;
            this.backingTag = new NbtCompound(null);
        }

        @NonNull
        private static ServerEntry fromNbt(@NonNull NbtCompound compound) {
            ServerEntry entry = new ServerEntry(getString(compound, "name"), getString(compound, "ip"));
            String icon = getString(compound, "icon");
            entry.icon = icon.isEmpty() ? null : icon;
            entry.backingTag = compound;
            return entry;
        }

        @NonNull
        private NbtCompound toNbt() {
            NbtCompound compound = backingTag == null ? new NbtCompound(null) : backingTag;
            compound.value.put("name", new NbtString("name", name == null ? "" : name));
            compound.value.put("ip", new NbtString("ip", address == null ? "" : address));
            if (icon != null && !icon.isEmpty()) compound.value.put("icon", new NbtString("icon", icon));
            return compound;
        }
    }

    private abstract static class NbtTag {
        @Nullable final String name;
        NbtTag(@Nullable String name) { this.name = name; }
        abstract byte id();
    }
    private static final class NbtEnd extends NbtTag { static final byte ID = 0; NbtEnd() { super(null); } byte id() { return ID; } }
    private static final class NbtByte extends NbtTag { static final byte ID = 1; final byte value; NbtByte(String n, byte v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtShort extends NbtTag { static final byte ID = 2; final short value; NbtShort(String n, short v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtInt extends NbtTag { static final byte ID = 3; final int value; NbtInt(String n, int v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtLong extends NbtTag { static final byte ID = 4; final long value; NbtLong(String n, long v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtFloat extends NbtTag { static final byte ID = 5; final float value; NbtFloat(String n, float v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtDouble extends NbtTag { static final byte ID = 6; final double value; NbtDouble(String n, double v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtByteArray extends NbtTag { static final byte ID = 7; final byte[] value; NbtByteArray(String n, byte[] v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtString extends NbtTag { static final byte ID = 8; final String value; NbtString(String n, String v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtList extends NbtTag { static final byte ID = 9; final byte childId; final List<NbtTag> value = new ArrayList<>(); NbtList(String n, byte c) { super(n); childId = c; } byte id() { return ID; } }
    private static final class NbtCompound extends NbtTag { static final byte ID = 10; final Map<String, NbtTag> value = new LinkedHashMap<>(); NbtCompound(String n) { super(n); } byte id() { return ID; } }
    private static final class NbtIntArray extends NbtTag { static final byte ID = 11; final int[] value; NbtIntArray(String n, int[] v) { super(n); value = v; } byte id() { return ID; } }
    private static final class NbtLongArray extends NbtTag { static final byte ID = 12; final long[] value; NbtLongArray(String n, long[] v) { super(n); value = v; } byte id() { return ID; } }
}
