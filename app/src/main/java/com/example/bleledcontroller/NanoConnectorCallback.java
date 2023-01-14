package com.example.bleledcontroller;

public interface NanoConnectorCallback {
    void acceptStatus(String status);
    void connected();
}
