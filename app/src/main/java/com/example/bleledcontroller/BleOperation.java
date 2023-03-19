package com.example.bleledcontroller;

import android.bluetooth.BluetoothGatt;

public abstract class BleOperation {
    private BluetoothGatt bluetoothGatt;

    public BleOperation(BluetoothGatt bluetoothGatt) {
        this.bluetoothGatt = bluetoothGatt;
    }

    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }
}
