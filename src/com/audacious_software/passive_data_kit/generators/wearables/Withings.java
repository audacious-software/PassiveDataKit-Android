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
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
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
import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;
import com.audacious_software.pdk.passivedatakit.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ClientSecretPost;
import net.openid.appauth.TokenResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings({"PointlessBooleanExpression", "SimplifiableIfStatement"})
public class Withings extends Generator {
    public static final String GENERATOR_IDENTIFIER = "pdk-withings";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Withings.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.wearables.Withings.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String DATASTREAM = "datastream";
    private static final String DATASTREAM_ACTIVITY_MEASURES = "activity-measures";
    private static final String DATASTREAM_INTRADAY_ACTIVITY = "intraday-activity";
    private static final String DATASTREAM_BODY = "body";
    private static final String DATASTREAM_SLEEP_MEASURES = "sleep-measures";
    private static final String DATASTREAM_SLEEP_SUMMARY = "sleep-summary";
    private static final String DATASTREAM_SERVER_CREDENTIALS = "server-credentials";

    private static final String TABLE_ACTIVITY_MEASURE_HISTORY = "activity_measure_history";
    private static final String ACTIVITY_MEASURE_HISTORY_DATE_START = "date_start";
    private static final String ACTIVITY_MEASURE_HISTORY_TIMEZONE = "timezone";
    private static final String ACTIVITY_MEASURE_STEPS = "steps";
    private static final String ACTIVITY_MEASURE_DISTANCE = "distance";
    private static final String ACTIVITY_MEASURE_ACTIVE_CALORIES = "active_calories";
    private static final String ACTIVITY_MEASURE_TOTAL_CALORIES = "total_calories";
    private static final String ACTIVITY_MEASURE_ELEVATION = "elevation";
    private static final String ACTIVITY_MEASURE_SOFT_ACTIVITY_DURATION = "soft_activity_duration";
    private static final String ACTIVITY_MEASURE_MODERATE_ACTIVITY_DURATION = "moderate_activity_duration";
    private static final String ACTIVITY_MEASURE_INTENSE_ACTIVITY_DURATION = "intense_activity_duration";

    private static final String TABLE_BODY_MEASURE_HISTORY = "body_measure_history";
    private static final String BODY_MEASURE_STATUS_UNKNOWN = "unknown";
    private static final String BODY_MEASURE_STATUS_USER_DEVICE = "user-device";
    private static final String BODY_MEASURE_STATUS_SHARED_DEVICE = "shared-device";
    private static final String BODY_MEASURE_STATUS_MANUAL_ENTRY = "manual-entry";
    private static final String BODY_MEASURE_STATUS_MANUAL_ENTRY_CREATION = "manual-entry-creation";
    private static final String BODY_MEASURE_STATUS_AUTO_DEVICE = "auto-device";
    private static final String BODY_MEASURE_STATUS_MEASURE_CONFIRMED = "measure-confirmed";

    private static final String BODY_MEASURE_CATEGORY_UNKNOWN = "unknown";
    private static final String BODY_MEASURE_CATEGORY_REAL_MEASUREMENTS = "real-measurements";
    private static final String BODY_MEASURE_CATEGORY_USER_OBJECTIVES = "user-objectives";

    private static final String BODY_MEASURE_TYPE_UNKNOWN = "unknown";
    private static final String BODY_MEASURE_TYPE_WEIGHT = "weight";
    private static final String BODY_MEASURE_TYPE_HEIGHT = "height";
    private static final String BODY_MEASURE_TYPE_FAT_FREE_MASS = "fat-free-mass";
    private static final String BODY_MEASURE_TYPE_FAT_RATIO = "fat-ratio";
    private static final String BODY_MEASURE_TYPE_FAT_MASS_WEIGHT = "fat-mass-weight";
    private static final String BODY_MEASURE_TYPE_DIASTOLIC_BLOOD_PRESSURE = "diastolic-blood-pressure";
    private static final String BODY_MEASURE_TYPE_SYSTOLIC_BLOOD_PRESSURE = "systolic-blood-pressure";
    private static final String BODY_MEASURE_TYPE_HEART_PULSE = "heart-pulse";
    private static final String BODY_MEASURE_TYPE_TEMPERATURE = "temperature";
    private static final String BODY_MEASURE_TYPE_OXYGEN_SATURATION = "oxygen-saturation";
    private static final String BODY_MEASURE_TYPE_BODY_TEMPERATURE = "body-temperature";
    private static final String BODY_MEASURE_TYPE_SKIN_TEMPERATURE = "skin-temperature";
    private static final String BODY_MEASURE_TYPE_MUSCLE_MASS = "muscle-mass";
    private static final String BODY_MEASURE_TYPE_HYDRATION = "hydration";
    private static final String BODY_MEASURE_TYPE_BONE_MASS = "bone-mass";
    private static final String BODY_MEASURE_TYPE_PULSE_WAVE_VELOCITY = "pulse-wave-velocity";

    private static final String BODY_MEASURE_HISTORY_DATE = "measure_date";
    private static final String BODY_MEASURE_HISTORY_STATUS = "measure_status";
    private static final String BODY_MEASURE_HISTORY_CATEGORY = "measure_category";
    private static final String BODY_MEASURE_HISTORY_TYPE = "measure_type";
    private static final String BODY_MEASURE_HISTORY_VALUE = "measure_value";

    private static final String TABLE_INTRADAY_ACTIVITY_HISTORY = "intraday_activity_history";
    private static final String INTRADAY_ACTIVITY_START = "activity_start";
    private static final String INTRADAY_ACTIVITY_DURATION = "activity_duration";
    private static final String INTRADAY_ACTIVITY_CALORIES = "calories";
    private static final String INTRADAY_ACTIVITY_DISTANCE = "distance";
    private static final String INTRADAY_ACTIVITY_ELEVATION_CLIMBED = "elevation_climbed";
    private static final String INTRADAY_ACTIVITY_STEPS = "steps";
    private static final String INTRADAY_ACTIVITY_SWIM_STROKES = "swim_strokes";
    private static final String INTRADAY_ACTIVITY_POOL_LAPS = "pool_laps";

    private static final String TABLE_SLEEP_MEASURE_HISTORY = "sleep_measure_history";
    private static final String SLEEP_MEASURE_MODEL_UNKNOWN = "unknown";
    private static final String SLEEP_MEASURE_MODEL_ACTIVITY_TRACKER = "activity-tracker";
    private static final String SLEEP_MEASURE_MODEL_AURA = "aura";

    private static final String SLEEP_MEASURE_STATE_UNKNOWN = "unknown";
    private static final String SLEEP_MEASURE_STATE_AWAKE = "awake";
    private static final String SLEEP_MEASURE_STATE_LIGHT_SLEEP = "light-sleep";
    private static final String SLEEP_MEASURE_STATE_DEEP_SLEEP = "deep-sleep";
    private static final String SLEEP_MEASURE_STATE_REM_SLEEP = "rem-sleep";

    private static final String SLEEP_MEASURE_START_DATE = "start_date";
    private static final String SLEEP_MEASURE_END_DATE = "end_date";
    private static final String SLEEP_MEASURE_STATE = "state";
    private static final String SLEEP_MEASURE_MEASUREMENT_DEVICE = "measurement_device";

    private static final String TABLE_SLEEP_SUMMARY_HISTORY = "sleep_summary_history";
    private static final String SLEEP_SUMMARY_MODEL_UNKNOWN = "unknown";
    private static final String SLEEP_SUMMARY_MODEL_ACTIVITY_TRACKER = "activity-tracker";
    private static final String SLEEP_SUMMARY_MODEL_AURA = "aura";

    private static final String SLEEP_SUMMARY_START_DATE = "start_date";
    private static final String SLEEP_SUMMARY_END_DATE = "end_date";
    private static final String SLEEP_SUMMARY_TIMEZONE = "timezone";
    private static final String SLEEP_SUMMARY_MEASUREMENT_DEVICE = "measurement_device";
    private static final String SLEEP_SUMMARY_WAKE_DURATION = "wake_duration";
    private static final String SLEEP_SUMMARY_LIGHT_SLEEP_DURATION = "light_sleep_duration";
    private static final String SLEEP_SUMMARY_DEEP_SLEEP_DURATION = "deep_sleep_duration";
    private static final String SLEEP_SUMMARY_TO_SLEEP_DURATION = "to_sleep_duration";
    private static final String SLEEP_SUMMARY_WAKE_COUNT = "wake_count";
    private static final String SLEEP_SUMMARY_REM_SLEEP_DURATION = "rem_sleep_duration";
    private static final String SLEEP_SUMMARY_TO_WAKE_DURATION = "to_wake_duration";

    private static final String HISTORY_OBSERVED = "observed";
    private static final String DATABASE_PATH = "pdk-withings.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String LAST_DATA_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Withings.LAST_DATA_FETCH";

    private static final String DATA_FETCH_INTERVAL = "com.audacious_software.passive_data_kit.generators.wearables.Withings.DATA_FETCH_INTERVAL";
    private static final long DATA_FETCH_INTERVAL_DEFAULT = (15 * 60 * 1000); // (60 * 60 * 1000);

