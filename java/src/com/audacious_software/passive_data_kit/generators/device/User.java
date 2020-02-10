package com.audacious_software.passive_data_kit.generators.device;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class User extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-user";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.User.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.device.User.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String TABLE_HISTORY = "history";

    public static final String HISTORY_OBSERVED = "observed";

    private static final String HISTORY_MODE = "mode";
    private static final String HISTORY_MODE_FOREGROUND = "foreground";
    private static final String HISTORY_MODE_BACKGROUND = "background";
    private static final String HISTORY_MODE_UNKNOWN = "unknown";
    private static final String HISTORY_IDENTIFIER = "identifier";

    private static final String DATABASE_PATH = "pdk-user.sqlite";
    private static final int DATABASE_VERSION = 2;

    private static User sInstance = null;
    private BroadcastReceiver mReceiver = null;
    private SQLiteDatabase mDatabase = null;

    @SuppressWarnings("WeakerAccess")
    public static synchronized User getInstance(Context context) {
        if (User.sInstance == null) {
            User.sInstance = new User(context.getApplicationContext());
        }

        return User.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public User(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        User.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final User me = this;

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, User.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_users_create_history_table));
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_users_history_table_add_identifier));
        }

        if (version != User.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, User.DATABASE_VERSION);
        }

        this.flushCachedData();

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                long now = System.currentTimeMillis();

                ContentValues values = new ContentValues();
                values.put(ScreenState.HISTORY_OBSERVED, now);

                Bundle update = new Bundle();
                update.putLong(ScreenState.HISTORY_OBSERVED, now);

                if (Intent.ACTION_USER_BACKGROUND.equals(intent.getAction())) {
                    update.putString(User.HISTORY_MODE, User.HISTORY_MODE_BACKGROUND);
                    values.put(User.HISTORY_MODE, User.HISTORY_MODE_BACKGROUND);
                } else if (Intent.ACTION_USER_FOREGROUND.equals(intent.getAction())) {
                    update.putString(User.HISTORY_MODE, User.HISTORY_MODE_FOREGROUND);
                    values.put(User.HISTORY_MODE, User.HISTORY_MODE_FOREGROUND);
                } else {
                    update.putString(User.HISTORY_MODE, User.HISTORY_MODE_UNKNOWN);
                    values.put(User.HISTORY_MODE, User.HISTORY_MODE_UNKNOWN);
                }

                if (intent.hasExtra("android.intent.extra.user_handle")) {
                    update.putInt(User.HISTORY_IDENTIFIER, intent.getIntExtra("android.intent.extra.user_handle", -1));
                    values.put(User.HISTORY_IDENTIFIER, intent.getIntExtra("android.intent.extra.user_handle", -1));
                }

                me.mDatabase.insert(User.TABLE_HISTORY, null, values);

                Generators.getInstance(context).notifyGeneratorUpdated(User.GENERATOR_IDENTIFIER, update);
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_USER_FOREGROUND);

        this.mContext.registerReceiver(this.mReceiver, filter);

        this.mReceiver.onReceive(this.mContext, new Intent(Intent.ACTION_USER_FOREGROUND));

        Generators.getInstance(this.mContext).registerCustomViewClass(User.GENERATOR_IDENTIFIER, User.class);
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(User.ENABLED, User.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (User.sInstance == null) {
            return false;
        }

        return User.sInstance.mReceiver != null;
    }


    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(User.DATA_RETENTION_PERIOD, User.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = User.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(User.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(User.DATA_RETENTION_PERIOD, period);

        e.apply();

    }

    @Override
    public String getIdentifier() {
        return User.IDENTIFIER;
    }

    @SuppressWarnings({"unused"})
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }
}
