package com.audacious_software.passive_data_kit.transmitters;

import android.content.Context;
import android.os.Handler;

import java.util.HashMap;

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class Transmitter {
    public static final String FAILURE_REASON = "com.audacious_software.passive_data_kit.transmitters.FAILURE_REASON";
    public static final String FAILURE_TIMESTAMP = "com.audacious_software.passive_data_kit.transmitters.FAILURE_TIMESTAMP";

    @SuppressWarnings("unused")
    public abstract void initialize(Context context, HashMap<String, String> options);

    @SuppressWarnings({"SameParameterValue", "unused"})
    public abstract boolean transmit(boolean force);

    @SuppressWarnings("unused")
    public abstract long pendingSize();

    @SuppressWarnings("unused")
    public abstract long transmittedSize();

    public abstract long pendingTransmissions();

    public abstract long pendingTransmissionSize();

    public abstract long lastSuccessfulTransmission();

    public abstract void testTransmission(Handler handler, boolean includeLocation, Runnable success, Runnable failure);
}
