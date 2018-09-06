package com.audacious_software.passive_data_kit.generators.services;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GoogleFit extends Generator {
    public static final String GENERATOR_IDENTIFIER = "pdk-google-fit";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.GoogleFit.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String LATEST_FETCH = "com.audacious_software.passive_data_kit.generators.services.GoogleFit.LATEST_FETCH";

    public static final String GOOGLE_FIT_PERMISSIONS = "com.audacious_software.passive_data_kit.generators.services.GoogleFit.GOOGLE_FIT_PERMISSIONS";
    private static final String APP_PACKAGE_NAME = "com.google.android.apps.fitness";
    private static final long POLL_INTERVAL_DEFAULT = 300000;
    private static final long FETCH_BACK_INTERVAL_DEFAULT = 7 * 24 * 60 * 60 * 1000;

    private static final String HISTORY_OBSERVED = "observed";
    private static final String DATE_START = "date_start";
    private static final String DATE_END = "date_end";

    private static final String STEP_COUNT_TABLE = "step_count_history";
    private static final String STEP_COUNT_STEPS = "steps";

    public static final String READING_TYPE_STEP_DELTA = "com.google.step_count.delta";

    public static final String READING_TYPE = "reading-type";

    private static final String STEP_CADENCE_TABLE = "step_cadence_history";
    private static final String STEP_CADENCE = "steps_per_minute";

    private static final String SPEED_TABLE = "speed_history";
    private static final String SPEED = "speed";

    private static final String CALORIES_EXPENDED_TABLE = "calories_expended_history";
    private static final String CALORIES_EXPENDED = "kcal";

    private static final String DISTANCE_TABLE = "distance_history";
    private static final String DISTANCE = "distance";

    private static GoogleFit sInstance = null;
    private Handler mFetchHandler = null;

    private static final String DATABASE_PATH = "pdk-google-fit.sqlite";
    private static final int DATABASE_VERSION = 1;

    private List<DataType> mHistoryDataTypes = null;
    private long mPollInterval = GoogleFit.POLL_INTERVAL_DEFAULT;
    private long mFetchBackInterval = GoogleFit.FETCH_BACK_INTERVAL_DEFAULT;
    private SQLiteDatabase mDatabase = null;

    private Map<DataType, Long> mLastReadings = new HashMap<>();

    public static GoogleFit getInstance(Context context) {
        if (GoogleFit.sInstance == null) {
            GoogleFit.sInstance = new GoogleFit(context.getApplicationContext());
        }

        return GoogleFit.sInstance;
    }

    private GoogleFit(Context context) {
        super(context);

        this.mHistoryDataTypes = new ArrayList<>();

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, GoogleFit.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_fit_step_count_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_fit_step_cadence_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_fit_speed_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_fit_calories_expended_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_fit_distance_history_table));
        }

        if (version != GoogleFit.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, GoogleFit.DATABASE_VERSION);
        }

        HandlerThread thread = new HandlerThread("fit-fetch-tasks");
        thread.start();

        this.mFetchHandler = new Handler(thread.getLooper());
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return null;
    }

    @Override
    protected void flushCachedData() {

    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {

    }

    public long getStepsForPeriod(long start, long end) {
        long steps = 0;

        if (this.mDatabase != null) {
            synchronized (this) {
                String where = GoogleFit.DATE_START + " <= ? AND " +
                        GoogleFit.DATE_END + " >= ?";

                String[] args = {
                        "" + end,
                        "" + start
                };

                Cursor c = this.mDatabase.query(GoogleFit.STEP_COUNT_TABLE, null, where, args, null, null, null);

                int columnIndex = c.getColumnIndex(GoogleFit.STEP_COUNT_STEPS);

                while (c.moveToNext()) {
                    steps += c.getLong(columnIndex);
                }
            }
        }

        return steps;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(GoogleFit.ENABLED, GoogleFit.ENABLED_DEFAULT);
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return GoogleFit.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final Handler handler = new Handler(Looper.getMainLooper());
        final GoogleFit me = this;

        if (this.isInstalled()) {
            if (GoogleFit.isEnabled(this.mContext)) {
                FitnessOptions.Builder builder = FitnessOptions.builder();

                for (DataType dataType : me.mHistoryDataTypes) {
                    builder.addDataType(dataType, FitnessOptions.ACCESS_READ);
                }

                if (GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this.mContext), builder.build()) == false) {
                    actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_google_fit_permission_title), me.mContext.getString(R.string.diagnostic_missing_google_fit_permission), new Runnable() {
                        @Override
                        public void run() {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    me.authenticate();
                                }
                            });
                        }
                    }));
                }
            }
        } else {
            actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_google_fit_app_title), me.mContext.getString(R.string.diagnostic_missing_google_fit_app), new Runnable() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            me.install();
                        }
                    });
                }
            }));
        }

        return actions;
    }

    public boolean isInstalled() {
        try {
            this.mContext.getPackageManager().getPackageInfo(GoogleFit.APP_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);

            return true;
        } catch (PackageManager.NameNotFoundException e) {

        }

        return false;
    }

    public void install() {
        try {
            Intent installIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GoogleFit.APP_PACKAGE_NAME));
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            this.mContext.startActivity(installIntent);
        } catch (android.content.ActivityNotFoundException anfe) {
            Intent installIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + GoogleFit.APP_PACKAGE_NAME));
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            this.mContext.startActivity(installIntent);
        }
    }

    public boolean isAuthenticated() {
        FitnessOptions.Builder builder = FitnessOptions.builder();

        for (DataType dataType : this.mHistoryDataTypes) {
            builder.addDataType(dataType, FitnessOptions.ACCESS_READ);
        }

        return GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this.mContext), builder.build());
    }

    public void authenticate() {
        Intent intent = new Intent(this.mContext, RequestPermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        intent.putExtra(RequestPermissionActivity.PERMISSION, GoogleFit.GOOGLE_FIT_PERMISSIONS);

        for (DataType dataType : this.mHistoryDataTypes) {
            intent.putExtra(dataType.getName(), true);
        }

        this.mContext.startActivity(intent);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        GoogleFit.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final GoogleFit me = this;

        this.mFetchHandler.post(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                me.mFetchHandler.postDelayed(this, me.mPollInterval);

                long latestTimestamp = me.latestFetch();

                if (latestTimestamp == 0) {
                    latestTimestamp = now - me.mFetchBackInterval;
                }

                FitnessOptions.Builder builder = FitnessOptions.builder();
                DataReadRequest.Builder requestBuilder = new DataReadRequest.Builder();
                requestBuilder.setTimeRange(latestTimestamp, now, TimeUnit.MILLISECONDS);

                for (DataType dataType : me.mHistoryDataTypes) {
                    builder.addDataType(dataType, FitnessOptions.ACCESS_READ);
                    requestBuilder.read(dataType);

                    GoogleSignInAccount gsa = GoogleSignIn.getAccountForExtension(me.mContext, builder.build());

                    Task<DataReadResponse> response = Fitness.getHistoryClient(me.mContext, gsa).readData(requestBuilder.build());

                    try {
                        DataReadResponse readDataResult = Tasks.await(response);

                        DataSet dataSet = readDataResult.getDataSet(dataType);

                        for (DataPoint point : dataSet.getDataPoints()) {
                            DataType pointType = point.getDataType();
                            String pointTypeName = pointType.getName();

//                            for (Field field : point.getDataType().getFields()) {
//                                Log.e("PDK", field.getName() + "[" + pointType.getName() + "]: " + point.getValue(field) + " -- " + (new Date(point.getStartTime(TimeUnit.MILLISECONDS))));
//                            }

                            if (DataType.TYPE_STEP_COUNT_DELTA.getName().equals(pointTypeName)) {
                                ContentValues values = new ContentValues();
                                values.put(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());

                                values.put(GoogleFit.DATE_START, point.getStartTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.DATE_END, point.getEndTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.STEP_COUNT_STEPS, point.getValue(Field.FIELD_STEPS).asInt());

                                String where = GoogleFit.DATE_START + " = ? AND " +
                                        GoogleFit.DATE_END + " = ? AND " +
                                        GoogleFit.STEP_COUNT_STEPS + " = ?";

                                String[] args = {
                                        "" + point.getStartTime(TimeUnit.MILLISECONDS),
                                        "" + point.getEndTime(TimeUnit.MILLISECONDS),
                                        "" + point.getValue(Field.FIELD_STEPS)
                                };

                                Cursor c = me.mDatabase.query(GoogleFit.STEP_COUNT_TABLE, null, where, args, null, null, null);

                                if (c.getCount() == 0) {
                                    me.mDatabase.insert(GoogleFit.STEP_COUNT_TABLE, null, values);

                                    Bundle updated = new Bundle();

                                    updated.putLong(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());
                                    updated.putLong(GoogleFit.DATE_START, point.getStartTime(TimeUnit.MILLISECONDS));
                                    updated.putLong(GoogleFit.DATE_END, point.getEndTime(TimeUnit.MILLISECONDS));
                                    updated.putInt(GoogleFit.STEP_COUNT_STEPS, point.getValue(Field.FIELD_STEPS).asInt());
                                    updated.putString(GoogleFit.READING_TYPE, pointType.getName());

                                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(GoogleFit.GENERATOR_IDENTIFIER, updated);
                                }

                                c.close();
                            } else if (DataType.TYPE_STEP_COUNT_CADENCE.getName().equals(pointTypeName)) {
                                ContentValues values = new ContentValues();
                                values.put(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());

                                values.put(GoogleFit.TIMESTAMP, point.getEndTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.STEP_CADENCE, point.getValue(Field.FIELD_STEPS).asFloat());

                                String where = GoogleFit.TIMESTAMP + " = ? AND " +
                                        GoogleFit.STEP_CADENCE + " = ?";

                                String[] args = {
                                        "" + point.getEndTime(TimeUnit.MILLISECONDS),
                                        "" + point.getValue(Field.FIELD_STEPS)
                                };

                                Cursor c = me.mDatabase.query(GoogleFit.STEP_CADENCE_TABLE, null, where, args, null, null, null);

                                if (c.getCount() == 0) {
                                    me.mDatabase.insert(GoogleFit.STEP_CADENCE_TABLE, null, values);

                                    Bundle updated = new Bundle();

                                    updated.putLong(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());
                                    updated.putLong(GoogleFit.TIMESTAMP, point.getEndTime(TimeUnit.MILLISECONDS));
                                    updated.putFloat(GoogleFit.STEP_CADENCE, point.getValue(Field.FIELD_STEPS).asFloat());
                                    updated.putString(GoogleFit.READING_TYPE, pointType.getName());

                                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(GoogleFit.GENERATOR_IDENTIFIER, updated);
                                }

                                c.close();
                            } else if (DataType.TYPE_SPEED.getName().equals(pointTypeName)) {
                                ContentValues values = new ContentValues();
                                values.put(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());

                                values.put(GoogleFit.TIMESTAMP, point.getEndTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.SPEED, point.getValue(Field.FIELD_SPEED).asFloat());

                                String where = GoogleFit.TIMESTAMP + " = ? AND " +
                                        GoogleFit.SPEED + " = ?";

                                String[] args = {
                                        "" + point.getEndTime(TimeUnit.MILLISECONDS),
                                        "" + point.getValue(Field.FIELD_SPEED)
                                };

                                Cursor c = me.mDatabase.query(GoogleFit.SPEED_TABLE, null, where, args, null, null, null);

                                if (c.getCount() == 0) {
                                    me.mDatabase.insert(GoogleFit.SPEED_TABLE, null, values);

                                    Bundle updated = new Bundle();

                                    updated.putLong(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());
                                    updated.putLong(GoogleFit.TIMESTAMP, point.getEndTime(TimeUnit.MILLISECONDS));
                                    updated.putFloat(GoogleFit.SPEED, point.getValue(Field.FIELD_SPEED).asFloat());
                                    updated.putString(GoogleFit.READING_TYPE, pointType.getName());

                                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(GoogleFit.GENERATOR_IDENTIFIER, updated);
                                }

                                c.close();
                            } else if (DataType.TYPE_CALORIES_EXPENDED.getName().equals(pointTypeName)) {
                                ContentValues values = new ContentValues();
                                values.put(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());

                                values.put(GoogleFit.DATE_START, point.getStartTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.DATE_END, point.getEndTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.CALORIES_EXPENDED, point.getValue(Field.FIELD_CALORIES).asFloat());

                                String where = GoogleFit.DATE_START + " = ? AND " +
                                        GoogleFit.DATE_END + " = ? AND " +
                                        GoogleFit.CALORIES_EXPENDED + " = ?";

                                String[] args = {
                                        "" + point.getStartTime(TimeUnit.MILLISECONDS),
                                        "" + point.getEndTime(TimeUnit.MILLISECONDS),
                                        "" + point.getValue(Field.FIELD_CALORIES)
                                };

                                Cursor c = me.mDatabase.query(GoogleFit.CALORIES_EXPENDED_TABLE, null, where, args, null, null, null);

                                if (c.getCount() == 0) {
                                    me.mDatabase.insert(GoogleFit.CALORIES_EXPENDED_TABLE, null, values);

                                    Bundle updated = new Bundle();

                                    updated.putLong(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());
                                    updated.putLong(GoogleFit.DATE_START, point.getStartTime(TimeUnit.MILLISECONDS));
                                    updated.putLong(GoogleFit.DATE_END, point.getEndTime(TimeUnit.MILLISECONDS));
                                    updated.putFloat(GoogleFit.CALORIES_EXPENDED, point.getValue(Field.FIELD_CALORIES).asFloat());
                                    updated.putString(GoogleFit.READING_TYPE, pointType.getName());

                                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(GoogleFit.GENERATOR_IDENTIFIER, updated);
                                }

                                c.close();
                            } else if (DataType.TYPE_DISTANCE_DELTA.getName().equals(pointTypeName)) {
                                ContentValues values = new ContentValues();
                                values.put(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());

                                values.put(GoogleFit.DATE_START, point.getStartTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.DATE_END, point.getEndTime(TimeUnit.MILLISECONDS));
                                values.put(GoogleFit.DISTANCE, point.getValue(Field.FIELD_DISTANCE).asFloat());

                                String where = GoogleFit.DATE_START + " = ? AND " +
                                        GoogleFit.DATE_END + " = ? AND " +
                                        GoogleFit.DISTANCE + " = ?";

                                String[] args = {
                                        "" + point.getStartTime(TimeUnit.MILLISECONDS),
                                        "" + point.getEndTime(TimeUnit.MILLISECONDS),
                                        "" + point.getValue(Field.FIELD_DISTANCE)
                                };

                                Cursor c = me.mDatabase.query(GoogleFit.DISTANCE_TABLE, null, where, args, null, null, null);

                                if (c.getCount() == 0) {
                                    me.mDatabase.insert(GoogleFit.DISTANCE_TABLE, null, values);

                                    Bundle updated = new Bundle();

                                    updated.putLong(GoogleFit.HISTORY_OBSERVED, System.currentTimeMillis());
                                    updated.putLong(GoogleFit.DATE_START, point.getStartTime(TimeUnit.MILLISECONDS));
                                    updated.putLong(GoogleFit.DATE_END, point.getEndTime(TimeUnit.MILLISECONDS));
                                    updated.putFloat(GoogleFit.DISTANCE, point.getValue(Field.FIELD_DISTANCE).asFloat());
                                    updated.putString(GoogleFit.READING_TYPE, pointType.getName());

                                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(GoogleFit.GENERATOR_IDENTIFIER, updated);
                                }

                                c.close();
                            } else {
                                Log.e("PDK", "TODO: Serialize Google Fit data type: " + pointType.getName());
                            }

                            if (me.mLastReadings.get(pointType) == null || me.mLastReadings.get(pointType) < point.getEndTime(TimeUnit.MILLISECONDS)) {
                                me.mLastReadings.put(pointType, point.getEndTime(TimeUnit.MILLISECONDS));
                            }
                        }

                        me.setLatestFetch(now);
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void setLatestFetch(long timestamp) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(GoogleFit.LATEST_FETCH, timestamp);

        e.apply();
    }

    private long latestFetch() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        return prefs.getLong(GoogleFit.LATEST_FETCH, 0);
    }

    private void stopGenerator() {
        this.mFetchHandler.removeCallbacksAndMessages(null);
    }

    @SuppressWarnings("WeakerAccess")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_services_google_fit);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (GoogleFit.sInstance == null || GoogleFit.sInstance.mFetchHandler == null) {
            return false;
        }

        return GoogleFit.sInstance.mFetchHandler.hasMessages(0);
    }

    public List<DataType> allDataTypes() {
        ArrayList<DataType> dataTypes = new ArrayList<>();

        dataTypes.add(DataType.TYPE_ACTIVITY_SAMPLES);
        dataTypes.add(DataType.TYPE_ACTIVITY_SEGMENT);
        dataTypes.add(DataType.TYPE_BASAL_METABOLIC_RATE);
        dataTypes.add(DataType.TYPE_BODY_FAT_PERCENTAGE);
        dataTypes.add(DataType.TYPE_CALORIES_EXPENDED);
        dataTypes.add(DataType.TYPE_CYCLING_PEDALING_CADENCE);
        dataTypes.add(DataType.TYPE_CYCLING_PEDALING_CUMULATIVE);
        dataTypes.add(DataType.TYPE_CYCLING_WHEEL_REVOLUTION);
        dataTypes.add(DataType.TYPE_CYCLING_WHEEL_RPM);
        dataTypes.add(DataType.TYPE_DISTANCE_DELTA);
        dataTypes.add(DataType.TYPE_HEART_RATE_BPM);
        dataTypes.add(DataType.TYPE_HEIGHT);
        dataTypes.add(DataType.TYPE_HYDRATION);
        dataTypes.add(DataType.TYPE_LOCATION_SAMPLE);
        dataTypes.add(DataType.TYPE_LOCATION_TRACK);
        dataTypes.add(DataType.TYPE_NUTRITION);
        dataTypes.add(DataType.TYPE_POWER_SAMPLE);
        dataTypes.add(DataType.TYPE_SPEED);
        dataTypes.add(DataType.TYPE_STEP_COUNT_CADENCE);
        dataTypes.add(DataType.TYPE_STEP_COUNT_DELTA);
        dataTypes.add(DataType.TYPE_WEIGHT);
        dataTypes.add(DataType.TYPE_WORKOUT_EXERCISE);

        return dataTypes;
    }

    public void monitorHistory(DataType fitDataType) {
        this.mHistoryDataTypes.add(fitDataType);
    }

    public void setPollInterval(long pollInterval) {
        this.mPollInterval = pollInterval;
    }

    public void setInitialFetchLimit(long fetchBackInterval) {
        this. mFetchBackInterval = fetchBackInterval;
    }

}