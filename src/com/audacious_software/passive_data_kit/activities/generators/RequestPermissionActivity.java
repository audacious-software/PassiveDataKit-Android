package com.audacious_software.passive_data_kit.activities.generators;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import com.audacious_software.passive_data_kit.generators.services.GoogleFit;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataType;

public class RequestPermissionActivity extends Activity
{
    public static final String PERMISSION = "com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity.PERMISSION";

    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 86753;

    protected void onResume()
    {
        super.onResume();

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        this.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
                GoogleFit.stop(this);
                GoogleFit.start(this);

                this.finish();
            }
        }
    }
}
