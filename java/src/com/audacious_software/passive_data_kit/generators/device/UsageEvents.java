package com.audacious_software.passive_data_kit.generators.device;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.Service;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UsageEvents extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-usage-event";
    private static final String GENERATOR_IDENTIFIER_DAILY_SUMMARY = "pdk-usage-event-daily-summary";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_PATH = "pdk-usage-events.sqlite";
    private static final String SAMPLE_INTERVAL = "com.audacious_software.passive_data_kit.generators.device.UsageEvents.SAMPLE_INTERVAL";
    private static final long SAMPLE_INTERVAL_DEFAULT = 300000;
    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.UsageEvents.ENABLED";
    private static final boolean ENABLED_DEFAULT = false;
    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.device.UsageEvents.DATA_RETENTION_PERIOD";;
    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_PACKAGE = "package";
    private static final String HISTORY_EVENT_TYPE = "event_type";
    private static final String HISTORY_EXTRAS = "extras";
    private static final String HISTORY_QUERY_START = "start";

    private static final String OBSCURE_SEED = "com.audacious_software.passive_data_kit.generators.device.UsageEvents.OBSCURE_SEED";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (30L * 24L * 60L * 60L * 1000L);
    private static final String LAST_CONFIGURATION = "com.audacious_software.passive_data_kit.generators.device.UsageEvents.LAST_CONFIGURATION";;

    private static final String DISABLED_APPS = "com.audacious_software.passive_data_kit.generators.device.UsageEvents.DISABLED_APPS";
    private static final String ENABLED_APPS = "com.audacious_software.passive_data_kit.generators.device.UsageEvents.ENABLED_APPS";
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
    private static final String HISTORY_EVENT_EXTRAS = "extras";
    private static final String HISTORY_EVENTS = "events";

    private static UsageEvents sInstance;
    private final SQLiteDatabase mDatabase;
    private long mEarliestTimestamp = 0;

    private final HashMap<String, String> mIdentifierCache = new HashMap<>();
    private ScheduledThreadPoolExecutor mService;

    @Override
    public String getIdentifier() {
        return UsageEvents.GENERATOR_IDENTIFIER;
    }

    public static synchronized UsageEvents getInstance(Context context) {
        if (UsageEvents.sInstance == null) {
            UsageEvents.sInstance = new UsageEvents(context.getApplicationContext());
        }

        return UsageEvents.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public UsageEvents(Context context) {
        super(context);

        synchronized (context.getApplicationContext()) {
            File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

            path = new File(path, UsageEvents.DATABASE_PATH);

            this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

            int version = this.getDatabaseVersion(this.mDatabase);

            switch (version) {
                case 0:
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_usage_events_create_history_table));
            }

            if (version != UsageEvents.DATABASE_VERSION) {
                this.setDatabaseVersion(this.mDatabase, UsageEvents.DATABASE_VERSION);
            }
        }
    }

    public static void start(final Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        if (prefs.getBoolean(UsageEvents.ENABLED, UsageEvents.ENABLED_DEFAULT)) {
            UsageEvents.getInstance(context).startGenerator();
        }
    }

    private void startGenerator() {
        if (this.mService != null) {
            return;
        }

        final UsageEvents me = this;

        this.mService = new ScheduledThreadPoolExecutor(1);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                me.fetchFullHistory(false, 0);
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

        return prefs.getBoolean(UsageEvents.ENABLED, UsageEvents.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (UsageEvents.sInstance == null) {
            return false;
        }

        return UsageEvents.sInstance.mService != null;
    }

    @SuppressLint("InlinedApi")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        if (UsageEvents.hasPermissions(context) == false) {
            actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_usage_stats_permission_required_title), context.getString(R.string.diagnostic_usage_stats_permission_required), new Runnable() {
                @Override
                public void run() {
                    UsageEvents.fetchPermissions(context);
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
        return context.getString(R.string.generator_usage_events);
    }

    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(UsageEvents.DATA_RETENTION_PERIOD, UsageEvents.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = UsageEvents.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(UsageEvents.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(UsageEvents.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, UsageEvents.DATABASE_PATH);

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
            return this.mDatabase.query(UsageEvents.TABLE_HISTORY, cols, where, args, null, null, orderBy);
        } else {
            if (cols == null) {
                cols = new String[0];
            }
        }

        return new MatrixCursor(cols);
    }

    public long getLatestTimestamp() {
        long latest = 0;

        Cursor c = this.queryHistory(null, null, null, UsageEvents.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            int columnIndex = c.getColumnIndex(UsageEvents.HISTORY_OBSERVED);

            if (columnIndex >= 0) {
                latest = c.getLong(columnIndex);
            }

            c.close();
        }

        return latest;
    }

    public long earliestTimestamp() {
        if (this.mEarliestTimestamp == 0) {
            Cursor c = this.queryHistory(null, null, null, UsageEvents.HISTORY_OBSERVED);

            if (c.moveToNext()) {
                int columnIndex = c.getColumnIndex(UsageEvents.HISTORY_OBSERVED);

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

        String obscureSeed = prefs.getString(UsageEvents.OBSCURE_SEED, null);

        if (obscureSeed == null) {
            obscureSeed = RandomStringUtils.randomAlphanumeric(64);

            SharedPreferences.Editor e = prefs.edit();
            e.putString(UsageEvents.OBSCURE_SEED, obscureSeed);
            e.apply();
        }

        String obscured = Toolbox.hash(identifier + obscureSeed);

        this.mIdentifierCache.put(identifier, obscured);

        return obscured;
    }

    public void updateConfig(JSONObject config) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putString(UsageEvents.LAST_CONFIGURATION, config.toString());

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

        e.putBoolean(UsageEvents.ENABLED, enabled);

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

        HashSet<String> disabledApps = new HashSet<>(prefs.getStringSet(UsageEvents.DISABLED_APPS, new HashSet<>()));

        HashSet<String> enabledApps = new HashSet<>(prefs.getStringSet(UsageEvents.ENABLED_APPS, new HashSet<>()));

        if (enabled) {
            disabledApps.remove(packageName);

            enabledApps.add(packageName);
        } else {
            enabledApps.remove(packageName);

            disabledApps.add(packageName);
        }

        e.putStringSet(UsageEvents.DISABLED_APPS, disabledApps);
        e.putStringSet(UsageEvents.ENABLED_APPS, enabledApps);

        e.commit();
    }

    public boolean isAppEnabled(String process) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        Set<String> disabledApps = prefs.getStringSet(UsageEvents.DISABLED_APPS, new HashSet<>());

        if (disabledApps.contains(process)) {
            return false;
        }

        Set<String> enabledApps = prefs.getStringSet(UsageEvents.ENABLED_APPS, new HashSet<>());

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
        e.putLong(UsageEvents.SAMPLE_INTERVAL, interval);
        e.apply();
    }

    public void fetchFullHistory(boolean createDailySummaries, long start) {
        final UsageEvents me = this;

        UsageStatsManager mUsageStatsManager = (UsageStatsManager) me.mContext.getSystemService(Service.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();

        long end = now;

        if (createDailySummaries) {
            if (start == 0) {
                android.app.usage.UsageEvents usageEvents = mUsageStatsManager.queryEvents(0, now);
                android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();

                if (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event);

                    start = event.getTimeStamp();
                }
            }

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(start);

            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            start = calendar.getTimeInMillis();

            calendar.add(Calendar.DATE, 1);

            end = calendar.getTimeInMillis() - 1;
        } else {
            if (start == 0) {
                start = me.getLatestTimestamp();
            }

            if ((end - start) > (24 * 60 * 60 * 1000)) {
                start = end - (24 * 60 * 60 * 1000);
            }
        }

        Bundle dailyEvents = new Bundle();
        dailyEvents.putLong(UsageEvents.HISTORY_OBSERVED, System.currentTimeMillis());
        dailyEvents.putLong(UsageEvents.HISTORY_QUERY_START, start);

        ArrayList<Bundle> dailyEventsList = new ArrayList<>();

        android.app.usage.UsageEvents usageEvents = mUsageStatsManager.queryEvents(start + 1, end);
        android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);

            String packageName = event.getPackageName();

            String eventType = "unknown:" + event.getEventType();

            switch (event.getEventType()) {
                case android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED:
                    eventType = UsageEvents.EVENT_ACTIVITY_RESUMED;
                    break;
                case android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED:
                    eventType = UsageEvents.EVENT_ACTIVITY_PAUSED;
                    break;
                case android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED:
                    eventType = UsageEvents.EVENT_ACTIVITY_STOPPED;
                    break;
                case android.app.usage.UsageEvents.Event.CONFIGURATION_CHANGE:
                    eventType = UsageEvents.EVENT_CONFIGURATION_CHANGE;
                    break;
                case android.app.usage.UsageEvents.Event.DEVICE_STARTUP:
                    eventType = UsageEvents.EVENT_DEVICE_STARTUP;
                    break;
                case android.app.usage.UsageEvents.Event.DEVICE_SHUTDOWN:
                    eventType = UsageEvents.EVENT_DEVICE_SHUTDOWN;
                    break;
                case android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_START:
                    eventType = UsageEvents.EVENT_FOREGROUND_SERVICE_START;
                    break;
                case android.app.usage.UsageEvents.Event.FOREGROUND_SERVICE_STOP:
                    eventType = UsageEvents.EVENT_FOREGROUND_SERVICE_STOP;
                    break;
                case android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN:
                    eventType = UsageEvents.EVENT_KEYGUARD_HIDDEN;
                    break;
                case android.app.usage.UsageEvents.Event.KEYGUARD_SHOWN:
                    eventType = UsageEvents.EVENT_KEYGUARD_SHOWN;
                    break;
                case android.app.usage.UsageEvents.Event.SCREEN_INTERACTIVE:
                    eventType = UsageEvents.EVENT_SCREEN_INTERACTIVE;
                    break;
                case android.app.usage.UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                    eventType = UsageEvents.EVENT_SCREEN_NON_INTERACTIVE;
                    break;
                case android.app.usage.UsageEvents.Event.SHORTCUT_INVOCATION:
                    eventType = UsageEvents.EVENT_SHORTCUT_INVOCATION;
                    break;
                case android.app.usage.UsageEvents.Event.STANDBY_BUCKET_CHANGED:
                    eventType = UsageEvents.EVENT_STANDBY_BUCKET_CHANGED;
                    break;
                case android.app.usage.UsageEvents.Event.USER_INTERACTION:
                    eventType = UsageEvents.EVENT_USER_INTERACTION;
                    break;
                case android.app.usage.UsageEvents.Event.NONE:
                    eventType = UsageEvents.EVENT_NONE;
                    break;
            }

            if (me.filterEvent(packageName, eventType) == false) {
                if (me.isAppEnabled(packageName) == false) {
                    packageName = me.obscureIdentifier(packageName);
                }

                ContentValues values = new ContentValues();
                values.put(UsageEvents.HISTORY_OBSERVED, event.getTimeStamp());
                values.put(UsageEvents.HISTORY_EVENT_TYPE, eventType);
                values.put(UsageEvents.HISTORY_PACKAGE, packageName);

                me.mDatabase.insert(UsageEvents.TABLE_HISTORY, null, values);

                Bundle update = new Bundle();
                update.putLong(UsageEvents.HISTORY_OBSERVED, event.getTimeStamp());
                update.putString(UsageEvents.HISTORY_PACKAGE, packageName);
                update.putString(UsageEvents.HISTORY_EVENT_TYPE, eventType);

                // Enable once able to test on Android 35 / Vanilla Ice Cream
                // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                //     update.putParcelable(UsageEvents.HISTORY_EVENT_EXTRAS, event.getExtras());
                // }

                if (createDailySummaries) {
                    dailyEventsList.add(update);
                } else {
                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(UsageEvents.GENERATOR_IDENTIFIER, event.getTimeStamp(), update);
               }
            }
        }

        if (createDailySummaries) {
            dailyEvents.putParcelableArrayList(UsageEvents.HISTORY_EVENTS, dailyEventsList);

            Generators.getInstance(me.mContext).notifyGeneratorUpdated(UsageEvents.GENERATOR_IDENTIFIER_DAILY_SUMMARY, event.getTimeStamp(), dailyEvents);

            if (end < now) {
                me.fetchFullHistory(true, (end + 1));
            }
         } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

            final long sampleInterval = prefs.getLong(UsageEvents.SAMPLE_INTERVAL, UsageEvents.SAMPLE_INTERVAL_DEFAULT);

            if (me.mService == null) {
                me.mService = new ScheduledThreadPoolExecutor(1);
            }

            me.mService.schedule(new Runnable() {
                @Override
                public void run() {
                    me.fetchFullHistory(false, 0);
                }
            }, sampleInterval, TimeUnit.MILLISECONDS);
        }
    }
}
