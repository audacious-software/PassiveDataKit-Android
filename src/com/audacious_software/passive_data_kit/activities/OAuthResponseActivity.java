package com.audacious_software.passive_data_kit.activities;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;

import com.audacious_software.passive_data_kit.generators.wearables.WithingsDevice;

@SuppressLint("Registered")
public class OAuthResponseActivity extends AppCompatActivity {
    protected void onResume() {
        super.onResume();

        Uri u = this.getIntent().getData();

        if (u.getPath().startsWith(WithingsDevice.API_OAUTH_CALLBACK_PATH)) {
            WithingsDevice.getInstance(this).finishAuthentication(u);
        }
    }
}
