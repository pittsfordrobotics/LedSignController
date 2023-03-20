package com.example.bleledcontroller;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public class BleReadCharacteristicOperation extends BleOperation {
    private BleReadOperationCallback callback;

    public BleReadCharacteristicOperation(
            BluetoothGatt bluetoothGatt,
            BluetoothGattCharacteristic characteristic,
            BleReadOperationCallback callback) {
        super(bluetoothGatt, characteristic);
        this.callback = callback;
    }

    public BleReadOperationCallback getCallback() {
        return callback;
    }
}
