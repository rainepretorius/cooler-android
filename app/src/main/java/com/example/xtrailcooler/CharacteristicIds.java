package com.example.xtrailcooler;

import java.util.UUID;

/**
 * BLE UUIDs exposed by the XTrailCooler firmware.
 */
public final class CharacteristicIds {
    public static final UUID SERVICE_COOLER = UUID.fromString("b4f00001-0000-1000-8000-00805f9b34fb");
    public static final UUID INSIDE_TEMP = UUID.fromString("b4f01001-0000-1000-8000-00805f9b34fb");
    public static final UUID HOT_TEMP = UUID.fromString("b4f01002-0000-1000-8000-00805f9b34fb");
    public static final UUID STATE_BITS = UUID.fromString("b4f01003-0000-1000-8000-00805f9b34fb");
    public static final UUID SETPOINT = UUID.fromString("b4f02001-0000-1000-8000-00805f9b34fb");
    public static final UUID HYSTERESIS = UUID.fromString("b4f02002-0000-1000-8000-00805f9b34fb");
    public static final UUID HOT_CUT = UUID.fromString("b4f02003-0000-1000-8000-00805f9b34fb");
    public static final UUID HOT_RESUME = UUID.fromString("b4f02004-0000-1000-8000-00805f9b34fb");
    public static final UUID FAN_RUNON = UUID.fromString("b4f02005-0000-1000-8000-00805f9b34fb");
    public static final UUID COMMAND = UUID.fromString("b4f03001-0000-1000-8000-00805f9b34fb");
    public static final UUID DEVICE_INFO = UUID.fromString("b4f03002-0000-1000-8000-00805f9b34fb");
    public static final UUID WIFI_CREDS = UUID.fromString("b4f04001-0000-1000-8000-00805f9b34fb");

    public static final UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private CharacteristicIds() {
    }
}
