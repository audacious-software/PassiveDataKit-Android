package com.audacious_software.passive_data_kit.generators.wearables;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.GeneratorViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ClientSecretBasic;
import net.openid.appauth.TokenResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import okhttp3.Authenticator;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.tls.HandshakeCertificates;

@SuppressWarnings({"PointlessBooleanExpression", "SimplifiableIfStatement"})
public class Fitbit extends Generator {
    public static final String GENERATOR_IDENTIFIER = "pdk-fitbit";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String HISTORY_OBSERVED = "observed";
    private static final String DATABASE_PATH = "pdk-fitbit.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String LAST_DATA_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.LAST_DATA_FETCH";

    private static final String DATA_FETCH_INTERVAL = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.DATA_FETCH_INTERVAL";
    private static final long DATA_FETCH_INTERVAL_DEFAULT = (15 * 60 * 1000); // (60 * 60 * 1000);

    private static final String LAST_REFRESH = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.LAST_REFRESH";

    private static final String ACTIVITY_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.ACTIVITY_ENABLED";
    private static final boolean ACTIVITY_ENABLED_DEFAULT = true;

    private static final String SLEEP_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.SLEEP_ENABLED";
    private static final boolean SLEEP_ENABLED_DEFAULT = true;

    private static final String HEART_RATE_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.HEART_RATE_ENABLED";
    private static final boolean HEART_RATE_ENABLED_DEFAULT = true;

    private static final String WEIGHT_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.WEIGHT_ENABLED";
    private static final boolean WEIGHT_ENABLED_DEFAULT = true;

    @SuppressWarnings("WeakerAccess")
    public static final String OPTION_OAUTH_CALLBACK_URL = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.OPTION_CALLBACK_URL";
    @SuppressWarnings("WeakerAccess")
    public static final String OPTION_OAUTH_CLIENT_ID = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.OPTION_OAUTH_CLIENT_ID";
    @SuppressWarnings("WeakerAccess")
    public static final String OPTION_OAUTH_CLIENT_SECRET = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.OPTION_OAUTH_CLIENT_SECRET";

    private static final String API_ACTION_ACTIVITY_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.API_ACTION_ACTIVITY_URL_LAST_FETCH";
    private static final String API_ACTION_SLEEP_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.API_ACTION_SLEEP_URL_LAST_FETCH";
    private static final String API_ACTION_HEART_RATE_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.API_ACTION_HEART_RATE_URL_LAST_FETCH";
    private static final String API_ACTION_WEIGHT_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.API_ACTION_WEIGHT_URL_LAST_FETCH";

    private static final String API_ACTION_ACTIVITY_URL = "https://api.fitbit.com/1/user/-/activities/date/today.json";
    private static final String API_ACTION_ACTIVITY_PREFIX = "https://api.fitbit.com/1/user/-/activities/date/";
    private static final String API_ACTION_SLEEP_URL = "https://api.fitbit.com/1.2/user/-/sleep/date/today.json";
    private static final String API_ACTION_HEART_RATE_URL = "https://api.fitbit.com/1/user/-/activities/heart/date/today/1d.json";
    private static final String API_ACTION_WEIGHT_URL = "https://api.fitbit.com/1/user/-/body/log/weight/date/today.json";

    private static final String PERSISTED_AUTH = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.PERSISTED_AUTH";
    private static final String ACCESS_TOKEN = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.ACCESS_TOKEN";
    private static final String REFRESH_TOKEN = "com.audacious_software.passive_data_kit.generators.wearables.Fitbit.REFRESH_TOKEN";

    private static final Uri OAUTH_AUTHORIZATION_ENDPOINT = Uri.parse("https://www.fitbit.com/oauth2/authorize");
    private static final Uri OAUTH_TOKEN_ENDPOINT = Uri.parse("https://api.fitbit.com/oauth2/token");

    private static final String PARAM_REQUESTED_DATE = "PARAM_REQUESTED_DATE";

    private static final String HISTORY_FETCHED = "fetched";

    private static final String TABLE_ACTIVITY_HISTORY = "activity_history";
    private static final String ACTIVITY_DATE_START = "date_start";
    private static final String ACTIVITY_STEPS = "steps";
    private static final String ACTIVITY_DISTANCE = "distance";
    private static final String ACTIVITY_FLOORS = "floors";
    private static final String ACTIVITY_ELEVATION = "elevation";
    private static final String ACTIVITY_CALORIES_ACTIVITY = "calories_activity";
    private static final String ACTIVITY_CALORIES_BMR = "calories_bmr";
    private static final String ACTIVITY_CALORIES_MARGINAL = "calories_marginal";
    private static final String ACTIVITY_MINUTES_VERY_ACTIVE = "minutes_very_active";
    private static final String ACTIVITY_MINUTES_FAIRLY_ACTIVE = "minutes_fairly_active";
    private static final String ACTIVITY_MINUTES_LIGHTLY_ACTIVE = "minutes_lightly_active";
    private static final String ACTIVITY_MINUTES_SEDENTARY = "minutes_sedentary";

    private static final String TABLE_SLEEP_HISTORY = "sleep_history";
    private static final String SLEEP_START_TIME = "start";
    private static final String SLEEP_DURATION = "duration";
    private static final String SLEEP_IS_MAIN_SLEEP = "is_main_sleep";
    private static final String SLEEP_MINUTES_ASLEEP = "minutes_asleep";
    private static final String SLEEP_MINUTES_AWAKE = "minutes_awake";
    private static final String SLEEP_MINUTES_AFTER_WAKE = "minutes_after_wake";
    private static final String SLEEP_MINUTES_TO_SLEEP = "minutes_to_sleep";
    private static final String SLEEP_MINUTES_IN_BED = "minutes_in_bed";
    private static final String SLEEP_TYPE = "sleep_type";
    private static final String SLEEP_DEEP_PERIODS = "deep_periods";
    private static final String SLEEP_DEEP_MINUTES = "deep_minutes";
    private static final String SLEEP_LIGHT_PERIODS = "light_periods";
    private static final String SLEEP_LIGHT_MINUTES = "light_minutes";
    private static final String SLEEP_REM_PERIODS = "rem_periods";
    private static final String SLEEP_REM_MINUTES = "rem_minutes";
    private static final String SLEEP_WAKE_PERIODS = "wake_periods";
    private static final String SLEEP_WAKE_MINUTES = "wake_minutes";
    private static final String SLEEP_ASLEEP_PERIODS = "asleep_periods";
    private static final String SLEEP_ASLEEP_MINUTES = "asleep_minutes";
    private static final String SLEEP_AWAKE_PERIODS = "awake_periods";
    private static final String SLEEP_AWAKE_MINUTES = "awake_minutes";
    private static final String SLEEP_RESTLESS_PERIODS = "restless_periods";
    private static final String SLEEP_RESTLESS_MINUTES = "restless_minutes";

