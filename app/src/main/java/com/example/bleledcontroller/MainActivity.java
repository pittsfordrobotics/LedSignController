package com.example.bleledcontroller;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    private static final int RUNTIME_PERMISSION_REQUEST_CODE = 1;

    private TextView txtStatus = null;
    private NanoConnector connector = null;
    private Spinner stylePicker = null;
    private SeekBar brightnessBar = null;
    private SeekBar speedBar = null;
    private SeekBar stepBar = null;
    private boolean showDebug = false;

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRequiredRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return hasPermission(Manifest.permission.BLUETOOTH_SCAN) && hasPermission(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void requestRelevantRuntimePermissions() {
        if (hasRequiredRuntimePermissions()) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            requestLocationPermission();
        } else {
            requestBluetoothPermissions();
        }
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                RUNTIME_PERMISSION_REQUEST_CODE
        );
    }

    private void requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
                this,
                new String[] {
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                },
                RUNTIME_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            // Bind the common UI elements
            txtStatus = findViewById(R.id.txtStatus);
            brightnessBar = findViewById(R.id.seekBarBrightness);
            speedBar = findViewById(R.id.seekBarSpeed);
            stepBar = findViewById(R.id.seekBarStep);
            stylePicker = findViewById(R.id.spStyle);

            // Bind any initial event handlers
            Button showDebugButton = findViewById(R.id.btnShowHideDebug);
            showDebugButton.setOnClickListener(showHideDebugListener);

            // Set the initial UI state
            txtStatus.setText("");
            showStatus("Initializing");
            showDebug = false;
            updateDebugStateInUI();

            // Request permissions if needed
            if (!hasRequiredRuntimePermissions()) {
                requestRelevantRuntimePermissions();
            } else {
                Toast.makeText(this, "Permissions already granted.", Toast.LENGTH_SHORT).show();
            }

            // Start the BLE connection
            connector = createConnector();
            connector.connect();
        } catch (Exception e) {
            txtStatus.setText(e.toString());
        }
    }

    private void updateDebugStateInUI() {
        // Update the UI to reflect the current status of the 'showDebug' flag
        ScrollView scrollView = findViewById(R.id.scrollview);
        scrollView.setVisibility(showDebug ? View.VISIBLE : View.GONE);
        Button showDebugButton = findViewById(R.id.btnShowHideDebug);
        showDebugButton.setText(showDebug ? "Hide Debug Info" : "Show Debug Info");
    }

    private NanoConnector createConnector() {
        NanoConnectorCallback callback = new NanoConnectorCallback() {
            @Override
            public void acceptStatus(String status) {
                showStatus(status);
            }

            @Override
            public void connected() {
                runOnUiThread(() ->
                {
                    onConnected();
                });
            }
        };

        showStatus("Starting scan");
        return new NanoConnector(this, callback);
    }

    private void onConnected() {
        TextView txt = findViewById(R.id.txtConnectStatus);
        txt.setText("Connected");

        // Populate UI with current values
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, connector.getKnownStyles());
        stylePicker.setAdapter(adapter);
        brightnessBar.setProgress(connector.getInitialBrightness());
        stylePicker.setSelection(connector.getInitialStyle());
        speedBar.setProgress(connector.getInitialSpeed());
        stepBar.setProgress(connector.getInitialStep());

        // Enable updates
        brightnessBar.setOnSeekBarChangeListener(brightnessListener);
        stylePicker.setOnItemSelectedListener(stylePickListener);
        speedBar.setOnSeekBarChangeListener(speedListener);
        stepBar.setOnSeekBarChangeListener(stepListener);
    }

    private void showStatus(String status) {
        runOnUiThread(() -> {
            String newText = txtStatus.getText().toString() + '\n' + status;
            txtStatus.setText(newText);
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1) {
            if ((grantResults.length == 1) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                showStatus("Permission granted.");
            } else {
                showStatus("Permission denied.");
            }
        }
    }

    //
    // Various event handlers
    //
    private AdapterView.OnItemSelectedListener stylePickListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            String style = connector.getKnownStyles()[i];
            showStatus("Selected item: " + style);
            connector.setStyle((byte)i);
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
        }
    };

    private SeekBar.OnSeekBarChangeListener speedListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            showStatus("Setting sped to " + i);
            connector.setSpeed((byte)i);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private SeekBar.OnSeekBarChangeListener stepListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            showStatus("Setting step to " + i);
            connector.setStep((byte)i);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private SeekBar.OnSeekBarChangeListener brightnessListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            connector.setBrightness((byte)i);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private View.OnClickListener showHideDebugListener = view -> {
        showDebug = !showDebug;
        updateDebugStateInUI();
    };
}