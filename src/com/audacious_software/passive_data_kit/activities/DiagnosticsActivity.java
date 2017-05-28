package com.audacious_software.passive_data_kit.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.pdk.passivedatakit.R;

import java.util.ArrayList;

public class DiagnosticsActivity extends AppCompatActivity {
    @SuppressLint("AlwaysShowAction")
    public static void setUpDiagnositicsItem(Activity activity, Menu menu, boolean showAction) {
        final ArrayList<DiagnosticAction> actions = PassiveDataKit.diagnostics(activity);

        MenuItem item = menu.add(Menu.NONE, R.id.action_diagnostics, 0, activity.getString(R.string.action_diagnostics));

        if (actions.size() > 0 && showAction) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

            item.setIcon(R.drawable.ic_pdk_diagnostic);
            item.setTitle("" + actions.size());
        } else {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            if (actions.size() > 0) {
                item.setTitle(activity.getString(R.string.action_diagnostics_incomplete, actions.size()));
            }
        }
    }

    public static boolean diagnosticItemSelected(Activity activity, MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_diagnostics) {
            Intent diagnosticsIntent = new Intent(activity, DiagnosticsActivity.class);
            activity.startActivity(diagnosticsIntent);

            return true;
        }

        return false;
    }

    private class DiagnosticViewHolder extends RecyclerView.ViewHolder {

        private View mView = null;
        private DiagnosticAction mAction = null;

        public DiagnosticViewHolder(View itemView) {
            super(itemView);

            this.mView = itemView;

            final DiagnosticViewHolder me = this;

            this.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (me.mAction != null)
                    {
                        me.mAction.run();
                    }
                }
            });
        }

        public void bindDiagnosticAction(DiagnosticAction action)
        {
            this.mAction = action;

            TextView title = (TextView) this.mView.findViewById(R.id.action_title);
            title.setText(action.getTitle());

            TextView message = (TextView) this.mView.findViewById(R.id.action_message);
            message.setText(action.getMessage());
        }
    }

    @SuppressWarnings("ConstantConditions")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.layout_diagnostics_pdk);
        this.getSupportActionBar().setTitle(R.string.title_pdk_diagnostics);
    }

    protected void onResume() {
        super.onResume();

        final ArrayList<DiagnosticAction> actions = PassiveDataKit.diagnostics(this);

        RecyclerView listView = (RecyclerView) this.findViewById(R.id.list_view);
        TextView emptyMessage = (TextView) this.findViewById(R.id.message_no_diagnostics);

        if (actions.size() > 0) {
            listView.setVisibility(View.VISIBLE);
            emptyMessage.setVisibility(View.GONE);

            listView.setLayoutManager(new LinearLayoutManager(this));

            listView.setAdapter(new RecyclerView.Adapter() {
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_diagnostic_action, parent, false);

                    return new DiagnosticViewHolder(v);
                }

                @Override
                public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    DiagnosticViewHolder diagHolder = (DiagnosticViewHolder) holder;

                    diagHolder.bindDiagnosticAction(actions.get(position));
                }

                @Override
                public int getItemCount() {
                    return actions.size();
                }
            });
        } else {
            listView.setVisibility(View.GONE);
            emptyMessage.setVisibility(View.VISIBLE);
        }
    }
}
