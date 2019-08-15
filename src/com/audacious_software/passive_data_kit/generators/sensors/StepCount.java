package com.audacious_software.passive_data_kit.generators.sensors;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import humanize.Humanize;

public class StepCount extends SensorGenerator implements SensorEventListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-sensor-step-count";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.StepCount.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.sensors.StepCount.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String DATABASE_PATH = "pdk-sensor-step-count.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";

    private static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_STEP_COUNT = "step_count";
    private static final String HISTORY_ELAPSED_RUNTIME = "elapsed_runtime";
    private static final long REFRESH_INTERVAL = 60000;

    private static StepCount sInstance = null;

    private SQLiteDatabase mDatabase = null;

    private long mLastCleanup = 0;
    private long mCleanupInterval = 15 * 60 * 1000;

    private Thread mLooperThread = null;
    private Handler mHandler = null;

    private long mLatestTimestamp = 0;
    private float mLastStepCount = -1;

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return StepCount.GENERATOR_IDENTIFIER;
    }

    @SuppressWarnings("WeakerAccess")
    public static synchronized StepCount getInstance(Context context) {
        if (StepCount.sInstance == null) {
            StepCount.sInstance = new StepCount(context.getApplicationContext());
        }

        return StepCount.sInstance;
    }

    private StepCount(Context context) {
        super(context);

        final StepCount me = this;

        this.mLooperThread = new Thread() {
            public void run() {
                Looper.prepare();

                me.mHandler = new Handler(Looper.myLooper()) {
                    public void handleMessage(Message message) {
                        Log.e("PDK", "[STEP-COUNTER] HANDLE MESSAGE: " + message);
                    }
                };

                Looper.loop();
            }
        };

        this.mLooperThread.start();
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        StepCount.getInstance(context).startGenerator();
    }

    public static boolean isAvailable(Context context) {
        PackageManager manager = context.getPackageManager();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return manager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER);
        }

        return false;
    }

    private void startGenerator() {
        final StepCount me = this;

        StepCount.isAvailable(me.mContext);

        Runnable r = new Runnable() {
            @Override
            public void run() {
                synchronized(me) {
//                    Generators.getInstance(me.mContext).registerCustomViewClass(StepCount.GENERATOR_IDENTIFIER, StepCount.class);

                    File path = PassiveDataKit.getGeneratorsStorage(me.mContext);

                    path = new File(path, StepCount.DATABASE_PATH);

                    if (me.mDatabase == null) {
                        me.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

                        int version = me.getDatabaseVersion(me.mDatabase);

                        switch (version) {
                            case 0:
                                me.mDatabase.execSQL(me.mContext.getString(R.string.pdk_generator_step_count_create_history_table));
                        }

                        if (version != StepCount.DATABASE_VERSION) {
                            me.setDatabaseVersion(me.mDatabase, StepCount.DATABASE_VERSION);
                        }
                    }

                    if (StepCount.isEnabled(me.mContext)) {
                        if (me.mHandler == null) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        };

                        me.mHandler.post(new Runnable() {
                                             @Override
                                             public void run() {
                                                me.refreshSteps();
                                             }
                                         });
                    } else {
                        me.stopGenerator();
                    }

                    me.flushCachedData();
                }
            }
        };

        Thread t = new Thread(r, "step-counter-start");
        t.start();
    }

    private void refreshSteps() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            final SensorManager sensors = (SensorManager) this.mContext.getSystemService(Context.SENSOR_SERVICE);

            Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void stopGenerator() {
        this.mHandler.removeCallbacksAndMessages(null);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(StepCount.ENABLED, StepCount.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (StepCount.sInstance == null) {
            return false;
        }

        return StepCount.isEnabled(context);
    }

    @SuppressWarnings("unused")
    @SuppressLint("InlinedApi")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        return actions;
    }

    @SuppressWarnings("WeakerAccess")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_sensors_step_count);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("WeakerAccess")
    public static long latestPointGenerated(Context context) {
        StepCount me = StepCount.getInstance(context);

        if (me.mLatestTimestamp == 0) {
            Cursor c = me.mDatabase.query(StepCount.TABLE_HISTORY, null, null, null, null, null, StepCount.HISTORY_OBSERVED + " DESC", "1");

            if (c.moveToNext()) {
                me.mLatestTimestamp = c.getLong(c.getColumnIndex(StepCount.HISTORY_OBSERVED));
            }

            c.close();
        }

        return me.mLatestTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(StepCount.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long now = System.currentTimeMillis();

        if (sensorEvent == null || sensorEvent.values == null) {
            return;
        } else {
            long elapsed = SystemClock.elapsedRealtime();

            final SensorManager sensors = (SensorManager) this.mContext.getSystemService(Context.SENSOR_SERVICE);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

                sensors.unregisterListener(this, sensor);

                float stepCount = sensorEvent.values[0];

                if (Math.abs(stepCount - this.mLastStepCount) > 0.5) {
                    ContentValues values = new ContentValues();

                    values.put(StepCount.HISTORY_STEP_COUNT, stepCount);
                    values.put(StepCount.HISTORY_OBSERVED, now);
                    values.put(StepCount.HISTORY_ELAPSED_RUNTIME, elapsed);

                    this.mDatabase.insert(StepCount.TABLE_HISTORY, null, values);

                    Bundle update = new Bundle();
                    update.putLong(StepCount.HISTORY_OBSERVED, now);
                    update.putLong(StepCount.HISTORY_ELAPSED_RUNTIME, elapsed);
                    update.putFloat(StepCount.HISTORY_STEP_COUNT, stepCount);

                    Generators.getInstance(this.mContext).notifyGeneratorUpdated(StepCount.GENERATOR_IDENTIFIER, update);

                    this.mLastStepCount = stepCount;
                }
            }
        }

        final StepCount me = this;

        this.mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                me.refreshSteps();
            }
        }, StepCount.REFRESH_INTERVAL);

        if (now - me.mLastCleanup > me.mCleanupInterval) {
            me.mLastCleanup = now;

            me.flushCachedData();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int newAccuracy) {
        // Do nothing...
    }

    @Override
    protected void flushCachedData() {
        if (this.mHandler == null) {
            return;
        }

        final StepCount me = this;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(StepCount.DATA_RETENTION_PERIOD, StepCount.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        final String where = StepCount.HISTORY_OBSERVED + " < ?";
        final String[] args = { "" + start };

        Runnable r = new Runnable() {
            @Override
            public void run() {
                synchronized(me) {
                    try {
                        me.mDatabase.delete(StepCount.TABLE_HISTORY, where, args);
                    } catch (SQLiteDatabaseLockedException e) {
                        Log.e("PDK", "Step counter database is locked. Will try again later...");
                    }
                }
            }
        };

        this.mHandler.post(r);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(StepCount.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public String getIdentifier() {
        return StepCount.GENERATOR_IDENTIFIER;
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, StepCount.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public boolean hasPermissions() {
        int permissionCheck = ContextCompat.checkSelfPermission(this.mContext, "android.permission.ACTIVITY_RECOGNITION");

        return (permissionCheck == PackageManager.PERMISSION_GRANTED);
    }

    public void requestPermissions() {
        final StepCount me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(me.mContext, RequestPermissionActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(RequestPermissionActivity.PERMISSION, "com.google.android.gms.permission.ACTIVITY_RECOGNITION");

                me.mContext.startActivity(intent);
            }
        });
    }

    public long getStepsForPeriod(long start, long end) {
        float steps = 0;

        if (this.mDatabase != null) {
            synchronized (this) {
                String where = StepCount.HISTORY_OBSERVED + " >= ? AND " + StepCount.HISTORY_OBSERVED + " < ?";

                String[] args = {
                        "" + start,
                        "" + end
                };

                Cursor c = this.mDatabase.query(StepCount.TABLE_HISTORY, null, where, args, null, null, StepCount.HISTORY_OBSERVED);

                int columnIndex = c.getColumnIndex(StepCount.HISTORY_STEP_COUNT);

                float offset = 0;

                while (c.moveToNext()) {
                    float count = c.getFloat(columnIndex);

                    if (offset == 0 && count > 0) {
                        offset = count;
                    }

                    if (count < offset) {
                        offset = 0;
                    }

                    steps += (count - offset);

                    offset = count;
                }

                c.close();
            }
        }

        return (long) steps;
    }
}
