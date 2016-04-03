package com.audacious_software.passive_data_kit.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.audacious_software.passive_data_kit.PassiveDataKit;
import com.audacious_software.passive_data_kit.diagnostics.DiagnosticAction;
import com.audacious_software.pdk.passivedatakit.R;

import java.util.ArrayList;

public class DiagnosticsActivity extends AppCompatActivity {
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

            TextView message = (TextView) this.mView.findViewById(R.id.message_action);
            message.setText(action.getMessage());
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.layout_diagnostics_pdk);

        final ArrayList<DiagnosticAction> actions = PassiveDataKit.diagnostics(this);

        Log.e("PDK", "ACTIONS COUNT: " + actions.size());

        RecyclerView listView = (RecyclerView) this.findViewById(R.id.list_view);

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
    }
}
