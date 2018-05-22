package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.content.ContextCompat;

import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generators;

import java.io.File;
import java.util.ArrayList;

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

                if (this.mStartForegroundService) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent intent = new Intent(ForegroundService.ACTION_START_SERVICE, null, this.mContext, ForegroundService.class);

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
                    }
                }
            }
        }
    }

    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
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
}
