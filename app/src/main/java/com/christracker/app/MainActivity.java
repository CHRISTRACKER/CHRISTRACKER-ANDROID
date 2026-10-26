package com.christracker.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private JSONArray pendingMessages;
    private static final int SMS_PERMISSION_REQUEST = 410;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new SmsBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class SmsBridge {
        @JavascriptInterface
        public void sendBulkSms(String json) {
            runOnUiThread(() -> {
                try {
                    pendingMessages = new JSONArray(json);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Invalid SMS list.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (pendingMessages.length() == 0) {
                    Toast.makeText(MainActivity.this, "No messages to send.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_REQUEST);
                } else {
                    sendPendingMessages();
                }
            });
        }
    }

    private void sendPendingMessages() {
        if (pendingMessages == null) return;
        int sent = 0, failed = 0;

        try {
            SmsManager manager = Build.VERSION.SDK_INT >= 31
                ? getSystemService(SmsManager.class)
                : SmsManager.getDefault();

            for (int i = 0; i < pendingMessages.length(); i++) {
                JSONObject item = pendingMessages.getJSONObject(i);
                String number = item.optString("phone", "").trim();
                String message = item.optString("message", "").trim();

                if (number.isEmpty() || message.isEmpty()) {
                    failed++;
                    continue;
                }

                try {
                    java.util.ArrayList<String> parts = manager.divideMessage(message);
                    if (parts.size() == 1) {
                        manager.sendTextMessage(number, null, message, null, null);
                    } else {
                        manager.sendMultipartTextMessage(number, null, parts, null, null);
                    }
                    sent++;
                } catch (Exception ex) {
                    failed++;
                }
            }

            Toast.makeText(this, "SMS submitted: " + sent + "; failed: " + failed,
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "SMS error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingMessages = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendPendingMessages();
            } else {
                pendingMessages = null;
                Toast.makeText(this, "SMS permission not granted.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
