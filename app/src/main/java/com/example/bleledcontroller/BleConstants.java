package com.example.bleledcontroller;

import java.util.UUID;

public class BleConstants {
    public static final UUID LedServiceUuid = UUID.fromString("99be4fac-c708-41e5-a149-74047f554cc1");
    public static final UUID BrightnessCharacteristicId = UUID.fromString("5eccb54e-465f-47f4-ac50-6735bfc0e730");
    public static final UUID StyleCharacteristicId = UUID.fromString("c99db9f7-1719-43db-ad86-d02d36b191b3");
    public static final UUID NamesCharacteristicId = UUID.fromString("9022a1e0-3a1f-428a-bad6-3181a4d010a5");
    public static final UUID SpeedCharacteristicId = UUID.fromString("b975e425-62e4-4b08-a652-d64ad5097815");
    public static final UUID StepCharacteristicId = UUID.fromString("70e51723-0771-4946-a5b3-49693e9646b5");
    public static final UUID PatternCharacteristicId = UUID.fromString("6b503d25-f643-4823-a8a6-da51109e713f");
    public static final UUID PatternNamesCharacteristicId = UUID.fromString("348195d1-e237-4b0b-aea4-c818c3eb5e2a");
    public static final UUID BatteryVoltageCharacteristicId = UUID.fromString("ea0a95bc-7561-4b1e-8925-7973b3ad7b9a");
}
