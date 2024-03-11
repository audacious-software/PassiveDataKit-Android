package com.audacious_software.passive_data_kit.activities.generators;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;

import com.audacious_software.passive_data_kit.Logger;
import com.audacious_software.passive_data_kit.generators.Generator;
import com.audacious_software.passive_data_kit.generators.Generators;
import com.audacious_software.passive_data_kit.generators.diagnostics.AppEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class DataPointsAdapter extends RecyclerView.Adapter<DataPointViewHolder> {
    public static final String SORT_BY_UPDATED = "com.audacious_software.passive_data_kit.activities.generators.DataPointsAdapter";
    public static final boolean SORT_BY_UPDATED_DEFAULT = true;

    private Context mContext = null;
    private final List<Class<? extends Generator>> mActiveGenerators = new ArrayList<>();
    private boolean mIsSorting = false;

    @Override
    public DataPointViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Class<? extends Generator> generatorClass = Generators.getInstance(this.mContext).fetchCustomViewClass(viewType);

        try {
            Method fetchView = generatorClass.getDeclaredMethod("fetchView", ViewGroup.class);
            View view = (View) fetchView.invoke(null, parent);

            return new DataPointViewHolder(view);
        } catch (Exception e) {
            e.printStackTrace();

            try {
                generatorClass = Generator.class;

                Method fetchView = generatorClass.getDeclaredMethod("fetchView", ViewGroup.class);
                View view = (View) fetchView.invoke(null, parent);

                return new DataPointViewHolder(view);
            } catch (NoSuchMethodException e1) {
                // Logger.getInstance(parent.getContext()).logThrowable(e1);
            } catch (InvocationTargetException e1) {
                // Logger.getInstance(parent.getContext()).logThrowable(e1);
            } catch (IllegalAccessException e1) {
                // Logger.getInstance(parent.getContext()).logThrowable(e1);
            }
        }

        return null;
    }


    @Override
    public void onViewAttachedToWindow(@NonNull DataPointViewHolder holder) {
        super.onViewAttachedToWindow(holder);

        holder.runOnAttach();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull DataPointViewHolder holder) {
        super.onViewDetachedFromWindow(holder);

        holder.runOnDetach();
    }

    @SuppressWarnings("UnusedReturnValue")
    private List<Class<? extends Generator>> getGenerators(Context context) {
        synchronized (this.mActiveGenerators) {
            if (this.mActiveGenerators.size() == 0) {
                this.mActiveGenerators.addAll(Generators.getInstance(context).activeGenerators());
            }
        }

        this.sortGenerators(false);

        return this.mActiveGenerators;
    }

    @Override
    public void onBindViewHolder(final DataPointViewHolder holder, int position) {
        this.getGenerators(holder.itemView.getContext());

        Class<? extends Generator> generatorClass = this.mActiveGenerators.get(position);

        try {
            Method bindViewHolder = generatorClass.getDeclaredMethod("bindViewHolder", DataPointViewHolder.class);
            bindViewHolder.invoke(null, holder);
        } catch (Exception e) {
            // AppEvent.getInstance(this.mContext).logThrowable(e);

            try {
                Method bindViewHolder = Generator.class.getDeclaredMethod("bindViewHolder", DataPointViewHolder.class, Class.class);

                bindViewHolder.invoke(null, holder, generatorClass);
            } catch (NoSuchMethodException e1) {
                // Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            } catch (InvocationTargetException e1) {
                // Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            } catch (IllegalAccessException e1) {
                // Logger.getInstance(holder.itemView.getContext()).logThrowable(e1);
            }
        }
    }

    @Override
    public int getItemCount() {
        this.getGenerators(this.mContext);

        return this.mActiveGenerators.size();
    }

    public void sortGenerators(boolean redrawAll) {
        if (this.mIsSorting) {
            return;
        }

        this.mIsSorting = true;

        final DataPointsAdapter me = this;

        final Context context = this.mContext;

        synchronized (this.mActiveGenerators) {
            this.getGenerators(this.mContext);

            final HashMap<Class, Long> timestamps = new HashMap<>();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            if (prefs.getBoolean(DataPointsAdapter.SORT_BY_UPDATED, DataPointsAdapter.SORT_BY_UPDATED_DEFAULT)) {
                Collections.sort(me.mActiveGenerators, new Comparator<Class<? extends Generator>>() {
                    @Override
                    public int compare(Class<? extends Generator> one, Class<? extends Generator> two) {
                        long oneUpdated = 0;

                        if (timestamps.containsKey(one)) {
                            oneUpdated = timestamps.get(one);
                        } else {
                            try {
                                Method oneGenerated = one.getDeclaredMethod("latestPointGenerated", Context.class);
                                oneUpdated = (long) oneGenerated.invoke(null, context);

                                timestamps.put(one, oneUpdated);
                            } catch (NoSuchMethodException e) {
//                        e.printStackTrace();
                            } catch (InvocationTargetException e) {
//                        e.printStackTrace();
                            } catch (IllegalAccessException e) {
//                        e.printStackTrace();
                            }
                        }

                        long twoUpdated = 0;

                        if (timestamps.containsKey(two)) {
                            twoUpdated = timestamps.get(two);
                        } else {
                            try {
                                Method twoGenerated = two.getDeclaredMethod("latestPointGenerated", Context.class);
                                twoUpdated = (long) twoGenerated.invoke(null, context);
                                timestamps.put(two, twoUpdated);
                            } catch (NoSuchMethodException e) {
//                        e.printStackTrace();
                            } catch (InvocationTargetException e) {
//                        e.printStackTrace();
                            } catch (IllegalAccessException e) {
//                        e.printStackTrace();
                            }
                        }

                        if (oneUpdated < twoUpdated) {
                            return 1;
                        } else if (oneUpdated > twoUpdated) {
                            return -1;
                        }

                        return 0;
                    }
                });
            }
        }

        if (redrawAll) {
            this.notifyDataSetChanged();
        }

        this.mIsSorting = false;
    }

    public void notifyDataSetChanged(String identifier) {
        int position = -1;

        for (int i = 0; position == -1 && i < this.mActiveGenerators.size(); i++) {
            Class<? extends Generator> generatorClass = this.mActiveGenerators.get(i);

            try {
                Method generatorIdentifier = generatorClass.getDeclaredMethod("generatorIdentifier");

                if (identifier.equals(generatorIdentifier.invoke(null))) {
                    position = i;
                }
            } catch (Exception e) {
                // Method does not exist, skip...
            }
        }

        if (position != -1) {
            this.notifyItemChanged(position);
        } else {
            this.notifyDataSetChanged();
        }
    }

    public int getItemViewType (int position) {
        this.getGenerators(this.mContext);

        Class<? extends Generator> generatorClass = this.mActiveGenerators.get(position);

        return generatorClass.hashCode();
    }

    public void setContext(Context context) {
        this.mContext = context;
    }
}
