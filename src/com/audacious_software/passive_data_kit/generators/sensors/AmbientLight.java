package com.audacious_software.passive_data_kit.generators.sensors;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by cjkarr on 4/17/2017.
 */

public class AmbientLight extends SensorGenerator implements SensorEventListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-sensor-light";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.AmbientLight.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String IGNORE_POWER_MANAGEMENT = "com.audacious_software.passive_data_kit.generators.sensors.AmbientLight.IGNORE_POWER_MANAGEMENT";
    private static final boolean IGNORE_POWER_MANAGEMENT_DEFAULT = true;

    private static final String DATABASE_PATH = "pdk-sensor-ambient-light.sqlite";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";

    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_LEVEL = "light_level";
    private static final String HISTORY_RAW_TIMESTAMP = "raw_timestamp";
    private static final String HISTORY_ACCURACY = "accuracy";

    private static AmbientLight sInstance = null;
    private static Handler sHandler = null;
    private static boolean sIsDrawing = false;
    private static long sLastDrawStart = 0;

    private SQLiteDatabase mDatabase = null;

    private Sensor mSensor = null;

    private static int NUM_BUFFERS = 3;
    private static int BUFFER_SIZE = 32;

    private long mLastCleanup = 0;
    private long mCleanupInterval = 15 * 60 * 1000;

    private int mActiveBuffersIndex = 0;
    private int mCurrentBufferIndex = 0;

    private float[][] mValueBuffers = null;
    private int[][] mAccuracyBuffers = null;
    private long[][] mRawTimestampBuffers = null;
    private long[][] mTimestampBuffers = null;

    long mBaseTimestamp = 0;
    private long mLatestTimestamp = 0;

    public static AmbientLight getInstance(Context context) {
        if (AmbientLight.sInstance == null) {
            AmbientLight.sInstance = new AmbientLight(context.getApplicationContext());
        }

        return AmbientLight.sInstance;
    }

    public AmbientLight(Context context) {
        super(context);
    }

    public void setIgnorePowerManagement(boolean ignore) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor e = prefs.edit();

        e.putBoolean(AmbientLight.IGNORE_POWER_MANAGEMENT, ignore);
        e.apply();

        this.stopGenerator();
        this.startGenerator();
    }

    public static void start(final Context context) {
        AmbientLight.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final SensorManager sensors = (SensorManager) this.mContext.getSystemService(Context.SENSOR_SERVICE);

        final AmbientLight me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(AmbientLight.GENERATOR_IDENTIFIER, AmbientLight.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, AmbientLight.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_ambient_light_create_history_table));
        }

        this.setDatabaseVersion(this.mDatabase, AmbientLight.DATABASE_VERSION);

        if (AmbientLight.isEnabled(this.mContext)) {
            this.mSensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);

            Runnable r = new Runnable()
            {
                public void run()
                {
                    Looper.prepare();

                    me.mValueBuffers = new float[AmbientLight.NUM_BUFFERS][AmbientLight.BUFFER_SIZE];
                    me.mAccuracyBuffers = new int[AmbientLight.NUM_BUFFERS][AmbientLight.BUFFER_SIZE];
                    me.mRawTimestampBuffers = new long[AmbientLight.NUM_BUFFERS][AmbientLight.BUFFER_SIZE];
                    me.mTimestampBuffers = new long[AmbientLight.NUM_BUFFERS][AmbientLight.BUFFER_SIZE];

                    me.mActiveBuffersIndex = 0;
                    me.mCurrentBufferIndex = 0;

                    AmbientLight.sHandler = new Handler();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                        sensors.registerListener(me, me.mSensor, SensorManager.SENSOR_DELAY_FASTEST, 0, AmbientLight.sHandler);
                    else
                        sensors.registerListener(me, me.mSensor, SensorManager.SENSOR_DELAY_FASTEST, AmbientLight.sHandler);

                    Looper.loop();
                }
            };

            Thread t = new Thread(r, "ambient-light");
            t.start();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

            if (prefs.getBoolean(AmbientLight.IGNORE_POWER_MANAGEMENT, AmbientLight.IGNORE_POWER_MANAGEMENT_DEFAULT)) {
                Generators.getInstance(this.mContext).acquireWakeLock(Accelerometer.IDENTIFIER, PowerManager.PARTIAL_WAKE_LOCK);
            } else {
                Generators.getInstance(this.mContext).releaseWakeLock(Accelerometer.IDENTIFIER);
            }
        } else {
            this.stopGenerator();
        }
    }

    private void stopGenerator() {
        final SensorManager sensors = (SensorManager) this.mContext.getSystemService(Context.SENSOR_SERVICE);

        if (this.mSensor != null) {
            sensors.unregisterListener(this, this.mSensor);

            if (AmbientLight.sHandler != null) {
                Looper loop = AmbientLight.sHandler.getLooper();
                loop.quit();

                AmbientLight.sHandler = null;
            }

            this.mValueBuffers = null;
            this.mAccuracyBuffers = null;
            this.mRawTimestampBuffers = null;
            this.mTimestampBuffers = null;

            this.mActiveBuffersIndex = 0;
            this.mCurrentBufferIndex = 0;

            this.mSensor = null;
        }

        Generators.getInstance(this.mContext).releaseWakeLock(AmbientLight.IDENTIFIER);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(AmbientLight.ENABLED, AmbientLight.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (AmbientLight.sInstance == null) {
            return false;
        }

        return AmbientLight.sInstance.mSensor != null;
    }

    public static ArrayList<DiagnosticAction> diagnostics(final Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (prefs.getBoolean(AmbientLight.IGNORE_POWER_MANAGEMENT, AmbientLight.IGNORE_POWER_MANAGEMENT_DEFAULT)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

                if (power.isIgnoringBatteryOptimizations(context.getPackageName()) == false) {
                    actions.add(new DiagnosticAction(context.getString(R.string.diagnostic_battery_optimization_exempt_title), context.getString(R.string.diagnostic_battery_optimization_exempt), new Runnable() {

                        @Override
                        public void run() {
                            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        }
                    }));
                }
            }
        }

        return actions;
    }

    public static void bindViewHolder(final DataPointViewHolder holder) {
        if (AmbientLight.sIsDrawing) {
            return;
        }

        final long drawStart = System.currentTimeMillis();

        if (drawStart - AmbientLight.sLastDrawStart < (30 * 1000)) {
            return;
        }

        AmbientLight.sLastDrawStart = drawStart;

        AmbientLight.sIsDrawing = true;

        final Context context = holder.itemView.getContext();

        final AmbientLight generator = AmbientLight.getInstance(context);

        final long now = System.currentTimeMillis() / (1000 * 60 * 5);
        final long start = now - (24 * 12); //  * 60);

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        if (context instanceof Activity) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            dateLabel.setText(Generator.formatTimestamp(context, AmbientLight.latestPointGenerated(context) / 1000));

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    final ArrayList<Entry> lowValues = new ArrayList<>();
                    final ArrayList<Entry> highValues = new ArrayList<>();

                    long lastTimestamp = -1;

                    float maxValue = 0;
                    float minValue = 0;

                    float lowLevel = -1;
                    float highLevel = -1;

                    final String where = Accelerometer.HISTORY_OBSERVED + " >= ? AND _id";
                    final String[] args = { "" + (System.currentTimeMillis() - (24 * 60 * 60 * 1000)) };

                    Cursor c = generator.mDatabase.query(AmbientLight.TABLE_HISTORY, null, where, args, null, null, AmbientLight.HISTORY_OBSERVED + " DESC");

                    while (c.moveToNext()) {
                        long when = c.getLong(c.getColumnIndex(AmbientLight.HISTORY_OBSERVED));

                        when = when / (1000 * 1000);
                        when = when / (1000 * 6 * 50);

                        float level = c.getFloat(c.getColumnIndex(AmbientLight.HISTORY_LEVEL));

                        if (level < 0.001) {
                            level = 0.001f;
                        }

                        level = (float) Math.log10(level);

                        if (lastTimestamp != when) {
                            if (lastTimestamp != -1) {
                                lowValues.add(0, new Entry(lastTimestamp, lowLevel));
                                highValues.add(0, new Entry(lastTimestamp, highLevel));
                            }

                            lastTimestamp = when;
                            lowLevel = level;
                            highLevel = level;
                        } else {
                            if (level < lowLevel) {
                                lowLevel = level;
                            }

                            if (level > highLevel) {
                                highLevel = level;
                            }
                        }

                        if (level > maxValue) {
                            maxValue = level;
                        }

                        if (level < minValue) {
                            minValue = level;
                        }
                    }

                    if (lastTimestamp != -1) {
                        lowValues.add(0, new Entry(lastTimestamp, lowLevel));
                        highValues.add(0, new Entry(lastTimestamp, highLevel));
                    }

                    final float finalMaxValue = maxValue;
                    final float finalMinValue = minValue;

                    Activity activity = (Activity) context;

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final LineChart chart = (LineChart) holder.itemView.findViewById(R.id.light_chart);
                            chart.setViewPortOffsets(0,0,0,0);
                            chart.setHighlightPerDragEnabled(false);
                            chart.setHighlightPerTapEnabled(false);
                            chart.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black));
                            chart.setPinchZoom(false);

                            final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);

                            final XAxis xAxis = chart.getXAxis();
                            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
                            xAxis.setTextSize(10f);
                            xAxis.setDrawAxisLine(true);
                            xAxis.setDrawGridLines(true);
                            xAxis.setCenterAxisLabels(true);
                            xAxis.setDrawLabels(true);
                            xAxis.setTextColor(ContextCompat.getColor(context, android.R.color.white));
                            xAxis.setGranularityEnabled(true);
                            xAxis.setGranularity(1);
                            xAxis.setAxisMinimum(start);
                            xAxis.setAxisMaximum(now);
                            xAxis.setValueFormatter(new IAxisValueFormatter() {
                                @Override
                                public String getFormattedValue(float value, AxisBase axis) {
                                    Date date = new Date((long) value * 5 * 60 * 1000);

                                    return timeFormat.format(date);
                                }
                            });

                            YAxis leftAxis = chart.getAxisLeft();
                            leftAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
                            leftAxis.setDrawGridLines(true);
                            leftAxis.setDrawAxisLine(true);
                            leftAxis.setGranularityEnabled(true);
                            leftAxis.setTextColor(ContextCompat.getColor(context, android.R.color.white));
                            leftAxis.setValueFormatter(new IAxisValueFormatter() {
                                @Override
                                public String getFormattedValue(float value, AxisBase axis) {
                                    return "" + Math.pow(10, value);
                                }
                            });

                            YAxis rightAxis = chart.getAxisRight();
                            rightAxis.setEnabled(false);

                            chart.getLegend().setEnabled(false);
                            chart.getDescription().setEnabled(false);

                            LineDataSet set = new LineDataSet(lowValues, "Low Light Levels");
                            set.setAxisDependency(YAxis.AxisDependency.LEFT);
                            set.setLineWidth(1.0f);
                            set.setDrawCircles(false);
                            set.setFillAlpha(192);
                            set.setDrawFilled(false);
                            set.setDrawValues(true);
                            set.setColor(ContextCompat.getColor(context, R.color.generator_ambient_light_low));
                            set.setDrawCircleHole(false);
                            set.setDrawValues(false);
                            set.setMode(LineDataSet.Mode.LINEAR);

                            LineData chartData = new LineData(set);

                            set = new LineDataSet(highValues, "High Light Levels");
                            set.setAxisDependency(YAxis.AxisDependency.LEFT);
                            set.setLineWidth(1.0f);
                            set.setDrawCircles(false);
                            set.setFillAlpha(192);
                            set.setDrawFilled(false);
                            set.setDrawValues(true);
                            set.setColor(ContextCompat.getColor(context, R.color.generator_ambient_light_high));
                            set.setDrawCircleHole(false);
                            set.setDrawValues(false);
                            set.setMode(LineDataSet.Mode.LINEAR);

                            chartData.addDataSet(set);

                            chart.setVisibleYRange((float) Math.floor(finalMinValue) - 1, (float) Math.ceil(finalMaxValue) + 1, YAxis.AxisDependency.LEFT);
                            chart.setData(chartData);

                            AmbientLight.sIsDrawing = false;
                        }
                    });
                }
            };

            Thread t = new Thread(r, "render-ambient-light-graph");
            t.start();
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_sensors_ambient_light, parent, false);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    public static long latestPointGenerated(Context context) {
        AmbientLight me = AmbientLight.getInstance(context);

        if (me.mLatestTimestamp == 0) {
            Cursor c = me.mDatabase.query(AmbientLight.TABLE_HISTORY, null, null, null, null, null, Accelerometer.HISTORY_OBSERVED + " DESC", "1");

            if (c.moveToNext()) {
                me.mLatestTimestamp = c.getLong(c.getColumnIndex(Accelerometer.HISTORY_OBSERVED) / (1000 * 1000));
            }

            c.close();
        }

        return me.mLatestTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(AmbientLight.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long rawTimestamp = sensorEvent.timestamp;

        if (this.mBaseTimestamp == 0) {
            this.mBaseTimestamp = (System.currentTimeMillis() * (1000 * 1000)) - rawTimestamp;
        }

        int accuracy = sensorEvent.accuracy;
        long normalizedTimestamp = this.mBaseTimestamp + rawTimestamp;
        float value = sensorEvent.values[0];

        if (this.mCurrentBufferIndex >= AmbientLight.BUFFER_SIZE) {
            this.saveBuffer(this.mActiveBuffersIndex, this.mCurrentBufferIndex);

            this.mCurrentBufferIndex = 0;
            this.mActiveBuffersIndex += 1;

            if (this.mActiveBuffersIndex >= AmbientLight.NUM_BUFFERS) {
                this.mActiveBuffersIndex = 0;
            }
        }

        this.mValueBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = value;
        this.mAccuracyBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = accuracy;
        this.mRawTimestampBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = rawTimestamp;
        this.mTimestampBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = normalizedTimestamp;

        this.mCurrentBufferIndex += 1;
    }

    private void saveBuffer(final int bufferIndex, final int bufferSize) {
        final AmbientLight me = this;

        me.mLatestTimestamp = System.currentTimeMillis();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                try {
                    me.mDatabase.beginTransaction();

                    for (int i = 0; i < bufferSize; i++) {
                        ContentValues values = new ContentValues();

                        values.put(AmbientLight.HISTORY_LEVEL, me.mValueBuffers[bufferIndex][i]);
                        values.put(AmbientLight.HISTORY_OBSERVED, me.mTimestampBuffers[bufferIndex][i]);
                        values.put(AmbientLight.HISTORY_RAW_TIMESTAMP, me.mRawTimestampBuffers[bufferIndex][i]);
                        values.put(AmbientLight.HISTORY_ACCURACY, me.mAccuracyBuffers[bufferIndex][i]);

                        me.mDatabase.insert(AmbientLight.TABLE_HISTORY, null, values);
                    }

                    me.mDatabase.setTransactionSuccessful();
                } finally {
                    me.mDatabase.endTransaction();
                }

                Bundle update = new Bundle();
                update.putLong(AmbientLight.HISTORY_OBSERVED, now);

                Bundle sensorReadings = new Bundle();

                sensorReadings.putFloatArray(AmbientLight.HISTORY_LEVEL, me.mValueBuffers[bufferIndex]);
                sensorReadings.putLongArray(AmbientLight.HISTORY_RAW_TIMESTAMP, me.mRawTimestampBuffers[bufferIndex]);
                sensorReadings.putLongArray(AmbientLight.HISTORY_OBSERVED, me.mTimestampBuffers[bufferIndex]);
                sensorReadings.putIntArray(AmbientLight.HISTORY_ACCURACY, me.mAccuracyBuffers[bufferIndex]);

                update.putBundle(SensorGenerator.SENSOR_DATA, sensorReadings);
                SensorGenerator.addSensorMetadata(update, me.mSensor);

                Generators.getInstance(me.mContext).notifyGeneratorUpdated(AmbientLight.GENERATOR_IDENTIFIER, update);

                if (now - me.mLastCleanup > me.mCleanupInterval) {
                    me.mLastCleanup = now;

                    long start = (now - (24 * 60 * 60 * 1000)) * 1000 * 1000;

                    String where = AmbientLight.HISTORY_OBSERVED + " < ?";
                    String[] args = { "" + start };

                    me.mDatabase.delete(AmbientLight.TABLE_HISTORY, where, args);
                }
            }
        };

        Thread t = new Thread(r, "ambient-light-save-buffer");
        t.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int newAccuracy) {
        // Do nothing...
    }
}
