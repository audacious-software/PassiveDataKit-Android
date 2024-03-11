package com.audacious_software.passive_data_kit.activities.generators;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class DataPointViewHolder extends RecyclerView.ViewHolder {
    private Runnable mOnAttach;
    private Runnable mOnDetach;

    public DataPointViewHolder(View itemView) {
        super(itemView);
    }

    public void setOnAttachListener(Runnable onAttach) {
        this.mOnAttach = onAttach;
    }

    public void setOnDetachListener(Runnable onDetach) {
        this.mOnDetach = onDetach;
    }

    public void runOnAttach() {
        if (this.mOnAttach != null) {
            this.mOnAttach.run();
        }
    }

    public void runOnDetach() {
        if (this.mOnDetach != null) {
            this.mOnDetach.run();
        }
    }
}
