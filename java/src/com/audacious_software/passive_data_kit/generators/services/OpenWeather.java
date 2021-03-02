package com.audacious_software.passive_data_kit.generators.services;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import androidx.core.content.ContextCompat;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.device.Location;
import com.audacious_software.pdk.passivedatakit.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.tls.HandshakeCertificates;

public class OpenWeather extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-open-weather";
    private static final String DATABASE_PATH = "pdk-open-weather.sql";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.OpenWeather.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.services.OpenWeather.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private SQLiteDatabase mDatabase = null;
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_HISTORY = "open_weather_history";
    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_LATITUDE = "latitude";
    public static final String HISTORY_LONGITUDE = "longitude";
    public static final String HISTORY_TIMEZONE = "timezone";
    private static final String HISTORY_TEMPERATURE = "temperature";
    private static final String HISTORY_APPARENT_TEMPERATURE = "apparent_temperature";
    private static final String HISTORY_HUMIDITY = "humidity";
    private static final String HISTORY_DEW_POINT = "dew_point";
    private static final String HISTORY_WIND_SPEED = "wind_speed";
    private static final String HISTORY_WIND_GUST_SPEED = "wind_gust_speed";
    private static final String HISTORY_WIND_BEARING = "wind_bearing";
    private static final String HISTORY_CLOUD_COVER = "cloud_cover";
    private static final String HISTORY_UV_INDEX = "uv_index";
    private static final String HISTORY_AIR_PRESSURE = "air_pressure";
    private static final String HISTORY_VISIBILITY = "visibility";
    private static final String HISTORY_FULL_READING = "full_reading";

    private long mLatestTimestamp = -1;

    private long mLastWeatherFetch = 0;

    private static OpenWeather sInstance = null;

    private long mFetchInterval = 15 * 60 * 1000;

    private Handler mHandler = null;

    @Override
    public String getIdentifier() {
        return OpenWeather.GENERATOR_IDENTIFIER;
    }

    public static synchronized OpenWeather getInstance(Context context) {
        if (OpenWeather.sInstance == null) {
            OpenWeather.sInstance = new OpenWeather(context.getApplicationContext());
        }

        return OpenWeather.sInstance;
    }

    public OpenWeather(Context context) {
        super(context);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, OpenWeather.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_open_weather_create_history_table));
        }

        if (version != OpenWeather.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, OpenWeather.DATABASE_VERSION);
        }

        Generators.getInstance(this.mContext).registerCustomViewClass(OpenWeather.GENERATOR_IDENTIFIER, OpenWeather.class);
    }

    public static void start(final Context context) {
        OpenWeather.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final OpenWeather me = this;

        if (this.mHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                this.mHandler.getLooper().quitSafely();
            } else {
                this.mHandler.getLooper().quit();
            }

            this.mHandler = null;
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                me.mHandler = new Handler();

                Looper.loop();
            }
        };

        Thread t = new Thread(r);
        t.start();

        this.fetchLatestWeather(false);
    }

    private void stopGenerator() {
        if (this.mHandler != null) {
            this.mHandler.removeCallbacksAndMessages(null);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                this.mHandler.getLooper().quitSafely();
            } else {
                this.mHandler.getLooper().quit();
            }

            this.mHandler = null;
        }
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(OpenWeather.ENABLED, OpenWeather.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static boolean isRunning(Context context) {
        if (OpenWeather.sInstance == null) {
            return false;
        }

        return true;
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return OpenWeather.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final OpenWeather me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (OpenWeather.isEnabled(this.mContext)) {
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
        OpenWeather me = OpenWeather.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(OpenWeather.TABLE_HISTORY, null, null, null, null, null, OpenWeather.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(OpenWeather.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return null;
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(OpenWeather.DATA_RETENTION_PERIOD, OpenWeather.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = OpenWeather.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(OpenWeather.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(OpenWeather.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, OpenWeather.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public void fetchLatestWeather(boolean force) {
        final OpenWeather me = this;

        long now = System.currentTimeMillis();

        if (force || (now - this.mLastWeatherFetch > this.mFetchInterval)) {
            android.location.Location lastPlace = Location.getInstance(this.mContext).getLastKnownLocation();

            if (lastPlace != null) {
                this.mLastWeatherFetch = now;

                String key = this.mContext.getString(R.string.open_weather_api_key);
                String fetchUrl = this.mContext.getString(R.string.generator_open_weather_url, lastPlace.getLatitude(), lastPlace.getLongitude(), key);

                HandshakeCertificates certificates = PassiveDataKit.getInstance(this.mContext).fetchTrustedCertificates();

                OkHttpClient client = new OkHttpClient.Builder()
                        .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager())
                        .build();

                Request request = new Request.Builder()
                        .url(fetchUrl)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            if (me.mHandler != null) {
                                me.mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        me.fetchLatestWeather(true);
                                    }
                                }, 300 * 1000);
                            }
                        } else {
                            String body = response.body().string();

                            try {
                                JSONObject weather = new JSONObject(body);

                                if (weather.has("current")) {
                                    JSONObject current = weather.getJSONObject("current");

                                    ContentValues values = new ContentValues();
                                    values.put(OpenWeather.HISTORY_OBSERVED, now);

                                    Bundle updated = new Bundle();
                                    updated.putLong(OpenWeather.HISTORY_OBSERVED, now);

                                    Bundle metadata = new Bundle();
                                    updated.putBundle(Generator.PDK_METADATA, metadata);

                                    if (weather.has("timezone")) {
                                        values.put(OpenWeather.HISTORY_TIMEZONE, weather.getString("timezone"));
                                        updated.putString(OpenWeather.HISTORY_TIMEZONE, weather.getString("timezone"));
                                    }

                                    if (weather.has("lat")) {
                                        values.put(OpenWeather.HISTORY_LATITUDE, weather.getDouble("lat"));
                                        updated.putDouble(OpenWeather.HISTORY_LATITUDE, weather.getDouble("lat"));
                                        metadata.putDouble(Generator.LATITUDE, weather.getDouble("lat"));
                                    }

                                    if (weather.has("lon")) {
                                        values.put(OpenWeather.HISTORY_LONGITUDE, weather.getDouble("lon"));
                                        updated.putDouble(OpenWeather.HISTORY_LONGITUDE, weather.getDouble("lon"));
                                        metadata.putDouble(Generator.LONGITUDE, weather.getDouble("lon"));
                                    }

                                    if (current.has("temp")) {
                                        values.put(OpenWeather.HISTORY_TEMPERATURE, current.getDouble("temp"));
                                        updated.putDouble(OpenWeather.HISTORY_TEMPERATURE, current.getDouble("temp"));
                                    }

                                    if (current.has("feels_like")) {
                                        values.put(OpenWeather.HISTORY_APPARENT_TEMPERATURE, current.getDouble("feels_like"));
                                        updated.putDouble(OpenWeather.HISTORY_APPARENT_TEMPERATURE, current.getDouble("feels_like"));
                                    }

                                    if (current.has("humidity")) {
                                        values.put(OpenWeather.HISTORY_HUMIDITY, current.getDouble("humidity"));
                                        updated.putDouble(OpenWeather.HISTORY_HUMIDITY, current.getDouble("humidity"));
                                    }

                                    if (current.has("dew_point")) {
                                        values.put(OpenWeather.HISTORY_DEW_POINT, current.getDouble("dew_point"));
                                        updated.putDouble(OpenWeather.HISTORY_DEW_POINT, current.getDouble("dew_point"));
                                    }

                                    if (current.has("wind_speed")) {
                                        values.put(OpenWeather.HISTORY_WIND_SPEED, current.getDouble("wind_speed"));
                                        updated.putDouble(OpenWeather.HISTORY_WIND_SPEED, current.getDouble("wind_speed"));
                                    }

                                    if (current.has("wind_gust")) {
                                        values.put(OpenWeather.HISTORY_WIND_GUST_SPEED, current.getDouble("wind_gust"));
                                        updated.putDouble(OpenWeather.HISTORY_WIND_GUST_SPEED, current.getDouble("wind_gust"));
                                    }

                                    if (current.has("wind_deg")) {
                                        values.put(OpenWeather.HISTORY_WIND_BEARING, current.getDouble("wind_deg"));
                                        updated.putDouble(OpenWeather.HISTORY_WIND_BEARING, current.getDouble("wind_deg"));
                                    }

                                    if (current.has("clouds")) {
                                        values.put(OpenWeather.HISTORY_CLOUD_COVER, current.getDouble("clouds"));
                                        updated.putDouble(OpenWeather.HISTORY_CLOUD_COVER, current.getDouble("clouds"));
                                    }

                                    if (current.has("uvi")) {
                                        values.put(OpenWeather.HISTORY_UV_INDEX, current.getDouble("uvi"));
                                        updated.putDouble(OpenWeather.HISTORY_UV_INDEX, current.getDouble("uvi"));
                                    }

                                    if (current.has("pressure")) {
                                        values.put(OpenWeather.HISTORY_AIR_PRESSURE, current.getDouble("pressure"));
                                        updated.putDouble(OpenWeather.HISTORY_AIR_PRESSURE, current.getDouble("pressure"));
                                    }

                                    if (current.has("visibility")) {
                                        values.put(OpenWeather.HISTORY_VISIBILITY, current.getDouble("visibility"));
                                        updated.putDouble(OpenWeather.HISTORY_VISIBILITY, current.getDouble("visibility"));
                                    }

                                    values.put(OpenWeather.HISTORY_FULL_READING, weather.toString());
                                    updated.putString(OpenWeather.HISTORY_FULL_READING, weather.toString());

                                    me.mDatabase.insert(OpenWeather.TABLE_HISTORY, null, values);

                                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(OpenWeather.GENERATOR_IDENTIFIER, updated);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        }

        if (force == false && me.mHandler != null) {
            me.mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    me.fetchLatestWeather(false);
                }
            }, this.mFetchInterval);
        }
    }
}
