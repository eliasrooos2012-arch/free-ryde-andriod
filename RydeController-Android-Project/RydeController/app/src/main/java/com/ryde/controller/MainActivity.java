package com.ryde.controller;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Standalone BLE diagnostic/controller app for a personally owned scooter.
 * No undocumented unlocking commands are provided. Commands must be configured
 * from authorized documentation for the actual controller.
 */
public class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 13, 11);
    private static final int PANEL = Color.rgb(24, 30, 26);
    private static final int LIME = Color.rgb(140, 241, 60);
    private static final int TEXT = Color.rgb(237, 245, 237);
    private static final int MUTED = Color.rgb(162, 176, 163);
    private static final int REQUEST_BLE = 400;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final List<String> deviceLabels = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> writableChars = new ArrayList<>();
    private final List<String> writeLabels = new ArrayList<>();

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic selectedCharacteristic;
    private BluetoothDevice selectedDevice;
    private boolean connected = false;
    private boolean scanning = false;
    private boolean writePending = false;
    private int lastRssi = 0;
    private String diagnostic = "No GATT services discovered yet.";
    private String scanAdvertisement = "";

    private TextView connectionText;
    private TextView deviceText;
    private TextView characteristicText;
    private TextView detailsText;
    private TextView statusText;
    private EditText onHex;
    private EditText offHex;
    private Button scanButton;
    private Button chooseDeviceButton;
    private Button chooseCharacteristicButton;
    private Button onButton;
    private Button offButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        createScreen();
        onHex.setText(getPreferences(0).getString("onHex", ""));
        offHex.setText(getPreferences(0).getString("offHex", ""));
        status("Ready. Scan nearby BLE devices with the scooter beside your phone.");
        refreshButtons();
    }

    private void createScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(34));
        scroll.addView(root);
        setContentView(scroll);

        TextView eyebrow = text("PRIVATE VEHICLE • EXPERIMENTAL", 11, LIME, true);
        root.addView(eyebrow);
        root.addView(space(8));
        root.addView(text("ryde.", 38, TEXT, true));
        root.addView(text("Controller & BLE diagnostics", 15, MUTED, false));
        root.addView(space(18));

        LinearLayout hero = panel();
        hero.addView(text("DEVICE STATUS", 11, MUTED, true));
        connectionText = text("●  DISCONNECTED", 21, TEXT, true);
        hero.addView(connectionText);
        hero.addView(space(7));
        deviceText = text("No scooter selected", 13, MUTED, false);
        hero.addView(deviceText);
        root.addView(hero);
        root.addView(space(19));

        root.addView(text("01  Find your scooter", 18, TEXT, true));
        root.addView(space(9));
        root.addView(text("Turn on Bluetooth and stand beside your own scooter. If it has no active Bluetooth radio, this scan may find nothing.", 13, MUTED, false));
        root.addView(space(10));
        scanButton = button("SCAN FOR BLUETOOTH", true);
        scanButton.setOnClickListener(v -> startScan());
        root.addView(scanButton);
        root.addView(space(7));
        chooseDeviceButton = button("CHOOSE FOUND DEVICE", false);
        chooseDeviceButton.setOnClickListener(v -> chooseDevice());
        root.addView(chooseDeviceButton);
        root.addView(space(18));

        root.addView(text("02  Inspect controller", 18, TEXT, true));
        root.addView(space(10));
        chooseCharacteristicButton = button("SELECT WRITE CHARACTERISTIC", false);
        chooseCharacteristicButton.setOnClickListener(v -> chooseCharacteristic());
        root.addView(chooseCharacteristicButton);
        root.addView(space(8));
        characteristicText = text("No writable GATT characteristic selected", 12, MUTED, false);
        root.addView(characteristicText);
        root.addView(space(10));
        detailsText = text("Connect to see GATT services and characteristics.", 12, MUTED, false);
        root.addView(detailsText);
        root.addView(space(9));
        Button copy = button("COPY DIAGNOSTIC REPORT", false);
        copy.setOnClickListener(v -> copyReport());
        root.addView(copy);
        root.addView(space(20));

        root.addView(text("03  Configure ON / OFF", 18, TEXT, true));
        root.addView(space(8));
        root.addView(text("Enter the HEX commands documented for your specific controller. We do not know Ryde's unlock protocol; random commands could damage equipment.", 13, MUTED, false));
        root.addView(space(12));
        root.addView(text("ON command (hex bytes)", 12, TEXT, true));
        onHex = hexInput("Example format: A1 02 FF (not a real command)");
        root.addView(onHex);
        root.addView(space(9));
        root.addView(text("OFF command (hex bytes)", 12, TEXT, true));
        offHex = hexInput("Enter documented OFF command");
        root.addView(offHex);
        root.addView(space(10));
        Button save = button("SAVE COMMAND PROFILE", false);
        save.setOnClickListener(v -> saveProfile());
        root.addView(save);
        root.addView(space(12));
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        onButton = button("POWER ON", true);
        offButton = button("POWER OFF", false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(55), 1);
        left.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(55), 1);
        right.setMargins(dp(6), 0, 0, 0);
        controls.addView(onButton, left);
        controls.addView(offButton, right);
        onButton.setOnClickListener(v -> confirmCommand(true));
        offButton.setOnClickListener(v -> confirmCommand(false));
        root.addView(controls);
        root.addView(space(16));

        LinearLayout statusPanel = panel();
        statusPanel.addView(text("ACTIVITY", 11, LIME, true));
        statusPanel.addView(space(6));
        statusText = text("", 13, TEXT, false);
        statusPanel.addView(statusText);
        root.addView(statusPanel);
        root.addView(space(18));
        root.addView(text("Safety: keep the scooter stationary with its drive wheel off the ground during any controller test. Never send commands while riding. OFF is not an emergency brake.", 12, MUTED, false));
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16), dp(16), dp(16), dp(16));
        p.setBackground(rounded(PANEL, 16, Color.rgb(48, 61, 50)));
        return p;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setLineSpacing(dp(3), 1.0f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private EditText hexInput(String hint) {
        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setTextSize(14);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setPadding(dp(13), dp(12), dp(13), dp(12));
        e.setBackground(rounded(PANEL, 9, Color.rgb(65, 78, 67)));
        return e;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : TEXT);
        b.setBackground(rounded(primary ? LIME : PANEL, 12, primary ? LIME : Color.rgb(64, 79, 65)));
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(52));
        return b;
    }

    private View space(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dp)));
        return v;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void status(String message) {
        if (statusText != null) statusText.setText(message);
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void startScan() {
        if (adapter == null) {
            status("This device does not support Bluetooth.");
            return;
        }
        if (!hasPermissions()) {
            if (Build.VERSION.SDK_INT >= 31) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLE);
            } else {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLE);
            }
            status("Allow Bluetooth permission, then tap SCAN again.");
            return;
        }
        if (!adapter.isEnabled()) {
            status("Enable Bluetooth on your phone, then tap SCAN again.");
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            return;
        }
        stopScan();
        devices.clear();
        deviceLabels.clear();
        chooseDeviceButton.setEnabled(false);
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            status("Bluetooth scanner unavailable. Try toggling Bluetooth.");
            return;
        }
        try {
            scanner.startScan(scanCallback);
            scanning = true;
            scanButton.setText("SCANNING (12 SECONDS)...");
            status("Scanning for Bluetooth Low Energy devices. Keep the phone close to the scooter.");
            handler.postDelayed(this::stopScan, 12000);
        } catch (SecurityException | IllegalStateException ex) {
            status("Could not scan: " + ex.getMessage());
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            runOnUiThread(() -> {
                BluetoothDevice device = result.getDevice();
                if (device == null) return;
                String address = device.getAddress();
                int index = -1;
                for (int i = 0; i < devices.size(); i++) {
                    if (devices.get(i).getAddress().equals(address)) { index = i; break; }
                }
                String name;
                try { name = device.getName(); }
                catch (SecurityException ex) { name = null; }
                if (name == null || name.trim().isEmpty()) name = "Unnamed BLE device";
                String label = name + "  •  " + address + "  •  " + result.getRssi() + " dBm";
                if (index < 0) {
                    devices.add(device);
                    deviceLabels.add(label);
                } else {
                    deviceLabels.set(index, label);
                }
                chooseDeviceButton.setEnabled(!devices.isEmpty());
                chooseDeviceButton.setText("CHOOSE DEVICE (" + devices.size() + " FOUND)");
            });
        }
        @Override public void onScanFailed(int errorCode) {
            runOnUiThread(() -> {
                stopScan();
                status("Bluetooth scan failed (code " + errorCode + "). Try again.");
            });
        }
    };

    private void stopScan() {
        if (!scanning) return;
        scanning = false;
        try { if (scanner != null && hasPermissions()) scanner.stopScan(scanCallback); }
        catch (SecurityException ex) { /* Permission may have been revoked during scan. */ }
        scanButton.setText("SCAN AGAIN");
        status(devices.size() + " BLE device(s) found. Select the scooter's likely device to inspect.");
    }

    private void chooseDevice() {
        if (devices.isEmpty()) {
            status("Scan first — no devices found yet.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a Bluetooth device")
                .setItems(deviceLabels.toArray(new String[0]), (dialog, which) -> connect(devices.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void connect(BluetoothDevice device) {
        if (!hasPermissions()) { status("Bluetooth permission was revoked. Scan again."); return; }
        stopScan();
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) { }
            gatt = null;
        }
        connected = false;
        writePending = false;
        selectedCharacteristic = null;
        selectedDevice = device;
        writableChars.clear();
        writeLabels.clear();
        diagnostic = "Waiting for GATT service discovery.";
        deviceText.setText(device.getAddress());
        connectionText.setText("●  CONNECTING...");
        connectionText.setTextColor(TEXT);
        refreshButtons();
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            status("Connecting to " + device.getAddress());
        } catch (SecurityException | IllegalStateException ex) {
            status("Connection failed: " + ex.getMessage());
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt incoming, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread(() -> {
                    if (incoming != gatt) return;
                    connected = true;
                    connectionText.setText("●  CONNECTED");
                    connectionText.setTextColor(LIME);
                    status("Connected. Discovering available services...");
                    refreshButtons();
                });
                try { incoming.discoverServices(); }
                catch (SecurityException ex) { runOnUiThread(() -> status("Service discovery denied.")); }
            } else {
                runOnUiThread(() -> {
                    if (incoming != gatt) return;
                    connected = false;
                    writePending = false;
                    selectedCharacteristic = null;
                    writableChars.clear();
                    writeLabels.clear();
                    connectionText.setText("●  DISCONNECTED");
                    connectionText.setTextColor(TEXT);
                    characteristicText.setText("No write characteristic selected");
                    status("Disconnected (GATT status " + status + ").");
                    refreshButtons();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt incoming, int status) {
            runOnUiThread(() -> {
                if (incoming != gatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    MainActivity.this.status("Could not discover services (" + status + ").");
                    return;
                }
                writableChars.clear();
                writeLabels.clear();
                StringBuilder report = new StringBuilder();
                for (BluetoothGattService service : incoming.getServices()) {
                    report.append("SERVICE ").append(service.getUuid()).append('\n');
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        int props = characteristic.getProperties();
                        report.append("  ").append(characteristic.getUuid())
                              .append("  [").append(propertiesName(props)).append("]\n");
                        if ((props & (BluetoothGattCharacteristic.PROPERTY_WRITE |
                                      BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                            writableChars.add(characteristic);
                            writeLabels.add(shortUuid(service.getUuid()) + " / " + shortUuid(characteristic.getUuid())
                                    + "  [" + propertiesName(props) + "]");
                        }
                    }
                }
                diagnostic = report.toString();
                detailsText.setText(incoming.getServices().size() + " service(s), " + writableChars.size()
                        + " writable characteristic(s). Tap COPY DIAGNOSTIC REPORT for all UUIDs.");
                String preferredService = getPreferences(0).getString("serviceUuid", "");
                String preferredChar = getPreferences(0).getString("characteristicUuid", "");
                selectedCharacteristic = null;
                for (BluetoothGattCharacteristic c : writableChars) {
                    if (c.getUuid().toString().equals(preferredChar)
                            && c.getService().getUuid().toString().equals(preferredService)) {
                        selectedCharacteristic = c;
                        break;
                    }
                }
                characteristicText.setText(selectedCharacteristic == null
                        ? "Select the authorized control characteristic (UUID)"
                        : "Selected: " + selectedCharacteristic.getUuid());
                MainActivity.this.status("GATT discovery complete. " + writableChars.size() + " writable characteristics found.");
                refreshButtons();
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt incoming, BluetoothGattCharacteristic characteristic, int status) {
            runOnUiThread(() -> {
                if (incoming != gatt) return;
                writePending = false;
                MainActivity.this.status(status == BluetoothGatt.GATT_SUCCESS
                        ? "BLE write acknowledged. Scooter state is NOT verified."
                        : "BLE write failed (GATT status " + status + ").");
                refreshButtons();
            });
        }
    };

    private String propertiesName(int props) {
        List<String> labels = new ArrayList<>();
        if ((props & BluetoothGattCharacteristic.PROPERTY_READ) != 0) labels.add("READ");
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) labels.add("WRITE");
        if ((props & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) labels.add("WRITE_NO_RESPONSE");
        if ((props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) labels.add("NOTIFY");
        if ((props & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) labels.add("INDICATE");
        return labels.isEmpty() ? "OTHER" : String.join(", ", labels);
    }

    private String shortUuid(UUID id) {
        String uuid = id.toString();
        return uuid.endsWith("-0000-1000-8000-00805f9b34fb") ? uuid.substring(4, 8) : uuid;
    }

    private void chooseCharacteristic() {
        if (!connected || writableChars.isEmpty()) {
            status("Connect to a BLE device with writable characteristics first.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a GATT write target")
                .setItems(writeLabels.toArray(new String[0]), (dialog, which) -> {
                    selectedCharacteristic = writableChars.get(which);
                    characteristicText.setText("Selected: " + selectedCharacteristic.getUuid());
                    getPreferences(0).edit()
                            .putString("serviceUuid", selectedCharacteristic.getService().getUuid().toString())
                            .putString("characteristicUuid", selectedCharacteristic.getUuid().toString()).apply();
                    status("Write characteristic selected; no command has been sent.");
                    refreshButtons();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyReport() {
        String name = selectedDevice == null ? "None" : selectedDevice.getAddress();
        String report = "RYDE CONTROLLER — BLUETOOTH DIAGNOSTICS\n"
                + "Device: " + name + "\nConnected: " + connected
                + "\n\n" + diagnostic;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("BLE report", report));
        Toast.makeText(this, "Report copied — paste it into ChatGPT", Toast.LENGTH_LONG).show();
    }

    private void saveProfile() {
        String on = onHex.getText().toString().trim();
        String off = offHex.getText().toString().trim();
        if (!on.isEmpty() && decodeHex(on) == null) { status("ON hex is invalid. Use complete byte pairs: A1 02 FF."); return; }
        if (!off.isEmpty() && decodeHex(off) == null) { status("OFF hex is invalid. Use complete byte pairs: A1 02 FF."); return; }
        getPreferences(0).edit().putString("onHex", on).putString("offHex", off).apply();
        status("Profile saved only on this phone. No command has been transmitted.");
        refreshButtons();
    }

    private byte[] decodeHex(String input) {
        String clean = input.trim().replaceAll("(?i)0x", "")
                .replaceAll("[\\s:,;\\-]", "");
        if (clean.length() == 0 || clean.length() % 2 != 0 || clean.length() > 400) return null;
        if (!clean.matches("[0-9a-fA-F]+")) return null;
        byte[] data = new byte[clean.length() / 2];
        for (int i = 0; i < clean.length(); i += 2) {
            data[i / 2] = (byte) Integer.parseInt(clean.substring(i, i + 2), 16);
        }
        return data;
    }

    private void refreshButtons() {
        if (chooseCharacteristicButton != null) chooseCharacteristicButton.setEnabled(connected && !writableChars.isEmpty());
        if (onButton != null) onButton.setEnabled(connected && selectedCharacteristic != null
                && !writePending && decodeHex(onHex.getText().toString()) != null);
        if (offButton != null) offButton.setEnabled(connected && selectedCharacteristic != null
                && !writePending && decodeHex(offHex.getText().toString()) != null);
    }

    private void confirmCommand(boolean turnOn) {
        if (!connected || gatt == null || selectedCharacteristic == null || writePending) {
            status("Not connected or no valid characteristic selected.");
            return;
        }
        byte[] data = decodeHex((turnOn ? onHex : offHex).getText().toString());
        if (data == null) { status("Please enter a valid hex command first."); return; }
        new AlertDialog.Builder(this)
                .setTitle(turnOn ? "Send ON command?" : "Send OFF command?")
                .setMessage("Send " + data.length + " byte(s) to " + selectedCharacteristic.getUuid()
                        + "? Only continue if this command is documented for your device. Keep the drive wheel clear.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, w) -> writeCommand(data))
                .show();
    }

    private void writeCommand(byte[] data) {
        if (gatt == null || !connected || selectedCharacteristic == null || !hasPermissions()) {
            status("Not connected or Bluetooth permission missing.");
            return;
        }
        int props = selectedCharacteristic.getProperties();
        int writeType = ((props & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0)
                ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        writePending = true;
        refreshButtons();
        try {
            boolean launched;
            if (Build.VERSION.SDK_INT >= 33) {
                launched = gatt.writeCharacteristic(selectedCharacteristic, data, writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS;
            } else {
                selectedCharacteristic.setWriteType(writeType);
                selectedCharacteristic.setValue(Arrays.copyOf(data, data.length));
                launched = gatt.writeCharacteristic(selectedCharacteristic);
            }
            if (launched) {
                status("Sending " + data.length + " byte(s). Waiting for GATT callback (not proof of activation).");
                handler.postDelayed(() -> {
                    if (writePending) {
                        writePending = false;
                        status("No BLE write callback received. Device state unknown.");
                        refreshButtons();
                    }
                }, 6000);
            } else {
                writePending = false;
                status("Android refused the GATT write.");
                refreshButtons();
            }
        } catch (SecurityException | IllegalStateException ex) {
            writePending = false;
            status("Write error: " + ex.getMessage());
            refreshButtons();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLE) {
            status(hasPermissions() ? "Permission granted — tap SCAN again."
                    : "Bluetooth permission denied. Enable it in Android Settings.");
        }
    }

    @Override
    protected void onDestroy() {
        stopScan();
        if (gatt != null) {
            try { if (hasPermissions()) gatt.disconnect(); gatt.close(); }
            catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
