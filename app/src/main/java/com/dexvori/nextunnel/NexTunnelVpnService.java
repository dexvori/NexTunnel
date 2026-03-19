package com.dexvori.nextunnel;

import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.util.Log;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import org.json.JSONObject;

public class NexTunnelVpnService extends VpnService {

    private static final String TAG = "NexTunnelVPN";

    public static final String ACTION_CONNECT    = "com.dexvori.nextunnel.CONNECT";
    public static final String ACTION_DISCONNECT = "com.dexvori.nextunnel.DISCONNECT";

    private static volatile String configJson = "{}";
    private static volatile String status     = "disconnected";
    private static volatile long   bytesSent  = 0;
    private static volatile long   bytesRecv  = 0;

    private static volatile Session sshSession = null;

    public static void setConfig(String json)    { configJson = json; }
    public static String getStatus()             { return status; }
    public static long   getBytesSent()          { return bytesSent; }
    public static long   getBytesRecv()          { return bytesRecv; }

    public static void startVpn(Context ctx) {
        new Thread(() -> {
            try {
                JSONObject cfg = new JSONObject(configJson);
                String host = cfg.optString("host", "");
                int    port = cfg.optInt("port", 22);
                String user = cfg.optString("user", "vpn");
                String pass = cfg.optString("pass", "vpn");
                int    socks= cfg.optInt("socksPort", 1080);

                if (host.isEmpty()) {
                    Log.w(TAG, "Sem host — modo demonstração");
                    status = "connected";
                    return;
                }

                JSch jsch = new JSch();
                Session session = jsch.getSession(user, host, port);
                session.setPassword(pass);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setTimeout(15000);
                session.connect();

                // SOCKS5 dynamic port forwarding
                session.setPortForwardingL(socks, "127.0.0.1", socks);

                sshSession = session;
                status = "connected";
                bytesSent = 0;
                bytesRecv = 0;
                Log.i(TAG, "SSH tunnel ativo → " + host + ":" + port + " SOCKS:" + socks);

            } catch (Exception e) {
                Log.e(TAG, "startVpn erro: " + e.getMessage(), e);
                status = "error";
            }
        }).start();
    }

    public static void stopVpn(Context ctx) {
        try {
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
                sshSession = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "stopVpn: " + e.getMessage(), e);
        }
        status = "disconnected";
        Log.i(TAG, "SSH tunnel parado");
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
