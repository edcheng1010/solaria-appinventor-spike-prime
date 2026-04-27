package io.github.appinventor.legospikeprime;

import android.util.Log;

import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.PropertyCategory;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.Form;

/**
 * Base class for Bluetooth Low Energy extensions.
 * 
 * This class provides common functionality for BLE extensions,
 * including BluetoothLE component management and connection state.
 */
@SimpleObject
public class BLEExtension extends AndroidNonvisibleComponent implements BluetoothConnectionListener {
    
    private static final String LOG_TAG = "BLEExtension";
    
    // The BluetoothLE component
    private Component bluetoothLE;
    
    // Connection state
    private boolean isConnected = false;
    
    // Debug mode
    private boolean debugMode = false;
    
    /**
     * Creates a new BLEExtension.
     *
     * @param form the container that this component will be placed in
     */
    protected BLEExtension(Form form) {
        super(form);
    }
    
    /**
     * Sets the BluetoothLE component to use for communication.
     * This property is restricted to only accept BluetoothLE components.
     *
     * @param bluetoothLE the BluetoothLE component
     */
    @DesignerProperty(editorType = PropertyTypeConstants.PROPERTY_TYPE_COMPONENT + 
                     ":edu.mit.appinventor.ble.BluetoothLE")
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "The BluetoothLE component used for communication with BLE devices")
    public void BluetoothDevice(Component bluetoothLE) {
        if (this.bluetoothLE != null) {
            // Remove this as a listener from the old BluetoothLE component
            try {
                BluetoothLEUtil.removeConnectionListener(this.bluetoothLE, this);
            } catch (Exception e) {
                logError("Error removing connection listener: " + e.getMessage());
            }
        }
        
        this.bluetoothLE = bluetoothLE;
        
        if (this.bluetoothLE != null) {
            // Add this as a listener to the new BluetoothLE component
            try {
                BluetoothLEUtil.addConnectionListener(this.bluetoothLE, this);
            } catch (Exception e) {
                logError("Error adding connection listener: " + e.getMessage());
            }
        }
        
        logDebug("BluetoothDevice set: " + (bluetoothLE != null));
    }
    
    /**
     * Gets the BluetoothLE component being used.
     *
     * @return the BluetoothLE component
     */
    @SimpleProperty(category = PropertyCategory.BEHAVIOR,
                   description = "The BluetoothLE component used for communication with BLE devices")
    public Component BluetoothDevice() {
        return bluetoothLE;
    }
    
    /**
     * Sets whether debug mode is enabled.
     *
     * @param debugMode true to enable debug mode, false otherwise
     */
    protected void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /**
     * Gets whether debug mode is enabled.
     *
     * @return true if debug mode is enabled, false otherwise
     */
    protected boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Gets whether the extension is connected to a device.
     *
     * @return true if connected, false otherwise
     */
    protected boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Sets the connection state.
     *
     * @param connected true if connected, false otherwise
     */
    protected void setConnected(boolean connected) {
        this.isConnected = connected;
    }
    
    /**
     * Called when a device is connected.
     *
     * @param deviceName the name of the connected device
     * @param deviceAddress the address of the connected device
     */
    @Override
    public void onConnected(String deviceName, String deviceAddress) {
        setConnected(true);
        logDebug("Connected to device: " + deviceName + " (" + deviceAddress + ")");
    }
    
    /**
     * Called when a device is disconnected.
     */
    @Override
    public void onDisconnected() {
        setConnected(false);
        logDebug("Disconnected from device");
    }
    
    /**
     * Logs a debug message.
     *
     * @param message the message to log
     */
    protected void logDebug(String message) {
        if (debugMode) {
            Log.d(LOG_TAG, message);
        }
    }
    
    /**
     * Logs an error message.
     *
     * @param message the message to log
     */
    protected void logError(String message) {
        Log.e(LOG_TAG, message);
    }
}
