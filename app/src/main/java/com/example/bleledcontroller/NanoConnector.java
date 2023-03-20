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
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class NanoConnector {
    //
    // Much help from
    // https://punchthrough.com/android-ble-guide/
    //
    private static final UUID LedServiceUuid = UUID.fromString("99be4fac-c708-41e5-a149-74047f554cc1");
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
    private HashMap<UUID, BleReadCharacteristicOperation> readOperations;
    private HashMap<UUID, BleWriteCharacteristicOperation> writeOperations;
    private Queue<BleOperation> operationQueue = new LinkedList<>();
    private BleOperation pendingOperation = null;
    private boolean isInitialized = false;

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
                .setServiceUuid(new ParcelUuid(LedServiceUuid))
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
        addOperation(writeOperations.get(BrightnessCharacteristicId).withValue(new byte[] {(byte)brightness}));
    }

    public int getInitialStyle() {
        return initialStyle;
    }

    public void setStyle(int style) {
        addOperation(writeOperations.get(StyleCharacteristicId).withValue(new byte[] {(byte)style}));
    }

    public String[] getKnownStyles() { return knownStyles; }
    public String[] getKnownPatterns() { return knownPatterns; }

    public int getInitialSpeed() { return initialSpeed; }
    public void setSpeed(int speed) {
        addOperation(writeOperations.get(SpeedCharacteristicId).withValue(new byte[] {(byte)speed}));
    }

    public int getInitialStep() { return initialStep; }
    public void setStep(int step) {
        addOperation(writeOperations.get(StepCharacteristicId).withValue(new byte[] {(byte)step}));
    }

    public int getInitialPattern() { return initialPattern; }
    public void setPattern(int pattern) {
        addOperation(writeOperations.get(PatternCharacteristicId).withValue(new byte[] {(byte)pattern}));
    }

    public void refreshVoltage() {
        addOperation(readOperations.get(BatteryVoltageCharacteristicId));
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

    //
    // Operation queuing methods - add / complete / doNext.
    // BLE is notorious for dropping concurrent operations, so
    // this queuing mechanism allows us to "stack up" operations.
    //
    private void addOperation(BleOperation operation) {
        operationQueue.add(operation);
        if (pendingOperation == null) {
            // No operations are yet processing. Kick off the next one in the queue.
            doNextOperation();
        }
    }

    // Complete the pending operation and start the next one.
    private void completeOperation() {
        pendingOperation = null;
        doNextOperation();
    }

    private void doNextOperation() {
        if (pendingOperation != null) {
            // Already working on an operation.
            return;
        }

        if (operationQueue.isEmpty()) {
            // No more operations to run.
            // If we haven't yet let our client know that we've been fully initialized,
            // (that is, if all of the initial values have been now been read), let the client know.
            if (!isInitialized) {
                isInitialized = true;
                callback.acceptStatus("Connected and ready.");
                callback.connected();
            }
            return;
        }

        pendingOperation = operationQueue.remove();
        if (pendingOperation instanceof BleReadCharacteristicOperation) {
            BleReadCharacteristicOperation op = (BleReadCharacteristicOperation) pendingOperation;
            op.getBluetoothGatt().readCharacteristic(op.getCharacteristic());
            return;
        }
        if (pendingOperation instanceof BleWriteCharacteristicOperation) {
            BleWriteCharacteristicOperation op = (BleWriteCharacteristicOperation) pendingOperation;
            BluetoothGattCharacteristic characteristic = op.getCharacteristic();
            characteristic.setValue(op.getTargetValue());
            op.getBluetoothGatt().writeCharacteristic(characteristic);
            return;
        }

        callback.acceptStatus("Unknown operation type encountered. Skipping.");
        return;
    }

    // Setup the set of known operations for reading/writing the BLE characteristics.
    // The hashmap is keyed by the characteristic UUID.
    private void InitializeCharacteristicOperations()
    {
        readOperations = new HashMap<>();
        readOperations.put(BrightnessCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                brightnessCharacteristic,
                this::setBrightnessFromCharacteristic));
        readOperations.put(StyleCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                styleCharacteristic,
                this::setStyleFromCharacteristic));
        readOperations.put(NamesCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                namesCharacteristic,
                this::setNamesFromCharacteristic));
        readOperations.put(SpeedCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                speedCharacteristic,
                this::setSpeedFromCharacteristic));
        readOperations.put(StepCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                stepCharacteristic,
                this::setStepFromCharacteristic));
        readOperations.put(PatternCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                patternCharacteristic,
                this::setPatternFromCharacteristic));
        readOperations.put(PatternNamesCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                patternCharacteristic,
                this::setPatternNamesFromCharacteristic));
        readOperations.put(BatteryVoltageCharacteristicId, new BleReadCharacteristicOperation(
                bluetoothGatt,
                batteryVoltageCharacteristic,
                this::setBatteryVoltageFromCharacteristic));

        writeOperations = new HashMap<>();
        writeOperations.put(BrightnessCharacteristicId, new BleWriteCharacteristicOperation(bluetoothGatt, brightnessCharacteristic));
        writeOperations.put(StyleCharacteristicId, new BleWriteCharacteristicOperation(bluetoothGatt, styleCharacteristic));
        writeOperations.put(SpeedCharacteristicId, new BleWriteCharacteristicOperation(bluetoothGatt, speedCharacteristic));
        writeOperations.put(StepCharacteristicId, new BleWriteCharacteristicOperation(bluetoothGatt, stepCharacteristic));
        writeOperations.put(PatternCharacteristicId, new BleWriteCharacteristicOperation(bluetoothGatt, patternCharacteristic));
    }

    //
    // Define the callbacks for processing the results of a characteristic read
    //
    private void setBrightnessFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte b = characteristic.getValue()[0];
        initialBrightness = Byte.toUnsignedInt(b);
        callback.acceptStatus("Retrieved brightness: " + initialBrightness);
    }

    private void setStyleFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte b = characteristic.getValue()[0];
        initialStyle = Byte.toUnsignedInt(b);
        callback.acceptStatus("Retrieved style: " + initialStyle);
    }

    private void setNamesFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        String s = new String(characteristic.getValue());
        callback.acceptStatus("Retrieved list of names: " + s);
        knownStyles = s.split(";");
    }

    private void setSpeedFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte b = characteristic.getValue()[0];
        initialSpeed = Byte.toUnsignedInt(b);
        callback.acceptStatus("Retrieved speed: " + b);
    }

    private void setStepFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte b = characteristic.getValue()[0];
        initialStep = Byte.toUnsignedInt(b);
        callback.acceptStatus("Retrieved step: " + b);
    }

    private void setPatternFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte b = characteristic.getValue()[0];
        initialPattern = Byte.toUnsignedInt(b);
        callback.acceptStatus("Retrieved pattern: " + b);
    }

    private void setPatternNamesFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        String s = new String(characteristic.getValue());
        callback.acceptStatus("Retrieved list of patterns: " + s);
        knownPatterns = s.split(";");
    }

    private void setBatteryVoltageFromCharacteristic(BluetoothGattCharacteristic characteristic) {
        byte[] b = characteristic.getValue();
        float voltage = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        callback.acceptStatus("Retrieved battery voltage: " + voltage);
        callback.acceptBatteryVoltage(voltage);
    }

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
            InitializeCharacteristicOperations();

            // We can only read one characteristic at a time, so add all the initial
            // characteristic read operations to the queue.
            addOperation(readOperations.get(BrightnessCharacteristicId));
            addOperation(readOperations.get(StyleCharacteristicId));
            addOperation(readOperations.get(NamesCharacteristicId));
            addOperation(readOperations.get(SpeedCharacteristicId));
            addOperation(readOperations.get(StepCharacteristicId));
            addOperation(readOperations.get(PatternCharacteristicId));
            addOperation(readOperations.get(PatternNamesCharacteristicId));
            addOperation(readOperations.get(BatteryVoltageCharacteristicId));
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

            if (!(pendingOperation instanceof BleReadCharacteristicOperation)) {
                // Something unexpected happened!
                callback.acceptStatus("ERROR: In the 'read' callback, but the pending operation is not a read operation.");
                completeOperation();
                return;
            }

            BleReadCharacteristicOperation op = (BleReadCharacteristicOperation)pendingOperation;
            op.getCallback().ProcessCharacteristic(characteristic);
            completeOperation();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            completeOperation();
        }
    };
}
