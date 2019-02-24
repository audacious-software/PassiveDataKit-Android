package com.audacious_software.passive_data_kit.generators.services;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.SnapshotClient;
import com.google.android.gms.awareness.fence.TimeFence;
import com.google.android.gms.awareness.snapshot.DetectedActivityResponse;
import com.google.android.gms.awareness.snapshot.HeadphoneStateResponse;
import com.google.android.gms.awareness.snapshot.PlacesResponse;
import com.google.android.gms.awareness.snapshot.TimeIntervalsResponse;
import com.google.android.gms.awareness.snapshot.WeatherResponse;
import com.google.android.gms.awareness.state.HeadphoneState;
import com.google.android.gms.awareness.state.TimeIntervals;
import com.google.android.gms.awareness.state.Weather;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class GoogleAwareness extends Generator {
    private static final String GENERATOR_IDENTIFIER = "google-awareness";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String INCLUDE_HEADPHONES = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_HEADPHONES";
    private static final boolean INCLUDE_HEADPHONES_DEFAULT = true;

    private static final String INCLUDE_TIME_OF_DAY = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_TIME_OF_DAY";
    private static final boolean INCLUDE_TIME_OF_DAY_DEFAULT = true;

    private static final String INCLUDE_WEATHER = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_WEATHER";
    private static final boolean INCLUDE_WEATHER_DEFAULT = true;

    private static final String INCLUDE_PLACES = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_PLACES";
    private static final boolean INCLUDE_PLACES_DEFAULT = true;

    private static final String INCLUDE_ACTIVITY = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.INCLUDE_ACTIVITY";
    private static final boolean INCLUDE_ACTIVITY_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.services.GoogleAwareness.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final long SENSING_INTERVAL = 60 * 1000;

    private static int HEADPHONE_STATE_PLUGGED_IN = 1;
    private static int HEADPHONE_STATE_UNPLUGGED = 0;
    private static int HEADPHONE_STATE_UNKNOWN = -1;

    private static int DAY_STATE_WEEKDAY = 1;
    private static int DAY_STATE_WEEKEND = 0;
    private static int DAY_STATE_UNKNOWN = -1;

    private static final int HOLIDAY_STATE_HOLIDAY = 1;
    private static final int HOLIDAY_STATE_NOT_HOLIDAY = 0;
    private static final int HOLIDAY_STATE_UNKNOWN = -1;

    public static final int TIME_OF_DAY_MORNING = 0;
    public static final int TIME_OF_DAY_AFTERNOON = 1;
    public static final int TIME_OF_DAY_EVENING = 2;
    public static final int TIME_OF_DAY_NIGHT = 3;
    public static final int TIME_OF_DAY_UNKNOWN = -1;

    private static int ACTIVITY_STILL = 0;
    private static int ACTIVITY_ON_BICYCLE = 1;
    private static int ACTIVITY_ON_FOOT = 2;
    private static int ACTIVITY_RUNNING = 3;
    private static int ACTIVITY_IN_VEHICLE = 4;
    private static int ACTIVITY_TILTING = 5;
    private static int ACTIVITY_WALKING = 6;

    private static int ACTIVITY_UNKNOWN = -1;

    private static GoogleAwareness sInstance = null;

    private Handler mSensingHandler = null;

    private boolean mIncludeHeadphone = GoogleAwareness.INCLUDE_HEADPHONES_DEFAULT;
    private boolean mIncludeTimeOfDay = GoogleAwareness.INCLUDE_TIME_OF_DAY_DEFAULT;
    private boolean mIncludeWeather = GoogleAwareness.INCLUDE_WEATHER_DEFAULT;
    private boolean mIncludePlaces = GoogleAwareness.INCLUDE_PLACES_DEFAULT;
    private boolean mIncludeActivity = GoogleAwareness.INCLUDE_ACTIVITY_DEFAULT;

    private int mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNKNOWN;

    private int mDayState = GoogleAwareness.DAY_STATE_UNKNOWN;
    private int mHolidayState = GoogleAwareness.HOLIDAY_STATE_UNKNOWN;
    private int mTimeOfDay = GoogleAwareness.TIME_OF_DAY_UNKNOWN;

    private float mDewPoint = Float.NaN;
    private float mPerceivedTemperature = Float.NaN;
    private float mTemperature = Float.NaN;
    private int mHumidity = -1;

    private int mActivity = GoogleAwareness.ACTIVITY_UNKNOWN;
    private float mActivityConfidence = -1;

    private boolean mWeatherIsClear = false;
    private boolean mWeatherIsCloudy = false;
    private boolean mWeatherIsFoggy = false;
    private boolean mWeatherIsHazy = false;
    private boolean mWeatherIsIcy = false;
    private boolean mWeatherIsRainy = false;
    private boolean mWeatherIsSnowy = false;
    private boolean mWeatherIsStormy = false;
    private boolean mWeatherIsWindy = false;
    private boolean mWeatherIsUnknown = false;

    private float mPlaceLikelihood = 0.0f;
    private Place mPlace = null;
    private long mRefreshInterval = (60 * 1000);

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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        this.mIncludeHeadphone = prefs.getBoolean(GoogleAwareness.INCLUDE_HEADPHONES, GoogleAwareness.INCLUDE_HEADPHONES_DEFAULT);
        this.mIncludeTimeOfDay = prefs.getBoolean(GoogleAwareness.INCLUDE_TIME_OF_DAY, GoogleAwareness.INCLUDE_TIME_OF_DAY_DEFAULT);
        this.mIncludeWeather = prefs.getBoolean(GoogleAwareness.INCLUDE_WEATHER, GoogleAwareness.INCLUDE_WEATHER_DEFAULT);
        this.mIncludePlaces = prefs.getBoolean(GoogleAwareness.INCLUDE_PLACES, GoogleAwareness.INCLUDE_WEATHER_DEFAULT);
        this.mIncludeActivity = prefs.getBoolean(GoogleAwareness.INCLUDE_ACTIVITY, GoogleAwareness.INCLUDE_ACTIVITY_DEFAULT);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        GoogleAwareness.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final GoogleAwareness me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(GoogleAwareness.GENERATOR_IDENTIFIER, GoogleAwareness.class);

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
            if (this.mIncludeTimeOfDay || this.mIncludePlaces || this.mIncludeWeather) {
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

                if (me.mIncludeHeadphone) {
                    client.getHeadphoneState().addOnCompleteListener(new OnCompleteListener<HeadphoneStateResponse>() {
                        @Override
                        public void onComplete(@NonNull Task<HeadphoneStateResponse> task) {
                            if (task.isSuccessful()) {
                                HeadphoneState headphone =  task.getResult().getHeadphoneState();

                                Log.e("PDK", "HEADPHONE: " + headphone);

                                if (headphone.getState() == HeadphoneState.PLUGGED_IN) {
                                    me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_PLUGGED_IN;
                                } else {
                                    me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNPLUGGED;
                                }
                            } else {
                                me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNKNOWN;
                            }
                        }
                    });
                }

                if (me.mIncludeTimeOfDay) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        client.getTimeIntervals().addOnCompleteListener(new OnCompleteListener<TimeIntervalsResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<TimeIntervalsResponse> task) {
                                if (task.isSuccessful()) {
                                    TimeIntervals intervals = task.getResult().getTimeIntervals();

                                    Log.e("PDK", "TIME INTERVALS: " + intervals);

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
                            }
                        });
                    }
                }

                if (me.mIncludeActivity) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, "com.google.android.gms.permission.ACTIVITY_RECOGNITION");

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        client.getDetectedActivity().addOnCompleteListener(new OnCompleteListener<DetectedActivityResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<DetectedActivityResponse> task) {
                                if (task.isSuccessful()) {
                                    DetectedActivity activity = task.getResult().getActivityRecognitionResult().getMostProbableActivity();

                                    Log.e("PDK", "ACTIVITY: " + activity);

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
                            }
                        });
                    }
                }

                if (me.mIncludeWeather) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        client.getWeather().addOnCompleteListener(new OnCompleteListener<WeatherResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<WeatherResponse> task) {
                                if (task.isSuccessful()) {
                                    Weather weather = task.getResult().getWeather();

                                    Log.e("PDK", "WEATHER: " + weather);

                                    for (int condition : weather.getConditions()) {
                                        switch (condition) {
                                            case Weather.CONDITION_CLEAR:
                                                me.mWeatherIsClear = true;
                                                break;
                                            case Weather.CONDITION_CLOUDY:
                                                me.mWeatherIsCloudy = true;
                                                break;
                                            case Weather.CONDITION_FOGGY:
                                                me.mWeatherIsFoggy = true;
                                                break;
                                            case Weather.CONDITION_HAZY:
                                                me.mWeatherIsHazy = true;
                                                break;
                                            case Weather.CONDITION_ICY:
                                                me.mWeatherIsIcy = true;
                                                break;
                                            case Weather.CONDITION_RAINY:
                                                me.mWeatherIsRainy = true;
                                                break;
                                            case Weather.CONDITION_SNOWY:
                                                me.mWeatherIsSnowy = true;
                                                break;
                                            case Weather.CONDITION_STORMY:
                                                me.mWeatherIsStormy = true;
                                                break;
                                            case Weather.CONDITION_WINDY:
                                                me.mWeatherIsWindy = true;
                                                break;
                                            case Weather.CONDITION_UNKNOWN:
                                                me.mWeatherIsUnknown = true;
                                                break;
                                        }
                                    }

                                    me.mDewPoint = weather.getDewPoint(Weather.CELSIUS);
                                    me.mPerceivedTemperature = weather.getFeelsLikeTemperature(Weather.CELSIUS);
                                    me.mTemperature = weather.getTemperature(Weather.CELSIUS);
                                    me.mHumidity = weather.getHumidity();
                                } else {
                                    me.mWeatherIsUnknown = true;

                                    me.mDewPoint = Float.NaN;
                                    me.mPerceivedTemperature = Float.NaN;
                                    me.mTemperature = Float.NaN;
                                    me.mHumidity = -1;
                                }

                            }
                        });
                    }
                }

                if (me.mIncludePlaces) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        client.getPlaces().addOnCompleteListener(new OnCompleteListener<PlacesResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<PlacesResponse> task) {
                                if (task.isSuccessful()) {
                                    List<PlaceLikelihood> places = task.getResult().getPlaceLikelihoods();

                                    if (places != null && places.size() > 0) {
                                        Collections.sort(places, new Comparator<PlaceLikelihood>() {
                                            @Override
                                            public int compare(PlaceLikelihood one, PlaceLikelihood two) {
                                                Float oneLikeihood = one.getLikelihood();
                                                Float twoLikeihood = two.getLikelihood();

                                                return twoLikeihood.compareTo(oneLikeihood);
                                            }
                                        });

                                        PlaceLikelihood mostLikely = places.get(0);

                                        Log.e("PDK", "MOST LIKELY PLACE: " + mostLikely);

                                        me.mPlaceLikelihood = mostLikely.getLikelihood();
                                        me.mPlace = mostLikely.getPlace();
                                    } else {
                                        me.mPlaceLikelihood = 0;
                                        me.mPlace = null;
                                    }
                                } else {
                                    me.mPlaceLikelihood = 0;
                                    me.mPlace = null;
                                }
                            }
                        });
                    }
                }
                if (me.mSensingHandler != null) {
                    me.mSensingHandler.postDelayed(this, GoogleAwareness.SENSING_INTERVAL);
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

    public void setIncludeWeather(boolean include) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(GoogleAwareness.INCLUDE_WEATHER, include);
        e.apply();

        this.mIncludeWeather = include;
    }

    public void setIncludePlaces(boolean include) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(GoogleAwareness.INCLUDE_PLACES, include);
        e.apply();

        this.mIncludePlaces = true;
    }

    public void setIncludeActivity(boolean include) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(GoogleAwareness.INCLUDE_ACTIVITY, include);
        e.apply();

        this.mIncludeActivity = true;
    }

    @Override
    public String getIdentifier() {
        return GoogleAwareness.GENERATOR_IDENTIFIER;
    }

}
