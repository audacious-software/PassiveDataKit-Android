package com.audacious_software.passive_data_kit.generators.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.os.Build;
import android.os.Bundle;

import com.audacious_software.passive_data_kit.generators.Generator;

@SuppressWarnings("WeakerAccess")
public abstract class SensorGenerator extends Generator {
    public static final String SENSOR_DATA = "sensor_data";

    private static final String SENSOR_REPORTING_MODE = "reporting_mode";
    private static final String SENSOR_NAME = "name";
    private static final String SENSOR_VENDOR = "vendor";
    private static final String SENSOR_MAX_RANGE = "max_range";
    private static final String SENSOR_POWER_USAGE = "power_usage";
    private static final String SENSOR_RESOLUTION = "resolution";
    private static final String SENSOR_MIN_DELAY = "min_delay";
    private static final String SENSOR_VERSION = "version";
    private static final String SENSOR_TYPE = "type";
    private static final String SENSOR_MAX_DELAY = "max_delay";
    private static final String SENSOR_IS_WAKEUP = "is_wakeup";

    private static final String SENSOR_REPORTING_MODE_CONTINUOUS = "continuous";
    private static final String SENSOR_REPORTING_MODE_ON_CHANGE = "on_change";
    private static final String SENSOR_REPORTING_MODE_ONE_SHOT = "one_shot";
    private static final String SENSOR_REPORTING_MODE_SPECIAL_TRIGGER = "special_trigger";

    public SensorGenerator(Context context) {
        super(context);
    }

    public static void addSensorMetadata(Bundle update, Sensor sensor) {
        update.putString(SensorGenerator.SENSOR_NAME, sensor.getName());
        update.putString(SensorGenerator.SENSOR_VENDOR, sensor.getVendor());

        update.putFloat(SensorGenerator.SENSOR_MAX_RANGE, sensor.getMaximumRange());
        update.putFloat(SensorGenerator.SENSOR_POWER_USAGE, sensor.getPower());
        update.putFloat(SensorGenerator.SENSOR_RESOLUTION, sensor.getResolution());

        update.putInt(SensorGenerator.SENSOR_MIN_DELAY, sensor.getMinDelay());
        update.putInt(SensorGenerator.SENSOR_VERSION, sensor.getVersion());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            update.putString(SensorGenerator.SENSOR_TYPE, sensor.getStringType());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                update.putInt(SensorGenerator.SENSOR_MAX_DELAY, sensor.getMaxDelay());

                switch(sensor.getReportingMode()) {
                    case Sensor.REPORTING_MODE_CONTINUOUS:
                        update.putString(SensorGenerator.SENSOR_REPORTING_MODE, SensorGenerator.SENSOR_REPORTING_MODE_CONTINUOUS);
                        break;
                    case Sensor.REPORTING_MODE_ON_CHANGE:
                        update.putString(SensorGenerator.SENSOR_REPORTING_MODE, SensorGenerator.SENSOR_REPORTING_MODE_ON_CHANGE);
                        break;
                    case Sensor.REPORTING_MODE_ONE_SHOT:
                        update.putString(SensorGenerator.SENSOR_REPORTING_MODE, SensorGenerator.SENSOR_REPORTING_MODE_ONE_SHOT);
                        break;
                    case Sensor.REPORTING_MODE_SPECIAL_TRIGGER:
                        update.putString(SensorGenerator.SENSOR_REPORTING_MODE, SensorGenerator.SENSOR_REPORTING_MODE_SPECIAL_TRIGGER);
                        break;
                }

                update.putBoolean(SensorGenerator.SENSOR_IS_WAKEUP, sensor.isWakeUpSensor());
            }
        }
    }
}
