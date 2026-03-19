package com.dexvori.nextunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class NexTunnelVpnService extends VpnService {

    private static final String TAG        = "NexTunnelVPN";
    private static final String CHANNEL_ID = "nextunnel_vpn";

    public static final String ACTION_CONNECT    = "com.dexvori.nextunnel.CONNECT";
    public static final String ACTION_DISCONNECT = "com.dexvori.nextunnel.DISCONNECT";

    private static volatile String configJson = "{}";
    private static volatile String status     = "disconnected";
    private static volatile long   bytesSent  = 0;
    private static volatile long   bytesRecv  = 0;

    public static void setConfig(String json)    { configJson = json; }
    public static String getStatus()             { return status; }
    public static long   getBytesSent()          { return bytesSent; }
    public static long   getBytesRecv()          { return bytesRecv; }

    public static void startVpn(Context ctx) {
        status = "connected";
        bytesSent = 0;
        bytesRecv = 0;
        Log.i(TAG, "VPN iniciada (modo stub)");
    }

    public static void stopVpn(Context ctx) {
        status = "disconnected";
        Log.i(TAG, "VPN parada");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (action == null) return START_NOT_STICKY;
        switch (action) {
            case ACTION_CONNECT:    startVpn(this); break;
            case ACTION_DISCONNECT: stopVpn(this);  break;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn(this);
        super.onDestroy();
    }
}