    private static final String TABLE_HEART_RATE_HISTORY = "heart_rate_history";
    private static final String HEART_RATE_OUT_MIN = "out_min";
    private static final String HEART_RATE_OUT_MAX = "out_max";
    private static final String HEART_RATE_OUT_MINUTES = "out_minutes";
    private static final String HEART_RATE_OUT_CALORIES = "out_calories";
    private static final String HEART_RATE_FAT_MIN = "fat_burn_min";
    private static final String HEART_RATE_FAT_MAX = "fat_burn_max";
    private static final String HEART_RATE_FAT_MINUTES = "fat_burn_minutes";
    private static final String HEART_RATE_FAT_CALORIES = "fat_burn_calories";
    private static final String HEART_RATE_CARDIO_MIN = "cardio_min";
    private static final String HEART_RATE_CARDIO_MAX = "cardio_max";
    private static final String HEART_RATE_CARDIO_MINUTES = "cardio_minutes";
    private static final String HEART_RATE_CARDIO_CALORIES = "cardio_calories";
    private static final String HEART_RATE_PEAK_MIN = "peak_min";
    private static final String HEART_RATE_PEAK_MAX = "peak_max";
    private static final String HEART_RATE_PEAK_MINUTES = "peak_minutes";
    private static final String HEART_RATE_PEAK_CALORIES = "peak_calories";
    private static final String HEART_RATE_RESTING_RATE = "resting_rate";

    private static final String TABLE_WEIGHT_HISTORY = "weight_history";
    private static final String WEIGHT_LOG_ID = "log_id";
    private static final String WEIGHT_WEIGHT = "weight";
    private static final String WEIGHT_BMI = "bmi";
    private static final String WEIGHT_SOURCE = "source";

    private static final String FITBIT_TYPE = "fitbit_type";
    private static final String FITBIT_TYPE_ACTIVITY = "activity";
    private static final String FITBIT_TYPE_SLEEP = "sleep";
    private static final String FITBIT_TYPE_HEART_RATE = "heart_rate";
    private static final String FITBIT_TYPE_WEIGHT = "weight";

    private static Fitbit sInstance = null;
    private Context mContext = null;
    private SQLiteDatabase mDatabase = null;
    private Handler mHandler = null;
    private Runnable fetchRequest = null;

    private final Map<String, String> mProperties = new HashMap<>();

    private int mPage = 0;

