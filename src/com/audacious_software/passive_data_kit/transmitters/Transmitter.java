package com.audacious_software.passive_data_kit.transmitters;

import android.content.Context;

import java.util.HashMap;

public abstract class Transmitter {
    public abstract void initialize(Context context, HashMap<String, String> options);
    public abstract void transmit(boolean force);
    public abstract long pendingSize();
    public abstract long transmittedSize();
}
