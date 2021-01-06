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
import android.view.View;
import android.view.ViewGroup;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.core.content.ContextCompat;

public class GooglePlaces extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-google-places";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.GooglePlaces.ENABLED";
    private static final boolean ENABLED_DEFAULT = false;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.services.GooglePlaces.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String API_KEY = "com.audacious_software.passive_data_kit.generators.services.GooglePlaces.API_KEY";

    private static final long SENSING_INTERVAL = 60 * 1000;

    private static final String DATABASE_PATH = "pdk-google-places.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";

    private static final String HISTORY_CURRENT_PLACE = "current_place";
    private static final String HISTORY_CURRENT_PLACE_UNKNOWN = "unknown";
    private static final String HISTORY_CURRENT_PLACE_ID = "current_place_id";
    private static final String HISTORY_CURRENT_PLACE_ID_UNKNOWN = "unknown";
    private static final String HISTORY_CURRENT_PLACE_LATITUDE = "current_place_latitude";
    private static final String HISTORY_CURRENT_PLACE_LONGITUDE = "current_place_longitude";
    private static final String HISTORY_CURRENT_PLACE_TYPES = "current_place_types";
    private static final String HISTORY_CURRENT_PLACE_CONFIDENCE = "current_place_confidence";

    private static GooglePlaces sInstance = null;

    private Handler mSensingHandler = null;

    private long mRefreshInterval = (60 * 1000);

    private long mLastTimestamp = 0;

    private SQLiteDatabase mDatabase = null;

    private int mPendingRequests = 0;

    private double mPlaceLikelihood = 0.0f;
    private Place mPlace = null;

    @SuppressWarnings("WeakerAccess")
    public static synchronized GooglePlaces getInstance(Context context) {
        if (GooglePlaces.sInstance == null) {
            GooglePlaces.sInstance = new GooglePlaces(context.getApplicationContext());
        }

        return GooglePlaces.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public GooglePlaces(Context context) {
        super(context);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        GooglePlaces.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final GooglePlaces me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(GooglePlaces.GENERATOR_IDENTIFIER, GooglePlaces.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, GooglePlaces.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_places_create_history_table));
        }

        if (version != GooglePlaces.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, GooglePlaces.DATABASE_VERSION);
        }

        this.flushCachedData();

        this.refresh();
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return GooglePlaces.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final GooglePlaces me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (GooglePlaces.isEnabled(this.mContext)) {
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
    public static boolean isRunning(Context context) {
        return (GooglePlaces.sInstance != null);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(GooglePlaces.ENABLED, GooglePlaces.ENABLED_DEFAULT);
    }

    public void refresh() {
        final GooglePlaces me = this;

        Runnable r = new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                if (Places.isInitialized() == false) {
                    SharedPreferences prefs = Generators.getInstance(me.mContext).getSharedPreferences(me.mContext);

                    Places.initialize(me.mContext, prefs.getString(GooglePlaces.API_KEY, null));
                }

                PlacesClient placesClient = Places.createClient(me.mContext);

                List<Place.Field> placeFields = Collections.singletonList(Place.Field.NAME);

                FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);

                if (ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    Task<FindCurrentPlaceResponse> placeResponse = placesClient.findCurrentPlace(request);
                    placeResponse.addOnCompleteListener(task -> {
                        if (task.isSuccessful()){
                            FindCurrentPlaceResponse response = task.getResult();

                            ArrayList<PlaceLikelihood> places = new ArrayList<>(response.getPlaceLikelihoods());

                            if (places != null && places.size() > 0) {
                                Collections.sort(places, new Comparator<PlaceLikelihood>() {
                                    @Override
                                    public int compare(PlaceLikelihood one, PlaceLikelihood two) {
                                        Double oneLikelihood = one.getLikelihood();
                                        Double twoLikelihood = two.getLikelihood();

                                        return twoLikelihood.compareTo(oneLikelihood);
                                    }
                                });

                                PlaceLikelihood mostLikely = places.get(0);

                                me.mPlaceLikelihood = mostLikely.getLikelihood();
                                me.mPlace = mostLikely.getPlace();

                                if (me.mPlace != null) {
                                    me.recordPlaces();
                                }
                            }
                        }
                    });
                }

                if (me.mSensingHandler != null) {
                    me.mSensingHandler.postDelayed(this, GooglePlaces.SENSING_INTERVAL);
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

    private void recordPlaces() {
        if (this.mPlace == null || this.mPlace.getLatLng() == null) {
            return;
        }

        long now = System.currentTimeMillis();

        ContentValues toInsert = new ContentValues();
        Bundle toTransmit = new Bundle();

        toInsert.put(GooglePlaces.HISTORY_OBSERVED, now);
        toTransmit.putLong(GooglePlaces.HISTORY_OBSERVED, now);

        toInsert.put(GooglePlaces.HISTORY_CURRENT_PLACE, this.mPlace.getName());
        toTransmit.putString(GooglePlaces.HISTORY_CURRENT_PLACE, this.mPlace.getName());

        toInsert.put(GooglePlaces.HISTORY_CURRENT_PLACE_ID, this.mPlace.getId());
        toTransmit.putString(GooglePlaces.HISTORY_CURRENT_PLACE_ID, this.mPlace.getId());

        LatLng coords = this.mPlace.getLatLng();

        toInsert.put(GooglePlaces.HISTORY_CURRENT_PLACE_LATITUDE, coords.latitude);
        toTransmit.putDouble(GooglePlaces.HISTORY_CURRENT_PLACE_LATITUDE, coords.latitude);

        toInsert.put(GooglePlaces.HISTORY_CURRENT_PLACE_LONGITUDE, coords.longitude);
        toTransmit.putDouble(GooglePlaces.HISTORY_CURRENT_PLACE_LONGITUDE, coords.longitude);

        List<Place.Type> placeTypes = this.mPlace.getTypes();
        ArrayList<String> bundlePlaceTypes = new ArrayList<>();

        StringBuilder builder = new StringBuilder();

        for (Place.Type type : placeTypes) {
            if (builder.length() > 0) {
                builder.append(";");
            }

            builder.append(type.name());

            bundlePlaceTypes.add(type.name());
        }

        toInsert.put(GooglePlaces.HISTORY_CURRENT_PLACE_TYPES, builder.toString());
        toTransmit.putStringArrayList(GooglePlaces.HISTORY_CURRENT_PLACE_TYPES, bundlePlaceTypes);

        toInsert.put(GooglePlaces.HISTORY_CURRENT_PLACE_CONFIDENCE, this.mPlaceLikelihood);
        toTransmit.putDouble(GooglePlaces.HISTORY_CURRENT_PLACE_CONFIDENCE, this.mPlaceLikelihood);

        this.mDatabase.insert(GooglePlaces.TABLE_HISTORY, null, toInsert);

        Generators.getInstance(this.mContext).notifyGeneratorUpdated(GooglePlaces.GENERATOR_IDENTIFIER, toTransmit);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(GooglePlaces.DATA_RETENTION_PERIOD, GooglePlaces.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = GooglePlaces.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(GooglePlaces.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(GooglePlaces.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public void setAPIKey(String apiKey) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putString(GooglePlaces.API_KEY, apiKey);

        e.apply();
    }

    public void setEnabled(boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(GooglePlaces.ENABLED, enabled);

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

        GooglePlaces generator = GooglePlaces.getInstance(holder.itemView.getContext());

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
        return GooglePlaces.GENERATOR_IDENTIFIER;
    }
}

