package com.rvalerio.fgchecker.detectors;

import android.annotation.TargetApi;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;

import com.rvalerio.fgchecker.Utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class LollipopDetector implements Detector {
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public String getForegroundApp(final Context context) {
        if(!Utils.hasUsageStatsPermission(context)) {
            return null;
        }

        String foregroundApp = null;

        UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Service.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();

        UsageEvents usageEvents = mUsageStatsManager.queryEvents(time - 1000 * 3600, time);

        UsageEvents.Event event = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);

            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foregroundApp = event.getPackageName();
            }
        }

        return foregroundApp ;
    }

    @Override
    public String[] getForegroundApps(Context context) {
        if (!Utils.hasUsageStatsPermission(context)) {
            return new String[]{ null };
        }

        HashSet<String> foreground = new HashSet<>();

        UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Service.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();

        UsageEvents usageEvents = mUsageStatsManager.queryEvents(time - 1000 * 3600, time);
        UsageEvents.Event event = new UsageEvents.Event();

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);

            String packageName = event.getPackageName();

            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                foreground.add(packageName);
            } else if (event.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                foreground.remove(packageName);
            }

            if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                foreground.add(packageName);
            }

            if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED) {
                foreground.remove(packageName);
            }

            if (event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED) {
                foreground.remove(packageName);
            }
        }

        PackageManager manager = context.getPackageManager();

        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.addCategory(Intent.CATEGORY_DEFAULT);

        List<ResolveInfo> startMatches = manager.queryIntentActivities(startMain, 0);

        if (foreground.size() > 1) {
            ArrayList<String> toRemove = new ArrayList<>();

            for (String app : foreground) {
                for (ResolveInfo info : startMatches) {
                    if (info.activityInfo.packageName != null && info.activityInfo.packageName.equals(app)) {
                        toRemove.add(app);
                    }
                }
            }

            foreground.removeAll(toRemove);
        }

        return foreground.toArray(new String[] {});
    }
}
