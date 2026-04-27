package io.github.appinventor.legospikeprime;

import android.util.Log;

/**
 * BluetoothInterfaceImpl class for handling Bluetooth communication with LEGO SPIKE Prime hubs
 * This implementation uses the App Inventor BluetoothLE component for communication
 */
public class BluetoothInterfaceImpl {

    private static final String LOG_TAG = "LegoSpikePrime";
    
    // Reference to the BluetoothLE component
    private Object bluetoothLE;
    
    // Reference to the LegoSpikePrime extension
    private LegoSpikePrime extension;
    
    // LEGO Wireless Protocol Service UUID
    private static final String LEGO_WIRELESS_SERVICE_UUID = "0000fd02-0000-1000-8000-00805f9b34fb";
    
    // LEGO Wireless Protocol Characteristic UUIDs
    private static final String LEGO_WIRELESS_RX_CHAR_UUID = "0000fd02-0001-1000-8000-00805f9b34fb"; // App → Hub
    private static final String LEGO_WIRELESS_TX_CHAR_UUID = "0000fd02-0002-1000-8000-00805f9b34fb"; // Hub → App
    
    // Nordic UART Service UUID (used by some LEGO hubs)
    private static final String NORDIC_UART_SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    
    // Nordic UART Characteristic UUIDs
    private static final String NORDIC_UART_RX_CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"; // App → Hub
    private static final String NORDIC_UART_TX_CHAR_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e"; // Hub → App
    
    /**
     * Constructor for the BluetoothInterfaceImpl class
     */
    public BluetoothInterfaceImpl() {
        logDebug("BluetoothInterfaceImpl initialized");
    }
    
    /**
     * Set the BluetoothLE component to use for communication
     * 
     * @param bluetoothLE the BluetoothLE component
     */
    public void setBluetoothLE(Object bluetoothLE) {
        this.bluetoothLE = bluetoothLE;
        // Note: TX notification subscription is handled by LegoSpikePrime.registerForTXNotifications()
        // after GATT connection is established. Do NOT register here.
    }

    /**
     * Set the LegoSpikePrime extension reference
     *
     * @param extension the LegoSpikePrime extension
     */
    public void setExtension(LegoSpikePrime extension) {
        this.extension = extension;
        logDebug("Extension reference set");
    }

    /**
     * Send a raw byte array to the hub's RX characteristic.
     * Used only by the legacy SetHubLEDColor / RunMotor placeholder methods.
     * The correct WriteBytes signature is (String, String, boolean, Object).
     *
     * @param message the message bytes to send
     * @return true if sent successfully
     */
    public boolean sendMessage(byte[] message) {
        if (bluetoothLE == null) {
            logDebug("sendMessage: BluetoothLE not set");
            return false;
        }
        try {
            // Convert byte[] to Object[] of unsigned ints for WriteBytes
            Object[] values = new Object[message.length];
            for (int i = 0; i < message.length; i++) values[i] = message[i] & 0xFF;
            java.lang.reflect.Method writeMethod =
                bluetoothLE.getClass().getMethod("WriteBytes",
                    String.class, String.class, boolean.class, Object.class);
            writeMethod.invoke(bluetoothLE,
                LEGO_WIRELESS_SERVICE_UUID, LEGO_WIRELESS_RX_CHAR_UUID,
                false,
                com.google.appinventor.components.runtime.util.YailList.makeList(values));
            return true;
        } catch (Exception e) {
            logDebug("sendMessage error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Callback for when bytes are received from the hub
     * 
     * @param bytes the bytes received
     */
    public void OnBytesReceived(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            logDebug("Received empty byte array");
            return;
        }
        
        logDebug("Received " + bytes.length + " bytes from hub");
        
        // Process the received bytes
        // This is where you would parse the LEGO Wireless Protocol messages
        // For now, we just log the bytes
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        logDebug("Bytes: " + sb.toString());
    }
    
    /**
     * Log debug messages
     * 
     * @param message the message to log
     */
    private void logDebug(String message) {
        Log.d(LOG_TAG, message);
    }
}

