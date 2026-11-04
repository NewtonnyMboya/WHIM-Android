package com.infinitycyber.whim;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.util.Log;
import java.util.Arrays;
import java.io.File;
import java.io.FileOutputStream;
import android.util.Base64;
import org.json.JSONObject;
import java.util.zip.Inflater;
import java.io.ByteArrayOutputStream;

public class MainActivity extends Activity {
    private static final int FILE_PICKER = 721;
    private static final int CAMERA_PERMISSION = 722;
    private static final int CREATE_DOWNLOAD = 723;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermission;
    private byte[] pendingDownloadBytes;
    private String pendingDownloadMime;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) s.setSafeBrowsingEnabled(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        webView.addJavascriptInterface(new AndroidBridge(), "WHIMAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }
            private boolean routeUrl(Uri uri) {
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
                if (scheme.equals("file") || scheme.equals("content") || scheme.equals("about") ||
                    scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) return false;
                try {
                    Intent external = new Intent(Intent.ACTION_VIEW, uri);
                    external.addCategory(Intent.CATEGORY_BROWSABLE);
                    startActivity(external); // HTTPS links and redirects open in Chrome/default browser.
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "No browser or app can open this link.", Toast.LENGTH_LONG).show();
                } catch (Exception e) { Log.w("WHIM", "External link failed", e); }
                return true;
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // Never bypass invalid certificate warnings.
                Toast.makeText(MainActivity.this, "Secure connection could not be verified.", Toast.LENGTH_LONG).show();
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript("(function(){try{window.print=function(){if(window.WHIMAndroid){WHIMAndroid.print();}};}catch(e){}})();", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(intent, FILE_PICKER);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Unable to open file picker.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView popup = new WebView(MainActivity.this);
                WebSettings ps = popup.getSettings();
                ps.setJavaScriptEnabled(true);
                ps.setDomStorageEnabled(true);
                ps.setJavaScriptCanOpenWindowsAutomatically(true);
                ps.setSupportMultipleWindows(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                        webView.loadUrl(req.getUrl().toString());
                        return true;
                    }
                    @SuppressWarnings("deprecation")
                    @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        webView.loadUrl(url); return true;
                    }
                });
                popup.setWebChromeClient(new WebChromeClient() {
                    @Override public void onCloseWindow(WebView window) {
                        window.destroy();
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                boolean camera = Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
                if (!camera) { request.deny(); return; }
                if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingPermission = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
                }
            }
        });

        webView.setDownloadListener((url, userAgent, disposition, mimeType, length) -> {
            try {
                String filename = URLUtil.guessFileName(url, disposition, mimeType);
                if (url.startsWith("blob:") || url.startsWith("data:")) {
                    String js = "(async function(){try{const r=await fetch(" + JSONObject.quote(url) + ");if(!r.ok)throw new Error('Generated download could not be read');const b=await r.blob();const fr=new FileReader();fr.onload=function(){const d=String(fr.result||'').split(',');if(!d[1])throw new Error('Generated file is empty');WHIMAndroid.saveFile(" + JSONObject.quote(filename) + ",b.type||" + JSONObject.quote(mimeType == null ? "application/octet-stream" : mimeType) + ",d[1]);};fr.onerror=function(){throw new Error('Could not read generated file')};fr.readAsDataURL(b);}catch(e){alert('Download failed: '+(e&&e.message?e.message:'Unsupported generated file'));}})();";
                    webView.evaluateJavascript(js, null);
                    return;
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this, "This download link type is not supported by Android.", Toast.LENGTH_LONG).show(); return;
                }
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setTitle(filename);
                req.setDescription("Downloading from WHIM");
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) req.addRequestHeader("Cookie", cookie);
                if (userAgent != null) req.addRequestHeader("User-Agent", userAgent);
                if (mimeType != null) req.setMimeType(mimeType);
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
                Toast.makeText(this, "Download started. Check Downloads.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        if (state == null) webView.loadUrl("file:///android_asset/index.html");
        else webView.restoreState(state);
    }

    private final class AndroidBridge {
        // WebView compatibility fallback for XLSX DEFLATE entries on older Android System WebView.
        @JavascriptInterface public String inflateRawBase64(String compressedBase64) {
            Inflater inflater = new Inflater(true);
            try {
                byte[] input = Base64.decode(compressedBase64, Base64.DEFAULT);
                inflater.setInput(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(input.length * 3, 1048576));
                byte[] buffer = new byte[8192];
                while (!inflater.finished()) {
                    int count = inflater.inflate(buffer);
                    if (count > 0) {
                        if (output.size() + count > 64 * 1024 * 1024) throw new IllegalStateException("Workbook entry is too large");
                        output.write(buffer, 0, count);
                    } else if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IllegalArgumentException("Invalid compressed workbook entry");
                    }
                }
                return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                Log.e("WHIM", "XLSX decompression failed", e);
                return "";
            } finally { inflater.end(); }
        }
        @JavascriptInterface public void saveFile(String filename, String mimeType, String base64) {
            try {
                final String safeName = filename == null ? "WHIM-export.bin" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                final byte[] data = Base64.decode(base64, Base64.DEFAULT);
                final String type = (mimeType == null || mimeType.trim().isEmpty()) ? "application/octet-stream" : mimeType;
                runOnUiThread(() -> {
                    try {
                        if (pendingDownloadBytes != null) {
                            Toast.makeText(MainActivity.this, "Finish the current save operation first.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        pendingDownloadBytes = data;
                        pendingDownloadMime = type;
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType(type);
                        intent.putExtra(Intent.EXTRA_TITLE, safeName);
                        intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                        startActivityForResult(intent, CREATE_DOWNLOAD);
                    } catch (Exception e) {
                        pendingDownloadBytes = null; pendingDownloadMime = null;
                        Toast.makeText(MainActivity.this, "Unable to open Android save dialog: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e("WHIM", "File export preparation failed", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
        @JavascriptInterface public void print() {
            runOnUiThread(() -> {
                try {
                    PrintManager pm = (PrintManager)getSystemService(Context.PRINT_SERVICE);
                    if (pm == null) throw new IllegalStateException("Android print service unavailable");
                    pm.print("WHIM document", webView.createPrintDocumentAdapter("WHIM document"),
                        new PrintAttributes.Builder().build());
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Unable to open print dialog: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_DOWNLOAD) {
            byte[] bytes = pendingDownloadBytes;
            String mime = pendingDownloadMime;
            pendingDownloadBytes = null; pendingDownloadMime = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && bytes != null) {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(data.getData(), "w")) {
                    if (out == null) throw new IllegalStateException("Android could not open the selected destination");
                    out.write(bytes); out.flush();
                    Toast.makeText(this, "Export saved successfully. Find it in your selected folder, such as Downloads.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Log.e("WHIM", "Could not save export", e);
                    Toast.makeText(this, "Export save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Export cancelled. No file was saved.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == FILE_PICKER && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i=0; i<count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) result = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(result); fileCallback = null;
        }
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == CAMERA_PERMISSION && pendingPermission != null) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED)
                pendingPermission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else pendingPermission.deny();
            pendingPermission = null;
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); if (webView != null) webView.saveState(out);
    }
    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (pendingPermission != null) { pendingPermission.deny(); pendingPermission = null; }
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
