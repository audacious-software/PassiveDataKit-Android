package com.audacious_software.passive_data_kit.generators.services;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

public class DarkSkyWeather extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-dark-sky-weather";
    private static final String DATABASE_PATH = "pdk-dark-sky-weather.sql";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.environment.TimeOfDay.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.environment.TimeOfDay.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private SQLiteDatabase mDatabase = null;
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_HISTORY = "dark_sky_weather_history";
    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_LATITUDE = "latitude";
    public static final String HISTORY_LONGITUDE = "longitude";
    public static final String HISTORY_TIMEZONE = "timezone";
    private static final String HISTORY_SUMMARY = "summary";
    private static final String HISTORY_TEMPERATURE = "temperature";
    private static final String HISTORY_OZONE = "ozone";
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
    private static final String HISTORY_PRECIPITATION_INTENSITY = "precipitation_intensity";
    private static final String HISTORY_PRECIPITATION_PROBABILITY = "precipitation_probability";
    private static final String HISTORY_FULL_READING = "full_reading";

    private long mLatestTimestamp = -1;

    private long mLastWeatherFetch = 0;

    private static DarkSkyWeather sInstance = null;

    private long mFetchInterval = 60 * 1000;

    @Override
    public String getIdentifier() {
        return DarkSkyWeather.GENERATOR_IDENTIFIER;
    }

    public static synchronized DarkSkyWeather getInstance(Context context) {
        if (DarkSkyWeather.sInstance == null) {
            DarkSkyWeather.sInstance = new DarkSkyWeather(context.getApplicationContext());
        }

        return DarkSkyWeather.sInstance;
    }

    public DarkSkyWeather(Context context) {
        super(context);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, DarkSkyWeather.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_dark_sky_weather_create_history_table));
        }

        if (version != DarkSkyWeather.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, DarkSkyWeather.DATABASE_VERSION);
        }

        Generators.getInstance(this.mContext).registerCustomViewClass(DarkSkyWeather.GENERATOR_IDENTIFIER, DarkSkyWeather.class);
    }

    public static void start(final Context context) {
        DarkSkyWeather.getInstance(context).startGenerator();
    }

    private void startGenerator() {
    }

    private void stopGenerator() {

    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(DarkSkyWeather.ENABLED, DarkSkyWeather.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static boolean isRunning(Context context) {
        if (DarkSkyWeather.sInstance == null) {
            return false;
        }

        return true;
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return DarkSkyWeather.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final DarkSkyWeather me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (DarkSkyWeather.isEnabled(this.mContext)) {
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
        DarkSkyWeather me = DarkSkyWeather.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(DarkSkyWeather.TABLE_HISTORY, null, null, null, null, null, DarkSkyWeather.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(DarkSkyWeather.HISTORY_OBSERVED));
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

        long retentionPeriod = prefs.getLong(DarkSkyWeather.DATA_RETENTION_PERIOD, DarkSkyWeather.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = DarkSkyWeather.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(DarkSkyWeather.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(DarkSkyWeather.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, DarkSkyWeather.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public void fetchLatestWeather() {
        final DarkSkyWeather me = this;

        long now = System.currentTimeMillis();

        if (now - this.mLastWeatherFetch > this.mFetchInterval) {
            android.location.Location lastPlace = Location.getInstance(this.mContext).getLastKnownLocation();

            if (lastPlace != null) {
                this.mLastWeatherFetch = now;

                String key = this.mContext.getString(R.string.dark_sky_api_key);
                String fetchUrl = this.mContext.getString(R.string.generator_dark_sky_url, key, lastPlace.getLatitude(), lastPlace.getLongitude(), now / 1000);

                OkHttpClient client = new OkHttpClient();

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
                            throw new IOException("Unexpected code " + response);
                        } else {
                            String body = response.body().string();

                            try {
                                JSONObject weather = new JSONObject(body);

                                if (weather.has("currently")) {
                                    JSONObject currently = weather.getJSONObject("currently");

                                    ContentValues values = new ContentValues();
                                    values.put(DarkSkyWeather.HISTORY_OBSERVED, now);

                                    Bundle updated = new Bundle();
                                    updated.putLong(DarkSkyWeather.HISTORY_OBSERVED, now);

                                    Bundle metadata = new Bundle();
                                    updated.putBundle(Generator.PDK_METADATA, metadata);

                                    if (weather.has("timezone")) {
                                        values.put(DarkSkyWeather.HISTORY_TIMEZONE, weather.getString("timezone"));
                                        updated.putString(DarkSkyWeather.HISTORY_TIMEZONE, weather.getString("timezone"));
                                    }

                                    if (weather.has("latitude")) {
                                        values.put(DarkSkyWeather.HISTORY_LATITUDE, weather.getDouble("latitude"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_LATITUDE, weather.getDouble("latitude"));
                                        metadata.putDouble(Generator.LATITUDE, weather.getDouble("latitude"));
                                    }

                                    if (weather.has("longitude")) {
                                        values.put(DarkSkyWeather.HISTORY_LONGITUDE, weather.getDouble("longitude"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_LONGITUDE, weather.getDouble("longitude"));
                                        metadata.putDouble(Generator.LONGITUDE, weather.getDouble("longitude"));
                                    }

                                    if (currently.has("summary")) {
                                        values.put(DarkSkyWeather.HISTORY_SUMMARY, currently.getString("summary"));
                                        updated.putString(DarkSkyWeather.HISTORY_SUMMARY, currently.getString("summary"));
                                    }

                                    if (currently.has("temperature")) {
                                        values.put(DarkSkyWeather.HISTORY_TEMPERATURE, currently.getDouble("temperature"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_TEMPERATURE, currently.getDouble("temperature"));
                                    }

                                    if (currently.has("apparentTemperature")) {
                                        values.put(DarkSkyWeather.HISTORY_APPARENT_TEMPERATURE, currently.getDouble("apparentTemperature"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_APPARENT_TEMPERATURE, currently.getDouble("apparentTemperature"));
                                    }

                                    if (currently.has("humidity")) {
                                        values.put(DarkSkyWeather.HISTORY_HUMIDITY, currently.getDouble("humidity"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_HUMIDITY, currently.getDouble("humidity"));
                                    }

                                    if (currently.has("dewPoint")) {
                                        values.put(DarkSkyWeather.HISTORY_DEW_POINT, currently.getDouble("dewPoint"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_DEW_POINT, currently.getDouble("dewPoint"));
                                    }

                                    if (currently.has("windSpeed")) {
                                        values.put(DarkSkyWeather.HISTORY_WIND_SPEED, currently.getDouble("windSpeed"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_WIND_SPEED, currently.getDouble("windSpeed"));
                                    }

                                    if (currently.has("windGust")) {
                                        values.put(DarkSkyWeather.HISTORY_WIND_GUST_SPEED, currently.getDouble("windGust"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_WIND_GUST_SPEED, currently.getDouble("windGust"));
                                    }

                                    if (currently.has("windBearing")) {
                                        values.put(DarkSkyWeather.HISTORY_WIND_BEARING, currently.getDouble("windBearing"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_WIND_BEARING, currently.getDouble("windBearing"));
                                    }

                                    if (currently.has("cloudCover")) {
                                        values.put(DarkSkyWeather.HISTORY_CLOUD_COVER, currently.getDouble("cloudCover"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_CLOUD_COVER, currently.getDouble("cloudCover"));
                                    }

                                    if (currently.has("uvIndex")) {
                                        values.put(DarkSkyWeather.HISTORY_UV_INDEX, currently.getDouble("uvIndex"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_UV_INDEX, currently.getDouble("uvIndex"));
                                    }

                                    if (currently.has("ozone")) {
                                        values.put(DarkSkyWeather.HISTORY_OZONE, currently.getDouble("ozone"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_OZONE, currently.getDouble("ozone"));
                                    }

                                    if (currently.has("pressure")) {
                                        values.put(DarkSkyWeather.HISTORY_AIR_PRESSURE, currently.getDouble("pressure"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_AIR_PRESSURE, currently.getDouble("pressure"));
                                    }

                                    if (currently.has("visibility")) {
                                        values.put(DarkSkyWeather.HISTORY_VISIBILITY, currently.getDouble("visibility"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_VISIBILITY, currently.getDouble("visibility"));
                                    }

                                    if (currently.has("precipIntensity")) {
                                        values.put(DarkSkyWeather.HISTORY_PRECIPITATION_INTENSITY, currently.getDouble("precipIntensity"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_PRECIPITATION_INTENSITY, currently.getDouble("precipIntensity"));
                                    }

                                    if (currently.has("precipProbability")) {
                                        values.put(DarkSkyWeather.HISTORY_PRECIPITATION_PROBABILITY, currently.getDouble("precipProbability"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_PRECIPITATION_PROBABILITY, currently.getDouble("precipProbability"));
                                    }

                                    if (currently.has("windSpeed")) {
                                        values.put(DarkSkyWeather.HISTORY_WIND_SPEED, currently.getDouble("windSpeed"));
                                        updated.putDouble(DarkSkyWeather.HISTORY_WIND_SPEED, currently.getDouble("windSpeed"));
                                    }

                                    values.put(DarkSkyWeather.HISTORY_FULL_READING, weather.toString());
                                    updated.putString(DarkSkyWeather.HISTORY_FULL_READING, weather.toString());

                                    me.mDatabase.insert(DarkSkyWeather.TABLE_HISTORY, null, values);

                                    Generators.getInstance(me.mContext).notifyGeneratorUpdated(DarkSkyWeather.GENERATOR_IDENTIFIER, updated);
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
        }
    }
}
