package com.audacious_software.passive_data_kit.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.pdk.passivedatakit.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;

public class TransmissionActivity extends Activity {
    public static final String LAST_TRANSMISSION = "com.audacious_software.passive_data_kit.activities.TransmissionActivity.LAST_TRANSMISSION";
    public static final String APP_NAME = "com.audacious_software.passive_data_kit.activities.TransmissionActivity.APP_NAME";

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = this.getIntent();

        String appName = intent.getStringExtra(TransmissionActivity.APP_NAME);
        long lastTransmission = intent.getLongExtra(TransmissionActivity.LAST_TRANSMISSION, 0);

        DateFormat format = android.text.format.DateFormat.getLongDateFormat(this);

        String message = this.getString(R.string.message_transmission_overdue, appName, format.format(new Date(lastTransmission)));

        final TransmissionActivity me = this;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.title_transmission_overdue);
        builder.setMessage(message);

        builder.setPositiveButton(R.string.action_transmit_now, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        PassiveDataKit.getInstance(me).transmitData(true);

                        Toast.makeText(me, R.string.toast_attempting_transmission_data, Toast.LENGTH_LONG).show();

                        HashMap<String, Object> payload = new HashMap<>();

                        Logger.getInstance(me).log("pdk-transmission-warning-dialog-transmit-now", payload);

                        me.finish();
                    }
                });

        builder.setNeutralButton(R.string.action_transmit_later, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                HashMap<String, Object> payload = new HashMap<>();

                Logger.getInstance(me).log("pdk-transmission-warning-dialog-later", payload);

                me.finish();
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        HashMap<String, Object> payload = new HashMap<>();

                        Logger.getInstance(me).log("pdk-transmission-warning-dialog-dismissed", payload);

                        me.finish();
                    }
                });

        builder.create().show();

        PassiveDataKit.getInstance(this).logMaintenanceAppearance("pdk-transmission-warning-activity", System.currentTimeMillis());
    }
}
