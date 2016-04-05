package com.audacious_software.passive_data_kit;

public class DeviceInformation {
    public static boolean isKindleFire()
    {
        boolean isKindle = android.os.Build.MANUFACTURER.equalsIgnoreCase("Amazon")
                && ((android.os.Build.MODEL.equalsIgnoreCase("Kindle Fire")
                || android.os.Build.MODEL.startsWith("KF") || android.os.Build.MODEL.startsWith("AFT")
                || android.os.Build.MODEL.startsWith("SD")));

        return isKindle;
    }
}
