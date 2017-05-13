package com.audacious_software.passive_data_kit.generators.device;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.BatteryManager;
import android.os.Bundle;
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

public class Battery extends Generator {
    private static final String GENERATOR_IDENTIFIER = "pdk-device-battery";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.device.Battery.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;


    private static final String DATABASE_PATH = "pdk-device-battery.sqlite";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_HISTORY = "history";

    public static final String HISTORY_OBSERVED = "observed";
    public static final String HISTORY_HEALTH = "health";
    public static final String HISTORY_LEVEL = "level";
    public static final String HISTORY_PLUGGED = "plugged";
    public static final String HISTORY_PRESENT = "present";
    public static final String HISTORY_SCALE = "scale";
    public static final String HISTORY_TEMPERATURE = "temperature";
    public static final String HISTORY_VOLTAGE = "voltage";
    public static final String HISTORY_TECHNOLOGY = "technology";
    public static final String HISTORY_STATUS = "status";

    private static final String HEALTH_COLD = "cold";
    private static final String HEALTH_DEAD = "dead";
    private static final String HEALTH_GOOD = "good";
    private static final String HEALTH_OVERHEAT = "overheat";
    private static final String HEALTH_OVER_VOLTAGE = "over-voltage";
    private static final String HEALTH_UNSPECIFIED_FAILURE = "unspecified-failure";
    private static final String HEALTH_UNKNOWN = "unknown";

    private static final String PLUGGED_AC = "ac";
    private static final String PLUGGED_USB = "usb";
    private static final String PLUGGED_WIRELESS = "wireless";
    private static final String PLUGGED_UNKNOWN = "unknown";

    private static final String TECHNOLOGY_UNKNOWN = "unknown";

    private static final String STATUS_CHARGING = "charging";
    private static final String STATUS_DISCHARGING = "discharging";
    private static final String STATUS_FULL = "full";
    private static final String STATUS_NOT_CHARGING = "not-charging";
    private static final String STATUS_UNKNOWN = "unknown";

    private static Battery sInstance = null;

    private BroadcastReceiver mReceiver = null;

    private SQLiteDatabase mDatabase = null;

    private long mLastTimestamp = 0;

    public static Battery getInstance(Context context) {
        if (Battery.sInstance == null) {
            Battery.sInstance = new Battery(context.getApplicationContext());
        }

        return Battery.sInstance;
    }

