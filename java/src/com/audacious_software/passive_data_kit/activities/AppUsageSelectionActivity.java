package com.audacious_software.passive_data_kit.activities;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;


import com.audacious_software.passive_data_kit.R;
import com.audacious_software.passive_data_kit.activities.generators.DataPointsAdapter;

import java.util.ArrayList;
import java.util.List;

public class AppUsageSelectionActivity extends AppCompatActivity {
    private Menu mMenu = null;
    private boolean mIsUpdating = false;

    @SuppressWarnings({"unchecked", "ConstantConditions"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AppUsageSelectionActivity me = this;

        this.setContentView(R.layout.layout_app_usage_selection);
        this.getSupportActionBar().setTitle(R.string.title_app_usage_selection);
        this.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final PackageManager packageManager = this.getPackageManager();

        List<PackageInfo> apps = new ArrayList<>();

        for (PackageInfo pkgInfo : packageManager.getInstalledPackages(PackageManager.GET_META_DATA)) {
            Intent intent = this.getPackageManager().getLaunchIntentForPackage(pkgInfo.packageName);

            if (intent != null) {
                apps.add(pkgInfo);
            }
        }

        Log.e("PDK", "FOUND " + apps.size() + " APPS");

        ListView actionsList = this.findViewById(R.id.app_selection_list);

        ArrayAdapter<PackageInfo> adapter = new ArrayAdapter<PackageInfo>(this, R.layout.row_app_selection, apps) {
            @NonNull
            @SuppressLint("InflateParams")
            public View getView (int position, View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(me).inflate(R.layout.row_app_selection, null);
                }

                Log.e("PDK", "FETCH APP  " + position);

                PackageInfo pkgInfo = apps.get(position);

                TextView title = convertView.findViewById(R.id.app_selection_name);
                title.setText(pkgInfo.applicationInfo.loadLabel(packageManager));

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    try {
                        ApplicationInfo applicationInfo = packageManager.getApplicationInfo(pkgInfo.packageName, 0);

                        int appCategory = applicationInfo.category;
                        String categoryTitle = (String) ApplicationInfo.getCategoryTitle(me, appCategory);

                        TextView description = convertView.findViewById(R.id.app_selection_description);
                        description.setText(categoryTitle);

                    } catch (PackageManager.NameNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }

                ImageView iconView = convertView.findViewById(R.id.app_selection_icon);
                iconView.setImageDrawable(pkgInfo.applicationInfo.loadIcon(packageManager));

                return convertView;
            }
        };

        actionsList.setAdapter(adapter);

        actionsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        this.getMenuInflater().inflate(R.menu.activity_select_apps, menu);

        this.mMenu = menu;

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            this.finish();
            return true;
        } else if (id == R.id.action_sort_apps) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle(R.string.title_sort_apps);

            builder.setItems(R.array.app_sort_options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {

                }
            });

            builder.create().show();

            return true;
        }

        return true;
    }
}