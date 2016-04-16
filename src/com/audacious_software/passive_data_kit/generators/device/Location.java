package com.audacious_software.passive_data_kit.generators.device;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.DeviceInformation;
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
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;

@SuppressWarnings("unused")
public class Location extends Generator implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-location";

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

    private static Location sInstance = null;
    private GoogleApiClient mGoogleApiClient = null;
    private android.location.Location mLastLocation = null;

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
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_location_permission), new Runnable() {

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

        request.setInterval(60000);

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
        if (location == null)
            return;

        long now = System.currentTimeMillis();

        Bundle bundle = new Bundle();

        bundle.putDouble(Location.LATITUDE_KEY, location.getLatitude());
        bundle.putDouble(Location.LONGITUDE_KEY, location.getLongitude());
        bundle.putDouble(Location.FIX_TIMESTAMP_KEY, ((double) location.getTime()) / 1000);
        bundle.putString(Location.PROVIDER_KEY, location.getProvider());

        this.mLastLocation = location;

        if (location.hasAccuracy()) {
            bundle.putFloat(Location.ACCURACY_KEY, location.getAccuracy());
        }

        if (location.hasAltitude()) {
            bundle.putDouble(Location.ALTITUDE_KEY, location.getAltitude());
        }

        if (location.hasBearing()) {
            bundle.putFloat(Location.BEARING_KEY, location.getBearing());
        }

        if (location.hasSpeed()) {
            bundle.putFloat(Location.SPEED_KEY, location.getSpeed());
        }

        Bundle extras = location.getExtras();

        if (extras != null) {
            bundle.putBundle(Location.EXTRAS_KEY, extras);
        }

        Generators.getInstance(this.mContext).transmitData(Location.GENERATOR_IDENTIFIER, bundle);
    }

    public static void bindViewHolder(DataPointViewHolder holder, final Bundle dataPoint) {
        final Context context = holder.itemView.getContext();

        String identifier = dataPoint.getBundle(Generator.PDK_METADATA).getString(Generator.IDENTIFIER);

        double timestamp = dataPoint.getBundle(Generator.PDK_METADATA).getDouble(Generator.TIMESTAMP);

        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        dateLabel.setText(Generator.formatTimestamp(context, timestamp));

        final double latitude = dataPoint.getDouble(Location.LATITUDE_KEY);
        final double longitude = dataPoint.getDouble(Location.LONGITUDE_KEY);

        if (Location.useKindleLocationServices())
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement Kindle support.");
        }
        else if (Location.useGoogleLocationServices(holder.itemView.getContext()))
        {
            final MapView mapView = (MapView) holder.itemView.findViewById(R.id.map_view);
            mapView.onCreate(null);

            mapView.getMapAsync(new OnMapReadyCallback() {
                public void onMapReady(GoogleMap googleMap) {
                    googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                    googleMap.getUiSettings().setZoomControlsEnabled(false);
                    googleMap.getUiSettings().setMyLocationButtonEnabled(false);

                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 14));

                    googleMap.addMarker(new MarkerOptions()
                            .position(new LatLng(latitude, longitude)));
//                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_none)));

                    DisplayMetrics metrics = context.getResources().getDisplayMetrics();

                    googleMap.setPadding(0, 0, 0, (int) (32 * metrics.density));

                    UiSettings settings = googleMap.getUiSettings();
                    settings.setMapToolbarEnabled(false);
                }
            });
        }
        else
        {
            // TODO
            throw new RuntimeException("Throw rocks at developer to implement generic location support.");
        }

        TextView description = (TextView) holder.itemView.findViewById(R.id.generator_location_description);
        description.setText(context.getResources().getString(R.string.generator_location_value, latitude, longitude));
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

        if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            Log.e("FC", "LOCATION PERMISSIONS GRANTED...");

            last = locations.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            Log.e("FC", "GPS: " + last);

            if (last == null) {
                last = locations.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                Log.e("FC", "NETWORK: " + last);
            }
        }

        return last;
    }
}
