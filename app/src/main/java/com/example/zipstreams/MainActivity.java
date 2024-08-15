package com.example.zipstreams;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;

public class MainActivity extends AppCompatActivity {

    private static final String CUSTOM_USER_AGENT = "ChromeZip/1.0";
    private static final Pattern ZIP_COM_PATTERN = Pattern.compile(".*\\.zipstreams\\.net");
    private static final Pattern MEME_COM_PATTERN = Pattern.compile(".*\\.memesyndicate\\.(com|to)");

    private long downloadID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebView webView = findViewById(R.id.web_view);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.setInitialScale(95);

        // Set custom User-Agent
        webView.getSettings().setUserAgentString(CUSTOM_USER_AGENT);

        webView.setWebViewClient(new CustomWebViewClient());
        webView.loadUrl("https://atv.zipstreams.net");

        checkForUpdates();

        // Register receiver to listen for the download completion
        registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void checkForUpdates() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            String currentVersion = BuildConfig.VERSION_NAME; // Get the current app version
            String latestVersion = getLatestVersionFromServer(); // This runs on a background thread

            if (!latestVersion.equals(currentVersion)) {
                // Update the UI on the main thread
                runOnUiThread(() -> {
                    // Prompt the user to update
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Update Available")
                            .setMessage("A new version is available. Please update to the latest version.")
                            .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    downloadAndInstallApk("https://atv.zipstreams.net/apk/ZipStreams.apk"); // Replace with your APK URL
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }
        });
    }

    private String getLatestVersionFromServer() {
        String latestVersion = "1.0.0"; // Default version (in case of failure)
        try {
            URL url = new URL("https://atv.zipstreams.net/apk/latest-version"); // Replace with your endpoint
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // 5 seconds timeout
            connection.setReadTimeout(5000);

            InputStream in = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Assuming the server response is the version string, e.g., "2.0.0"
            latestVersion = response.toString().trim();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return latestVersion;
    }

    private void downloadAndInstallApk(String url) {
        // Check if the app can install unknown sources
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            boolean canInstallApps = getPackageManager().canRequestPackageInstalls();
            if (!canInstallApps) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                return;
            }
        }
        new Thread(() -> {
            try {
                // Simulating download (Replace with actual download logic)
                URL downloadUrl = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
                connection.connect();
                InputStream input = connection.getInputStream();

                File apkFile = new File(getExternalFilesDir(null), "ZipStreams.apk");
                FileOutputStream output = new FileOutputStream(apkFile);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                output.close();
                input.close();

                Log.d("Main", "APK downloaded to: " + apkFile.getAbsolutePath());

                // Now install the APK
                Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", apkFile);
                Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                installIntent.setData(apkUri);
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(installIntent);

                Log.d("Main", "Install intent triggered.");
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Main", "Error downloading or installing APK: " + e.getMessage());
            }
        }).start();
    }

    private BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadID == id) {
                installAPK();
            }
        }
    };

    private void installAPK() {
        Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ZipStreams.apk"));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        WebView webView = findViewById(R.id.web_view);
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private class CustomWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                URL url = new URL(request.getUrl().toString());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Remove the Origin header for all domains except p.com and y.com
                if (!ZIP_COM_PATTERN.matcher(url.getHost()).matches() && !MEME_COM_PATTERN.matcher(url.getHost()).matches()) {
                    connection.setRequestProperty("Origin", "");
                }
                if (ZIP_COM_PATTERN.matcher(url.getHost()).matches() || MEME_COM_PATTERN.matcher(url.getHost()).matches()) {
                    connection.setRequestProperty("Origin", "https://atv.zipstreams.net");
                    // Set the custom User-Agent for the intercepted request
                    connection.setRequestProperty("User-Agent", CUSTOM_USER_AGENT);
                } else {
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.3");
                }

                connection.connect();

                InputStream inputStream = connection.getInputStream();

                // Flatten the headers
                Map<String, String> headers = new HashMap<>();
                for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                    if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                        headers.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
                    }
                }

                // Remove any existing 'Access-Control-Allow-Origin' header
                if (headers.containsKey("access-control-allow-origin")) {
                    headers.remove("access-control-allow-origin");
                }

                // Add the 'Access-Control-Allow-Origin' header
                headers.put("Access-Control-Allow-Origin", "*");

                // Log the headers to ensure it's correctly set
                Log.d("Main", "Headers after modification: " + headers.toString());

                return new WebResourceResponse(
                        connection.getContentType().split(";")[0],
                        connection.getContentEncoding(),
                        connection.getResponseCode(),
                        connection.getResponseMessage(),
                        headers,
                        inputStream
                );
            } catch (IOException e) {
                e.printStackTrace();
                return super.shouldInterceptRequest(view, request);
            }
        }
    }
}
