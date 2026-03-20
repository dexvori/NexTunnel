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
    private static NexTunnelVpnService instance;

    public interface Callback {
        void onConnected();
        void onError(String msg);
    }

    public static void setConfig(String json) { configJson = json; }
    public static String getStatus()          { return status; }
    public static long   getBytesSent()       { return bytesSent; }
    public static long   getBytesRecv()       { return bytesRecv; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Seq.setContext(this);
    }

    public static void startVpn(NexTunnelVpnService svc, Callback cb) {
        new Thread(() -> {
            try {
                v2rayPoint = Libv2ray.newV2RayPoint(new V2RayVPNServiceSupportsSet() {
                    @Override
                    public long setup(String conf) {
                        return 0;
                    }
                    @Override
                    public long prepare() {
                        return 0;
                    }
                    @Override
                    public long shutdown() {
                        return 0;
                    }
                    @Override
                    public long protect(long fd) {
                        if (svc != null) svc.protect((int) fd);
                        return 0;
                    }
                    @Override
                    public long onEmitStatus(long l, String s) {
                        Log.i(TAG, "V2Ray: " + s);
                        return 0;
                    }
                }, false);

                v2rayPoint.setConfigureFileContent(configJson);
                v2rayPoint.runLoop(false);

                status = "connected";
                Log.i(TAG, "V2Ray iniciado!");
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
        instance = null;
        super.onDestroy();
    }
}
