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
import java.util.Optional;
import java.util.UUID;

public class NanoConnector {
    //
    // Much help from
    // https://punchthrough.com/android-ble-guide/
    //
    private static final UUID LedServiceUuid = UUID.fromString("99be4fac-c708-41e5-a149-74047f554cc1");
    private static final ParcelUuid LedServiceParcelUuid = new ParcelUuid(LedServiceUuid);
    private static final UUID BrightnessCharacteristicId = UUID.fromString("5eccb54e-465f-47f4-ac50-6735bfc0e730");
    private static final UUID StyleCharacteristicId = UUID.fromString("c99db9f7-1719-43db-ad86-d02d36b191b3");
    private static final UUID NamesCharacteristicId = UUID.fromString("9022a1e0-3a1f-428a-bad6-3181a4d010a5");
    private static final UUID SpeedCharacteristicId = UUID.fromString("b975e425-62e4-4b08-a652-d64ad5097815");
    private static final UUID StepCharacteristicId = UUID.fromString("70e51723-0771-4946-a5b3-49693e9646b5");

    private Context context;
    private NanoConnectorCallback callback;

    private BluetoothDevice bluetoothDevice;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic brightnessCharacteristic;
    private BluetoothGattCharacteristic styleCharacteristic;
    private BluetoothGattCharacteristic namesCharacteristic;
    private BluetoothGattCharacteristic speedCharacteristic;
    private BluetoothGattCharacteristic stepCharacteristic;

    private int initialBrightness = -1;
    private int initialStyle = -1;
    private String[] knownStyles;
    private int initialSpeed = -1;
    private int initialStep = -1;

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

    public int getInitialBrightness() {
        return initialBrightness;
    }

    public void setBrightness(int brightness) {
        brightnessCharacteristic.setValue(brightness, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        bluetoothGatt.writeCharacteristic(brightnessCharacteristic);
    }

    public int getInitialStyle() {
        return initialStyle;
    }

    public void setStyle(int style) {
        styleCharacteristic.setValue(style, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        bluetoothGatt.writeCharacteristic(styleCharacteristic);
    }

    public String[] getKnownStyles() { return knownStyles; }

    private void connectToDevice(BluetoothDevice device) {
        callback.acceptStatus("Attempting GATT connection.");
        device.connectGatt(context, true, gattCallback);
    }

    public int getInitialSpeed() { return initialSpeed; }
    public void setSpeed(int speed) {
        speedCharacteristic.setValue(speed, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        bluetoothGatt.writeCharacteristic(speedCharacteristic);
    }

    public int getInitialStep() { return initialStep; }
    public void setStep(int step) {
        stepCharacteristic.setValue(step, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        bluetoothGatt.writeCharacteristic(stepCharacteristic);
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
            brightnessCharacteristic = findCharacteristic(ledService, BrightnessCharacteristicId, "Brightness");
            styleCharacteristic = findCharacteristic(ledService, StyleCharacteristicId, "Style");
            namesCharacteristic = findCharacteristic(ledService, NamesCharacteristicId, "Style Names");
            speedCharacteristic = findCharacteristic(ledService, SpeedCharacteristicId, "Speed");
            stepCharacteristic = findCharacteristic(ledService, StepCharacteristicId, "Step");

            if (brightnessCharacteristic == null
                || styleCharacteristic == null
                || namesCharacteristic == null
                || speedCharacteristic == null
                || stepCharacteristic == null) {
                return;
            }

            callback.acceptStatus("Services bound successfully.");

            // Start reading initial values - do this one at a time to avoid parallelism issues.
            // Should have some sort of queuing mechanism.
            gatt.readCharacteristic(brightnessCharacteristic);
        }

        private BluetoothGattCharacteristic findCharacteristic(BluetoothGattService service, UUID id, String name) {
            BluetoothGattCharacteristic gattChar = service.getCharacteristic(id);
            if (gattChar == null) {
                callback.acceptStatus("Characteristic '" + name + "' not found!");
            }
            return gattChar;
        }

       @Override
       public void onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic,
                                        int status) {

            if (characteristic.getUuid().equals(BrightnessCharacteristicId)) {
                byte b = characteristic.getValue()[0];
                initialBrightness = Byte.toUnsignedInt(b);
                callback.acceptStatus("Initial brightness: " + initialBrightness);
                gatt.readCharacteristic(styleCharacteristic);
                return;
            }

            if (characteristic.getUuid().equals(StyleCharacteristicId)) {
                byte b = characteristic.getValue()[0];
                initialStyle = Byte.toUnsignedInt(b);
                callback.acceptStatus("Initial style: " + initialStyle);
                gatt.readCharacteristic(namesCharacteristic);
                return;
            }

            if (characteristic.getUuid().equals(NamesCharacteristicId)) {
                String s = new String(characteristic.getValue());
                callback.acceptStatus("List of names: " + s);
                knownStyles = s.split(";");
                gatt.readCharacteristic(speedCharacteristic);
                return;
            }

            if (characteristic.getUuid().equals(SpeedCharacteristicId)) {
                byte b = characteristic.getValue()[0];
                initialSpeed = Byte.toUnsignedInt(b);
                callback.acceptStatus("Initial speed: " + b);
                gatt.readCharacteristic(stepCharacteristic);
                return;
            }

            if (characteristic.getUuid().equals(StepCharacteristicId)) {
               byte b = characteristic.getValue()[0];
               initialStep = Byte.toUnsignedInt(b);
               callback.acceptStatus("Initial step: " + b);

               // Finally done!
               callback.acceptStatus("Connected and ready.");
               callback.connected();
            }
       }
    };
}
