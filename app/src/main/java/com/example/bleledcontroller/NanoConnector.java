package com.example.bleledcontroller;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_FLOAT;
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
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
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
    private static final UUID PatternCharacteristicId = UUID.fromString("6b503d25-f643-4823-a8a6-da51109e713f");
    private static final UUID PatternNamesCharacteristicId = UUID.fromString("348195d1-e237-4b0b-aea4-c818c3eb5e2a");
    private static final UUID BatteryVoltageCharacteristicId = UUID.fromString("ea0a95bc-7561-4b1e-8925-7973b3ad7b9a");

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
    private BluetoothGattCharacteristic patternCharacteristic;
    private BluetoothGattCharacteristic patternNamesCharacteristic;
    private BluetoothGattCharacteristic batteryVoltageCharacteristic;
    private LinkedList<BluetoothGattCharacteristic> characteristicQueue = new LinkedList<>();

    // Could make this Optional<Integer> to avoid needing a "-1" sentinel value,
    // but Optional was introduced in an API that's higher than the current minimum.
    private int initialBrightness = -1;
    private int initialStyle = -1;
    private String[] knownStyles;
    private String[] knownPatterns;
    private int initialSpeed = -1;
    private int initialStep = -1;
    private int initialPattern = -1;

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

        bluetoothAdapter.getBluetoothLeScanner().startScan(filters, scanSettings, leScanCallback);
    }

    public int getInitialBrightness() {
        return initialBrightness;
    }

    public void setBrightness(int brightness) {
        brightnessCharacteristic.setValue(new byte[] {(byte)brightness});
        bluetoothGatt.writeCharacteristic(brightnessCharacteristic);
    }

    public int getInitialStyle() {
        return initialStyle;
    }

    public void setStyle(int style) {
        styleCharacteristic.setValue(new byte[] {(byte)style});
        bluetoothGatt.writeCharacteristic(styleCharacteristic);
    }

    public String[] getKnownStyles() { return knownStyles; }
    public String[] getKnownPatterns() { return knownPatterns; }

    public int getInitialSpeed() { return initialSpeed; }
    public void setSpeed(int speed) {
        speedCharacteristic.setValue(new byte[] {(byte)speed});
        bluetoothGatt.writeCharacteristic(speedCharacteristic);
    }

    public int getInitialStep() { return initialStep; }
    public void setStep(int step) {
        stepCharacteristic.setValue(new byte[] {(byte)step});
        bluetoothGatt.writeCharacteristic(stepCharacteristic);
    }

    public int getInitialPattern() { return initialPattern; }
    public void setPattern(int pattern) {
        patternCharacteristic.setValue(new byte[] {(byte)pattern});
        bluetoothGatt.writeCharacteristic(patternCharacteristic);
    }

    public void refreshVoltage() {
        bluetoothGatt.readCharacteristic(batteryVoltageCharacteristic);
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    bluetoothDevice = result.getDevice();
                    String status = "Discovered device: " + bluetoothDevice.getName();
                    callback.acceptStatus(status);
                    callback.acceptStatus("Stopping scan and attempting GATT connection.");
                    bluetoothAdapter.getBluetoothLeScanner().stopScan(leScanCallback);
                    bluetoothDevice.connectGatt(context, false, gattCallback);
                }
            };

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            callback.acceptStatus("BLE connect state changed. Status: " + status + ", state: " + newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    callback.acceptStatus("Connected to device - discovering services");
                    bluetoothGatt = gatt;
                    bluetoothGatt.discoverServices();
                } else {
                    processDisconnect(gatt, "Unexpected GATT state encountered: " + newState);
                }
            } else {
                String msg = "Unexpected GATT status encountered. Status: " + status + ", state: " + newState;
                processDisconnect(gatt, msg);
            }
        }

        private void processDisconnect(BluetoothGatt gatt, String callbackMessage) {
            callback.acceptStatus(callbackMessage);
            gatt.disconnect();
            gatt.close();
            callback.disconnected();
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
            patternCharacteristic = findCharacteristic(ledService, PatternCharacteristicId, "Pattern");
            patternNamesCharacteristic = findCharacteristic(ledService, PatternNamesCharacteristicId, "PatternNames");
            batteryVoltageCharacteristic = findCharacteristic(ledService, BatteryVoltageCharacteristicId, "BatterVoltage");

            if (brightnessCharacteristic == null
                || styleCharacteristic == null
                || namesCharacteristic == null
                || speedCharacteristic == null
                || stepCharacteristic == null
                || patternCharacteristic == null
                || patternNamesCharacteristic == null
                || batteryVoltageCharacteristic == null) {
                callback.acceptStatus("At least one characteristic was not found in the service.");
                return;
            }

            callback.acceptStatus("Services bound successfully.");

            // We can only read one characteristic at a time, so we'll start by reading the
            // brightness characteristic, and create a list of all the other characteristics
            // to be read after that.
            characteristicQueue.add(styleCharacteristic);
            characteristicQueue.add(namesCharacteristic);
            characteristicQueue.add(speedCharacteristic);
            characteristicQueue.add(stepCharacteristic);
            characteristicQueue.add(patternCharacteristic);
            characteristicQueue.add(patternNamesCharacteristic);
            characteristicQueue.add(batteryVoltageCharacteristic);

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
            }

            if (characteristic.getUuid().equals(StyleCharacteristicId)) {
                byte b = characteristic.getValue()[0];
                initialStyle = Byte.toUnsignedInt(b);
                callback.acceptStatus("Initial style: " + initialStyle);
            }

            if (characteristic.getUuid().equals(NamesCharacteristicId)) {
                String s = new String(characteristic.getValue());
                callback.acceptStatus("List of names: " + s);
                knownStyles = s.split(";");
            }

            if (characteristic.getUuid().equals(SpeedCharacteristicId)) {
                byte b = characteristic.getValue()[0];
                initialSpeed = Byte.toUnsignedInt(b);
                callback.acceptStatus("Initial speed: " + b);
            }

            if (characteristic.getUuid().equals(StepCharacteristicId)) {
               byte b = characteristic.getValue()[0];
               initialStep = Byte.toUnsignedInt(b);
               callback.acceptStatus("Initial step: " + b);
            }

            if (characteristic.getUuid().equals(PatternCharacteristicId)) {
                byte b = characteristic.getValue()[0];
                initialPattern = Byte.toUnsignedInt(b);
                callback.acceptStatus("Initial pattern: " + b);
            }

            if (characteristic.getUuid().equals(PatternNamesCharacteristicId)) {
                String s = new String(characteristic.getValue());
                callback.acceptStatus("List of patterns: " + s);
                knownPatterns = s.split(";");
            }

            if (characteristic.getUuid().equals(BatteryVoltageCharacteristicId)) {
                byte[] b = characteristic.getValue();
                float voltage = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getFloat();
                callback.acceptStatus("Read battery voltage: " + voltage);
                callback.acceptBatteryVoltage(voltage);
            }

            if (characteristicQueue.isEmpty()) {
                callback.acceptStatus("Connected and ready.");
                callback.connected();
                return;
            }

            BluetoothGattCharacteristic nextCharacteristic = characteristicQueue.pop();
            gatt.readCharacteristic(nextCharacteristic);
        }
    };
}
