package com.audacious_software.passive_data_kit.generators.device;

import android.Manifest;
import android.app.IntentService;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.R;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NotificationEvents extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-notification-events";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.NotificationEvents.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.device.NotificationEvents.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);


    private static final String DATABASE_PATH = "pdk-notification-events.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";

    public static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_PACKAGE = "package";
    private static final String HISTORY_CHANNEL = "channel";
    private static final String HISTORY_ACTION = "action";

    private static final String HISTORY_ACTION_POSTED = "posted";
    private static final String HISTORY_ACTION_REMOVED = "removed";

    private static final String HISTORY_REASON = "reason";
    private static final String HISTORY_REASON_APP_CANCEL = "app_cancel";
    private static final String HISTORY_REASON_APP_CANCEL_ALL = "app_cancel_all";
    private static final String HISTORY_REASON_USER_CANCEL = "user_cancel";
    private static final String HISTORY_REASON_USER_CANCEL_ALL = "user_cancel_all";
    private static final String HISTORY_REASON_USER_CLICK = "user_click";
    private static final String HISTORY_REASON_CHANNEL_BANNED = "channel_ban";
    private static final String HISTORY_REASON_ERROR = "error";
    private static final String HISTORY_REASON_GROUP_OPTIMIZATION = "group_optimization";
    private static final String HISTORY_REASON_GROUP_SUMMARY_CANCEL = "group_cancel";
    private static final String HISTORY_REASON_LISTENER_CANCEL = "listener_cancel";
    private static final String HISTORY_REASON_LISTENER_CANCEL_ALL = "listener_cancel_all";
    private static final String HISTORY_REASON_PACKAGE_BANNED = "package_banned";
    private static final String HISTORY_REASON_PACKAGE_CHANGED = "package_changed";
    private static final String HISTORY_REASON_PACKAGE_SUSPENDED = "package_suspended";
    private static final String HISTORY_REASON_PROFILE_OFF = "profile_turned_off";
    private static final String HISTORY_REASON_SNOOZED = "snoozed";
    private static final String HISTORY_REASON_TIMEOUT = "timeout";
    private static final String HISTORY_REASON_USER_STOP = "user_contect_stopped";

    private static NotificationEvents sInstance = null;

    private SQLiteDatabase mDatabase = null;

    private long mLatestTimestamp = -1;
    private String mLastNotificationString = "";

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return NotificationEvents.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static synchronized NotificationEvents getInstance(Context context) {
        if (NotificationEvents.sInstance == null) {
            NotificationEvents.sInstance = new NotificationEvents(context.getApplicationContext());
        }

        return NotificationEvents.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public NotificationEvents(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        NotificationEvents.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final NotificationEvents me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(NotificationEvents.GENERATOR_IDENTIFIER, NotificationEvents.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, NotificationEvents.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_notification_events_create_history_table));
        }

        if (version != NotificationEvents.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, NotificationEvents.DATABASE_VERSION);
        }

        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(NotificationEvents.ENABLED, NotificationEvents.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        return NotificationEvents.sInstance != null;
    }

    @SuppressWarnings({"unused"})
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_notification_events);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        NotificationEvents me = NotificationEvents.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(NotificationEvents.TABLE_HISTORY, null, null, null, null, null, NotificationEvents.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(NotificationEvents.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(NotificationEvents.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(NotificationEvents.DATA_RETENTION_PERIOD, NotificationEvents.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = ScreenState.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(NotificationEvents.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(NotificationEvents.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public String getIdentifier() {
        return NotificationEvents.GENERATOR_IDENTIFIER;
    }

    public void updateConfig(JSONObject config) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(NotificationEvents.ENABLED, true);
        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, NotificationEvents.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public static class ListenerService extends NotificationListenerService {
        public void onNotificationPosted (StatusBarNotification sbn) {
            if (NotificationEvents.getInstance(this).isEnabled(this)) {
                final NotificationEvents me = NotificationEvents.getInstance(this);

                long now = System.currentTimeMillis();

                ContentValues values = new ContentValues();
                values.put(NotificationEvents.HISTORY_OBSERVED, now);

                Bundle update = new Bundle();
                update.putLong(NotificationEvents.HISTORY_OBSERVED, now);

                String notePackage = sbn.getPackageName();

                values.put(NotificationEvents.HISTORY_PACKAGE, notePackage);
                update.putString(NotificationEvents.HISTORY_PACKAGE, notePackage);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    String channelId = sbn.getNotification().getChannelId();

                    values.put(NotificationEvents.HISTORY_CHANNEL, channelId);
                    update.putString(NotificationEvents.HISTORY_CHANNEL, channelId);
                }

                values.put(NotificationEvents.HISTORY_ACTION, NotificationEvents.HISTORY_ACTION_POSTED);
                update.putString(NotificationEvents.HISTORY_ACTION, NotificationEvents.HISTORY_ACTION_POSTED);

                String notificationString = values.get(NotificationEvents.HISTORY_PACKAGE) + "_" + values.get(NotificationEvents.HISTORY_ACTION) + "_" + values.get(NotificationEvents.HISTORY_REASON);

                if (me.mLastNotificationString.equals(notificationString) == false) {
                    if (me.mDatabase != null) {
                        me.mDatabase.insert(NotificationEvents.TABLE_HISTORY, null, values);
                    }

                    Generators.getInstance(this).notifyGeneratorUpdated(NotificationEvents.GENERATOR_IDENTIFIER, update);

                    me.mLastNotificationString = notificationString;
                }
            }
        }

        public void onNotificationRemoved (StatusBarNotification sbn) {
            if (NotificationEvents.getInstance(this).isEnabled(this)) {
                final NotificationEvents me = NotificationEvents.getInstance(this);

                long now = System.currentTimeMillis();

                ContentValues values = new ContentValues();
                values.put(NotificationEvents.HISTORY_OBSERVED, now);

                Bundle update = new Bundle();
                update.putLong(NotificationEvents.HISTORY_OBSERVED, now);

                String notePackage = sbn.getPackageName();

                values.put(NotificationEvents.HISTORY_PACKAGE, notePackage);
                update.putString(NotificationEvents.HISTORY_PACKAGE, notePackage);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    String channelId = sbn.getNotification().getChannelId();

                    values.put(NotificationEvents.HISTORY_CHANNEL, channelId);
                    update.putString(NotificationEvents.HISTORY_CHANNEL, channelId);
                }

                values.put(NotificationEvents.HISTORY_ACTION, NotificationEvents.HISTORY_ACTION_REMOVED);
                update.putString(NotificationEvents.HISTORY_ACTION, NotificationEvents.HISTORY_ACTION_REMOVED);

                String notificationString = values.get(NotificationEvents.HISTORY_PACKAGE) + "_" + values.get(NotificationEvents.HISTORY_ACTION) + "_" + values.get(NotificationEvents.HISTORY_REASON);

                if (me.mLastNotificationString.equals(notificationString) == false) {
                    if (me.mDatabase != null) {
                        me.mDatabase.insert(NotificationEvents.TABLE_HISTORY, null, values);
                    }

                    Generators.getInstance(this).notifyGeneratorUpdated(NotificationEvents.GENERATOR_IDENTIFIER, update);

                    me.mLastNotificationString = notificationString;
                }
            }
        }

        public void onNotificationRemoved (StatusBarNotification sbn, NotificationListenerService.RankingMap rankingMap, int reason) {
            if (NotificationEvents.getInstance(this).isEnabled(this)) {
                final NotificationEvents me = NotificationEvents.getInstance(this);

                long now = System.currentTimeMillis();

                ContentValues values = new ContentValues();
                values.put(NotificationEvents.HISTORY_OBSERVED, now);

                Bundle update = new Bundle();
                update.putLong(NotificationEvents.HISTORY_OBSERVED, now);

                if (sbn != null) {
                    String notePackage = sbn.getPackageName();

                    values.put(NotificationEvents.HISTORY_PACKAGE, notePackage);
                    update.putString(NotificationEvents.HISTORY_PACKAGE, notePackage);

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        String channelId = sbn.getNotification().getChannelId();

                        values.put(NotificationEvents.HISTORY_CHANNEL, channelId);
                        update.putString(NotificationEvents.HISTORY_CHANNEL, channelId);
                    }
                }

                values.put(NotificationEvents.HISTORY_ACTION, NotificationEvents.HISTORY_ACTION_REMOVED);
                update.putString(NotificationEvents.HISTORY_ACTION, NotificationEvents.HISTORY_ACTION_REMOVED);

                switch (reason) {
                    case NotificationListenerService.REASON_APP_CANCEL:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_APP_CANCEL);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_APP_CANCEL);

                        break;
                    case NotificationListenerService.REASON_APP_CANCEL_ALL:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_APP_CANCEL_ALL);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_APP_CANCEL_ALL);

                        break;
                    case NotificationListenerService.REASON_CANCEL:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_CANCEL);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_CANCEL);

                        break;
                    case NotificationListenerService.REASON_CANCEL_ALL:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_CANCEL_ALL);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_CANCEL_ALL);

                        break;
                    case NotificationListenerService.REASON_CLICK:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_CLICK);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_CLICK);

                        break;
                    case NotificationListenerService.REASON_CHANNEL_BANNED:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_CHANNEL_BANNED);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_CHANNEL_BANNED);

                        break;
                    case NotificationListenerService.REASON_ERROR:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_ERROR);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_ERROR);

                        break;
                    case NotificationListenerService.REASON_GROUP_OPTIMIZATION:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_GROUP_OPTIMIZATION);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_GROUP_OPTIMIZATION);

                        break;
                    case NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_GROUP_SUMMARY_CANCEL);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_GROUP_SUMMARY_CANCEL);

                        break;
                    case NotificationListenerService.REASON_LISTENER_CANCEL:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_LISTENER_CANCEL);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_LISTENER_CANCEL);

                        break;
                    case NotificationListenerService.REASON_LISTENER_CANCEL_ALL:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_LISTENER_CANCEL_ALL);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_LISTENER_CANCEL_ALL);

                        break;
                    case NotificationListenerService.REASON_PACKAGE_BANNED:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PACKAGE_BANNED);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PACKAGE_BANNED);

                        break;
                    case NotificationListenerService.REASON_PACKAGE_CHANGED:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PACKAGE_CHANGED);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PACKAGE_CHANGED);

                        break;
                    case NotificationListenerService.REASON_PACKAGE_SUSPENDED:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PACKAGE_SUSPENDED);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PACKAGE_SUSPENDED);

                        break;
                    case NotificationListenerService.REASON_PROFILE_TURNED_OFF:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PROFILE_OFF);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_PROFILE_OFF);

                        break;
                    case NotificationListenerService.REASON_SNOOZED:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_SNOOZED);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_SNOOZED);

                        break;
                    case NotificationListenerService.REASON_TIMEOUT:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_TIMEOUT);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_TIMEOUT);

                        break;
                    case NotificationListenerService.REASON_USER_STOPPED:
                        values.put(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_STOP);
                        update.putString(NotificationEvents.HISTORY_REASON, NotificationEvents.HISTORY_REASON_USER_STOP);

                        break;
                }

                String notificationString = values.get(NotificationEvents.HISTORY_PACKAGE) + "_" + values.get(NotificationEvents.HISTORY_ACTION) + "_" + values.get(NotificationEvents.HISTORY_REASON);

                if (me.mLastNotificationString.equals(notificationString) == false) {
                    if (me.mDatabase != null) {
                        me.mDatabase.insert(NotificationEvents.TABLE_HISTORY, null, values);
                    }

                    Generators.getInstance(this).notifyGeneratorUpdated(NotificationEvents.GENERATOR_IDENTIFIER, update);

                    me.mLastNotificationString = notificationString;
                }
            }
        }
    }

    public static boolean areNotificationsEnabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        }

        return true;
    }

    public static boolean areNotificationListenersEnabled(Context context) {
        NotificationManager notes = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            boolean isEnabled = notes.isNotificationListenerAccessGranted(new ComponentName(context, NotificationEvents.ListenerService.class));

            return isEnabled;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return notes.areNotificationsEnabled();
        }

        return true;
    }

    public static boolean areNotificationsVisible(Context context) {
        NotificationManager notes = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return notes.isNotificationListenerAccessGranted(new ComponentName(context, NotificationEvents.ListenerService.class));
        }

        return true;
    }

    public static void enableVisibility(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);

            context.startActivity(intent);
        }
    }

    public static void fetchPemissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent intent = new Intent(context, RequestPermissionActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            intent.putExtra(RequestPermissionActivity.PERMISSION, Manifest.permission.POST_NOTIFICATIONS);

            context.startActivity(intent);
        }
    }
}
