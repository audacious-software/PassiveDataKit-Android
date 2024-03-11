package com.audacious_software.passive_data_kit.generators.device;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazon.geo.mapsv2.AmazonMap;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;

import com.audacious_software.passive_data_kit.DeviceInformation;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.DataDisclosureDetailActivity;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.GeneratorViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;
import com.audacious_software.passive_data_kit.R;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.ui.IconGenerator;
import com.mapbox.geojson.Polygon;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.EdgeInsets;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.expressions.generated.Expression;
import com.mapbox.maps.extension.style.layers.LayerUtils;
import com.mapbox.maps.extension.style.layers.generated.HeatmapLayer;
import com.mapbox.maps.extension.style.sources.SourceUtils;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import humanize.Humanize;

@SuppressLint("Range")
public class Location extends Generator implements LocationListener, android.location.LocationListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-location";
    private static final String DATABASE_PATH = "pdk-location.sqlite";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.Location.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATA_RETENTION_PERIOD = "com.audacious_software.passive_data_kit.generators.device.Location.DATA_RETENTION_PERIOD";
    private static final long DATA_RETENTION_PERIOD_DEFAULT = (60L * 24L * 60L * 60L * 1000L);

    private static final String USE_GOOGLE_SERVICES = "com.audacious_software.passive_data_kit.generators.device.Location.USE_GOOGLE_SERVICES";
    private static final boolean USE_GOOGLE_SERVICES_DEFAULT = true;

    private static final String USE_MAPBOX_MAPS = "com.audacious_software.passive_data_kit.generators.device.Location.USE_MAPBOX_MAPS";
    private static final boolean USE_MAPBOX_MAPS_DEFAULT = false;

    private static final String MAPBOX_HEATMAP_LAYER_ID = "mapbox-heatmap";
    private static final String MAPBOX_HEATMAP_LAYER_SOURCE = "mapbox-location-generator";
    private static final String MAPBOX_LOCATION_SOURCE_ID = "mapbox-locations";

    private static final String SETTING_DISPLAY_HYBRID_MAP = "com.audacious_software.passive_data_kit.generators.device.Location.SETTING_DISPLAY_HYBRID_MAP";
    private static final boolean SETTING_DISPLAY_HYBRID_MAP_DEFAULT = true;

    private static final String ACCURACY_MODE = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE";

    public static final int ACCURACY_BEST = 0;
    public static final int ACCURACY_RANDOMIZED = 1;
    public static final int ACCURACY_USER = 2;
    public static final int ACCURACY_DISABLED = 3;

    private static final String ACCURACY_MODE_RANDOMIZED_RANGE = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE_RANDOMIZED_RANGE";
    private static final long ACCURACY_MODE_RANDOMIZED_RANGE_DEFAULT = 100;

    private static final String ACCURACY_MODE_RANDOMIZED_VECTOR_PRESERVED = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE_RANDOMIZED_VECTOR_PRESERVED";
    private static final boolean ACCURACY_MODE_RANDOMIZED_VECTOR_PRESERVED_DEFAULT = false;

    private static final String ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE";
    private static final float ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE_DEFAULT = 0;

    private static final String ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE";
    private static final float ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE_DEFAULT = 0;

    private static final String ACCURACY_MODE_USER_LOCATION = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE_USER_LOCATION";
    private static final String ACCURACY_MODE_USER_LOCATION_DEFAULT = "Chicago, Illinois";

    private static final String ACCURACY_MODE_USER_LOCATION_LATITUDE = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE_USER_LOCATION_LATITUDE";
    private static final String ACCURACY_MODE_USER_LOCATION_LONGITUDE = "com.audacious_software.passive_data_kit.generators.device.Location.ACCURACY_MODE_USER_LOCATION_LONGITUDE";

    private static Location sInstance = null;

    private FusedLocationProviderClient mGoogleApiClient = null;
    private LocationCallback mLocationCallback = null;

    private long mUpdateInterval = 60000;
    private float mMinimumDistance = 400;

    private SQLiteDatabase mDatabase = null;
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE_HISTORY = "history";
    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_OBSERVED = "observed";
    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_LATITUDE = "latitude";
    @SuppressWarnings("WeakerAccess")
    public static final String HISTORY_LONGITUDE = "longitude";
    private static final String HISTORY_ALTITUDE = "altitude";
    private static final String HISTORY_BEARING = "bearing";
    private static final String HISTORY_SPEED = "speed";
    private static final String HISTORY_PROVIDER = "provider";
    private static final String HISTORY_LOCATION_TIMESTAMP = "location_timestamp";
    private static final String HISTORY_ACCURACY = "accuracy";
    private static final String HISTORY_MOCK_LOCATIONS_ENABLED = "mock_locations_enabled";
    public static final String HISTORY_MOCK_LOCATION_APPS_COUNT = "mock_location_apps_count";
    public static final String HISTORY_MOCK_LOCATION_APPS = "mock_location_apps";
    public static final String HISTORY_MOCK_LOCATION_PROVIDER = "mock_location_provider";

    private long mLatestTimestamp = -1;

    @SuppressWarnings("unused")
    public static String generatorIdentifier() {
        return Location.GENERATOR_IDENTIFIER;
    }

    public static synchronized Location getInstance(Context context) {
        if (Location.sInstance == null) {
            Location.sInstance = new Location(context.getApplicationContext());
        }

        return Location.sInstance;
    }

    private Location(Context context) {
        super(context);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Location.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_location_create_history_table));
            case 1:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_location_history_table_add_mock_locations_enabled));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_location_history_table_add_mock_location_apps_count));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_location_history_table_add_mock_location_apps));
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_location_history_table_add_mock_location_provider));
        }

        if (version != Location.DATABASE_VERSION) {
            this.setDatabaseVersion(this.mDatabase, Location.DATABASE_VERSION);
        }

        Generators.getInstance(this.mContext).registerCustomViewClass(Location.GENERATOR_IDENTIFIER, Location.class);
    }

    @SuppressWarnings("unused")
    public static void start(final Context context) {
        Location.getInstance(context).startGenerator();
    }

    public void startGenerator() {
        final Location me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (Location.useKindleLocationServices()) {
                    if (ActivityCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(me.mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        LocationManager locations = (LocationManager) me.mContext.getSystemService(Context.LOCATION_SERVICE);

                        Criteria criteria = new Criteria();
                        criteria.setAccuracy(Criteria.ACCURACY_COARSE);

                        locations.requestLocationUpdates(me.mUpdateInterval, me.mMinimumDistance, criteria, me, Looper.getMainLooper());
                    }
                } else if (Location.useGoogleLocationServices(me.mContext)) {
                    if (me.mGoogleApiClient == null) {
                        me.mGoogleApiClient = LocationServices.getFusedLocationProviderClient(me.mContext);

                        if (me.mGoogleApiClient != null) {
                            LocationRequest request = LocationRequest.create();
                            request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

                            request.setFastestInterval(me.mUpdateInterval);
                            request.setInterval(me.mUpdateInterval);

                            if (me.mLocationCallback == null) {
                                me.mLocationCallback = new LocationCallback() {
                                    public void onLocationAvailability(LocationAvailability locationAvailability) {

                                    }

                                    public void onLocationResult(LocationResult result) {
                                        for (android.location.Location location : result.getLocations()) {
                                            me.onLocationChanged(location);
                                        }
                                    }
                                };
                            }

                            if (me.mGoogleApiClient != null) {
                                me.mGoogleApiClient.requestLocationUpdates(request, me.mLocationCallback, me.mContext.getMainLooper());
                            }
                        }
                    }
                } else {
                    // TODO
                    throw new RuntimeException("Throw rocks at developer to implement generic location support.");
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    public void stopGenerator() {
        if (this.mGoogleApiClient != null) {
            if (this.mLocationCallback != null) {
                this.mGoogleApiClient.removeLocationUpdates(this.mLocationCallback);

                this.mLocationCallback = null;
            }

            this.mGoogleApiClient = null;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean useGoogleLocationServices(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Location.USE_GOOGLE_SERVICES, Location.USE_GOOGLE_SERVICES_DEFAULT);
    }

    public void setUseGoogleLocationServices(boolean useGoogle) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(Location.USE_GOOGLE_SERVICES, useGoogle);
        e.apply();
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean useMapboxMaps(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Location.USE_MAPBOX_MAPS, Location.USE_MAPBOX_MAPS_DEFAULT);
    }

    public void setUseMapboxMaps(boolean usesMapboxMaps) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        SharedPreferences.Editor e = prefs.edit();
        e.putBoolean(Location.USE_MAPBOX_MAPS, usesMapboxMaps);
        e.apply();
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean useKindleLocationServices() {
        return DeviceInformation.isKindleFire();
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Location.ENABLED, Location.ENABLED_DEFAULT);
    }

    @SuppressWarnings({"WeakerAccess"})
    public static boolean isRunning(Context context) {
        if (Location.sInstance == null) {
            return false;
        }

        if (Location.useKindleLocationServices())
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        }
        else if (Location.useGoogleLocationServices(context))
        {
            return (Location.sInstance.mGoogleApiClient != null);
        }
        else
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    @SuppressWarnings("unused")
    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return Location.getInstance(context).runDiagnostics();
    }

    private ArrayList<DiagnosticAction> runDiagnostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final Location me = this;

        final Handler handler = new Handler(Looper.getMainLooper());

        if (Location.isEnabled(this.mContext)) {
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

    @SuppressLint("TrulyRandom")
    @Override
    public void onLocationChanged(android.location.Location location) {
        if (location == null) {
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        int selected = prefs.getInt(Location.ACCURACY_MODE, Location.ACCURACY_BEST);

        this.mLatestTimestamp = System.currentTimeMillis();

        if (selected == Location.ACCURACY_RANDOMIZED) {
            // http://gis.stackexchange.com/a/68275/10230

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            double radius = prefs.getLong(Location.ACCURACY_MODE_RANDOMIZED_RANGE, Location.ACCURACY_MODE_RANDOMIZED_RANGE_DEFAULT);

            double radiusInDegrees = radius / 111300;

            double u = (double) Location.ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE_DEFAULT;
            double v = (double) Location.ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE_DEFAULT;

            boolean preserveVector = prefs.getBoolean(Location.ACCURACY_MODE_RANDOMIZED_VECTOR_PRESERVED, Location.ACCURACY_MODE_RANDOMIZED_VECTOR_PRESERVED_DEFAULT);

            if (preserveVector) {
                u = (double) prefs.getFloat(Location.ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE, Location.ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE_DEFAULT);
                v = (double) prefs.getFloat(Location.ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE, Location.ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE_DEFAULT);
            }

            if (u == Location.ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE_DEFAULT && v == Location.ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE_DEFAULT) {
                Random r = new SecureRandom();

                u = r.nextDouble();
                v = r.nextDouble();

                if (preserveVector) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putFloat(Location.ACCURACY_MODE_RANDOMIZED_VECTOR_DISTANCE, (float) u);
                    e.putFloat(Location.ACCURACY_MODE_RANDOMIZED_VECTOR_ANGLE, (float) v);

                    e.apply();
                }
            }

            double w = radiusInDegrees * Math.sqrt(u);
            double t = 2 * Math.PI * v;
            double x = w * Math.cos(t);
            double y = w * Math.sin(t);

            // Adjust the x-coordinate for the shrinking of the east-west distances
            longitude = x + longitude; //  + (x / Math.cos(latitude));
            latitude = y + latitude;

            location.setLongitude(longitude);
            location.setLatitude(latitude);
        }

        final ContentValues values = new ContentValues();
        values.put(Location.HISTORY_OBSERVED, System.currentTimeMillis());
        values.put(Location.HISTORY_LATITUDE, location.getLatitude());
        values.put(Location.HISTORY_LONGITUDE, location.getLongitude());
        values.put(Location.HISTORY_PROVIDER, location.getProvider());
        values.put(Location.HISTORY_LOCATION_TIMESTAMP, location.getTime());

        final Bundle updated = new Bundle();
        updated.putLong(Location.HISTORY_OBSERVED, System.currentTimeMillis());
        updated.putDouble(Location.HISTORY_LATITUDE, location.getLatitude());
        updated.putDouble(Location.HISTORY_LONGITUDE, location.getLongitude());
        updated.putString(Location.HISTORY_PROVIDER, location.getProvider());
        updated.putLong(Location.HISTORY_LOCATION_TIMESTAMP, location.getTime());

        Bundle metadata = new Bundle();
        metadata.putDouble(Generator.LATITUDE, location.getLatitude());
        metadata.putDouble(Generator.LONGITUDE, location.getLongitude());

        updated.putBundle(Generator.PDK_METADATA, metadata);

        if (location.hasAltitude()) {
            values.put(Location.HISTORY_ALTITUDE, location.getAltitude());
            updated.putDouble(Location.HISTORY_ALTITUDE, location.getAltitude());
        }

        if (location.hasBearing()) {
            values.put(Location.HISTORY_BEARING, location.getBearing());
            updated.putDouble(Location.HISTORY_BEARING, location.getBearing());
        }

        if (location.hasSpeed()) {
            values.put(Location.HISTORY_SPEED, location.getBearing());
            updated.putDouble(Location.HISTORY_SPEED, location.getBearing());
        }

        if (location.hasAccuracy()) {
            values.put(Location.HISTORY_ACCURACY, location.getAccuracy());
            updated.putDouble(Location.HISTORY_ACCURACY, location.getAccuracy());
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            boolean mockLocationsEnabled = Settings.Secure.getString(this.mContext.getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION).equals("0");

            values.put(Location.HISTORY_MOCK_LOCATIONS_ENABLED, mockLocationsEnabled);
            updated.putBoolean(Location.HISTORY_MOCK_LOCATIONS_ENABLED, mockLocationsEnabled);
        }

        ArrayList<String> mockLocationApps = new ArrayList<String>();
        JSONArray mockLocationAppsJson = new JSONArray();

        PackageManager pm = this.mContext.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo applicationInfo : packages) {
            try {
                PackageInfo packageInfo = pm.getPackageInfo(applicationInfo.packageName, PackageManager.GET_PERMISSIONS);

                String[] requestedPermissions = packageInfo.requestedPermissions;

                if (requestedPermissions != null) {
                    for (int i = 0; i < requestedPermissions.length; i++) {
                        if (requestedPermissions[i].equals("android.permission.ACCESS_MOCK_LOCATION")) {
                            if (mockLocationApps.contains(applicationInfo.packageName) == false) {
                                mockLocationApps.add(applicationInfo.packageName);
                                mockLocationAppsJson.put(applicationInfo.packageName);
                            }
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("PDK" , "MOCK LOCATION STACK TRACE", e);
            }
        }

        values.put(Location.HISTORY_MOCK_LOCATION_APPS_COUNT, mockLocationApps.size());
        updated.putInt(Location.HISTORY_MOCK_LOCATION_APPS_COUNT, mockLocationApps.size());

        values.put(Location.HISTORY_MOCK_LOCATION_APPS, mockLocationAppsJson.toString());
        updated.putStringArrayList(Location.HISTORY_MOCK_LOCATION_APPS, mockLocationApps);

        values.put(Location.HISTORY_MOCK_LOCATION_PROVIDER, location.isFromMockProvider());
        updated.putBoolean(Location.HISTORY_MOCK_LOCATION_PROVIDER, location.isFromMockProvider());

        final Location me = this;

        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (me.mDatabase != null) {
                    me.mDatabase.insert(Location.TABLE_HISTORY, null, values);
                }

                Generators.getInstance(me.mContext).notifyGeneratorUpdated(Location.GENERATOR_IDENTIFIER, updated);

                me.flushCachedData();
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @SuppressWarnings("unused")
    public static long latestPointGenerated(Context context) {
        Location me = Location.getInstance(context);

        if (me.mLatestTimestamp != -1) {
            return me.mLatestTimestamp;
        }

        Cursor c = me.mDatabase.query(Location.TABLE_HISTORY, null, null, null, null, null, Location.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            me.mLatestTimestamp = c.getLong(c.getColumnIndex(Location.HISTORY_OBSERVED));
        }

        c.close();

        return me.mLatestTimestamp;
    }

    @SuppressWarnings({"UnusedAssignment", "unused"})
    public static void bindViewHolder(DataPointViewHolder holder) {
        Log.e("PDK", "bindViewHolder");

        final Context context = holder.itemView.getContext();

        Location me = Location.getInstance(context);

        double lastLatitude = 0.0;
        double lastLongitude = 0.0;
        long timestamp = 0;

        final List<LatLng> locations = new ArrayList<>();

        String where = Location.HISTORY_OBSERVED + " > ?";
        String[] args = { "" + (System.currentTimeMillis() - (1000 * 60 * 60 * 24)) };

        Cursor c = me.mDatabase.query(Location.TABLE_HISTORY, null, where, args, null, null, Location.HISTORY_OBSERVED);

        while (c.moveToNext()) {
            lastLatitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LATITUDE));
            lastLongitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LONGITUDE));
            timestamp = c.getLong(c.getColumnIndex(Location.HISTORY_OBSERVED));

            LatLng location = new LatLng(lastLatitude, lastLongitude);

            locations.add(location);
        }

        c.close();

        TextView dateLabel = holder.itemView.findViewById(R.id.generator_data_point_date);

        long storage = me.storageUsed();

        String storageDesc = context.getString(R.string.label_storage_unknown);

        if (storage >= 0) {
            storageDesc = Humanize.binaryPrefix(storage);
        }

        dateLabel.setText(context.getString(R.string.label_storage_date_card, Generator.formatTimestamp(context, timestamp / 1000.0), storageDesc));

        final DisplayMetrics metrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(metrics);

        if (Location.useMapboxMaps(holder.itemView.getContext())) {
            final com.mapbox.maps.MapView mapView = holder.itemView.findViewById(R.id.mapbox_map);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            final boolean useHybrid = prefs.getBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, Location.SETTING_DISPLAY_HYBRID_MAP_DEFAULT);

            SwitchCompat hybridSwitch = holder.itemView.findViewById(R.id.pdk_mapbox_map_type_hybrid);
            hybridSwitch.setChecked(useHybrid);

            holder.setOnAttachListener(new Runnable() {
                @Override
                public void run() {
                    mapView.onStart();

                    if (hybridSwitch.isChecked()) {
                        mapView.getMapboxMap().loadStyle(Style.SATELLITE_STREETS, new Style.OnStyleLoaded() {
                            @Override
                            public void onStyleLoaded(@NonNull Style style) {
                                me.addHeatmapLayer(style, locations);
                            }
                        });
                    } else {
                        mapView.getMapboxMap().loadStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
                            @Override
                            public void onStyleLoaded(@NonNull Style style) {
                                me.addHeatmapLayer(style, locations);
                            }
                        });
                    }

                    ArrayList<Point> points = new ArrayList<>();

                    for (LatLng latLng : locations) {
                        points.add(Point.fromLngLat(latLng.longitude, latLng.latitude));
                    }

                    List<List<Point>> allPoints = new ArrayList<>();
                    allPoints.add(points);

                    Polygon coverArea = Polygon.fromLngLats(allPoints);

                    CameraOptions camera = mapView.getMapboxMap().cameraForCoordinates(points, new EdgeInsets(4, 4, 4, 4), null, null);


                    mapView.getMapboxMap().setCamera(camera);
                }
            });

            holder.setOnDetachListener(new Runnable() {
                @Override
                public void run() {
                    mapView.onStop();
                }
            });

            hybridSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, final boolean checked) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, checked);
                    e.apply();

                    if (checked) {
                        mapView.getMapboxMap().loadStyle(Style.SATELLITE_STREETS, new Style.OnStyleLoaded() {
                            @Override
                            public void onStyleLoaded(@NonNull Style style) {
                                me.addHeatmapLayer(style, locations);
                            }
                        });
                    } else {
                        mapView.getMapboxMap().loadStyle(Style.MAPBOX_STREETS);

                        mapView.getMapboxMap().loadStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
                            @Override
                            public void onStyleLoaded(@NonNull Style style) {
                                me.addHeatmapLayer(style, locations);
                            }
                        });
                    }
                }
            });

            ColorStateList buttonStates = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_enabled},
                            new int[]{}
                    },
                    new int[]{
                            0xfff1f1f1,
                            0x1c000000,
                            0xff33691E
                    }
            );

            DrawableCompat.setTintList(hybridSwitch.getThumbDrawable(), buttonStates);

            IconGenerator iconGen = new IconGenerator(context);

            Drawable shapeDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_location_heatmap_marker, null);
            iconGen.setBackground(shapeDrawable);

            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(8, 8));
            iconGen.setContentView(view);

            final Bitmap bitmap = iconGen.makeIcon();

        } else if (Location.useKindleLocationServices()) {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        } else if (Location.useGoogleLocationServices(holder.itemView.getContext())) {
            final MapView mapView = holder.itemView.findViewById(R.id.map_view);
            mapView.onCreate(null);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            final boolean useHybrid = prefs.getBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, Location.SETTING_DISPLAY_HYBRID_MAP_DEFAULT);

            SwitchCompat hybridSwitch = holder.itemView.findViewById(R.id.pdk_google_location_map_type_hybrid);
            hybridSwitch.setChecked(useHybrid);

            hybridSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, final boolean checked) {
                    SharedPreferences.Editor e = prefs.edit();
                    e.putBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, checked);
                    e.apply();

                    mapView.getMapAsync(new OnMapReadyCallback() {
                        public void onMapReady(GoogleMap googleMap) {
                            if (checked) {
                                googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                            } else {
                                googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                            }
                        }
                    });
                }
            });

            ColorStateList buttonStates = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_enabled},
                            new int[]{}
                    },
                    new int[]{
                            0xfff1f1f1,
                            0x1c000000,
                            0xff33691E
                    }
            );

            DrawableCompat.setTintList(hybridSwitch.getThumbDrawable(), buttonStates);

            IconGenerator iconGen = new IconGenerator(context);

            Drawable shapeDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_location_heatmap_marker, null);
            iconGen.setBackground(shapeDrawable);

            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(8, 8));
            iconGen.setContentView(view);

            final Bitmap bitmap = iconGen.makeIcon();

            mapView.getMapAsync(new OnMapReadyCallback() {
                public void onMapReady(GoogleMap googleMap) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        googleMap.setMyLocationEnabled(true);
                    }

                    if (useHybrid) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    } else {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }

                    googleMap.getUiSettings().setZoomControlsEnabled(true);
                    googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                    googleMap.getUiSettings().setMapToolbarEnabled(false);
                    googleMap.getUiSettings().setAllGesturesEnabled(false);

                    LatLngBounds.Builder builder = new LatLngBounds.Builder();

                    for (LatLng latlng : locations) {
                        builder.include(latlng);
                    }

                    if (locations.size() > 0) {
                        try {
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), (int) (16 * metrics.density)));
                        } catch (IllegalStateException e) {
                            // View not ready to update yet...
                        }
                    }

                    for (LatLng latLng : locations) {
                        googleMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)));
                    }

                    mapView.onResume();
                }
            });
        } else {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    private void addHeatmapLayer(Style style, List<LatLng> locations) {
        ArrayList<Feature> features = new ArrayList<>();

        GeoJsonSource.Builder builder = new GeoJsonSource.Builder(Location.MAPBOX_LOCATION_SOURCE_ID);

        for (LatLng latlng : locations) {
            Point location = Point.fromLngLat(latlng.longitude, latlng.latitude);

            Feature feature = Feature.fromGeometry(location);

            features.add(feature);
        }

        builder.featureCollection(FeatureCollection.fromFeatures(features));

        SourceUtils.addSource(style, builder.build());

        HeatmapLayer layer = new HeatmapLayer(MAPBOX_HEATMAP_LAYER_ID, MAPBOX_LOCATION_SOURCE_ID);
        layer.sourceLayer(MAPBOX_LOCATION_SOURCE_ID);

        layer.heatmapColor(Expression.interpolate(
            Expression.linear(), Expression.heatmapDensity(),
                Expression.literal(0.0), Expression.rgba(255, 255, 255, 0),
                Expression.literal(0.1), Expression.rgba(33, 102, 172, 1),
                Expression.literal(0.75), Expression.rgb(103, 169, 207),
                Expression.literal(0.875), Expression.rgb(209, 229, 240),
                Expression.literal(0.9375), Expression.rgb(253, 219, 199),
                Expression.literal(0.96875), Expression.rgb(239, 138, 98),
                Expression.literal(1), Expression.rgb(178, 24, 43)
            )
        );

        layer.heatmapWeight(Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.literal(0), Expression.literal(0),
                        Expression.literal(22), Expression.literal(1.0)
                )
        );

        layer.heatmapIntensity(Expression.interpolate(
            Expression.linear(), Expression.zoom(),
                Expression.literal(0), Expression.literal(0),
                Expression.literal(22), Expression.literal(1)
            )
        );

        layer.heatmapRadius(Expression.interpolate(
                Expression.linear(), Expression.zoom(),
                Expression.literal(0), Expression.literal(2),
                Expression.literal(22), Expression.literal(10)
            )
        );

        layer.heatmapOpacity(1.0);

        LayerUtils.addLayer(style, layer);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    public static View fetchView(ViewGroup parent) {
        if (Location.useMapboxMaps(parent.getContext())) {
            return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_location_mapbox, parent, false);
        } else if (Location.useKindleLocationServices()) {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        } else if (Location.useGoogleLocationServices(parent.getContext())) {
            return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_location_google, parent, false);
        } else {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static String getGeneratorTitle(Context context) {
        return context.getString(R.string.generator_location);
    }

    @SuppressWarnings("unused")
    public static List<DataDisclosureDetailActivity.Action> getDisclosureActions(final Context context) {
        List<DataDisclosureDetailActivity.Action> actions = new ArrayList<>();

        DataDisclosureDetailActivity.Action disclosure = new DataDisclosureDetailActivity.Action();

        disclosure.title = context.getString(R.string.label_data_collection_description);
        disclosure.subtitle = context.getString(R.string.label_data_collection_description_more);

        WebView disclosureView = new WebView(context);
        disclosureView.loadUrl("file:///android_asset/html/passive_data_kit/generator_location_disclosure.html");

        disclosure.view = disclosureView;

        actions.add(disclosure);

        DataDisclosureDetailActivity.Action accuracy = new DataDisclosureDetailActivity.Action();
        accuracy.title = context.getString(R.string.label_data_collection_location_accuracy);
        accuracy.subtitle = context.getString(R.string.label_data_collection_location_accuracy_more);

        final Integer[] options = { Location.ACCURACY_BEST, Location.ACCURACY_RANDOMIZED, Location.ACCURACY_USER, Location.ACCURACY_DISABLED };

        final ListView listView = new ListView(context);

        final ArrayAdapter<Integer> accuracyAdapter = new ArrayAdapter<Integer>(context, R.layout.row_disclosure_location_accuracy_pdk, options) {
            @NonNull
            @SuppressLint("InflateParams")
            public View getView (final int position, View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(context).inflate(R.layout.row_disclosure_location_accuracy_pdk, null);
                }

                final Integer option = options[position];

                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                int selected = prefs.getInt(Location.ACCURACY_MODE, Location.ACCURACY_BEST);

                CheckBox checked = convertView.findViewById(R.id.action_checked);

                checked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {

                    }
                });

                checked.setChecked(selected == option);

                TextView title = convertView.findViewById(R.id.action_title);
                TextView description = convertView.findViewById(R.id.action_description);

                if (option == Location.ACCURACY_BEST) {
                    title.setText(R.string.label_data_collection_location_accuracy_best);
                    description.setText(R.string.label_data_collection_location_accuracy_best_more);
                } else if (option == Location.ACCURACY_RANDOMIZED) {
                    title.setText(R.string.label_data_collection_location_accuracy_randomized);
                    description.setText(R.string.label_data_collection_location_accuracy_randomized_more);
                } else if (option == Location.ACCURACY_USER) {
                    title.setText(R.string.label_data_collection_location_accuracy_user);
                    description.setText(R.string.label_data_collection_location_accuracy_user_more);
                } else if (option == Location.ACCURACY_DISABLED) {
                    title.setText(R.string.label_data_collection_location_accuracy_disabled);
                    description.setText(R.string.label_data_collection_location_accuracy_disabled_more);
                }

                final ArrayAdapter<Integer> meAdapter = this;

                checked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @SuppressLint("SetTextI18n")
                    @Override
                    public void onCheckedChanged(final CompoundButton compoundButton, final boolean checked) {
                        final CompoundButton.OnCheckedChangeListener me = this;

                        if (option == Location.ACCURACY_BEST) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTitle(R.string.title_location_accuracy_best);
                            builder.setMessage(R.string.message_location_accuracy_best);

                            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    SharedPreferences.Editor e = prefs.edit();
                                    e.putInt(Location.ACCURACY_MODE, option);
                                    e.apply();

                                    meAdapter.notifyDataSetChanged();
                                }
                            });

                            builder.create().show();
                        } else if (option == Location.ACCURACY_RANDOMIZED) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTitle(R.string.title_location_accuracy_randomized);

                            @SuppressLint("InflateParams") View body = LayoutInflater.from(context).inflate(R.layout.dialog_location_randomized, null);
                            builder.setView(body);

                            final EditText rangeField = body.findViewById(R.id.random_range);

                            long existingRange = prefs.getLong(Location.ACCURACY_MODE_RANDOMIZED_RANGE, Location.ACCURACY_MODE_RANDOMIZED_RANGE_DEFAULT);

                            rangeField.setText("" + existingRange);

                            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    SharedPreferences.Editor e = prefs.edit();

                                    long randomRange = Long.parseLong(rangeField.getText().toString());

                                    e.putLong(Location.ACCURACY_MODE_RANDOMIZED_RANGE, randomRange);

                                    e.putInt(Location.ACCURACY_MODE, option);
                                    e.apply();

                                    meAdapter.notifyDataSetChanged();
                                }
                            });

                            builder.create().show();
                        } else if (option == Location.ACCURACY_USER) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTitle(R.string.title_location_accuracy_user);

                            @SuppressLint("InflateParams") View body = LayoutInflater.from(context).inflate(R.layout.dialog_location_user, null);
                            builder.setView(body);

                            final EditText locationField = body.findViewById(R.id.user_location);

                            String existingLocation = prefs.getString(Location.ACCURACY_MODE_USER_LOCATION, Location.ACCURACY_MODE_USER_LOCATION_DEFAULT);

                            locationField.setText(existingLocation);

                            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    SharedPreferences.Editor e = prefs.edit();

                                    String location = locationField.getText().toString();

                                    e.putString(Location.ACCURACY_MODE_USER_LOCATION, location);

                                    try {
                                        List<Address> results = (new Geocoder(context)).getFromLocationName(location, 1);

                                        if (results.size() > 0) {
                                            Address match = results.get(0);

                                            e.putFloat(Location.ACCURACY_MODE_USER_LOCATION_LATITUDE, (float) match.getLatitude());
                                            e.putFloat(Location.ACCURACY_MODE_USER_LOCATION_LONGITUDE, (float) match.getLongitude());
                                        } else {
                                            Toast.makeText(context, R.string.toast_location_lookup_failed, Toast.LENGTH_LONG).show();

                                            me.onCheckedChanged(compoundButton, checked);
                                        }
                                    } catch (IOException e1) {
                                        AppEvent.getInstance(context).logThrowable(e1);
                                    }

                                    e.putInt(Location.ACCURACY_MODE, option);
                                    e.apply();

                                    meAdapter.notifyDataSetChanged();
                                }
                            });

                            builder.create().show();
                        } else if (option == Location.ACCURACY_DISABLED) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                            builder.setTitle(R.string.title_location_accuracy_disabled);
                            builder.setMessage(R.string.message_location_accuracy_disabled);

                            builder.setPositiveButton(R.string.action_continue, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    SharedPreferences.Editor e = prefs.edit();
                                    e.putInt(Location.ACCURACY_MODE, option);
                                    e.apply();

                                    meAdapter.notifyDataSetChanged();
                                }
                            });

                            builder.create().show();
                        }

                    }
                });

                return convertView;
            }
        };

        listView.setAdapter(accuracyAdapter);

        accuracy.view = listView;

        actions.add(accuracy);

        return actions;
    }

    @SuppressWarnings("unused")
    public static View getDisclosureDataView(final GeneratorViewHolder holder) {
        final Context context = holder.itemView.getContext();

        if (Location.useMapboxMaps(holder.itemView.getContext())) {
            final MapView mapView = new MapView(context);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(params);

            mapView.onCreate(null);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            final boolean useHybrid = prefs.getBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, Location.SETTING_DISPLAY_HYBRID_MAP_DEFAULT);

            IconGenerator iconGen = new IconGenerator(context);

            Drawable shapeDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_location_heatmap_marker, null);
            iconGen.setBackground(shapeDrawable);

            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(8, 8));
            iconGen.setContentView(view);

            final Bitmap bitmap = iconGen.makeIcon();

            mapView.getMapAsync(new OnMapReadyCallback() {
                @SuppressWarnings("UnusedAssignment")
                public void onMapReady(GoogleMap googleMap) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        googleMap.setMyLocationEnabled(true);
                    }

                    if (useHybrid) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    } else {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }

                    googleMap.getUiSettings().setZoomControlsEnabled(true);
                    googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                    googleMap.getUiSettings().setMapToolbarEnabled(false);
                    googleMap.getUiSettings().setAllGesturesEnabled(false);

                    Location me = Location.getInstance(context);

                    double lastLatitude = 0.0;
                    double lastLongitude = 0.0;

                    final List<LatLng> locations = new ArrayList<>();

                    String where = Location.HISTORY_OBSERVED + " > ?";
                    String[] args = { "" + (System.currentTimeMillis() - (1000 * 60 * 60 * 24)) };

                    Cursor c = me.mDatabase.query(Location.TABLE_HISTORY, null, where, args, null, null, Location.HISTORY_OBSERVED);

                    while (c.moveToNext()) {
                        lastLatitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LATITUDE));
                        lastLongitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LONGITUDE));

                        LatLng location = new LatLng(lastLatitude, lastLongitude);

                        locations.add(location);
                    }

                    c.close();

                    LatLngBounds.Builder builder = new LatLngBounds.Builder();

                    for (LatLng latlng : locations) {
                        builder.include(latlng);
                    }

                    final DisplayMetrics metrics = new DisplayMetrics();
                    ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(metrics);

                    if (locations.size() > 0) {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), (int) (16 * metrics.density)));
                    }

                    for (LatLng latLng : locations) {
                        googleMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)));
                    }

                    mapView.onResume();
                }
            });

            return mapView;
        } else if (Location.useKindleLocationServices()) {
            final com.amazon.geo.mapsv2.MapView mapView = new com.amazon.geo.mapsv2.MapView(context);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(params);

            mapView.onCreate(null);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            final boolean useHybrid = prefs.getBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, Location.SETTING_DISPLAY_HYBRID_MAP_DEFAULT);

            IconGenerator iconGen = new IconGenerator(context);

            Drawable shapeDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_location_heatmap_marker, null);
            iconGen.setBackground(shapeDrawable);

            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(8, 8));
            iconGen.setContentView(view);

            final Bitmap bitmap = iconGen.makeIcon();

            mapView.getMapAsync(new com.amazon.geo.mapsv2.OnMapReadyCallback() {
                @SuppressWarnings("UnusedAssignment")
                public void onMapReady(AmazonMap amazonMap) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        amazonMap.setMyLocationEnabled(true);
                    }

                    if (useHybrid) {
                        amazonMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    } else {
                        amazonMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }

                    amazonMap.getUiSettings().setZoomControlsEnabled(true);
                    amazonMap.getUiSettings().setMyLocationButtonEnabled(false);
                    amazonMap.getUiSettings().setMapToolbarEnabled(false);
                    amazonMap.getUiSettings().setAllGesturesEnabled(false);

                    Location me = Location.getInstance(context);

                    double lastLatitude = 0.0;
                    double lastLongitude = 0.0;

                    final List<com.amazon.geo.mapsv2.model.LatLng> locations = new ArrayList<>();

                    String where = Location.HISTORY_OBSERVED + " > ?";
                    String[] args = { "" + (System.currentTimeMillis() - (1000 * 60 * 60 * 24)) };

                    Cursor c = me.mDatabase.query(Location.TABLE_HISTORY, null, where, args, null, null, Location.HISTORY_OBSERVED);

                    while (c.moveToNext()) {
                        lastLatitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LATITUDE));
                        lastLongitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LONGITUDE));

                        com.amazon.geo.mapsv2.model.LatLng location = new com.amazon.geo.mapsv2.model.LatLng(lastLatitude, lastLongitude);

                        locations.add(location);
                    }

                    c.close();

                    com.amazon.geo.mapsv2.model.LatLngBounds.Builder builder = new com.amazon.geo.mapsv2.model.LatLngBounds.Builder();

                    for (com.amazon.geo.mapsv2.model.LatLng latlng : locations) {
                        builder.include(latlng);
                    }

                    final DisplayMetrics metrics = new DisplayMetrics();
                    ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(metrics);

                    if (locations.size() > 0) {
                        amazonMap.moveCamera(com.amazon.geo.mapsv2.CameraUpdateFactory.newLatLngBounds(builder.build(), (int) (16 * metrics.density)));
                    }

                    for (com.amazon.geo.mapsv2.model.LatLng latLng : locations) {
                        amazonMap.addMarker(new com.amazon.geo.mapsv2.model.MarkerOptions()
                                .position(latLng)
                                .icon(com.amazon.geo.mapsv2.model.BitmapDescriptorFactory.fromBitmap(bitmap)));
                    }

                    mapView.onResume();
                }
            });

            return mapView;
        } else if (Location.useGoogleLocationServices(holder.itemView.getContext())) {
            final MapView mapView = new MapView(context);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mapView.setLayoutParams(params);

            mapView.onCreate(null);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            final boolean useHybrid = prefs.getBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, Location.SETTING_DISPLAY_HYBRID_MAP_DEFAULT);

            IconGenerator iconGen = new IconGenerator(context);

            Drawable shapeDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_location_heatmap_marker, null);
            iconGen.setBackground(shapeDrawable);

            View view = new View(context);
            view.setLayoutParams(new ViewGroup.LayoutParams(8, 8));
            iconGen.setContentView(view);

            final Bitmap bitmap = iconGen.makeIcon();

            mapView.getMapAsync(new OnMapReadyCallback() {
                @SuppressWarnings("UnusedAssignment")
                public void onMapReady(GoogleMap googleMap) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        googleMap.setMyLocationEnabled(true);
                    }

                    if (useHybrid) {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    } else {
                        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }

                    googleMap.getUiSettings().setZoomControlsEnabled(true);
                    googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                    googleMap.getUiSettings().setMapToolbarEnabled(false);
                    googleMap.getUiSettings().setAllGesturesEnabled(false);

                    Location me = Location.getInstance(context);

                    double lastLatitude = 0.0;
                    double lastLongitude = 0.0;

                    final List<LatLng> locations = new ArrayList<>();

                    String where = Location.HISTORY_OBSERVED + " > ?";
                    String[] args = { "" + (System.currentTimeMillis() - (1000 * 60 * 60 * 24)) };

                    Cursor c = me.mDatabase.query(Location.TABLE_HISTORY, null, where, args, null, null, Location.HISTORY_OBSERVED);

                    while (c.moveToNext()) {
                        lastLatitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LATITUDE));
                        lastLongitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LONGITUDE));

                        LatLng location = new LatLng(lastLatitude, lastLongitude);

                        locations.add(location);
                    }

                    c.close();

                    LatLngBounds.Builder builder = new LatLngBounds.Builder();

                    for (LatLng latlng : locations) {
                        builder.include(latlng);
                    }

                    final DisplayMetrics metrics = new DisplayMetrics();
                    ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(metrics);

                    if (locations.size() > 0) {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), (int) (16 * metrics.density)));
                    }

                    for (LatLng latLng : locations) {
                        googleMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)));
                    }

                    mapView.onResume();
                }
            });

            return mapView;
        } else {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    @SuppressWarnings("unused")
    public static void bindDisclosureViewHolder(final GeneratorViewHolder holder) {
        TextView generatorLabel = holder.itemView.findViewById(R.id.label_generator);

        generatorLabel.setText(Location.getGeneratorTitle(holder.itemView.getContext()));
    }

    @SuppressWarnings("unused")
    @SuppressLint("TrulyRandom")
    public android.location.Location getLastKnownLocation() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        int selected = prefs.getInt(Location.ACCURACY_MODE, Location.ACCURACY_BEST);

        if (selected == Location.ACCURACY_USER) {
            double latitude = prefs.getFloat(Location.ACCURACY_MODE_USER_LOCATION_LATITUDE, 0);
            double longitude = prefs.getFloat(Location.ACCURACY_MODE_USER_LOCATION_LONGITUDE, 0);

            android.location.Location location = new android.location.Location("");
            location.setLatitude(latitude);
            location.setLongitude(longitude);

            return location;
        } else if (selected == Location.ACCURACY_DISABLED) {
            android.location.Location location = new android.location.Location("");
            location.setLatitude(41.8781);
            location.setLongitude(-87.6298);

            return location;
        }

        android.location.Location lastLocation = null;

        if (this.mDatabase != null) {
            Cursor c = this.mDatabase.query(Location.TABLE_HISTORY, null, null, null, null, null, Location.HISTORY_OBSERVED + " DESC");

            if (c.moveToNext()) {
                double latitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LATITUDE));
                double longitude = c.getDouble(c.getColumnIndex(Location.HISTORY_LONGITUDE));

                lastLocation = new android.location.Location("Passive-Data-Kit");
                lastLocation.setLatitude(latitude);
                lastLocation.setLongitude(longitude);
            }

            c.close();
        } else {
            return null;
        }

        if (lastLocation != null) {
            return lastLocation;
        }

        LocationManager locations = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        android.location.Location last = null;

        if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            last = locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (last == null) {
                last = locations.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (last != null && selected == Location.ACCURACY_RANDOMIZED) {
            // http://gis.stackexchange.com/a/68275/10230

            double latitude = last.getLatitude();
            double longitude = last.getLongitude();

            double radius = prefs.getLong(Location.ACCURACY_MODE_RANDOMIZED_RANGE, Location.ACCURACY_MODE_RANDOMIZED_RANGE_DEFAULT);

            double radiusInDegrees = radius / 111000;

            Random r = new SecureRandom();

            double u = r.nextDouble();
            double v = r.nextDouble();

            double w = radiusInDegrees * Math.sqrt(u);
            double t = 2 * Math.PI * v;
            double x = w * Math.cos(t);
            double y = w * Math.sin(t);

            // Adjust the x-coordinate for the shrinking of the east-west distances
            longitude = longitude + (x / Math.cos(latitude));
            latitude = y + latitude;

            last.setLongitude(longitude);
            last.setLatitude(latitude);
        }

        return last;
    }

    @SuppressWarnings({"SameParameterValue", "unused"})
    public void setUpdateInterval(long interval) {
        this.mUpdateInterval = interval;

        if (Location.isRunning(this.mContext)) {
            this.stopGenerator();
            this.startGenerator();
        }
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(Location.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @SuppressWarnings("unused")
    public void setAccuracyMode(int accuracyMode) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putInt(Location.ACCURACY_MODE, accuracyMode);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void setRandomizationRange(long meters) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(Location.ACCURACY_MODE_RANDOMIZED_RANGE, meters);

        e.apply();
    }

    @SuppressWarnings("unused")
    public void setPreservesRandomVector(boolean preservesVector) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(Location.ACCURACY_MODE_RANDOMIZED_VECTOR_PRESERVED, preservesVector);

        e.apply();
    }

    @Override
    protected void flushCachedData() {
        if (this.mDatabase == null) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        long retentionPeriod = prefs.getLong(Location.DATA_RETENTION_PERIOD, Location.DATA_RETENTION_PERIOD_DEFAULT);

        long start = System.currentTimeMillis() - retentionPeriod;

        String where = Location.HISTORY_OBSERVED + " < ?";
        String[] args = { "" + start };

        try {
            this.mDatabase.delete(Location.TABLE_HISTORY, where, args);
        } catch (SQLiteCantOpenDatabaseException ex) {
            // Try again later
        }
    }

    @Override
    public void setCachedDataRetentionPeriod(long period) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putLong(Location.DATA_RETENTION_PERIOD, period);

        e.apply();
    }

    @Override
    public String getIdentifier() {
        return Location.GENERATOR_IDENTIFIER;
    }

    public long storageUsed() {
        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Location.DATABASE_PATH);

        if (path.exists()) {
            return path.length();
        }

        return -1;
    }

    public boolean hasPermissions() {
        ArrayList<DiagnosticAction> actions = this.runDiagnostics();

        return (actions.size() == 0);
    }

    public void requestPermissions() {
        ArrayList<DiagnosticAction> actions = this.runDiagnostics();

        if (actions.size() > 0) {
            actions.get(0).run();
        }
    }

    public void updateConfig(JSONObject config) {
        try {
            if (config.has("update-interval")) {
                this.setUpdateInterval(config.getLong("update-interval"));

                config.remove("update-interval");
            }

            if (config.has("use-mapbox-maps")) {
                this.setUseMapboxMaps(config.getBoolean("use-mapbox-maps"));

                config.remove("use-mapbox-maps");
            }

            if (config.has("use-google-location-services")) {
                this.setUseGoogleLocationServices(config.getBoolean("use-google-location-services"));

                config.remove("use-google-location-services");
            }

            if (config.has("accuracy")) {
                String accuracy = config.getString("accuracy");

                if ("best".equals(accuracy)) {
                    this.setAccuracyMode(Location.ACCURACY_BEST);
                } else if ("randomized".equals(accuracy)) {
                    this.setAccuracyMode(Location.ACCURACY_RANDOMIZED);
                } else if ("user".equals(accuracy)) {
                    this.setAccuracyMode(Location.ACCURACY_USER);
                } else if ("disabled".equals(accuracy)) {
                    this.setAccuracyMode(Location.ACCURACY_DISABLED);
                }

                config.remove("accuracy");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