    private static final String ACTIVITY_MEASURES_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Withings.ACTIVITY_MEASURES_ENABLED";
    private static final boolean ACTIVITY_MEASURES_ENABLED_DEFAULT = true;

    private static final String BODY_MEASURES_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Withings.BODY_MEASURES_ENABLED";
    private static final boolean BODY_MEASURES_ENABLED_DEFAULT = true;

    private static final String INTRADAY_ACTIVITY_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Withings.INTRADAY_ACTIVITY_ENABLED";
    private static final boolean INTRADAY_ACTIVITY_ENABLED_DEFAULT = false;

    private static final String SLEEP_MEASURES_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Withings.SLEEP_MEASURES_ENABLED";
    private static final boolean SLEEP_MEASURES_ENABLED_DEFAULT = true;

    private static final String SLEEP_SUMMARY_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.Withings.SLEEP_SUMMARY_ENABLED";
    private static final boolean SLEEP_SUMMARY_ENABLED_DEFAULT = true;

    private static final String SERVER_ONLY = "com.audacious_software.passive_data_kit.generators.wearables.Withings.SERVER_ONLY";
    private static final boolean SERVER_ONLY_DEFAULT = false;

    public static final String OPTION_OAUTH_CALLBACK_URL = "com.audacious_software.passive_data_kit.generators.wearables.Withings.OPTION_CALLBACK_URL";
    public static final String OPTION_OAUTH_CLIENT_ID = "com.audacious_software.passive_data_kit.generators.wearables.Withings.OPTION_OAUTH_CLIENT_ID";
    public static final String OPTION_OAUTH_CLIENT_SECRET = "com.audacious_software.passive_data_kit.generators.wearables.Withings.OPTION_OAUTH_CLIENT_SECRET";

    private static final String OAUTH_ACCESS_TOKEN = "access_token";
    private static final String OAUTH_REFRESH_TOKEN = "refresh_token";
    private static final String OAUTH_CLIENT_ID = "client_id";
    private static final String OAUTH_CLIENT_SECRET = "client_secret";

    private static final String API_SCAN_DAYS = "com.audacious_software.passive_data_kit.generators.wearables.Withings.API_SCAN_DAYS";
    private static final long API_SCAN_DAYS_DEFAULT = 0;

    private static final String API_ACTION_ACTIVITY_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Withings.API_ACTION_ACTIVITY_URL_LAST_FETCH";
    private static final String API_ACTION_BODY_MEASURES_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Withings.API_ACTION_BODY_MEASURES_URL_LAST_FETCH";
    private static final String API_ACTION_INTRADAY_ACTIVITY_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Withings.API_ACTION_INTRADAY_ACTIVITY_URL_LAST_FETCH";
    private static final String API_ACTION_SLEEP_MEASURES_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Withings.API_ACTION_SLEEP_MEASURES_URL_LAST_FETCH";
    private static final String API_ACTION_SLEEP_SUMMARY_URL_LAST_FETCH = "com.audacious_software.passive_data_kit.generators.wearables.Withings.API_ACTION_SLEEP_SUMMARY_URL_LAST_FETCH";

    private static final String API_ACTION_ACTIVITY_URL = "https://wbsapi.withings.net/v2/measure?action=getactivity";
    private static final String API_ACTION_BODY_MEASURES_URL = "https://wbsapi.withings.net/measure?action=getmeas";
    private static final String API_ACTION_INTRADAY_ACTIVITY_URL = "https://wbsapi.withings.net/v2/measure?action=getintradayactivity";
    private static final String API_ACTION_SLEEP_MEASURES_URL = "https://wbsapi.withings.net/v2/sleep?action=get";
    private static final String API_ACTION_SLEEP_SUMMARY_URL = "https://wbsapi.withings.net/v2/sleep?action=getsummary";

    private static final String PERSISTED_AUTH = "com.audacious_software.passive_data_kit.generators.wearables.Withings.PERSISTED_AUTH";

    private static final Uri OAUTH_AUTHORIZATION_ENDPOINT = Uri.parse("https://account.withings.com/oauth2_user/authorize2");
    private static final Uri OAUTH_TOKEN_ENDPOINT = Uri.parse("https://account.withings.com/oauth2/token");

    private static final String PARAM_START_DATE = "PARAM_START_DATE";

    private static Withings sInstance = null;
    private Context mContext = null;
    private SQLiteDatabase mDatabase = null;

    private Handler mHandler = null;
    private Runnable fetchRequest = null;

    private final Map<String, String> mProperties = new HashMap<>();

    private int mPage = 0;

    private long mLatestTimestamp = -1;

