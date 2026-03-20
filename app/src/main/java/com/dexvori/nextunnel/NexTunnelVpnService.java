package com.dexvori.nextunnel;

import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import go.Seq;
import libv2ray.Libv2ray;
import libv2ray.V2RayPoint;
import libv2ray.V2RayVPNServiceSupportsSet;

public class NexTunnelVpnService extends VpnService {

    private static final String TAG = "NexTunnelVPN";

    private static volatile String configJson = "{}";
    private static volatile String status     = "disconnected";
    private static volatile long   bytesSent  = 0;
    private static volatile long   bytesRecv  = 0;

    private static V2RayPoint v2rayPoint;

    public interface Callback {
        void onConnected();
        void onError(String msg);
    }

    public static void setConfig(String json) { configJson = json; }
    public static String getStatus()          { return status; }
    public static long   getBytesSent()       { return bytesSent; }
    public static long   getBytesRecv()       { return bytesRecv; }

    public static void startVpn(NexTunnelVpnService svc, Callback cb) {
        new Thread(() -> {
            try {
                Seq.setContext(svc);

                v2rayPoint = Libv2ray.newV2RayPoint(new V2RayVPNServiceSupportsSet() {
                    @Override
                    public boolean isVpnLaunched() {
                        return svc != null;
                    }

                    @Override
                    public void onEmitStatus(long l, String s) {
                        Log.i(TAG, "V2Ray status: " + s);
                    }

                    @Override
                    public void shutdown() {
                        stopVpn(svc);
                    }

                    @Override
                    public String getVpnInterfaceName() {
                        return "tun0";
                    }

                    @Override
                    public boolean protect(long fd) {
                        return svc.protect((int) fd);
                    }

                    @Override
                    public String getDnsServers() {
                        return "1.1.1.1,8.8.8.8";
                    }

                    @Override
                    public String getExcludedOutboundTags() {
                        return "";
                    }

                    @Override
                    public String getEnabledExtension() {
                        return "";
                    }
                }, false);

                v2rayPoint.setConfigureFileContent(configJson);
                v2rayPoint.runLoop(false);

                status = "connected";
                Log.i(TAG, "V2Ray iniciado com sucesso");
                if (cb != null) cb.onConnected();

            } catch (Exception e) {
                Log.e(TAG, "V2Ray erro: " + e.getMessage(), e);
                status = "error";
                if (cb != null) cb.onError(e.getMessage() != null ? e.getMessage() : "Erro desconhecido");
            }
        }).start();
    }

    public static void stopVpn(NexTunnelVpnService svc) {
        try {
            if (v2rayPoint != null) {
                v2rayPoint.stopLoop();
                v2rayPoint = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "stopVpn: " + e.getMessage(), e);
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
