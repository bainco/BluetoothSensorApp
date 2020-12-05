package org.nlogo.BLapp;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SensorData {

    private static final String KEY_ADD_ON = "KeyAddOn";

    private Sensor sensor;
    private String friendlySensorName;
    private float sensorValue;
    private boolean wantedValue;
    private SensorDataRunnable onSensorValueChanged;
    private int eventValue;

    public SensorData(Sensor sensor, String friendlySensorName, int eventValue) {
        this.sensor = sensor;
        this.friendlySensorName = friendlySensorName;
        this.eventValue = eventValue;

        if (sensor != null) {
            if (!SensorDataEventListener.getInstance().valueNumberMap.containsKey(sensor.getType())) {
                SensorDataEventListener.getInstance().valueNumberMap.put(sensor.getType(), eventValue);
            } else {
                int curVal = SensorDataEventListener.getInstance().valueNumberMap.get(sensor.getType());
                SensorDataEventListener.getInstance().valueNumberMap.put(sensor.getType(), eventValue);
            }

            String key = String.valueOf(sensor.getType()) + eventValue + KEY_ADD_ON;

            SensorDataEventListener.getInstance().sensorDataMap.put(key, this);
        }
    }

    public Sensor getSensor() {
        return sensor;
    }

    public void setSensor(Sensor sensor) {
        this.sensor = sensor;
    }

    public String getFriendlySensorName() {
        return friendlySensorName;
    }

    public void setFriendlySensorName(String friendlySensorName) {
        this.friendlySensorName = friendlySensorName;
    }

    public float getSensorValue() {
        return sensorValue;
    }

    void setSensorValue(float sensorValue) {
        if (onSensorValueChanged != null) {
            onSensorValueChanged.onSensorValueChange(sensorValue);
        }
        this.sensorValue = sensorValue;
    }

    public boolean isWantedValue() {
        return wantedValue;
    }

    void setWantedValue(boolean wantedValue) {
        this.wantedValue = wantedValue;
    }

    public int getEventValue() {
        return eventValue;
    }

    void setEventValue(int eventValue) {
        this.eventValue = eventValue;
    }

    public SensorDataRunnable getOnSensorValueChanged() {
        return onSensorValueChanged;
    }

    public void setOnSensorValueChanged(SensorDataRunnable onSensorValueChanged) {
        this.onSensorValueChanged = onSensorValueChanged;
    }

    public static interface SensorDataRunnable {
        void onSensorValueChange(float newValue);
    }

    public static class SensorDataEventListener implements SensorEventListener {

        private static SensorDataEventListener instance;
        protected Map<Integer, Integer> valueNumberMap = new ConcurrentHashMap<>();
        protected Map<String, SensorData> sensorDataMap = new ConcurrentHashMap<>();

        private SensorDataEventListener() { }

        public static SensorDataEventListener getInstance() {
            if (instance == null) {
                instance = new SensorDataEventListener();
            }
            return instance;
        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            int type = sensorEvent.sensor.getType();
            if (valueNumberMap.containsKey(type)) {
                int values = valueNumberMap.get(type);
                for (int i = 0; i <= values; i++) {
                    String key = String.valueOf(type) + i + KEY_ADD_ON;
                    if (sensorDataMap.containsKey(key)) {
                        SensorData sensorData = sensorDataMap.get(key);
                        sensorData.setSensorValue(sensorEvent.values[i]);
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }
}