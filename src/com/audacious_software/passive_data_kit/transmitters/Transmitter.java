package com.audacious_software.passive_data_kit.transmitters;

import android.content.Context;

import java.util.HashMap;

@SuppressWarnings("WeakerAccess")
public abstract class Transmitter {
    @SuppressWarnings("unused")
    public abstract void initialize(Context context, HashMap<String, String> options);

    @SuppressWarnings({"SameParameterValue", "unused"})
    public abstract void transmit(boolean force);

    @SuppressWarnings("unused")
    public abstract long pendingSize();

    @SuppressWarnings("unused")
    public abstract long transmittedSize();
}
