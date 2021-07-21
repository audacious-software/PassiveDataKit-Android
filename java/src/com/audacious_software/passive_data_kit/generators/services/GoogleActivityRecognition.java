package com.audacious_software.passive_data_kit.generators.services;

import android.Manifest;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.SleepClassifyEvent;
import com.google.android.gms.location.SleepSegmentEvent;
import com.google.android.gms.location.SleepSegmentRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import com.google.android.gms.location.ActivityRecognition;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GoogleActivityRecognition extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-google-activity-recognition";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.GoogleActivityRecognition.ENABLED";
    private static final boolean ENABLED_DEFAULT = false;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.services.GoogleActivityRecognition.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String DATABASE_PATH = "pdk-google-activity-recognition.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    private static final String TABLE_SLEEP_HISTORY = "sleep_history";
    private static final String HISTORY_OBSERVED = "observed";

    private static GoogleActivityRecognition sInstance = null;

    private Handler mRefreshHandler = null;

    private SQLiteDatabase mDatabase = null;

    private ActivityRecognitionClient mClient = null;
    private PendingIntent mPendingIntent = null;

    @SuppressWarnings("WeakerAccess")
    public static synchronized GoogleActivityRecognition getInstance(Context context) {
        if (GoogleActivityRecognition.sInstance == null) {
            GoogleActivityRecognition.sInstance = new GoogleActivityRecognition(context.getApplicationContext());
        }

        return GoogleActivityRecognition.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public GoogleActivityRecognition(Context context) {
        super(context);

        Log.e("PDK", "GAR INIT");
        (new Throwable()).printStackTrace();
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        GoogleActivityRecognition.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        Generators.getInstance(this.mContext).registerCustomViewClass(GoogleActivityRecognition.GENERATOR_IDENTIFIER, GoogleActivityRecognition.class);

        /*

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, GoogleActivityRecognition.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_activity_recognition_create_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_activity_recognition_create_sleep_table));
        }

        if (version != GoogleActivityRecognition.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, GoogleActivityRecognition.DATABASE_VERSION);
        }

        this.flushCachedData();
*/
        if (GoogleActivityRecognition.isEnabled(this.mContext)) {
            int permissionCheck = ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACTIVITY_RECOGNITION);

            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {

                this.mPendingIntent = PendingIntent.getService(this.mContext, 0, new Intent(this.mContext, GoogleActivityRecognition.Service.class), 0);

                this.mClient = ActivityRecognition.getClient(this.mContext);

                List transitions = new ArrayList<>();

                int[] activities = {
                        DetectedActivity.IN_VEHICLE,
                        DetectedActivity.ON_BICYCLE,
                        DetectedActivity.ON_FOOT,
                        DetectedActivity.RUNNING,
                        DetectedActivity.STILL,
                        // DetectedActivity.TILTING,
                        DetectedActivity.WALKING
                        // DetectedActivity.UNKNOWN
                };

                for (int activity : activities) {
                    transitions.add(new ActivityTransition.Builder()
                            .setActivityType(activity)
                            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                            .build());
                }

                ActivityTransitionRequest activityRequest = new ActivityTransitionRequest(transitions);

                this.mClient = ActivityRecognition.getClient(this.mContext);

                Task activityTask = this.mClient.requestActivityTransitionUpdates(activityRequest, this.mPendingIntent);

                activityTask.addOnSuccessListener(
                        new OnSuccessListener() {
                            @Override
                            public void onSuccess(Object o) {

                            }
                        });

                activityTask.addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                Log.e("PDK", "Unable to connect to Google Activity Recognition service.");
                                e.printStackTrace();
                            }
                        });

                Task sleepTask = this.mClient.requestSleepSegmentUpdates(this.mPendingIntent, SleepSegmentRequest.getDefaultSleepSegmentRequest());

                sleepTask.addOnSuccessListener(
                        new OnSuccessListener() {
                            @Override
                            public void onSuccess(Object o) {

                            }
                        });

                sleepTask.addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                Log.e("PDK", "Unable to connect to Google Activity Recognition sleep service.");
                                e.printStackTrace();
                            }
                        });
            }
        }
    }

    public static void stop(Context context) {
        GoogleActivityRecognition.getInstance(context).stopGenerator();
    }

    private void stopGenerator() {
        if (this.mClient != null) {
            this.mClient.removeActivityTransitionUpdates(this.mPendingIntent);
            this.mClient.removeSleepSegmentUpdates(this.mPendingIntent);

            this.mClient = null;
        }
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return GoogleActivityRecognition.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final GoogleActivityRecognition me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (GoogleActivityRecognition.isEnabled(this.mContext)) {
            int permissionCheck = ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACTIVITY_RECOGNITION);

            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_activity_recognition_permission_title), me.mContext.getString(R.string.diagnostic_missing_activity_recognition_permission), new Runnable() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(me.mContext, RequestPermissionActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra(RequestPermissionActivity.PERMISSION, Manifest.permission.ACTIVITY_RECOGNITION);

                                me.mContext.startActivity(intent);
                            }
                        });
                    }
                }));
            }
        }

        return actions;
    }

    @SuppressWarnings("unused")
    public static boolean isRunning(Context context) {
        return (GoogleActivityRecognition.sInstance != null);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(GoogleActivityRecognition.ENABLED, GoogleActivityRecognition.ENABLED_DEFAULT);
    }

    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(GoogleActivityRecognition.ENABLED, enabled);
        e.apply();

        if (enabled) {
            GoogleActivityRecognition.start(context);
        }
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {
        /*
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(GoogleActivityRecognition.DATA_RETENTION_PERIOD, GoogleActivityRecognition.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = GoogleActivityRecognition.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(GoogleActivityRecognition.TABLE_HISTORY, where, args);
        this.mDatabase.delete(GoogleActivityRecognition.TABLE_SLEEP_HISTORY, where, args);
         */
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(GoogleActivityRecognition.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public void setEnabled(boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(GoogleActivityRecognition.ENABLED, enabled);

        e.apply();
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return null; // LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_google_places, parent, false);
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(DataPointViewHolder holder) {
        long start = System.currentTimeMillis();

        final Context context = holder.itemView.getContext();

        long lastTimestamp = 0;

        GoogleActivityRecognition generator = GoogleActivityRecognition.getInstance(holder.itemView.getContext());
    }

    @Override
    public String getIdentifier() {
        return GoogleActivityRecognition.GENERATOR_IDENTIFIER;
    }

    public static class Service extends IntentService {
        public Service(String name) {
            super(name);
        }

        public Service() {
            super("PDK Google Activity Recognition");
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            if (GoogleActivityRecognition.isEnabled(this)) {
                if (SleepSegmentEvent.hasEvents(intent)) {
                    List<SleepSegmentEvent> events = SleepSegmentEvent.extractEvents(intent);

                    for (SleepSegmentEvent event : events) {
                        Log.e("PDK", "SLEEP SEGMENT: " + event);
                    }
                } else {
                    Log.e("PDK", "NO SLEEP SEGMENT.");
                }

                if (SleepClassifyEvent.hasEvents(intent)) {
                    List<SleepClassifyEvent> events = SleepClassifyEvent.extractEvents(intent);

                    for (SleepClassifyEvent event : events) {
                        Log.e("PDK", "SLEEP CLASS: " + event);
                    }
                } else {
                    Log.e("PDK", "NO SLEEP CLASSIFICATION.");
                }

                if (ActivityRecognitionResult.hasResult(intent)) {
                    ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

                    Log.e("PDK", "ACTIVITY RESULT: " + result);
                } else {
                    Log.e("PDK", "NO ACTIVITY RESULT.");
                }
            }
        }
    }
}

