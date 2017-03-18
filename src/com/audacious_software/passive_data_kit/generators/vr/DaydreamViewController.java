package com.audacious_software.passive_data_kit.generators.vr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.audacious_software.passive_data_kit.activities.generators.DataPointViewHolder;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.google.vr.sdk.controller.Controller;
import com.google.vr.sdk.controller.ControllerManager;

import java.util.ArrayList;

/**
 * Created by cjkarr on 11/20/2016.
 */

public class DaydreamViewController extends Generator  {
    private static final String GENERATOR_IDENTIFIER = "pdk-daydream-vr-controller";

    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.vr.DaydreamViewController.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private static DaydreamViewController sInstance = null;

    private BroadcastReceiver mReceiver = null;
    private ControllerManager mControllerManager = null;
    private Controller mController = null;

    public static DaydreamViewController getInstance(Context context) {
        if (DaydreamViewController.sInstance == null) {
            DaydreamViewController.sInstance = new DaydreamViewController(context.getApplicationContext());
        }

        return DaydreamViewController.sInstance;
    }

    public DaydreamViewController(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        DaydreamViewController.getInstance(context).startGenerator();

        DaydreamViewController.sInstance.mControllerManager = new ControllerManager(context, new ControllerManager.EventListener() {
            @Override
            public void onApiStatusChanged(int status) {
                Log.e("PDK", "DVC STATUS CHANGE: " + status);
            }

            @Override
            public void onRecentered() {
                Log.e("PDK", "DVC RECENTERED");
            }
        });

        DaydreamViewController.sInstance.mController = DaydreamViewController.sInstance.mControllerManager.getController();
        DaydreamViewController.sInstance.mController.setEventListener(new Controller.EventListener() {
            public void onConnectionStateChanged (int state) {
                super.onConnectionStateChanged(state);

                Log.e("PDK", "DVC STATE CHANGE: " + state);
            }

            public void onUpdate() {
                super.onUpdate();

                Log.e("PDK", "DVC UPDATE");
            }
        });

        DaydreamViewController.sInstance.mControllerManager.start();
    }

    private void startGenerator() {
        Generators.getInstance(this.mContext).registerCustomViewClass(DaydreamViewController.GENERATOR_IDENTIFIER, DaydreamViewController.class);
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(DaydreamViewController.ENABLED, DaydreamViewController.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (DaydreamViewController.sInstance == null) {
            return false;
        }

        return DaydreamViewController.sInstance.mReceiver != null;
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        return new ArrayList<>();
    }

    public static void bindViewHolder(DataPointViewHolder holder, final Bundle dataPoint) {
        final Context context = holder.itemView.getContext();

        /*
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            JSONArray history = new JSONArray(prefs.getString(ScreenState.SCREEN_HISTORY_KEY, "[]"));

//            Log.e("PDK", "SCREEN HISTORY: " + history.toString(2));

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            long zeroStart = cal.getTimeInMillis();
            cal.add(Calendar.DATE, -1);

            LinearLayout zeroTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_zero_value);

            ScreenState.populateTimeline(context, zeroTimeline, zeroStart, history);

            long oneStart = cal.getTimeInMillis();
            cal.add(Calendar.DATE, -1);

            LinearLayout oneTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_one_value);

            ScreenState.populateTimeline(context, oneTimeline, oneStart, history);

            long twoStart = cal.getTimeInMillis();

            LinearLayout twoTimeline = (LinearLayout) holder.itemView.findViewById(R.id.day_two_value);

            ScreenState.populateTimeline(context, twoTimeline, twoStart, history);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        double timestamp = dataPoint.getBundle(Generator.PDK_METADATA).getDouble(Generator.TIMESTAMP);

        TextView dateLabel = (TextView) holder.itemView.findViewById(R.id.generator_data_point_date);

        dateLabel.setText(Generator.formatTimestamp(context, timestamp));

        Calendar cal = Calendar.getInstance();
        DateFormat format = android.text.format.DateFormat.getDateFormat(context);

        TextView zeroDayLabel = (TextView) holder.itemView.findViewById(R.id.day_zero_label);
        zeroDayLabel.setText(format.format(cal.getTime()));

        cal.add(Calendar.DATE, -1);

        TextView oneDayLabel = (TextView) holder.itemView.findViewById(R.id.day_one_label);
        oneDayLabel.setText(format.format(cal.getTime()));

        cal.add(Calendar.DATE, -1);

        TextView twoDayLabel = (TextView) holder.itemView.findViewById(R.id.day_two_label);
        twoDayLabel.setText(format.format(cal.getTime()));

        */
    }

    public static View fetchView(ViewGroup parent)
    {
        return LayoutInflater.from(parent.getContext()).inflate(R.layout.card_generator_daydream_vr_controller, parent, false);
    }

    public static void broadcastLatestDataPoint(Context context) {
        Generators.getInstance(context).transmitData(DaydreamViewController.GENERATOR_IDENTIFIER, new Bundle());
    }

}
