package com.dexvori.nextunnel;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.json.JSONObject;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

public class NexTunnelVpnService extends VpnService {

    private static final String TAG = "NexTunnelVPN";

    private static volatile String configJson   = "{}";
    private static volatile String status       = "disconnected";
    private static volatile long   bytesSent    = 0;
    private static volatile long   bytesRecv    = 0;
    private static CoreController  coreController;
    private ParcelFileDescriptor   tunInterface;

    public interface Callback {
        void onConnected();
        void onError(String msg);
    }

    public static void setConfig(String json) { configJson = json; }
    public static String getStatus()          { return status; }
    public static long   getBytesSent()       { return bytesSent; }
    public static long   getBytesRecv()       { return bytesRecv; }

    private static String buildV2RayConfig(String rawJson) {
        try {
            JSONObject c = new JSONObject(rawJson);

            if (c.optBoolean("_v2ray", false)) {
                return c.getString("_config");
            }

            String proto       = c.optString("proto",       "VLESS").toUpperCase();
            String transport   = c.optString("transport",   "ws").toLowerCase();
            String host        = c.optString("host",        "");
            int    port        = c.optInt   ("port",        443);
            String uuid        = c.optString("uuid",        "");
            String sni         = c.optString("sni",         host);
            String bugHost     = c.optString("bug_host",    sni);
            String wsPath      = c.optString("ws_path",     "/ws");
            String serviceName = c.optString("service_name","grpc");

            // Se bug_host vazio, usa sni
            if (bugHost.isEmpty()) bugHost = sni;
            if (bugHost.isEmpty()) bugHost = host;

            // User settings
            String userSettings;
            if (proto.equals("VMESS")) {
                userSettings = "{\"id\":\"" + uuid + "\",\"alterId\":0,\"security\":\"auto\"}";
            } else {
                userSettings = "{\"id\":\"" + uuid + "\",\"encryption\":\"none\",\"flow\":\"\"}";
            }

            // Stream settings baseado no transport
            String streamSettings;
            switch (transport) {
                case "grpc":
                    streamSettings = "{"
                        + "\"network\":\"grpc\","
                        + "\"security\":\"tls\","
                        + "\"tlsSettings\":{\"serverName\":\"" + bugHost + "\",\"allowInsecure\":true},"
                        + "\"grpcSettings\":{\"serviceName\":\"" + serviceName + "\"}"
                        + "}";
                    break;
                case "xhttp":
                    streamSettings = "{"
                        + "\"network\":\"xhttp\","
                        + "\"security\":\"tls\","
                        + "\"tlsSettings\":{\"serverName\":\"" + bugHost + "\",\"allowInsecure\":true},"
                        + "\"xhttpSettings\":{\"path\":\"" + wsPath + "\",\"host\":\"" + bugHost + "\"}"
                        + "}";
                    break;
                case "tcp":
                    streamSettings = "{"
                        + "\"network\":\"tcp\","
                        + "\"security\":\"tls\","
                        + "\"tlsSettings\":{\"serverName\":\"" + bugHost + "\",\"allowInsecure\":true}"
                        + "}";
                    break;
                default: // ws
                    streamSettings = "{"
                        + "\"network\":\"ws\","
                        + "\"security\":\"tls\","
                        + "\"tlsSettings\":{\"serverName\":\"" + bugHost + "\",\"allowInsecure\":true},"
                        + "\"wsSettings\":{\"path\":\"" + wsPath + "\",\"headers\":{\"Host\":\"" + bugHost + "\"}}"
                        + "}";
                    break;
            }

            String protocolName = proto.equals("VMESS") ? "vmess" : "vless";

            return "{"
                + "\"log\":{\"loglevel\":\"warning\"},"
                + "\"inbounds\":[{"
                    + "\"port\":10808,"
                    + "\"protocol\":\"socks\","
                    + "\"settings\":{\"auth\":\"noauth\",\"udp\":true},"
                    + "\"tag\":\"socks\""
                + "}],"
                + "\"outbounds\":[{"
                    + "\"protocol\":\"" + protocolName + "\","
                    + "\"settings\":{"
                        + "\"vnext\":[{"
                            + "\"address\":\"" + host + "\","
                            + "\"port\":" + port + ","
                            + "\"users\":[" + userSettings + "]"
                        + "}]"
                    + "},"
                    + "\"streamSettings\":" + streamSettings + ","
                    + "\"tag\":\"proxy\""
                + "},{"
                    + "\"protocol\":\"freedom\","
                    + "\"tag\":\"direct\""
                + "}],"
                + "\"routing\":{"
                    + "\"rules\":[{"
                        + "\"type\":\"field\","
                        + "\"ip\":[\"geoip:private\"],"
                        + "\"outboundTag\":\"direct\""
                    + "}]"
                + "}"
            + "}";

        } catch (Exception e) {
            Log.e(TAG, "buildV2RayConfig erro: " + e.getMessage());
            return "{}";
        }
    }

    public static void startVpn(NexTunnelVpnService svc, Callback cb) {
        new Thread(() -> {
            try {
                Builder builder = svc.new Builder();
                builder.setSession("NexTunnel");
                builder.addAddress("10.0.0.1", 32);
                builder.addDnsServer("8.8.8.8");
                builder.addDnsServer("1.1.1.1");
                builder.addRoute("0.0.0.0", 0);
                builder.setMtu(1500);

                svc.tunInterface = builder.establish();
                if (svc.tunInterface == null) {
                    throw new Exception("Falha ao criar interface TUN");
                }

                String v2rayJson = buildV2RayConfig(configJson);
                Log.d(TAG, "V2Ray config: " + v2rayJson.substring(0, Math.min(200, v2rayJson.length())));

                coreController = Libv2ray.newCoreController(new CoreCallbackHandler() {
                    public long startup() {
                        Log.i(TAG, "V2Ray core iniciado");
                        return 0;
                    }
                    public long shutdown() {
                        Log.i(TAG, "V2Ray core parado");
                        return 0;
                    }
                    public long onEmitStatus(long level, String msg) {
                        Log.i(TAG, "V2Ray: " + msg);
                        return 0;
                    }
                });

                coreController.startLoop(v2rayJson);

                status = "connected";
                if (cb != null) cb.onConnected();

            } catch (Exception e) {
                Log.e(TAG, "Erro: " + e.getMessage(), e);
                status = "error";
                if (cb != null) cb.onError(e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
            }
        }).start();
    }

    public static void stopVpn(NexTunnelVpnService svc) {
        try {
            if (coreController != null) {
                coreController.stopLoop();
                coreController = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "stopVpn: " + e.getMessage(), e);
        }
        try {
            if (svc != null && svc.tunInterface != null) {
                svc.tunInterface.close();
                svc.tunInterface = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "fechar TUN: " + e.getMessage(), e);
        }
        status = "disconnected";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;
        switch (action) {
            case "com.dexvori.nextunnel.CONNECT":    startVpn(this, null); break;
            case "com.dexvori.nextunnel.DISCONNECT": stopVpn(this); break;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn(this);
        super.onDestroy();
    }
}
