package com.example.measurething;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.lang.System.currentTimeMillis;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanReceiver;
    private IntentFilter intentFilterWifi;

    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler;
    private static final long SCAN_PERIOD = 5000;

    private SensorManager sensorManager;
    private SensorListener sensorListener;
    private Sensor magneticSensor, rotationSensor;
    private float[] magneticField = new float[3];
    private float[] rotationField = new float[3];


    private Map<String,Integer> wifiMac, bleName;

    private String xyz;

    private String filename = "data.csv";
    private String datapath = "measurements";
    private String pointpath = "points";
    File dataFile, pointFile;
    private LinkedList<String> pointList;
    private int pointListSize;
    private long startTime;

    private TextView wifiTextView, bleTextView, magTextView, valuesTextView, posTextView, fileroomTextView, rotTextView;
    private Button wifiBtn, bleBtn, allBtn, fileBtn, nextBtn, saveBtn;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startTime = currentTimeMillis();

        xyz = "x,y,z";

        valuesTextView = findViewById(R.id.valuesTextView);
        valuesTextView.setMovementMethod(new ScrollingMovementMethod());
        posTextView = findViewById(R.id.posTextView);

        fileroomTextView = findViewById(R.id.fileroom);

        fileBtn = findViewById(R.id.fileBtn);
        fileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("wifi",getValueString());
                pointFile = new File(getExternalFilesDir(pointpath), fileroomTextView.getText() + ".csv");
                pointList = new LinkedList<>();
                try {
                    FileInputStream fis = new FileInputStream(pointFile);
                    DataInputStream in = new DataInputStream(fis);
                    BufferedReader br =
                            new BufferedReader(new InputStreamReader(in));
                    String strLine;
                    while ((strLine = br.readLine()) != null) {
                        pointList.add(strLine);
                    }
                    in.close();
                    saveBtn.setEnabled(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                pointListSize = pointList.size();
                Toast.makeText(getApplicationContext(), "Loaded", Toast.LENGTH_SHORT).show();
            }
        });

        nextBtn = findViewById(R.id.nextBtn);
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                xyz = pointList.pollFirst();
                posTextView.setText("Pos:"+ (pointListSize - pointList.size()) +" "+ xyz);
            }
        });

        nextBtn = findViewById(R.id.nextBtn);
        saveBtn = findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isExternalStorageAvailable() && !isExternalStorageReadOnly()) {
                    dataFile = new File(getExternalFilesDir(datapath), fileroomTextView.getText() + "-" +filename);

                    Log.i("wifi",getValueString());
                    try {
                        FileOutputStream fos = new FileOutputStream(dataFile, true);
                        fos.write(getValueString().getBytes());
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(getApplicationContext(), "Saved", Toast.LENGTH_SHORT).show();
                }
            }
        });

        saveBtn.setEnabled(false);

        //String[] wmac = {"64:cc:22:9c:c4:31","64:cc:22:9c:c4:32","64:cc:22:9c:c4:30", "1c:b0:44:d6:86:7c", "1c:b0:44:d6:86:6f"};
        String[] wmac = {"A8:9D:21:74:69:0F", "B4:14:89:14:0A:3F", "A8:9D:21:82:6B:BF", "84:B8:02:22:2D:5F", "A8:9D:21:8C:9E:6F", "A8:9D:21:74:69:00", "B4:14:89:14:0A:30", "A8:9D:21:82:6B:B0", "84:B8:02:22:2D:50", "A8:9D:21:8C:9E:60"};
        String[] bnam = {"P00000","P00001","P00002","P00003","P00004","P00005","P00006","P00007","P00008","P00009","P00010","P10000","P10001","P10002","P10003","P10004","P10005","P10006","P10007","P10008","P10009","P10010","E00000","E00001","E00002"};

        wifiMac = new LinkedHashMap<>();
        for (String n : wmac) {
            wifiMac.put(n,0);
        }
        bleName = new LinkedHashMap<>();
        for (String n : bnam) {
            bleName.put(n,0);
        }

        wifiBtn = findViewById(R.id.scanWifiBtn);
        wifiTextView = findViewById(R.id.wifiTextView);
        wifiTextView.setMovementMethod(new ScrollingMovementMethod());
        wifiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (String key : wifiMac.keySet()) {
                    wifiMac.put(key, 0);
                }
                wifiTextView.setText("Scanning now");
                wifiManager.startScan();
                wifiBtn.setEnabled(false);
            }
        });
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    scanSuccess();
                } else {
                    scanFailure();
                }
                wifiBtn.setEnabled(true);
            }
        };

        intentFilterWifi = new IntentFilter();
        intentFilterWifi.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        getApplicationContext().registerReceiver(wifiScanReceiver, intentFilterWifi);


        bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        handler = new Handler();

        bleBtn = findViewById(R.id.scanBleBtn);
        bleTextView = findViewById(R.id.bleTextView);
        bleTextView.setMovementMethod(new ScrollingMovementMethod());
        bleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (String key : bleName.keySet()) {
                    bleName.put(key, 0);
                }
                bleTextView.setText("Scanning now\n");
                scanLeDevice();
            }
        });

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorListener = new SensorListener();
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        magTextView = findViewById(R.id.magTextView);
        magTextView.setMovementMethod(new ScrollingMovementMethod());

        rotTextView = findViewById(R.id.rotTextView);
        rotTextView.setMovementMethod(new ScrollingMovementMethod());

        allBtn = findViewById(R.id.scanAll);
        allBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                valuesTextView.setText("");
                wifiBtn.performClick();
                bleBtn.performClick();
            }
        });
    }

    private void scanSuccess() {
        List<ScanResult> results = wifiManager.getScanResults();
        wifiTextView.setText("");
        for (ScanResult re : results) {
            if (wifiMac.containsKey(re.BSSID.toUpperCase())) {
                wifiMac.put(re.BSSID.toUpperCase(), re.level);
            }

            wifiTextView.append("SSID: " + re.SSID + "\n");
            wifiTextView.append("BSSID: " + re.BSSID.toUpperCase() + "\n");
            wifiTextView.append("level: " + re.level + "\n");
        }
        updateValueView();
        Log.i("wifi", results.toString());
    }

    private void scanFailure() {
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        List<ScanResult> results = wifiManager.getScanResults();
        wifiTextView.setText("Scan failed");
        Log.i("mal", results.toString());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void scanLeDevice() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothLeScanner.stopScan(leScanCallback);
                bluetoothLeScanner.flushPendingScanResults(leScanCallback);
                updateValueView();
                bleBtn.setEnabled(true);
            }}, SCAN_PERIOD);
        bluetoothLeScanner.startScan(leScanCallback);
        bleBtn.setEnabled(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, android.bluetooth.le.ScanResult result) {
                    super.onScanResult(callbackType, result);
                    result.getDevice();
                    if (bleName.containsKey(result.getScanRecord().getDeviceName())) {
                        bleName.put(result.getScanRecord().getDeviceName(), result.getRssi());
                    }

                    /*if (bleName.containsKey(result.getDevice().toString())) {
                        bleName.put(result.getDevice().toString(), result.getRssi());
                    }*/

                    bleTextView.append("Name: " + result.getScanRecord().getDeviceName()+"\n");
                    bleTextView.append("Dev: " + result.getDevice()+"\n");
                    bleTextView.append("RSSI: " + result.getRssi()+"\n");
                    Log.i("wifi", "ble " + result.toString());
                }
            };

    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(sensorListener, magneticSensor, 1000000);
        sensorManager.registerListener(sensorListener, rotationSensor, 2000000);
    }

    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(sensorListener, magneticSensor);
        sensorManager.unregisterListener(sensorListener, rotationSensor);
    }


    public class SensorListener implements SensorEventListener {

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor == magneticSensor) {
                System.arraycopy(event.values, 0, magneticField, 0, event.values.length);
                magTextView.setText((String.valueOf(magneticField[0])+","+String.valueOf(magneticField[1])+","+String.valueOf(magneticField[2])));
                updateValueView();
                Log.i("onSensorChangedM: ", (String.valueOf(magneticField[0])+","+String.valueOf(magneticField[1])+","+String.valueOf(magneticField[2])));
            } else if (event.sensor == rotationSensor) {
                System.arraycopy(event.values, 0, rotationField, 0, 3);
                rotTextView.setText((String.format(Locale.US,"%.5f",rotationField[0])+","+String.format(Locale.US,"%.5f",rotationField[1])+","+String.format(Locale.US,"%.5f",rotationField[2])));
                updateValueView();
                Log.i("onSensorChangedM: ", (String.valueOf(rotationField[0])+","+String.valueOf(rotationField[1])+","+String.valueOf(rotationField[2])));
                Log.i("rotation", String.valueOf(event.values[0]) + String.valueOf(event.values[1]) + String.valueOf(event.values[0]));
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    }

    protected void updateValueView(){
        valuesTextView.setText("");
        valuesTextView.append("MAG: " + (String.valueOf(magneticField[0])+","+String.valueOf(magneticField[1])+","+String.valueOf(magneticField[2])) + "\n");
        valuesTextView.append("ROT: " + rotTextView.getText() + "\n");
        valuesTextView.append("WIFI: \n");
        for (String key : wifiMac.keySet()) {
            valuesTextView.append(key + " " + wifiMac.get(key).toString() + "\n");
        }
        valuesTextView.append("BLE: \n");
        for (String key : bleName.keySet()) {
            valuesTextView.append(key + " " + bleName.get(key).toString() + "\n");
        }
    }

    protected String getValueString(){
        String res = new String();
        long dif = (currentTimeMillis() - startTime);
        res = dif + ","+ xyz +"," + (String.valueOf(magneticField[0])+","+String.valueOf(magneticField[1])+","+String.valueOf(magneticField[2]));
        res = res + "," + rotTextView.getText();
        for (String key : wifiMac.keySet()) {
           res = res + "," + wifiMac.get(key).toString();
        }
        for (String key : bleName.keySet()) {
            res = res + "," + bleName.get(key).toString();
        }
        res = res + "\n";

        return res;
    }

    private static boolean isExternalStorageReadOnly() {
        String extStorageState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState)) {
            return true;
        }
        return false;
    }

    private static boolean isExternalStorageAvailable() {
        String extStorageState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
            return true;
        }
        return false;
    }


}