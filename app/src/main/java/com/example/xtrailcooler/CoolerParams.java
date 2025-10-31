package com.example.xtrailcooler;

/**
 * Parameter set mirrored from the controller.
 */
public class CoolerParams {
    private float setpointC;
    private float hysteresisC;
    private float hotCutC;
    private float hotResumeC;
    private int fanRunOnSeconds;

    public float getSetpointC() {
        return setpointC;
    }

    public void setSetpointC(float setpointC) {
        this.setpointC = setpointC;
    }

    public float getHysteresisC() {
        return hysteresisC;
    }

    public void setHysteresisC(float hysteresisC) {
        this.hysteresisC = hysteresisC;
    }

    public float getHotCutC() {
        return hotCutC;
    }

    public void setHotCutC(float hotCutC) {
        this.hotCutC = hotCutC;
    }

    public float getHotResumeC() {
        return hotResumeC;
    }

    public void setHotResumeC(float hotResumeC) {
        this.hotResumeC = hotResumeC;
    }

    public int getFanRunOnSeconds() {
        return fanRunOnSeconds;
    }

    public void setFanRunOnSeconds(int fanRunOnSeconds) {
        this.fanRunOnSeconds = fanRunOnSeconds;
    }
}