    public Battery(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        Battery.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final Battery me = this;

        final long now = System.currentTimeMillis();

        me.mLastTimestamp = now;

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                ContentValues values = new ContentValues();
                values.put(Battery.HISTORY_OBSERVED, now);

                Bundle update = new Bundle();
                update.putLong(Battery.HISTORY_OBSERVED, now);

                switch (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
                    case BatteryManager.BATTERY_HEALTH_COLD:
                        values.put(Battery.HISTORY_HEALTH, Battery.HEALTH_COLD);
                        update.putString(Battery.HISTORY_HEALTH, Battery.HEALTH_COLD);
                        break;
                    case BatteryManager.BATTERY_HEALTH_DEAD:
                        values.put(Battery.HISTORY_HEALTH, Battery.HEALTH_DEAD);
                        update.putString(Battery.HISTORY_HEALTH, Battery.HEALTH_DEAD);
                        break;
                    case BatteryManager.BATTERY_HEALTH_GOOD:
                        values.put(Battery.HISTORY_HEALTH, Battery.HEALTH_GOOD);
                        update.putString(Battery.HISTORY_HEALTH, Battery.HEALTH_GOOD);
                        break;
                    case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                        values.put(Battery.HISTORY_HEALTH, Battery.HEALTH_OVERHEAT);
                        update.putString(Battery.HISTORY_HEALTH, Battery.HEALTH_OVERHEAT);
                        break;
                    case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                        values.put(Battery.HISTORY_HEALTH, Battery.HEALTH_OVER_VOLTAGE);
                        update.putString(Battery.HISTORY_HEALTH, Battery.HEALTH_OVER_VOLTAGE);
                        break;
                    case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                        values.put(Battery.HISTORY_HEALTH, Battery.HEALTH_UNSPECIFIED_FAILURE);
                        update.putString(Battery.HISTORY_HEALTH, Battery.HEALTH_UNSPECIFIED_FAILURE);
                        break;
                    default:
                        values.put(Battery.HISTORY_HEALTH, Battery.HEALTH_UNKNOWN);
                        update.putString(Battery.HISTORY_HEALTH, Battery.HEALTH_UNKNOWN);
                        break;
                }

                switch (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    case BatteryManager.BATTERY_PLUGGED_AC:
                        values.put(Battery.HISTORY_PLUGGED, Battery.PLUGGED_AC);
                        update.putString(Battery.HISTORY_PLUGGED, Battery.PLUGGED_AC);
                        break;
                    case BatteryManager.BATTERY_PLUGGED_USB:
                        values.put(Battery.HISTORY_PLUGGED, Battery.PLUGGED_USB);
                        update.putString(Battery.HISTORY_PLUGGED, Battery.PLUGGED_USB);
                        break;
                    case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                        values.put(Battery.HISTORY_PLUGGED, Battery.PLUGGED_WIRELESS);
                        update.putString(Battery.HISTORY_PLUGGED, Battery.PLUGGED_WIRELESS);
                        break;
                    default:
                        values.put(Battery.HISTORY_PLUGGED, Battery.PLUGGED_UNKNOWN);
                        update.putString(Battery.HISTORY_PLUGGED, Battery.PLUGGED_UNKNOWN);
                        break;
                }

                switch (intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
                    case BatteryManager.BATTERY_STATUS_CHARGING:
                        values.put(Battery.HISTORY_STATUS, Battery.STATUS_CHARGING);
                        update.putString(Battery.HISTORY_STATUS, Battery.STATUS_CHARGING);
                        break;
                    case BatteryManager.BATTERY_STATUS_DISCHARGING:
                        values.put(Battery.HISTORY_STATUS, Battery.STATUS_DISCHARGING);
                        update.putString(Battery.HISTORY_STATUS, Battery.STATUS_DISCHARGING);
                        break;
                    case BatteryManager.BATTERY_STATUS_FULL:
                        values.put(Battery.HISTORY_STATUS, Battery.STATUS_FULL);
                        update.putString(Battery.HISTORY_STATUS, Battery.STATUS_FULL);
                        break;
                    case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                        values.put(Battery.HISTORY_STATUS, Battery.STATUS_NOT_CHARGING);
                        update.putString(Battery.HISTORY_STATUS, Battery.STATUS_NOT_CHARGING);
                        break;
                    default:
                        values.put(Battery.HISTORY_STATUS, Battery.STATUS_UNKNOWN);
                        update.putString(Battery.HISTORY_STATUS, Battery.STATUS_UNKNOWN);
                        break;
                }

                values.put(Battery.HISTORY_PRESENT, intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));
                update.putBoolean(Battery.HISTORY_PRESENT, intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));

                values.put(Battery.HISTORY_LEVEL, intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
                update.putInt(Battery.HISTORY_LEVEL, intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));

                values.put(Battery.HISTORY_SCALE, intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
                update.putInt(Battery.HISTORY_SCALE, intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1));

                values.put(Battery.HISTORY_TEMPERATURE, intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
                update.putInt(Battery.HISTORY_TEMPERATURE, intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));

                values.put(Battery.HISTORY_VOLTAGE, intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
                update.putInt(Battery.HISTORY_VOLTAGE, intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));

                values.put(Battery.HISTORY_TECHNOLOGY, Battery.TECHNOLOGY_UNKNOWN);
                update.putString(Battery.HISTORY_TECHNOLOGY, Battery.TECHNOLOGY_UNKNOWN);

                me.mDatabase.insert(Battery.TABLE_HISTORY, null, values);

                Generators.getInstance(context).notifyGeneratorUpdated(Battery.GENERATOR_IDENTIFIER, update);
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

        this.mContext.registerReceiver(this.mReceiver, filter);

        Generators.getInstance(this.mContext).registerCustomViewClass(Battery.GENERATOR_IDENTIFIER, Battery.class);

        File path = PassiveDataKit.getGeneratorsStorage(this.mContext);

        path = new File(path, Battery.DATABASE_PATH);

        this.mDatabase = SQLiteDatabase.openOrCreateDatabase(path, null);

        int version = this.getDatabaseVersion(this.mDatabase);

        switch (version) {
            case 0:
                this.mDatabase.execSQL(this.mContext.getString(R.string.pdk_generator_device_battery_create_history_table));
        }

        this.setDatabaseVersion(this.mDatabase, Battery.DATABASE_VERSION);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(Battery.ENABLED, Battery.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (Battery.sInstance == null) {
            return false;
        }

        return Battery.sInstance.mReceiver != null;
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    public static void bindViewHolder(DataPointViewHolder holder) {
        final Context context = holder.itemView.getContext();

        Battery generator = Battery.getInstance(context);

        long now = System.currentTimeMillis();
        long start = now - (24 * 60 * 60 * 1000);

        String where = Battery.HISTORY_OBSERVED + " >= ?";
        String[] args = { "" + start };

        Cursor c = generator.mDatabase.query(Battery.TABLE_HISTORY, null, where, args, null, null, Battery.HISTORY_OBSERVED + " DESC");

        Log.e("PDK", "BATTERY COUNT: " + c.getCount());

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long timestamp = c.getLong(c.getColumnIndex(Battery.HISTORY_OBSERVED)) / 1000;

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

            int observedIndex = c.getColumnIndex(Battery.HISTORY_OBSERVED);
            int levelIndex = c.getColumnIndex(Battery.HISTORY_LEVEL);

            while (c.moveToNext()) {
                long when = c.getLong(observedIndex);
                long level = c.getLong(levelIndex);

                if (level != lastLevel) {
                    values.add(0, new Entry(when, level));
                    lastLevel = level;
                }
            }

            LineDataSet set = new LineDataSet(values, "Battery");
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
        Battery me = Battery.getInstance(context);

        if (me.mLastTimestamp == 0) {
            Cursor c = me.mDatabase.query(Battery.TABLE_HISTORY, null, null, null, null, null, Battery.HISTORY_OBSERVED + " DESC");

            if (c.moveToNext()) {
                me.mLastTimestamp = c.getLong(c.getColumnIndex(Battery.HISTORY_OBSERVED));
            }

            c.close();
        }

        return me.mLastTimestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(Battery.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }
}