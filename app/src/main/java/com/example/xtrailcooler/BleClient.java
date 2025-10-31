package com.example.xtrailcooler;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;

/**
 * Simple BLE client capable of discovering and interacting with the XTrailCooler controller.
 */
public class BleClient {
    public interface Listener {
        void onConnectionStateChanged(boolean connected);

        void onTelemetryUpdated(@NonNull Telemetry telemetry);

        void onParametersUpdated(@NonNull CoolerParams params);

        void onDeviceInfo(@NonNull String info);

        void onError(@NonNull String message);
    }

    private static final long SCAN_TIMEOUT_MS = 15000L;

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BluetoothLeScanner scanner;
    private boolean scanning;
    private BluetoothGatt bluetoothGatt;

    private BluetoothGattCharacteristic chInsideTemp;
    private BluetoothGattCharacteristic chHotTemp;
    private BluetoothGattCharacteristic chStateBits;
    private BluetoothGattCharacteristic chSetpoint;
    private BluetoothGattCharacteristic chHysteresis;
    private BluetoothGattCharacteristic chHotCut;
    private BluetoothGattCharacteristic chHotResume;
    private BluetoothGattCharacteristic chFanRunOn;
    private BluetoothGattCharacteristic chCommand;
    private BluetoothGattCharacteristic chDeviceInfo;
    private BluetoothGattCharacteristic chWifiCreds;

    private Float lastInside;
    private Float lastHot;
    private Integer lastStateBits;
    private final CoolerParams params = new CoolerParams();

    private final Queue<BluetoothGattDescriptor> descriptorQueue = new ArrayDeque<>();
    private final Queue<BluetoothGattCharacteristic> readQueue = new ArrayDeque<>();

    public BleClient(@NonNull Context context, @NonNull Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /**
     * Initiates a scan for a controller advertising the {@link #TARGET_NAME} name.
     */
    public void connect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
        startScan();
    }

    public void disconnect() {
        stopScan();
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        notifyConnectionState(false);
    }

    private static final String TARGET_NAME = "XTrailCooler";

