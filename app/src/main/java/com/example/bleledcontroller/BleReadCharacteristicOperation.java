package com.example.bleledcontroller;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public class BleReadCharacteristicOperation extends BleOperation {
    private BluetoothGattCharacteristic characteristic;
    private BleReadOperationCallback callback;

    public BleReadCharacteristicOperation(
            BluetoothGatt bluetoothGatt,
            BluetoothGattCharacteristic characteristic,
            BleReadOperationCallback callback) {
        super(bluetoothGatt);
        this.characteristic = characteristic;
        this.callback = callback;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }

    public BleReadOperationCallback getCallback() {
        return callback;
    }
}
