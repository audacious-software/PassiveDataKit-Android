package com.audacious_software.passive_data_kit.generators.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.R;
import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.SnapshotClient;
import com.google.android.gms.awareness.fence.TimeFence;
import com.google.android.gms.awareness.snapshot.DetectedActivityResponse;
import com.google.android.gms.awareness.snapshot.HeadphoneStateResponse;
import com.google.android.gms.awareness.snapshot.TimeIntervalsResponse;
import com.google.android.gms.awareness.state.HeadphoneState;
import com.google.android.gms.awareness.state.TimeIntervals;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class GoogleAwareness extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-google-awareness";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String INCLUDE_HEADPHONES = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_HEADPHONES";
    private static final boolean INCLUDE_HEADPHONES_DEFAULT = true;

    private static final String INCLUDE_TIME_OF_DAY = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_TIME_OF_DAY";
    private static final boolean INCLUDE_TIME_OF_DAY_DEFAULT = true;

    private static final String INCLUDE_ACTIVITY = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_ACTIVITY";
    private static final boolean INCLUDE_ACTIVITY_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final long SENSING_INTERVAL = 60 * 1000;

    private static final String DATABASE_PATH = "pdk-google-awareness.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";

    private static final String HISTORY_HEADPHONE_STATE = "headphone_state";
    private static final String HISTORY_HEADPHONE_STATE_PLUGGED_IN = "plugged_in";
    private static final String HISTORY_HEADPHONE_STATE_UNPLUGGED = "unplugged";
    private static final String HISTORY_HEADPHONE_STATE_UNKNOWN = "unknown";

    private static final String HISTORY_WEEKDAY = "weekday";
    private static final String HISTORY_WEEKDAY_WEEKDAY = "weekday";
    private static final String HISTORY_WEEKDAY_WEEKEND = "weekend";
    private static final String HISTORY_WEEKDAY_UNKNOWN = "unknown";

    private static final String HISTORY_HOLIDAY = "holiday";
    private static final String HISTORY_HOLIDAY_HOLIDAY = "holiday";
    private static final String HISTORY_HOLIDAY_NOT_HOLIDAY = "not_holiday";
    private static final String HISTORY_HOLIDAY_UNKNOWN = "unknown";

    private static final String HISTORY_TIME_OF_DAY = "time_of_day";
    private static final String HISTORY_TIME_OF_DAY_MORNING = "morning";
    private static final String HISTORY_TIME_OF_DAY_AFTERNOON = "afternoon";
    private static final String HISTORY_TIME_OF_DAY_EVENING = "evening";
    private static final String HISTORY_TIME_OF_DAY_NIGHT = "night";
    private static final String HISTORY_TIME_OF_DAY_UNKNOWN = "unknown";

    private static final String HISTORY_ACTIVITY = "current_activity";
    private static final String HISTORY_ACTIVITY_IN_VEHICLE = "in_vehicle";
    private static final String HISTORY_ACTIVITY_ON_BICYCLE = "on_bicycle";
    private static final String HISTORY_ACTIVITY_ON_FOOT = "on_foot";
    private static final String HISTORY_ACTIVITY_RUNNING = "running";
    private static final String HISTORY_ACTIVITY_STILL = "still";
    private static final String HISTORY_ACTIVITY_TILTING = "tilting";
    private static final String HISTORY_ACTIVITY_WALKING = "walking";
    private static final String HISTORY_ACTIVITY_UNKNOWN = "unknown";

    private static final String HISTORY_ACTIVITY_CONFIDENCE = "current_activity_confidence";

    private static final int HEADPHONE_STATE_PLUGGED_IN = 1;
    private static final int HEADPHONE_STATE_UNPLUGGED = 0;
    private static final int HEADPHONE_STATE_UNKNOWN = -1;

    private static final int DAY_STATE_WEEKDAY = 1;
    private static final int DAY_STATE_WEEKEND = 0;
    private static final int DAY_STATE_UNKNOWN = -1;

    private static final int HOLIDAY_STATE_HOLIDAY = 1;
    private static final int HOLIDAY_STATE_NOT_HOLIDAY = 0;
    private static final int HOLIDAY_STATE_UNKNOWN = -1;

    public static final int TIME_OF_DAY_MORNING = 0;
    public static final int TIME_OF_DAY_AFTERNOON = 1;
    public static final int TIME_OF_DAY_EVENING = 2;
    public static final int TIME_OF_DAY_NIGHT = 3;
    public static final int TIME_OF_DAY_UNKNOWN = -1;

    private static final int ACTIVITY_STILL = 0;
    private static final int ACTIVITY_ON_BICYCLE = 1;
    private static final int ACTIVITY_ON_FOOT = 2;
    private static final int ACTIVITY_RUNNING = 3;
    private static final int ACTIVITY_IN_VEHICLE = 4;
    private static final int ACTIVITY_TILTING = 5;
    private static final int ACTIVITY_WALKING = 6;
    private static final int ACTIVITY_UNKNOWN = -1;

    private static GoogleAwareness sInstance = null;

    private boolean mIncludeHeadphone;
    private boolean mIncludeTimeOfDay;
    private boolean mIncludeActivity;

    private int mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNKNOWN;

    private int mDayState = GoogleAwareness.DAY_STATE_UNKNOWN;
    private int mHolidayState = GoogleAwareness.HOLIDAY_STATE_UNKNOWN;
    private int mTimeOfDay = GoogleAwareness.TIME_OF_DAY_UNKNOWN;

    private int mActivity = GoogleAwareness.ACTIVITY_UNKNOWN;
    private float mActivityConfidence = -1;

    private long mRefreshInterval = (60 * 1000);

    private SQLiteDatabase mDatabase = null;

    private int mPendingRequests = 0;

    @SuppressWarnings("WeakerAccess")
    public static synchronized GoogleAwareness getInstance(Context context) {
        if (GoogleAwareness.sInstance == null) {
            GoogleAwareness.sInstance = new GoogleAwareness(context.getApplicationContext());
        }

        return GoogleAwareness.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public GoogleAwareness(Context context) {
        super(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        this.mIncludeHeadphone = prefs.getBoolean(GoogleAwareness.INCLUDE_HEADPHONES, GoogleAwareness.INCLUDE_HEADPHONES_DEFAULT);
        this.mIncludeTimeOfDay = prefs.getBoolean(GoogleAwareness.INCLUDE_TIME_OF_DAY, GoogleAwareness.INCLUDE_TIME_OF_DAY_DEFAULT);
        this.mIncludeActivity = prefs.getBoolean(GoogleAwareness.INCLUDE_ACTIVITY, GoogleAwareness.INCLUDE_ACTIVITY_DEFAULT);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        GoogleAwareness.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final GoogleAwareness me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(GoogleAwareness.GENERATOR_IDENTIFIER, GoogleAwareness.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, GoogleAwareness.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_awareness_create_history_table));
        }

        if (version != GoogleAwareness.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, GoogleAwareness.DATABASE_VERSION);
        }

        this.flushCachedData();

        this.refresh();
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return GoogleAwareness.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final GoogleAwareness me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (GoogleAwareness.isEnabled(this.mContext)) {
            if (this.mIncludeTimeOfDay) {
                int permissionCheck = ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_location_permission_title), me.mContext.getString(R.string.diagnostic_missing_location_permission), new Runnable() {
                        @Override
                        public void run() {
                            handler.post(new Runnable() {

                                @Override
                                public void run() {
                                    Intent intent = new Intent(me.mContext, RequestPermissionActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    intent.putExtra(RequestPermissionActivity.PERMISSION, Manifest.permission.ACCESS_FINE_LOCATION);

                                    me.mContext.startActivity(intent);
                                }
                            });
                        }
                    }));
                }
            }

            if (this.mIncludeActivity) {
                int permissionCheck = ContextCompat.checkSelfPermission(this.mContext, "com.google.android.gms.permission.ACTIVITY_RECOGNITION");

                if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                    actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_activity_recognition_permission_title), me.mContext.getString(R.string.diagnostic_missing_activity_recognition_permission), new Runnable() {
                        @Override
                        public void run() {
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
                    }));
                }
            }
        }

        return actions;
    }

    @SuppressWarnings("unused")
    public static boolean isRunning(Context context) {
        return (GoogleAwareness.sInstance != null);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(GoogleAwareness.ENABLED, GoogleAwareness.ENABLED_DEFAULT);
    }

    public void refresh() {
        final GoogleAwareness me = this;

        Runnable r = new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                SnapshotClient client = Awareness.getSnapshotClient(me.mContext);

                me.resetPendingRequests();

                if (me.mIncludeHeadphone) {
                    me.incrementPendingRequests();

                    client.getHeadphoneState().addOnCompleteListener(new OnCompleteListener<HeadphoneStateResponse>() {
                        @Override
                        public void onComplete(@NonNull Task<HeadphoneStateResponse> task) {
                            if (task.isSuccessful()) {
                                HeadphoneState headphone = task.getResult().getHeadphoneState();

                                if (headphone.getState() == HeadphoneState.PLUGGED_IN) {
                                    me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_PLUGGED_IN;
                                } else {
                                    me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNPLUGGED;
                                }
                            } else {
                                me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNKNOWN;
                            }

                            me.decrementPendingRequests();
                        }
                    });
                }

                if (me.mIncludeTimeOfDay) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        me.incrementPendingRequests();

                        client.getTimeIntervals().addOnCompleteListener(new OnCompleteListener<TimeIntervalsResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<TimeIntervalsResponse> task) {
                                if (task.isSuccessful()) {
                                    TimeIntervals intervals = task.getResult().getTimeIntervals();

                                    if (intervals.hasTimeInterval(TimeFence.TIME_INTERVAL_WEEKDAY)) {
                                        me.mDayState = GoogleAwareness.DAY_STATE_WEEKDAY;
                                    } else if (intervals.hasTimeInterval(TimeFence.TIME_INTERVAL_WEEKEND)) {
                                        me.mDayState = GoogleAwareness.DAY_STATE_WEEKEND;
                                    }

                                    if (intervals.hasTimeInterval(TimeFence.TIME_INTERVAL_HOLIDAY)) {
                                        me.mHolidayState = GoogleAwareness.HOLIDAY_STATE_HOLIDAY;
                                    } else {
                                        me.mHolidayState = GoogleAwareness.HOLIDAY_STATE_NOT_HOLIDAY;
                                    }

                                    if (intervals.hasTimeInterval(TimeFence.TIME_INTERVAL_MORNING)) {
                                        me.mTimeOfDay = GoogleAwareness.TIME_OF_DAY_MORNING;
                                    } else if (intervals.hasTimeInterval(TimeFence.TIME_INTERVAL_AFTERNOON)) {
                                        me.mTimeOfDay = GoogleAwareness.TIME_OF_DAY_AFTERNOON;
                                    } else if (intervals.hasTimeInterval(TimeFence.TIME_INTERVAL_EVENING)) {
                                        me.mTimeOfDay = GoogleAwareness.TIME_OF_DAY_EVENING;
                                    } else if (intervals.hasTimeInterval(TimeFence.TIME_INTERVAL_NIGHT)) {
                                        me.mTimeOfDay = GoogleAwareness.TIME_OF_DAY_NIGHT;
                                    } else {
                                        me.mTimeOfDay = GoogleAwareness.TIME_OF_DAY_UNKNOWN;
                                    }
                                } else {
                                    me.mDayState = GoogleAwareness.DAY_STATE_UNKNOWN;
                                    me.mHolidayState = GoogleAwareness.HOLIDAY_STATE_UNKNOWN;
                                    me.mTimeOfDay = GoogleAwareness.TIME_OF_DAY_UNKNOWN;
                                }

                                me.decrementPendingRequests();
                            }
                        });
                    }
                }

                if (me.mIncludeActivity) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, "com.google.android.gms.permission.ACTIVITY_RECOGNITION");

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        me.incrementPendingRequests();

                        client.getDetectedActivity().addOnCompleteListener(new OnCompleteListener<DetectedActivityResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<DetectedActivityResponse> task) {
                                if (task.isSuccessful()) {
                                    DetectedActivity activity = task.getResult().getActivityRecognitionResult().getMostProbableActivity();

                                    switch(activity.getType()) {
                                        case DetectedActivity.IN_VEHICLE:
                                            me.mActivity = GoogleAwareness.ACTIVITY_IN_VEHICLE;
                                            break;
                                        case DetectedActivity.ON_BICYCLE:
                                            me.mActivity = GoogleAwareness.ACTIVITY_ON_BICYCLE;
                                            break;
                                        case DetectedActivity.ON_FOOT:
                                            me.mActivity = GoogleAwareness.ACTIVITY_ON_FOOT;
                                            break;
                                        case DetectedActivity.RUNNING:
                                            me.mActivity = GoogleAwareness.ACTIVITY_RUNNING;
                                            break;
                                        case DetectedActivity.STILL:
                                            me.mActivity = GoogleAwareness.ACTIVITY_STILL;
                                            break;
                                        case DetectedActivity.TILTING:
                                            me.mActivity = GoogleAwareness.ACTIVITY_TILTING;
                                            break;
                                        case DetectedActivity.WALKING:
                                            me.mActivity = GoogleAwareness.ACTIVITY_WALKING;
                                            break;
                                        default:
                                            me.mActivity = GoogleAwareness.ACTIVITY_UNKNOWN;
                                            break;
                                    }

                                    me.mActivityConfidence = activity.getConfidence() / 100.0f;
                                } else {
                                    me.mActivity = GoogleAwareness.ACTIVITY_UNKNOWN;
                                    me.mActivityConfidence = 0.0f;
                                }

                                me.decrementPendingRequests();
                            }
                        });
                    }
                }

                try {
                    Thread.sleep(me.mRefreshInterval);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                me.refresh();
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    private void resetPendingRequests() {
        this.mPendingRequests = 0;
    }

    private void incrementPendingRequests() {
        this.mPendingRequests += 1;
    }

    private void decrementPendingRequests() {
        this.mPendingRequests -= 1;

        if (this.mPendingRequests == 0) {
            this.recordSnapshot();
        }
    }

    private void recordSnapshot() {
        long now = System.currentTimeMillis();

        ContentValues toInsert = new ContentValues();
        Bundle toTransmit = new Bundle();

        toInsert.put(GoogleAwareness.HISTORY_OBSERVED, now);
        toTransmit.putLong(GoogleAwareness.HISTORY_OBSERVED, now);

        if (this.mIncludeHeadphone) {
            switch (this.mHeadphoneState) {
                case GoogleAwareness.HEADPHONE_STATE_PLUGGED_IN:
                    toInsert.put(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_PLUGGED_IN);
                    toTransmit.putString(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_PLUGGED_IN);
                    break;
                case GoogleAwareness.HEADPHONE_STATE_UNPLUGGED:
                    toInsert.put(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNPLUGGED);
                    toTransmit.putString(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNPLUGGED);
                    break;
                case GoogleAwareness.HEADPHONE_STATE_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNKNOWN);
                    break;
            }
        }

        if (this.mIncludeTimeOfDay) {
            switch (this.mDayState) {
                case GoogleAwareness.DAY_STATE_WEEKDAY:
                    toInsert.put(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKDAY);
                    toTransmit.putString(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKDAY);
                    break;
                case GoogleAwareness.DAY_STATE_WEEKEND:
                    toInsert.put(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKEND);
                    toTransmit.putString(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKEND);
                    break;
                case GoogleAwareness.DAY_STATE_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_UNKNOWN);
                    break;
            }

            switch (this.mHolidayState) {
                case GoogleAwareness.HOLIDAY_STATE_HOLIDAY:
                    toInsert.put(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_HOLIDAY);
                    toTransmit.putString(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_HOLIDAY);
                    break;
                case GoogleAwareness.HOLIDAY_STATE_NOT_HOLIDAY:
                    toInsert.put(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_NOT_HOLIDAY);
                    toTransmit.putString(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_NOT_HOLIDAY);
                    break;
                case GoogleAwareness.HOLIDAY_STATE_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_UNKNOWN);
                    break;
            }

            switch (this.mTimeOfDay) {
                case GoogleAwareness.TIME_OF_DAY_MORNING:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_MORNING);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_MORNING);
                    break;
                case GoogleAwareness.TIME_OF_DAY_AFTERNOON:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_AFTERNOON);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_AFTERNOON);
                    break;
                case GoogleAwareness.TIME_OF_DAY_EVENING:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_EVENING);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_EVENING);
                    break;
                case GoogleAwareness.TIME_OF_DAY_NIGHT:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_NIGHT);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_NIGHT);
                    break;
                case GoogleAwareness.TIME_OF_DAY_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_UNKNOWN);
                    break;
            }
        }

        if (this.mIncludeActivity) {
            switch (this.mActivity) {
                case GoogleAwareness.ACTIVITY_IN_VEHICLE:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_IN_VEHICLE);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_IN_VEHICLE);

                    break;
                case GoogleAwareness.ACTIVITY_ON_BICYCLE:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_BICYCLE);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_BICYCLE);

                    break;
                case GoogleAwareness.ACTIVITY_ON_FOOT:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_FOOT);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_FOOT);

                    break;
                case GoogleAwareness.ACTIVITY_RUNNING:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_RUNNING);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_RUNNING);

                    break;
                case GoogleAwareness.ACTIVITY_STILL:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_STILL);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_STILL);

                    break;
                case GoogleAwareness.ACTIVITY_TILTING:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_TILTING);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_TILTING);

                    break;
                case GoogleAwareness.ACTIVITY_WALKING:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_WALKING);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_WALKING);

                    break;
                case GoogleAwareness.ACTIVITY_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_UNKNOWN);

                    break;
            }

            toInsert.put(GoogleAwareness.HISTORY_ACTIVITY_CONFIDENCE, this.mActivityConfidence);
            toTransmit.putDouble(GoogleAwareness.HISTORY_ACTIVITY_CONFIDENCE, this.mActivityConfidence);
        }

        this.mDatabase.insert(GoogleAwareness.TABLE_HISTORY, null, toInsert);

        Generators.getInstance(this.mContext).notifyGeneratorUpdated(GoogleAwareness.GENERATOR_IDENTIFIER, toTransmit);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(GoogleAwareness.DATA_RETENTION_PERIOD, GoogleAwareness.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = GoogleAwareness.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(GoogleAwareness.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(GoogleAwareness.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public int getHeadphoneState() {
        return this.mHeadphoneState;
    }

    public int getDayState() {
        return this.mDayState;
    }

    public int getHolidayState() {
        return this.mHolidayState;
    }

    public int getTimeOfDay() {
        return this.mTimeOfDay;
    }

    public void setIncludeTimeOfDay(boolean include) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(GoogleAwareness.INCLUDE_TIME_OF_DAY, include);
        e.apply();

        this.mIncludeTimeOfDay = include;
    }

    public void setIncludeHeadphones(boolean include) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(GoogleAwareness.INCLUDE_HEADPHONES, include);
        e.apply();

        this.mIncludeHeadphone = include;
    }

    public void setIncludeActivity(boolean include) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(GoogleAwareness.INCLUDE_ACTIVITY, include);
        e.apply();

        this.mIncludeActivity = true;
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_google_awareness, parent, false);
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(DataPointViewHolder holder) {
        long start = System.currentTimeMillis();

        final Context context = holder.itemView.getContext();

        long lastTimestamp = 0;

        GoogleAwareness generator = GoogleAwareness.getInstance(holder.itemView.getContext());

        /*

        Cursor c = generator.mDatabase.query(ForegroundApplication.TABLE_HISTORY, null, null, null, null, null, ForegroundApplication.HISTORY_OBSERVED + " DESC", "1");

        if (c.moveToNext()) {
            if (lastTimestamp == 0) {
                lastTimestamp = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED));
            }
        }

        c.close();

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        long now = System.currentTimeMillis();
        long yesterday = now - (24 * 60 * 60 * 1000);

        HashMap<String,Double> appDurations = new HashMap<>();
        HashMap<String,Long> appWhens = new HashMap<>();

        ArrayList<String> latest = new ArrayList<>();

        String where = ForegroundApplication.HISTORY_OBSERVED + " > ? AND " + ForegroundApplication.HISTORY_SCREEN_ACTIVE + " = ?";
        String[] args = { "" + yesterday, "1" };

        c = generator.mDatabase.query(ForegroundApplication.TABLE_HISTORY, null, where, args, null, null, ForegroundApplication.HISTORY_OBSERVED);

        while (c.moveToNext()) {
            String application = c.getString(c.getColumnIndex(ForegroundApplication.HISTORY_APPLICATION));

            if (application != null) {
                latest.remove(application);

                latest.add(0, application);

                if (appDurations.containsKey(application) == false) {
                    appDurations.put(application, 0.0);
                }

                double appDuration = appDurations.get(application);
                double duration = c.getDouble(c.getColumnIndex(ForegroundApplication.HISTORY_DURATION));

                long lastObserved = c.getLong(c.getColumnIndex(ForegroundApplication.HISTORY_OBSERVED));

                appDuration += duration;

                appDurations.put(application, appDuration);
                appWhens.put(application, lastObserved);
            }
        }

        c.close();

        double largestUsage = 0.0;

        ArrayList<HashMap<String,Double>> largest = new ArrayList<>();

        for (String key : appDurations.keySet()) {
            HashMap<String, Double> app = new HashMap<>();

            double duration = appDurations.get(key);

            app.put(key, duration);

            if (duration > largestUsage) {
                largestUsage = duration;
            }

            largest.add(app);
        }

        Collections.sort(largest, new Comparator<HashMap<String, Double>>() {
            @Override
            public int compare(HashMap<String, Double> mapOne, HashMap<String, Double> mapTwo) {
                String keyOne = mapOne.keySet().iterator().next();
                String keyTwo = mapTwo.keySet().iterator().next();

                Double valueOne = mapOne.get(keyOne);
                Double valueTwo = mapTwo.get(keyTwo);

                return valueTwo.compareTo(valueOne);
            }
        });

        int[] appRowIds = { R.id.application_one, R.id.application_two, R.id.application_three, R.id.application_four };
        int[] whenAppRowIds = { R.id.application_recent_one, R.id.application_recent_two, R.id.application_recent_three, R.id.application_recent_four };

        while (largest.size() > appRowIds.length) {
            largest.remove(largest.size() - 1);
        }

        PackageManager packageManager = context.getPackageManager();

        if (largest.size() > 0) {
            for (int appRowId : appRowIds) {
                View row = cardContent.findViewById(appRowId);

                row.setVisibility(View.GONE);
            }

            for (int i = 0; i < appRowIds.length && i < largest.size(); i++) {
                HashMap<String, Double> appDef = largest.get(i);
                int appRowId = appRowIds[i];

                View row = cardContent.findViewById(appRowId);
                row.setVisibility(View.VISIBLE);

                TextView appName = row.findViewById(R.id.app_name);
                ImageView appIcon = row.findViewById(R.id.application_icon);

                View usedDuration = row.findViewById(R.id.app_used_duration);
                View remainderDuration = row.findViewById(R.id.app_remaining_duration);

                for (String key : appDef.keySet()) {
                    double duration = appDef.get(key);

                    double minutes = duration / (1000 * 60);

                    try {
                        String name = packageManager.getApplicationLabel(packageManager.getApplicationInfo(key, PackageManager.GET_META_DATA)).toString();

                        appName.setText(context.getString(R.string.generator_foreground_application_app_name_duration, name, minutes));
                        Drawable icon = packageManager.getApplicationIcon(key);
                        appIcon.setImageDrawable(icon);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();

                        appName.setText(context.getString(R.string.generator_foreground_application_app_name_duration, key, minutes));
                        appIcon.setImageDrawable(null);
                    }

                    double remainder = largestUsage - duration;

                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) usedDuration.getLayoutParams();
                    params.weight = (float) duration;
                    usedDuration.setLayoutParams(params);

                    params = (LinearLayout.LayoutParams) remainderDuration.getLayoutParams();
                    params.weight = (float) remainder;
                    remainderDuration.setLayoutParams(params);
                }
            }

            for (int i = 0; i < whenAppRowIds.length && i < latest.size(); i++) {
                int appRowId = whenAppRowIds[i];

                String appPackage = latest.get(i);

                while (appPackage == null && i < latest.size() - 1) {
                    i += 1;

                    appPackage = latest.get(i);
                }

                if (appPackage != null) {
                    View row = cardContent.findViewById(appRowId);
                    row.setVisibility(View.VISIBLE);

                    TextView appName = row.findViewById(R.id.app_name);
                    ImageView appIcon = row.findViewById(R.id.application_icon);

                    try {
                        String name = packageManager.getApplicationLabel(packageManager.getApplicationInfo(appPackage, PackageManager.GET_META_DATA)).toString();

                        appName.setText(name);
                        Drawable icon = packageManager.getApplicationIcon(appPackage);
                        appIcon.setImageDrawable(icon);
                    } catch (PackageManager.NameNotFoundException e) {
                        appName.setText(appPackage);
                        appIcon.setImageDrawable(null);
                    }

                    TextView appWhen = row.findViewById(R.id.app_last_used);
                    appWhen.setText(Generator.formatTimestamp(context, appWhens.get(appPackage) / 1000));
                }
            }

            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            dateLabel.setText(Generator.formatTimestamp(context, lastTimestamp / 1000));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        */
    }

    @Override
    public String getIdentifier() {
        return GoogleAwareness.GENERATOR_IDENTIFIER;
    }

    public void updateConfig(JSONObject config) {
        try {
            if (config.has("include-headphones")) {
                this.setIncludeHeadphones(config.getBoolean("include-headphones"));

                config.remove("include-headphones");
            }

            if (config.has("include-time-of-day")) {
                this.setIncludeTimeOfDay(config.getBoolean("include-time-of-day"));

                config.remove("include-time-of-day");
            }

            if (config.has("include-activity")) {
                this.setIncludeActivity(config.getBoolean("include-activity"));

                config.remove("include-activity");
            }

            if (config.has("include-places")) {
                config.remove("include-places");
            }

            if (config.has("include-weather")) {
                config.remove("include-weather");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}

