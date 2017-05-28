package com.audacious_software.passive_data_kit.activities.generators;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

public class RequestPermissionActivity extends Activity
{
    public static final String PERMISSION = "com.audacious_software.passive_data_kit.activities.generators.RequestPermissionActivity.PERMISSION";

    protected void onResume()
    {
        super.onResume();

        Bundle extras = this.getIntent().getExtras();

        ActivityCompat.requestPermissions(this, new String[]{ extras.getString(RequestPermissionActivity.PERMISSION)}, 0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        this.finish();
    }
}
