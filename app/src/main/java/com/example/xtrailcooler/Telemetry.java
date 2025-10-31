package com.example.xtrailcooler;

/**
 * Current telemetry reported by the cooler over BLE.
 */
public class Telemetry {
    private final float insideCelsius;
    private final float hotCelsius;
    private final int stateBits;

    public Telemetry(float insideCelsius, float hotCelsius, int stateBits) {
        this.insideCelsius = insideCelsius;
        this.hotCelsius = hotCelsius;
        this.stateBits = stateBits;
    }

    public float getInsideCelsius() {
        return insideCelsius;
    }

    public float getHotCelsius() {
        return hotCelsius;
    }

    public int getStateBits() {
        return stateBits;
    }

    public boolean isPel1Active() {
        return (stateBits & 0x01) != 0;
    }

    public boolean isPel2Active() {
        return (stateBits & 0x02) != 0;
    }

    public boolean isHotFanActive() {
        return (stateBits & 0x04) != 0;
    }

    public boolean hasSensorAlarm() {
        return (stateBits & (1 << 8)) != 0;
    }

    public boolean hasOverheatAlarm() {
        return (stateBits & (1 << 9)) != 0;
    }

    public boolean hasSupplyAlarm() {
        return (stateBits & (1 << 10)) != 0;
    }
}
