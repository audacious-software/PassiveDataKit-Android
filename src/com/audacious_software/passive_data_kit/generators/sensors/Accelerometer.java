package com.audacious_software.passive_data_kit.generators.sensors;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
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

public class Accelerometer extends SensorGenerator implements SensorEventListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-sensor-accelerometer";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.Accelerometer.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATABASE_PATH = "pdk-sensor-accelerometer.sqlite";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";

    public static final String HISTORY_OBSERVED = "observed";
    private static final String HISTORY_RAW_TIMESTAMP = "raw_timestamp";
    private static final String HISTORY_ACCURACY = "accuracy";
    public static final String HISTORY_X = "x";
    public static final String HISTORY_Y = "y";
    public static final String HISTORY_Z = "z";

    private static Accelerometer sInstance = null;
    private static Handler sHandler = null;

    private static boolean sIsDrawing = false;
    private static long sLastDrawStart = 0;

    private SQLiteDatabase mDatabase = null;

    private Sensor mSensor = null;

    private static int NUM_BUFFERS = 3;
    private static int BUFFER_SIZE = 1024;

    private long mLastCleanup = 0;
    private long mCleanupInterval = 15 * 60 * 1000;

    private int mActiveBuffersIndex = 0;
    private int mCurrentBufferIndex = 0;

    private float[][] mXValueBuffers = null;
    private float[][] mYValueBuffers = null;
    private float[][] mZValueBuffers = null;
    private int[][] mAccuracyBuffers = null;
    private long[][] mRawTimestampBuffers = null;
    private long[][] mTimestampBuffers = null;

    long mBaseTimestamp = 0;

    private long mLatestTimestamp = 0;

    public static Accelerometer getInstance(Context context) {
        if (Accelerometer.sInstance == null) {
            Accelerometer.sInstance = new Accelerometer(context.getApplicationContext());
        }

        return Accelerometer.sInstance;
    }

    public Accelerometer(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        Accelerometer.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final SensorManager sensors = (SensorManager) this.mContext.getSystemService(Context.SENSOR_SERVICE);

        final Accelerometer me = this;

        Generators.getInstance(this.mContext).registerCustomViewClass(Accelerometer.GENERATOR_IDENTIFIER, Accelerometer.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Accelerometer.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_accelerometer_create_history_table));
        }

        this.setDatabaseVersion(this.mDatabase, Accelerometer.DATABASE_VERSION);

        if (Accelerometer.isEnabled(this.mContext)) {
            this.mSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            Runnable r = new Runnable()
            {
                public void run()
                {
                    Looper.prepare();

                    me.mXValueBuffers = new float[Accelerometer.NUM_BUFFERS][Accelerometer.BUFFER_SIZE];
                    me.mYValueBuffers = new float[Accelerometer.NUM_BUFFERS][Accelerometer.BUFFER_SIZE];
                    me.mZValueBuffers = new float[Accelerometer.NUM_BUFFERS][Accelerometer.BUFFER_SIZE];
                    me.mAccuracyBuffers = new int[Accelerometer.NUM_BUFFERS][Accelerometer.BUFFER_SIZE];
                    me.mRawTimestampBuffers = new long[Accelerometer.NUM_BUFFERS][Accelerometer.BUFFER_SIZE];
                    me.mTimestampBuffers = new long[Accelerometer.NUM_BUFFERS][Accelerometer.BUFFER_SIZE];

                    me.mActiveBuffersIndex = 0;
                    me.mCurrentBufferIndex = 0;

                    Accelerometer.sHandler = new Handler();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                        sensors.registerListener(me, me.mSensor, SensorManager.SENSOR_DELAY_NORMAL, 0, Accelerometer.sHandler);
                    else
                        sensors.registerListener(me, me.mSensor, SensorManager.SENSOR_DELAY_NORMAL, Accelerometer.sHandler);

                    Looper.loop();
                }
            };

            Thread t = new Thread(r, "accelerometer");
            t.start();
        } else {
            if (this.mSensor != null) {
                sensors.unregisterListener(this, this.mSensor);

                if (Accelerometer.sHandler != null) {
                    Looper loop = Accelerometer.sHandler.getLooper();
                    loop.quit();

                    Accelerometer.sHandler = null;
                }

                me.mXValueBuffers = null;
                me.mYValueBuffers = null;
                me.mZValueBuffers = null;
                me.mAccuracyBuffers = null;
                me.mRawTimestampBuffers = null;
                me.mTimestampBuffers = null;

                me.mActiveBuffersIndex = 0;
                me.mCurrentBufferIndex = 0;

                this.mSensor = null;
            }
        }
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Accelerometer.ENABLED, Accelerometer.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (Accelerometer.sInstance == null) {
            return false;
        }

        return Accelerometer.sInstance.mSensor != null;
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    public static void bindViewHolder(final DataPointViewHolder holder) {
        if (Accelerometer.sIsDrawing) {
            Log.e("PDK", "IS DRAWING");
            return;
        }

        final long drawStart = System.currentTimeMillis();

        if (drawStart - Accelerometer.sLastDrawStart < (30 * 1000)) {
            Log.e("PDK", "TOO SOON");
            return;
        }

        Accelerometer.sLastDrawStart = drawStart;

        Accelerometer.sIsDrawing = true;

        Log.e("PDK", "HOLDER " + holder.hashCode());

        final Context context = holder.itemView.getContext();
        final View itemView = holder.itemView;

        final Accelerometer generator = Accelerometer.getInstance(context);

        final long now = System.currentTimeMillis() / (1000 * 60 * 5);
        final long start = now - (24 * 12); //  * 60);

        Log.e("PDK", "START QUERY: " + (System.currentTimeMillis() - drawStart));

        Cursor c = generator.mDatabase.query(Accelerometer.TABLE_HISTORY, null, null, null, null, null, Accelerometer.HISTORY_OBSERVED + " DESC");

        Log.e("PDK", "END QUERY: " + (System.currentTimeMillis() - drawStart));

        View cardContent = itemView.findViewById(R.id.card_content);
        View cardEmpty = itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) itemView.findViewById(R.id.generator_data_point_date);

        Log.e("PDK", "ACCEL PREP: " + (System.currentTimeMillis() - drawStart) + " -- COUNT: " + c.getCount());

        if (c.moveToNext() && (context instanceof Activity)) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long timestamp = c.getLong(c.getColumnIndex(Accelerometer.HISTORY_OBSERVED)) / (1000 * 1000 * 1000);

            dateLabel.setText(Generator.formatTimestamp(context, timestamp));

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    Log.e("PDK", "THREAD START: " + (System.currentTimeMillis() - drawStart));

                    final ArrayList<Entry> xLowValues = new ArrayList<>();
                    final ArrayList<Entry> xHighValues = new ArrayList<>();

                    final ArrayList<Entry> yLowValues = new ArrayList<>();
                    final ArrayList<Entry> yHighValues = new ArrayList<>();

                    final ArrayList<Entry> zLowValues = new ArrayList<>();
                    final ArrayList<Entry> zHighValues = new ArrayList<>();

                    final String where = Accelerometer.HISTORY_OBSERVED + " >= ? AND _id % 1024 = 0";
                    final String[] args = { "" + start };

                    Cursor c = generator.mDatabase.query(Accelerometer.TABLE_HISTORY, null, where, args, null, null, Accelerometer.HISTORY_OBSERVED + " DESC");

                    long lastTimestamp = -1;

                    float maxValue = 0;
                    float minValue = 0;

                    float lowX = -1;
                    float highX = -1;

                    float lowY = -1;
                    float highY = -1;

                    float lowZ = -1;
                    float highZ = -1;

                    int whenIndex = c.getColumnIndex(Accelerometer.HISTORY_OBSERVED);
                    int xIndex = c.getColumnIndex(Accelerometer.HISTORY_X);
                    int yIndex = c.getColumnIndex(Accelerometer.HISTORY_Y);
                    int zIndex = c.getColumnIndex(Accelerometer.HISTORY_Z);

                    Log.e("PDK", "COUNT: " + c.getCount());
                    Log.e("PDK", "ACCEL START BUILD: " + (System.currentTimeMillis() - drawStart));

                    while (c.moveToNext()) {
                        long when = c.getLong(whenIndex);

                        when = when / (1000 * 1000);
                        when = when / (1000 * 6 * 50);

                        float x = c.getFloat(xIndex);
                        float y = c.getFloat(yIndex);
                        float z = c.getFloat(zIndex);

                        if (lastTimestamp != when) {
                            if (lastTimestamp != -1) {
                                xLowValues.add(0, new Entry(lastTimestamp, lowX));
                                xHighValues.add(0, new Entry(lastTimestamp, highX));

                                yLowValues.add(0, new Entry(lastTimestamp, lowY));
                                yHighValues.add(0, new Entry(lastTimestamp, highY));

                                zLowValues.add(0, new Entry(lastTimestamp, lowZ));
                                zHighValues.add(0, new Entry(lastTimestamp, highZ));
                            }

                            lastTimestamp = when;

                            lowX = x;
                            highX = x;

                            lowY = y;
                            highY = y;

                            lowZ = z;
                            highZ = z;
                        } else {
                            if (x < lowX) {
                                lowX = x;
                            }

                            if (x > highX) {
                                highX = x;
                            }

                            if (y < lowY) {
                                lowY = y;
                            }

                            if (y > highY) {
                                highY = y;
                            }

                            if (z < lowZ) {
                                lowZ = z;
                            }

                            if (z > highZ) {
                                highZ = z;
                            }
                        }

                        if (x > maxValue) {
                            maxValue = x;
                        }

                        if (x < minValue) {
                            minValue = x;
                        }

                        if (y > maxValue) {
                            maxValue = y;
                        }

                        if (y < minValue) {
                            minValue = y;
                        }

                        if (z > maxValue) {
                            maxValue = z;
                        }

                        if (z < minValue) {
                            minValue = z;
                        }
                    }

                    Log.e("PDK", "ACCEL END BUILD BUILD: " + (System.currentTimeMillis() - drawStart));

                    Log.e("PDK", "DATA COUNT: " + xLowValues.size());

                    if (lastTimestamp != -1) {
                        xLowValues.add(0, new Entry(lastTimestamp, lowX));
                        xHighValues.add(0, new Entry(lastTimestamp, highX));

                        yLowValues.add(0, new Entry(lastTimestamp, lowY));
                        yHighValues.add(0, new Entry(lastTimestamp, highY));

                        zLowValues.add(0, new Entry(lastTimestamp, lowZ));
                        zHighValues.add(0, new Entry(lastTimestamp, highZ));
                    }

                    c.close();

                    Activity activity = (Activity) context;

                    final float finalMaxValue = maxValue;
                    final float finalMinValue = minValue;

                    Log.e("PDK", "THREAD HANDOFF: " + (System.currentTimeMillis() - drawStart));

                    final List<ArrayList<Entry>> data = new ArrayList<>();
                    data.add(xLowValues);
                    data.add(xHighValues);
                    data.add(yLowValues);
                    data.add(yHighValues);
                    data.add(zLowValues);
                    data.add(zHighValues);

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.e("PDK", "UI START: " + (System.currentTimeMillis() - drawStart));

                            int[] colors = {
                                    R.color.generator_accelerometer_x_low,
                                    R.color.generator_accelerometer_x_high,
                                    R.color.generator_accelerometer_y_low,
                                    R.color.generator_accelerometer_y_high,
                                    R.color.generator_accelerometer_z_low,
                                    R.color.generator_accelerometer_z_high
                            };

                            LineData chartData = new LineData();

                            for (int i = 0; i < colors.length; i++) {
                                int color = colors[i];

                                ArrayList<Entry> entries = data.get(i);

                                LineDataSet set = new LineDataSet(entries, "");
                                set.setAxisDependency(YAxis.AxisDependency.LEFT);
                                set.setLineWidth(1.0f);
                                set.setDrawCircles(false);
                                set.setFillAlpha(192);
                                set.setDrawFilled(false);
                                set.setDrawValues(true);
                                set.setColor(ContextCompat.getColor(context, color));
                                set.setDrawCircleHole(false);
                                set.setDrawValues(false);
                                set.setMode(LineDataSet.Mode.LINEAR);

                                chartData.addDataSet(set);
                            }

                            Log.e("PDK", "ACCEL START GRAPH: " + (System.currentTimeMillis() - drawStart));

                            final LineChart chart = (LineChart) itemView.findViewById(R.id.accelerometer_chart);

                            if (chart != null) {
                                chart.setViewPortOffsets(0, 0, 0, 0);
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

                                YAxis rightAxis = chart.getAxisRight();
                                rightAxis.setEnabled(false);

                                chart.getLegend().setEnabled(false);
                                chart.getDescription().setEnabled(false);

                                chart.setVisibleYRange((float) Math.floor(finalMinValue) - 1, (float) Math.ceil(finalMaxValue) + 1, YAxis.AxisDependency.LEFT);
                                chart.setData(chartData);
                            }

                            Log.e("PDK", "UI END: " + (System.currentTimeMillis() - drawStart));

                            Accelerometer.sIsDrawing = false;
                        }
                    });
                }
            };

            Thread t = new Thread(r, "render_accelerometer_graph");
            t.start();

            c.close();
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        c.close();

        Log.e("PDK", "MAIN DONE: " + (System.currentTimeMillis() - drawStart));
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_sensors_accelerometer, parent, false);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    public static long latestPointGenerated(Context context) {
        Accelerometer me = Accelerometer.getInstance(context);

        if (me.mLatestTimestamp == 0) {
            Cursor c = me.mDatabase.query(Accelerometer.TABLE_HISTORY, null, null, null, null, null, Accelerometer.HISTORY_OBSERVED + " DESC");

            if (c.moveToNext()) {
                me.mLatestTimestamp = c.getLong(c.getColumnIndex(Accelerometer.HISTORY_OBSERVED) / (1000 * 1000));
            }

            c.close();
        }

        return me.mLatestTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(Accelerometer.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long rawTimestamp = sensorEvent.timestamp;

        if (this.mBaseTimestamp == 0) {
            this.mBaseTimestamp = (System.currentTimeMillis() * (1000 * 1000)) - rawTimestamp;
        }

        int accuracy = sensorEvent.accuracy;
        long normalizedTimestamp = this.mBaseTimestamp + rawTimestamp;

        if (this.mCurrentBufferIndex >= Accelerometer.BUFFER_SIZE) {
            this.saveBuffer(this.mActiveBuffersIndex, this.mCurrentBufferIndex);

            this.mCurrentBufferIndex = 0;
            this.mActiveBuffersIndex += 1;

            if (this.mActiveBuffersIndex >= Accelerometer.NUM_BUFFERS) {
                this.mActiveBuffersIndex = 0;
            }
        }

//        Log.e("PDK", "ACCEL[" + this.mCurrentBufferIndex + "/" + this.mActiveBuffersIndex + "] = " + sensorEvent.values[0] + " -- " + sensorEvent.values[1] + " -- " + sensorEvent.values[2]);

        this.mXValueBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = sensorEvent.values[0];
        this.mYValueBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = sensorEvent.values[1];
        this.mZValueBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = sensorEvent.values[2];
        this.mAccuracyBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = accuracy;
        this.mRawTimestampBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = rawTimestamp;
        this.mTimestampBuffers[this.mActiveBuffersIndex][this.mCurrentBufferIndex] = normalizedTimestamp;

        this.mCurrentBufferIndex += 1;
    }

    private void saveBuffer(final int bufferIndex, final int bufferSize) {
        final Accelerometer me = this;

        me.mLatestTimestamp = System.currentTimeMillis();

        Runnable r = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                try {
                    me.mDatabase.beginTransaction();

                    for (int i = 0; i < bufferSize; i++) {
                        ContentValues values = new ContentValues();

                        values.put(Accelerometer.HISTORY_X, me.mXValueBuffers[bufferIndex][i]);
                        values.put(Accelerometer.HISTORY_Y, me.mYValueBuffers[bufferIndex][i]);
                        values.put(Accelerometer.HISTORY_Z, me.mZValueBuffers[bufferIndex][i]);
                        values.put(Accelerometer.HISTORY_OBSERVED, me.mTimestampBuffers[bufferIndex][i]);
                        values.put(Accelerometer.HISTORY_RAW_TIMESTAMP, me.mRawTimestampBuffers[bufferIndex][i]);
                        values.put(Accelerometer.HISTORY_ACCURACY, me.mAccuracyBuffers[bufferIndex][i]);

                        me.mDatabase.insert(Accelerometer.TABLE_HISTORY, null, values);
                    }

                    me.mDatabase.setTransactionSuccessful();
                } finally {
                    me.mDatabase.endTransaction();
                }

                Bundle update = new Bundle();
                update.putLong(Accelerometer.HISTORY_OBSERVED, now);

                Bundle sensorReadings = new Bundle();

                sensorReadings.putFloatArray(Accelerometer.HISTORY_X, me.mXValueBuffers[bufferIndex]);
                sensorReadings.putFloatArray(Accelerometer.HISTORY_Y, me.mYValueBuffers[bufferIndex]);
                sensorReadings.putFloatArray(Accelerometer.HISTORY_Z, me.mZValueBuffers[bufferIndex]);
                sensorReadings.putLongArray(Accelerometer.HISTORY_RAW_TIMESTAMP, me.mRawTimestampBuffers[bufferIndex]);
                sensorReadings.putLongArray(Accelerometer.HISTORY_OBSERVED, me.mTimestampBuffers[bufferIndex]);
                sensorReadings.putIntArray(Accelerometer.HISTORY_ACCURACY, me.mAccuracyBuffers[bufferIndex]);

                update.putBundle(SensorGenerator.SENSOR_DATA, sensorReadings);
                SensorGenerator.addSensorMetadata(update, me.mSensor);

                Generators.getInstance(me.mContext).notifyGeneratorUpdated(Accelerometer.GENERATOR_IDENTIFIER, update);

                if (now - me.mLastCleanup > me.mCleanupInterval) {
                    me.mLastCleanup = now;

                    long start = (now - (2 * 24 * 60 * 60 * 1000)) * 1000 * 1000;

                    String where = Accelerometer.HISTORY_OBSERVED + " < ?";
                    String[] args = { "" + start };

                    me.mDatabase.delete(Accelerometer.TABLE_HISTORY, where, args);
                }
            }
        };

        Thread t = new Thread(r, "accelerometer-save-buffer");
        t.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int newAccuracy) {
        // Do nothing...
    }
}
