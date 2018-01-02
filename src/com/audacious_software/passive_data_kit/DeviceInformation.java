package com.audacious_software.passive_data_kit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;

public class DeviceInformation {
    private static boolean sWifiAvailable = false;
    private static long sLastWifiCheck = 0;

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static boolean isKindleFire()
    {
        boolean isKindle = android.os.Build.MANUFACTURER.equalsIgnoreCase("Amazon")
                && ((android.os.Build.MODEL.equalsIgnoreCase("Kindle Fire")
                || android.os.Build.MODEL.startsWith("KF") || android.os.Build.MODEL.startsWith("AFT")
                || android.os.Build.MODEL.startsWith("SD")));

        return isKindle;
    }

    public static boolean wifiAvailable(Context context) {
        long now = System.currentTimeMillis();

        if (now - DeviceInformation.sLastWifiCheck > 10000) {
            DeviceInformation.sLastWifiCheck = now;

            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            if (wifi.isWifiEnabled()) {
                DeviceInformation.sWifiAvailable = true;

                ConnectivityManager connection = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo netInfo = connection.getActiveNetworkInfo();

                if (netInfo != null) {
                    if (netInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                        DeviceInformation.sWifiAvailable = false;
                    }
                    else if (netInfo.getState() != NetworkInfo.State.CONNECTED
                            && netInfo.getState() != NetworkInfo.State.CONNECTING) {
                        DeviceInformation.sWifiAvailable = false;
                    }
                }
                else {
                    DeviceInformation.sWifiAvailable = false;
                }
            }
            else {
                DeviceInformation.sWifiAvailable = false;
            }
        }

        return DeviceInformation.sWifiAvailable;
    }

    @SuppressLint("InlinedApi")
    @SuppressWarnings("ConstantConditions")
    public static boolean isPluggedIn(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

        return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }
}
