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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

public class GoogleAwareness extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-google-awareness";

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

    private static final String DATABASE_PATH = "pdk-google-awareness.sqlite";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";

    private static final String HISTORY_HEADPHONE_STATE = "headphone_state";
    private static final String HISTORY_HEADPHONE_STATE_PLUGGED_IN = "plugged_in";
    private static final String HISTORY_HEADPHONE_STATE_UNPLUGGED = "unplugged";
    private static final String HISTORY_HEADPHONE_STATE_UNKNOWN = "unknown";

    private static final String HISTORY_WEEKDAY = "weekday";
    private static final String HISTORY_WEEKDAY_WEEKDAY = "weekday";
    private static final String HISTORY_WEEKDAY_WEEKEND = "weekend";
    private static final String HISTORY_WEEKDAY_UNKNOWN = "unknown";

    private static final String HISTORY_HOLIDAY = "holiday";
    private static final String HISTORY_HOLIDAY_HOLIDAY = "holiday";
    private static final String HISTORY_HOLIDAY_NOT_HOLIDAY = "not_holiday";
    private static final String HISTORY_HOLIDAY_UNKNOWN = "unknown";

    private static final String HISTORY_TIME_OF_DAY = "time_of_day";
    private static final String HISTORY_TIME_OF_DAY_MORNING = "morning";
    private static final String HISTORY_TIME_OF_DAY_AFTERNOON = "afternoon";
    private static final String HISTORY_TIME_OF_DAY_EVENING = "evening";
    private static final String HISTORY_TIME_OF_DAY_NIGHT = "night";
    private static final String HISTORY_TIME_OF_DAY_UNKNOWN = "unknown";

    private static final String HISTORY_ACTIVITY = "current_activity";
    private static final String HISTORY_ACTIVITY_IN_VEHICLE = "in_vehicle";
    private static final String HISTORY_ACTIVITY_ON_BICYCLE = "on_bicycle";
    private static final String HISTORY_ACTIVITY_ON_FOOT = "on_foot";
    private static final String HISTORY_ACTIVITY_RUNNING = "running";
    private static final String HISTORY_ACTIVITY_STILL = "still";
    private static final String HISTORY_ACTIVITY_TILTING = "tilting";
    private static final String HISTORY_ACTIVITY_WALKING = "walking";
    private static final String HISTORY_ACTIVITY_UNKNOWN = "unknown";

    private static final String HISTORY_ACTIVITY_CONFIDENCE = "current_activity_confidence";

    private static final String HISTORY_WEATHER_TEMPERATURE = "current_temperature";
    private static final String HISTORY_WEATHER_PERCEIVED_TEMPERATURE = "current_perceived_temperature";
    private static final String HISTORY_WEATHER_DEW_POINT = "current_dew_point";
    private static final String HISTORY_WEATHER_HUMIDITY = "current_humidity";

    private static final String HISTORY_WEATHER_CONDITIONS = "current_weather_conditions";
    private static final String HISTORY_WEATHER_CONDITION_CLEAR = "clear";
    private static final String HISTORY_WEATHER_CONDITION_CLOUDY = "cloudy";
    private static final String HISTORY_WEATHER_CONDITION_FOGGY = "foggy";
    private static final String HISTORY_WEATHER_CONDITION_HAZY = "hazy";
    private static final String HISTORY_WEATHER_CONDITION_ICY = "icy";
    private static final String HISTORY_WEATHER_CONDITION_RAINY = "rainy";
    private static final String HISTORY_WEATHER_CONDITION_SNOWY = "snowy";
    private static final String HISTORY_WEATHER_CONDITION_STORMY = "stormy";
    private static final String HISTORY_WEATHER_CONDITION_WINDY = "windy";
    private static final String HISTORY_WEATHER_CONDITION_UNKNOWN = "unknown";

    private static final String HISTORY_CURRENT_PLACE = "current_place";
    private static final String HISTORY_CURRENT_PLACE_UNKNOWN = "unknown";
    private static final String HISTORY_CURRENT_PLACE_ID = "current_place_id";
    private static final String HISTORY_CURRENT_PLACE_ID_UNKNOWN = "unknown";
    private static final String HISTORY_CURRENT_PLACE_LATITUDE = "current_place_latitude";
    private static final String HISTORY_CURRENT_PLACE_LONGITUDE = "current_place_longitude";
    private static final String HISTORY_CURRENT_PLACE_TYPES = "current_place_types";
    private static final String HISTORY_CURRENT_PLACE_CONFIDENCE = "current_place_confidence";

    private static final int HEADPHONE_STATE_PLUGGED_IN = 1;
    private static final int HEADPHONE_STATE_UNPLUGGED = 0;
    private static final int HEADPHONE_STATE_UNKNOWN = -1;

    private static final int DAY_STATE_WEEKDAY = 1;
    private static final int DAY_STATE_WEEKEND = 0;
    private static final int DAY_STATE_UNKNOWN = -1;

    private static final int HOLIDAY_STATE_HOLIDAY = 1;
    private static final int HOLIDAY_STATE_NOT_HOLIDAY = 0;
    private static final int HOLIDAY_STATE_UNKNOWN = -1;

    public static final int TIME_OF_DAY_MORNING = 0;
    public static final int TIME_OF_DAY_AFTERNOON = 1;
    public static final int TIME_OF_DAY_EVENING = 2;
    public static final int TIME_OF_DAY_NIGHT = 3;
    public static final int TIME_OF_DAY_UNKNOWN = -1;

    private static final int ACTIVITY_STILL = 0;
    private static final int ACTIVITY_ON_BICYCLE = 1;
    private static final int ACTIVITY_ON_FOOT = 2;
    private static final int ACTIVITY_RUNNING = 3;
    private static final int ACTIVITY_IN_VEHICLE = 4;
    private static final int ACTIVITY_TILTING = 5;
    private static final int ACTIVITY_WALKING = 6;
    private static final int ACTIVITY_UNKNOWN = -1;

    private static GoogleAwareness sInstance = null;

    private Handler mSensingHandler = null;

    private boolean mIncludeHeadphone;
    private boolean mIncludeTimeOfDay;
    private boolean mIncludeWeather;
    private boolean mIncludePlaces;
    private boolean mIncludeActivity;

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

    private long mLastTimestamp = 0;

    private SQLiteDatabase mDatabase = null;

    private int mPendingRequests = 0;

    @SuppressWarnings("WeakerAccess")
    public static synchronized GoogleAwareness getInstance(Context context) {
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

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, GoogleAwareness.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_google_awareness_create_history_table));
        }

        if (version != GoogleAwareness.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, GoogleAwareness.DATABASE_VERSION);
        }

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

                me.resetPendingRequests();

                if (me.mIncludeHeadphone) {
                    me.incrementPendingRequests();

                    client.getHeadphoneState().addOnCompleteListener(new OnCompleteListener<HeadphoneStateResponse>() {
                        @Override
                        public void onComplete(@NonNull Task<HeadphoneStateResponse> task) {
                            if (task.isSuccessful()) {
                                HeadphoneState headphone =  task.getResult().getHeadphoneState();

                                if (headphone.getState() == HeadphoneState.PLUGGED_IN) {
                                    me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_PLUGGED_IN;
                                } else {
                                    me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNPLUGGED;
                                }
                            } else {
                                me.mHeadphoneState = GoogleAwareness.HEADPHONE_STATE_UNKNOWN;
                            }

                            me.decrementPendingRequests();
                        }
                    });
                }

                if (me.mIncludeTimeOfDay) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        me.incrementPendingRequests();

                        client.getTimeIntervals().addOnCompleteListener(new OnCompleteListener<TimeIntervalsResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<TimeIntervalsResponse> task) {
                                if (task.isSuccessful()) {
                                    TimeIntervals intervals = task.getResult().getTimeIntervals();

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
                        me.incrementPendingRequests();

                        client.getDetectedActivity().addOnCompleteListener(new OnCompleteListener<DetectedActivityResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<DetectedActivityResponse> task) {
                                if (task.isSuccessful()) {
                                    DetectedActivity activity = task.getResult().getActivityRecognitionResult().getMostProbableActivity();

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

                                me.decrementPendingRequests();
                            }
                        });
                    }
                }

                if (me.mIncludeWeather) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        me.incrementPendingRequests();

                        client.getWeather().addOnCompleteListener(new OnCompleteListener<WeatherResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<WeatherResponse> task) {
                                if (task.isSuccessful()) {
                                    Weather weather = task.getResult().getWeather();

                                    if (weather != null) {
                                        me.mWeatherIsClear = false;
                                        me.mWeatherIsCloudy = false;
                                        me.mWeatherIsFoggy = false;
                                        me.mWeatherIsHazy = false;
                                        me.mWeatherIsIcy = false;
                                        me.mWeatherIsRainy = false;
                                        me.mWeatherIsSnowy = false;
                                        me.mWeatherIsStormy = false;
                                        me.mWeatherIsWindy = false;
                                        me.mWeatherIsUnknown = false;

                                        int[] conditions = weather.getConditions();

                                        if (conditions == null) {
                                            conditions = new int[0];
                                        }

                                        for (int condition : conditions) {
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
                                    }
                                } else {
                                    me.mWeatherIsUnknown = true;

                                    me.mDewPoint = Float.NaN;
                                    me.mPerceivedTemperature = Float.NaN;
                                    me.mTemperature = Float.NaN;
                                    me.mHumidity = -1;
                                }

                                me.decrementPendingRequests();
                            }
                        });
                    }
                }

                if (me.mIncludePlaces) {
                    int permissionCheck = ContextCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION);

                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        me.incrementPendingRequests();

                        client.getPlaces().addOnCompleteListener(new OnCompleteListener<PlacesResponse>() {
                            @Override
                            public void onComplete(@NonNull Task<PlacesResponse> task) {
                                if (task.isSuccessful()) {
                                    List<PlaceLikelihood> places = task.getResult().getPlaceLikelihoods();

                                    if (places != null && places.size() > 0) {
                                        Collections.sort(places, new Comparator<PlaceLikelihood>() {
                                            @Override
                                            public int compare(PlaceLikelihood one, PlaceLikelihood two) {
                                                Float oneLikelihood = one.getLikelihood();
                                                Float twoLikelihood = two.getLikelihood();

                                                return twoLikelihood.compareTo(oneLikelihood);
                                            }
                                        });

                                        PlaceLikelihood mostLikely = places.get(0);

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

                                me.decrementPendingRequests();
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

    private void resetPendingRequests() {
        this.mPendingRequests = 0;
    }

    private void incrementPendingRequests() {
        this.mPendingRequests += 1;
    }

    private void decrementPendingRequests() {
        this.mPendingRequests -= 1;

        if (this.mPendingRequests == 0) {
            this.recordSnapshot();
        }
    }

    private void recordSnapshot() {
        long now = System.currentTimeMillis();

        ContentValues toInsert = new ContentValues();
        Bundle toTransmit = new Bundle();

        toInsert.put(GoogleAwareness.HISTORY_OBSERVED, now);
        toTransmit.putLong(GoogleAwareness.HISTORY_OBSERVED, now);

        if (this.mIncludeHeadphone) {
            switch (this.mHeadphoneState) {
                case GoogleAwareness.HEADPHONE_STATE_PLUGGED_IN:
                    toInsert.put(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_PLUGGED_IN);
                    toTransmit.putString(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_PLUGGED_IN);
                    break;
                case GoogleAwareness.HEADPHONE_STATE_UNPLUGGED:
                    toInsert.put(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNPLUGGED);
                    toTransmit.putString(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNPLUGGED);
                    break;
                case GoogleAwareness.HEADPHONE_STATE_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_HEADPHONE_STATE, GoogleAwareness.HISTORY_HEADPHONE_STATE_UNKNOWN);
                    break;
            }
        }

        if (this.mIncludeTimeOfDay) {
            switch (this.mDayState) {
                case GoogleAwareness.DAY_STATE_WEEKDAY:
                    toInsert.put(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKDAY);
                    toTransmit.putString(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKDAY);
                    break;
                case GoogleAwareness.DAY_STATE_WEEKEND:
                    toInsert.put(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKEND);
                    toTransmit.putString(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_WEEKEND);
                    break;
                case GoogleAwareness.DAY_STATE_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_WEEKDAY, GoogleAwareness.HISTORY_WEEKDAY_UNKNOWN);
                    break;
            }

            switch (this.mHolidayState) {
                case GoogleAwareness.HOLIDAY_STATE_HOLIDAY:
                    toInsert.put(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_HOLIDAY);
                    toTransmit.putString(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_HOLIDAY);
                    break;
                case GoogleAwareness.HOLIDAY_STATE_NOT_HOLIDAY:
                    toInsert.put(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_NOT_HOLIDAY);
                    toTransmit.putString(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_NOT_HOLIDAY);
                    break;
                case GoogleAwareness.HOLIDAY_STATE_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_HOLIDAY, GoogleAwareness.HISTORY_HOLIDAY_UNKNOWN);
                    break;
            }

            switch (this.mTimeOfDay) {
                case GoogleAwareness.TIME_OF_DAY_MORNING:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_MORNING);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_MORNING);
                    break;
                case GoogleAwareness.TIME_OF_DAY_AFTERNOON:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_AFTERNOON);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_AFTERNOON);
                    break;
                case GoogleAwareness.TIME_OF_DAY_EVENING:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_EVENING);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_EVENING);
                    break;
                case GoogleAwareness.TIME_OF_DAY_NIGHT:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_NIGHT);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_NIGHT);
                    break;
                case GoogleAwareness.TIME_OF_DAY_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_TIME_OF_DAY, GoogleAwareness.HISTORY_TIME_OF_DAY_UNKNOWN);
                    break;
            }
        }

        if (this.mIncludeActivity) {
            switch (this.mActivity) {
                case GoogleAwareness.ACTIVITY_IN_VEHICLE:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_IN_VEHICLE);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_IN_VEHICLE);

                    break;
                case GoogleAwareness.ACTIVITY_ON_BICYCLE:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_BICYCLE);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_BICYCLE);

                    break;
                case GoogleAwareness.ACTIVITY_ON_FOOT:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_FOOT);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_ON_FOOT);

                    break;
                case GoogleAwareness.ACTIVITY_RUNNING:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_RUNNING);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_RUNNING);

                    break;
                case GoogleAwareness.ACTIVITY_STILL:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_STILL);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_STILL);

                    break;
                case GoogleAwareness.ACTIVITY_TILTING:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_TILTING);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_TILTING);

                    break;
                case GoogleAwareness.ACTIVITY_WALKING:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_WALKING);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_WALKING);

                    break;
                case GoogleAwareness.ACTIVITY_UNKNOWN:
                    toInsert.put(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_UNKNOWN);
                    toTransmit.putString(GoogleAwareness.HISTORY_ACTIVITY, GoogleAwareness.HISTORY_ACTIVITY_UNKNOWN);

                    break;
            }

            toInsert.put(GoogleAwareness.HISTORY_ACTIVITY_CONFIDENCE, this.mActivityConfidence);
            toTransmit.putDouble(GoogleAwareness.HISTORY_ACTIVITY_CONFIDENCE, this.mActivityConfidence);
        }

        if (this.mIncludeWeather) {
            toInsert.put(GoogleAwareness.HISTORY_WEATHER_TEMPERATURE, this.mTemperature);
            toTransmit.putDouble(GoogleAwareness.HISTORY_WEATHER_TEMPERATURE, this.mTemperature);

            toInsert.put(GoogleAwareness.HISTORY_WEATHER_PERCEIVED_TEMPERATURE, this.mPerceivedTemperature);
            toTransmit.putDouble(GoogleAwareness.HISTORY_WEATHER_PERCEIVED_TEMPERATURE, this.mPerceivedTemperature);

            toInsert.put(GoogleAwareness.HISTORY_WEATHER_HUMIDITY, this.mHumidity);
            toTransmit.putDouble(GoogleAwareness.HISTORY_WEATHER_HUMIDITY, this.mHumidity);

            toInsert.put(GoogleAwareness.HISTORY_WEATHER_DEW_POINT, this.mDewPoint);
            toTransmit.putDouble(GoogleAwareness.HISTORY_WEATHER_DEW_POINT, this.mDewPoint);

            ArrayList<String> weatherConditions = new ArrayList<>();

            if (this.mWeatherIsClear) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_CLEAR);
            }

            if (this.mWeatherIsCloudy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_CLOUDY);
            }

            if (this.mWeatherIsFoggy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_FOGGY);
            }

            if (this.mWeatherIsHazy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_HAZY);
            }

            if (this.mWeatherIsIcy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_ICY);
            }

            if (this.mWeatherIsRainy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_RAINY);
            }

            if (this.mWeatherIsSnowy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_SNOWY);
            }

            if (this.mWeatherIsStormy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_STORMY);
            }

            if (this.mWeatherIsWindy) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_WINDY);
            }

            if (this.mWeatherIsUnknown) {
                weatherConditions.add(GoogleAwareness.HISTORY_WEATHER_CONDITION_UNKNOWN);
            }

            toTransmit.putStringArrayList(GoogleAwareness.HISTORY_WEATHER_CONDITIONS, weatherConditions);

            StringBuilder builder = new StringBuilder();

            for (String condition : weatherConditions) {
                if (builder.length() > 0) {
                    builder.append(";");
                }

                builder.append(condition);
            }

            toInsert.put(GoogleAwareness.HISTORY_WEATHER_CONDITIONS, builder.toString());
        }

        if (this.mIncludePlaces) {
            if (this.mPlace != null) {
                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE, this.mPlace.getName().toString());
                toTransmit.putString(GoogleAwareness.HISTORY_CURRENT_PLACE, this.mPlace.getName().toString());

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_ID, this.mPlace.getId());
                toTransmit.putString(GoogleAwareness.HISTORY_CURRENT_PLACE_ID, this.mPlace.getId());

                LatLng coords = this.mPlace.getLatLng();

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_LATITUDE, coords.latitude);
                toTransmit.putDouble(GoogleAwareness.HISTORY_CURRENT_PLACE_LATITUDE, coords.latitude);

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_LONGITUDE, coords.longitude);
                toTransmit.putDouble(GoogleAwareness.HISTORY_CURRENT_PLACE_LONGITUDE, coords.longitude);

                ArrayList<String> placeTypes = GoogleAwareness.getPlaceTypes(this.mPlace);

                StringBuilder builder = new StringBuilder();

                for (String placeType : placeTypes) {
                    if (builder.length() > 0) {
                        builder.append(";");
                    }

                    builder.append(placeType);
                }

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_TYPES, builder.toString());
                toTransmit.putStringArrayList(GoogleAwareness.HISTORY_CURRENT_PLACE_TYPES, placeTypes);

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_CONFIDENCE, this.mPlaceLikelihood);
                toTransmit.putDouble(GoogleAwareness.HISTORY_CURRENT_PLACE_CONFIDENCE, this.mPlaceLikelihood);


            } else {
                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE, GoogleAwareness.HISTORY_CURRENT_PLACE_UNKNOWN);
                toTransmit.putString(GoogleAwareness.HISTORY_CURRENT_PLACE, GoogleAwareness.HISTORY_CURRENT_PLACE_UNKNOWN);

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_ID, GoogleAwareness.HISTORY_CURRENT_PLACE_ID_UNKNOWN);
                toTransmit.putString(GoogleAwareness.HISTORY_CURRENT_PLACE_ID, GoogleAwareness.HISTORY_CURRENT_PLACE_ID_UNKNOWN);

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_LATITUDE, Double.NaN);
                toTransmit.putDouble(GoogleAwareness.HISTORY_CURRENT_PLACE_LATITUDE, Double.NaN);

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_LONGITUDE, Double.NaN);
                toTransmit.putDouble(GoogleAwareness.HISTORY_CURRENT_PLACE_LONGITUDE, Double.NaN);

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_TYPES, "");
                toTransmit.putStringArrayList(GoogleAwareness.HISTORY_CURRENT_PLACE_TYPES, new ArrayList<>());

                toInsert.put(GoogleAwareness.HISTORY_CURRENT_PLACE_CONFIDENCE, 0);
                toTransmit.putDouble(GoogleAwareness.HISTORY_CURRENT_PLACE_CONFIDENCE, 0);
            }
        }

        this.mDatabase.insert(GoogleAwareness.TABLE_HISTORY, null, toInsert);

        Generators.getInstance(this.mContext).notifyGeneratorUpdated(GoogleAwareness.GENERATOR_IDENTIFIER, toTransmit);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(GoogleAwareness.DATA_RETENTION_PERIOD, GoogleAwareness.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = GoogleAwareness.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(GoogleAwareness.TABLE_HISTORY, where, args);
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

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_google_awareness, parent, false);
    }

    @SuppressWarnings("unused")
    public static void bindViewHolder(DataPointViewHolder holder) {
        long start = System.currentTimeMillis();

        final Context context = holder.itemView.getContext();

        long lastTimestamp = 0;

        GoogleAwareness generator = GoogleAwareness.getInstance(holder.itemView.getContext());

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

    private static ArrayList<String> getPlaceTypes(Place place) {
        ArrayList<String> types = new ArrayList<>();

        for (int placeType : place.getPlaceTypes()) {
            switch (placeType) {
                case Place.TYPE_ACCOUNTING:
                    types.add("accounting");
                    break;
                case Place.TYPE_ADMINISTRATIVE_AREA_LEVEL_1:
                    types.add("administrative_area_level_1");
                    break;
                case Place.TYPE_ADMINISTRATIVE_AREA_LEVEL_2:
                    types.add("administrative_area_level_2");
                    break;
                case Place.TYPE_ADMINISTRATIVE_AREA_LEVEL_3:
                    types.add("administrative_area_level_3");
                    break;
                case Place.TYPE_AIRPORT:
                    types.add("airport");
                    break;
                case Place.TYPE_AMUSEMENT_PARK:
                    types.add("amusement_park");
                    break;
                case Place.TYPE_AQUARIUM:
                    types.add("aquarium");
                    break;
                case Place.TYPE_ART_GALLERY:
                    types.add("art_gallery");
                    break;
                case Place.TYPE_ATM:
                    types.add("atm");
                    break;
                case Place.TYPE_BAKERY:
                    types.add("bakery");
                    break;
                case Place.TYPE_BANK:
                    types.add("bank");
                    break;
                case Place.TYPE_BAR:
                    types.add("bar");
                    break;
                case Place.TYPE_BEAUTY_SALON:
                    types.add("beauty_salon");
                    break;
                case Place.TYPE_BICYCLE_STORE:
                    types.add("bicycle_store");
                    break;
                case Place.TYPE_BOOK_STORE:
                    types.add("book_store");
                    break;
                case Place.TYPE_BOWLING_ALLEY:
                    types.add("bowling_alley");
                    break;
                case Place.TYPE_BUS_STATION:
                    types.add("bus_station");
                    break;
                case Place.TYPE_CAFE:
                    types.add("cafe");
                    break;
                case Place.TYPE_CAMPGROUND:
                    types.add("campground");
                    break;
                case Place.TYPE_CAR_DEALER:
                    types.add("car_dealer");
                    break;
                case Place.TYPE_CAR_RENTAL:
                    types.add("car_rental");
                    break;
                case Place.TYPE_CAR_REPAIR:
                    types.add("car_repair");
                    break;
                case Place.TYPE_CAR_WASH:
                    types.add("car_wash");
                    break;
                case Place.TYPE_CASINO:
                    types.add("casino");
                    break;
                case Place.TYPE_CEMETERY:
                    types.add("cemetery");
                    break;
                case Place.TYPE_CHURCH:
                    types.add("church");
                    break;
                case Place.TYPE_CITY_HALL:
                    types.add("city_hall");
                    break;
                case Place.TYPE_CLOTHING_STORE:
                    types.add("clothing_store");
                    break;
                case Place.TYPE_COLLOQUIAL_AREA:
                    types.add("colloquial_area");
                    break;
                case Place.TYPE_CONVENIENCE_STORE:
                    types.add("convenience_store");
                    break;
                case Place.TYPE_COUNTRY:
                    types.add("country");
                    break;
                case Place.TYPE_COURTHOUSE:
                    types.add("courthouse");
                    break;
                case Place.TYPE_DENTIST:
                    types.add("dentist");
                    break;
                case Place.TYPE_DEPARTMENT_STORE:
                    types.add("department_store");
                    break;
                case Place.TYPE_DOCTOR:
                    types.add("doctor");
                    break;
                case Place.TYPE_ELECTRICIAN:
                    types.add("electrician");
                    break;
                case Place.TYPE_ELECTRONICS_STORE:
                    types.add("electronics_store");
                    break;
                case Place.TYPE_EMBASSY:
                    types.add("embassy");
                    break;
                case Place.TYPE_ESTABLISHMENT:
                    types.add("establishment");
                    break;
                case Place.TYPE_FINANCE:
                    types.add("finance");
                    break;
                case Place.TYPE_FIRE_STATION:
                    types.add("fire_station");
                    break;
                case Place.TYPE_FLOOR:
                    types.add("floor");
                    break;
                case Place.TYPE_FLORIST:
                    types.add("florist");
                    break;
                case Place.TYPE_FOOD:
                    types.add("food");
                    break;
                case Place.TYPE_FUNERAL_HOME:
                    types.add("funeral_home");
                    break;
                case Place.TYPE_FURNITURE_STORE:
                    types.add("furniture_store");
                    break;
                case Place.TYPE_GAS_STATION:
                    types.add("gas_station");
                    break;
                case Place.TYPE_GENERAL_CONTRACTOR:
                    types.add("general_contractor");
                    break;
                case Place.TYPE_GEOCODE:
                    types.add("geocode");
                    break;
                case Place.TYPE_GROCERY_OR_SUPERMARKET:
                    types.add("grocery_or_supermarket");
                    break;
                case Place.TYPE_GYM:
                    types.add("gym");
                    break;
                case Place.TYPE_HAIR_CARE:
                    types.add("hair_care");
                    break;
                case Place.TYPE_HARDWARE_STORE:
                    types.add("hardware_store");
                    break;
                case Place.TYPE_HEALTH:
                    types.add("health");
                    break;
                case Place.TYPE_HINDU_TEMPLE:
                    types.add("hindu_temple");
                    break;
                case Place.TYPE_HOME_GOODS_STORE:
                    types.add("home_goods_store");
                    break;
                case Place.TYPE_HOSPITAL:
                    types.add("hospital");
                    break;
                case Place.TYPE_INSURANCE_AGENCY:
                    types.add("insurance_agency");
                    break;
                case Place.TYPE_INTERSECTION:
                    types.add("intersection");
                    break;
                case Place.TYPE_JEWELRY_STORE:
                    types.add("jewelry_store");
                    break;
                case Place.TYPE_LAUNDRY:
                    types.add("laundry");
                    break;
                case Place.TYPE_LAWYER:
                    types.add("lawyer");
                    break;
                case Place.TYPE_LIBRARY:
                    types.add("library");
                    break;
                case Place.TYPE_LIQUOR_STORE:
                    types.add("liquor_store");
                    break;
                case Place.TYPE_LOCALITY:
                    types.add("locality");
                    break;
                case Place.TYPE_LOCAL_GOVERNMENT_OFFICE:
                    types.add("local_government_office");
                    break;
                case Place.TYPE_LOCKSMITH:
                    types.add("locksmith");
                    break;
                case Place.TYPE_LODGING:
                    types.add("lodging");
                    break;
                case Place.TYPE_MEAL_DELIVERY:
                    types.add("meal_delivery");
                    break;
                case Place.TYPE_MEAL_TAKEAWAY:
                    types.add("meal_takeaway");
                    break;
                case Place.TYPE_MOSQUE:
                    types.add("mosque");
                    break;
                case Place.TYPE_MOVIE_RENTAL:
                    types.add("movie_rental");
                    break;
                case Place.TYPE_MOVIE_THEATER:
                    types.add("movie_theater");
                    break;
                case Place.TYPE_MOVING_COMPANY:
                    types.add("moving_company");
                    break;
                case Place.TYPE_MUSEUM:
                    types.add("museum");
                    break;
                case Place.TYPE_NATURAL_FEATURE:
                    types.add("natural_feature");
                    break;
                case Place.TYPE_NEIGHBORHOOD:
                    types.add("neighborhood");
                    break;
                case Place.TYPE_NIGHT_CLUB:
                    types.add("night_club");
                    break;
                case Place.TYPE_OTHER:
                    types.add("other");
                    break;
                case Place.TYPE_PAINTER:
                    types.add("painter");
                    break;
                case Place.TYPE_PARK:
                    types.add("park");
                    break;
                case Place.TYPE_PARKING:
                    types.add("parking");
                    break;
                case Place.TYPE_PET_STORE:
                    types.add("pet_store");
                    break;
                case Place.TYPE_PHARMACY:
                    types.add("pharmacy");
                    break;
                case Place.TYPE_PHYSIOTHERAPIST:
                    types.add("physiotherapist");
                    break;
                case Place.TYPE_PLACE_OF_WORSHIP:
                    types.add("place_of_worship");
                    break;
                case Place.TYPE_PLUMBER:
                    types.add("plumber");
                    break;
                case Place.TYPE_POINT_OF_INTEREST:
                    types.add("point_of_interest");
                    break;
                case Place.TYPE_POLICE:
                    types.add("police");
                    break;
                case Place.TYPE_POLITICAL:
                    types.add("political");
                    break;
                case Place.TYPE_POSTAL_CODE:
                    types.add("postal_code");
                    break;
                case Place.TYPE_POSTAL_CODE_PREFIX:
                    types.add("postal_code_prefix");
                    break;
                case Place.TYPE_POSTAL_TOWN:
                    types.add("postal_town");
                    break;
                case Place.TYPE_POST_BOX:
                    types.add("post_box");
                    break;
                case Place.TYPE_POST_OFFICE:
                    types.add("post_office");
                    break;
                case Place.TYPE_PREMISE:
                    types.add("premise");
                    break;
                case Place.TYPE_REAL_ESTATE_AGENCY:
                    types.add("real_estate_agency");
                    break;
                case Place.TYPE_RESTAURANT:
                    types.add("restaurant");
                    break;
                case Place.TYPE_ROOFING_CONTRACTOR:
                    types.add("roofing_contractor");
                    break;
                case Place.TYPE_ROOM:
                    types.add("room");
                    break;
                case Place.TYPE_ROUTE:
                    types.add("route");
                    break;
                case Place.TYPE_RV_PARK:
                    types.add("rv_park");
                    break;
                case Place.TYPE_SCHOOL:
                    types.add("school");
                    break;
                case Place.TYPE_SHOE_STORE:
                    types.add("shoe_store");
                    break;
                case Place.TYPE_SHOPPING_MALL:
                    types.add("shopping_mall");
                    break;
                case Place.TYPE_SPA:
                    types.add("spa");
                    break;
                case Place.TYPE_STADIUM:
                    types.add("stadium");
                    break;
                case Place.TYPE_STORAGE:
                    types.add("storage");
                    break;
                case Place.TYPE_STORE:
                    types.add("store");
                    break;
                case Place.TYPE_STREET_ADDRESS:
                    types.add("street_address");
                    break;
                case Place.TYPE_SUBLOCALITY:
                    types.add("sublocality");
                    break;
                case Place.TYPE_SUBLOCALITY_LEVEL_1:
                    types.add("sublocality_level_1");
                    break;
                case Place.TYPE_SUBLOCALITY_LEVEL_2:
                    types.add("sublocality_level_2");
                    break;
                case Place.TYPE_SUBLOCALITY_LEVEL_3:
                    types.add("sublocality_level_3");
                    break;
                case Place.TYPE_SUBLOCALITY_LEVEL_4:
                    types.add("sublocality_level_4");
                    break;
                case Place.TYPE_SUBLOCALITY_LEVEL_5:
                    types.add("sublocality_level_5");
                    break;
                case Place.TYPE_SUBPREMISE:
                    types.add("subpremise");
                    break;
                case Place.TYPE_SUBWAY_STATION:
                    types.add("subway_station");
                    break;
                case Place.TYPE_SYNAGOGUE:
                    types.add("synagogue");
                    break;
                case Place.TYPE_SYNTHETIC_GEOCODE:
                    types.add("synthetic_geocode");
                    break;
                case Place.TYPE_TAXI_STAND:
                    types.add("taxi_stand");
                    break;
                case Place.TYPE_TRAIN_STATION:
                    types.add("train_station");
                    break;
                case Place.TYPE_TRANSIT_STATION:
                    types.add("transit_station");
                    break;
                case Place.TYPE_TRAVEL_AGENCY:
                    types.add("travel_agency");
                    break;
                case Place.TYPE_UNIVERSITY:
                    types.add("university");
                    break;
                case Place.TYPE_VETERINARY_CARE:
                    types.add("veterinary_care");
                    break;
                case Place.TYPE_ZOO:
                    types.add("zoo");
                    break;
                default:
                    types.add("unknown-" + placeType);
                    break;
            }
        }

        return types;
    }

    @Override
    public String getIdentifier() {
        return GoogleAwareness.GENERATOR_IDENTIFIER;
    }
}

