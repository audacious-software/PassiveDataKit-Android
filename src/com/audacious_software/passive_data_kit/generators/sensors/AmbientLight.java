package com.audacious_software.passive_data_kit.generators.sensors;

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
import com.audacious_software.passive_data_kit.generators.device.Battery;
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

public class AmbientLight extends Generator implements SensorEventListener {
    private static final String GENERATOR_IDENTIFIER = "pdk-sensor-light";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.AmbientLight.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static final String DATABASE_PATH = "pdk-sensor-light.sqlite";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";

    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_LEVEL = "light_level";

    private static AmbientLight sInstance = null;
    private static Handler sHandler = null;

    private SQLiteDatabase mDatabase = null;

    private Sensor mSensor = null;

    public static AmbientLight getInstance(Context context) {
        if (AmbientLight.sInstance == null) {
            AmbientLight.sInstance = new AmbientLight(context.getApplicationContext());
        }

        return AmbientLight.sInstance;
    }

    public AmbientLight(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        AmbientLight.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final SensorManager sensors = (SensorManager) this.mContext.getSystemService(Context.SENSOR_SERVICE);

        final AmbientLight me = this;

        if (AmbientLight.isEnabled(this.mContext)) {
            this.mSensor = sensors.getDefaultSensor(Sensor.TYPE_LIGHT);

            Runnable r = new Runnable()
            {
                public void run()
                {
                    Looper.prepare();

                    AmbientLight.sHandler = new Handler();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                        sensors.registerListener(me, me.mSensor, SensorManager.SENSOR_DELAY_NORMAL, 0, AmbientLight.sHandler);
                    else
                        sensors.registerListener(me, me.mSensor, SensorManager.SENSOR_DELAY_NORMAL, AmbientLight.sHandler);

                    Looper.loop();
                }
            };

            Thread t = new Thread(r, "ambient-light");
            t.start();
        } else {
            if (this.mSensor != null) {
                if (AmbientLight.sHandler != null) {
                    Looper loop = AmbientLight.sHandler.getLooper();
                    loop.quit();

                    AmbientLight.sHandler = null;
                }

                sensors.unregisterListener(this, this.mSensor);

                this.mSensor = null;
            }
        }


        Generators.getInstance(this.mContext).registerCustomViewClass(AmbientLight.GENERATOR_IDENTIFIER, Battery.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, AmbientLight.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
//
//                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_device_ambient_light_create_history_table));
        }

        this.setDatabaseVersion(this.mDatabase, AmbientLight.DATABASE_VERSION);
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

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        AmbientLight generator = AmbientLight.getInstance(context);

        long now = System.currentTimeMillis();
        long start = now - (24 * 60 * 60 * 1000);

        String where = AmbientLight.HISTORY_OBSERVED + " >= ?";
        String[] args = { "" + start };

        Cursor c = generator.mDatabase.query(AmbientLight.TABLE_HISTORY, null, where, args, null, null, AmbientLight.HISTORY_OBSERVED + " DESC");

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long timestamp = c.getLong(c.getColumnIndex(AmbientLight.HISTORY_OBSERVED)) / 1000;

            dateLabel.setText(Generator.formatTimestamp(context, timestamp));

            c.moveToPrevious();

            final LineChart chart = (LineChart) holder.itemView.findViewById(R.id.battery_level_chart);
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
                    Date date = new Date((long) value);

                    return timeFormat.format(date);
                }
            });

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
            leftAxis.setDrawGridLines(true);
            leftAxis.setDrawAxisLine(true);
            leftAxis.setGranularityEnabled(true);
            leftAxis.setAxisMaximum(110);
            leftAxis.setAxisMinimum(-10);
            leftAxis.setTextColor(ContextCompat.getColor(context, android.R.color.white));

            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setEnabled(false);

            chart.getLegend().setEnabled(false);
            chart.getDescription().setEnabled(false);

            ArrayList<Entry> values = new ArrayList<>();

            long lastLevel = -1;

            while (c.moveToNext()) {
                long when = c.getLong(c.getColumnIndex(AmbientLight.HISTORY_OBSERVED));
                long level = c.getLong(c.getColumnIndex(AmbientLight.HISTORY_LEVEL));

                if (level != lastLevel) {
                    values.add(0, new Entry(when, level));
                    lastLevel = level;

                    Log.e("SLEEP-SIGHT", "VALUE: " + level + " -- " + (when - start));
                }
            }

            Log.e("SLEEP-SIGHT", "LIGHT VALUES COUNT 2: " + values.size());

            LineDataSet set = new LineDataSet(values, "Light Level");
            set.setAxisDependency(YAxis.AxisDependency.LEFT);
            set.setLineWidth(2.0f);
            set.setDrawCircles(false);
            set.setFillAlpha(192);
            set.setDrawFilled(false);
            set.setDrawValues(true);
            set.setColor(ContextCompat.getColor(context, R.color.generator_battery_plot));
            set.setDrawCircleHole(false);
            set.setDrawValues(false);
            set.setMode(LineDataSet.Mode.LINEAR);

            chart.setVisibleYRange(0, 120, YAxis.AxisDependency.LEFT);
            chart.setData(new LineData(set));
        } else {
            cardContent.setVisibility(View.GONE);
            cardEmpty.setVisibility(View.VISIBLE);

            dateLabel.setText(R.string.label_never_pdk);
        }

        c.close();
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_device_battery, parent, false);
    }

    @Override
    public List<Bundle> fetchPayloads() {
        return new ArrayList<>();
    }

    public static long latestPointGenerated(Context context) {
        long timestamp = 0;

        AmbientLight me = AmbientLight.getInstance(context);

        Cursor c = me.mDatabase.query(AmbientLight.TABLE_HISTORY, null, null, null, null, null, AmbientLight.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(AmbientLight.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(AmbientLight.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int newAccuracy) {

    }
}
