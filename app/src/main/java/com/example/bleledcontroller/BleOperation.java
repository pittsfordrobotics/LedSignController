package com.example.bleledcontroller;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public abstract class BleOperation {
    protected BluetoothGatt bluetoothGatt;
    protected BluetoothGattCharacteristic characteristic;

    public BleOperation(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
        this.bluetoothGatt = bluetoothGatt;
        this.characteristic = characteristic;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return characteristic;
    }
}
