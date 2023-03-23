package com.example.bleledcontroller;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

public class BleWriteCharacteristicOperation extends BleOperation {
    private byte[] value = new byte[] {0};

    public BleWriteCharacteristicOperation(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
        super(bluetoothGatt, characteristic);
    }

    public BleWriteCharacteristicOperation(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, byte[] value) {
        super(bluetoothGatt, characteristic);
        this.value = value;
    }

    public BleWriteCharacteristicOperation withValue(byte[] value) {
        return new BleWriteCharacteristicOperation(bluetoothGatt, characteristic, value);
    }

    public byte[] getTargetValue() {
        return value;
    }
}
