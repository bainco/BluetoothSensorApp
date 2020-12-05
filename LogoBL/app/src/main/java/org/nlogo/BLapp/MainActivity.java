package org.nlogo.BLapp;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.nlogoBLapp.R;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter mBluetoothAdapter;

    private SensorManager mSensorManager;
    ArrayList<SensorData> mSensorData = new ArrayList<>();

    private Button mStartServerBtn;
    private Button mStopServerBtn;
    private RecyclerView mRecyclerView;
    private RecyclerViewAdapter mRecyclerViewAdapter;
    private ProgressBar mProgressBar;
    private AlertDialog mConnectionSuccessAlert;
    private AlertDialog mConnectionDroppedAlert;

    private static final int CONNECTION_ESTABLISHED = 100;
    private static final int CONNECTION_DROPPED = 101;

    private Thread mSendDataThread;
    private BluetoothServerSocket mBluetoothServerSocket;
    private BluetoothSocket mBluetoothSocket;
    private boolean threadRunning;

    private Handler mConnectionHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == CONNECTION_ESTABLISHED) {
                mConnectionSuccessAlert.show();
            }
            if (msg.what == CONNECTION_DROPPED) {
                mSendDataThread = null;
                if (mStopServerBtn.isEnabled()) {
                    mConnectionDroppedAlert.show();
                    switchEnabled();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Toast toast = setupToast("Bluetooth isn't supported by this device");
            toast.show();
            finish();
        }

        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        mProgressBar = findViewById(R.id.progressBar);
        mProgressBar.setVisibility(View.INVISIBLE);

        setupToolbar();
        setupConnectionSuccessAlert();
        setupConnectionDropAlert();
        setupSensors();
        setupButtons();
        setupRecyclerView();

    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSensorManager.unregisterListener(SensorData.SensorDataEventListener.getInstance());
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        registerSensors();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mSensorManager.unregisterListener(SensorData.SensorDataEventListener.getInstance());
        closeSocket();
        closeServerSocket();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            case R.id.action_turn_on_all:
                if (mRecyclerViewAdapter != null) {
                    mRecyclerViewAdapter.changeSensors(true);
                }
                break;
            case R.id.action_turn_off_all:
                if (mRecyclerViewAdapter != null) {
                    mRecyclerViewAdapter.changeSensors(false);
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setupButtons() {
        mStartServerBtn = findViewById(R.id.start_server_btn);
        mStopServerBtn = findViewById(R.id.stop_server_btn);

        mStartServerBtn.setOnClickListener(view -> {
            if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                requestBTDiscoverable();
                Toast toast = setupToast("Enable discovery mode and then press Start Server again");
                toast.show();
                return;
            }

            Toast toast = setupToast("Waiting for a connection...");
            toast.show();

            new ConnectionTask().execute();

            switchEnabled();
        });

        mStopServerBtn.setOnClickListener(view -> {
            threadRunning = false;
            mProgressBar.setVisibility(View.INVISIBLE);
            Toast toast = setupToast("Stopping Bluetooth Server");
            toast.show();
            switchEnabled();
            closeSocket();
        });
    }

    private void setupRecyclerView() {
        mRecyclerView = findViewById(R.id.recycler_view);
        mRecyclerViewAdapter = new RecyclerViewAdapter(this, mSensorData);
        mRecyclerView.setAdapter(mRecyclerViewAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this) {
            @Override
            public boolean canScrollVertically() {
                return false;
            }
        });
    }

    private void setupConnectionSuccessAlert() {
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
        mBuilder.setMessage("Connection successful!");
        mBuilder.setPositiveButton("Ok", (dialog, id) -> {
            try {
                mSendDataThread = new SendDataThread(mBluetoothSocket.getOutputStream());
                threadRunning = true;
                mSendDataThread.start();
            } catch (IOException e) {
                e.printStackTrace();
                mConnectionHandler.sendEmptyMessage(CONNECTION_DROPPED);
            }
        });
        mConnectionSuccessAlert = mBuilder.create();
    }

    private void setupConnectionDropAlert() {
        AlertDialog.Builder mBuilder = new AlertDialog.Builder(MainActivity.this);
        mBuilder.setMessage("Connection was dropped!");
        mBuilder.setPositiveButton("Ok", (dialog, id) -> {

        });
        mConnectionDroppedAlert = mBuilder.create();
    }

    private void switchEnabled() {
        if (mStartServerBtn != null) {
            mStartServerBtn.setEnabled(!mStartServerBtn.isEnabled());
        }
        if (mStopServerBtn != null) {
            mStopServerBtn.setEnabled(!mStopServerBtn.isEnabled());
        }
    }

    private void setupSensors() {
        if (mSensorManager != null) {
            addSensor(Sensor.TYPE_AMBIENT_TEMPERATURE, "Temperature");
            addSensor(Sensor.TYPE_RELATIVE_HUMIDITY, "Humidity");
            addSensor(Sensor.TYPE_LIGHT, "Light");
            addSensor(Sensor.TYPE_PRESSURE, "Pressure");
            addSensor(Sensor.TYPE_ACCELEROMETER, "Accelerometer X", 0);
            addSensor(Sensor.TYPE_ACCELEROMETER, "Accelerometer Y", 1);
            addSensor(Sensor.TYPE_ACCELEROMETER, "Accelerometer Z", 2);
            addSensor(Sensor.TYPE_GYROSCOPE, "Gyroscope X", 0);
            addSensor(Sensor.TYPE_GYROSCOPE, "Gyroscope Y", 1);
            addSensor(Sensor.TYPE_GYROSCOPE, "Gyroscope Z", 2);
        }
    }

    private void addSensor(int sensorType, String friendlyName) {
        addSensor(sensorType, friendlyName, 0);
    }

    private void addSensor(int sensorType, String friendlyName, int eventValue) {
        Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
        if (sensor != null) {
            SensorData sensorData = new SensorData(sensor, friendlyName, eventValue);
            sensorData.setEventValue(eventValue);
            mSensorData.add(sensorData);
        }
    }

    private void registerSensors() {
        for (SensorData sensor : mSensorData) {
            mSensorManager.registerListener(SensorData.SensorDataEventListener.getInstance(), sensor.getSensor(), SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void requestBTDiscoverable() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);
    }

    private void closeSocket() {
        if (mBluetoothSocket != null) {
            try {
                mBluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeServerSocket() {
        if (mBluetoothServerSocket != null) {
            try {
                mBluetoothServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Toast setupToast(String message) {
        Toast toast = new Toast(this);
        toast.setDuration(Toast.LENGTH_SHORT);
        View toastView = getLayoutInflater().inflate(R.layout.toast_layout, null);
        TextView toastText = toastView.findViewById(R.id.toast_text);
        toastText.setText(message);
        toast.setView(toastView);
        return toast;
    }

    private class ConnectionTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgressBar.setVisibility(View.VISIBLE);

            if (mBluetoothServerSocket == null) {
                BluetoothServerSocket tmp = null;
                try {
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(TAG, MY_UUID);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mBluetoothServerSocket = tmp;
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            BluetoothSocket tmp = null;
            try {
                tmp = mBluetoothServerSocket.accept();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (tmp != null) {
                mBluetoothSocket = tmp;
                mConnectionHandler.sendEmptyMessage(CONNECTION_ESTABLISHED);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    private class SendDataThread extends Thread {

        OutputStream mOutputStream;

        SendDataThread(OutputStream outputStream) {
            mOutputStream = outputStream;
        }

        @Override
        public void run() {
            super.run();

            while (threadRunning) {
                try {
                    Thread.sleep(100);
                    StringBuilder message = new StringBuilder();

                    for (SensorData sensor : mSensorData) {
                        message.append(sensor.getFriendlySensorName()).append(",D,").append(sensor.isWantedValue() ? sensor.getSensorValue() : 0).append(";");
                    }
                    mOutputStream.write(message.toString().getBytes());
                } catch (Exception e) {
                    threadRunning = false;
                    e.printStackTrace();
                    mConnectionHandler.sendEmptyMessage(MainActivity.CONNECTION_DROPPED);
                }
            }
        }
    }
}
