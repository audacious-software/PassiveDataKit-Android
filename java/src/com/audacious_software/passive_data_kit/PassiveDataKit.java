package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.audacious_software.passive_data_kit.activities.MaintenanceActivity;
import com.audacious_software.passive_data_kit.activities.TransmissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;
import com.audacious_software.passive_data_kit.transmitters.HttpTransmitter;
import com.audacious_software.passive_data_kit.transmitters.Transmitter;
import com.audacious_software.passive_data_kit.R;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import javax.net.ssl.SSLException;

import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.tls.HandshakeCertificates;

@SuppressWarnings("PointlessBooleanExpression")
public class PassiveDataKit {
    private static final String STORAGE_PATH = "passive-data-kit";
    private static final String GENERATORS_PATH = "generators";
    public static final String NOTIFICATION_ICON_ID = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_ICON_ID";
    public static final String NOTIFICATION_COLOR = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_COLOR";
    private static final String FIREBASE_DEVICE_TOKEN = "com.audacious_software.passive_data_kit.PassiveDataKit.FIREBASE_DEVICE_TOKEN";

    private static final String MAINTENANCE_DIALOG_ENABLED = "com.audacious_software.passive_data_kit.PassiveDataKit.MAINTENANCE_DIALOG_ENABLED";
    private static final boolean MAINTENANCE_DIALOG_ENABLED_DEFAULT = false;
    private static final String MAINTENANCE_DIALOG_MESSAGE = "com.audacious_software.passive_data_kit.PassiveDataKit.MAINTENANCE_DIALOG_MESSAGE";
    private static final String MAINTENANCE_DIALOG_START_HOUR = "com.audacious_software.passive_data_kit.PassiveDataKit.MAINTENANCE_DIALOG_START_HOUR";
    private static final int MAINTENANCE_DIALOG_START_HOUR_DEFAULT = 3;
    private static final String MAINTENANCE_DIALOG_DURATION = "com.audacious_software.passive_data_kit.PassiveDataKit.MAINTENANCE_DIALOG_DURATION";
    private static final long MAINTENANCE_DIALOG_DURATION_DEFAULT = 3 * 60 * 60 * 1000;
    private static final String MAINTENANCE_DIALOG_FORCE_INTERVAL = "com.audacious_software.passive_data_kit.PassiveDataKit.MAINTENANCE_DIALOG_FORCE_INTERVAL";
    private static final long MAINTENANCE_DIALOG_FORCE_INTERVAL_DEFAULT = 2 * 24 * 60 * 60 * 1000;
    private static final String MAINTENANCE_LAST_APPEARANCE = "com.audacious_software.passive_data_kit.PassiveDataKit.MAINTENANCE_LAST_APPEARANCE";

    private static final String LAST_TRANSMISSION_DIALOG_ENABLED = "com.audacious_software.passive_data_kit.PassiveDataKit.LAST_TRANSMISSION_DIALOG_ENABLED";
    private static final boolean LAST_TRANSMISSION_DIALOG_ENABLED_DEFAULT = false;

    private static final String LAST_TRANSMISSION_WARNING_TRANSMISSION_INTERVAL = "com.audacious_software.passive_data_kit.PassiveDataKit.LAST_TRANSMISSION_WARNING_TRANSMISSION_INTERVAL";
    private static final long LAST_TRANSMISSION_WARNING_TRANSMISSION_INTERVAL_DEFAULT = 24 * 60 * 60 * 1000;

    private static final String LAST_TRANSMISSION_WARNING_DIALOG_INTERVAL = "com.audacious_software.passive_data_kit.PassiveDataKit.LAST_TRANSMISSION_WARNING_DIALOG_INTERVAL";
    private static final long LAST_TRANSMISSION_WARNING_DIALOG_INTERVAL_DEFAULT = 24 * 60 * 60 * 1000;

    private static final String LAST_TRANSMISSION_WARNING_LAST_DIALOG = "com.audacious_software.passive_data_kit.PassiveDataKit.LAST_TRANSMISSION_WARNING_LAST_DIALOG";

    private static final String REMOTE_CONFIGURATION_URL = "com.audacious_software.passive_data_kit.PassiveDataKit.REMOTE_CONFIGURATION_URL";
    private static final String REMOTE_CONFIGURATION_UPDATE_INTERVAL = "com.audacious_software.passive_data_kit.PassiveDataKit.REMOTE_CONFIGURATION_UPDATE_INTERVAL";
    private static final long REMOTE_CONFIGURATION_UPDATE_INTERVAL_DEFAULT = 60 * 60 * 1000;

