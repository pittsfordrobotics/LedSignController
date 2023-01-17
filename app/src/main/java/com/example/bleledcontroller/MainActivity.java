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
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final int RUNTIME_PERMISSION_REQUEST_CODE = 1;
    private static final String[] StyleEntries = { "Red", "Blue", "Green", "Rainbow" };

    private TextView txtStatus = null;
    private NanoConnector connector = null;
    private Spinner stylePicker = null;
    private SeekBar brightnessBar = null;

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
            txtStatus = findViewById(R.id.txtStatus);
            txtStatus.setText("");
            showStatus("Initializing");
            brightnessBar = findViewById(R.id.seekBarBrightness);

            stylePicker = findViewById(R.id.spStyle);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, StyleEntries);
            stylePicker.setAdapter(adapter);

            if (!hasRequiredRuntimePermissions()) {
                requestRelevantRuntimePermissions();
            } else {
                Toast.makeText(this, "Permissions already granted.", Toast.LENGTH_SHORT).show();
            }

            connector = createConnector();
            connector.connect();
        } catch (Exception e) {
            txtStatus.setText(e.toString());
        }
    }

    private NanoConnector createConnector() {
        NanoConnectorCallback callback = new NanoConnectorCallback() {
            @Override
            public void acceptStatus(String status) {
                showStatus(status);
            }

            @Override
            public void connected() {
                showStatus("Attempting to set brightness to " + connector.getInitialBrightness() + " and style to " + connector.getInitialStyle());
                runOnUiThread(() ->
                {
                    brightnessBar.setProgress(connector.getInitialBrightness());
                    stylePicker.setSelection(connector.getInitialStyle());
                    enableUpdates();
                });
            }
        };

        showStatus("Starting scan");
        return new NanoConnector(this, callback);
    }

    private void enableUpdates() {
        brightnessBar.setOnSeekBarChangeListener(brightnessListener);
        stylePicker.setOnItemSelectedListener(dropdownListener);
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

    private AdapterView.OnItemSelectedListener dropdownListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            String style = StyleEntries[i];
            showStatus("Selected item: " + style);
            connector.setStyle((byte)i);
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
        }
    };

    private SeekBar.OnSeekBarChangeListener brightnessListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            //showStatus("Setting brightness to " + i);
            connector.setBrightness((byte)i);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
//            int value = seekBar.getProgress();
//            showStatus("Setting brightness to " + value);
//            connector.setBrightness((byte)value);
        }
    };
}