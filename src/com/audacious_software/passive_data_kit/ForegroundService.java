package com.audacious_software.passive_data_kit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.audacious_software.pdk.passivedatakit.R;

public class ForegroundService extends Service {

    public static final String ACTION_START_SERVICE = "com.audacious_software.passive_data_kit.ForegroundService.ACTION_START_SERVICE";

    private static final int NOTIFICATION_ID = 778642;

    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onCreate();

        String channelId = intent.getStringExtra(PassiveDataKit.NOTIFICATION_CHANNEL_ID);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
        builder.setContentTitle(this.getString(R.string.foreground_service_title));
        builder.setContentText(this.getString(R.string.foreground_service_message));

        builder.setSmallIcon(intent.getIntExtra(PassiveDataKit.NOTIFICATION_ICON_ID, R.drawable.ic_foreground_service));

        if (intent.hasExtra(PassiveDataKit.NOTIFICATION_COLOR)) {
            builder.setColor(intent.getIntExtra(PassiveDataKit.NOTIFICATION_COLOR, 0));
        }

        Log.e("PDK", "START STICKY SERVICE");

        this.startForeground(ForegroundService.NOTIFICATION_ID, builder.build());

        return Service.START_STICKY;
    }
}
