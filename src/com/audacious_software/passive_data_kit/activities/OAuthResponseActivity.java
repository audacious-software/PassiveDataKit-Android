package com.audacious_software.passive_data_kit.activities;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.audacious_software.passive_data_kit.generators.wearables.WithingsDevice;
import com.audacious_software.pdk.passivedatakit.R;

/**
 * Created by cjkarr on 3/18/2017.
 */

public class OAuthResponseActivity extends AppCompatActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    protected void onResume() {
        super.onResume();

        Uri u = this.getIntent().getData();

        Log.e("PDK", "CALLBACK FROM OAUTH: " + u);
        Log.e("PDK", "PATH: " + u.getPath());

        if (u.getPath().startsWith(WithingsDevice.API_OAUTH_CALLBACK_PATH)) {
            WithingsDevice.getInstance(this).finishAuthentication(u);
        }
    }
}