    private long mLatestTimestamp = -1;
    private boolean mIsMandatory = true;


    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return Fitbit.GENERATOR_IDENTIFIER;
    }

    public static synchronized Fitbit getInstance(Context context) {
        if (Fitbit.sInstance == null) {
            Fitbit.sInstance = new Fitbit(context.getApplicationContext());
        }

        return Fitbit.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public Fitbit(Context context) {
        super(context);

        this.mContext = context.getApplicationContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.remove(Fitbit.LAST_DATA_FETCH);
        e.apply();

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Fitbit.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                try {
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_fitbit_create_activity_history_table));
                } catch (SQLException ex) {
                    // Table already exists...
                }

                try {
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_fitbit_create_heart_rate_history_table));
                } catch (SQLException ex) {
                    // Table already exists...
                }

                try {
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_fitbit_create_sleep_history_table));
                } catch (SQLException ex) {
                    // Table already exists...
                }

                try {
                    this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_fitbit_create_weight_history_table));
                } catch (SQLException ex) {
                    // Table already exists...
                }
        }

        if (version != Fitbit.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, Fitbit.DATABASE_VERSION);
        }
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        Fitbit.getInstance(context).startGenerator();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void startGenerator() {
        final Fitbit me = this;

        if (this.mHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                this.mHandler.getLooper().quitSafely();
            } else {
                this.mHandler.getLooper().quit();
            }

            this.mHandler = null;
        }

        this.fetchRequest = new Runnable() {
            @Override
            public void run() {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);
                long fetchInterval = prefs.getLong(Fitbit.DATA_FETCH_INTERVAL, Fitbit.DATA_FETCH_INTERVAL_DEFAULT);

                if (me.isAuthenticated()) {
                    long lastFetch = prefs.getLong(Fitbit.LAST_DATA_FETCH, 0);

                    long now = System.currentTimeMillis();

                    if (now - lastFetch > fetchInterval) {
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                if (prefs.getBoolean(Fitbit.ACTIVITY_ENABLED, Fitbit.ACTIVITY_ENABLED_DEFAULT)) {
                                    me.fetchActivity();
                                }

                                if (prefs.getBoolean(Fitbit.HEART_RATE_ENABLED, Fitbit.HEART_RATE_ENABLED_DEFAULT)) {
                                    me.fetchHeartRate();
                                }

                                if (prefs.getBoolean(Fitbit.SLEEP_ENABLED, Fitbit.SLEEP_ENABLED_DEFAULT)) {
                                    me.fetchSleep();
                                }

                                if (prefs.getBoolean(Fitbit.WEIGHT_ENABLED, Fitbit.WEIGHT_ENABLED_DEFAULT)) {
                                    me.fetchWeight();
                                }
                            }
                        };

                        Thread t = new Thread(r);
                        t.start();

                        SharedPreferences.Editor e = prefs.edit();
                        e.putLong(Fitbit.LAST_DATA_FETCH, now);
                        e.apply();
                    }
                }

                if (me.mHandler != null) {
                    me.mHandler.postDelayed(this, fetchInterval);
                }
            }
        };

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

        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        me.mHandler.post(this.fetchRequest);

        Generators.getInstance(this.mContext).registerCustomViewClass(Fitbit.GENERATOR_IDENTIFIER, Fitbit.class);

        this.flushCachedData();
    }

    public void refresh() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.remove(Fitbit.LAST_DATA_FETCH);
        e.apply();

        if (this.fetchRequest != null) {
            if (this.mHandler != null) {
                this.mHandler.removeCallbacks(this.fetchRequest);
            }

            this.mHandler.post(this.fetchRequest);
        }
    }

    @SuppressLint("SimpleDateFormat")
    private void queryApi(final String apiUrl, final Map<String, Object> params) {
        final Fitbit me = this;

        if (this.mHandler == null) {
            Handler h = new Handler(Looper.getMainLooper());

            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    me.queryApi(apiUrl, params);
                }
            }, 100);

            return;
        }

        this.mHandler.post(new Runnable() {
            @Override
            public void run() {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

                final String accessToken = prefs.getString(Fitbit.ACCESS_TOKEN, null);

                if (accessToken == null) {
                    return;
                }

                long lastRefresh = prefs.getLong(Fitbit.LAST_REFRESH, 0);

                long now = System.currentTimeMillis();

                if (now - lastRefresh > 60 * 60 * 1000) {
                    String refreshToken = prefs.getString(Fitbit.REFRESH_TOKEN, null);

                    SharedPreferences.Editor e = prefs.edit();
                    e.putLong(Fitbit.LAST_REFRESH, now);
                    e.apply();

                    HandshakeCertificates certificates = PassiveDataKit.getInstance(me.mContext).fetchTrustedCertificates();

                    final OkHttpClient client = new OkHttpClient.Builder()
                            .authenticator(new Authenticator() {
                                @Override
                                public Request authenticate(Route route, Response response) throws IOException {
                                    String credential = Credentials.basic(me.getProperty(Fitbit.OPTION_OAUTH_CLIENT_ID), me.getProperty(Fitbit.OPTION_OAUTH_CLIENT_SECRET));
                                    return response.request().newBuilder().header("Authorization", credential).build();
                                }
                            })
                            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager())
                            .build();

                    FormBody.Builder bodyBuilder = new FormBody.Builder();
                    bodyBuilder.add("grant_type", "refresh_token");
                    bodyBuilder.add("expires_in", "3600");
                    bodyBuilder.add("refresh_token", refreshToken);

                    Request request = new Request.Builder()
                            .url("https://api.fitbit.com/oauth2/token")
                            .post(bodyBuilder.build())
                            .build();

                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                            Log.e("PDK", "FITBIT EXCEPTION: " + e);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                            if (response.code() == 200) {
                                try {
                                    JSONObject responseJson = new JSONObject(response.body().string());

                                    if (responseJson.has("access_token") && responseJson.has("refresh_token")) {
                                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);
                                        SharedPreferences.Editor e = prefs.edit();

                                        e.putString(Fitbit.ACCESS_TOKEN, responseJson.getString("access_token"));
                                        e.putString(Fitbit.REFRESH_TOKEN, responseJson.getString("refresh_token"));
                                        e.putLong(Fitbit.LAST_REFRESH, System.currentTimeMillis());

                                        e.apply();

                                        me.queryApi(apiUrl, params);
                                    }
                                } catch (JSONException e1) {
                                    e1.printStackTrace();
                                }
                            } else if (response.code() == 401) {
                                me.logout();
                            }
                        }
                    });
                } else {
                    HandshakeCertificates certificates = PassiveDataKit.getInstance(me.mContext).fetchTrustedCertificates();

                    OkHttpClient client = new OkHttpClient.Builder().addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                                    Request newRequest = chain.request().newBuilder()
                                            .addHeader("Authorization", "Bearer " + accessToken)
                                            .build();
                                    return chain.proceed(newRequest);
                                }
                            })
                            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager())
                            .build();

                    Request request = new Request.Builder()
                            .url(apiUrl)
                            .build();

                    try {
                        Response response = client.newCall(request).execute();

                        String fitbitResponse = response.body().string();

                        if (response.isSuccessful()) {
                            SharedPreferences.Editor e = prefs.edit();

                            JSONObject apiResponse = new JSONObject(fitbitResponse);

                            if (Fitbit.API_ACTION_ACTIVITY_URL.equals(apiUrl)) {
                                e.putLong(Fitbit.API_ACTION_ACTIVITY_URL_LAST_FETCH, System.currentTimeMillis());
                            } else if (Fitbit.API_ACTION_SLEEP_URL.equals(apiUrl)) {
                                e.putLong(Fitbit.API_ACTION_SLEEP_URL_LAST_FETCH, System.currentTimeMillis());
                            } else if (Fitbit.API_ACTION_WEIGHT_URL.equals(apiUrl)) {
                                e.putLong(Fitbit.API_ACTION_WEIGHT_URL_LAST_FETCH, System.currentTimeMillis());
                            } else if (Fitbit.API_ACTION_HEART_RATE_URL.equals(apiUrl)) {
                                e.putLong(Fitbit.API_ACTION_HEART_RATE_URL_LAST_FETCH, System.currentTimeMillis());
                            }

                            e.apply();

                            if (Fitbit.API_ACTION_ACTIVITY_URL.equals(apiUrl)) {
                                me.logActivity(apiResponse, params);
                            } else if (Fitbit.API_ACTION_SLEEP_URL.equals(apiUrl)) {
                                me.logSleep(apiResponse);
                            } else if (Fitbit.API_ACTION_WEIGHT_URL.equals(apiUrl)) {
                                me.logWeight(apiResponse);
                            } else if (Fitbit.API_ACTION_HEART_RATE_URL.equals(apiUrl)) {
                                me.logHeartRate(apiResponse);
                            } else if (apiUrl.startsWith(Fitbit.API_ACTION_ACTIVITY_PREFIX)) {
                                me.logActivity(apiResponse, params);
                            }
                        } else {
                            if (response.code() == 401) {
                                me.logout();
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void fetchActivity() {
        this.queryApi(Fitbit.API_ACTION_ACTIVITY_URL, null);
    }

    public void fetchActivity(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        String urlString = Fitbit.API_ACTION_ACTIVITY_PREFIX + formatter.format(date) + ".json";

        Map<String, Object> params = new HashMap<>();
        params.put(Fitbit.PARAM_REQUESTED_DATE, date);

        this.queryApi(urlString, params);
    }

    private void logActivity(JSONObject response, Map<String, Object> params) throws JSONException {
        if (response.has("summary")) {
            JSONObject summary = response.getJSONObject("summary");

            long now = System.currentTimeMillis();

            Date requestDate = new Date();

            if (params != null && params.containsKey(Fitbit.PARAM_REQUESTED_DATE)) {
                requestDate = (Date) params.get(Fitbit.PARAM_REQUESTED_DATE);
            }

            ContentValues values = new ContentValues();
            values.put(Fitbit.HISTORY_FETCHED, now);
            values.put(Fitbit.HISTORY_OBSERVED, now);

            Calendar cal = Calendar.getInstance();

            cal.setTime(requestDate);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            values.put(Fitbit.ACTIVITY_DATE_START, cal.getTimeInMillis());
            values.put(Fitbit.ACTIVITY_STEPS, summary.getDouble("steps"));
            values.put(Fitbit.ACTIVITY_FLOORS, summary.getDouble("floors"));
            values.put(Fitbit.ACTIVITY_ELEVATION, summary.getDouble("elevation"));
            values.put(Fitbit.ACTIVITY_CALORIES_ACTIVITY, summary.getDouble("activityCalories"));
            values.put(Fitbit.ACTIVITY_CALORIES_BMR, summary.getDouble("caloriesBMR"));
            values.put(Fitbit.ACTIVITY_CALORIES_MARGINAL, summary.getDouble("marginalCalories"));
            values.put(Fitbit.ACTIVITY_MINUTES_VERY_ACTIVE, summary.getDouble("veryActiveMinutes"));
            values.put(Fitbit.ACTIVITY_MINUTES_FAIRLY_ACTIVE, summary.getDouble("fairlyActiveMinutes"));
            values.put(Fitbit.ACTIVITY_MINUTES_LIGHTLY_ACTIVE, summary.getDouble("lightlyActiveMinutes"));
            values.put(Fitbit.ACTIVITY_MINUTES_SEDENTARY, summary.getDouble("sedentaryMinutes"));

            double distance = 0;

            if (summary.has("distances")) {
                JSONArray distances = summary.getJSONArray("distances");

                for (int i = 0; i < distances.length(); i++) {
                    JSONObject distanceRecord = distances.getJSONObject(i);

                    distance += distanceRecord.getDouble("distance");
                }
            }

            values.put(Fitbit.ACTIVITY_DISTANCE, distance);

            this.mDatabase.insert(Fitbit.TABLE_ACTIVITY_HISTORY, null, values);

            Bundle updated = new Bundle();

            updated.putLong(Fitbit.HISTORY_OBSERVED, values.getAsLong(Fitbit.HISTORY_FETCHED));
            updated.putLong(Fitbit.HISTORY_FETCHED, values.getAsLong(Fitbit.HISTORY_FETCHED));
            updated.putLong(Fitbit.ACTIVITY_DATE_START, values.getAsLong(Fitbit.ACTIVITY_DATE_START));
            updated.putDouble(Fitbit.ACTIVITY_STEPS, values.getAsDouble(Fitbit.ACTIVITY_STEPS));
            updated.putDouble(Fitbit.ACTIVITY_DISTANCE, values.getAsDouble(Fitbit.ACTIVITY_DISTANCE));
            updated.putDouble(Fitbit.ACTIVITY_FLOORS, values.getAsDouble(Fitbit.ACTIVITY_FLOORS));
            updated.putDouble(Fitbit.ACTIVITY_ELEVATION, values.getAsDouble(Fitbit.ACTIVITY_ELEVATION));
            updated.putDouble(Fitbit.ACTIVITY_CALORIES_ACTIVITY, values.getAsDouble(Fitbit.ACTIVITY_CALORIES_ACTIVITY));
            updated.putDouble(Fitbit.ACTIVITY_CALORIES_BMR, values.getAsDouble(Fitbit.ACTIVITY_CALORIES_BMR));
            updated.putDouble(Fitbit.ACTIVITY_CALORIES_MARGINAL, values.getAsDouble(Fitbit.ACTIVITY_CALORIES_MARGINAL));
            updated.putDouble(Fitbit.ACTIVITY_MINUTES_VERY_ACTIVE, values.getAsDouble(Fitbit.ACTIVITY_MINUTES_VERY_ACTIVE));
            updated.putDouble(Fitbit.ACTIVITY_MINUTES_FAIRLY_ACTIVE, values.getAsDouble(Fitbit.ACTIVITY_MINUTES_FAIRLY_ACTIVE));
            updated.putDouble(Fitbit.ACTIVITY_MINUTES_LIGHTLY_ACTIVE, values.getAsDouble(Fitbit.ACTIVITY_MINUTES_LIGHTLY_ACTIVE));
            updated.putDouble(Fitbit.ACTIVITY_MINUTES_SEDENTARY, values.getAsDouble(Fitbit.ACTIVITY_MINUTES_SEDENTARY));

            updated.putString(Fitbit.FITBIT_TYPE, Fitbit.FITBIT_TYPE_ACTIVITY);

            Generators.getInstance(this.mContext).notifyGeneratorUpdated(Fitbit.GENERATOR_IDENTIFIER, updated);
        }
    }

    public long getStepsForPeriod(long start, long end) {
        long steps = 0;

        while (start < end) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(start);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long dayStart = calendar.getTimeInMillis();
            long dayEnd = dayStart + (24 * 60 * 60 * 1000);

            String where = Fitbit.ACTIVITY_DATE_START + " >= ? AND " + Fitbit.ACTIVITY_DATE_START + " < ?";
            String[] args = { "" + dayStart, "" + dayEnd};

            Cursor c = this.mDatabase.query(Fitbit.TABLE_ACTIVITY_HISTORY, null, where, args, null, null, Fitbit.ACTIVITY_STEPS + " DESC", "1");

            long daySteps = 0;

            if (c.moveToNext()) {
                daySteps = c.getLong(c.getColumnIndex(Fitbit.ACTIVITY_STEPS));
            }

            c.close();

            if (daySteps == 0) {
                this.fetchActivity(new Date(dayStart));
            }

            steps += daySteps;

            start += (24 * 60 * 60 * 1000);
        }

        return steps;
    }

    private void fetchSleep() {
        this.queryApi(Fitbit.API_ACTION_SLEEP_URL, null);
    }

    private void logSleep(JSONObject response) throws JSONException {
        if (response.has("sleeps")) {
            long now = System.currentTimeMillis();

            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat startFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

            JSONArray sleeps = response.getJSONArray("sleep");

            for (int i = 0 ; i < sleeps.length(); i++) {
                JSONObject sleep = sleeps.getJSONObject(i);

                try {
                    ContentValues values = new ContentValues();
                    values.put(Fitbit.HISTORY_FETCHED, now);
                    values.put(Fitbit.HISTORY_OBSERVED, now);

                    Bundle updated = new Bundle();

                    updated.putLong(Fitbit.HISTORY_OBSERVED, values.getAsLong(Fitbit.HISTORY_FETCHED));
                    updated.putLong(Fitbit.HISTORY_FETCHED, values.getAsLong(Fitbit.HISTORY_FETCHED));

                    Date sleepStart = startFormat.parse(sleep.getString("startTime"));

                    values.put(Fitbit.SLEEP_START_TIME, sleepStart.getTime());
                    values.put(Fitbit.SLEEP_DURATION, sleep.getLong("duration"));
                    values.put(Fitbit.SLEEP_IS_MAIN_SLEEP, sleep.getBoolean("isMainSleep"));

                    updated.putLong(Fitbit.SLEEP_START_TIME, values.getAsLong(Fitbit.SLEEP_START_TIME));
                    updated.putLong(Fitbit.SLEEP_START_TIME, values.getAsLong(Fitbit.SLEEP_START_TIME));
                    updated.putBoolean(Fitbit.SLEEP_IS_MAIN_SLEEP, values.getAsBoolean(Fitbit.SLEEP_IS_MAIN_SLEEP));

                    values.put(Fitbit.SLEEP_MINUTES_ASLEEP, sleep.getDouble("minutesAsleep"));
                    values.put(Fitbit.SLEEP_MINUTES_AWAKE, sleep.getDouble("minutesAwake"));
                    values.put(Fitbit.SLEEP_MINUTES_AFTER_WAKE, sleep.getDouble("minutesAfterWakeup"));
                    values.put(Fitbit.SLEEP_MINUTES_TO_SLEEP, sleep.getDouble("minutesToFallAsleep"));
                    values.put(Fitbit.SLEEP_MINUTES_IN_BED, sleep.getDouble("timeInBed"));

                    updated.putDouble(Fitbit.SLEEP_MINUTES_ASLEEP, values.getAsDouble(Fitbit.SLEEP_MINUTES_ASLEEP));
                    updated.putDouble(Fitbit.SLEEP_MINUTES_AWAKE, values.getAsDouble(Fitbit.SLEEP_MINUTES_AWAKE));
                    updated.putDouble(Fitbit.SLEEP_MINUTES_AFTER_WAKE, values.getAsDouble(Fitbit.SLEEP_MINUTES_AFTER_WAKE));
                    updated.putDouble(Fitbit.SLEEP_MINUTES_TO_SLEEP, values.getAsDouble(Fitbit.SLEEP_MINUTES_TO_SLEEP));
                    updated.putDouble(Fitbit.SLEEP_MINUTES_IN_BED, values.getAsDouble(Fitbit.SLEEP_MINUTES_IN_BED));

                    values.put(Fitbit.SLEEP_TYPE, sleep.getString("type"));

                    updated.putString(Fitbit.SLEEP_TYPE, values.getAsString(Fitbit.SLEEP_TYPE));

                    if ("stages".equals(sleep.getString("type"))) {
                        JSONObject summary = sleep.getJSONObject("levels").getJSONObject("summary");

                        if (summary.has("deep")) {
                            JSONObject deep = summary.getJSONObject("deep");

                            values.put(Fitbit.SLEEP_DEEP_PERIODS, deep.getDouble("count"));
                            values.put(Fitbit.SLEEP_DEEP_MINUTES, deep.getDouble("minutes"));

                            updated.putDouble(Fitbit.SLEEP_DEEP_PERIODS, values.getAsDouble(Fitbit.SLEEP_DEEP_PERIODS));
                            updated.putDouble(Fitbit.SLEEP_DEEP_MINUTES, values.getAsDouble(Fitbit.SLEEP_DEEP_MINUTES));
                        }

                        if (summary.has("light")) {
                            JSONObject deep = summary.getJSONObject("light");

                            values.put(Fitbit.SLEEP_LIGHT_PERIODS, deep.getDouble("count"));
                            values.put(Fitbit.SLEEP_LIGHT_MINUTES, deep.getDouble("minutes"));

                            updated.putDouble(Fitbit.SLEEP_LIGHT_PERIODS, values.getAsDouble(Fitbit.SLEEP_LIGHT_PERIODS));
                            updated.putDouble(Fitbit.SLEEP_LIGHT_MINUTES, values.getAsDouble(Fitbit.SLEEP_LIGHT_MINUTES));
                        }

                        if (summary.has("rem")) {
                            JSONObject deep = summary.getJSONObject("rem");

                            values.put(Fitbit.SLEEP_REM_PERIODS, deep.getDouble("count"));
                            values.put(Fitbit.SLEEP_REM_MINUTES, deep.getDouble("minutes"));

                            updated.putDouble(Fitbit.SLEEP_REM_PERIODS, values.getAsDouble(Fitbit.SLEEP_REM_PERIODS));
                            updated.putDouble(Fitbit.SLEEP_REM_MINUTES, values.getAsDouble(Fitbit.SLEEP_REM_MINUTES));
                        }

                        if (summary.has("wake")) {
                            JSONObject deep = summary.getJSONObject("wake");

                            values.put(Fitbit.SLEEP_WAKE_PERIODS, deep.getDouble("count"));
                            values.put(Fitbit.SLEEP_WAKE_MINUTES, deep.getDouble("minutes"));

                            updated.putDouble(Fitbit.SLEEP_WAKE_PERIODS, values.getAsDouble(Fitbit.SLEEP_WAKE_PERIODS));
                            updated.putDouble(Fitbit.SLEEP_WAKE_MINUTES, values.getAsDouble(Fitbit.SLEEP_WAKE_MINUTES));
                        }
                    } else if ("classic".equals(sleep.getString("type"))) {
                        JSONObject summary = sleep.getJSONObject("levels").getJSONObject("summary");

                        if (summary.has("asleep")) {
                            JSONObject deep = summary.getJSONObject("asleep");

                            values.put(Fitbit.SLEEP_ASLEEP_PERIODS, deep.getDouble("count"));
                            values.put(Fitbit.SLEEP_ASLEEP_MINUTES, deep.getDouble("minutes"));

                            updated.putDouble(Fitbit.SLEEP_ASLEEP_PERIODS, values.getAsDouble(Fitbit.SLEEP_ASLEEP_PERIODS));
                            updated.putDouble(Fitbit.SLEEP_ASLEEP_MINUTES, values.getAsDouble(Fitbit.SLEEP_ASLEEP_MINUTES));
                        }

                        if (summary.has("awake")) {
                            JSONObject deep = summary.getJSONObject("awake");

                            values.put(Fitbit.SLEEP_AWAKE_PERIODS, deep.getDouble("count"));
                            values.put(Fitbit.SLEEP_AWAKE_MINUTES, deep.getDouble("minutes"));

                            updated.putDouble(Fitbit.SLEEP_AWAKE_PERIODS, values.getAsDouble(Fitbit.SLEEP_AWAKE_PERIODS));
                            updated.putDouble(Fitbit.SLEEP_AWAKE_MINUTES, values.getAsDouble(Fitbit.SLEEP_AWAKE_MINUTES));
                        }

                        if (summary.has("restless")) {
                            JSONObject deep = summary.getJSONObject("restless");

                            values.put(Fitbit.SLEEP_RESTLESS_PERIODS, deep.getDouble("count"));
                            values.put(Fitbit.SLEEP_RESTLESS_MINUTES, deep.getDouble("minutes"));

                            updated.putDouble(Fitbit.SLEEP_RESTLESS_PERIODS, values.getAsDouble(Fitbit.SLEEP_RESTLESS_PERIODS));
                            updated.putDouble(Fitbit.SLEEP_RESTLESS_MINUTES, values.getAsDouble(Fitbit.SLEEP_RESTLESS_MINUTES));
                        }
                    }

                    this.mDatabase.insert(Fitbit.TABLE_SLEEP_HISTORY, null, values);

                    updated.putString(Fitbit.FITBIT_TYPE, Fitbit.FITBIT_TYPE_SLEEP);

                    Generators.getInstance(this.mContext).notifyGeneratorUpdated(Fitbit.GENERATOR_IDENTIFIER, updated);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void fetchHeartRate() {
        this.queryApi(Fitbit.API_ACTION_HEART_RATE_URL, null);
    }

    private void logHeartRate(JSONObject response) throws JSONException {
        if (response.has("activities-heart")) {
            long now = System.currentTimeMillis();

            ContentValues values = new ContentValues();
            values.put(Fitbit.HISTORY_FETCHED, now);
            values.put(Fitbit.HISTORY_OBSERVED, now);

            JSONArray zones = response.getJSONArray("activities-heart").getJSONObject(0).getJSONObject("value").getJSONArray("heartRateZones");

            for (int i = 0; i < zones.length(); i++) {
                JSONObject zone = zones.getJSONObject(i);

                if ("Out of Range".equals(zone.getString("name"))) {
                    values.put(Fitbit.HEART_RATE_OUT_MIN, zone.getDouble("min"));
                    values.put(Fitbit.HEART_RATE_OUT_MAX, zone.getDouble("max"));
                    values.put(Fitbit.HEART_RATE_OUT_MINUTES, zone.getDouble("minutes"));
                    values.put(Fitbit.HEART_RATE_OUT_CALORIES, zone.getDouble("caloriesOut"));
                } else if ("Fat Burn".equals(zone.getString("name"))) {
                    values.put(Fitbit.HEART_RATE_FAT_MIN, zone.getDouble("min"));
                    values.put(Fitbit.HEART_RATE_FAT_MAX, zone.getDouble("max"));
                    values.put(Fitbit.HEART_RATE_FAT_MINUTES, zone.getDouble("minutes"));
                    values.put(Fitbit.HEART_RATE_FAT_CALORIES, zone.getDouble("caloriesOut"));
                } else if ("Cardio".equals(zone.getString("name"))) {
                    values.put(Fitbit.HEART_RATE_CARDIO_MIN, zone.getDouble("min"));
                    values.put(Fitbit.HEART_RATE_CARDIO_MAX, zone.getDouble("max"));
                    values.put(Fitbit.HEART_RATE_CARDIO_MINUTES, zone.getDouble("minutes"));
                    values.put(Fitbit.HEART_RATE_CARDIO_CALORIES, zone.getDouble("caloriesOut"));
                } else if ("Peak".equals(zone.getString("name"))) {
                    values.put(Fitbit.HEART_RATE_PEAK_MIN, zone.getDouble("min"));
                    values.put(Fitbit.HEART_RATE_PEAK_MAX, zone.getDouble("max"));
                    values.put(Fitbit.HEART_RATE_PEAK_MINUTES, zone.getDouble("minutes"));
                    values.put(Fitbit.HEART_RATE_PEAK_CALORIES, zone.getDouble("caloriesOut"));
                }
            }

            JSONObject value = response.getJSONArray("activities-heart").getJSONObject(0).getJSONObject("value");

            values.put(Fitbit.HEART_RATE_RESTING_RATE, value.getDouble("restingHeartRate"));

            this.mDatabase.insert(Fitbit.TABLE_HEART_RATE_HISTORY, null, values);

            Bundle updated = new Bundle();

            updated.putLong(Fitbit.HISTORY_OBSERVED, values.getAsLong(Fitbit.HISTORY_FETCHED));
            updated.putLong(Fitbit.HISTORY_FETCHED, values.getAsLong(Fitbit.HISTORY_FETCHED));

            updated.putDouble(Fitbit.HEART_RATE_OUT_MIN, values.getAsDouble(Fitbit.HEART_RATE_OUT_MIN));
            updated.putDouble(Fitbit.HEART_RATE_OUT_MAX, values.getAsDouble(Fitbit.HEART_RATE_OUT_MAX));
            updated.putDouble(Fitbit.HEART_RATE_OUT_MINUTES, values.getAsDouble(Fitbit.HEART_RATE_OUT_MINUTES));
            updated.putDouble(Fitbit.HEART_RATE_OUT_CALORIES, values.getAsDouble(Fitbit.HEART_RATE_OUT_CALORIES));
            updated.putDouble(Fitbit.HEART_RATE_FAT_MIN, values.getAsDouble(Fitbit.HEART_RATE_FAT_MIN));
            updated.putDouble(Fitbit.HEART_RATE_FAT_MAX, values.getAsDouble(Fitbit.HEART_RATE_FAT_MAX));
            updated.putDouble(Fitbit.HEART_RATE_FAT_MINUTES, values.getAsDouble(Fitbit.HEART_RATE_FAT_MINUTES));
            updated.putDouble(Fitbit.HEART_RATE_FAT_CALORIES, values.getAsDouble(Fitbit.HEART_RATE_FAT_CALORIES));
            updated.putDouble(Fitbit.HEART_RATE_CARDIO_MIN, values.getAsDouble(Fitbit.HEART_RATE_CARDIO_MIN));
            updated.putDouble(Fitbit.HEART_RATE_CARDIO_MAX, values.getAsDouble(Fitbit.HEART_RATE_CARDIO_MAX));
            updated.putDouble(Fitbit.HEART_RATE_CARDIO_MINUTES, values.getAsDouble(Fitbit.HEART_RATE_CARDIO_MINUTES));
            updated.putDouble(Fitbit.HEART_RATE_CARDIO_CALORIES, values.getAsDouble(Fitbit.HEART_RATE_CARDIO_CALORIES));
            updated.putDouble(Fitbit.HEART_RATE_PEAK_MIN, values.getAsDouble(Fitbit.HEART_RATE_PEAK_MIN));
            updated.putDouble(Fitbit.HEART_RATE_PEAK_MAX, values.getAsDouble(Fitbit.HEART_RATE_PEAK_MAX));
            updated.putDouble(Fitbit.HEART_RATE_PEAK_MINUTES, values.getAsDouble(Fitbit.HEART_RATE_PEAK_MINUTES));
            updated.putDouble(Fitbit.HEART_RATE_PEAK_CALORIES, values.getAsDouble(Fitbit.HEART_RATE_PEAK_CALORIES));

            updated.putString(Fitbit.FITBIT_TYPE, Fitbit.FITBIT_TYPE_HEART_RATE);

            Generators.getInstance(this.mContext).notifyGeneratorUpdated(Fitbit.GENERATOR_IDENTIFIER, updated);
        }
    }

    private void fetchWeight() {
        this.queryApi(Fitbit.API_ACTION_WEIGHT_URL, null);
    }

    private void logWeight(JSONObject response) throws JSONException {
        if (response.has("weight")) {
            long now = System.currentTimeMillis();

            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat startFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

            JSONArray weights = response.getJSONArray("weight");

            for (int i = 0; i < weights.length(); i++) {
                JSONObject weight = weights.getJSONObject(i);

                long logId = weight.getLong("logId");

                String where = Fitbit.WEIGHT_LOG_ID + " = ?";
                String[] args = { "" + logId };

                Cursor c = this.mDatabase.query(Fitbit.TABLE_WEIGHT_HISTORY, null, where, args, null, null, null);

                if (c.getCount() == 0) {
                    try {
                        Date observed = startFormat.parse(weight.get("date") + "T" + weight.getString("time"));
                        ContentValues values = new ContentValues();
                        values.put(Fitbit.HISTORY_FETCHED, now);
                        values.put(Fitbit.HISTORY_OBSERVED, observed.getTime());

                        values.put(Fitbit.WEIGHT_WEIGHT, weight.getDouble("weight"));
                        values.put(Fitbit.WEIGHT_BMI, weight.getDouble("bmi"));
                        values.put(Fitbit.WEIGHT_SOURCE, weight.getString("source"));
                        values.put(Fitbit.WEIGHT_LOG_ID, weight.getLong("logId"));

                        this.mDatabase.insert(Fitbit.TABLE_WEIGHT_HISTORY, null, values);

                        Bundle updated = new Bundle();

                        updated.putLong(Fitbit.HISTORY_OBSERVED, values.getAsLong(Fitbit.HISTORY_FETCHED));
                        updated.putLong(Fitbit.HISTORY_FETCHED, values.getAsLong(Fitbit.HISTORY_FETCHED));
                        updated.putLong(Fitbit.WEIGHT_LOG_ID, values.getAsLong(Fitbit.WEIGHT_LOG_ID));
                        updated.putString(Fitbit.WEIGHT_SOURCE, values.getAsString(Fitbit.WEIGHT_SOURCE));
                        updated.putDouble(Fitbit.WEIGHT_WEIGHT, values.getAsDouble(Fitbit.WEIGHT_WEIGHT));

                        updated.putString(Fitbit.FITBIT_TYPE, Fitbit.FITBIT_TYPE_WEIGHT);

                        Generators.getInstance(this.mContext).notifyGeneratorUpdated(Fitbit.GENERATOR_IDENTIFIER, updated);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }

                c.close();
            }
        }
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Fitbit.ENABLED, Fitbit.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (Fitbit.sInstance == null) {
            return false;
        }

        return Fitbit.sInstance.mHandler != null;
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        final Fitbit me = Fitbit.getInstance(context);

        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

        String clientId = me.getProperty(Fitbit.OPTION_OAUTH_CLIENT_ID);
        String clientSecret = me.getProperty(Fitbit.OPTION_OAUTH_CLIENT_SECRET);
        String callbackUrl = me.getProperty(Fitbit.OPTION_OAUTH_CALLBACK_URL);

        if (clientId == null || clientSecret == null || callbackUrl == null) {
            actions.add(new DiagnosticAction(context.getString(R.string.title_dialog_fitbit_auth_misconfigured), context.getString(R.string.message_dialog_fitbit_auth_misconfigured), new Runnable() {
                @Override
                public void run() { }
            }));
        }

        if (me.isAuthenticated() == false && me.mIsMandatory) {
            actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_fitbit_auth_required_title), context.getString(R.string.diagnostic_fitbit_auth_required), new Runnable() {
                @Override
                public void run() {
                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            if (context instanceof Activity) {
                                Activity activity = (Activity) context;

                                me.loginToService(activity);
                            }
                        }
                    };

                    Thread t = new Thread(r);
                    t.start();
                }
            }));
        }

        return actions;
    }

    public void loginToService(Activity activity) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        String clientId = this.getProperty(Fitbit.OPTION_OAUTH_CLIENT_ID);
        String callbackUrl = this.getProperty(Fitbit.OPTION_OAUTH_CALLBACK_URL);

        AuthorizationServiceConfiguration config = new AuthorizationServiceConfiguration(Fitbit.OAUTH_AUTHORIZATION_ENDPOINT, Fitbit.OAUTH_TOKEN_ENDPOINT);

        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(config, clientId, "code", Uri.parse(callbackUrl));

        builder.setPrompt("login");

        HashMap<String, String> params = new HashMap<>();
        params.put("expires_in", "2592000");

        builder.setAdditionalParameters(params);

        ArrayList<String> scopes = new ArrayList<>();
        scopes.add("profile");

        if (prefs.getBoolean(Fitbit.ACTIVITY_ENABLED, Fitbit.ACTIVITY_ENABLED_DEFAULT)) {
            scopes.add("activity");
        }

        if (prefs.getBoolean(Fitbit.HEART_RATE_ENABLED, Fitbit.HEART_RATE_ENABLED_DEFAULT)) {
            scopes.add("heartrate");
        }

        if (prefs.getBoolean(Fitbit.SLEEP_ENABLED, Fitbit.SLEEP_ENABLED_DEFAULT)) {
            scopes.add("sleep");
        }

        if (prefs.getBoolean(Fitbit.WEIGHT_ENABLED, Fitbit.WEIGHT_ENABLED_DEFAULT)) {
            scopes.add("weight");
        }

        builder.setScopes(scopes);

        builder.setCodeVerifier(null);
        builder.setState(null);

        AuthorizationRequest request = builder.build();

        AuthorizationService service = new AuthorizationService(activity);

        Intent handlerIntent = new Intent(activity, Fitbit.OAuthResultHandlerActivity.class);
        handlerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0, handlerIntent, PendingIntent.FLAG_IMMUTABLE);

        service.performAuthorizationRequest(request, pendingIntent);
    }

    public void logout() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.remove(Fitbit.PERSISTED_AUTH);

        e.apply();
    }

    private String getProperty(String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (Fitbit.OPTION_OAUTH_CLIENT_ID.equals(key)) {
            return prefs.getString(Fitbit.OPTION_OAUTH_CLIENT_ID, null);
        } else if (Fitbit.OPTION_OAUTH_CALLBACK_URL.equals(key)) {
            return prefs.getString(Fitbit.OPTION_OAUTH_CALLBACK_URL, null);
        } else if (Fitbit.OPTION_OAUTH_CLIENT_SECRET.equals(key)) {
            return prefs.getString(Fitbit.OPTION_OAUTH_CLIENT_SECRET, null);
        }

        return this.mProperties.get(key);
    }

    private void authorizationSuccessful(final AuthorizationResponse authResponse, final AuthorizationException authException) {
        final Fitbit me = this;

        AuthorizationService service = new AuthorizationService(this.mContext);

        ClientSecretBasic secret = new ClientSecretBasic(me.getProperty(Fitbit.OPTION_OAUTH_CLIENT_SECRET));

        service.performTokenRequest(authResponse.createTokenExchangeRequest(), secret, new AuthorizationService.TokenResponseCallback() {
            @Override public void onTokenRequestCompleted(TokenResponse tokenResponse, AuthorizationException ex) {
                if (tokenResponse != null) {
                    AuthState authState = new AuthState(authResponse, tokenResponse, authException);

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);
                    SharedPreferences.Editor e = prefs.edit();

                    e.putString(Fitbit.PERSISTED_AUTH, authState.jsonSerializeString());
                    e.putString(Fitbit.ACCESS_TOKEN, tokenResponse.accessToken);
                    e.putString(Fitbit.REFRESH_TOKEN, tokenResponse.refreshToken);
                    e.putLong(Fitbit.LAST_REFRESH, System.currentTimeMillis());

                    e.apply();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(me.mContext);
                    builder.setTitle(R.string.title_dialog_fitbit_auth_unsuccessful);
                    builder.setMessage(R.string.message_dialog_fitbit_auth_unsuccessful);

                    builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                        }
                    });

                    builder.create().show();
                }
            }
        });
    }

    @SuppressWarnings("WeakerAccess")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_fitbit_device);
    }

    @SuppressWarnings("unused")
    public static void bindDisclosureViewHolder(final GeneratorViewHolder holder) {
        TextView generatorLabel = holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(Fitbit.getGeneratorTitle(holder.itemView.getContext()));
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(final DataPointViewHolder holder) {
        final Fitbit device = Fitbit.getInstance(holder.itemView.getContext());

        ViewPager pager = holder.itemView.findViewById(R.id.content_pager);

        PagerAdapter adapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return 5;
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object content) {
                return view.getTag().equals(content);
            }

            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object content) {
                int toRemove = -1;

                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);

                    if (this.isViewFromObject(child, content))
                        toRemove = i;
                }

                if (toRemove >= 0)
                    container.removeViewAt(toRemove);
            }

            @NonNull
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                switch (position) {
                    case 0:
                        return Fitbit.bindInformationPage(container, holder, position);
                    case 1:
                        return Fitbit.bindActivityPage(container, holder, position);
                    case 2:
                        return Fitbit.bindHeartRatePage(container, holder, position);
                    case 3:
                        return Fitbit.bindSleepPage(container, holder, position);
                    default:
                        return Fitbit.bindWeightPage(container, holder, position);
                }
            }
        };

        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                device.mPage = position;
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        pager.setAdapter(adapter);

        pager.setCurrentItem(device.mPage);
    }

    private static String bindInformationPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        @SuppressLint("InflateParams")
        LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_fitbit_info_page, null);
        card.setTag("" + position);

        container.addView(card);

        return "" + card.getTag();
    }

    private static String bindActivityPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        @SuppressLint("InflateParams") LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_fitbit_activity_page, null);
        card.setTag("" + position);

        long lastTimestamp = 0;

        double steps = 0;
        double distance = 0;
        double elevation = 0;

        double softActivity = 0;
        double moderateActivity = 0;
        double intenseActivity = 0;

        Fitbit generator = Fitbit.getInstance(card.getContext());

        /*
        Cursor c = generator.mDatabase.query(Fitbit.TABLE_ACTIVITY_MEASURE_HISTORY, null, null, null, null, null, Fitbit.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            lastTimestamp = c.getLong(c.getColumnIndex(Fitbit.HISTORY_OBSERVED));

            steps = c.getDouble(c.getColumnIndex(Fitbit.ACTIVITY_MEASURE_STEPS));
            distance = c.getDouble(c.getColumnIndex(Fitbit.ACTIVITY_MEASURE_DISTANCE));
            elevation = c.getDouble(c.getColumnIndex(Fitbit.ACTIVITY_MEASURE_ELEVATION));

            softActivity = c.getDouble(c.getColumnIndex(Fitbit.ACTIVITY_MEASURE_SOFT_ACTIVITY_DURATION));
            moderateActivity = c.getDouble(c.getColumnIndex(Fitbit.ACTIVITY_MEASURE_MODERATE_ACTIVITY_DURATION));
            intenseActivity = c.getDouble(c.getColumnIndex(Fitbit.ACTIVITY_MEASURE_INTENSE_ACTIVITY_DURATION));
        }

        c.close();
        */

        View cardContent = card.findViewById(R.id.content_activity);
        View cardEmpty = card.findViewById(R.id.card_empty);
        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        if (lastTimestamp > 0) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            dateLabel.setText(Generator.formatTimestamp(context, lastTimestamp));

            PieChart pieChart = card.findViewById(R.id.chart_phone_calls);
            pieChart.getLegend().setEnabled(false);

            pieChart.setEntryLabelColor(android.R.color.transparent);
            pieChart.getDescription().setEnabled(false);
            pieChart.setDrawHoleEnabled(false);

            List<PieEntry> entries = new ArrayList<>();

            if (softActivity > 0) {
                entries.add(new PieEntry((long) softActivity, context.getString(R.string.generator_fitbit_soft_activities_label)));
            }

            if (moderateActivity > 0) {
                entries.add(new PieEntry((long) moderateActivity, context.getString(R.string.generator_fitbit_moderate_activities_label)));
            }

            if (intenseActivity > 0) {
                entries.add(new PieEntry((long) intenseActivity, context.getString(R.string.generator_fitbit_intense_activities_label)));
            }

            if (entries.size() == 0) {
                entries.add(new PieEntry(1L, context.getString(R.string.generator_fitbit_soft_activities_label)));
            }

            PieDataSet set = new PieDataSet(entries, " ");

            int[] colors = {
                    R.color.generator_nokia_health_soft_activities,
                    R.color.generator_nokia_health_moderate_activities,
                    R.color.generator_nokia_health_intense_activities
            };

            set.setColors(colors, context);

            PieData data = new PieData(set);
            data.setValueTextSize(14);
            data.setValueTypeface(Typeface.DEFAULT_BOLD);
            data.setValueTextColor(0xffffffff);

            data.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return "" + ((Float) value).intValue();
                }
            });

            pieChart.setData(data);
            pieChart.invalidate();

            dateLabel.setText(Generator.formatTimestamp(context, lastTimestamp / 1000.0));

            TextView stepsValue = card.findViewById(R.id.field_steps);
            stepsValue.setText(context.getResources().getQuantityString(R.plurals.generator_fitbit_steps_value, (int) steps, (int) steps));

            TextView distanceValue = card.findViewById(R.id.field_distance);
            distanceValue.setText(context.getString(R.string.generator_fitbit_distance_value, (distance / 1000)));

            TextView elevationValue = card.findViewById(R.id.field_elevation);
            elevationValue.setText(context.getString(R.string.generator_fitbit_elevation_value, elevation));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        container.addView(card);

        return "" + card.getTag();
    }

    private static String bindHeartRatePage(ViewGroup container, DataPointViewHolder holder, int position) {
        // TODO: Heart Rate Graph

        final Context context = container.getContext();

        @SuppressLint("InflateParams")
        LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_fitbit_info_page, null);
        card.setTag("" + position);

        container.addView(card);

        return "" + card.getTag();
    }

    private static String bindWeightPage(ViewGroup container, DataPointViewHolder holder, int position) {
        // TODO: WEIGHT

        final Context context = container.getContext();

        @SuppressLint("InflateParams")
        LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_fitbit_info_page, null);
        card.setTag("" + position);

        container.addView(card);

        return "" + card.getTag();
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_fitbit, parent, false);
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        Fitbit me = Fitbit.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        if (me.mDatabase != null) {
            Cursor c = me.mDatabase.query(Fitbit.TABLE_ACTIVITY_HISTORY, null, null, null, null, null, Fitbit.HISTORY_OBSERVED + " DESC");

            if (c.moveToNext()) {
                me.mLatestTimestamp = c.getLong(c.getColumnIndex(Fitbit.HISTORY_OBSERVED));
            }

            c.close();
        } else {
            me.mLatestTimestamp = -1;
        }

        return me.mLatestTimestamp;
    }

    @SuppressWarnings("WeakerAccess")
    public void setProperty(String key, String value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (Fitbit.OPTION_OAUTH_CLIENT_ID.equals(key)) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString(Fitbit.OPTION_OAUTH_CLIENT_ID, value);
            e.apply();
        } else if (Fitbit.OPTION_OAUTH_CALLBACK_URL.equals(key)) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString(Fitbit.OPTION_OAUTH_CALLBACK_URL, value);
            e.apply();
        } else if (Fitbit.OPTION_OAUTH_CLIENT_SECRET.equals(key)) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString(Fitbit.OPTION_OAUTH_CLIENT_SECRET, value);
            e.apply();
        }

        this.mProperties.put(key, value);
    }

    private static String bindSleepPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        @SuppressLint("InflateParams")
        LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_fitbit_sleep_page, null);
        card.setTag("" + position);

        container.addView(card);

        return "" + card.getTag();
    }

    @SuppressWarnings("unused")
    public void enableActivity(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Fitbit.ACTIVITY_ENABLED, enable);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void enableSleep(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Fitbit.SLEEP_ENABLED, enable);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void enableHeartRateActivity(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Fitbit.HEART_RATE_ENABLED, enable);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void enableWeight(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Fitbit.WEIGHT_ENABLED, enable);

        e.apply();
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(Fitbit.DATA_RETENTION_PERIOD, Fitbit.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = Fitbit.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(Fitbit.TABLE_ACTIVITY_HISTORY, where, args);
        this.mDatabase.delete(Fitbit.TABLE_SLEEP_HISTORY, where, args);
        this.mDatabase.delete(Fitbit.TABLE_HEART_RATE_HISTORY, where, args);
        this.mDatabase.delete(Fitbit.TABLE_WEIGHT_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(Fitbit.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    public boolean isAuthenticated() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        return prefs.contains(Fitbit.PERSISTED_AUTH);
    }

    public void setMandatory(boolean isMandatory) {
        this.mIsMandatory = isMandatory;
    }

    public static class OAuthResultHandlerActivity extends Activity {
        protected void onResume() {
            super.onResume();

            final OAuthResultHandlerActivity me = this;

            final Fitbit device = Fitbit.getInstance(this.getApplicationContext());

            AuthorizationResponse resp = AuthorizationResponse.fromIntent(this.getIntent());
            AuthorizationException ex = AuthorizationException.fromIntent(this.getIntent());

            if (resp != null) {
                device.authorizationSuccessful(resp, ex);

                this.finish();
            } else {
                ContextThemeWrapper wrapper = new ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog);

                AlertDialog.Builder builder = new AlertDialog.Builder(wrapper);
                builder.setTitle(R.string.title_dialog_fitbit_auth_unsuccessful);
                builder.setMessage(R.string.message_dialog_fitbit_auth_unsuccessful);

                builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        me.finish();
                    }
                });

                builder.create().show();
            }
        }
    }

    @Override
    public String getIdentifier() {
        return Fitbit.GENERATOR_IDENTIFIER;
    }
}

