package com.audacious_software.passive_data_kit.generators.wearables;

import android.content.Context;

import com.audacious_software.passive_data_kit.generators.Generator;
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandPendingResult;
import com.microsoft.band.ConnectionState;

public class MicrosoftBand extends Generator
{
    private BandClient mBandClient = null;

    @Override
    public void start(final Context context) {
        final MicrosoftBand me = this;

        Runnable r = new Runnable()
        {
            @Override
            public void run() {
                BandInfo[] pairedBands = BandClientManager.getInstance().getPairedBands();

                if (pairedBands.length > 0) {
                    me.mBandClient = BandClientManager.getInstance().create(context, pairedBands[0]);

                    BandPendingResult<ConnectionState> pendingResult = me.mBandClient.connect();

                    try {
                        ConnectionState state = pendingResult.await();

                        if(state == ConnectionState.CONNECTED) {
                            // do work on success
                        } else {
                            // do work on failure
                        }
                    } catch(InterruptedException ex) {
                        // handle InterruptedException
                    } catch(BandException ex) {
                        // handle BandException
                    }
                }
                else
                {
                    // Log error about being unable to connect to band...
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    @Override
    public void stop(Context context) {

    }

    @Override
    public boolean isEnabled(Context context) {
        return false;
    }

    @Override
    public boolean isRunning(Context context) {
        return false;
    }
}
