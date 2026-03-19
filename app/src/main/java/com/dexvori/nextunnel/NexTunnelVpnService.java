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

import org.json.JSONObject;

import ru.tim06.vpnprotocols.openvpn.OpenVPNConfig;
import ru.tim06.vpnprotocols.openvpn.OpenVPNService;

public class NexTunnelVpnService extends VpnService {

    private static final String TAG        = "NexTunnelVPN";
    private static final String CHANNEL_ID = "nextunnel_vpn";
    private static final int    NOTIF_ID   = 1;

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
        try {
            JSONObject cfg  = new JSONObject(configJson);
            String host     = cfg.optString("host",  "");
            int    port     = cfg.optInt   ("port",  443);
            String user     = cfg.optString("user",  "vpn");
            String pass     = cfg.optString("pass",  "vpn");
            String sni      = cfg.optString("sni",   "");

            if (host.isEmpty()) {
                Log.w(TAG, "Sem host configurado — modo demonstração");
                status = "connected";
                return;
            }

            String ovpnConfig = buildOvpnConfig(host, port, user, pass, sni);
            OpenVPNConfig openVPNConfig = new OpenVPNConfig(ovpnConfig);
            OpenVPNService.startService(ctx, openVPNConfig);

            status = "connected";
            bytesSent = 0;
            bytesRecv = 0;
            Log.i(TAG, "OpenVPN iniciado → " + host + ":" + port);

        } catch (Exception e) {
            Log.e(TAG, "startVpn: " + e.getMessage(), e);
            status = "error";
        }
    }

    public static void stopVpn(Context ctx) {
        try {
            OpenVPNService.stopService(ctx);
        } catch (Exception e) {
            Log.e(TAG, "stopVpn: " + e.getMessage(), e);
        }
        status = "disconnected";
        Log.i(TAG, "OpenVPN parado");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        switch (intent.getAction() == null ? "" : intent.getAction()) {
            case ACTION_CONNECT:    startVpn(this);    break;
            case ACTION_DISCONNECT: stopVpn(this);     break;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() { stopVpn(this); super.onDestroy(); }

    private static String buildOvpnConfig(String host, int port,
                                           String user, String pass,
                                           String sni) {
        StringBuilder sb = new StringBuilder();
        sb.append("client\n");
        sb.append("dev tun\n");
        sb.append("proto tcp\n");
        sb.append("remote ").append(host).append(" ").append(port).append("\n");
        sb.append("resolv-retry infinite\n");
        sb.append("nobind\n");
        sb.append("persist-key\n");
        sb.append("persist-tun\n");
        sb.append("verb 3\n");
        sb.append("cipher AES-256-CBC\n");
        sb.append("auth SHA256\n");
        sb.append("tls-client\n");
        sb.append("ns-cert-type server\n");
        sb.append("connect-retry 3\n");
        sb.append("connect-timeout 10\n");

        if (!sni.isEmpty()) {
            sb.append("tls-ext-sni ").append(sni).append("\n");
        }

        if (!user.isEmpty() && !pass.isEmpty()) {
            sb.append("<auth-user-pass>\n");
            sb.append(user).append("\n");
            sb.append(pass).append("\n");
            sb.append("</auth-user-pass>\n");
        }

        sb.append("<ca>\n");
        sb.append("-----BEGIN CERTIFICATE-----\n");
        sb.append("MIIBszCCAVmgAwIBAgIJAIzWMBmWtILuMA0GCSqGSIb3DQEBCwUAMCMxITAfBgNV\n");
        sb.append("BAMTGFZQTkdhdGVTZXJ2ZXJDZXJOMjAxNjAxMB4XDTE2MDExODA5MDExOVoXDTI2\n");
        sb.append("MDExNTA5MDExOVowIzEhMB8GA1UEAxMYVlBOR2F0ZVNlcnZlckNlcm4yMDE2MDEw\n");
        sb.append("XDANBgkqhkiG9w0BAQEFAANLADBIAkEA2raVmH59eOs6wRBbLSIasNz/7b5exzKP\n");
        sb.append("idPKtRWcOoYRFgPRLzBkn0oBHNTMp+RSRWR9e1pOGGEn63CdywIDAQABo2YwZDAd\n");
        sb.append("BgNVHQ4EFgQURHhHlxPiT7xs3dqZMApC2bFMiRgwHwYDVR0jBBgwFoAURHhHlxPi\n");
        sb.append("T7xs3dqZMApC2bFMiRgwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMC\n");
        sb.append("AQYwDQYJKoZIhvcNAQELBQADQQCfmvFsoSHFaOGzWaFjWOcHSvLInxv9GLXX4I4g\n");
        sb.append("FrGAubsUAQ6rkIqxNEe3lIqIWK/W7beFCQqQxSCjH08H\n");
        sb.append("-----END CERTIFICATE-----\n");
        sb.append("</ca>\n");

        return sb.toString();
    }

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "NexTunnel VPN", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotif(String title, String text) {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, flags);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).build();
    }
}
