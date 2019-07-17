package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;
import com.audacious_software.passive_data_kit.transmitters.HttpTransmitter;
import com.audacious_software.passive_data_kit.transmitters.Transmitter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.core.content.ContextCompat;

@SuppressWarnings("PointlessBooleanExpression")
public class PassiveDataKit {
    private static final String STORAGE_PATH = "passive-data-kit";
    private static final String GENERATORS_PATH = "generators";
    public static final String NOTIFICATION_CHANNEL_ID = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_CHANNEL_ID";
    public static final String NOTIFICATION_ICON_ID = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_ICON_ID";
    public static final String NOTIFICATION_COLOR = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_COLOR";
    private static final String FIREBASE_DEVICE_TOKEN = "com.audacious_software.passive_data_kit.PassiveDataKit.FIREBASE_DEVICE_TOKEN";

    private Context mContext = null;
    private boolean mStarted = false;
    private boolean mStartForegroundService = false;
    private String mForegroundChannelId = null;
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

    private void initializeNotifications() {
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
            Notification note = ForegroundService.getForegroundNotification(this.mContext, intent);

            NotificationManager notes = (NotificationManager) this.mContext.getSystemService(Context.NOTIFICATION_SERVICE);

            notes.notify(ForegroundService.getNotificationId(), note);
        }
    }

    public void annotateForegroundIntent(Intent intent) {
        if (this.mForegroundChannelId != null) {
            intent.putExtra(PassiveDataKit.NOTIFICATION_CHANNEL_ID, this.mForegroundChannelId);
        }

        if (this.mForegroundIconId != 0) {
            intent.putExtra(PassiveDataKit.NOTIFICATION_ICON_ID, this.mForegroundIconId);
        }

        if (this.mForegroundColor != 0) {
            intent.putExtra(PassiveDataKit.NOTIFICATION_COLOR, this.mForegroundColor);
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
    public void setForegroundServiceChannelId(String channelId) {
        this.mForegroundChannelId = channelId;
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (prefs.contains(PassiveDataKit.FIREBASE_DEVICE_TOKEN)) {
            String token = prefs.getString(PassiveDataKit.FIREBASE_DEVICE_TOKEN, null);

            if (token != null) {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("token", token);

                Logger.getInstance(this.mContext).log("pdk-firebase-token", payload);
            }
        }
    }

    private static class PassiveDataKitHolder {
        @SuppressLint("StaticFieldLeak")
        public static final PassiveDataKit instance = new PassiveDataKit();
    }

    @SuppressWarnings("SameReturnValue")
    public static PassiveDataKit getInstance(Context context)
    {
        PassiveDataKitHolder.instance.setContext(context.getApplicationContext());

        return PassiveDataKitHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }

    public long pendingTransmissions() {
        long pending = 0;

        List<Transmitter> transmitters = Generators.getInstance(this.mContext).activeTransmitters();

        for (Transmitter transmitted : transmitters) {
            pending += transmitted.pendingTransmissions();
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
}
