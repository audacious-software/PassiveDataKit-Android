package com.audacious_software.passive_data_kit;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.audacious_software.pdk.passivedatakit.R;

public class ForegroundService extends Service {
    public static final String ACTION_START_SERVICE = "com.audacious_software.passive_data_kit.ForegroundService.ACTION_START_SERVICE";

    private static final int NOTIFICATION_ID = 778642;

    private static Intent mLastIntent = null;

    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onCreate();

        ForegroundService.mLastIntent = intent;

        this.startForeground(ForegroundService.NOTIFICATION_ID, ForegroundService.getForegroundNotification(this));

        return Service.START_STICKY;
    }

    public static int getNotificationId() {
        return ForegroundService.NOTIFICATION_ID;
    }

    public static Notification getForegroundNotification(Context context) {
        Intent intent = ForegroundService.mLastIntent;

        if (intent != null) {
            String channelId = intent.getStringExtra(PassiveDataKit.NOTIFICATION_CHANNEL_ID);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId);
            builder.setContentTitle(context.getString(R.string.foreground_service_title));
            builder.setContentText(context.getString(R.string.foreground_service_message));
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
            builder.setSound(null);

            builder.setSmallIcon(intent.getIntExtra(PassiveDataKit.NOTIFICATION_ICON_ID, R.drawable.ic_foreground_service));

            if (intent.hasExtra(PassiveDataKit.NOTIFICATION_COLOR)) {
                builder.setColor(intent.getIntExtra(PassiveDataKit.NOTIFICATION_COLOR, 0));
            }

            PendingIntent pendingIntent = PassiveDataKit.getInstance(context).getForegroundPendingIntent();

            if (pendingIntent == null) {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getApplicationContext().getPackageName());

                pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, 0);
            }

            builder.setContentIntent(pendingIntent);

            return builder.build();
        }

        return null;
    }
}
