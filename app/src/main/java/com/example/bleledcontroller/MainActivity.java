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
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    //
    // Much help from
    // https://punchthrough.com/android-ble-guide/
    //

    private static final int RUNTIME_PERMISSION_REQUEST_CODE = 1;

    private TextView txtStatus = null;
    private CheckBox chkLed = null;
    private NanoConnector connector = null;
    private Spinner stylePicker = null;

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

            chkLed = findViewById(R.id.chkLed);
            stylePicker = findViewById(R.id.spStyle);
            String[] items = { "Red", "Blue", "Green" };
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items);
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

    private void updateLedBrightness(View v) {
        int value = chkLed.isChecked() ? 150 : 20;
        showStatus("Setting characteristic to " + value);
        connector.setBrightness((byte)value);
    }

    private NanoConnector createConnector() {
        NanoConnectorCallback callback = new NanoConnectorCallback() {
            @Override
            public void acceptStatus(String status) {
                showStatus(status);
            }

            @Override
            public void connected() {
                enableUpdates();
            }
        };

        showStatus("Starting scan");
        return new NanoConnector(this, callback);
    }

    private void enableUpdates() {
        chkLed.setOnClickListener(this::updateLedBrightness);
        stylePicker.setOnItemSelectedListener(this);
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

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        showStatus("Selected item " + i);
        connector.setStyle((byte)i);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }
}