    private boolean mIsMandatory = true;
    private boolean mLocalInstallRequired = false;


    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return Withings.GENERATOR_IDENTIFIER;
    }

    public static Withings getInstance(Context context) {
        if (Withings.sInstance == null) {
            Withings.sInstance = new Withings(context.getApplicationContext());
        }

        return Withings.sInstance;
    }

    @SuppressWarnings("WeakerAccess")
    public Withings(Context context) {
        super(context);

        this.mContext = context.getApplicationContext();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.remove(Withings.LAST_DATA_FETCH);
        e.apply();

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Withings.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_withings_create_activity_measure_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_withings_create_body_measure_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_withings_create_intraday_activity_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_withings_create_sleep_measure_history_table));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_withings_create_sleep_summary_history_table));
            default:

        }

        if (version != Withings.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, Withings.DATABASE_VERSION);
        }
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        Withings.getInstance(context).startGenerator();
    }

    @SuppressLint("ObsoleteSdkInt")
    private void startGenerator() {
        final Withings me = this;

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

                long fetchInterval = prefs.getLong(Withings.DATA_FETCH_INTERVAL, Withings.DATA_FETCH_INTERVAL_DEFAULT);

                if (prefs.getBoolean(Withings.SERVER_ONLY, Withings.SERVER_ONLY_DEFAULT)) {
                    if (prefs.contains(Withings.PERSISTED_AUTH)) {
                        long lastFetch = prefs.getLong(Withings.LAST_DATA_FETCH, 0);

                        long now = System.currentTimeMillis();

                        if (now - lastFetch > fetchInterval) {
                            Runnable r = new Runnable() {
                                @Override
                                public void run() {
                                    if (prefs.getBoolean(Withings.ACTIVITY_MEASURES_ENABLED, Withings.ACTIVITY_MEASURES_ENABLED_DEFAULT)) {
                                        me.fetchActivityMeasures();
                                    }

                                    if (prefs.getBoolean(Withings.BODY_MEASURES_ENABLED, Withings.BODY_MEASURES_ENABLED_DEFAULT)) {
                                        me.fetchBodyMeasures();
                                    }

                                    if (prefs.getBoolean(Withings.INTRADAY_ACTIVITY_ENABLED, Withings.INTRADAY_ACTIVITY_ENABLED_DEFAULT)) {
                                        me.fetchIntradayActivities();
                                    }

                                    if (prefs.getBoolean(Withings.SLEEP_MEASURES_ENABLED, Withings.SLEEP_MEASURES_ENABLED_DEFAULT)) {
                                        me.fetchSleepMeasures();
                                    }

                                    if (prefs.getBoolean(Withings.SLEEP_SUMMARY_ENABLED, Withings.SLEEP_SUMMARY_ENABLED_DEFAULT)) {
                                        me.fetchSleepSummary();
                                    }
                                }
                            };

                            Thread t = new Thread(r);
                            t.start();

                            SharedPreferences.Editor e = prefs.edit();
                            e.putLong(Withings.LAST_DATA_FETCH, now);
                            e.apply();
                        }
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

        Generators.getInstance(this.mContext).registerCustomViewClass(Withings.GENERATOR_IDENTIFIER, Withings.class);

        this.flushCachedData();
    }

    public void refresh() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.remove(Withings.LAST_DATA_FETCH);
        e.apply();

        if (this.fetchRequest != null) {
            if (this.mHandler != null) {
                this.mHandler.removeCallbacks(this.fetchRequest);
            }

            this.mHandler.post(this.fetchRequest);
        }
    }

    private String getProperty(String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (Withings.OPTION_OAUTH_CLIENT_ID.equals(key)) {
            return prefs.getString(Withings.OPTION_OAUTH_CLIENT_ID, null);
        } else if (Withings.OPTION_OAUTH_CLIENT_SECRET.equals(key)) {
            return prefs.getString(Withings.OPTION_OAUTH_CLIENT_SECRET, null);
        } else if (Withings.OPTION_OAUTH_CALLBACK_URL.equals(key)) {
            return prefs.getString(Withings.OPTION_OAUTH_CALLBACK_URL, null);
        }

        return this.mProperties.get(key);
    }

    @SuppressLint("SimpleDateFormat")
    private JSONObject queryApi(final String apiUrl, final Map<String, Object> params) {
        final Withings me = this;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        String authJson = prefs.getString(Withings.PERSISTED_AUTH, null);

        if (authJson == null) {
            return null;
        }

        try {
            final AuthState authState = AuthState.jsonDeserialize(authJson);

            AuthorizationService service = new AuthorizationService(this.mContext);

            authState.performActionWithFreshTokens(service, new AuthState.AuthStateAction() {
                @Override
                public void execute(@Nullable String accessToken, @Nullable String idToken, @Nullable AuthorizationException ex) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putString(Withings.PERSISTED_AUTH, authState.jsonSerializeString());
                    e.apply();

                    Date start = new Date();

                    if (params != null && params.containsKey(Withings.PARAM_START_DATE)) {
                        start = (Date) params.get(Withings.PARAM_START_DATE);
                    }

                    final Calendar cal = Calendar.getInstance();
                    cal.setTime(start);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    String startDate = null;
                    String endDate = null;

                    long startTime = 0;
                    long endTime = 0;

                    if (accessToken != null) {
                        final long scanDays = prefs.getLong(Withings.API_SCAN_DAYS, Withings.API_SCAN_DAYS_DEFAULT);

                        if (scanDays > 0) {
                            long lastFetch = 0;

                            if (Withings.API_ACTION_ACTIVITY_URL.equals(apiUrl)) {
                                lastFetch = prefs.getLong(Withings.API_ACTION_ACTIVITY_URL_LAST_FETCH, 0);
                            } else if (Withings.API_ACTION_SLEEP_SUMMARY_URL.equals(apiUrl)) {
                                lastFetch = prefs.getLong(Withings.API_ACTION_SLEEP_SUMMARY_URL_LAST_FETCH, 0);
                            } else if (Withings.API_ACTION_BODY_MEASURES_URL.equals(apiUrl)) {
                                lastFetch = prefs.getLong(Withings.API_ACTION_BODY_MEASURES_URL_LAST_FETCH, 0);
                            } else if (Withings.API_ACTION_INTRADAY_ACTIVITY_URL.equals(apiUrl)) {
                                lastFetch = prefs.getLong(Withings.API_ACTION_INTRADAY_ACTIVITY_URL_LAST_FETCH, 0);
                            } else if (Withings.API_ACTION_SLEEP_MEASURES_URL.equals(apiUrl)) {
                                lastFetch = prefs.getLong(Withings.API_ACTION_SLEEP_MEASURES_URL_LAST_FETCH, 0);
                            }

                            if (lastFetch == 0) {
                                lastFetch = System.currentTimeMillis() - (scanDays * 24 * 60 * 60 * 1000);
                            }

                            while (cal.getTimeInMillis() > lastFetch) {
                                cal.add(Calendar.DATE, -1);
                            }
                        }

                        if (Withings.API_ACTION_ACTIVITY_URL.equals(apiUrl) ||
                                Withings.API_ACTION_SLEEP_SUMMARY_URL.equals(apiUrl)) {
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

                            startDate = format.format(cal.getTime());

                            cal.add(Calendar.DATE, 1);

                            endDate = format.format(cal.getTime());
                        } else if (Withings.API_ACTION_BODY_MEASURES_URL.equals(apiUrl) ||
                                Withings.API_ACTION_INTRADAY_ACTIVITY_URL.equals(apiUrl) ||
                                Withings.API_ACTION_SLEEP_MEASURES_URL.equals(apiUrl)) {

                            startTime = cal.getTimeInMillis() / 1000;

                            cal.add(Calendar.DATE, 1);

                            endTime = cal.getTimeInMillis() / 1000;
                        }

                        Uri apiUri = Uri.parse(apiUrl);

                        Uri.Builder builder = new Uri.Builder();
                        builder.scheme(apiUri.getScheme());
                        builder.authority(apiUri.getAuthority());
                        builder.path(apiUri.getPath());

                        builder.appendQueryParameter("access_token", accessToken);

                        try {
                            String action = apiUri.getQueryParameter("action");

                            builder.appendQueryParameter("action", action);

                            if (endTime != 0) {
                                builder.appendQueryParameter("enddate", "" + endTime);
                            }

                            if (endDate != null) {
                                builder.appendQueryParameter("enddateymd", endDate);
                            }

                            if (startTime != 0) {
                                builder.appendQueryParameter("startdate", "" + startTime);
                            }

                            if (startDate != null) {
                                builder.appendQueryParameter("startdateymd", startDate);
                            }

                            Uri uri = builder.build();

                            OkHttpClient client = new OkHttpClient();

                            Request request = new Request.Builder()
                                    .url(uri.toString())
                                    .build();

                            Response response = client.newCall(request).execute();

                            if (response.isSuccessful()) {
                                if (scanDays > 0) {
                                    long fetchTime = cal.getTimeInMillis();

                                    if (fetchTime > System.currentTimeMillis()) {
                                        fetchTime = 0;
                                    }

                                    if (Withings.API_ACTION_ACTIVITY_URL.equals(apiUrl)) {
                                        e.putLong(Withings.API_ACTION_ACTIVITY_URL_LAST_FETCH, fetchTime);
                                    } else if (Withings.API_ACTION_SLEEP_SUMMARY_URL.equals(apiUrl)) {
                                        e.putLong(Withings.API_ACTION_SLEEP_SUMMARY_URL_LAST_FETCH, fetchTime);
                                    } else if (Withings.API_ACTION_BODY_MEASURES_URL.equals(apiUrl)) {
                                        e.putLong(Withings.API_ACTION_BODY_MEASURES_URL_LAST_FETCH, fetchTime);
                                    } else if (Withings.API_ACTION_INTRADAY_ACTIVITY_URL.equals(apiUrl)) {
                                        e.putLong(Withings.API_ACTION_INTRADAY_ACTIVITY_URL_LAST_FETCH, fetchTime);
                                    } else if (Withings.API_ACTION_SLEEP_MEASURES_URL.equals(apiUrl)) {
                                        e.putLong(Withings.API_ACTION_SLEEP_MEASURES_URL_LAST_FETCH, fetchTime);
                                    }

                                    e.apply();
                                }

                                me.mLatestTimestamp = System.currentTimeMillis();

                                JSONObject apiResponse = new JSONObject(response.body().string());

                                if (Withings.API_ACTION_ACTIVITY_URL.equals(apiUrl)) {
                                    me.logActivityMeasures(apiResponse);
                                } else if (Withings.API_ACTION_BODY_MEASURES_URL.equals(apiUrl)) {
                                    me.logBodyMeasures(apiResponse);
                                } else if (Withings.API_ACTION_INTRADAY_ACTIVITY_URL.equals(apiUrl)) {
                                    me.logIntradayActivities(apiResponse);
                                } else if (Withings.API_ACTION_SLEEP_MEASURES_URL.equals(apiUrl)) {
                                    me.logSleepMeasures(apiResponse);
                                } else if (Withings.API_ACTION_SLEEP_SUMMARY_URL.equals(apiUrl)) {
                                    me.logSleepSummary(apiResponse);
                                }
                            }
                        } catch (OutOfMemoryError ex2) {
                            // Try again next cycle...
                        } catch (JSONException ex2) {
                            ex2.printStackTrace();
                        } catch (IOException ex2) {
                            ex2.printStackTrace();
                        }
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void fetchActivityMeasures() {
        this.queryApi(Withings.API_ACTION_ACTIVITY_URL, null);
    }

    private void fetchActivityMeasures(Date date) {
        HashMap<String, Object> params = new HashMap<>();
        params.put(Withings.PARAM_START_DATE, date);

        this.queryApi(Withings.API_ACTION_ACTIVITY_URL, params);
    }

    private void logActivityMeasures(JSONObject response) {
        try {
            if (response.getInt("status") == 0) {
                JSONObject body = response.getJSONObject("body");
                JSONArray activities = body.getJSONArray("activities");

                for (int i = 0; i < activities.length(); i++) {
                    JSONObject activity = activities.getJSONObject(i);

                    Calendar cal = Calendar.getInstance();

                    String[] tokens = activity.getString("date").split("-");

                    cal.set(Calendar.YEAR, Integer.parseInt(tokens[0]));
                    cal.set(Calendar.MONTH, Integer.parseInt(tokens[1]) - 1);
                    cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(tokens[2]));
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    ContentValues values = new ContentValues();
                    values.put(Withings.HISTORY_OBSERVED, System.currentTimeMillis());

                    values.put(Withings.ACTIVITY_MEASURE_HISTORY_DATE_START, cal.getTimeInMillis());
                    values.put(Withings.ACTIVITY_MEASURE_HISTORY_TIMEZONE, activity.getString("timezone"));
                    values.put(Withings.ACTIVITY_MEASURE_STEPS, activity.getDouble("steps"));
                    values.put(Withings.ACTIVITY_MEASURE_DISTANCE, activity.getDouble("distance"));
                    values.put(Withings.ACTIVITY_MEASURE_ACTIVE_CALORIES, activity.getDouble("calories"));
                    values.put(Withings.ACTIVITY_MEASURE_TOTAL_CALORIES, activity.getDouble("totalcalories"));
                    values.put(Withings.ACTIVITY_MEASURE_ELEVATION, activity.getDouble("elevation"));
                    values.put(Withings.ACTIVITY_MEASURE_SOFT_ACTIVITY_DURATION, activity.getDouble("soft"));
                    values.put(Withings.ACTIVITY_MEASURE_MODERATE_ACTIVITY_DURATION, activity.getDouble("moderate"));
                    values.put(Withings.ACTIVITY_MEASURE_INTENSE_ACTIVITY_DURATION, activity.getDouble("intense"));

                    String where = Withings.ACTIVITY_MEASURE_HISTORY_DATE_START + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_HISTORY_TIMEZONE + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_STEPS + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_DISTANCE + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_ACTIVE_CALORIES + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_TOTAL_CALORIES + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_ELEVATION + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_SOFT_ACTIVITY_DURATION + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_MODERATE_ACTIVITY_DURATION + " = ? AND " +
                            Withings.ACTIVITY_MEASURE_INTENSE_ACTIVITY_DURATION + " = ?";

                    String[] args = {
                            "" + cal.getTimeInMillis(),
                            "" + activity.getString("timezone"),
                            "" + activity.getDouble("steps"),
                            "" + activity.getDouble("distance"),
                            "" + activity.getDouble("calories"),
                            "" + activity.getDouble("totalcalories"),
                            "" + activity.getDouble("elevation"),
                            "" + activity.getDouble("soft"),
                            "" + activity.getDouble("moderate"),
                            "" + activity.getDouble("intense")
                    };

                    Cursor c = this.mDatabase.query(Withings.TABLE_ACTIVITY_MEASURE_HISTORY, null, where, args, null, null, null);

                    if (c.getCount() == 0) {
                        this.mDatabase.insert(Withings.TABLE_ACTIVITY_MEASURE_HISTORY, null, values);

                        Bundle updated = new Bundle();

                        updated.putLong(Withings.HISTORY_OBSERVED, System.currentTimeMillis());
                        updated.putLong(Withings.ACTIVITY_MEASURE_HISTORY_DATE_START, cal.getTimeInMillis());
                        updated.putString(Withings.ACTIVITY_MEASURE_HISTORY_TIMEZONE, activity.getString("timezone"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_STEPS, activity.getDouble("steps"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_DISTANCE, activity.getDouble("distance"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_ACTIVE_CALORIES, activity.getDouble("calories"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_TOTAL_CALORIES, activity.getDouble("totalcalories"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_ELEVATION, activity.getDouble("elevation"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_SOFT_ACTIVITY_DURATION, activity.getDouble("soft"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_MODERATE_ACTIVITY_DURATION, activity.getDouble("moderate"));
                        updated.putDouble(Withings.ACTIVITY_MEASURE_INTENSE_ACTIVITY_DURATION, activity.getDouble("intense"));
                        updated.putString(Withings.DATASTREAM, Withings.DATASTREAM_ACTIVITY_MEASURES);

                        Generators.getInstance(this.mContext).notifyGeneratorUpdated(Withings.GENERATOR_IDENTIFIER, updated);
                    }

                    c.close();
                }
            }
        } catch (JSONException e) {
            AppEvent.getInstance(this.mContext).logThrowable(e);
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

            String where = Withings.ACTIVITY_MEASURE_HISTORY_DATE_START + " >= ? AND " + Withings.ACTIVITY_MEASURE_HISTORY_DATE_START + " < ?";
            String[] args = { "" + dayStart, "" + dayEnd};

            Cursor c = this.mDatabase.query(Withings.TABLE_ACTIVITY_MEASURE_HISTORY, null, where, args, null, null, Withings.ACTIVITY_MEASURE_STEPS + " DESC");

            long daySteps = 0;

            if (c.moveToNext()) {
                daySteps = c.getLong(c.getColumnIndex(Withings.ACTIVITY_MEASURE_STEPS));
            }

            if (daySteps == 0) {
                this.fetchActivityMeasures(new Date(dayStart));
            }

            steps += daySteps;

            c.close();

            start += (24 * 60 * 60 * 1000);
        }

        return steps;
    }

    private void fetchBodyMeasures() {
        this.queryApi(Withings.API_ACTION_BODY_MEASURES_URL, null);
    }

    private void logBodyMeasures(JSONObject response) {
        try {
            if (response.getInt("status") == 0) {
                JSONObject body = response.getJSONObject("body");
                JSONArray measureGroups = body.getJSONArray("measuregrps");

                for (int i = 0; i < measureGroups.length(); i++) {
                    JSONObject measureGroup = measureGroups.getJSONObject(i);

                    long measureDate = measureGroup.getLong("date");
                    long now = System.currentTimeMillis();

                    String status = Withings.BODY_MEASURE_STATUS_UNKNOWN;

                    switch (measureGroup.getInt("attrib")) {
                        case 0:
                            status = Withings.BODY_MEASURE_STATUS_USER_DEVICE;
                            break;
                        case 1:
                            status = Withings.BODY_MEASURE_STATUS_SHARED_DEVICE;
                            break;
                        case 2:
                            status = Withings.BODY_MEASURE_STATUS_MANUAL_ENTRY;
                            break;
                        case 4:
                            status = Withings.BODY_MEASURE_STATUS_MANUAL_ENTRY_CREATION;
                            break;
                        case 5:
                            status = Withings.BODY_MEASURE_STATUS_AUTO_DEVICE;
                            break;
                        case 7:
                            status = Withings.BODY_MEASURE_STATUS_MEASURE_CONFIRMED;
                            break;
                    }

                    String category = Withings.BODY_MEASURE_CATEGORY_UNKNOWN;

                    switch (measureGroup.getInt("category")) {
                        case 1:
                            category = Withings.BODY_MEASURE_CATEGORY_REAL_MEASUREMENTS;
                            break;
                        case 2:
                            category = Withings.BODY_MEASURE_CATEGORY_USER_OBJECTIVES;
                            break;
                    }

                    JSONArray measures = measureGroup.getJSONArray("measures");

                    for (int j = 0; j < measures.length(); j++) {
                        JSONObject measure = measures.optJSONObject(j);

                        ContentValues values = new ContentValues();
                        values.put(Withings.HISTORY_OBSERVED, now);

                        String type = Withings.BODY_MEASURE_TYPE_UNKNOWN;

                        switch (measure.getInt("type")) {
                            case 1:
                                type = Withings.BODY_MEASURE_TYPE_WEIGHT;
                                break;
                            case 4:
                                type = Withings.BODY_MEASURE_TYPE_HEIGHT;
                                break;
                            case 5:
                                type = Withings.BODY_MEASURE_TYPE_FAT_FREE_MASS;
                                break;
                            case 6:
                                type = Withings.BODY_MEASURE_TYPE_FAT_RATIO;
                                break;
                            case 8:
                                type = Withings.BODY_MEASURE_TYPE_FAT_MASS_WEIGHT;
                                break;
                            case 9:
                                type = Withings.BODY_MEASURE_TYPE_DIASTOLIC_BLOOD_PRESSURE;
                                break;
                            case 10:
                                type = Withings.BODY_MEASURE_TYPE_SYSTOLIC_BLOOD_PRESSURE;
                                break;
                            case 11:
                                type = Withings.BODY_MEASURE_TYPE_HEART_PULSE;
                                break;
                            case 12:
                                type = Withings.BODY_MEASURE_TYPE_TEMPERATURE;
                                break;
                            case 54:
                                type = Withings.BODY_MEASURE_TYPE_OXYGEN_SATURATION;
                                break;
                            case 71:
                                type = Withings.BODY_MEASURE_TYPE_BODY_TEMPERATURE;
                                break;
                            case 73:
                                type = Withings.BODY_MEASURE_TYPE_SKIN_TEMPERATURE;
                                break;
                            case 76:
                                type = Withings.BODY_MEASURE_TYPE_MUSCLE_MASS;
                                break;
                            case 77:
                                type = Withings.BODY_MEASURE_TYPE_HYDRATION;
                                break;
                            case 88:
                                type = Withings.BODY_MEASURE_TYPE_BONE_MASS;
                                break;
                            case 91:
                                type = Withings.BODY_MEASURE_TYPE_PULSE_WAVE_VELOCITY;
                                break;
                        }

                        double value = measure.getDouble("value") * Math.pow(10, measure.getDouble("unit"));

                        values.put(Withings.BODY_MEASURE_HISTORY_DATE, measureDate);
                        values.put(Withings.BODY_MEASURE_HISTORY_STATUS, status);
                        values.put(Withings.BODY_MEASURE_HISTORY_CATEGORY, category);
                        values.put(Withings.BODY_MEASURE_HISTORY_TYPE, type);
                        values.put(Withings.BODY_MEASURE_HISTORY_VALUE, value);

                        String where = Withings.BODY_MEASURE_HISTORY_DATE + " = ? AND " +
                                Withings.BODY_MEASURE_HISTORY_STATUS + " = ? AND " +
                                Withings.BODY_MEASURE_HISTORY_CATEGORY + " = ? AND " +
                                Withings.BODY_MEASURE_HISTORY_TYPE + " = ? AND " +
                                Withings.BODY_MEASURE_HISTORY_VALUE + " = ?";

                        String[] args = {
                                "" + measureDate,
                                "" + status,
                                "" + category,
                                "" + type,
                                "" + value
                        };

                        Cursor c = this.mDatabase.query(Withings.TABLE_BODY_MEASURE_HISTORY, null, where, args, null, null, null);

                        if (c.getCount() == 0) {
                            this.mDatabase.insert(Withings.TABLE_BODY_MEASURE_HISTORY, null, values);

                            Bundle updated = new Bundle();

                            updated.putLong(Withings.HISTORY_OBSERVED, System.currentTimeMillis());
                            updated.putLong(Withings.BODY_MEASURE_HISTORY_DATE, measureDate);
                            updated.putString(Withings.BODY_MEASURE_HISTORY_STATUS, status);
                            updated.putString(Withings.BODY_MEASURE_HISTORY_CATEGORY, category);
                            updated.putString(Withings.BODY_MEASURE_HISTORY_TYPE, type);
                            updated.putDouble(Withings.BODY_MEASURE_HISTORY_VALUE, value);
                            updated.putString(Withings.DATASTREAM, Withings.DATASTREAM_BODY);

                            Generators.getInstance(this.mContext).notifyGeneratorUpdated(Withings.GENERATOR_IDENTIFIER, updated);
                        }

                        c.close();
                    }
                }
            }
        } catch (JSONException e) {
            AppEvent.getInstance(this.mContext).logThrowable(e);
        }
    }

    private void fetchIntradayActivities() {
        this.queryApi(Withings.API_ACTION_INTRADAY_ACTIVITY_URL, null);
    }

    private void logIntradayActivities(JSONObject response) {
        try {
            long now = System.currentTimeMillis();

            if (response.getInt("status") == 0) {
                JSONObject body = response.getJSONObject("body");

                try {
                    JSONObject series = body.getJSONObject("series");

                    Iterator<String> keys = series.keys();

                    while (keys.hasNext()) {
                        String key = keys.next();

                        long timestamp = Long.parseLong(key);

                        String where = Withings.INTRADAY_ACTIVITY_START + " = ?";
                        String[] args = { "" + timestamp };

                        Cursor c = this.mDatabase.query(Withings.TABLE_INTRADAY_ACTIVITY_HISTORY, null, where, args, null, null, Withings.HISTORY_OBSERVED + " DESC");

                        if (c.moveToNext() == false) {
                            JSONObject item = series.getJSONObject(key);

                            ContentValues values = new ContentValues();
                            values.put(Withings.HISTORY_OBSERVED, now);
                            values.put(Withings.INTRADAY_ACTIVITY_START, timestamp);
                            values.put(Withings.INTRADAY_ACTIVITY_DURATION, item.getLong("duration"));

                            Bundle updated = new Bundle();
                            updated.putLong(Withings.HISTORY_OBSERVED, System.currentTimeMillis());
                            updated.putLong(Withings.INTRADAY_ACTIVITY_START, timestamp);
                            updated.putLong(Withings.INTRADAY_ACTIVITY_DURATION, item.getLong("duration"));

                            if (item.has("steps")) {
                                values.put(Withings.INTRADAY_ACTIVITY_STEPS, item.getLong("steps"));
                                updated.putLong(Withings.INTRADAY_ACTIVITY_STEPS, item.getLong("steps"));
                            }

                            if (item.has("elevation")) {
                                values.put(Withings.INTRADAY_ACTIVITY_ELEVATION_CLIMBED, item.getLong("elevation"));
                                updated.putLong(Withings.INTRADAY_ACTIVITY_ELEVATION_CLIMBED, item.getLong("elevation"));
                            }

                            if (item.has("distance")) {
                                values.put(Withings.INTRADAY_ACTIVITY_DISTANCE, item.getLong("distance"));
                                updated.putLong(Withings.INTRADAY_ACTIVITY_DISTANCE, item.getLong("distance"));
                            }

                            if (item.has("calories")) {
                                values.put(Withings.INTRADAY_ACTIVITY_CALORIES, item.getLong("calories"));
                                updated.putLong(Withings.INTRADAY_ACTIVITY_CALORIES, item.getLong("calories"));
                            }

                            if (item.has("stroke")) {
                                values.put(Withings.INTRADAY_ACTIVITY_SWIM_STROKES, item.getLong("stroke"));
                                updated.putLong(Withings.INTRADAY_ACTIVITY_SWIM_STROKES, item.getLong("stroke"));
                            }

                            if (item.has("pool_lap")) {
                                values.put(Withings.INTRADAY_ACTIVITY_POOL_LAPS, item.getLong("pool_lap"));
                                updated.putLong(Withings.INTRADAY_ACTIVITY_POOL_LAPS, item.getLong("pool_lap"));
                            }

                            this.mDatabase.insert(Withings.TABLE_INTRADAY_ACTIVITY_HISTORY, null, values);

                            updated.putString(Withings.DATASTREAM, Withings.DATASTREAM_INTRADAY_ACTIVITY);

                            Generators.getInstance(this.mContext).notifyGeneratorUpdated(Withings.GENERATOR_IDENTIFIER, updated);
                        }

                        c.close();
                    }
                } catch (JSONException e) {
                    // JSON type mismatch when day's data is empty. Do nothing...
                }
            }
        } catch (JSONException e) {
            AppEvent.getInstance(this.mContext).logThrowable(e);
        }
    }

    private void fetchSleepMeasures() {
        this.queryApi(Withings.API_ACTION_SLEEP_MEASURES_URL, null);
    }

    private void logSleepMeasures(JSONObject response) {
        try {
            if (response.getInt("status") == 0) {
                JSONObject body = response.getJSONObject("body");

                String model = Withings.SLEEP_MEASURE_MODEL_UNKNOWN;

                switch(body.getInt("model")) {
                    case 16:
                        model = Withings.SLEEP_MEASURE_MODEL_ACTIVITY_TRACKER;
                        break;
                    case 32:
                        model = Withings.SLEEP_MEASURE_MODEL_AURA;
                        break;
                }

                JSONArray series = body.getJSONArray("series");

                for (int i = 0; i < series.length(); i++) {
                    JSONObject item = series.getJSONObject(i);

                    long now = System.currentTimeMillis();

                    String state = Withings.SLEEP_MEASURE_STATE_UNKNOWN;

                    switch (item.getInt("state")) {
                        case 0:
                            state = Withings.SLEEP_MEASURE_STATE_AWAKE;
                            break;
                        case 1:
                            state = Withings.SLEEP_MEASURE_STATE_LIGHT_SLEEP;
                            break;
                        case 2:
                            state = Withings.SLEEP_MEASURE_STATE_DEEP_SLEEP;
                            break;
                        case 3:
                            state = Withings.SLEEP_MEASURE_STATE_REM_SLEEP;
                            break;
                    }

                    ContentValues values = new ContentValues();
                    values.put(Withings.HISTORY_OBSERVED, now);
                    values.put(Withings.SLEEP_MEASURE_START_DATE, item.getLong("startdate"));
                    values.put(Withings.SLEEP_MEASURE_END_DATE, item.getLong("enddate"));
                    values.put(Withings.SLEEP_MEASURE_STATE, state);
                    values.put(Withings.SLEEP_MEASURE_MEASUREMENT_DEVICE, model);

                    String where = Withings.SLEEP_MEASURE_START_DATE + " = ? AND " +
                            Withings.SLEEP_MEASURE_END_DATE + " = ? AND " +
                            Withings.SLEEP_MEASURE_STATE + " = ? AND " +
                            Withings.SLEEP_MEASURE_MEASUREMENT_DEVICE + " = ?";

                    String[] args = {
                            "" + item.getLong("startdate"),
                            "" + item.getLong("enddate"),
                            "" + state,
                            "" + model
                    };

                    Cursor c = this.mDatabase.query(Withings.TABLE_SLEEP_MEASURE_HISTORY, null, where, args, null, null, null);

                    if (c.getCount() == 0) {
                        this.mDatabase.insert(Withings.TABLE_SLEEP_MEASURE_HISTORY, null, values);

                        Bundle updated = new Bundle();
                        updated.putLong(Withings.HISTORY_OBSERVED, System.currentTimeMillis());
                        updated.putLong(Withings.SLEEP_MEASURE_START_DATE, item.getLong("startdate"));
                        updated.putLong(Withings.SLEEP_MEASURE_END_DATE, item.getLong("enddate"));
                        updated.putString(Withings.SLEEP_MEASURE_STATE, state);
                        updated.putString(Withings.SLEEP_MEASURE_MEASUREMENT_DEVICE, model);

                        updated.putString(Withings.DATASTREAM, Withings.DATASTREAM_SLEEP_MEASURES);

                        Generators.getInstance(this.mContext).notifyGeneratorUpdated(Withings.GENERATOR_IDENTIFIER, updated);
                    }

                    c.close();
                }
            }
        } catch (JSONException e) {
            AppEvent.getInstance(this.mContext).logThrowable(e);
        }
    }

    private void fetchSleepSummary() {
        this.queryApi(Withings.API_ACTION_SLEEP_SUMMARY_URL, null);
    }

    private void logSleepSummary(JSONObject response) {
        try {
            if (response.getInt("status") == 0) {
                JSONObject body = response.getJSONObject("body");

                JSONArray series = body.getJSONArray("series");

                long now = System.currentTimeMillis();

                for (int i = 0; i < series.length(); i++) {
                    JSONObject item = series.getJSONObject(i);

                    String timezone = body.getString("timezone");

                    String model = Withings.SLEEP_SUMMARY_MODEL_UNKNOWN;

                    switch(body.getInt("model")) {
                        case 16:
                            model = Withings.SLEEP_SUMMARY_MODEL_ACTIVITY_TRACKER;
                            break;
                        case 32:
                            model = Withings.SLEEP_SUMMARY_MODEL_AURA;
                            break;
                    }

                    JSONObject data = item.getJSONObject("data");

                    ContentValues values = new ContentValues();
                    values.put(Withings.HISTORY_OBSERVED, now);
                    values.put(Withings.SLEEP_SUMMARY_START_DATE, item.getLong("startdate"));
                    values.put(Withings.SLEEP_SUMMARY_END_DATE, item.getLong("enddate"));
                    values.put(Withings.SLEEP_SUMMARY_TIMEZONE, timezone);
                    values.put(Withings.SLEEP_SUMMARY_MEASUREMENT_DEVICE, model);
                    values.put(Withings.SLEEP_SUMMARY_WAKE_DURATION, data.getDouble("wakeupduration"));
                    values.put(Withings.SLEEP_SUMMARY_LIGHT_SLEEP_DURATION, data.getDouble("lightsleepduration"));
                    values.put(Withings.SLEEP_SUMMARY_DEEP_SLEEP_DURATION, data.getDouble("deepsleepduration"));
                    values.put(Withings.SLEEP_SUMMARY_TO_SLEEP_DURATION, data.getDouble("durationtosleep"));
                    values.put(Withings.SLEEP_SUMMARY_WAKE_COUNT, data.getDouble("wakeupcount"));

                    if (data.has("remsleepduration")) {
                        values.put(Withings.SLEEP_SUMMARY_REM_SLEEP_DURATION, data.getDouble("remsleepduration"));
                    }

                    if (data.has("durationtowakeup")) {
                        values.put(Withings.SLEEP_SUMMARY_TO_WAKE_DURATION, data.getDouble("durationtowakeup"));
                    }

                    String where = Withings.SLEEP_SUMMARY_START_DATE + " = ? AND " +
                            Withings.SLEEP_SUMMARY_END_DATE + " = ? AND " +
                            Withings.SLEEP_SUMMARY_TIMEZONE + " = ? AND " +
                            Withings.SLEEP_SUMMARY_MEASUREMENT_DEVICE + " = ? AND " +
                            Withings.SLEEP_SUMMARY_WAKE_DURATION + " = ? AND " +
                            Withings.SLEEP_SUMMARY_LIGHT_SLEEP_DURATION + " = ? AND " +
                            Withings.SLEEP_SUMMARY_DEEP_SLEEP_DURATION + " = ? AND " +
                            Withings.SLEEP_SUMMARY_TO_SLEEP_DURATION + " = ? AND " +
                            Withings.SLEEP_SUMMARY_WAKE_COUNT + " = ?";

                    String[] args = {
                            "" + item.getLong("startdate"),
                            "" + item.getLong("enddate"),
                            "" + timezone,
                            "" + model,
                            "" + data.getDouble("wakeupduration"),
                            "" + data.getDouble("lightsleepduration"),
                            "" + data.getDouble("deepsleepduration"),
                            "" + data.getDouble("durationtosleep"),
                            "" + data.getDouble("wakeupcount")
                    };

                    Cursor c = this.mDatabase.query(Withings.TABLE_SLEEP_MEASURE_HISTORY, null, where, args, null, null, null);

                    if (c.getCount() == 0) {
                        this.mDatabase.insert(Withings.TABLE_SLEEP_MEASURE_HISTORY, null, values);

                        Bundle updated = new Bundle();
                        updated.putLong(Withings.HISTORY_OBSERVED, now);
                        updated.putLong(Withings.SLEEP_SUMMARY_START_DATE, item.getLong("startdate"));
                        updated.putLong(Withings.SLEEP_SUMMARY_END_DATE, item.getLong("enddate"));
                        updated.putString(Withings.SLEEP_SUMMARY_TIMEZONE, timezone);
                        updated.putString(Withings.SLEEP_SUMMARY_MEASUREMENT_DEVICE, model);
                        updated.putDouble(Withings.SLEEP_SUMMARY_WAKE_DURATION, data.getDouble("wakeupduration"));
                        updated.putDouble(Withings.SLEEP_SUMMARY_LIGHT_SLEEP_DURATION, data.getDouble("lightsleepduration"));
                        updated.putDouble(Withings.SLEEP_SUMMARY_DEEP_SLEEP_DURATION, data.getDouble("deepsleepduration"));
                        updated.putDouble(Withings.SLEEP_SUMMARY_TO_SLEEP_DURATION, data.getDouble("durationtosleep"));
                        updated.putDouble(Withings.SLEEP_SUMMARY_WAKE_COUNT, data.getDouble("wakeupcount"));

                        if (data.has("remsleepduration")) {
                            updated.putDouble(Withings.SLEEP_SUMMARY_REM_SLEEP_DURATION, data.getDouble("remsleepduration"));
                        }

                        if (data.has("durationtowakeup")) {
                            updated.putDouble(Withings.SLEEP_SUMMARY_TO_WAKE_DURATION, data.getDouble("durationtowakeup"));
                        }

                        updated.putString(Withings.DATASTREAM, Withings.DATASTREAM_SLEEP_SUMMARY);

                        Generators.getInstance(this.mContext).notifyGeneratorUpdated(Withings.GENERATOR_IDENTIFIER, updated);
                    }

                    c.close();
                }
            }
        } catch (JSONException e) {
            AppEvent.getInstance(this.mContext).logThrowable(e);
        }
    }

    @SuppressWarnings("unused")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Withings.ENABLED, Withings.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"unused"})
    public static boolean isRunning(Context context) {
        if (Withings.sInstance == null) {
            return false;
        }

        return Withings.sInstance.mHandler != null;
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        final Withings me = Withings.getInstance(context);

        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);

        String clientId = me.getProperty(Withings.OPTION_OAUTH_CLIENT_ID);
        String clientSecret = me.getProperty(Withings.OPTION_OAUTH_CLIENT_SECRET);
        String callbackUrl = me.getProperty(Withings.OPTION_OAUTH_CALLBACK_URL);

        if (clientId == null || clientSecret == null || callbackUrl == null) {
            actions.add(new DiagnosticAction(context.getString(R.string.title_dialog_withings_auth_misconfigured), context.getString(R.string.message_dialog_withings_auth_misconfigured), new Runnable() {
                @Override
                public void run() { }
            }));
        }

        if (me.isAuthenticated() == false && me.mIsMandatory) {
            actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_withings_auth_required_title), context.getString(R.string.diagnostic_withings_auth_required), new Runnable() {
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

        if (me.mLocalInstallRequired) {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.withings.wiscale2");

            if (launchIntent == null) {
                actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_withings_local_install_required_title), context.getString(R.string.diagnostic_withings_local_install_required), new Runnable() {
                    @Override
                    public void run() {
                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Intent appStoreIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.withings.wiscale2"));
                                    appStoreIntent.setPackage("com.android.vending");

                                    context.startActivity(appStoreIntent);
                                } catch (android.content.ActivityNotFoundException exception) {
                                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.withings.wiscale2")));
                                }
                            }
                        };

                        Thread t = new Thread(r);
                        t.start();
                    }
                }));
            }
        }

        return actions;
    }

    public void loginToService(Activity activity) {
        String clientId = this.getProperty(Withings.OPTION_OAUTH_CLIENT_ID);
        String callbackUrl = this.getProperty(Withings.OPTION_OAUTH_CALLBACK_URL);

        AuthorizationServiceConfiguration config = new AuthorizationServiceConfiguration(Withings.OAUTH_AUTHORIZATION_ENDPOINT, Withings.OAUTH_TOKEN_ENDPOINT);

        AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(config, clientId, "code", Uri.parse(callbackUrl));

        builder.setScope("user.info,user.activity,user.metrics");

        builder.setCodeVerifier(null);

        AuthorizationRequest request = builder.build();

        AuthorizationService service = new AuthorizationService(activity);

        Intent handlerIntent = new Intent(activity, Withings.OAuthResultHandlerActivity.class);
        handlerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(activity, 0, handlerIntent, 0);

        service.performAuthorizationRequest(request, pendingIntent);
    }

    private void authorizationSuccessful(final AuthorizationResponse authResponse, final AuthorizationException authException) {
        final Withings me = this;

        AuthorizationService service = new AuthorizationService(this.mContext);

        ClientSecretPost secret = new ClientSecretPost(me.getProperty(Withings.OPTION_OAUTH_CLIENT_SECRET));

        service.performTokenRequest(authResponse.createTokenExchangeRequest(), secret, new AuthorizationService.TokenResponseCallback() {
            @Override public void onTokenRequestCompleted(TokenResponse tokenResponse, AuthorizationException ex) {
                if (tokenResponse != null) {
                    AuthState authState = new AuthState(authResponse, tokenResponse, authException);

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(me.mContext);
                    SharedPreferences.Editor e = prefs.edit();

                    e.putString(Withings.PERSISTED_AUTH, authState.jsonSerializeString());

                    if (prefs.getBoolean(Withings.SERVER_ONLY, Withings.SERVER_ONLY_DEFAULT)) {
                        Bundle updated = new Bundle();
                        updated.putLong(Withings.HISTORY_OBSERVED, System.currentTimeMillis());

                        updated.putString(Withings.DATASTREAM, Withings.DATASTREAM_SERVER_CREDENTIALS);
                        updated.putString(Withings.OAUTH_ACCESS_TOKEN, tokenResponse.accessToken);
                        updated.putString(Withings.OAUTH_REFRESH_TOKEN, tokenResponse.refreshToken);
                        updated.putString(Withings.OAUTH_CLIENT_ID, me.getProperty(Withings.OPTION_OAUTH_CLIENT_ID));
                        updated.putString(Withings.OAUTH_CLIENT_SECRET, me.getProperty(Withings.OPTION_OAUTH_CLIENT_SECRET));

                        Generators.getInstance(me.mContext).notifyGeneratorUpdated(Withings.GENERATOR_IDENTIFIER, updated);
                    }

                    e.apply();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(me.mContext);
                    builder.setTitle(R.string.title_dialog_withings_auth_unsuccessful);
                    builder.setMessage(R.string.message_dialog_withings_auth_unsuccessful);

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
        return context.getString(R.string.generator_withings_device);
    }

    @SuppressWarnings("unused")
    public static void bindDisclosureViewHolder(final GeneratorViewHolder holder) {
        TextView generatorLabel = holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(Withings.getGeneratorTitle(holder.itemView.getContext()));
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(final DataPointViewHolder holder) {
        final Withings device = Withings.getInstance(holder.itemView.getContext());

        ViewPager pager = holder.itemView.findViewById(R.id.content_pager);

        PagerAdapter adapter = new PagerAdapter() {
            @Override
            public int getCount() {
                return 2;
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
                        return Withings.bindActivityPage(container, holder, position);
                    case 1:
                        return Withings.bindIntradayPage(container, holder, position);
                    case 2:
                        return Withings.bindBodyPage(container, holder, position);
                    case 3:
                        return Withings.bindSleepPage(container, holder, position);
                    case 4:
                        return Withings.bindSleepSummaryPage(container, holder, position);
                    default:
                        return Withings.bindInformationPage(container, holder, position);
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

        @SuppressLint("InflateParams") LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_withings_info_page, null);
        card.setTag("" + position);

        container.addView(card);

        return "" + card.getTag();
    }

    private static String bindActivityPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        @SuppressLint("InflateParams") LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_withings_activity_page, null);
        card.setTag("" + position);

        long lastTimestamp = 0;

        double steps = 0;
        double distance = 0;
        double elevation = 0;

        double softActivity = 0;
        double moderateActivity = 0;
        double intenseActivity = 0;

        Withings generator = Withings.getInstance(card.getContext());

        Cursor c = generator.mDatabase.query(Withings.TABLE_ACTIVITY_MEASURE_HISTORY, null, null, null, null, null, Withings.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            lastTimestamp = c.getLong(c.getColumnIndex(Withings.HISTORY_OBSERVED));

            steps = c.getDouble(c.getColumnIndex(Withings.ACTIVITY_MEASURE_STEPS));
            distance = c.getDouble(c.getColumnIndex(Withings.ACTIVITY_MEASURE_DISTANCE));
            elevation = c.getDouble(c.getColumnIndex(Withings.ACTIVITY_MEASURE_ELEVATION));

            softActivity = c.getDouble(c.getColumnIndex(Withings.ACTIVITY_MEASURE_SOFT_ACTIVITY_DURATION));
            moderateActivity = c.getDouble(c.getColumnIndex(Withings.ACTIVITY_MEASURE_MODERATE_ACTIVITY_DURATION));
            intenseActivity = c.getDouble(c.getColumnIndex(Withings.ACTIVITY_MEASURE_INTENSE_ACTIVITY_DURATION));
        }

        c.close();

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
                entries.add(new PieEntry((long) softActivity, context.getString(R.string.generator_withings_soft_activities_label)));
            }

            if (moderateActivity > 0) {
                entries.add(new PieEntry((long) moderateActivity, context.getString(R.string.generator_withings_moderate_activities_label)));
            }

            if (intenseActivity > 0) {
                entries.add(new PieEntry((long) intenseActivity, context.getString(R.string.generator_withings_intense_activities_label)));
            }

            if (entries.size() == 0) {
                entries.add(new PieEntry(1L, context.getString(R.string.generator_withings_soft_activities_label)));
            }

            PieDataSet set = new PieDataSet(entries, " ");

            int[] colors = {
                    R.color.generator_withings_soft_activities,
                    R.color.generator_withings_moderate_activities,
                    R.color.generator_withings_intense_activities
            };

            set.setColors(colors, context);

            PieData data = new PieData(set);
            data.setValueTextSize(14);
            data.setValueTypeface(Typeface.DEFAULT_BOLD);
            data.setValueTextColor(0xffffffff);

            data.setValueFormatter(new IValueFormatter() {
                @Override
                public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                    return "" + ((Float) value).intValue();
                }
            });

            pieChart.setData(data);
            pieChart.invalidate();

            dateLabel.setText(Generator.formatTimestamp(context, lastTimestamp / 1000));

            TextView stepsValue = card.findViewById(R.id.field_steps);
            stepsValue.setText(context.getResources().getQuantityString(R.plurals.generator_withings_steps_value, (int) steps, (int) steps));

            TextView distanceValue = card.findViewById(R.id.field_distance);
            distanceValue.setText(context.getString(R.string.generator_withings_distance_value, (distance / 1000)));

            TextView elevationValue = card.findViewById(R.id.field_elevation);
            elevationValue.setText(context.getString(R.string.generator_withings_elevation_value, elevation));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        container.addView(card);

        return "" + card.getTag();
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_withings_device, parent, false);
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        Withings me = Withings.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        if (me.mDatabase != null) {
            Cursor c = me.mDatabase.query(Withings.TABLE_ACTIVITY_MEASURE_HISTORY, null, null, null, null, null, Withings.HISTORY_OBSERVED + " DESC");

            if (c.moveToNext()) {
                me.mLatestTimestamp = c.getLong(c.getColumnIndex(Withings.HISTORY_OBSERVED));
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

        if (Withings.OPTION_OAUTH_CALLBACK_URL.equals(key)) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString(Withings.OPTION_OAUTH_CALLBACK_URL, value);
            e.apply();
        } else if (Withings.OPTION_OAUTH_CLIENT_ID.equals(key)) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString(Withings.OPTION_OAUTH_CLIENT_ID, value);
            e.apply();
        } else if (Withings.OPTION_OAUTH_CLIENT_SECRET.equals(key)) {
            SharedPreferences.Editor e = prefs.edit();
            e.putString(Withings.OPTION_OAUTH_CLIENT_SECRET, value);
            e.apply();
        }

        this.mProperties.put(key, value);
    }

    private static String bindIntradayPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        long now = System.currentTimeMillis();

        Withings device = Withings.getInstance(context);

        @SuppressLint("InflateParams") LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_withings_intraday_page, null);
        card.setTag("" + position);

        LineChart stepsChart = card.findViewById(R.id.chart_steps);
        LineChart distanceChart = card.findViewById(R.id.chart_distance);
        LineChart elevationChart = card.findViewById(R.id.chart_elevation);
        LineChart caloriesChart = card.findViewById(R.id.chart_calories);

        ArrayList<Entry> steps = new ArrayList<>();
        ArrayList<Entry> distance = new ArrayList<>();
        ArrayList<Entry> elevation = new ArrayList<>();
        ArrayList<Entry> calories = new ArrayList<>();

        float stepSum = 0;
        float distanceSum = 0;
        float elevationSum = 0;
        float caloriesSum = 0;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long start = cal.getTimeInMillis() / 1000;

        cal.add(Calendar.DATE, 1);

        long end = cal.getTimeInMillis() / 1000;

        String where = Withings.INTRADAY_ACTIVITY_START + " > ?";
        String[] args = { "" + start };

        Cursor c = device.mDatabase.query(Withings.TABLE_INTRADAY_ACTIVITY_HISTORY, null, where, args, null, null, Withings.INTRADAY_ACTIVITY_START);

        steps.add(new Entry(0, 0));
        distance.add(new Entry(0, 0));
        elevation.add(new Entry(0, 0));
        calories.add(new Entry(0, 0));

        while (c.moveToNext()) {
            long when = c.getLong(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_START));

            if (c.isNull(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_STEPS)) == false) {
                float value = c.getFloat(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_STEPS));

                stepSum += value;

                steps.add(new Entry(when - start, stepSum));
            }

            if (c.isNull(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_DISTANCE)) == false) {
                float value = c.getFloat(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_DISTANCE));

                distanceSum += value;

                distance.add(new Entry(when - start, distanceSum));
            }

            if (c.isNull(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_ELEVATION_CLIMBED)) == false) {
                float value = c.getFloat(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_ELEVATION_CLIMBED));

                elevationSum += value;

                elevation.add(new Entry(when - start, elevationSum));
            }

            if (c.isNull(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_CALORIES)) == false) {
                float value = c.getFloat(c.getColumnIndex(Withings.INTRADAY_ACTIVITY_CALORIES));

                caloriesSum += value;

                calories.add(new Entry(when - start, caloriesSum));
            }
        }

        steps.add(new Entry((now / 1000) - start, stepSum));
        distance.add(new Entry((now / 1000) - start, distanceSum));
        elevation.add(new Entry((now / 1000) - start, elevationSum));
        calories.add(new Entry((now / 1000) - start, caloriesSum));

        Withings.populateIntradayChart(context, stepsChart, steps, 0, end - start);
        Withings.populateIntradayChart(context, distanceChart, distance, 0, end - start);
        Withings.populateIntradayChart(context, elevationChart, elevation, 0, end - start);
        Withings.populateIntradayChart(context, caloriesChart, calories, 0, end - start);

        c.close();

        container.addView(card);

        return "" + card.getTag();
    }

    @SuppressWarnings("SameParameterValue")
    private static void populateIntradayChart(Context context, LineChart chart, ArrayList<Entry> values, long start, long end) {
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getDescription().setEnabled(false);

        LineData data = new LineData();

        LineDataSet set = new LineDataSet(values, "");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(1.0f);
        set.setDrawCircles(false);
        set.setFillAlpha(128);
        set.setDrawFilled(true);
        set.setDrawValues(false);
        set.setColor(ContextCompat.getColor(context, R.color.generator_battery_plot));
        set.setFillColor(ContextCompat.getColor(context, R.color.generator_battery_plot));

        data.addDataSet(set);

        float minimum = (float) (0 - (values.get(values.size() - 1).getY() * 0.05));
        float maximum = (float) (values.get(values.size() - 1).getY() * 1.25);

        if (minimum == 0) {
            minimum = -0.05f;
            maximum = 0.95f;
        }

        chart.getAxisLeft().setAxisMinimum(minimum);
        chart.getAxisLeft().setAxisMaximum(maximum);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisLeft().setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisLeft().setDrawLabels(false);
        chart.getAxisLeft().setTextColor(ContextCompat.getColor(context, android.R.color.white));

        chart.getXAxis().setAxisMinimum(start);
        chart.getXAxis().setAxisMaximum(end);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setDrawLabels(false);
        chart.getXAxis().setDrawAxisLine(false);

        chart.setViewPortOffsets(0,0,8,0);
        chart.setHighlightPerDragEnabled(false);
        chart.setHighlightPerTapEnabled(false);
        chart.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black));
        chart.setPinchZoom(false);

        ArrayList<Entry> lastValue = new ArrayList<>();
        lastValue.add(values.get(values.size() - 1));

        LineDataSet lastItem = new LineDataSet(lastValue, "");
        lastItem.setAxisDependency(YAxis.AxisDependency.LEFT);
        lastItem.setLineWidth(1.0f);
        lastItem.setCircleRadius(3.0f);
        lastItem.setCircleHoleRadius(2.0f);
        lastItem.setDrawCircles(true);
        lastItem.setValueTextSize(10f);
        lastItem.setDrawValues(true);
        lastItem.setCircleColor(ContextCompat.getColor(context, R.color.generator_battery_plot));
        lastItem.setCircleColorHole(ContextCompat.getColor(context, android.R.color.black));
        lastItem.setValueTextColor(ContextCompat.getColor(context, android.R.color.white));

        data.addDataSet(lastItem);

        chart.setData(data);
    }

    @SuppressLint("SetTextI18n")
    private static String bindBodyPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        Withings device = Withings.getInstance(holder.itemView.getContext());

        @SuppressLint("InflateParams") LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_withings_body_page, null);
        card.setTag("" + position);

        int[] labels = {
                R.id.label_body_one,
                R.id.label_body_two,
                R.id.label_body_three,
                R.id.label_body_four,
                R.id.label_body_five,
                R.id.label_body_six,
                R.id.label_body_seven,
                R.id.label_body_eight
        };

        int[] values = {
                R.id.value_body_one,
                R.id.value_body_two,
                R.id.value_body_three,
                R.id.value_body_four,
                R.id.value_body_five,
                R.id.value_body_six,
                R.id.value_body_seven,
                R.id.value_body_eight
        };

        HashMap<String, Double> bodyValues = new HashMap<>();
        ArrayList<String> keys = new ArrayList<>();

        Cursor c = device.mDatabase.query(Withings.TABLE_BODY_MEASURE_HISTORY, null, null, null, null, null, Withings.BODY_MEASURE_HISTORY_DATE + " DESC");

        while (c.moveToNext() && bodyValues.size() < labels.length) {
            String label = c.getString(c.getColumnIndex(Withings.BODY_MEASURE_HISTORY_TYPE));

            if (bodyValues.containsKey(label) == false) {
                double value = c.getDouble(c.getColumnIndex(Withings.BODY_MEASURE_HISTORY_VALUE));

                bodyValues.put(label, value);

                keys.add(label);
            }
        }

        c.close();

        for (int i = 0; i < keys.size() && i < labels.length; i++) {
            String label = keys.get(i);

            TextView labelView = card.findViewById(labels[i]);
            labelView.setText(label.substring(0, 1).toUpperCase(Locale.getDefault()) + label.substring(1) + ":");

            Double value = bodyValues.get(label);

            TextView valueView = card.findViewById(values[i]);
            valueView.setText(String.format(Locale.getDefault(), "%f", value));
        }

        container.addView(card);

        return "" + card.getTag();
    }

    private static String bindSleepPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        @SuppressLint("InflateParams") LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_withings_sleep_page, null);
        card.setTag("" + position);

        container.addView(card);

        return "" + card.getTag();
    }

    private static String bindSleepSummaryPage(ViewGroup container, DataPointViewHolder holder, int position) {
        final Context context = container.getContext();

        @SuppressLint("InflateParams") LinearLayout card = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.card_generator_withings_sleep_summary_page, null);
        card.setTag("" + position);

        container.addView(card);

        return "" + card.getTag();
    }

    @SuppressWarnings("unused")
    public void enableActivityMeasures(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Withings.ACTIVITY_MEASURES_ENABLED, enable);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void enableBodyMeasures(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Withings.BODY_MEASURES_ENABLED, enable);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void enableIntradayActivity(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Withings.INTRADAY_ACTIVITY_ENABLED, enable);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void enableSleepMeasures(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Withings.SLEEP_MEASURES_ENABLED, enable);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void enableSleepSummary(boolean enable) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Withings.SLEEP_SUMMARY_ENABLED, enable);

        e.apply();
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(Withings.DATA_RETENTION_PERIOD, Withings.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = Withings.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(Withings.TABLE_ACTIVITY_MEASURE_HISTORY, where, args);
        this.mDatabase.delete(Withings.TABLE_SLEEP_MEASURE_HISTORY, where, args);
        this.mDatabase.delete(Withings.TABLE_BODY_MEASURE_HISTORY, where, args);
        this.mDatabase.delete(Withings.TABLE_INTRADAY_ACTIVITY_HISTORY, where, args);
        this.mDatabase.delete(Withings.TABLE_SLEEP_SUMMARY_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(Withings.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void setScanDays(long days) {
        if (days >= 0) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
            SharedPreferences.Editor e = prefs.edit();

            e.putLong(Withings.API_SCAN_DAYS, days);
            e.apply();
        }
    }

    public void setServerOnly(boolean serverOnly) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(Withings.SERVER_ONLY, serverOnly);
        e.apply();
    }

    public void setMandatory(boolean isMandatory) {
        this.mIsMandatory = isMandatory;
    }

    public void setRequiresLocalInstall(boolean requiresLocalInstall) {
        this.mLocalInstallRequired = requiresLocalInstall;
    }

    public boolean isAuthenticated() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        return prefs.contains(Withings.PERSISTED_AUTH);
    }

    public void logout() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.remove(Withings.PERSISTED_AUTH);

        e.apply();
    }

    public static class OAuthResultHandlerActivity extends Activity {
        protected void onResume() {
            super.onResume();

            final OAuthResultHandlerActivity me = this;

            final Withings device = Withings.getInstance(this.getApplicationContext());

            AuthorizationResponse resp = AuthorizationResponse.fromIntent(getIntent());
            AuthorizationException ex = AuthorizationException.fromIntent(getIntent());

            if (resp != null) {
                device.authorizationSuccessful(resp, ex);

                this.finish();
            } else {
                ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.Theme_AppCompat_Light_Dialog);

                AlertDialog.Builder builder = new AlertDialog.Builder(wrapper);
                builder.setTitle(R.string.title_dialog_withings_auth_unsuccessful);
                builder.setMessage(R.string.message_dialog_withings_auth_unsuccessful);

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
}

