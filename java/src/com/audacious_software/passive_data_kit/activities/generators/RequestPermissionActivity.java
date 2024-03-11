package com.audacious_software.passive_data_kit.activities.generators;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.services.GoogleFit;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataType;

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class RequestPermissionActivity extends Activity
{
    public static final String PERMISSION = "com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity.PERMISSION";

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 86753;

    private boolean mIsRequesting = false;

    protected void onCreate(Bundle data) {
        super.onCreate(data);

        this.mIsRequesting = false;
    }

    protected void onResume() {
        super.onResume();

        if (this.mIsRequesting == false) {
            this.mIsRequesting = true;

            Bundle extras = this.getIntent().getExtras();

            String permission = extras.getString(RequestPermissionActivity.PERMISSION);

            if (GoogleFit.GOOGLE_FIT_PERMISSIONS.equals(permission)) {
                FitnessOptions.Builder builder = FitnessOptions.builder();

                for (DataType type : GoogleFit.getInstance(this).allDataTypes()) {
                    if (extras.getBoolean(type.getName(), false)) {
                        builder.addDataType(type, FitnessOptions.ACCESS_READ);
                    }
                }

                FitnessOptions options = builder.build();

                GoogleSignIn.requestPermissions(this, RequestPermissionActivity.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE, GoogleSignIn.getLastSignedInAccount(this), options);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{permission}, 0);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Bundle extras = this.getIntent().getExtras();
        String permission = extras.getString(RequestPermissionActivity.PERMISSION);

        /*
        if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permission) || Manifest.permission.ACCESS_COARSE_LOCATION.equals(permission)) {
            Location.getInstance(this).stopGenerator();
            Location.getInstance(this).startGenerator();
        }
        */

        this.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                GoogleFit.stop(this);
                GoogleFit.start(this);

                this.finish();
            } else {
                HashMap<String, Object> payload = new HashMap<>();
                payload.put("request-code", requestCode);
                payload.put("result-code", resultCode);

                Logger.getInstance(this).log("pdk-fit-permission-failed", payload);
            }
        }
    }
}