    private void startScan() {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            postError("Bluetooth not available on this device");
            return;
        }
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            postError("Bluetooth adapter disabled");
            return;
        }
        if (!hasScanPermission()) {
            postError("Missing Bluetooth permissions");
            return;
        }
        if (scanning) {
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            postError("Unable to access BLE scanner");
            return;
        }
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder().setDeviceName(TARGET_NAME).build());
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(filters, settings, scanCallback);
        scanning = true;
        mainHandler.postDelayed(() -> {
            if (scanning && bluetoothGatt == null) {
                stopScan();
                postError("No XTrailCooler device found");
            }
        }, SCAN_TIMEOUT_MS);
    }

    private void stopScan() {
        if (!scanning) {
            return;
        }
        if (scanner != null && hasScanPermission()) {
            scanner.stopScan(scanCallback);
        }
        scanning = false;
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanFailed(int errorCode) {
            stopScan();
            postError("Scan failed: " + errorCode);
        }

        @Override
        public void onScanResult(int callbackType, @NonNull ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : device.getName();
            if (name != null && name.equalsIgnoreCase(TARGET_NAME)) {
                stopScan();
                connectGatt(device);
            }
        }
    };

    private void connectGatt(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                gatt.close();
                bluetoothGatt = null;
                postError(String.format(Locale.US, "Connection error: 0x%02X", status));
                notifyConnectionState(false);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                notifyConnectionState(true);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt.close();
                bluetoothGatt = null;
                notifyConnectionState(false);
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postError("Service discovery failed: " + status);
                return;
            }
            BluetoothGattService service = gatt.getService(CharacteristicIds.SERVICE_COOLER);
            if (service == null) {
                postError("Cooler service not found");
                return;
            }
            chInsideTemp = service.getCharacteristic(CharacteristicIds.INSIDE_TEMP);
            chHotTemp = service.getCharacteristic(CharacteristicIds.HOT_TEMP);
            chStateBits = service.getCharacteristic(CharacteristicIds.STATE_BITS);
            chSetpoint = service.getCharacteristic(CharacteristicIds.SETPOINT);
            chHysteresis = service.getCharacteristic(CharacteristicIds.HYSTERESIS);
            chHotCut = service.getCharacteristic(CharacteristicIds.HOT_CUT);
            chHotResume = service.getCharacteristic(CharacteristicIds.HOT_RESUME);
            chFanRunOn = service.getCharacteristic(CharacteristicIds.FAN_RUNON);
            chCommand = service.getCharacteristic(CharacteristicIds.COMMAND);
            chDeviceInfo = service.getCharacteristic(CharacteristicIds.DEVICE_INFO);
            chWifiCreds = service.getCharacteristic(CharacteristicIds.WIFI_CREDS);

            enableNotifications(chInsideTemp);
            enableNotifications(chHotTemp);
            enableNotifications(chStateBits);

            queueRead(chInsideTemp);
            queueRead(chHotTemp);
            queueRead(chStateBits);
            queueRead(chSetpoint);
            queueRead(chHysteresis);
            queueRead(chHotCut);
            queueRead(chHotResume);
            queueRead(chFanRunOn);
            queueRead(chDeviceInfo);
        }

        @Override
        public void onDescriptorWrite(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status) {
            descriptorQueue.poll();
            if (status != BluetoothGatt.GATT_SUCCESS) {
                postError("Descriptor write failed: " + status);
            }
            writeNextDescriptor(gatt);
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
            readQueue.poll();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleCharacteristic(characteristic);
            }
            readNext(gatt);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic) {
            handleCharacteristic(characteristic);
        }
    };

    private void enableNotifications(@Nullable BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) {
            return;
        }
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CharacteristicIds.CLIENT_CONFIG_DESCRIPTOR);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            descriptorQueue.add(descriptor);
            if (descriptorQueue.size() == 1) {
                bluetoothGatt.writeDescriptor(descriptor);
            }
        }
    }

    private void writeNextDescriptor(@NonNull BluetoothGatt gatt) {
        BluetoothGattDescriptor next = descriptorQueue.peek();
        if (next != null) {
            gatt.writeDescriptor(next);
        }
    }

    private void queueRead(@Nullable BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) {
            return;
        }
        readQueue.add(characteristic);
        if (readQueue.size() == 1) {
            bluetoothGatt.readCharacteristic(characteristic);
        }
    }

    private void readNext(@NonNull BluetoothGatt gatt) {
        BluetoothGattCharacteristic next = readQueue.peek();
        if (next != null) {
            gatt.readCharacteristic(next);
        }
    }

    private void handleCharacteristic(@NonNull BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        if (CharacteristicIds.INSIDE_TEMP.equals(uuid)) {
            lastInside = decodeTemperature(characteristic);
            notifyTelemetry();
        } else if (CharacteristicIds.HOT_TEMP.equals(uuid)) {
            lastHot = decodeTemperature(characteristic);
            notifyTelemetry();
        } else if (CharacteristicIds.STATE_BITS.equals(uuid)) {
            Integer bits = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            if (bits != null) {
                lastStateBits = bits;
                notifyTelemetry();
            }
        } else if (CharacteristicIds.SETPOINT.equals(uuid)) {
            params.setSetpointC(decodeTemperature(characteristic));
            notifyParams();
        } else if (CharacteristicIds.HYSTERESIS.equals(uuid)) {
            params.setHysteresisC(decodeTemperature(characteristic));
            notifyParams();
        } else if (CharacteristicIds.HOT_CUT.equals(uuid)) {
            params.setHotCutC(decodeTemperature(characteristic));
            notifyParams();
        } else if (CharacteristicIds.HOT_RESUME.equals(uuid)) {
            params.setHotResumeC(decodeTemperature(characteristic));
            notifyParams();
        } else if (CharacteristicIds.FAN_RUNON.equals(uuid)) {
            Integer value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            if (value != null) {
                params.setFanRunOnSeconds(value);
                notifyParams();
            }
        } else if (CharacteristicIds.DEVICE_INFO.equals(uuid)) {
            byte[] value = characteristic.getValue();
            if (value != null && value.length > 0) {
                postDeviceInfo(new String(value));
            }
        }
    }

    private float decodeTemperature(@NonNull BluetoothGattCharacteristic characteristic) {
        Integer raw = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0);
        if (raw == null) {
            return Float.NaN;
        }
        return raw / 10f;
    }

    private void notifyTelemetry() {
        if (lastInside == null || lastHot == null || lastStateBits == null) {
            return;
        }
        final Telemetry telemetry = new Telemetry(lastInside, lastHot, lastStateBits);
        mainHandler.post(() -> listener.onTelemetryUpdated(telemetry));
    }

    private void notifyParams() {
        final CoolerParams snapshot = cloneParams();
        mainHandler.post(() -> listener.onParametersUpdated(snapshot));
    }

    private CoolerParams cloneParams() {
        CoolerParams copy = new CoolerParams();
        copy.setSetpointC(params.getSetpointC());
        copy.setHysteresisC(params.getHysteresisC());
        copy.setHotCutC(params.getHotCutC());
        copy.setHotResumeC(params.getHotResumeC());
        copy.setFanRunOnSeconds(params.getFanRunOnSeconds());
        return copy;
    }

    private void notifyConnectionState(boolean connected) {
        mainHandler.post(() -> listener.onConnectionStateChanged(connected));
    }

    private void postError(@NonNull String message) {
        mainHandler.post(() -> listener.onError(message));
    }

    private void postDeviceInfo(@NonNull String info) {
        mainHandler.post(() -> listener.onDeviceInfo(info));
    }

    // ---------------------------------------------------------------------------------------------
    // Write helpers
    // ---------------------------------------------------------------------------------------------
    public void sendCommand(int commandBits) {
        if (bluetoothGatt == null || chCommand == null) {
            postError("Not connected");
            return;
        }
        chCommand.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        chCommand.setValue(new byte[]{(byte) (commandBits & 0xFF)});
        bluetoothGatt.writeCharacteristic(chCommand);
    }

    public void updateSetpoint(float setpointC) {
        writeTemperatureCharacteristic(chSetpoint, setpointC);
    }

    public void updateHysteresis(float hysteresisC) {
        writeTemperatureCharacteristic(chHysteresis, hysteresisC);
    }

    public void updateHotCut(float hotCutC) {
        writeTemperatureCharacteristic(chHotCut, hotCutC);
    }

    public void updateHotResume(float hotResumeC) {
        writeTemperatureCharacteristic(chHotResume, hotResumeC);
    }

    public void updateFanRunOn(int seconds) {
        if (bluetoothGatt == null || chFanRunOn == null) {
            postError("Not connected");
            return;
        }
        if (seconds < 0) {
            seconds = 0;
        }
        chFanRunOn.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        chFanRunOn.setValue(seconds, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
        bluetoothGatt.writeCharacteristic(chFanRunOn);
    }

    public void writeWifiCredentials(@NonNull String ssid, @NonNull String password) {
        if (bluetoothGatt == null || chWifiCreds == null) {
            postError("Not connected");
            return;
        }
        byte[] ssidBytes = ssid.getBytes();
        byte[] passBytes = password.getBytes();
        if (ssidBytes.length > 32 || passBytes.length > 63) {
            postError("Credentials too long");
            return;
        }
        byte[] payload = new byte[2 + ssidBytes.length + passBytes.length];
        payload[0] = (byte) ssidBytes.length;
        System.arraycopy(ssidBytes, 0, payload, 1, ssidBytes.length);
        payload[1 + ssidBytes.length] = (byte) passBytes.length;
        System.arraycopy(passBytes, 0, payload, 2 + ssidBytes.length, passBytes.length);
        chWifiCreds.setValue(payload);
        bluetoothGatt.writeCharacteristic(chWifiCreds);
    }

    private void writeTemperatureCharacteristic(@Nullable BluetoothGattCharacteristic characteristic, float valueC) {
        if (bluetoothGatt == null || characteristic == null) {
            postError("Not connected");
            return;
        }
        int raw = Math.round(valueC * 10f);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        characteristic.setValue(raw, BluetoothGattCharacteristic.FORMAT_SINT16, 0);
        bluetoothGatt.writeCharacteristic(characteristic);
    }
}
