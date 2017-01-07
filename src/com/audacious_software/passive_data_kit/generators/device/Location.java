package com.audacious_software.passive_data_kit.generators.device;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.SwitchCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
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
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.ui.IconGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class Location extends Generator implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-location";
    private static final String DATABASE_PATH = "pdk-location.sqlite";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.Location.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String USE_GOOGLE_SERVICES = "com.audacious_software.passive_data_kit.generators.device.Location.USE_GOOGLE_SERVICES";
    private static final boolean USE_GOOGLE_SERVICES_DEFAULT = true;

    private static final String ACCURACY_KEY = "accuracy";
    private static final String LATITUDE_KEY = "latitude";
    private static final String LONGITUDE_KEY = "longitude";
    private static final String FIX_TIMESTAMP_KEY = "fix-timestamp";
    private static final String PROVIDER_KEY = "provider";
    private static final String ALTITUDE_KEY = "altitude";
    private static final String BEARING_KEY = "bearing";
    private static final String SPEED_KEY = "speed";
    private static final String EXTRAS_KEY = "extras";
    private static final String SETTING_DISPLAY_HYBRID_MAP = "com.audacious_software.passive_data_kit.generators.device.Location.SETTING_DISPLAY_HYBRID_MAP";
    private static final boolean SETTING_DISPLAY_HYBRID_MAP_DEFAULT = true;

    private static Location sInstance = null;
    private GoogleApiClient mGoogleApiClient = null;
    private android.location.Location mLastLocation = null;
    private long mUpdateInterval = 60000;

    private SQLiteDatabase mDatabase = null;
    private static int DATABASE_VERSION = 1;

    private static final String TABLE_HISTORY = "history";
    private static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_LATITUDE = "latitude";
    private static final String HISTORY_LONGITUDE = "longitude";
    private static final String HISTORY_ALTITUDE = "altitude";
    private static final String HISTORY_BEARING = "bearing";
    private static final String HISTORY_SPEED = "speed";
    private static final String HISTORY_PROVIDER = "provider";
    private static final String HISTORY_LOCATION_TIMESTAMP = "location_timestamp";
    private static final String HISTORY_ACCURACY = "accuracy";

    public static Location getInstance(Context context) {
        if (Location.sInstance == null) {
            Location.sInstance = new Location(context.getApplicationContext());
        }

        return Location.sInstance;
    }

    public Location(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        Location.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final Location me = this;

        Runnable r = new Runnable()
        {

            @Override
            public void run() {
                if (Location.useKindleLocationServices())
                {
                    // TODO
                    throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
                }
                else if (Location.useGoogleLocationServices(me.mContext))
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

        Generators.getInstance(this.mContext).registerCustomViewClass(Location.GENERATOR_IDENTIFIER, Location.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Location.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_location_create_history_table));
        }

        this.setDatabaseVersion(this.mDatabase, Location.DATABASE_VERSION);
    }

    private void stopGenerator() {
        if (this.mGoogleApiClient != null) {
            this.mGoogleApiClient.disconnect();
            this.mGoogleApiClient = null;
        }

        this.mDatabase.close();
        this.mDatabase = null;
    }

    public static boolean useGoogleLocationServices(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Location.USE_GOOGLE_SERVICES, Location.USE_GOOGLE_SERVICES_DEFAULT);
    }

    public static boolean useKindleLocationServices() {
        return DeviceInformation.isKindleFire();
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Location.ENABLED, Location.ENABLED_DEFAULT);
    }

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

    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
        return Location.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
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

    @Override
    public void onConnected(Bundle bundle) {
        final LocationRequest request = new LocationRequest();
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        request.setFastestInterval(this.mUpdateInterval);
        request.setInterval(this.mUpdateInterval);

        if (this.mGoogleApiClient != null && this.mGoogleApiClient.isConnected()) {
            if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                LocationServices.FusedLocationApi.requestLocationUpdates(this.mGoogleApiClient, request, this, this.mContext.getMainLooper());
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        if (this.mGoogleApiClient != null && this.mGoogleApiClient.isConnected())
            LocationServices.FusedLocationApi.removeLocationUpdates(this.mGoogleApiClient, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        this.mGoogleApiClient = null;
    }

    @Override
    public void onLocationChanged(android.location.Location location) {
        Log.e("PDK", "LOCATION CHANGED");

        if (location == null)
            return;

        long now = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.put(Location.HISTORY_OBSERVED, System.currentTimeMillis());
        values.put(Location.HISTORY_LATITUDE, location.getLatitude());
        values.put(Location.HISTORY_LONGITUDE, location.getLongitude());
        values.put(Location.HISTORY_PROVIDER, location.getProvider());
        values.put(Location.HISTORY_LOCATION_TIMESTAMP, location.getTime());

        Bundle updated = new Bundle();
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

        this.mDatabase.insert(Location.TABLE_HISTORY, null, values);

        Generators.getInstance(this.mContext).notifyGeneratorUpdated(Location.GENERATOR_IDENTIFIER, updated);
    }

    public static long latestPointGenerated(Context context) {
        long timestamp = 0;

        Location me = Location.getInstance(context);

        Cursor c = me.mDatabase.query(Location.TABLE_HISTORY, null, null, null, null, null, Location.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(Location.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    public static void bindViewHolder(DataPointViewHolder holder) {
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

        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        if (timestamp > 0) {
            dateLabel.setText(Generator.formatTimestamp(context, timestamp / 1000));
        } else {
            dateLabel.setText(R.string.label_never_pdk);
        }

        final double finalLatitude = lastLatitude;
        final double finalLongitude = lastLongitude;

        final DisplayMetrics metrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(metrics);

        if (Location.useKindleLocationServices())
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        }
        else if (Location.useGoogleLocationServices(holder.itemView.getContext()))
        {
            final MapView mapView = (MapView) holder.itemView.findViewById(R.id.map_view);
            mapView.onCreate(null);

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            final boolean useHybrid = prefs.getBoolean(Location.SETTING_DISPLAY_HYBRID_MAP, Location.SETTING_DISPLAY_HYBRID_MAP_DEFAULT);

            SwitchCompat hybridSwitch = (SwitchCompat) holder.itemView.findViewById(R.id.pdk_google_location_map_type_hybrid);
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
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), (int) (16 * metrics.density)));
                    }

                    DisplayMetrics metrics = context.getResources().getDisplayMetrics();

                    for (LatLng latLng : locations) {
                        googleMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .icon(BitmapDescriptorFactory.fromBitmap(bitmap)));
                    }

                    mapView.onResume();
                }
            });
        }
        else
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<Bundle>();
    }

    public static View fetchView(ViewGroup parent)
    {
        if (Location.useKindleLocationServices())
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        }
        else if (Location.useGoogleLocationServices(parent.getContext()))
        {
            return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_location_google, parent, false);
        }
        else
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }
    }

    public android.location.Location getLastKnownLocation() {
        if (this.mLastLocation != null) {
            return this.mLastLocation;
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

        return last;
    }

    public void setUpdateInterval(long interval) {
        this.mUpdateInterval = interval;

        this.stopGenerator();
        this.startGenerator();
    }
}
