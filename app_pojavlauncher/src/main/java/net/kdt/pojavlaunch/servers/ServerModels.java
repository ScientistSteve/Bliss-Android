package net.kdt.pojavlaunch.servers;

public class ServerModels {
    public enum PingState { CONNECTING, ONLINE, OFFLINE }

    public static class ServerEntry {
        public String name;
        public String ip;
        public String iconBase64;
        public PingState state = PingState.CONNECTING;
        public int onlinePlayers;
        public int maxPlayers;
        public long pingMs = -1;

        public ServerEntry(String name, String ip, String iconBase64) {
            this.name = name;
            this.ip = ip;
            this.iconBase64 = iconBase64;
        }
    }
}
