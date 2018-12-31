package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.transmitters.Transmitter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.core.content.ContextCompat;

@SuppressWarnings("PointlessBooleanExpression")
public class PassiveDataKit {
    private static final String STORAGE_PATH = "passive-data-kit";
    private static final String GENERATORS_PATH = "generators";
    public static final String NOTIFICATION_CHANNEL_ID = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_CHANNEL_ID";
    public static final String NOTIFICATION_ICON_ID = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_ICON_ID";
    public static final String NOTIFICATION_COLOR = "com.audacious_software.passive_data_kit.PassiveDataKit.NOTIFICATION_COLOR";

    private Context mContext = null;
    private boolean mStarted = false;
    private boolean mStartForegroundService = false;
    private String mForegroundChannelId = null;
    private int mForegroundIconId = 0;
    private int mForegroundColor = 0;
    private PendingIntent mForegroundPendingIntent = null;
    private boolean mAlwaysNotify = false;

    public void start() {
        synchronized (this) {
            if (!this.mStarted) {
                this.mStarted = true;

                final PassiveDataKit me = this;

                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        Generators.getInstance(me.mContext).start();
                        Logger.getInstance(me.mContext);
                    }
                };

                Thread t = new Thread(r);
                t.start();

                Boolean notificationStarted = false;
                if (this.mStartForegroundService || this.mAlwaysNotify) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent intent = new Intent(ForegroundService.ACTION_START_SERVICE, null, this.mContext, ForegroundService.class);

                        Log.e("PDK", "HAS CHANNEL ID: " + this.mForegroundChannelId);

                        if (this.mForegroundChannelId != null) {
                            intent.putExtra(PassiveDataKit.NOTIFICATION_CHANNEL_ID, this.mForegroundChannelId);
                        }

                        if (this.mForegroundIconId != 0) {
                            intent.putExtra(PassiveDataKit.NOTIFICATION_ICON_ID, this.mForegroundIconId);
                        }

                        if (this.mForegroundColor != 0) {
                            intent.putExtra(PassiveDataKit.NOTIFICATION_COLOR, this.mForegroundColor);
                        }

                        ContextCompat.startForegroundService(this.mContext, intent);

                        notificationStarted = true;
                    }
                }

                if (this.mAlwaysNotify && notificationStarted == false) {
                    Notification note = ForegroundService.getForegroundNotification(this.mContext, null);

                    NotificationManager notes = (NotificationManager) this.mContext.getSystemService(Context.NOTIFICATION_SERVICE);

                    notes.notify(ForegroundService.getNotificationId(), note);
                }
            }
        }
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context) {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        actions.addAll(Generators.getInstance(context).diagnostics(context));

        return actions;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static File getGeneratorsStorage(Context context) {
        File path = new File(context.getFilesDir(), PassiveDataKit.STORAGE_PATH);
        path = new File(path, PassiveDataKit.GENERATORS_PATH);

        if (path.exists() == false) {
            path.mkdirs();
        }

        return path;
    }

    @SuppressWarnings("unused")
    public void setStartForegroundService(boolean startService) {
        this.mStartForegroundService = startService;
    }

    public void setAlwaysNotify(boolean always) {
        this.mAlwaysNotify = always;
    }

    @SuppressWarnings("unused")
    public void setForegroundServiceChannelId(String channelId) {
        this.mForegroundChannelId = channelId;
    }

    @SuppressWarnings("unused")
    public void setForegroundServiceIcon(int resourceId) {
        this.mForegroundIconId = resourceId;
    }

    @SuppressWarnings("unused")
    public void setForegroundServiceColor(int color) {
        this.mForegroundColor = color;
    }

    public PendingIntent getForegroundPendingIntent() {
        return this.mForegroundPendingIntent;
    }

    public void setForegroundPendingIntent(PendingIntent pendingIntent) {
        this.mForegroundPendingIntent = pendingIntent;
    }

    private static class PassiveDataKitHolder {
        @SuppressLint("StaticFieldLeak")
        public static final PassiveDataKit instance = new PassiveDataKit();
    }

    @SuppressWarnings("SameReturnValue")
    public static PassiveDataKit getInstance(Context context)
    {
        PassiveDataKitHolder.instance.setContext(context.getApplicationContext());

        return PassiveDataKitHolder.instance;
    }

    private void setContext(Context context) {
        this.mContext = context;
    }

    public long pendingTransmissions() {
        long pending = 0;

        List<Transmitter> transmitters = Generators.getInstance(this.mContext).activeTransmitters();

        for (Transmitter transmitted : transmitters) {
            pending += transmitted.pendingTransmissions();
        }

        return pending;
    }
}
