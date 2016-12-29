package com.audacious_software.passive_data_kit.generators.wearables;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.activities.generators.MicrosoftBandAuthActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandPendingResult;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.InvalidBandVersionException;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandAltimeterEvent;
import com.microsoft.band.sensors.BandAltimeterEventListener;
import com.microsoft.band.sensors.BandAmbientLightEvent;
import com.microsoft.band.sensors.BandAmbientLightEventListener;
import com.microsoft.band.sensors.BandBarometerEvent;
import com.microsoft.band.sensors.BandBarometerEventListener;
import com.microsoft.band.sensors.BandCaloriesEvent;
import com.microsoft.band.sensors.BandCaloriesEventListener;
import com.microsoft.band.sensors.BandContactEvent;
import com.microsoft.band.sensors.BandContactEventListener;
import com.microsoft.band.sensors.BandContactState;
import com.microsoft.band.sensors.BandDistanceEvent;
import com.microsoft.band.sensors.BandDistanceEventListener;
import com.microsoft.band.sensors.BandGsrEvent;
import com.microsoft.band.sensors.BandGsrEventListener;
import com.microsoft.band.sensors.BandGyroscopeEvent;
import com.microsoft.band.sensors.BandGyroscopeEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandPedometerEvent;
import com.microsoft.band.sensors.BandPedometerEventListener;
import com.microsoft.band.sensors.BandRRIntervalEvent;
import com.microsoft.band.sensors.BandRRIntervalEventListener;
import com.microsoft.band.sensors.BandSensorManager;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;
import com.microsoft.band.sensors.BandUVEvent;
import com.microsoft.band.sensors.BandUVEventListener;
import com.microsoft.band.sensors.HeartRateQuality;
import com.microsoft.band.sensors.MotionType;
import com.microsoft.band.sensors.SampleRate;
import com.microsoft.band.sensors.UVIndexLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class MicrosoftBand extends Generator
{
    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;
    private static final String GENERATOR_IDENTIFIER = "pdk-microsoft-band";

    private static final String TIMESTAMP_KEY = "timestamps";
    private static final String X_KEY = "xs";
    private static final String Y_KEY = "ys";
    private static final String Z_KEY = "zs";
    private static final String CALORIES_KEY = "calories";
    private static final String CONTACT_STATE_KEY = "states";
    private static final String PACE_KEY = "paces";
    private static final String SPEED_KEY = "speeds";
    private static final String TOTAL_DISTANCE_KEY = "total-distances";
    private static final String MOTION_TYPE_KEY = "motion-type";
    private static final String LOCKED_ON_KEY = "locked-on";
    private static final String HEART_RATE_KEY = "heart-rate";
    private static final String TOTAL_STEPS_KEY = "total-steps";
    private static final String TODAY_STEPS_KEY = "today-steps";
    private static final String TEMPERATURE_KEY = "temperature";
    private static final String EXPOSURE_KEY = "exposure";
    private static final String INDEX_LEVEL_KEY = "index-level";
    private static final String FLIGHTS_ASCENDED_KEY = "flights-ascended";
    private static final String FLIGHTS_ASCENDED_TODAY_KEY = "flights-ascended-today";
    private static final String FLIGHTS_DESCENDED_KEY = "flights-descended";
    private static final String STEPPING_GAIN_KEY = "stepping-gain";
    private static final String STEPPING_LOSS_KEY = "stepping-loss";
    private static final String STEPS_ASCENDED_KEY = "steps-ascended";
    private static final String STEPS_DESCENDED_KEY = "steps-descended";
    private static final String TOTAL_GAIN_KEY = "total-gain";
    private static final String TOTAL_GAIN_TODAY_KEY = "total-gain-today";
    private static final String TOTAL_LOSS_KEY = "total-loss";
    private static final String RATE_KEY = "rate";
    private static final String BRIGHTNESS_KEY = "brightness";
    private static final String PRESSURE_KEY = "pressure";
    private static final String RESISTANCE_KEY = "resistance";
    private static final String INTERVAL_KEY = "interval";

    private static String ACCELEROMETER_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.ACCELEROMETER_ENABLED";
    private static final boolean ACCELEROMETER_ENABLED_DEFAULT = true;
    private static String HEART_RATE_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.HEART_RATE_ENABLED";
    private static final boolean HEART_RATE_ENABLED_ENABLED_DEFAULT = true;
    private static String ALTIMETER_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.ALTIMETER_ENABLED";
    private static final boolean ALTIMETER_ENABLED_DEFAULT = true;
    private static String AMBIENT_LIGHT_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.AMBIENT_LIGHT_ENABLED";
    private static final boolean AMBIENT_LIGHT_ENABLED_DEFAULT = true;
    private static String BAROMETER_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.BAROMETER_ENABLED";
    private static final boolean BAROMETER_ENABLED_DEFAULT = true;
    private static String CALORIES_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.CALORIES_ENABLED";
    private static final boolean CALORIES_ENABLED_DEFAULT = true;
    private static String CONTACT_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.CONTACT_ENABLED";
    private static final boolean CONTACT_ENABLED_DEFAULT = true;
    private static String DISTANCE_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.DISTANCE_ENABLED";
    private static final boolean DISTANCE_ENABLED_DEFAULT = true;
    private static String GALVANIC_SKIN_RESPONSE_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.GALVANIC_SKIN_RESPONSE_ENABLED";
    private static final boolean GALVANIC_SKIN_RESPONSE_ENABLED_DEFAULT = true;
    private static String GYROSCOPE_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.GYROSCOPE_ENABLED";
    private static final boolean GYROSCOPE_ENABLED_DEFAULT = true;
    private static String PEDOMETER_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.PEDOMETER_ENABLED";
    private static final boolean PEDOMETER_ENABLED_DEFAULT = true;
    private static String HEART_RATE_VARIABILITY_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.HEART_RATE_VARIABILITY_ENABLED";
    private static final boolean HEART_RATE_VARIABILITY_ENABLED_DEFAULT = true;
    private static String SKIN_TEMPERATURE_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.SKIN_TEMPERATURE_ENABLED";
    private static final boolean SKIN_TEMPERATURE_ENABLED_DEFAULT = true;
    private static String ULTRAVIOLET_LIGHT_ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.ULTRAVIOLET_LIGHT_ENABLED";
    private static final boolean ULTRAVIOLET_LIGHT_ENABLED_DEFAULT = true;


    private static final long TRANSMISSION_INTERVAL = 30000;

    private static MicrosoftBand sInstance = null;

    private BandClient mBandClient = null;
    private long mLastTransmission = 0;

    private ArrayList<MicrosoftBand.AccelerometerDataPoint> mAccelerometerDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.AltimiterDataPoint> mAltimiterDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.AmbientLightDataPoint> mAmbientLightDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.BarometerDataPoint> mBarometerDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.CaloriesDataPoint> mCaloriesDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.ContactDataPoint> mContactDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.DistanceDataPoint> mDistanceDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.GalvanicSkinResponseDataPoint> mGalvanicSkinResponseDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.GyroscopeDataPoint> mGyroscopeDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.PedometerDataPoint> mPedometerDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.HeartRateDataPoint> mHeartRateDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.HeartRateVariabilityDataPoint> mHeartRateVariabilityDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.SkinTemperatureDataPoint> mSkinTemperatureDataPoints = new ArrayList<>();
    private ArrayList<MicrosoftBand.UltravioletLightDataPoint> mUltravioletLightDataPoints = new ArrayList<>();

    public static MicrosoftBand getInstance(Context context) {
        if (MicrosoftBand.sInstance == null) {
            MicrosoftBand.sInstance = new MicrosoftBand(context.getApplicationContext());
        }

        return MicrosoftBand.sInstance;
    }

    public MicrosoftBand(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        MicrosoftBand.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final MicrosoftBand me = this;

        Runnable r = new Runnable()
        {
            @Override
            public void run() {
                BandInfo[] pairedBands = BandClientManager.getInstance().getPairedBands();

                if (pairedBands.length > 0) {
                    me.mBandClient = BandClientManager.getInstance().create(me.mContext, pairedBands[0]);

                    BandPendingResult<ConnectionState> pendingResult = me.mBandClient.connect();

                    try {
                        ConnectionState state = pendingResult.await();


                        if (state == ConnectionState.CONNECTED) {
                            final BandSensorManager sensors = me.mBandClient.getSensorManager();

                            try {
                                if (me.canAccessSensor(sensors, MicrosoftBand.AccelerometerDataPoint.class)) {
                                    sensors.registerAccelerometerEventListener(new BandAccelerometerEventListener() {
                                        @Override
                                        public void onBandAccelerometerChanged(BandAccelerometerEvent event) {
                                            me.mAccelerometerDataPoints.add(new MicrosoftBand.AccelerometerDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    }, SampleRate.MS128);
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.CaloriesDataPoint.class)) {
                                    sensors.registerCaloriesEventListener(new BandCaloriesEventListener() {
                                        @Override
                                        public void onBandCaloriesChanged(BandCaloriesEvent event) {
                                            me.mCaloriesDataPoints.add(new MicrosoftBand.CaloriesDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    });
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.ContactDataPoint.class)) {
                                    sensors.registerContactEventListener(new BandContactEventListener() {
                                        @Override
                                        public void onBandContactChanged(BandContactEvent event) {
                                            me.mContactDataPoints.add(new MicrosoftBand.ContactDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    });
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.GyroscopeDataPoint.class)) {
                                    sensors.registerGyroscopeEventListener(new BandGyroscopeEventListener() {
                                        @Override
                                        public void onBandGyroscopeChanged(BandGyroscopeEvent event) {
                                            me.mGyroscopeDataPoints.add(new MicrosoftBand.GyroscopeDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    }, SampleRate.MS128);
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.DistanceDataPoint.class)) {
                                    sensors.registerDistanceEventListener(new BandDistanceEventListener() {
                                        @Override
                                        public void onBandDistanceChanged(BandDistanceEvent event) {
                                            me.mDistanceDataPoints.add(new MicrosoftBand.DistanceDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    });
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.HeartRateDataPoint.class)) {
                                    sensors.registerHeartRateEventListener(new BandHeartRateEventListener() {
                                        @Override
                                        public void onBandHeartRateChanged(BandHeartRateEvent event) {
                                            me.mHeartRateDataPoints.add(new MicrosoftBand.HeartRateDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    });
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.PedometerDataPoint.class)) {
                                    sensors.registerPedometerEventListener(new BandPedometerEventListener() {
                                        @Override
                                        public void onBandPedometerChanged(BandPedometerEvent event) {
                                            me.mPedometerDataPoints.add(new MicrosoftBand.PedometerDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    });
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.SkinTemperatureDataPoint.class)) {
                                    sensors.registerSkinTemperatureEventListener(new BandSkinTemperatureEventListener() {
                                        @Override
                                        public void onBandSkinTemperatureChanged(BandSkinTemperatureEvent event) {
                                            me.mSkinTemperatureDataPoints.add(new MicrosoftBand.SkinTemperatureDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    });
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.UltravioletLightDataPoint.class)) {
                                    sensors.registerUVEventListener(new BandUVEventListener() {
                                        @Override
                                        public void onBandUVChanged(BandUVEvent event) {
                                            me.mUltravioletLightDataPoints.add(new MicrosoftBand.UltravioletLightDataPoint(event));

                                            me.transmitData(sensors);
                                        }
                                    });
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.AltimiterDataPoint.class)) {
                                    try {
                                        sensors.registerAltimeterEventListener(new BandAltimeterEventListener() {
                                            @Override
                                            public void onBandAltimeterChanged(BandAltimeterEvent event) {
                                                me.mAltimiterDataPoints.add(new MicrosoftBand.AltimiterDataPoint(event));

                                                me.transmitData(sensors);
                                            }
                                        });
                                    } catch (InvalidBandVersionException e) {
                                        Logger.getInstance(me.mContext).logThrowable(e);
                                    }
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.AmbientLightDataPoint.class)) {
                                    try {
                                        sensors.registerAmbientLightEventListener(new BandAmbientLightEventListener() {
                                            @Override
                                            public void onBandAmbientLightChanged(BandAmbientLightEvent event) {
                                                me.mAmbientLightDataPoints.add(new MicrosoftBand.AmbientLightDataPoint(event));

                                                me.transmitData(sensors);
                                            }
                                        });
                                    } catch (InvalidBandVersionException e) {
                                        Logger.getInstance(me.mContext).logThrowable(e);
                                    }
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.BarometerDataPoint.class)) {
                                    try {
                                        sensors.registerBarometerEventListener(new BandBarometerEventListener() {
                                            @Override
                                            public void onBandBarometerChanged(BandBarometerEvent event) {
                                                me.mBarometerDataPoints.add(new MicrosoftBand.BarometerDataPoint(event));

                                                me.transmitData(sensors);
                                            }
                                        });
                                    } catch (InvalidBandVersionException e) {
                                        Logger.getInstance(me.mContext).logThrowable(e);
                                    }
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.GalvanicSkinResponseDataPoint.class)) {
                                    try {
                                        sensors.registerGsrEventListener(new BandGsrEventListener() {
                                            @Override
                                            public void onBandGsrChanged(BandGsrEvent event) {
                                                me.mGalvanicSkinResponseDataPoints.add(new MicrosoftBand.GalvanicSkinResponseDataPoint(event));

                                                me.transmitData(sensors);
                                            }
                                        });
                                    } catch (InvalidBandVersionException e) {
                                        Logger.getInstance(me.mContext).logThrowable(e);
                                    }
                                }

                                if (me.canAccessSensor(sensors, MicrosoftBand.HeartRateVariabilityDataPoint.class)) {
                                    try {
                                        sensors.registerRRIntervalEventListener(new BandRRIntervalEventListener() {
                                            @Override
                                            public void onBandRRIntervalChanged(BandRRIntervalEvent event) {
                                                me.mHeartRateVariabilityDataPoints.add(new MicrosoftBand.HeartRateVariabilityDataPoint(event));

                                                me.transmitData(sensors);
                                            }
                                        });
                                    } catch (InvalidBandVersionException e) {
                                        Logger.getInstance(me.mContext).logThrowable(e);
                                    }
                                }
                            } catch(BandIOException e) {
                                Logger.getInstance(me.mContext).logThrowable(e);
                            }

                        } else {
                            // do work on failure
                        }
                    } catch(InterruptedException|BandException e) {
                        Logger.getInstance(me.mContext).logThrowable(e);
                    }
                }
                else
                {
                    // Log error about being unable to connect to band...
                }
            }
        };

        Thread t = new Thread(r);
        t.start();

        Generators.getInstance(this.mContext).registerCustomViewClass(MicrosoftBand.GENERATOR_IDENTIFIER, MicrosoftBand.class);
    }

    private boolean canAccessSensor(BandSensorManager sensors, Class sensorClass) {
        SharedPreferences prefs = Generators.getInstance(this.mContext).getSharedPreferences(this.mContext);

        if (sensorClass == AccelerometerDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.ACCELEROMETER_ENABLED, MicrosoftBand.ACCELEROMETER_ENABLED_DEFAULT);
        }
        else if (sensorClass == AltimiterDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.ALTIMETER_ENABLED, MicrosoftBand.ALTIMETER_ENABLED_DEFAULT);
        }
        else if (sensorClass == AmbientLightDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.AMBIENT_LIGHT_ENABLED, MicrosoftBand.AMBIENT_LIGHT_ENABLED_DEFAULT);
        }
        else if (sensorClass == BarometerDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.BAROMETER_ENABLED, MicrosoftBand.BAROMETER_ENABLED_DEFAULT);
        }
        else if (sensorClass == CaloriesDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.CALORIES_ENABLED, MicrosoftBand.CALORIES_ENABLED_DEFAULT);
        }
        else if (sensorClass == ContactDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.CONTACT_ENABLED, MicrosoftBand.CONTACT_ENABLED_DEFAULT);
        }
        else if (sensorClass == DistanceDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.DISTANCE_ENABLED, MicrosoftBand.DISTANCE_ENABLED_DEFAULT);
        }
        else if (sensorClass == GalvanicSkinResponseDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.GALVANIC_SKIN_RESPONSE_ENABLED, MicrosoftBand.GALVANIC_SKIN_RESPONSE_ENABLED_DEFAULT);
        }
        else if (sensorClass == GyroscopeDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.GYROSCOPE_ENABLED, MicrosoftBand.GYROSCOPE_ENABLED_DEFAULT);
        }
        else if (sensorClass == PedometerDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.PEDOMETER_ENABLED, MicrosoftBand.PEDOMETER_ENABLED_DEFAULT);
        }
        else if (sensorClass == HeartRateDataPoint.class) {
            if (prefs.getBoolean(MicrosoftBand.HEART_RATE_ENABLED, MicrosoftBand.HEART_RATE_ENABLED_ENABLED_DEFAULT)) {
                return sensors.getCurrentHeartRateConsent() == UserConsent.GRANTED;
            }
        }
        else if (sensorClass == HeartRateVariabilityDataPoint.class) {
            if (prefs.getBoolean(MicrosoftBand.HEART_RATE_VARIABILITY_ENABLED, MicrosoftBand.HEART_RATE_VARIABILITY_ENABLED_DEFAULT)) {
                return sensors.getCurrentHeartRateConsent() == UserConsent.GRANTED;
            }
        }
        else if (sensorClass == SkinTemperatureDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.SKIN_TEMPERATURE_ENABLED, MicrosoftBand.SKIN_TEMPERATURE_ENABLED_DEFAULT);
        }
        else if (sensorClass == UltravioletLightDataPoint.class) {
            return prefs.getBoolean(MicrosoftBand.ULTRAVIOLET_LIGHT_ENABLED, MicrosoftBand.ULTRAVIOLET_LIGHT_ENABLED_DEFAULT);
        }

        return false;
    }

    private void transmitData(BandSensorManager sensors) {
        long now = System.currentTimeMillis();

        if (now - this.mLastTransmission < MicrosoftBand.TRANSMISSION_INTERVAL) {
            return;
        }

        this.mLastTransmission = now;

        Bundle bundle = new Bundle();

        if (this.canAccessSensor(sensors, MicrosoftBand.AccelerometerDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.AccelerometerDataPoint.bundleForPoints(this.mAccelerometerDataPoints);
            bundle.putBundle(MicrosoftBand.AccelerometerDataPoint.KEY, sensorBundle);

            this.mAccelerometerDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.CaloriesDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.CaloriesDataPoint.bundleForPoints(this.mCaloriesDataPoints);
            bundle.putBundle(MicrosoftBand.CaloriesDataPoint.KEY, sensorBundle);

            this.mCaloriesDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.ContactDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.ContactDataPoint.bundleForPoints(this.mContactDataPoints);
            bundle.putBundle(MicrosoftBand.ContactDataPoint.KEY, sensorBundle);
            this.mContactDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.GyroscopeDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.GyroscopeDataPoint.bundleForPoints(this.mGyroscopeDataPoints);
            bundle.putBundle(MicrosoftBand.GyroscopeDataPoint.KEY, sensorBundle);
            this.mGyroscopeDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.DistanceDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.DistanceDataPoint.bundleForPoints(this.mDistanceDataPoints);
            bundle.putBundle(MicrosoftBand.DistanceDataPoint.KEY, sensorBundle);
            this.mDistanceDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.HeartRateDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.HeartRateDataPoint.bundleForPoints(this.mHeartRateDataPoints);
            bundle.putBundle(MicrosoftBand.HeartRateDataPoint.KEY, sensorBundle);
            this.mHeartRateDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.PedometerDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.PedometerDataPoint.bundleForPoints(this.mPedometerDataPoints);
            bundle.putBundle(MicrosoftBand.PedometerDataPoint.KEY, sensorBundle);
            this.mPedometerDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.SkinTemperatureDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.SkinTemperatureDataPoint.bundleForPoints(this.mSkinTemperatureDataPoints);
            bundle.putBundle(MicrosoftBand.SkinTemperatureDataPoint.KEY, sensorBundle);
            this.mSkinTemperatureDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.UltravioletLightDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.UltravioletLightDataPoint.bundleForPoints(this.mUltravioletLightDataPoints);
            bundle.putBundle(MicrosoftBand.UltravioletLightDataPoint.KEY, sensorBundle);
            this.mUltravioletLightDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.AltimiterDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.AltimiterDataPoint.bundleForPoints(this.mAltimiterDataPoints);
            bundle.putBundle(MicrosoftBand.AltimiterDataPoint.KEY, sensorBundle);
            this.mAltimiterDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.AmbientLightDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.AmbientLightDataPoint.bundleForPoints(this.mAmbientLightDataPoints);
            bundle.putBundle(MicrosoftBand.AmbientLightDataPoint.KEY, sensorBundle);
            this.mAmbientLightDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.BarometerDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.BarometerDataPoint.bundleForPoints(this.mBarometerDataPoints);
            bundle.putBundle(MicrosoftBand.BarometerDataPoint.KEY, sensorBundle);
            this.mBarometerDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.GalvanicSkinResponseDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.GalvanicSkinResponseDataPoint.bundleForPoints(this.mGalvanicSkinResponseDataPoints);
            bundle.putBundle(MicrosoftBand.GalvanicSkinResponseDataPoint.KEY, sensorBundle);
            this.mGalvanicSkinResponseDataPoints.clear();
        }

        if (this.canAccessSensor(sensors, MicrosoftBand.HeartRateVariabilityDataPoint.class)) {
            Bundle sensorBundle = MicrosoftBand.HeartRateVariabilityDataPoint.bundleForPoints(this.mHeartRateVariabilityDataPoints);
            bundle.putBundle(MicrosoftBand.HeartRateVariabilityDataPoint.KEY, sensorBundle);
            this.mHeartRateVariabilityDataPoints.clear();
        }

//        Generators.getInstance(this.mContext).transmitData(MicrosoftBand.GENERATOR_IDENTIFIER, bundle);
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
        return MicrosoftBand.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        final Handler handler = new Handler(Looper.getMainLooper());

        if (MicrosoftBand.isEnabled(this.mContext)) {
            final MicrosoftBand me = this;

            if (MicrosoftBand.sInstance.mBandClient == null) {
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_msft_band_client_title), me.mContext.getString(R.string.diagnostic_missing_msft_band_client), new Runnable() {

                    @Override
                    public void run() {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(me.mContext, "UnAbLe to CONNect TO Band. Verify BAnd iS nearBy. (1)", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }));
            } else if (!MicrosoftBand.sInstance.mBandClient.isConnected()) {
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_msft_band_client_title), me.mContext.getString(R.string.diagnostic_missing_msft_band_client), new Runnable() {

                    @Override
                    public void run() {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(me.mContext, "UnAbLe to CONNect TO Band. Verify BAnd iS nearBy. (2)", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }));
            }
            else
            {
                BandSensorManager sensors = this.mBandClient.getSensorManager();

                if (this.canAccessSensor(sensors, MicrosoftBand.HeartRateDataPoint.class) ||
                        this.canAccessSensor(sensors, MicrosoftBand.HeartRateVariabilityDataPoint.class)) {
                    if (sensors.getCurrentHeartRateConsent() != UserConsent.GRANTED) {
                        actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_msft_band_auth_title), me.mContext.getString(R.string.diagnostic_missing_msft_band_auth), new Runnable() {

                            @Override
                            public void run() {
                                Intent authIntent = new Intent(me.mContext, MicrosoftBandAuthActivity.class);
                                authIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                me.mContext.startActivity(authIntent);
                            }
                        }));
                    }
                }
            }
        }

        return actions;
    }

    public static void stop(Context context) {
        if (MicrosoftBand.sInstance != null) {
            if (MicrosoftBand.sInstance.mBandClient != null) {
                MicrosoftBand.sInstance.mLastTransmission = 0;
                MicrosoftBand.sInstance.transmitData(MicrosoftBand.sInstance.mBandClient.getSensorManager());

                if (MicrosoftBand.sInstance.mBandClient.isConnected()) {
                    MicrosoftBand.sInstance.mBandClient.disconnect();
                }

                MicrosoftBand.sInstance.mBandClient = null;
            }
        }
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(MicrosoftBand.ENABLED, MicrosoftBand.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (MicrosoftBand.sInstance == null) {
            return false;
        }

        return MicrosoftBand.sInstance.mBandClient != null;
    }

    private static class DataPoint implements Comparable<DataPoint>
    {
        protected long mTimestamp = -1;

        @Override
        public int compareTo(DataPoint another) {
            if (this.mTimestamp < another.mTimestamp)
            {
                return -1;
            }
            else if (this.mTimestamp > another.mTimestamp)
            {
                return 1;
            }

            return 0;
        }
    }

    private static class HeartRateDataPoint extends DataPoint {
        public static final String KEY = "heart-rate";

        private boolean mLocked = false;
        private int mHeartRate = -1;

        public HeartRateDataPoint(BandHeartRateEvent event) {
            this.mHeartRate = event.getHeartRate();
            this.mLocked = event.getQuality() == HeartRateQuality.LOCKED;
            this.mTimestamp = event.getTimestamp();
        }

        public static Bundle bundleForPoints(ArrayList<HeartRateDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            boolean[] locks = new boolean[size];
            int[] rates = new int[size];

            for (int i = 0; i < size; i++)
            {
                HeartRateDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                locks[i] = dataPoint.mLocked;
                rates[i] = dataPoint.mHeartRate;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putBooleanArray(MicrosoftBand.LOCKED_ON_KEY, locks);
            bundle.putIntArray(MicrosoftBand.HEART_RATE_KEY, rates);

            return bundle;
        }
    }

    private static class AccelerometerDataPoint extends DataPoint {
        public static final String KEY = "accelerometer";

        private float mX = 0;
        private float mY = 0;
        private float mZ = 0;

        public AccelerometerDataPoint(BandAccelerometerEvent event) {
            this.mX = event.getAccelerationX();
            this.mY = event.getAccelerationY();
            this.mZ = event.getAccelerationZ();
            this.mTimestamp = event.getTimestamp();
        }

        public static Bundle bundleForPoints(ArrayList<AccelerometerDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            float[] xs = new float[size];
            float[] ys = new float[size];
            float[] zs = new float[size];

            for (int i = 0; i < size; i++)
            {
                AccelerometerDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                xs[i] = dataPoint.mX;
                ys[i] = dataPoint.mY;
                zs[i] = dataPoint.mZ;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putFloatArray(MicrosoftBand.X_KEY, xs);
            bundle.putFloatArray(MicrosoftBand.Y_KEY, ys);
            bundle.putFloatArray(MicrosoftBand.Z_KEY, zs);

            return bundle;
        }
    }

    private static class AltimiterDataPoint extends DataPoint {
        public static final String KEY = "altimeter";

        private long mFlightsAscended = -1;
        private long mFlightsAscendedToday = -1;
        private long mFlightsDescended = -1;
        private float mRate = -1;
        private long mSteppingGain = -1;
        private long mSteppingLoss = -1;
        private long mStepsAscended = -1;
        private long mStepsDescended = -1;
        private long mTotalGain = -1;
        private long mTotalGainToday = -1;
        private long mTotalLoss = -1;

        public AltimiterDataPoint(BandAltimeterEvent event) {
            this.mTimestamp = event.getTimestamp();

            this.mFlightsAscended = event.getFlightsAscended();
            this.mFlightsDescended = event.getFlightsDescended();
            this.mRate = event.getRate();
            this.mSteppingGain = event.getSteppingGain();
            this.mSteppingLoss = event.getSteppingLoss();
            this.mStepsAscended = event.getStepsAscended();
            this.mStepsDescended = event.getStepsDescended();
            this.mTotalGain = event.getTotalGain();
            this.mTotalLoss = event.getTotalLoss();

            try {
                this.mFlightsAscendedToday = event.getFlightsAscendedToday();
                this.mTotalGainToday = event.getTotalGainToday();
            } catch (InvalidBandVersionException e) {
                Logger.getInstance(null).logThrowable(e);
            }
        }

        public static Bundle bundleForPoints(ArrayList<AltimiterDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];

            long[] fas = new long[size];
            long[] fats = new long[size];
            long[] fds = new long[size];
            long[] sgs = new long[size];
            long[] sls = new long[size];
            long[] sas = new long[size];
            long[] sds = new long[size];
            long[] tgs = new long[size];
            long[] tgts = new long[size];
            long[] tls = new long[size];

            float[] rs = new float[size];

            for (int i = 0; i < size; i++)
            {
                AltimiterDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                fas[i] = dataPoint.mFlightsAscended;
                fats[i] = dataPoint.mFlightsAscendedToday;
                fds[i] = dataPoint.mFlightsDescended;
                sgs[i] = dataPoint.mSteppingGain;
                sls[i] = dataPoint.mSteppingLoss;
                sas[i] = dataPoint.mStepsAscended;
                sds[i] = dataPoint.mStepsDescended;
                tgs[i] = dataPoint.mTotalGain;
                tgts[i] = dataPoint.mTotalGainToday;
                tls[i] = dataPoint.mTotalLoss;

                rs[i] = dataPoint.mRate;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putLongArray(MicrosoftBand.FLIGHTS_ASCENDED_KEY, fas);
            bundle.putLongArray(MicrosoftBand.FLIGHTS_ASCENDED_TODAY_KEY, fats);
            bundle.putLongArray(MicrosoftBand.FLIGHTS_DESCENDED_KEY, fds);
            bundle.putLongArray(MicrosoftBand.STEPPING_GAIN_KEY, sgs);
            bundle.putLongArray(MicrosoftBand.STEPPING_LOSS_KEY, sls);
            bundle.putLongArray(MicrosoftBand.STEPS_ASCENDED_KEY, sas);
            bundle.putLongArray(MicrosoftBand.STEPS_DESCENDED_KEY, sds);
            bundle.putLongArray(MicrosoftBand.TOTAL_GAIN_KEY, tgs);
            bundle.putLongArray(MicrosoftBand.TOTAL_GAIN_TODAY_KEY, tgts);
            bundle.putLongArray(MicrosoftBand.TOTAL_LOSS_KEY, tls);
            bundle.putFloatArray(MicrosoftBand.RATE_KEY, rs);

            return bundle;
        }
    }

    private static class AmbientLightDataPoint extends DataPoint {
        public static final String KEY = "ambient-light";

        private int mBrightness = -1;

        public AmbientLightDataPoint(BandAmbientLightEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mBrightness = event.getBrightness();
        }

        public static Bundle bundleForPoints(ArrayList<AmbientLightDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            int[] bs = new int[size];

            for (int i = 0; i < size; i++)
            {
                AmbientLightDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                bs[i] = dataPoint.mBrightness;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putIntArray(MicrosoftBand.BRIGHTNESS_KEY, bs);

            return bundle;
        }
    }

    private static class BarometerDataPoint extends DataPoint {
        public static final String KEY = "barometer";
        private double mAirPressure = -1;
        private double mTemperature = 0;

        public BarometerDataPoint(BandBarometerEvent event) {
            this.mTimestamp = event.getTimestamp();

            this.mAirPressure = event.getAirPressure();
            this.mTemperature = event.getTemperature();
        }

        public static Bundle bundleForPoints(ArrayList<BarometerDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            double[] tmps = new double[size];
            double[] ps = new double[size];

            for (int i = 0; i < size; i++)
            {
                BarometerDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                tmps[i]= dataPoint.mTemperature;
                ps[i] = dataPoint.mAirPressure;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putDoubleArray(MicrosoftBand.TEMPERATURE_KEY, tmps);
            bundle.putDoubleArray(MicrosoftBand.PRESSURE_KEY, ps);

            return bundle;
        }
    }

    private static class CaloriesDataPoint extends DataPoint {
        public static final String KEY = "calories";

        private long mCalories = -1;

        public CaloriesDataPoint(BandCaloriesEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mCalories = event.getCalories();
        }

        public static Bundle bundleForPoints(ArrayList<CaloriesDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            long[] calories = new long[size];

            for (int i = 0; i < size; i++)
            {
                CaloriesDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                calories[i] = dataPoint.mCalories;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putLongArray(MicrosoftBand.CALORIES_KEY, calories);

            return bundle;
        }
    }

    private static class ContactDataPoint extends DataPoint {
        public static final String KEY = "contacts";

        private BandContactState mState = BandContactState.UNKNOWN;

        public ContactDataPoint(BandContactEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mState = event.getContactState();
        }

        public static Bundle bundleForPoints(ArrayList<ContactDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            short[] states = new short[size];

            for (int i = 0; i < size; i++)
            {
                ContactDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;

                if (dataPoint.mState == BandContactState.NOT_WORN)
                    states[i] = 0;
                else if (dataPoint.mState == BandContactState.WORN)
                    states[i] = 1;
                else
                    states[i] = -1;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putShortArray(MicrosoftBand.CONTACT_STATE_KEY, states);

            return bundle;
        }
    }

    private static class DistanceDataPoint extends DataPoint {
        public static final String KEY = "distances";

        private MotionType mMotionType = MotionType.UNKNOWN;
        private float mPace = -1;
        private float mSpeed = -1;
        private long mTotalDistance = -1;

        public DistanceDataPoint(BandDistanceEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mMotionType = event.getMotionType();
            this.mPace = event.getPace();
            this.mSpeed = event.getSpeed();
            this.mTotalDistance = event.getTotalDistance();
        }

        public static Bundle bundleForPoints(ArrayList<DistanceDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            String[] motions = new String[size];
            float[] paces = new float[size];
            float[] speeds = new float[size];
            long[] distances = new long[size];

            for (int i = 0; i < size; i++)
            {
                DistanceDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                motions[i] = dataPoint.mMotionType.toString();
                paces[i] = dataPoint.mPace;
                speeds[i] = dataPoint.mSpeed;
                distances[i] = dataPoint.mTotalDistance;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putFloatArray(MicrosoftBand.PACE_KEY, paces);
            bundle.putFloatArray(MicrosoftBand.SPEED_KEY, speeds);
            bundle.putLongArray(MicrosoftBand.TOTAL_DISTANCE_KEY, distances);
            bundle.putStringArray(MicrosoftBand.MOTION_TYPE_KEY, motions);

            return bundle;
        }
    }

    private static class GalvanicSkinResponseDataPoint extends DataPoint{
        public static final String KEY = "galvanic-skin-response";
        private int mResistance = -1;

        public GalvanicSkinResponseDataPoint(BandGsrEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mResistance = event.getResistance();
        }

        public static Bundle bundleForPoints(ArrayList<GalvanicSkinResponseDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            int[] rs = new int[size];

            for (int i = 0; i < size; i++)
            {
                GalvanicSkinResponseDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                rs[i] = dataPoint.mResistance;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putIntArray(MicrosoftBand.RESISTANCE_KEY, rs);

            return bundle;
        }
    }

    private static class GyroscopeDataPoint extends DataPoint {
        public static final String KEY = "gyroscope";
        private float mX = 0;
        private float mY = 0;
        private float mZ = 0;

        public GyroscopeDataPoint(BandGyroscopeEvent event) {
            this.mTimestamp = event.getTimestamp();

            this.mX = event.getAngularVelocityX();
            this.mY = event.getAngularVelocityY();
            this.mZ = event.getAngularVelocityZ();

        }

        public static Bundle bundleForPoints(ArrayList<GyroscopeDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            float[] xs = new float[size];
            float[] ys = new float[size];
            float[] zs = new float[size];

            for (int i = 0; i < size; i++)
            {
                GyroscopeDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                xs[i] = dataPoint.mX;
                ys[i] = dataPoint.mY;
                zs[i] = dataPoint.mZ;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putFloatArray(MicrosoftBand.X_KEY, xs);
            bundle.putFloatArray(MicrosoftBand.Y_KEY, ys);
            bundle.putFloatArray(MicrosoftBand.Z_KEY, zs);

            return bundle;
        }
    }

    private static class PedometerDataPoint extends DataPoint {
        public static final String KEY = "pedometer";

        private long mStepsToday = -1;
        private long mTotalSteps = -1;

        public PedometerDataPoint(BandPedometerEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mTotalSteps = event.getTotalSteps();

            try {
                this.mStepsToday = event.getStepsToday();
            } catch (InvalidBandVersionException e) {
                Logger.getInstance(null).logThrowable(e);
            }
        }

        public static Bundle bundleForPoints(ArrayList<PedometerDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            long[] totals = new long[size];
            long[] todays = new long[size];

            for (int i = 0; i < size; i++)
            {
                PedometerDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                totals[i] = dataPoint.mTotalSteps;
                todays[i] = dataPoint.mStepsToday;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putLongArray(MicrosoftBand.TOTAL_STEPS_KEY, totals);
            bundle.putLongArray(MicrosoftBand.TODAY_STEPS_KEY, todays);

            return bundle;
        }
    }

    private static class HeartRateVariabilityDataPoint extends DataPoint{
        public static final String KEY = "heart-rate-variability";

        private double mInterval = -1;

        public HeartRateVariabilityDataPoint(BandRRIntervalEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mInterval = event.getInterval();
        }

        public static Bundle bundleForPoints(ArrayList<HeartRateVariabilityDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            double[] is = new double[size];

            for (int i = 0; i < size; i++)
            {
                HeartRateVariabilityDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                is[i] = dataPoint.mInterval;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putDoubleArray(MicrosoftBand.INTERVAL_KEY, is);

            return bundle;
        }
    }

    private static class SkinTemperatureDataPoint extends DataPoint {
        public static final String KEY = "skin-temperature";

        private float mTemperature = 0;

        public SkinTemperatureDataPoint(BandSkinTemperatureEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mTemperature = event.getTemperature();
        }

        public static Bundle bundleForPoints(ArrayList<SkinTemperatureDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            float[] temperatures = new float[size];

            for (int i = 0; i < size; i++)
            {
                SkinTemperatureDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                temperatures[i] = dataPoint.mTemperature;
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putFloatArray(MicrosoftBand.TEMPERATURE_KEY, temperatures);

            return bundle;
        }
    }

    private static class UltravioletLightDataPoint extends DataPoint {
        public static final String KEY = "ultraviolet-light";

        private long mExposure = -1;
        private UVIndexLevel mIndexLevel = UVIndexLevel.NONE;

        public UltravioletLightDataPoint(BandUVEvent event) {
            this.mTimestamp = event.getTimestamp();
            this.mIndexLevel = event.getUVIndexLevel();

            try {
                this.mExposure = event.getUVExposureToday();
            } catch (InvalidBandVersionException e) {
                Logger.getInstance(null).logThrowable(e);
            }
        }

        public static Bundle bundleForPoints(ArrayList<UltravioletLightDataPoint> dataPoints) {
            Bundle bundle = new Bundle();

            Collections.sort(dataPoints);

            int size = dataPoints.size();

            long[] ts = new long[size];
            long[] exposures = new long[size];
            String[] indexLevels = new String[size];

            for (int i = 0; i < size; i++)
            {
                UltravioletLightDataPoint dataPoint = dataPoints.get(i);

                ts[i] = dataPoint.mTimestamp;
                exposures[i] = dataPoint.mExposure;
                indexLevels[i] = dataPoint.mIndexLevel.toString();
            }

            bundle.putLongArray(MicrosoftBand.TIMESTAMP_KEY, ts);
            bundle.putLongArray(MicrosoftBand.EXPOSURE_KEY, exposures);
            bundle.putStringArray(MicrosoftBand.INDEX_LEVEL_KEY, indexLevels);

            return bundle;
        }
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_microsoft_band, parent, false);
    }

    public static void bindViewHolder(DataPointViewHolder holder, Bundle dataPoint)
    {
        Context context = holder.itemView.getContext();

        String identifier = dataPoint.getBundle(Generator.PDK_METADATA).getString(Generator.IDENTIFIER);

        double timestamp = dataPoint.getBundle(Generator.PDK_METADATA).getDouble(Generator.TIMESTAMP);

        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        dateLabel.setText(Generator.formatTimestamp(context, timestamp));

        // Heart rate...
        TextView heartRateField = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_heart_rate);
        int[] heartRates = dataPoint.getBundle(HeartRateDataPoint.KEY).getIntArray(MicrosoftBand.HEART_RATE_KEY);

        if (heartRates.length > 0) {
            heartRateField.setText(context.getString(R.string.generator_microsoft_band_value_heart_rate, heartRates[heartRates.length - 1]));
        } else {
            heartRateField.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Band contact state...
        TextView bandStateField = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_contact_state);
        short[] bandStates = dataPoint.getBundle(ContactDataPoint.KEY).getShortArray(MicrosoftBand.CONTACT_STATE_KEY);

        if (bandStates.length > 0) {
            switch(bandStates[bandStates.length - 1])
            {
                case 0:
                    bandStateField.setText(R.string.generator_microsoft_band_value_band_state_not_worn);
                    break;
                case 1:
                    bandStateField.setText(R.string.generator_microsoft_band_value_band_state_worn);
                    break;
                case -1:
                    bandStateField.setText(R.string.generator_microsoft_band_value_band_state_unknown);
                    break;
            }
        } else {
            bandStateField.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Steps...
        TextView stepsField = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_steps_today);
        long[] steps = dataPoint.getBundle(PedometerDataPoint.KEY).getLongArray(MicrosoftBand.TODAY_STEPS_KEY);

        if (steps.length > 0) {
            stepsField.setText(context.getString(R.string.generator_microsoft_band_value_steps_today, steps[steps.length - 1]));
        } else {
            stepsField.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Motion type...
        TextView motionsField = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_distance_motion_type);
        String[] motions = dataPoint.getBundle(DistanceDataPoint.KEY).getStringArray(MicrosoftBand.MOTION_TYPE_KEY);

        if (motions.length > 0) {
            motionsField.setText(motions[motions.length - 1]);
        } else {
            motionsField.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Speed...
        TextView speedField = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_distance_speed);
        float[] speeds = dataPoint.getBundle(DistanceDataPoint.KEY).getFloatArray(MicrosoftBand.SPEED_KEY);

        if (speeds.length > 0) {
            speedField.setText(context.getString(R.string.generator_microsoft_band_value_distance_speed, speeds[speeds.length - 1]));
        } else {
            speedField.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // GSR...
        TextView gsrField = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_galavanic_skin_resistance);
        int[] gsrs = dataPoint.getBundle(GalvanicSkinResponseDataPoint.KEY).getIntArray(MicrosoftBand.RESISTANCE_KEY);

        if (gsrs.length > 0) {
            gsrField.setText(context.getString(R.string.generator_microsoft_band_value_galavanic_skin_resistance, gsrs[gsrs.length - 1]));
        } else {
            gsrField.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Skin temperature...
        TextView skinTempField = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_skin_temperature);
        float[] skinTemps = dataPoint.getBundle(SkinTemperatureDataPoint.KEY).getFloatArray(MicrosoftBand.TEMPERATURE_KEY);

        if (skinTemps.length > 0) {
            skinTempField.setText(context.getString(R.string.generator_microsoft_band_value_skin_temperature, skinTemps[skinTemps.length - 1]));
        } else {
            skinTempField.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Altimiter...
        TextView flightsAscended = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_altimeter_flights_ascended_today);
        long[] flights = dataPoint.getBundle(AltimiterDataPoint.KEY).getLongArray(MicrosoftBand.FLIGHTS_ASCENDED_TODAY_KEY);

        if (flights.length > 0) {
            flightsAscended.setText(context.getString(R.string.generator_microsoft_band_value_altimeter_flights_ascended_today, flights[flights.length - 1]));
        } else {
            flightsAscended.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Barometric pressure...
        TextView barometricPressure = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_barometer_pressure);
        double[] pressures = dataPoint.getBundle(BarometerDataPoint.KEY).getDoubleArray(MicrosoftBand.PRESSURE_KEY);

        if (pressures.length > 0) {
            barometricPressure.setText(context.getString(R.string.generator_microsoft_band_value_barometer_pressure, pressures[pressures.length - 1]));
        } else {
            barometricPressure.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Air temperature...
        TextView airTemperature = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_barometer_temperature);
        double[] airTemps = dataPoint.getBundle(BarometerDataPoint.KEY).getDoubleArray(MicrosoftBand.TEMPERATURE_KEY);

        if (airTemps.length > 0) {
            airTemperature.setText(context.getString(R.string.generator_microsoft_band_value_barometer_temperature, airTemps[airTemps.length - 1]));
        } else {
            airTemperature.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // Ambient brightness...
        TextView brightness = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_ambient_light_brightness);
        int[] brights = dataPoint.getBundle(AmbientLightDataPoint.KEY).getIntArray(MicrosoftBand.BRIGHTNESS_KEY);

        if (brights.length > 0) {
            brightness.setText(context.getString(R.string.generator_microsoft_band_value_ambient_light_brightness, brights[brights.length - 1]));
        } else {
            brightness.setText(context.getString(R.string.generator_value_not_applicable));
        }

        // UV level...
        TextView uvLevel = (TextView) holder.itemView.findViewById(R.id.generator_microsoft_band_value_uv_level);
        String[] uvs = dataPoint.getBundle(UltravioletLightDataPoint.KEY).getStringArray(MicrosoftBand.INDEX_LEVEL_KEY);

        if (uvs.length > 0) {
            uvLevel.setText(uvs[uvs.length - 1]);
        } else {
            uvLevel.setText(context.getString(R.string.generator_value_not_applicable));
        }
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }
}
