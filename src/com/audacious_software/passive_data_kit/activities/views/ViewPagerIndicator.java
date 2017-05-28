package com.audacious_software.passive_data_kit.activities.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.audacious_software.pdk.passivedatakit.R;

import java.util.ArrayList;

public class ViewPagerIndicator extends LinearLayout {
    private final LinearLayout mIndicatorLayout;

    public ViewPagerIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);

        LinearLayout.LayoutParams borderParams = new LinearLayout.LayoutParams(12, 12);
        borderParams.weight = 1;

        View left = new View(context);
        left.setLayoutParams(borderParams);
        left.setVisibility(View.VISIBLE);

        this.addView(left);

        this.mIndicatorLayout = new LinearLayout(context);
        this.mIndicatorLayout.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        this.mIndicatorLayout.setLayoutParams(indicatorParams);
        this.mIndicatorLayout.setVisibility(View.VISIBLE);

        this.addView(this.mIndicatorLayout);

        View right = new View(context);
        right.setLayoutParams(borderParams);
        right.setVisibility(View.VISIBLE);

        this.addView(right);
    }

    @SuppressWarnings("unused")
    public void setPageCount(int count) {
        ArrayList<View> toRemove = new ArrayList<>();

        for (int i = 0; i < this.mIndicatorLayout.getChildCount(); i++) {
            toRemove.add(this.mIndicatorLayout.getChildAt(i));
        }

        for (View view : toRemove) {
            this.mIndicatorLayout.removeView(view);
        }

        DisplayMetrics metrics = this.getContext().getResources().getDisplayMetrics();

        for (int i = 0; i < count; i++) {
            ImageView indicator = new ImageView(this.getContext());

            indicator.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

            indicator.setPadding((int) (3 * metrics.density), (int) (3 * metrics.density), (int) (3 * metrics.density), (int) (3 * metrics.density));

            LayoutParams params = new LayoutParams((int) (12 * metrics.density), (int) (12 * metrics.density));
            indicator.setLayoutParams(params);

            indicator.setImageResource(R.drawable.ic_page_indicator);

            this.mIndicatorLayout.addView(indicator);
        }
    }

    @SuppressWarnings("unused")
    public void setSelectedPage(int position) {
        DisplayMetrics metrics = this.getContext().getResources().getDisplayMetrics();

        for (int i = 0; i < this.mIndicatorLayout.getChildCount(); i++) {
            View view = this.mIndicatorLayout.getChildAt(i);

            view.setPadding((int) (3 * metrics.density), (int) (3 * metrics.density), (int) (3 * metrics.density), (int) (3 * metrics.density));
        }

        View view = this.mIndicatorLayout.getChildAt(position);

        view.setPadding((int) (1 * metrics.density), (int) (1 * metrics.density), (int) (1 * metrics.density), (int) (1 * metrics.density));
    }
}
