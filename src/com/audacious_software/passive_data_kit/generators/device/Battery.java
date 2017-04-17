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

import java.io.File;
import java.util.ArrayList;
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

        this.mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, Intent intent) {
                long now = System.currentTimeMillis();

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

        Cursor c = generator.mDatabase.query(Battery.TABLE_HISTORY, null, null, null, null, null, Battery.HISTORY_OBSERVED + " DESC");

        View cardContent = holder.itemView.findViewById(R.id.card_content);
        View cardEmpty = holder.itemView.findViewById(R.id.card_empty);
        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        if (c.moveToNext()) {
            cardContent.setVisibility(View.VISIBLE);
            cardEmpty.setVisibility(View.GONE);

            long timestamp = c.getLong(c.getColumnIndex(Battery.HISTORY_OBSERVED)) / 1000;

            dateLabel.setText(Generator.formatTimestamp(context, timestamp));

            TextView lastLevel = (TextView) holder.itemView.findViewById(R.id.card_last_battery_level);
            lastLevel.setText("TODO: LEVEL " + c.getInt(c.getColumnIndex(Battery.HISTORY_LEVEL)));
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

        Battery me = Battery.getInstance(context);

        Cursor c = me.mDatabase.query(Battery.TABLE_HISTORY, null, null, null, null, null, Battery.HISTORY_OBSERVED + " DESC");

        if (c.moveToNext()) {
            timestamp = c.getLong(c.getColumnIndex(Battery.HISTORY_OBSERVED));
        }

        c.close();

        return timestamp;
    }

    public Cursor queryHistory(String[] cols, String where, String[] args, String orderBy) {
        return this.mDatabase.query(Battery.TABLE_HISTORY, cols, where, args, null, null, orderBy);
    }
}
