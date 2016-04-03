package com.audacious_software.passive_data_kit.activities.generators;

import android.app.Activity;

import com.audacious_software.passive_data_kit.generators.wearables.MicrosoftBand;
import com.microsoft.band.BandClient;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandInfo;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.HeartRateConsentListener;

public class MicrosoftBandAuthActivity extends Activity {
    protected void onResume(){
        super.onResume();

        final MicrosoftBandAuthActivity me = this;
        MicrosoftBand.stop(this);

        BandInfo[] pairedBands = BandClientManager.getInstance().getPairedBands();

        BandClient client = BandClientManager.getInstance().create(this, pairedBands[0]);

        if(client.getSensorManager().getCurrentHeartRateConsent() != UserConsent.GRANTED) {
            client.getSensorManager().requestHeartRateConsent(this, new HeartRateConsentListener() {
                @Override
                public void userAccepted(boolean accepted) {
                    MicrosoftBand.start(me);
                }
            });
        }

//        this.finish();
    }
}
