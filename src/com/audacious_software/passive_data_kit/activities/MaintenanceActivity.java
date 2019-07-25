package com.audacious_software.passive_data_kit.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.pdk.passivedatakit.R;

import java.util.HashMap;

public class MaintenanceActivity extends Activity {
    public static final String MESSAGE_TEXT = "com.audacious_software.passive_data_kit.activities.MaintenanceActivity.MESSAGE_TEXT";

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = this.getIntent();

        final MaintenanceActivity me = this;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        @SuppressLint("InflateParams")
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_maintenance, null, false);

        ProgressBar progress = content.findViewById(R.id.database_bar);

        progress.setIndeterminate(true);

        TextView message = this.findViewById(R.id.maintenance_message);

        String messageText = intent.getStringExtra(MaintenanceActivity.MESSAGE_TEXT);

        if (messageText == null) {
            messageText = this.getString(R.string.message_default_maintenance);
        }

        message.setText(messageText);

        builder.setView(content);

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("source", "user");

                Logger.getInstance(me).log("pdk-maintenance-dialog-dismissed", payload);

                me.finish();
            }
        });

        builder.create().show();

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("message", messageText);

        Logger.getInstance(this).log("pdk-maintenance-dialog-shown", payload);

        Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("source", "app");

                Logger.getInstance(me).log("pdk-maintenance-dialog-dismissed", payload);

                me.finish();
            }
        }, 5000);

        PassiveDataKit.getInstance(this).logMaintenanceAppearance("pdk-maintenance-activity", System.currentTimeMillis());
    }
}