    private static final String REMOTE_CONFIGURATION_LAST_UPDATE = "com.audacious_software.passive_data_kit.PassiveDataKit.REMOTE_CONFIGURATION_LAST_UPDATE";
    private static final String CACHED_REMOTE_CONFIGURATION = "com.audacious_software.passive_data_kit.PassiveDataKit.CACHED_REMOTE_CONFIGURATION";

    private static final String REMOTE_CONFIGURATION_IDENTIFIER = "com.audacious_software.passive_data_kit.PassiveDataKit.REMOTE_CONFIGURATION_IDENTIFIER";
    private static final String REMOTE_CONFIGURATION_CONTEXT = "com.audacious_software.passive_data_kit.PassiveDataKit.REMOTE_CONFIGURATION_CONTEXT";

    private Context mContext = null;
    private boolean mStarted = false;
    private boolean mStartForegroundService = false;
    private int mForegroundIconId = 0;
    private int mForegroundColor = 0;
    private PendingIntent mForegroundPendingIntent = null;
    private boolean mAlwaysNotify = false;

    public void start() {
        synchronized (this) {
            if (!this.mStarted) {
                this.mStarted = true;

                final PassiveDataKit me = this;

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        Generators.getInstance(me.mContext).start();
                        Logger.getInstance(me.mContext);
                    }
                };

                Thread t = new Thread(r);
                t.start();

                this.initializeNotifications();

                Log.i("PDK", "Passive Data Kit is running...");
            }
        }
    }

    public void initializeNotifications() {
        boolean notificationStarted = false;

        Intent intent = new Intent(ForegroundService.ACTION_START_SERVICE, null, this.mContext, ForegroundService.class);

        this.annotateForegroundIntent(intent);

        if (this.mStartForegroundService || this.mAlwaysNotify) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this.mContext, intent);

                notificationStarted = true;
            }
        }

        if (this.mAlwaysNotify && notificationStarted == false) {
            final PassiveDataKit me = this;

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Notification note = ForegroundService.getForegroundNotification(me.mContext, intent);

                    NotificationManagerCompat notes = NotificationManagerCompat.from(me.mContext);

                    notes.notify(ForegroundService.getNotificationId(), note);
                }
            };

            Thread t = new Thread(r, "pdk-notification-init");
            t.start();
        }
    }

    public void annotateForegroundIntent(Intent intent) {
        if (intent != null) {
            if (this.mForegroundIconId != 0) {
                intent.putExtra(PassiveDataKit.NOTIFICATION_ICON_ID, this.mForegroundIconId);
            }

            if (this.mForegroundColor != 0) {
                intent.putExtra(PassiveDataKit.NOTIFICATION_COLOR, this.mForegroundColor);
            }
        }
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>(Generators.getInstance(context).diagnostics(context));
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File getGeneratorsStorage(Context context) {
        File path = new File(context.getFilesDir(), PassiveDataKit.STORAGE_PATH);
        path = new File(path, PassiveDataKit.GENERATORS_PATH);

        if (path.exists() == false) {
            path.mkdirs();
        }

        return path;
    }

    @SuppressWarnings("unused")
    public void setStartForegroundService(boolean startService) {
        this.mStartForegroundService = startService;
    }

    public void setAlwaysNotify(boolean always) {
        this.mAlwaysNotify = always;
    }

    @SuppressWarnings("unused")
    public void setForegroundServiceIcon(int resourceId) {
        this.mForegroundIconId = resourceId;
    }

    @SuppressWarnings("unused")
    public void setForegroundServiceColor(int color) {
        this.mForegroundColor = color;
    }

    public PendingIntent getForegroundPendingIntent() {
        return this.mForegroundPendingIntent;
    }

    public void setForegroundPendingIntent(PendingIntent pendingIntent) {
        this.mForegroundPendingIntent = pendingIntent;
    }

    public void updateFirebaseDeviceToken(String token) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putString(PassiveDataKit.FIREBASE_DEVICE_TOKEN, token);
        e.apply();

        this.transmitTokens();
    }

    public void transmitTokens() {
        this.transmitTokens(false);
    }

    public void transmitTokens(boolean isRetry) {
        final PassiveDataKit me = this;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (prefs.contains(PassiveDataKit.FIREBASE_DEVICE_TOKEN)) {
            String token = prefs.getString(PassiveDataKit.FIREBASE_DEVICE_TOKEN, null);

            if (token != null) {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("token", token);

                Logger.getInstance(this.mContext).log("pdk-firebase-token", payload);
            }
        } else {
            try {
                FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w("PDK", "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        String token = task.getResult();

                        me.updateFirebaseDeviceToken(token);
                    }
                });
            } catch (IllegalStateException ex) {
                ex.printStackTrace();

                if (isRetry == false) {
                    FirebaseApp.initializeApp(this.mContext);

                    this.transmitTokens(true);
                }
            }
        }
    }

    private static X509Certificate convertToX509Cert(String certificateString) throws CertificateException {
        X509Certificate certificate = null;
        CertificateFactory cf = null;
        try {
            if (certificateString != null && !certificateString.trim().isEmpty()) {
                certificateString = certificateString.replace("-----BEGIN CERTIFICATE-----\n", "")
                        .replace("-----END CERTIFICATE-----", ""); // NEED FOR PEM FORMAT CERT STRING
                byte[] certificateData = Base64.decode(certificateString, Base64.DEFAULT);
                cf = CertificateFactory.getInstance("X509");
                certificate = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateData));
            }
        } catch (CertificateException e) {
            throw new CertificateException(e);
        }
        return certificate;
    }

    public HandshakeCertificates fetchTrustedCertificates() {
        HandshakeCertificates.Builder builder = new HandshakeCertificates.Builder();

        builder.addPlatformTrustedCertificates();

        Resources resources = this.mContext.getResources();

        String[] certificates = resources.getStringArray(R.array.pdk_trusted_certificates);

        for (String certificate : certificates) {
            try {
                builder.addTrustedCertificate(PassiveDataKit.convertToX509Cert(certificate));
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        }

        return builder.build();
    }

    private static class PassiveDataKitHolder {
        @SuppressLint("StaticFieldLeak")
        public static final PassiveDataKit instance = new PassiveDataKit();
    }

    @SuppressWarnings("SameReturnValue")
    public static synchronized PassiveDataKit getInstance(Context context) {
        if (context != null) {
            PassiveDataKitHolder.instance.setContext(context.getApplicationContext());
        }

        return PassiveDataKitHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }

    public long pendingTransmissions() {
        long pending = 0;

        List<Transmitter> transmitters = Generators.getInstance(this.mContext).activeTransmitters();

        for (Transmitter transmitter : transmitters) {
            pending += transmitter.pendingTransmissions();
        }

        return pending;
    }

    public long pendingTransmissionSize() {
        long pending = 0;

        List<Transmitter> transmitters = Generators.getInstance(this.mContext).activeTransmitters();

        for (Transmitter transmitter : transmitters) {
            pending += transmitter.pendingTransmissionSize();
        }

        return pending;
    }

    public void updateGenerators(JSONObject config) {
        Generators.getInstance(this.mContext).updateGenerators(config);
    }

    public List<Transmitter> fetchTransmitters(String userId, String appName, JSONObject config) {
        ArrayList<Transmitter> transmitters = new ArrayList<>();

        try {
            if (config.has("transmitters")) {
                JSONArray transmitterDefs = config.getJSONArray("transmitters");

                for (int i = 0; i < transmitterDefs.length(); i++) {
                    JSONObject transmitterDef = transmitterDefs.getJSONObject(i);

                    if ("pdk-http-transmitter".equals(transmitterDef.getString("type"))) {
                        HttpTransmitter transmitter = new HttpTransmitter();

                        HashMap<String, String> options = new HashMap<>();
                        options.put(HttpTransmitter.USER_ID, userId);

                        if (transmitterDef.has("upload-uri")) {
                            options.put(HttpTransmitter.UPLOAD_URI, transmitterDef.getString("upload-uri"));
                        }

                        if (transmitterDef.has("compression")) {
                            if (transmitterDef.getBoolean("compression")) {
                                options.put(HttpTransmitter.COMPRESS_PAYLOADS, "true");
                            } else {
                                options.put(HttpTransmitter.COMPRESS_PAYLOADS, "false");
                            }
                        }

                        if (transmitterDef.has("wifi-only")) {
                            boolean wifiOnly = transmitterDef.getBoolean("wifi-only");

                            if (wifiOnly) {
                                options.put(HttpTransmitter.WIFI_ONLY, "true");
                            } else {
                                options.put(HttpTransmitter.WIFI_ONLY, "false");
                            }
                        }

                        if (transmitterDef.has("charging-only")) {
                            boolean chargingOnly = transmitterDef.getBoolean("charging-only");

                            if (chargingOnly) {
                                options.put(HttpTransmitter.CHARGING_ONLY, "true");
                            } else {
                                options.put(HttpTransmitter.CHARGING_ONLY, "false");
                            }
                        }

                        if (transmitterDef.has("use-external-storage")) {
                            boolean useExternal = transmitterDef.getBoolean("use-external-storage");

                            if (useExternal) {
                                options.put(HttpTransmitter.USE_EXTERNAL_STORAGE, "true");
                            } else {
                                options.put(HttpTransmitter.USE_EXTERNAL_STORAGE, "false");
                            }
                        }

                        if (transmitterDef.has("strict-ssl-verification")) {
                            boolean strictSsl = transmitterDef.getBoolean("strict-ssl-verification");

                            if (strictSsl) {
                                options.put(HttpTransmitter.STRICT_SSL_VERIFICATION, "true");
                            } else {
                                options.put(HttpTransmitter.STRICT_SSL_VERIFICATION, "false");
                            }
                        }

                        if (transmitterDef.has("max-bundle-size")) {
                            int maxBundleSize = transmitterDef.getInt("max-bundle-size");

                            options.put(HttpTransmitter.MAX_BUNDLE_SIZE, "" + maxBundleSize);
                        }

                        if (transmitterDef.has("device-key") && transmitterDef.has("server-key")) {
                            options.put(HttpTransmitter.PRIVATE_KEY, transmitterDef.getString("device-key"));
                            options.put(HttpTransmitter.PUBLIC_KEY, transmitterDef.getString("server-key"));
                        }


                        try {
                            String version = this.mContext.getPackageManager().getPackageInfo(this.mContext.getPackageName(), 0).versionName;

                            options.put(HttpTransmitter.USER_AGENT_NAME, appName + " " + version);
                        } catch (PackageManager.NameNotFoundException ex) {
                            AppEvent.getInstance(this.mContext).logThrowable(ex);
                        }

                        transmitter.initialize(this.mContext, options);

                        transmitters.add(transmitter);
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return transmitters;
    }

    public void logMaintenanceAppearance(String source, long timestamp) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();

        e.putLong(PassiveDataKit.MAINTENANCE_LAST_APPEARANCE, timestamp);
        e.apply();

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("source", source);

        Logger.getInstance(this.mContext).log("pdk-log-app-appearance", payload);
    }

    public void nudgeMaintenanceDialogs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (prefs.getBoolean(PassiveDataKit.MAINTENANCE_DIALOG_ENABLED, PassiveDataKit.MAINTENANCE_DIALOG_ENABLED_DEFAULT)) {
            int hour = prefs.getInt(PassiveDataKit.MAINTENANCE_DIALOG_START_HOUR, PassiveDataKit.MAINTENANCE_DIALOG_START_HOUR_DEFAULT);
            long interval = prefs.getLong(PassiveDataKit.MAINTENANCE_DIALOG_DURATION, PassiveDataKit.MAINTENANCE_DIALOG_DURATION_DEFAULT);
            long forceInterval = prefs.getLong(PassiveDataKit.MAINTENANCE_DIALOG_FORCE_INTERVAL, PassiveDataKit.MAINTENANCE_DIALOG_FORCE_INTERVAL_DEFAULT);
            String message = prefs.getString(PassiveDataKit.MAINTENANCE_DIALOG_MESSAGE, null);

            long now = System.currentTimeMillis();

            long lastAppearance = prefs.getLong(PassiveDataKit.MAINTENANCE_LAST_APPEARANCE, 0);

            long elapsed = now - lastAppearance;

            if (lastAppearance == 0) {
                this.logMaintenanceAppearance("pdk-initial-launch", System.currentTimeMillis());
            } else {
                Intent launchIntent = new Intent(this.mContext, MaintenanceActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (message != null) {
                    launchIntent.putExtra(MaintenanceActivity.MESSAGE_TEXT, message);
                }

                if (elapsed > forceInterval) {
                    this.logMaintenanceAppearance("force-immediate-launch", System.currentTimeMillis());

                    this.mContext.startActivity(launchIntent);

                    Logger.getInstance(this.mContext).log("pdk-maintenance-force-appearance", new HashMap<>());
                } else if (elapsed > ((interval * 4) / 5)) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.set(Calendar.HOUR_OF_DAY, hour);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);

                    long intervalStart = calendar.getTimeInMillis();
                    long intervalEnd = intervalStart + interval;

                    if (now >= intervalStart && now <= intervalEnd) {
                        Intent intent = this.mContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

                        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

                        if (plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS) {
                            this.logMaintenanceAppearance("force-opportunistic-launch", System.currentTimeMillis());

                            this.mContext.startActivity(launchIntent);

                            Logger.getInstance(this.mContext).log("pdk-maintenance-opportunistic-appearance", new HashMap<>());
                        }
                    }
                }
            }
        }
    }

     public void enableMaintenanceDialogs(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(PassiveDataKit.MAINTENANCE_DIALOG_ENABLED, enable);
        e.apply();
    }

    public void configureMaintenanceDialog(String message, int hour, long duration, long forceInterval) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putString(PassiveDataKit.MAINTENANCE_DIALOG_MESSAGE, message);
        e.putInt(PassiveDataKit.MAINTENANCE_DIALOG_START_HOUR, hour);
        e.putLong(PassiveDataKit.MAINTENANCE_DIALOG_DURATION, duration);
        e.putLong(PassiveDataKit.MAINTENANCE_DIALOG_FORCE_INTERVAL, forceInterval);

        e.apply();
    }

    public void enableLastTransmissionDialog(boolean enable, long warningInterval, long dialogInterval) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(PassiveDataKit.LAST_TRANSMISSION_DIALOG_ENABLED, enable);
        e.putLong(PassiveDataKit.LAST_TRANSMISSION_WARNING_TRANSMISSION_INTERVAL, warningInterval);
        e.putLong(PassiveDataKit.LAST_TRANSMISSION_WARNING_DIALOG_INTERVAL, warningInterval);
        e.apply();
    }

    public synchronized void nudgeLastTransmissionDialogs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (prefs.getBoolean(PassiveDataKit.LAST_TRANSMISSION_DIALOG_ENABLED, PassiveDataKit.LAST_TRANSMISSION_DIALOG_ENABLED_DEFAULT)) {
            long now = System.currentTimeMillis();

            boolean showDialog = false;

            List<Transmitter> transmitters = Generators.getInstance(this.mContext).activeTransmitters();

            long transmissionInterval = prefs.getLong(PassiveDataKit.LAST_TRANSMISSION_WARNING_TRANSMISSION_INTERVAL, PassiveDataKit.LAST_TRANSMISSION_WARNING_TRANSMISSION_INTERVAL_DEFAULT);

            long latestTransmission = 0;

            for (Transmitter transmitter : transmitters) {
                long lastTransmission = transmitter.lastSuccessfulTransmission();

                if (lastTransmission > latestTransmission) {
                    latestTransmission = lastTransmission;
                }

                if (now - lastTransmission > transmissionInterval) {
                    showDialog = true;
                }
            }

            if (showDialog && latestTransmission > 0) {
                long dialogInterval = prefs.getLong(PassiveDataKit.LAST_TRANSMISSION_WARNING_DIALOG_INTERVAL, PassiveDataKit.LAST_TRANSMISSION_WARNING_DIALOG_INTERVAL_DEFAULT);

                long lastDialog = prefs.getLong(PassiveDataKit.LAST_TRANSMISSION_WARNING_LAST_DIALOG, 0);

                if (now - lastDialog > dialogInterval) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putLong(PassiveDataKit.LAST_TRANSMISSION_WARNING_LAST_DIALOG, now);
                    e.apply();

                    Intent launchIntent = new Intent(this.mContext, TransmissionActivity.class);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    launchIntent.putExtra(TransmissionActivity.LAST_TRANSMISSION, latestTransmission);

                    String appName = this.mContext.getString(this.mContext.getApplicationInfo().labelRes);
                    launchIntent.putExtra(TransmissionActivity.APP_NAME, appName);

                    this.mContext.startActivity(launchIntent);

                    HashMap<String, Object> payload = new HashMap<>();
                    payload.put("last_transmission", latestTransmission);

                    Logger.getInstance(this.mContext).log("pdk-dialog-upload-overdue", payload);
                }
            }
        }
    }

    public void transmitData(boolean force) {
        List<Transmitter> transmitters = Generators.getInstance(this.mContext).activeTransmitters();

        for (Transmitter transmitter : transmitters) {
            transmitter.transmit(force);
        }
    }

    public void setRemoteOptionsUrl(String remoteConfigurationUrl, boolean forceUpdate) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putString(PassiveDataKit.REMOTE_CONFIGURATION_URL, remoteConfigurationUrl);

        if (forceUpdate) {
            e.remove(PassiveDataKit.REMOTE_CONFIGURATION_LAST_UPDATE);
            e.apply();

        }

        e.apply();

        this.updateRemoteOptions();
    }

    public void updateRemoteOptions() {
        final PassiveDataKit me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

                String configUrl = prefs.getString(PassiveDataKit.REMOTE_CONFIGURATION_URL, null);
                String identifier = me.getRemoteConfigurationIdentifier();

                long now = System.currentTimeMillis();

                long lastUpdate = prefs.getLong(PassiveDataKit.REMOTE_CONFIGURATION_LAST_UPDATE, 0);
                long interval = prefs.getLong(PassiveDataKit.REMOTE_CONFIGURATION_UPDATE_INTERVAL, PassiveDataKit.REMOTE_CONFIGURATION_UPDATE_INTERVAL_DEFAULT);

                if (identifier != null && configUrl != null && now - lastUpdate > interval) {
                    String context = me.getRemoteConfigurationContext();

                    SharedPreferences.Editor e = prefs.edit();
                    e.putLong(PassiveDataKit.REMOTE_CONFIGURATION_LAST_UPDATE, now);
                    e.apply();

                    try {
                        HandshakeCertificates certificates = PassiveDataKit.getInstance(me.mContext).fetchTrustedCertificates();

                        OkHttpClient client = new OkHttpClient.Builder()
                                .connectTimeout(3, TimeUnit.MINUTES)
                                .writeTimeout(60, TimeUnit.SECONDS)
                                .readTimeout(3, TimeUnit.MINUTES)
                                .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager())
                                .build();

                        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);

                        builder = builder.addFormDataPart("id", identifier);

                        if (context != null) {
                            builder = builder.addFormDataPart("context", context);
                        }

                        RequestBody requestBody = builder.build();

                        String version = me.mContext.getPackageManager().getPackageInfo(me.mContext.getPackageName(), 0).versionName;
                        String appName = me.mContext.getString(me.mContext.getApplicationInfo().labelRes);

                        String userAgent = appName + " " + version;

                        Request request = new Request.Builder()
                                .removeHeader("User-Agent")
                                .addHeader("User-Agent", userAgent)
                                .url(configUrl)
                                .post(requestBody)
                                .build();

                        Response response = client.newCall(request).execute();

                        int code = response.code();

                        ResponseBody body = response.body();

                        String bodyString = body.string();

                        response.body().close();

                        if (code >= 200 && code < 300) {
                            JSONObject responseObject = new JSONObject(bodyString);

                            if (responseObject.has("generators") || responseObject.has("transmitters")) {
                                e = prefs.edit();
                                e.putString(PassiveDataKit.CACHED_REMOTE_CONFIGURATION, responseObject.toString(2));
                                e.apply();
                            }
                        }
                    } catch (SSLException ex) {

                    } catch (JSONException ex) {

                    } catch (PackageManager.NameNotFoundException ex) {

                    } catch (IOException ex) {

                    }
                }

                try {
                    JSONObject configuration = new JSONObject(prefs.getString(PassiveDataKit.CACHED_REMOTE_CONFIGURATION, "{}"));

                    if (configuration.has("generators")) {
                        Generators.getInstance(me.mContext).updateGenerators(configuration);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        Thread t = new Thread(r, "pdk-configuration-refresh");
        t.start();
    }

    public JSONObject remoteOptions() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        String configuration = prefs.getString(PassiveDataKit.CACHED_REMOTE_CONFIGURATION, "{}");

        try {
            return new JSONObject(configuration);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return new JSONObject();
    }

    public String getRemoteConfigurationIdentifier() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        return prefs.getString(PassiveDataKit.REMOTE_CONFIGURATION_IDENTIFIER, null);
    }

    public void setRemoteConfigurationIdentifier(String identifier) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();

        if (identifier == null) {
            e.remove(PassiveDataKit.REMOTE_CONFIGURATION_IDENTIFIER);
        } else {
            e.putString(PassiveDataKit.REMOTE_CONFIGURATION_IDENTIFIER, identifier);
        }

        e.apply();
    }

    public String getRemoteConfigurationContext() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        return prefs.getString(PassiveDataKit.REMOTE_CONFIGURATION_CONTEXT, null);
    }

    public void setRemoteConfigurationContext(String identifier) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();

        if (identifier == null) {
            e.remove(PassiveDataKit.REMOTE_CONFIGURATION_CONTEXT);
        } else {
            e.putString(PassiveDataKit.REMOTE_CONFIGURATION_CONTEXT, identifier);
        }

        e.apply();
    }
}
