package io.github.appinventor.legospikeprime;

/**
 * Interface for Bluetooth connection listeners.
 * 
 * This interface allows components to be notified of Bluetooth connection events.
 */
public interface BluetoothConnectionListener {
    
    /**
     * Called when a device is connected.
     *
     * @param deviceName the name of the connected device
     * @param deviceAddress the address of the connected device
     */
    void onConnected(String deviceName, String deviceAddress);
    
    /**
     * Called when a device is disconnected.
     */
    void onDisconnected();
}
