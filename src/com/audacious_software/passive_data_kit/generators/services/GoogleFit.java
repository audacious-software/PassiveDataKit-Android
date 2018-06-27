package com.audacious_software.passive_data_kit.generators.services;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptionsExtension;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GoogleFit extends Generator {
    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.GoogleFit.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;
    public static final String GOOGLE_FIT_PERMISSIONS = "com.audacious_software.passive_data_kit.generators.services.GoogleFit.GOOGLE_FIT_PERMISSIONS";
    private static final String APP_PACKAGE_NAME = "com.google.android.apps.fitness";

    private static GoogleFit sInstance = null;

    public static GoogleFit getInstance(Context context) {
        if (GoogleFit.sInstance == null) {
            GoogleFit.sInstance = new GoogleFit(context.getApplicationContext());
        }

        return GoogleFit.sInstance;
    }

    public GoogleFit(Context context) {
        super(context);
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
        GoogleSignInOptionsExtension fitnessOptions =
                FitnessOptions.builder()
                        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                        .build();

        GoogleSignInAccount gsa = GoogleSignIn.getAccountForExtension(this.mContext, fitnessOptions);

        Task<DataReadResponse> response = Fitness.getHistoryClient(this.mContext, gsa)
                .readData(new DataReadRequest.Builder()
                        .read(DataType.TYPE_STEP_COUNT_DELTA)
                        .setTimeRange(start, end, TimeUnit.MILLISECONDS)
                        .build());

        long steps = 0;

        try {
            DataReadResponse readDataResult = Tasks.await(response);

            DataSet dataSet = readDataResult.getDataSet(DataType.TYPE_STEP_COUNT_DELTA);

            for (DataPoint point : dataSet.getDataPoints()) {
                for (Field field : point.getDataType().getFields()) {
                    steps += point.getValue(field).asInt();
                }
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return steps;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(GoogleFit.ENABLED, GoogleFit.ENABLED_DEFAULT);
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        Log.e("PDK", "DIAGS FIT");
        return GoogleFit.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final Handler handler = new Handler(Looper.getMainLooper());
        final GoogleFit me = this;

        try {
            this.mContext.getPackageManager().getPackageInfo(GoogleFit.APP_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);

            if (GoogleFit.isEnabled(this.mContext)) {
                FitnessOptions fitnessOptions = FitnessOptions.builder()
                        .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                        .build();

                if (GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this.mContext), fitnessOptions) == false) {
                    actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_google_fit_permission_title), me.mContext.getString(R.string.diagnostic_missing_google_fit_permission), new Runnable() {
                        @Override
                        public void run() {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    Intent intent = new Intent(me.mContext, RequestPermissionActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                    intent.putExtra(RequestPermissionActivity.PERMISSION, GoogleFit.GOOGLE_FIT_PERMISSIONS);
                                    intent.putExtra(DataType.TYPE_STEP_COUNT_DELTA.getName(), true);

                                    me.mContext.startActivity(intent);
                                }
                            });
                        }
                    }));
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_google_fit_app_title), me.mContext.getString(R.string.diagnostic_missing_google_fit_app), new Runnable() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Intent installIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + GoogleFit.APP_PACKAGE_NAME));
                                installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                me.mContext.startActivity(installIntent);
                            } catch (android.content.ActivityNotFoundException anfe) {
                                Intent installIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + GoogleFit.APP_PACKAGE_NAME));
                                installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                me.mContext.startActivity(installIntent);
                            }
                        }
                    });
                }
            }));
        }

        Log.e("PDK", "5 " + actions.size());

        return actions;
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        GoogleFit.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        Log.e("PDK", "FIT START");
    }

    private void stopGenerator() {
        Log.e("PDK", "FIT STOP");
    }

    @SuppressWarnings("WeakerAccess")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_services_google_fit);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (GoogleFit.sInstance == null) {
            return false;
        }

        return true;
    }

    public List<DataType> allDataTypes() {
        ArrayList<DataType> dataTypes = new ArrayList<>();

        dataTypes.add(DataType.AGGREGATE_ACTIVITY_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_BASAL_METABOLIC_RATE_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_BODY_FAT_PERCENTAGE_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_CALORIES_EXPENDED);
        dataTypes.add(DataType.AGGREGATE_CALORIES_EXPENDED);
        dataTypes.add(DataType.AGGREGATE_DISTANCE_DELTA);
        dataTypes.add(DataType.AGGREGATE_HEART_RATE_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_HEIGHT_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_HYDRATION);
        dataTypes.add(DataType.AGGREGATE_LOCATION_BOUNDING_BOX);
        dataTypes.add(DataType.AGGREGATE_NUTRITION_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_POWER_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_SPEED_SUMMARY);
        dataTypes.add(DataType.AGGREGATE_STEP_COUNT_DELTA);
        dataTypes.add(DataType.AGGREGATE_WEIGHT_SUMMARY);
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
}