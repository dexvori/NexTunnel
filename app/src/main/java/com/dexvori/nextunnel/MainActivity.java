package com.dexvori.nextunnel;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String TAG              = "NexTunnel";
    private static final int    VPN_REQUEST_CODE = 100;

    private WebView webView;
    private NexTunnelVpnService vpnService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serviceBound = true;
            Log.i(TAG, "VPN Service conectado");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            vpnService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        // Iniciar e ligar ao serviço
        Intent serviceIntent = new Intent(this, NexTunnelVpnService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startV2Ray();
        }
    }

    void jsCallback(final String js) {
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private void startV2Ray() {
        Intent intent = new Intent(this, NexTunnelVpnService.class);
        intent.setAction("com.dexvori.nextunnel.CONNECT");
        startService(intent);

        NexTunnelVpnService.startVpn(null, new NexTunnelVpnService.Callback() {
            @Override
            public void onConnected() {
                jsCallback("NexTunnelBridge.onConnected()");
            }
            @Override
            public void onError(String msg) {
                jsCallback("NexTunnelBridge.onError('" + msg.replace("'", "\\'") + "')");
            }
        });
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void connect(String configJson) {
            try {
                NexTunnelVpnService.setConfig(configJson);
                runOnUiThread(() -> {
                    Intent perm = VpnService.prepare(MainActivity.this);
                    if (perm != null) {
                        startActivityForResult(perm, VPN_REQUEST_CODE);
                    } else {
                        startV2Ray();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "connect error: " + e.getMessage(), e);
                jsCallback("NexTunnelBridge.onError('" + e.getMessage() + "')");
            }
        }

        @JavascriptInterface
        public void disconnect() {
            Intent intent = new Intent(MainActivity.this, NexTunnelVpnService.class);
            intent.setAction("com.dexvori.nextunnel.DISCONNECT");
            startService(intent);
            jsCallback("NexTunnelBridge.onDisconnected()");
        }

        @JavascriptInterface
        public String getStatus() {
            return NexTunnelVpnService.getStatus();
        }

        @JavascriptInterface
        public String getStats() {
            return "{\"sent\":" + NexTunnelVpnService.getBytesSent() +
                   ",\"recv\":"  + NexTunnelVpnService.getBytesRecv() + "}";
        }

        @JavascriptInterface
        public void showToast(String msg) {
            runOnUiThread(() ->
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }
    }
}
