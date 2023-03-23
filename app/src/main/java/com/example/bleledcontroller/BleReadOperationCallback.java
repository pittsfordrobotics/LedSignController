package com.example.bleledcontroller;

import android.bluetooth.BluetoothGattCharacteristic;

public interface BleReadOperationCallback {
    void ProcessCharacteristic(BluetoothGattCharacteristic characteristic);
}
