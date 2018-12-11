package com.audacious_software.passive_data_kit.generators.environment;

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
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.DeviceInformation;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class TimeOfDay extends Generator implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-time-of-day";
    private static final String DATABASE_PATH = "pdk-time-of-day.sqlite";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.environment.TimeOfDay.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.environment.TimeOfDay.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String USE_GOOGLE_SERVICES = "com.audacious_software.passive_data_kit.generators.environment.TimeOfDay.USE_GOOGLE_SERVICES";
    private static final boolean USE_GOOGLE_SERVICES_DEFAULT = true;

    private static final String INCLUDE_LOCATION = "com.audacious_software.passive_data_kit.generators.environment.TimeOfDay.INCLUDE_LOCATION";
    private static final boolean INCLUDE_LOCATION_DEFAULT = false;

    public static final int TIME_OF_DAY_MORNING = 0;
    public static final int TIME_OF_DAY_AFTERNOON = 1;
    public static final int TIME_OF_DAY_EVENING = 2;
    public static final int TIME_OF_DAY_NIGHT = 3;
    public static final int TIME_OF_DAY_UNKNOWN = -1;

    private static TimeOfDay sInstance = null;
    private GoogleApiClient mGoogleApiClient = null;
    private long mUpdateInterval = 300000;

    private boolean mIncludeLocation = TimeOfDay.INCLUDE_LOCATION_DEFAULT;

    private SQLiteDatabase mDatabase = null;
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_LATITUDE = "latitude";
    public static final String HISTORY_LONGITUDE = "longitude";
    public static final String HISTORY_TIMEZONE = "timezone";
    public static final String HISTORY_SUNRISE = "sunrise";
    public static final String HISTORY_SUNSET = "sunset";

    private long mSunrise = 0;
    private long mSunset = 0;

    private long mLatestTimestamp = -1;

    public static String generatorIdentifier() {
        return TimeOfDay.GENERATOR_IDENTIFIER;
    }

    public static TimeOfDay getInstance(Context context) {
        if (TimeOfDay.sInstance == null) {
            TimeOfDay.sInstance = new TimeOfDay(context.getApplicationContext());
        }

        return TimeOfDay.sInstance;
    }

    private TimeOfDay(Context context) {
        super(context);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        this.mIncludeLocation = prefs.getBoolean(TimeOfDay.INCLUDE_LOCATION, TimeOfDay.INCLUDE_LOCATION_DEFAULT);
    }

    public static void start(final Context context) {
        TimeOfDay.getInstance(context).startGenerator();
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return null;
    }

    private void startGenerator() {
        final TimeOfDay me = this;

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, TimeOfDay.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_time_of_day_create_history_table));
        }

        if (version != TimeOfDay.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, TimeOfDay.DATABASE_VERSION);
        }

        Runnable r = new Runnable()
        {

            @Override
            public void run() {
                if (TimeOfDay.useKindleLocationServices())
                {
                    // TODO
                    throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
                }
                else if (TimeOfDay.useGoogleLocationServices(me.mContext))
                {
                    if (me.mGoogleApiClient == null) {
                        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(me.mContext);
                        builder.addConnectionCallbacks(me);
                        builder.addOnConnectionFailedListener(me);
                        builder.addApi(LocationServices.API);

                        me.mGoogleApiClient = builder.build();
                        me.mGoogleApiClient.connect();
                    }
                }
                else
                {
                    // TODO
                    throw new RuntimeException("Throw rocks at developer to implement generic location support.");
                }
            }
        };

        Thread t = new Thread(r);
        t.start();

        Generators.getInstance(this.mContext).registerCustomViewClass(TimeOfDay.GENERATOR_IDENTIFIER, TimeOfDay.class);
    }

    private void stopGenerator() {
        if (this.mGoogleApiClient != null) {
            this.mGoogleApiClient.disconnect();
            this.mGoogleApiClient = null;
        }

        this.mDatabase.close();
        this.mDatabase = null;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean useGoogleLocationServices(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(TimeOfDay.USE_GOOGLE_SERVICES, TimeOfDay.USE_GOOGLE_SERVICES_DEFAULT);
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean useKindleLocationServices() {
        return DeviceInformation.isKindleFire();
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(TimeOfDay.ENABLED, TimeOfDay.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static boolean isRunning(Context context) {
        if (TimeOfDay.sInstance == null) {
            return false;
        }

        if (TimeOfDay.useKindleLocationServices()) {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        } else if (TimeOfDay.useGoogleLocationServices(context)) {
            return (TimeOfDay.sInstance.mGoogleApiClient != null);
        } else {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
        return TimeOfDay.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final TimeOfDay me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (TimeOfDay.isEnabled(this.mContext)) {
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

    @Override
    public void onConnected(Bundle bundle) {
        final LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_LOW_POWER);

        request.setFastestInterval(this.mUpdateInterval);
        request.setInterval(this.mUpdateInterval);

        if (this.mGoogleApiClient != null && this.mGoogleApiClient.isConnected()) {
            if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                //noinspection deprecation
                LocationServices.FusedLocationApi.requestLocationUpdates(this.mGoogleApiClient, request, this, this.mContext.getMainLooper());
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (this.mGoogleApiClient != null && this.mGoogleApiClient.isConnected())
            //noinspection deprecation
            LocationServices.FusedLocationApi.removeLocationUpdates(this.mGoogleApiClient, this);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        this.mGoogleApiClient = null;
    }

    @Override
    public void onLocationChanged(final android.location.Location location) {
        if (location == null)
            return;

        final TimeOfDay me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {

                com.luckycatlabs.sunrisesunset.dto.Location calcLocation = new com.luckycatlabs.sunrisesunset.dto.Location("" + location.getLatitude(), "" + location.getLongitude());
                TimeZone timezone = TimeZone.getDefault();

                SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(calcLocation, timezone.getDisplayName());

                Calendar now = Calendar.getInstance();

                Calendar sunrise = calculator.getOfficialSunriseCalendarForDate(now);
                Calendar sunset = calculator.getOfficialSunsetCalendarForDate(now);

                if (sunrise.getTimeInMillis() > sunset.getTimeInMillis()) {
                    sunset.add(Calendar.DATE, 1);
                }

                ContentValues values = new ContentValues();
                values.put(TimeOfDay.HISTORY_OBSERVED, System.currentTimeMillis());
                values.put(TimeOfDay.HISTORY_LATITUDE, location.getLatitude());
                values.put(TimeOfDay.HISTORY_LONGITUDE, location.getLongitude());
                values.put(TimeOfDay.HISTORY_TIMEZONE, timezone.getDisplayName());
                values.put(TimeOfDay.HISTORY_SUNRISE, sunrise.getTimeInMillis());
                values.put(TimeOfDay.HISTORY_SUNSET, sunset.getTimeInMillis());

                Bundle updated = new Bundle();
                updated.putLong(TimeOfDay.HISTORY_OBSERVED, System.currentTimeMillis());

                if (me.mIncludeLocation) {
                    updated.putDouble(TimeOfDay.HISTORY_LATITUDE, location.getLatitude());
                    updated.putDouble(TimeOfDay.HISTORY_LONGITUDE, location.getLongitude());

                    Bundle metadata = new Bundle();
                    metadata.putDouble(Generator.LATITUDE, location.getLatitude());
                    metadata.putDouble(Generator.LONGITUDE, location.getLongitude());

                    updated.putBundle(Generator.PDK_METADATA, metadata);
                }

                updated.putString(TimeOfDay.HISTORY_TIMEZONE, timezone.getDisplayName());
                updated.putLong(TimeOfDay.HISTORY_SUNRISE, sunrise.getTimeInMillis());
                updated.putLong(TimeOfDay.HISTORY_SUNSET, sunset.getTimeInMillis());

                me.mDatabase.insert(TimeOfDay.TABLE_HISTORY, null, values);

                Generators.getInstance(me.mContext).notifyGeneratorUpdated(TimeOfDay.GENERATOR_IDENTIFIER, updated);

                me.mSunrise = sunrise.getTimeInMillis();
                me.mSunset = sunset.getTimeInMillis();

                me.flushCachedData();

                me.mLatestTimestamp = System.currentTimeMillis();
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        TimeOfDay me = TimeOfDay.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(TimeOfDay.TABLE_HISTORY, null, null, null, null, null, TimeOfDay.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(TimeOfDay.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    public void setIncludeLocation(boolean include) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(TimeOfDay.INCLUDE_LOCATION, include);
        e.apply();

        this.mIncludeLocation = true;
    }

    public long getSunrise() {
        return this.mSunrise;
    }

    public long getSunset() {
        return this.mSunset;
    }

    public int getTimeOfDay() {
        if (this.mSunrise == 0 || this.mSunset == 0) {
            return TIME_OF_DAY_UNKNOWN;
        }

        long now = System.currentTimeMillis();

        Calendar noon = Calendar.getInstance();
        noon.setTimeInMillis(now);
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        noon.set(Calendar.SECOND, 0);
        noon.set(Calendar.MILLISECOND, 0);

        if (now < this.mSunrise) {
            return TimeOfDay.TIME_OF_DAY_NIGHT;
        } else if (now < noon.getTimeInMillis()) {
            return TimeOfDay.TIME_OF_DAY_MORNING;
        } else if (now < this.mSunset - (60 * 60 * 1000)) {
            return TimeOfDay.TIME_OF_DAY_AFTERNOON;
        } else if (now < this.mSunset + (60 * 60 * 1000)) {
            return TimeOfDay.TIME_OF_DAY_EVENING;
        }

        return TimeOfDay.TIME_OF_DAY_NIGHT;
    }

    @Override
    protected void flushCachedData() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(TimeOfDay.DATA_RETENTION_PERIOD, TimeOfDay.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = TimeOfDay.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        this.mDatabase.delete(TimeOfDay.TABLE_HISTORY, where, args);
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(TimeOfDay.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        if (TimeOfDay.useKindleLocationServices()) {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        } else if (TimeOfDay.useGoogleLocationServices(parent.getContext())) {
            return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_time_of_day, parent, false);
        } else {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    @SuppressWarnings({"unused"})
    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        TimeOfDay me = TimeOfDay.getInstance(context);

        long timestamp = 0;

        Cursor c = me.mDatabase.query(TimeOfDay.TABLE_HISTORY, null, null, null, null, null, TimeOfDay.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(TimeOfDay.HISTORY_OBSERVED));
        }

        c.close();

        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        View cardContent = holder.itemView.findViewById(R.id.card_content);

        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        if (timestamp > 0) {
            dateLabel.setText(Generator.formatTimestamp(context, timestamp / 1000));
            cardEmpty.setVisibility(View.GONE);
            cardContent.setVisibility(View.VISIBLE);

            View nightMorning = cardContent.findViewById(R.id.cell_night_morning);
            View morning = cardContent.findViewById(R.id.cell_morning);
            View afternoon = cardContent.findViewById(R.id.cell_afternoon);
            View evening = cardContent.findViewById(R.id.cell_evening);
            View nightNight = cardContent.findViewById(R.id.cell_night_night);

            nightMorning.setAlpha(0.5f);
            morning.setAlpha(0.5f);
            afternoon.setAlpha(0.5f);
            evening.setAlpha(0.5f);
            nightNight.setAlpha(0.5f);

            switch (me.getTimeOfDay()) {
                case TIME_OF_DAY_NIGHT:
                    Calendar now = Calendar.getInstance();

                    if (now.get(Calendar.HOUR_OF_DAY) < 12) {
                        nightMorning.setAlpha(1.0f);
                    } else {
                        nightNight.setAlpha(1.0f);
                    }

                    break;
                case TIME_OF_DAY_MORNING:
                    morning.setAlpha(1.0f);
                    break;
                case TIME_OF_DAY_AFTERNOON:
                    afternoon.setAlpha(1.0f);
                    break;
                case TIME_OF_DAY_EVENING:
                    evening.setAlpha(1.0f);
                    break;
            }

            DateFormat format = android.text.format.DateFormat.getTimeFormat(context);

            TextView sunrise = cardContent.findViewById(R.id.label_sunrise);

            Calendar sunriseCalendar = Calendar.getInstance();
            sunriseCalendar.setTimeInMillis(me.mSunrise);

            sunrise.setText(format.format(sunriseCalendar.getTime()));

            TextView sunset = cardContent.findViewById(R.id.label_sunset);

            Calendar sunsetCalendar = Calendar.getInstance();
            sunsetCalendar.setTimeInMillis(me.mSunset);

            sunset.setText(format.format(sunsetCalendar.getTime()));
        } else {
            dateLabel.setText(R.string.label_never_pdk);
            cardEmpty.setVisibility(View.VISIBLE);
            cardContent.setVisibility(View.GONE);
        }
    }

    public void setUpdateInterval(long interval) {
        this.mUpdateInterval = interval;
    }
}
