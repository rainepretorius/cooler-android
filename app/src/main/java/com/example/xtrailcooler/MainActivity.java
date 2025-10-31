package com.example.xtrailcooler;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements BleClient.Listener {

    private static final int REQ_PERMISSIONS = 42;

    private BleClient bleClient;

    private TextView statusText;
    private TextView deviceInfoText;
    private TextView insideTempText;
    private TextView hotTempText;
    private TextView outputStateText;
    private TextView alarmText;
    private EditText setpointInput;
    private EditText hysteresisInput;
    private EditText hotCutInput;
    private EditText hotResumeInput;
    private EditText fanRunOnInput;
    private EditText ssidInput;
    private EditText passwordInput;
    private SwitchMaterial forcePel1Switch;
    private SwitchMaterial forcePel2Switch;
    private SwitchMaterial forceFansSwitch;
    private Button connectButton;

    private boolean connected;
    private boolean forcePel1;
    private boolean forcePel2;
    private boolean forceFans;

    private static final int CMD_FORCE_PEL1 = 1 << 0;
    private static final int CMD_FORCE_PEL2 = 1 << 1;
    private static final int CMD_FORCE_FANS = 1 << 2;
    private static final int CMD_CLEAR_ALARMS = 1 << 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        deviceInfoText = findViewById(R.id.deviceInfoText);
        insideTempText = findViewById(R.id.insideTempText);
        hotTempText = findViewById(R.id.hotTempText);
        outputStateText = findViewById(R.id.outputStateText);
        alarmText = findViewById(R.id.alarmText);
        setpointInput = findViewById(R.id.setpointInput);
        hysteresisInput = findViewById(R.id.hysteresisInput);
        hotCutInput = findViewById(R.id.hotCutInput);
        hotResumeInput = findViewById(R.id.hotResumeInput);
        fanRunOnInput = findViewById(R.id.fanRunOnInput);
        ssidInput = findViewById(R.id.ssidInput);
        passwordInput = findViewById(R.id.passwordInput);
        forcePel1Switch = findViewById(R.id.forcePel1Switch);
        forcePel2Switch = findViewById(R.id.forcePel2Switch);
        forceFansSwitch = findViewById(R.id.forceFansSwitch);
        connectButton = findViewById(R.id.connectButton);
        Button applyParamsButton = findViewById(R.id.applyParamsButton);
        Button sendWifiButton = findViewById(R.id.sendWifiButton);
        Button clearAlarmsButton = findViewById(R.id.clearAlarmsButton);

        bleClient = new BleClient(this, this);

        connectButton.setOnClickListener(v -> {
            if (!connected) {
                if (ensurePermissions()) {
                    statusText.setText("Status: Scanning...");
                    bleClient.connect();
                }
            } else {
                bleClient.disconnect();
            }
        });

        applyParamsButton.setOnClickListener(v -> applyParams());
        sendWifiButton.setOnClickListener(v -> sendWifiCredentials());
        clearAlarmsButton.setOnClickListener(v -> {
            if (connected) {
                bleClient.sendCommand(CMD_CLEAR_ALARMS);
            } else {
                Toast.makeText(this, "Connect first", Toast.LENGTH_SHORT).show();
            }
        });

        forcePel1Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            forcePel1 = isChecked;
            pushForceCommand();
        });
        forcePel2Switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            forcePel2 = isChecked;
            pushForceCommand();
        });
        forceFansSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            forceFans = isChecked;
            pushForceCommand();
        });

        ensurePermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bleClient.disconnect();
    }

    private boolean ensurePermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addIfMissing(missing, Manifest.permission.BLUETOOTH);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_ADMIN);
            addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (missing.isEmpty()) {
            return true;
        }
        ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_PERMISSIONS);
        return false;
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
        }
    }

    // -----------------------------------------------------------------------------------------
    // BleClient.Listener
    // -----------------------------------------------------------------------------------------

    @Override
    public void onConnectionStateChanged(boolean connected) {
        this.connected = connected;
        if (connected) {
            statusText.setText("Status: Connected");
            connectButton.setText("Disconnect");
        } else {
            statusText.setText("Status: Disconnected");
            connectButton.setText("Connect");
        }
    }

    @Override
    public void onTelemetryUpdated(@NonNull Telemetry telemetry) {
        insideTempText.setText(String.format(Locale.US, "Inside temp: %.1f °C", telemetry.getInsideCelsius()));
        hotTempText.setText(String.format(Locale.US, "Hot side temp: %.1f °C", telemetry.getHotCelsius()));
        outputStateText.setText(String.format(Locale.US,
                "Outputs: P1 %s | P2 %s | Fan %s",
                telemetry.isPel1Active() ? "ON" : "OFF",
                telemetry.isPel2Active() ? "ON" : "OFF",
                telemetry.isHotFanActive() ? "ON" : "OFF"));
        List<String> alarms = new ArrayList<>();
        if (telemetry.hasSensorAlarm()) {
            alarms.add("Sensor fault");
        }
        if (telemetry.hasOverheatAlarm()) {
            alarms.add("Overheat");
        }
        if (telemetry.hasSupplyAlarm()) {
            alarms.add("Supply");
        }
        alarmText.setText(alarms.isEmpty() ? "Alarms: none" : "Alarms: " + TextUtils.join(", ", alarms));
    }

    @Override
    public void onParametersUpdated(@NonNull CoolerParams params) {
        setpointInput.setText(String.format(Locale.US, "%.1f", params.getSetpointC()));
        hysteresisInput.setText(String.format(Locale.US, "%.1f", params.getHysteresisC()));
        hotCutInput.setText(String.format(Locale.US, "%.1f", params.getHotCutC()));
        hotResumeInput.setText(String.format(Locale.US, "%.1f", params.getHotResumeC()));
        fanRunOnInput.setText(String.valueOf(params.getFanRunOnSeconds()));
    }

    @Override
    public void onDeviceInfo(@NonNull String info) {
        deviceInfoText.setText("Device info: " + info);
    }

    @Override
    public void onError(@NonNull String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        statusText.setText("Status: " + message);
    }

    // -----------------------------------------------------------------------------------------
    // UI helpers
    // -----------------------------------------------------------------------------------------

    private void pushForceCommand() {
        int mask = 0;
        if (forcePel1) {
            mask |= CMD_FORCE_PEL1;
        }
        if (forcePel2) {
            mask |= CMD_FORCE_PEL2;
        }
        if (forceFans) {
            mask |= CMD_FORCE_FANS;
        }
        if (!connected) {
            Toast.makeText(this, "Connect first", Toast.LENGTH_SHORT).show();
            return;
        }
        bleClient.sendCommand(mask);
    }

    private void applyParams() {
        if (!connected) {
            Toast.makeText(this, "Connect first", Toast.LENGTH_SHORT).show();
            return;
        }
        Float setpoint = parseFloat(setpointInput.getText().toString());
        Float hyst = parseFloat(hysteresisInput.getText().toString());
        Float hotCut = parseFloat(hotCutInput.getText().toString());
        Float hotResume = parseFloat(hotResumeInput.getText().toString());
        Integer fanRunOn = parseInt(fanRunOnInput.getText().toString());
        if (setpoint != null) {
            bleClient.updateSetpoint(setpoint);
        }
        if (hyst != null) {
            bleClient.updateHysteresis(hyst);
        }
        if (hotCut != null) {
            bleClient.updateHotCut(hotCut);
        }
        if (hotResume != null) {
            bleClient.updateHotResume(hotResume);
        }
        if (fanRunOn != null) {
            bleClient.updateFanRunOn(fanRunOn);
        }
        Toast.makeText(this, "Parameters sent", Toast.LENGTH_SHORT).show();
    }

    private void sendWifiCredentials() {
        if (!connected) {
            Toast.makeText(this, "Connect first", Toast.LENGTH_SHORT).show();
            return;
        }
        String ssid = ssidInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (ssid.isEmpty()) {
            Toast.makeText(this, "Enter SSID", Toast.LENGTH_SHORT).show();
            return;
        }
        bleClient.writeWifiCredentials(ssid, password);
        Toast.makeText(this, "Wi-Fi credentials sent", Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private Float parseFloat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number: " + value, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    @Nullable
    private Integer parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number: " + value, Toast.LENGTH_SHORT).show();
            return null;
        }
    }
}
