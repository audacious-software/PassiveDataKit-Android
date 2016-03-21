package com.audacious_software.passive_data_kit.generators;

import android.content.Context;

/**
 * Created by cjkarr on 3/20/2016.
 */
public abstract class Generator
{
    public abstract void start(Context context);
    public abstract void stop(Context context);

    public abstract boolean isEnabled(Context context);
    public abstract boolean isRunning(Context context);
}
