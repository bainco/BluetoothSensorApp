package com.nashzhou.logobl;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>{

    private static final String TAG = "RecyclerViewAdapter";

    private Context mContext;
    private List<SensorData> mSensorData;

    private List<Switch> mSwitches = new ArrayList<>();

    public RecyclerViewAdapter(Context mContext, List<SensorData> sensorData) {
        this.mContext = mContext;
        this.mSensorData = sensorData;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view, parent, false);
        ViewHolder viewHolder = new ViewHolder(view);
        mSwitches.add(viewHolder.sensorOn);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.setSensorData(mSensorData.get(position));
    }

    @Override
    public int getItemCount() {
        return mSensorData.size();
    }

    public void changeSensors(boolean newValue) {
        for (Switch mSwitch : mSwitches) {
            if (mSwitch != null) {
                mSwitch.setChecked(newValue);
            }
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private TextView sensorName;
        private TextView sensorValue;
        private Switch sensorOn;
        private SensorData sensorData;

        public ViewHolder(View itemView) {
            super(itemView);

            sensorName = itemView.findViewById(R.id.sensor_name);
            sensorValue = itemView.findViewById(R.id.sensor_value);
            sensorOn = itemView.findViewById(R.id.sensor_on);
        }

        public SensorData getSensorData() {
            return sensorData;
        }

        public void setSensorData(SensorData sensorData) {
            this.sensorData = sensorData;

            sensorName.setText(sensorData.getFriendlySensorName());
            setOnCheckListener();
        }

        private void setOnCheckListener() {
            sensorOn.setOnCheckedChangeListener((compoundButton, isChecked) -> {
                if (sensorData != null) {
                    sensorData.setWantedValue(isChecked);
                    if (isChecked) {
                        sensorData.setOnSensorValueChanged((newValue) -> {
                            StringBuilder sensorValueText = new StringBuilder("Value : ");
                            sensorValueText.append(String.format("%.2f", newValue));
                            sensorValue.setText(sensorValueText.toString());
                        });
                    } else {
                        sensorData.setOnSensorValueChanged(null);
                        sensorValue.setText("");
                    }
                }
            });

        }
    }
}