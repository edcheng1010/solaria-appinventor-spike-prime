package io.github.appinventor.legospikeprime;

import android.util.Log;

import java.lang.reflect.Method;

import com.google.appinventor.components.runtime.Component;

/**
 * Utility class for interacting with BluetoothLE components.
 * 
 * This class provides methods to interact with BluetoothLE components
 * using reflection, allowing for proper connection listener management.
 */
public class BluetoothLEUtil {
    
    private static final String LOG_TAG = "BluetoothLEUtil";
    
    /**
     * Adds a connection listener to a BluetoothLE component.
     *
     * @param bluetoothLE the BluetoothLE component
     * @param listener the connection listener to add
     * @return true if successful, false otherwise
     */
    public static boolean addConnectionListener(Component bluetoothLE, BluetoothConnectionListener listener) {
        if (bluetoothLE == null || listener == null) {
            return false;
        }
        
        try {
            Method method = bluetoothLE.getClass().getMethod("addConnectionListener", Object.class);
            method.invoke(bluetoothLE, new BluetoothLEConnectionAdapter(listener));
            return true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error adding connection listener: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Removes a connection listener from a BluetoothLE component.
     *
     * @param bluetoothLE the BluetoothLE component
     * @param listener the connection listener to remove
     * @return true if successful, false otherwise
     */
    public static boolean removeConnectionListener(Component bluetoothLE, BluetoothConnectionListener listener) {
        if (bluetoothLE == null || listener == null) {
            return false;
        }
        
        try {
            Method method = bluetoothLE.getClass().getMethod("removeConnectionListener", Object.class);
            method.invoke(bluetoothLE, new BluetoothLEConnectionAdapter(listener));
            return true;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error removing connection listener: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Adapter class to bridge between our BluetoothConnectionListener and
     * the BluetoothLE.BluetoothConnectionListener interface.
     */
    private static class BluetoothLEConnectionAdapter {
        private final BluetoothConnectionListener listener;
        
        public BluetoothLEConnectionAdapter(BluetoothConnectionListener listener) {
            this.listener = listener;
        }
        
        // This method will be called by BluetoothLE when a device is connected
        public void onConnected(Object bleConnection) {
            try {
                // Extract device name and address using reflection
                Method getDeviceNameMethod = bleConnection.getClass().getMethod("DeviceName");
                Method getDeviceAddressMethod = bleConnection.getClass().getMethod("DeviceAddress");
                
                String deviceName = (String) getDeviceNameMethod.invoke(bleConnection);
                String deviceAddress = (String) getDeviceAddressMethod.invoke(bleConnection);
                
                // Notify our listener
                listener.onConnected(deviceName, deviceAddress);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error in onConnected adapter: " + e.getMessage(), e);
            }
        }
        
        // This method will be called by BluetoothLE when a device is disconnected
        public void onDisconnected(Object bleConnection) {
            listener.onDisconnected();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            BluetoothLEConnectionAdapter that = (BluetoothLEConnectionAdapter) obj;
            return listener.equals(that.listener);
        }
        
        @Override
        public int hashCode() {
            return listener.hashCode();
        }
    }
}
