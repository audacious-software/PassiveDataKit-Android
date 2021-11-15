package com.audacious_software.passive_data_kit.generators.environment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;

import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.environment.services.GeofencesService;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.tls.HandshakeCertificates;

public class Geofences extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-geofences";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.environment.Geofences.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.environment.Geofences.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String LAST_FENCES_URL = "com.audacious_software.passive_data_kit.generators.environment.Geofences.LAST_FENCES_URL";
    private static final String CACHED_GEOFENCES = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CACHED_GEOFENCES";
    private static final int LOITERING_DELAY_DEFAULT = 300000;

    public static final String GEOFENCE_TRANSITION_UNKNOWN = "unknown";
    public static final String GEOFENCE_TRANSITION_DWELL = "dwell";
    public static final String GEOFENCE_TRANSITION_ENTER = "enter";
    public static final String GEOFENCE_TRANSITION_EXIT = "exit";

    private static final String INITIAL_TRIGGERS = "com.audacious_software.passive_data_kit.generators.environment.Geofences.INITIAL_TRIGGERS";
    private static final int INITIAL_TRIGGERS_DEFAULT = GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_EXIT;

    private static final String CUSTOM_FENCE_NAME = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_NAME";
    private static final String CUSTOM_FENCE_NAME_DEFAULT = "???";
    private static final String CUSTOM_FENCE_IDENTIFIER = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_IDENTIFIER";
    private static final int CUSTOM_FENCE_IDENTIFIER_DEFAULT = 0;
    private static final String CUSTOM_FENCE_ADDRESS = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_ADDRESS";
    private static final String CUSTOM_FENCE_RADIUS = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_RADIUS";
    private static final float CUSTOM_FENCE_RADIUS_DEFAULT = 200;
    private static final String CUSTOM_FENCE_DWELL_MINUTES = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_DWELL_MINUTES";
    private static final float CUSTOM_FENCE_DWELL_MINUTES_DEFAULT = 30;
    private static final String CUSTOM_FENCE_WINDOW_MINUTES = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_WINDOW_MINUTES";
    private static final float CUSTOM_FENCE_WINDOW_MINUTES_DEFAULT = 45;
    private static final String CUSTOM_FENCE_LATITUDE = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_LATITUDE";
    private static final float CUSTOM_FENCE_LATITUDE_DEFAULT = 0;
    private static final String CUSTOM_FENCE_LONGITUDE = "com.audacious_software.passive_data_kit.generators.environment.Geofences.CUSTOM_FENCE_LONGITUDE";
    private static final float CUSTOM_FENCE_LONGITUDE_DEFAULT = 0;

    private static Geofences sInstance = null;

    private static int MAX_ACTIVE_FENCES = 95;

    private GeofencingClient mGeofencingClient;
    private PendingIntent mGeofencePendingIntent;

    private FusedLocationProviderClient mFusedLocationClient;

    private Handler mHandler = null;

    private SQLiteDatabase mDatabase = null;
    private static final String DATABASE_PATH = "pdk-geofences.sqlite";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_HISTORY = "history";
    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_OBSERVED = "observed";
    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_TRANSITION = "transition";
    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_METADATA_JSON = "metadata_json";
    public static final String HISTORY_IDENTIFIER = "identifier";
    private static final String HISTORY_FENCE_DETAILS = "fence_details";

    private long mLatestTimestamp = -1;

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return Geofences.GENERATOR_IDENTIFIER;
    }

    public static synchronized Geofences getInstance(Context context) {
        context = context.getApplicationContext();

        if (Geofences.sInstance == null) {
            Geofences.sInstance = new Geofences(context);

            Intent intent = new Intent(context, GeofencesService.class);

            Geofences.sInstance.mGeofencePendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        }

        return Geofences.sInstance;
    }

    private Geofences(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        Geofences.getInstance(context).startGenerator();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void startGenerator() {
        final Geofences me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (me.mGeofencingClient == null) {
                    me.mGeofencingClient = LocationServices.getGeofencingClient(me.mContext);
                }

                if (me.mFusedLocationClient == null) {
                    me.mFusedLocationClient = LocationServices.getFusedLocationProviderClient(me.mContext);
                }
            }
        };

        Thread t = new Thread(r);
        t.start();

        Generators.getInstance(this.mContext).registerCustomViewClass(Geofences.GENERATOR_IDENTIFIER, Geofences.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Geofences.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_geofences_create_history_table));
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_geofences_history_table_add_identifier));
        }

        if (version != Geofences.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, Geofences.DATABASE_VERSION);
        }

        if (this.mHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                this.mHandler.getLooper().quitSafely();
            } else {
                this.mHandler.getLooper().quit();
            }

            this.mHandler = null;
        }

        this.initializeHandler();

        this.mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                me.fetchGeofences(null);
            }
        }, 500);
    }

    private void initializeHandler() {
        HandlerThread handlerThread = new HandlerThread("pdk-geofences-worker");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        this.mHandler = new Handler(looper);
    }

    public void setFencesURL(String url) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(Geofences.LAST_FENCES_URL, url);

        e.apply();
    }

    public void fetchGeofences(final Runnable next) {
        final Geofences me = this;

        OnSuccessListener<Location> successListener = new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location == null) {
                    location = new Location("passive-data-kit");
                    location.setLatitude(41.8781);
                    location.setLongitude(-87.6298);
                }

                final Location sortLocation = location;

                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

                final String fencesUrl = prefs.getString(Geofences.LAST_FENCES_URL, null);

                if (fencesUrl != null) {
                    HandshakeCertificates certificates = PassiveDataKit.getInstance(me.mContext).fetchTrustedCertificates();

                    OkHttpClient client = new OkHttpClient.Builder()
                            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager())
                            .build();

                    final Request request = new Request.Builder()
                            .url(fencesUrl)
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            e.printStackTrace();
                        }

                        @SuppressLint("MissingPermission")
                        @Override
                        public void onResponse(Call call, final Response response) throws IOException {
                            JSONObject fences = new JSONObject();

                            try {
                                fences = new JSONObject(prefs.getString(Geofences.CACHED_GEOFENCES, "{}"));
                            } catch (JSONException e) {
                                Logger.getInstance(me.mContext).logThrowable(e);
                            }

                            if (!response.isSuccessful()) {
                                Logger.getInstance(me.mContext).logThrowable(new IOException("Unexpected code " + response));
                            } else {
                                try {
                                    fences = new JSONObject(response.body().string());

                                    if (fences.has("features")) {
                                        SharedPreferences.Editor e = prefs.edit();
                                        e.putString(Geofences.CACHED_GEOFENCES, fences.toString(2));
                                        e.apply();
                                    }
                                } catch (JSONException e) {
                                    Logger.getInstance(me.mContext).logThrowable(e);
                                }
                            }

                            if (fences.has("features")) {
                                try {
                                    JSONArray features = fences.getJSONArray("features");

                                    ArrayList<JSONObject> fenceList = new ArrayList<>();

                                    JSONObject customFence = me.getCustomGeofence();

                                    if (customFence != null) {
                                        fenceList.add(customFence);
                                    }

                                    for (int i = 0; i < features.length(); i++) {
                                        fenceList.add(features.getJSONObject(i));
                                    }

                                    Collections.sort(fenceList, new Comparator<JSONObject>() {
                                        @Override
                                        public int compare(JSONObject one, JSONObject two) {
                                            try {
                                                JSONArray oneCoordinates = one.getJSONObject("geometry").getJSONArray("coordinates");

                                                Location oneLocation = new Location("passive-data-kit");
                                                oneLocation.setLongitude(oneCoordinates.getDouble(0));
                                                oneLocation.setLatitude(oneCoordinates.getDouble(1));

                                                JSONArray twoCoordinates = two.getJSONObject("geometry").getJSONArray("coordinates");

                                                Location twoLocation = new Location("passive-data-kit");
                                                twoLocation.setLongitude(twoCoordinates.getDouble(0));
                                                twoLocation.setLatitude(twoCoordinates.getDouble(1));

                                                Float oneDistance = sortLocation.distanceTo(oneLocation);

                                                Float twoDistance = sortLocation.distanceTo(twoLocation);

                                                return oneDistance.compareTo(twoDistance);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }

                                            return 0;
                                        }
                                    });

                                    ArrayList<Geofence> activeFences = new ArrayList<>();

                                    GeofencingRequest.Builder builder = new GeofencingRequest.Builder();

                                    builder.setInitialTrigger(me.getInitialTriggers());

                                    for (JSONObject fenceDef : fenceList) {
                                        if (activeFences.size() < Geofences.MAX_ACTIVE_FENCES) {
                                            activeFences.add(me.buildFence(fenceDef));
                                        }
                                    }

                                    builder.addGeofences(activeFences);

                                    final GeofencingRequest request = builder.build();

                                    me.mGeofencingClient.removeGeofences(me.mGeofencePendingIntent);

                                    if (ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        me.mGeofencingClient.addGeofences(request, me.mGeofencePendingIntent)
                                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                    @Override
                                                    public void onSuccess(Void aVoid) {
                                                        if (next != null) {
                                                            next.run();
                                                        }
                                                    }
                                                })
                                                .addOnFailureListener(new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e) {
                                                        Log.e("PDK", "GEOFENCE FENCES REGISTER FAILED: " + e);
                                                    }
                                                });
                                    }
                                } catch (JSONException e) {
                                    Logger.getInstance(me.mContext).logThrowable(e);
                                }
                            }
                        }
                    });
                }
            }
        };

        if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            this.mFusedLocationClient.getLastLocation().addOnSuccessListener(successListener);
        }
    }

    private int getInitialTriggers() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        return prefs.getInt(Geofences.INITIAL_TRIGGERS, Geofences.INITIAL_TRIGGERS_DEFAULT);
    }

    public void setInitialTriggers(int triggerFlags) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putInt(Geofences.INITIAL_TRIGGERS, triggerFlags);
        e.apply();
    }

    private Geofence buildFence(JSONObject feature) throws JSONException {
        JSONObject metadata = feature.getJSONObject("properties");

        String identifier = "fence-" + metadata.getInt("identifier");

        JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");

        double longitude = coordinates.getDouble(0);
        double latitude = coordinates.getDouble(1);
        double radius = metadata.getDouble("radius");

        int dwellDelay = Geofences.LOITERING_DELAY_DEFAULT;

        if (metadata.has("dwell_minutes")) {
            dwellDelay = metadata.getInt("dwell_minutes") * (60 * 1000);
        }

        Geofence.Builder builder = new Geofence.Builder();
        builder.setRequestId(identifier);
        builder.setCircularRegion(latitude, longitude, (float) radius);
        builder.setExpirationDuration(Geofence.NEVER_EXPIRE);
        builder.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT | Geofence.GEOFENCE_TRANSITION_DWELL);
        builder.setLoiteringDelay(dwellDelay);

        return builder.build();
    }

    private void stopGenerator() {
        if (this.mGeofencingClient != null && this.mGeofencePendingIntent != null) {
            this.mGeofencingClient.removeGeofences(this.mGeofencePendingIntent)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            // Geofences removed
                            // ...
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            // Failed to remove geofences
                            // ...
                        }
                    });
        }

        this.mDatabase.close();
        this.mDatabase = null;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Geofences.ENABLED, Geofences.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static boolean isRunning(Context context) {
        if (Geofences.sInstance == null) {
            return false;
        }

        return (Geofences.sInstance.mGeofencingClient != null);
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return Geofences.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final Geofences me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (Geofences.isEnabled(this.mContext)) {
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

        return actions;
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        Geofences me = Geofences.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(Geofences.TABLE_HISTORY, null, null, null, null, null, Geofences.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(Geofences.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("WeakerAccess")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_environment_geofences);
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(Geofences.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    protected void flushCachedData() {
        if (this.mDatabase == null) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(Geofences.DATA_RETENTION_PERIOD, Geofences.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = Geofences.HISTORY_OBSERVED + " < ?";
        String[] args = {"" + start};

        this.mDatabase.delete(Geofences.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(Geofences.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public List<JSONObject> getFences() {
        ArrayList<JSONObject> fences = new ArrayList<>();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        try {
            JSONObject fencesJson = new JSONObject(prefs.getString(Geofences.CACHED_GEOFENCES, "{}"));

            if (fencesJson.has("features")) {
                JSONArray features = fencesJson.getJSONArray("features");

                JSONObject customFence = this.getCustomGeofence();

                if (customFence != null) {
                    features.put(customFence);
                }

                for (int i = 0; i < features.length(); i++) {
                    fences.add(features.getJSONObject(i));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return fences;
    }

    public void recordTransition(String requestId, String transition, long when) {
        final Geofences me = this;

        boolean found = false;

        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

            JSONObject fencesJson = new JSONObject(prefs.getString(Geofences.CACHED_GEOFENCES, "{}"));

            if (fencesJson.has("features")) {
                JSONArray features = fencesJson.getJSONArray("features");

                JSONObject customFence = this.getCustomGeofence();

                if (customFence != null) {
                    features.put(customFence);
                }

                for (int i = 0; i < features.length() && found == false; i++) {
                    JSONObject fence = features.getJSONObject(i);
                    JSONObject metadata = fence.getJSONObject("properties");

                    String identifier = "fence-" + metadata.getInt("identifier");

                    if (identifier.equals(requestId)) {
                        found = true;

                        final ContentValues values = new ContentValues();
                        values.put(Geofences.HISTORY_OBSERVED, when);
                        values.put(Geofences.HISTORY_IDENTIFIER, metadata.getInt("identifier"));
                        values.put(Geofences.HISTORY_TRANSITION, transition);
                        values.put(Geofences.HISTORY_METADATA_JSON, metadata.toString());

                        final Bundle updated = new Bundle();
                        updated.putLong(Geofences.HISTORY_OBSERVED, when);
                        updated.putString(Geofences.HISTORY_TRANSITION, transition);
                        updated.putString(Geofences.HISTORY_IDENTIFIER, "" + metadata.getInt("identifier"));

                        Bundle fenceDetails = new Bundle();

                        if (metadata.has("identifier")) {
                            fenceDetails.putInt("identifier", metadata.getInt("identifier"));
                        }

                        if (metadata.has("name")) {
                            fenceDetails.putString("name", metadata.getString("name"));
                        }

                        if (metadata.has("address")) {
                            fenceDetails.putString("address", metadata.getString("address"));
                        }

                        if (metadata.has("dwell_minutes")) {
                            fenceDetails.putInt("dwell_minutes", metadata.getInt("dwell_minutes"));
                        }

                        if (metadata.has("center_latitude")) {
                            fenceDetails.putDouble("center_latitude", metadata.getDouble("center_latitude"));
                        }

                        if (metadata.has("center_longitude")) {
                            fenceDetails.putDouble("center_longitude", metadata.getDouble("center_longitude"));
                        }

                        if (metadata.has("radius")) {
                            fenceDetails.putDouble("radius", metadata.getDouble("radius"));
                        }

                        updated.putBundle(Geofences.HISTORY_FENCE_DETAILS, fenceDetails);

                        if (this.mHandler == null) {
                            this.initializeHandler();
                        }

                        this.mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (me.mDatabase != null) {
                                    me.mDatabase.insert(Geofences.TABLE_HISTORY, null, values);
                                }

                                Generators.getInstance(me.mContext).notifyGeneratorUpdated(Geofences.GENERATOR_IDENTIFIER, updated);
                            }
                        });

                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                me.flushCachedData();
                            }
                        };

                        Thread t = new Thread(r);
                        t.start();
                    }
                }
            }
        } catch (JSONException e) {
            Logger.getInstance(this.mContext).logThrowable(e);
        }
    }

    @Override
    public String getIdentifier() {
        return Geofences.GENERATOR_IDENTIFIER;
    }

    private JSONObject getCustomGeofence() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

            if (prefs.contains(Geofences.CUSTOM_FENCE_NAME)) {
                JSONObject feature = new JSONObject();
                feature.put("type", "Feature");

                JSONObject properties = new JSONObject();

                properties.put("name", prefs.getString(Geofences.CUSTOM_FENCE_NAME, Geofences.CUSTOM_FENCE_NAME_DEFAULT));
                properties.put("identifier", prefs.getInt(Geofences.CUSTOM_FENCE_IDENTIFIER, Geofences.CUSTOM_FENCE_IDENTIFIER_DEFAULT));
                properties.put("address", prefs.getString(Geofences.CUSTOM_FENCE_ADDRESS, null));
                properties.put("radius", prefs.getFloat(Geofences.CUSTOM_FENCE_RADIUS, Geofences.CUSTOM_FENCE_RADIUS_DEFAULT));
                properties.put("dwell_minutes", prefs.getFloat(Geofences.CUSTOM_FENCE_DWELL_MINUTES, Geofences.CUSTOM_FENCE_DWELL_MINUTES_DEFAULT));
                properties.put("window_minutes", prefs.getFloat(Geofences.CUSTOM_FENCE_WINDOW_MINUTES, Geofences.CUSTOM_FENCE_WINDOW_MINUTES_DEFAULT));
                properties.put("center_latitude", prefs.getFloat(Geofences.CUSTOM_FENCE_LATITUDE, Geofences.CUSTOM_FENCE_LATITUDE_DEFAULT));
                properties.put("center_longitude", prefs.getFloat(Geofences.CUSTOM_FENCE_LONGITUDE, Geofences.CUSTOM_FENCE_LONGITUDE_DEFAULT));

                feature.put("properties", properties);

                JSONObject geometry = new JSONObject();
                geometry.put("type", "Point");

                JSONArray coordinates = new JSONArray();
                coordinates.put(prefs.getFloat(Geofences.CUSTOM_FENCE_LONGITUDE, Geofences.CUSTOM_FENCE_LONGITUDE_DEFAULT));
                coordinates.put(prefs.getFloat(Geofences.CUSTOM_FENCE_LATITUDE, Geofences.CUSTOM_FENCE_LATITUDE_DEFAULT));

                geometry.put("coordinates", coordinates);

                feature.put("geometry", geometry);

                return feature;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void setCustomGeofence(Place place, int identifier) {
        this.clearCustomGeofence();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putString(Geofences.CUSTOM_FENCE_NAME, place.getName().toString());
        e.putInt(Geofences.CUSTOM_FENCE_IDENTIFIER, identifier);

        LatLng location = place.getLatLng();

        e.putFloat(Geofences.CUSTOM_FENCE_LATITUDE, (float) location.latitude);
        e.putFloat(Geofences.CUSTOM_FENCE_LONGITUDE, (float) location.longitude);

        e.apply();

        this.fetchGeofences(null);
    }

    public void clearCustomGeofence() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.remove(Geofences.CUSTOM_FENCE_NAME);
        e.remove(Geofences.CUSTOM_FENCE_IDENTIFIER);
        e.remove(Geofences.CUSTOM_FENCE_ADDRESS);
        e.remove(Geofences.CUSTOM_FENCE_RADIUS);
        e.remove(Geofences.CUSTOM_FENCE_DWELL_MINUTES);
        e.remove(Geofences.CUSTOM_FENCE_WINDOW_MINUTES);
        e.remove(Geofences.CUSTOM_FENCE_LATITUDE);
        e.remove(Geofences.CUSTOM_FENCE_LATITUDE);
        e.remove(Geofences.CUSTOM_FENCE_LONGITUDE);

        e.apply();

        this.recordTransition("0", "exit", System.currentTimeMillis());
    }
}
