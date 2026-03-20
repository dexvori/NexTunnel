package com.dexvori.nextunnel;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import libv2ray.CoreCallbackHandler;
import libv2ray.CoreController;
import libv2ray.Libv2ray;

public class NexTunnelVpnService extends VpnService {

    private static final String TAG = "NexTunnelVPN";

    private static volatile String configJson  = "{}";
    private static volatile String status      = "disconnected";
    private static volatile long   bytesSent   = 0;
    private static volatile long   bytesRecv   = 0;
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

    public static void startVpn(NexTunnelVpnService svc, Callback cb) {
        new Thread(() -> {
            try {
                Builder builder = svc.new Builder();
                builder.setSession("NexTunnel");
                builder.addAddress("10.0.0.1", 32);
                builder.addDnsServer("8.8.8.8");
                builder.addRoute("0.0.0.0", 0);
                builder.setMtu(1500);

                svc.tunInterface = builder.establish();
                if (svc.tunInterface == null) {
                    throw new Exception("Falha ao criar interface TUN");
                }

                long tunFd = (long) svc.tunInterface.getFd();

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

                coreController.startLoop(configJson, tunFd);

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
