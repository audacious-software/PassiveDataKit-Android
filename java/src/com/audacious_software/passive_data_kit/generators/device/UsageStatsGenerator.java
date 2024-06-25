package com.audacious_software.passive_data_kit.generators.device;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.R;
import com.audacious_software.passive_data_kit.Toolbox;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;

import org.apache.commons.lang3.RandomStringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UsageStatsGenerator extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-usage-stats";
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_PATH = "pdk-usage-stats.sqlite";
    private static final String SAMPLE_INTERVAL = "com.audacious_software.passive_data_kit.generators.device.UsageStatsGenerator.SAMPLE_INTERVAL";
    private static final long SAMPLE_INTERVAL_DEFAULT = 300000;
    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.UsageStatsGenerator.ENABLED";
    private static final boolean ENABLED_DEFAULT = false;
    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.device.UsageStatsGenerator.DATA_RETENTION_PERIOD";;
    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_PACKAGE = "package";
    private static final String HISTORY_EVENT_TYPE = "event_type";
    private static final String HISTORY_EXTRAS = "extras";

    private static final String OBSCURE_SEED = "com.audacious_software.passive_data_kit.generators.device.UsageStatsGenerator.OBSCURE_SEED";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);
    private static final String LAST_CONFIGURATION = "com.audacious_software.passive_data_kit.generators.device.UsageStatsGenerator.LAST_CONFIGURATION";;

    private static final String DISABLED_APPS = "com.audacious_software.passive_data_kit.generators.device.UsageStatsGenerator.DISABLED_APPS";
    private static final String ENABLED_APPS = "com.audacious_software.passive_data_kit.generators.device.UsageStatsGenerator.ENABLED_APPS";
    private static final String EVENT_ACTIVITY_RESUMED = "activity-resumed";
    private static final String EVENT_ACTIVITY_PAUSED = "activity-paused";
    private static final String EVENT_ACTIVITY_STOPPED = "activity-stopped";
    private static final String EVENT_CONFIGURATION_CHANGE = "configuration-change";
    private static final String EVENT_DEVICE_STARTUP = "device-startup";
    private static final String EVENT_DEVICE_SHUTDOWN = "device-shutdown";
    private static final String EVENT_FOREGROUND_SERVICE_START = "foreground-service-start";
    private static final String EVENT_FOREGROUND_SERVICE_STOP = "foreground-service-stop";
    private static final String EVENT_KEYGUARD_HIDDEN = "keyguard-hidden";
    private static final String EVENT_KEYGUARD_SHOWN = "keyguard-shown";
    private static final String EVENT_SCREEN_INTERACTIVE = "screen-interactive";
    private static final String EVENT_SCREEN_NON_INTERACTIVE = "screen-non-interactive";
    private static final String EVENT_SHORTCUT_INVOCATION = "shortcut-invocation";
    private static final String EVENT_STANDBY_BUCKET_CHANGED = "standby-bucket-changed";
    private static final String EVENT_USER_INTERACTION = "user-interaction";
    private static final String EVENT_NONE = "none";

    private static UsageStatsGenerator sInstance;
    private final SQLiteDatabase mDatabase;
    private long mEarliestTimestamp = 0;

    private final HashMap<String, String> mIdentifierCache = new HashMap<>();
    private ScheduledThreadPoolExecutor mService;

    @Override
    public String getIdentifier() {
        return UsageStatsGenerator.GENERATOR_IDENTIFIER;
    }

    public static synchronized UsageStatsGenerator getInstance(Context context) {
        if (UsageStatsGenerator.sInstance == null) {
            UsageStatsGenerator.sInstance = new UsageStatsGenerator(context.getApplicationContext());
        }

        return UsageStatsGenerator.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public UsageStatsGenerator(Context context) {
        super(context);

        synchronized (context.getApplicationContext()) {
            File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

            path = new File(path, UsageStatsGenerator.DATABASE_PATH);

            this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

            int version = this.getDatabaseVersion(this.mDatabase);

            switch (version) {
                case 0:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_usage_stats_create_history_table));
            }

            if (version != UsageStatsGenerator.DATABASE_VERSION) {
                this.setDatabaseVersion(this.mDatabase, UsageStatsGenerator.DATABASE_VERSION);
            }
        }
    }

    public static void start(final Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        if (prefs.getBoolean(UsageStatsGenerator.ENABLED, UsageStatsGenerator.ENABLED_DEFAULT)) {
            UsageStatsGenerator.getInstance(context).startGenerator();
        }
    }

    private void startGenerator() {
        if (this.mService != null) {
            return;
        }

        final UsageStatsGenerator me = this;

        this.mService = new ScheduledThreadPoolExecutor(1);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        final long sampleInterval = prefs.getLong(UsageStatsGenerator.SAMPLE_INTERVAL, UsageStatsGenerator.SAMPLE_INTERVAL_DEFAULT);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                UsageStatsManager mUsageStatsManager = (UsageStatsManager) me.mContext.getSystemService(Service.USAGE_STATS_SERVICE);
                long now = System.currentTimeMillis();

                long start = me.getLatestTimestamp();

                if ((now - start) > (24 * 60 * 60 * 1000)) {
                    start = now - (24 * 60 * 60 * 1000);
                }

                UsageEvents usageEvents = mUsageStatsManager.queryEvents(start + 1, now);
                UsageEvents.Event event = new UsageEvents.Event();

                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event);

                    String packageName = event.getPackageName();

                    String eventType = "unknown";

                    switch (event.getEventType()) {
                        case UsageEvents.Event.ACTIVITY_RESUMED:
                            eventType = UsageStatsGenerator.EVENT_ACTIVITY_RESUMED;
                            break;
                        case UsageEvents.Event.ACTIVITY_PAUSED:
                            eventType = UsageStatsGenerator.EVENT_ACTIVITY_PAUSED;
                            break;
                        case UsageEvents.Event.ACTIVITY_STOPPED:
                            eventType = UsageStatsGenerator.EVENT_ACTIVITY_STOPPED;
                            break;
                        case UsageEvents.Event.CONFIGURATION_CHANGE:
                            eventType = UsageStatsGenerator.EVENT_CONFIGURATION_CHANGE;
                            break;
                        case UsageEvents.Event.DEVICE_STARTUP:
                            eventType = UsageStatsGenerator.EVENT_DEVICE_STARTUP;
                            break;
                        case UsageEvents.Event.DEVICE_SHUTDOWN:
                            eventType = UsageStatsGenerator.EVENT_DEVICE_SHUTDOWN;
                            break;
                        case UsageEvents.Event.FOREGROUND_SERVICE_START:
                            eventType = UsageStatsGenerator.EVENT_FOREGROUND_SERVICE_START;
                            break;
                        case UsageEvents.Event.FOREGROUND_SERVICE_STOP:
                            eventType = UsageStatsGenerator.EVENT_FOREGROUND_SERVICE_STOP;
                            break;
                        case UsageEvents.Event.KEYGUARD_HIDDEN:
                            eventType = UsageStatsGenerator.EVENT_KEYGUARD_HIDDEN;
                            break;
                        case UsageEvents.Event.KEYGUARD_SHOWN:
                            eventType = UsageStatsGenerator.EVENT_KEYGUARD_SHOWN;
                            break;
                        case UsageEvents.Event.SCREEN_INTERACTIVE:
                            eventType = UsageStatsGenerator.EVENT_SCREEN_INTERACTIVE;
                            break;
                        case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                            eventType = UsageStatsGenerator.EVENT_SCREEN_NON_INTERACTIVE;
                            break;
                        case UsageEvents.Event.SHORTCUT_INVOCATION:
                            eventType = UsageStatsGenerator.EVENT_SHORTCUT_INVOCATION;
                            break;
                        case UsageEvents.Event.STANDBY_BUCKET_CHANGED:
                            eventType = UsageStatsGenerator.EVENT_STANDBY_BUCKET_CHANGED;
                            break;
                        case UsageEvents.Event.USER_INTERACTION:
                            eventType = UsageStatsGenerator.EVENT_USER_INTERACTION;
                            break;
                        case UsageEvents.Event.NONE:
                            eventType = UsageStatsGenerator.EVENT_NONE;
                            break;
                    }

                    if (me.filterEvent(packageName, eventType) == false) {
                        if (me.isAppEnabled(packageName) == false) {
                            packageName = me.obscureIdentifier(packageName);
                        }

                        ContentValues values = new ContentValues();
                        values.put(UsageStatsGenerator.HISTORY_OBSERVED, event.getTimeStamp());
                        values.put(UsageStatsGenerator.HISTORY_EVENT_TYPE, eventType);
                        values.put(UsageStatsGenerator.HISTORY_PACKAGE, packageName);

                        me.mDatabase.insert(UsageStatsGenerator.TABLE_HISTORY, null, values);

                        Bundle update = new Bundle();
                        update.putLong(UsageStatsGenerator.HISTORY_OBSERVED, event.getTimeStamp());
                        update.putString(UsageStatsGenerator.HISTORY_PACKAGE, packageName);
                        update.putString(UsageStatsGenerator.HISTORY_EVENT_TYPE, eventType);

                        Generators.getInstance(me.mContext).notifyGeneratorUpdated(UsageStatsGenerator.GENERATOR_IDENTIFIER, update);
                    }
                }

                me.mService.schedule(this, sampleInterval, TimeUnit.MILLISECONDS);
            }
        };

        me.mService.schedule(runnable, 5000, TimeUnit.MILLISECONDS);
    }

    private void stopGenerator() {
        if (this.mService == null) {
            return;
        }

        this.mService.shutdown();

        this.mService = null;
    }

    private boolean filterEvent(String packageName, String eventType) {
        return false;
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(UsageStatsGenerator.ENABLED, UsageStatsGenerator.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (UsageStatsGenerator.sInstance == null) {
            return false;
        }

        return UsageStatsGenerator.sInstance.mService != null;
    }

    @SuppressLint("InlinedApi")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        if (UsageStatsGenerator.hasPermissions(context) == false) {
            actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_usage_stats_permission_required_title), context.getString(R.string.diagnostic_usage_stats_permission_required), new Runnable() {
                @Override
                public void run() {
                    UsageStatsGenerator.fetchPermissions(context);
                }
            }));
        }

        return actions;
    }

    public static void fetchPermissions(final Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static boolean hasPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow("android:get_usage_stats", android.os.Process.myUid(), context.getPackageName());

            if (mode != AppOpsManager.MODE_ALLOWED) {
                return false;
            }
        }

        return true;
    }

    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_usage_stats_application);
    }

    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(UsageStatsGenerator.DATA_RETENTION_PERIOD, UsageStatsGenerator.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = UsageStatsGenerator.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(UsageStatsGenerator.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(UsageStatsGenerator.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, UsageStatsGenerator.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return null;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        if (this.mDatabase != null) {
            return this.mDatabase.query(UsageStatsGenerator.TABLE_HISTORY, cols, where, args, null, null, orderBy);
        } else {
            if (cols == null) {
                cols = new String[0];
            }
        }

        return new MatrixCursor(cols);
    }

    public long getLatestTimestamp() {
        long latest = 0;

        Cursor c = this.queryHistory(null, null, null, UsageStatsGenerator.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            int columnIndex = c.getColumnIndex(UsageStatsGenerator.HISTORY_OBSERVED);

            if (columnIndex >= 0) {
                latest = c.getLong(columnIndex);
            }

            c.close();
        }

        return latest;
    }

    public long earliestTimestamp() {
        if (this.mEarliestTimestamp == 0) {
            Cursor c = this.queryHistory(null, null, null, UsageStatsGenerator.HISTORY_OBSERVED);

            if (c.moveToNext()) {
                int columnIndex = c.getColumnIndex(UsageStatsGenerator.HISTORY_OBSERVED);

                if (columnIndex >= 0) {
                    this.mEarliestTimestamp = c.getLong(columnIndex);
                }
            }

            c.close();
        }

        return this.mEarliestTimestamp;
    }

    public String obscureIdentifier(String identifier) {
        if (this.mIdentifierCache.containsKey(identifier)) {
            return this.mIdentifierCache.get(identifier);
        }

        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        String obscureSeed = prefs.getString(UsageStatsGenerator.OBSCURE_SEED, null);

        if (obscureSeed == null) {
            obscureSeed = RandomStringUtils.randomAlphanumeric(64);

            SharedPreferences.Editor e = prefs.edit();
            e.putString(UsageStatsGenerator.OBSCURE_SEED, obscureSeed);
            e.apply();
        }

        String obscured = Toolbox.hash(identifier + obscureSeed);

        this.mIdentifierCache.put(identifier, obscured);

        return obscured;
    }

    public void updateConfig(JSONObject config) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putString(UsageStatsGenerator.LAST_CONFIGURATION, config.toString());

        e.apply();

        try {
            if (config.has("enabled")) {
                this.setEnabled(config.getBoolean("enabled"));

                config.remove("enabled");
            }

            if (config.has("sample-interval")) {
                this.setSampleInterval(config.getLong("sample-interval"));

                config.remove("sample-interval");
            }

            if (config.has("included-apps")) {
                JSONArray apps = config.getJSONArray("included-apps");

                for (int i = 0; i < apps.length(); i++) {
                    String app = apps.getString(i);

                    this.enableApp(app);
                }

                config.remove("included-apps");
            }

            if (config.has("excluded-apps")) {
                JSONArray apps = config.getJSONArray("excluded-apps");

                for (int i = 0; i < apps.length(); i++) {
                    String app = apps.getString(i);

                    this.disableApp(app);
                }

                config.remove("excluded-apps");
            }
        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    private void setEnabled(boolean enabled) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(UsageStatsGenerator.ENABLED, enabled);

        e.apply();

        if (enabled) {
            this.startGenerator();
        } else {
            this.stopGenerator();
        }
    }

    public void disableApp(String packageName) {
        this.setAppEnabled(packageName, false);
    }

    public void enableApp(String packageName) {
        this.setAppEnabled(packageName, true);
    }

    private void setAppEnabled(String packageName, boolean enabled) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        HashSet<String> disabledApps = new HashSet<>(prefs.getStringSet(UsageStatsGenerator.DISABLED_APPS, new HashSet<>()));

        HashSet<String> enabledApps = new HashSet<>(prefs.getStringSet(UsageStatsGenerator.ENABLED_APPS, new HashSet<>()));

        if (enabled) {
            disabledApps.remove(packageName);

            enabledApps.add(packageName);
        } else {
            enabledApps.remove(packageName);

            disabledApps.add(packageName);
        }

        e.putStringSet(UsageStatsGenerator.DISABLED_APPS, disabledApps);
        e.putStringSet(UsageStatsGenerator.ENABLED_APPS, enabledApps);

        e.commit();
    }

    public boolean isAppEnabled(String process) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        Set<String> disabledApps = prefs.getStringSet(UsageStatsGenerator.DISABLED_APPS, new HashSet<>());

        if (disabledApps.contains(process)) {
            return false;
        }

        Set<String> enabledApps = prefs.getStringSet(UsageStatsGenerator.ENABLED_APPS, new HashSet<>());

        if (enabledApps.contains(process)) {
            return true;
        }

        if (disabledApps.contains("*")) {
            return false;
        }

        return true;
    }

    public void setSampleInterval(long interval) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putLong(UsageStatsGenerator.SAMPLE_INTERVAL, interval);
        e.apply();
    }
}
