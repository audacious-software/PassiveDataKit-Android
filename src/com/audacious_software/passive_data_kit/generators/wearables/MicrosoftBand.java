package com.audacious_software.passive_data_kit.generators.wearables;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.activities.generators.MicrosoftBandAuthActivity;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.pdk.passivedatakit.R;
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.BandPendingResult;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;

import java.util.ArrayList;

@SuppressWarnings("unused")
public class MicrosoftBand extends Generator
{
    private static final String ENABLED = "com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand.ENABLED";
    private static final boolean ENABLED_DEFAULT = true;

    private BandClient mBandClient = null;
    private static MicrosoftBand sInstance = null;

    public static MicrosoftBand getInstance(Context context) {
        if (MicrosoftBand.sInstance == null) {
            MicrosoftBand.sInstance = new MicrosoftBand(context.getApplicationContext());
        }

        return MicrosoftBand.sInstance;
    }

    public MicrosoftBand(Context context) {
        super(context);
    }

    public static void start(final Context context) {
        MicrosoftBand.getInstance(context).startGenerator();
    }

    private void startGenerator() {
        final MicrosoftBand me = this;

        Runnable r = new Runnable()
        {
            @Override
            public void run() {
                BandInfo[] pairedBands = BandClientManager.getInstance().getPairedBands();

                if (pairedBands.length > 0) {
                    me.mBandClient = BandClientManager.getInstance().create(me.mContext, pairedBands[0]);

                    BandPendingResult<ConnectionState> pendingResult = me.mBandClient.connect();

                    try {
                        ConnectionState state = pendingResult.await();

                        if (state == ConnectionState.CONNECTED) {
                            try {
                                me.mBandClient.getSensorManager().registerHeartRateEventListener(new BandHeartRateEventListener() {
                                    @Override
                                    public void onBandHeartRateChanged(BandHeartRateEvent event) {
                                        Log.e("PDK", "GOT HEART RATE: " + event.getHeartRate() + " (" + event.getQuality() + ")");
                                    }
                                });
                            } catch(BandIOException e) {
                                Logger.getInstance(me.mContext).logThrowable(e);
                            }

                        } else {
                            // do work on failure
                        }
                    } catch(InterruptedException|BandException e) {
                        Logger.getInstance(me.mContext).logThrowable(e);
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

    public static ArrayList<DiagnosticAction> diagnostics(Context context)
    {
        return MicrosoftBand.getInstance(context).runDiagostics();
    }

    private ArrayList<DiagnosticAction> runDiagostics() {
        ArrayList<DiagnosticAction> actions = new ArrayList<>();

        Log.e("PDK", "MSFT BAND ENABLED: " + MicrosoftBand.isEnabled(this.mContext));

        final Handler handler = new Handler(Looper.getMainLooper());

        if (MicrosoftBand.isEnabled(this.mContext)) {
            final MicrosoftBand me = this;

            if (MicrosoftBand.sInstance.mBandClient == null) {
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_msft_band_client), new Runnable() {

                    @Override
                    public void run() {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(me.mContext, "UnAbLe to CONNect TO Band. Verify BAnd iS nearBy. (1)", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }));
            } else if (!MicrosoftBand.sInstance.mBandClient.isConnected()) {
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_msft_band_client), new Runnable() {

                    @Override
                    public void run() {
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                Toast.makeText(me.mContext, "UnAbLe to CONNect TO Band. Verify BAnd iS nearBy. (2)", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }));
            }
            else if(me.mBandClient.getSensorManager().getCurrentHeartRateConsent() != UserConsent.GRANTED) {
                actions.add(new DiagnosticAction(me.mContext.getString(R.string.diagnostic_missing_msft_band_auth), new Runnable() {

                    @Override
                    public void run() {
                        Intent authIntent = new Intent(me.mContext, MicrosoftBandAuthActivity.class);
                        authIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        me.mContext.startActivity(authIntent);
                    }
                }));
            }

        }

        return actions;
    }

    public static void stop(Context context) {
        if (MicrosoftBand.sInstance != null) {
            if (MicrosoftBand.sInstance.mBandClient != null) {
                if (MicrosoftBand.sInstance.mBandClient.isConnected()) {
                    MicrosoftBand.sInstance.mBandClient.disconnect();
                }

                MicrosoftBand.sInstance.mBandClient = null;
            }
        }
    }

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = Generators.getInstance(context).getSharedPreferences(context);

        return prefs.getBoolean(MicrosoftBand.ENABLED, MicrosoftBand.ENABLED_DEFAULT);
    }

    public static boolean isRunning(Context context) {
        if (MicrosoftBand.sInstance == null) {
            return false;
        }

        return MicrosoftBand.sInstance.mBandClient != null;
    }
}
