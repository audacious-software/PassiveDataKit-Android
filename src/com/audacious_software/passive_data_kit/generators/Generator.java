package com.audacious_software.passive_data_kit.generators;

import android.content.Context;

/**
 * Created by cjkarr on 3/20/2016.
 */
public abstract class Generator
{
    protected Context mContext = null;

    public Generator(Context context)
    {
        this.mContext = context.getApplicationContext();
    }

    public static void start(Context context)
    {
        // Do nothing - override in subclasses...
    }

    public static void stop(Context context)
    {
        // Do nothing - override in subclasses.
    }

    public static boolean isEnabled(Context context)
    {
        return false;
    }

    public static boolean isRunning(Context context)
    {
        return false;
    }
}
