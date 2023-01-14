package com.example.bleledcontroller;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NanoConnector {
    private static final UUID LedServiceUuid = UUID.fromString("99be4fac-c708-41e5-a149-74047f554cc1");
    private static final ParcelUuid LedServiceParcelUuid = new ParcelUuid(LedServiceUuid);
    private static final UUID BrightnessCharacteristicId = UUID.fromString("5eccb54e-465f-47f4-ac50-6735bfc0e730");
    private static final UUID StyleCharacteristicId = UUID.fromString("c99db9f7-1719-43db-ad86-d02d36b191b3");

    private Context context;
    private NanoConnectorCallback callback;

    private BluetoothDevice bluetoothDevice;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic brightnessCharacteristic;
    private BluetoothGattCharacteristic styleCharacteristic;

    public NanoConnector(Context context, NanoConnectorCallback callback) {
        this.context = context;
        this.callback = callback;

        BluetoothManager mgr = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = mgr.getAdapter();
    }

    public void connect() {
        if (!bluetoothAdapter.isEnabled()) {
            callback.acceptStatus("Bluetooth adapter disabled!");
            return;
        }

        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(LedServiceParcelUuid)
                .build();

        List<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(SCAN_MODE_BALANCED)
                .setNumOfMatches(MATCH_NUM_ONE_ADVERTISEMENT)
                .setCallbackType(CALLBACK_TYPE_FIRST_MATCH)
                .build();

        bluetoothLeScanner.startScan(filters, scanSettings, leScanCallback);
    }

    public void setBrightness(byte brightness) {
        brightnessCharacteristic.setValue(brightness, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        bluetoothGatt.writeCharacteristic(brightnessCharacteristic);
    }

    public void setStyle(byte style) {
        styleCharacteristic.setValue(style, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        bluetoothGatt.writeCharacteristic(styleCharacteristic);
    }

    private void connectToDevice(BluetoothDevice device) {
        callback.acceptStatus("Attempting GATT connection.");
        device.connectGatt(context, true, gattCallback);
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    bluetoothDevice = result.getDevice();
                    String status = "Connected to device: " + bluetoothDevice.getName();
                    callback.acceptStatus(status);
                    connectToDevice(bluetoothDevice);
                }
            };

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    callback.acceptStatus("Connected to device - discovering services");
                    bluetoothGatt = gatt;
                    bluetoothGatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    callback.acceptStatus("Disconnected.");
                    gatt.close();
                }
            } else {
                callback.acceptStatus("Error encountered while connecting: " + status);
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            List<BluetoothGattService> services = gatt.getServices();
            BluetoothGattService ledService = null;
            String t = "Found " + services.size() + " services.";
            t += "Looking for " + LedServiceUuid + "...";

            for (BluetoothGattService service: services ) {
                t += service.getUuid() + "; ";

                if (service.getUuid().equals(LedServiceUuid)) {
                    ledService = service;
                }
            }

            callback.acceptStatus(t);
            if (ledService == null) {
                callback.acceptStatus("LED service not found!");
                return;
            }
            callback.acceptStatus("Found LED service.");
            brightnessCharacteristic = ledService.getCharacteristic(BrightnessCharacteristicId);

            if (brightnessCharacteristic == null) {
                callback.acceptStatus("LED Brightness Characteristic not found!");
                return;
            }
            callback.acceptStatus("Found LED Brightness Characteristic.");
            styleCharacteristic = ledService.getCharacteristic(StyleCharacteristicId);

            if (styleCharacteristic == null) {
                callback.acceptStatus("LED Style Characteristic not found!");
                return;
            }

            callback.acceptStatus("Found LED Style Characteristic.");

            callback.acceptStatus("Connected and ready.");
            callback.connected();
        }
    };
}
