package com.audacious_software.passive_data_kit.generators.services;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.snapshot.HeadphoneStateResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("SimplifiableIfStatement")
public class GoogleAwareness extends Generator implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    private static final String GENERATOR_IDENTIFIER = "google-awareness";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60 * 24 * 60 * 60 * 1000);

    private static final long SENSING_INTERVAL = 60 * 1000;

    private static GoogleAwareness sInstance = null;
    private GoogleApiClient mGoogleApiClient = null;
    private Handler mSensingHandler = null;

    @SuppressWarnings("WeakerAccess")
    public static GoogleAwareness getInstance(Context context) {
        if (GoogleAwareness.sInstance == null) {
            GoogleAwareness.sInstance = new GoogleAwareness(context.getApplicationContext());
        }

        return GoogleAwareness.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public GoogleAwareness(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        GoogleAwareness.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final GoogleAwareness me = this;

        Runnable r = new Runnable()
        {

            @Override
            public void run() {
                if (me.mGoogleApiClient == null) {
                    GoogleApiClient.Builder builder = new GoogleApiClient.Builder(me.mContext);
                    builder.addConnectionCallbacks(me);
                    builder.addOnConnectionFailedListener(me);
                    builder.addApi(Awareness.API);

                    me.mGoogleApiClient = builder.build();
                    me.mGoogleApiClient.connect();
                }
            }
        };

        Thread t = new Thread(r);
        t.start();

        Generators.getInstance(this.mContext).registerCustomViewClass(GoogleAwareness.GENERATOR_IDENTIFIER, GoogleAwareness.class);

        this.flushCachedData();
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
        return GoogleAwareness.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final GoogleAwareness me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (GoogleAwareness.isEnabled(this.mContext)) {
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

            permissionCheck = ContextCompat.checkSelfPermission(this.mContext, "com.google.android.gms.permission.ACTIVITY_RECOGNITION");

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

        return actions;
    }

    @SuppressWarnings("unused")
    public static boolean isRunning(Context context) {
        if (GoogleAwareness.sInstance == null) {
            return false;
        }

        return (GoogleAwareness.sInstance.mGoogleApiClient != null);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(GoogleAwareness.ENABLED, GoogleAwareness.ENABLED_DEFAULT);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        final GoogleAwareness me = this;

        Runnable r = new Runnable() {

            @Override
            public void run() {
                Awareness.SnapshotApi.getHeadphoneState(mGoogleApiClient).setResultCallback(new ResultCallback<HeadphoneStateResult>() {
                    @Override
                    public void onResult(@NonNull HeadphoneStateResult headphoneStateResult) {
                        if (!headphoneStateResult.getStatus().isSuccess()) {
                            // return;
                        }
                    }
                });

                if (me.mSensingHandler != null) {
                    me.mSensingHandler.postDelayed(this, GoogleAwareness.SENSING_INTERVAL);
                }
            }
        };

        this.mSensingHandler = new Handler();
        this.mSensingHandler.post(r);
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (this.mGoogleApiClient != null && this.mGoogleApiClient.isConnected()) {
            this.mGoogleApiClient.disconnect();
        }

        this.mSensingHandler = null;
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        this.mGoogleApiClient = null;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {
        Log.e("PDK", "TODO: Implement data cache flush in GoogleAwareness!");

//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
//
//        long retentionPeriod = prefs.getLong(GoogleAwareness.DATA_RETENTION_PERIOD, GoogleAwareness.DATA_RETENTION_PERIOD_DEFAULT);
//
//        long start = System.currentTimeMillis() - retentionPeriod;
//
//        String where = GoogleAwareness.HISTORY_OBSERVED + " < ?";
//        String[] args = { "" + start };
//
//        this.mDatabase.delete(GoogleAwareness.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(GoogleAwareness.DATA_RETENTION_PERIOD, period);

        e.apply();
    }
}
