package io.github.appinventor.legospikeprime;

/**
 * Message handler for LEGO SPIKE Prime hub communication
 * Updated to comply with SPIKE™ Prime protocol 1.0
 * 
 * Enhanced with improved error handling and debug logging
 */
public class MessageHandler {
    private static final String LOG_TAG = "MessageHandler";
    
    private Callback callback;
    private boolean debugMode = false;
    
    /**
     * Callback interface for message handling events
     */
    public interface Callback {
        /**
         * Called when hub information is received
         * 
         * @param hubType the type of the hub
         * @param hubName the name of the hub
         * @param firmwareVersion the firmware version of the hub
         * @param batteryLevel the battery level of the hub (0-100)
         */
        void onHubInfo(int hubType, String hubName, String firmwareVersion, int batteryLevel);
        
        /**
         * Called when a port value changes
         * 
         * @param portNumber the port number
         * @param portValue the new port value
         */
        void onPortValue(int portNumber, int portValue);
        
        /**
         * Called when the battery level changes
         * 
         * @param batteryLevel the new battery level (0-100)
         */
        void onBatteryLevel(int batteryLevel);
        
        /**
         * Called when an error occurs
         * 
         * @param message the error message
         */
        void onError(String message);
    }
    
    /**
     * Constructor for the message handler
     * 
     * @param callback the callback to receive message events
     */
    public MessageHandler(Callback callback) {
        this.callback = callback;
    }
    
    /**
     * Sets debug mode
     * 
     * @param debugMode true to enable debug mode, false otherwise
     */
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }
    
    /**
     * Handles a message received from the hub
     * 
     * @param data the message data
     */
    public void handleMessage(byte[] data) {
        if (data == null || data.length < 3) {
            logDebug("Received invalid message (too short or null)");
            return;
        }
        
        // Decode the message using COBS if needed
        byte[] decodedData = COBSEncoder.decode(data);
        if (decodedData.length < 3) {
            logDebug("Decoded message is too short");
            return;
        }
        
        byte messageType = decodedData[2];
        logDebug("Handling message type: 0x" + String.format("%02X", messageType));
        
        try {
            switch (messageType) {
                case SpikeProtocol.HUB_PROPERTIES:
                    handleHubPropertiesMessage(decodedData);
                    break;
                    
                case SpikeProtocol.HUB_ATTACHED_IO:
                    handleHubAttachedIOMessage(decodedData);
                    break;
                    
                case SpikeProtocol.GENERIC_ERROR_MESSAGES:
                    handleErrorMessage(decodedData);
                    break;
                    
                case SpikeProtocol.PORT_VALUE:
                    handlePortValueMessage(decodedData);
                    break;
                    
                case SpikeProtocol.PORT_OUTPUT_COMMAND_FEEDBACK:
                    handlePortOutputFeedbackMessage(decodedData);
                    break;
                    
                default:
                    logDebug("Unhandled message type: 0x" + String.format("%02X", messageType));
                    break;
            }
        } catch (Exception e) {
            logError("Error processing message: " + e.getMessage());
            if (callback != null) {
                callback.onError("Error processing message: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handles a hub properties message
     * 
     * @param data the message data
     */
    private void handleHubPropertiesMessage(byte[] data) {
        if (data.length < 5) {
            logDebug("Hub properties message too short");
            return;
        }
        
        byte operation = data[3];
        
        if (operation == SpikeProtocol.HUB_PROPERTY_OPERATION_RESPONSE) {
            byte propertyType = data[4];
            
            if (propertyType == SpikeProtocol.HUB_PROPERTY_FW_VERSION) {
                // Hub info response
                SpikeProtocol.HubInfo hubInfo = SpikeProtocol.parseHubInfoResponse(data);
                if (hubInfo != null && callback != null) {
                    logDebug("Received hub info: " + hubInfo.getHubName() + ", FW: " + hubInfo.getFirmwareVersion());
                    callback.onHubInfo(hubInfo.getHubType(), hubInfo.getHubName(), 
                                      hubInfo.getFirmwareVersion(), hubInfo.getBatteryLevel());
                }
            } else if (propertyType == SpikeProtocol.HUB_PROPERTY_BATTERY_VOLTAGE) {
                // Battery level response
                if (data.length >= 6 && callback != null) {
                    int batteryLevel = data[5] & 0xFF;
                    logDebug("Received battery level: " + batteryLevel + "%");
                    callback.onBatteryLevel(batteryLevel);
                }
            } else {
                logDebug("Unhandled property type: 0x" + String.format("%02X", propertyType));
            }
        } else {
            logDebug("Unhandled operation: 0x" + String.format("%02X", operation));
        }
    }
    
    /**
     * Handles a hub attached IO message
     * 
     * @param data the message data
     */
    private void handleHubAttachedIOMessage(byte[] data) {
        if (data.length < 6 || callback == null) {
            logDebug("Hub attached IO message too short or no callback");
            return;
        }
        
        int portNumber = data[3] & 0xFF;
        byte event = data[4];
        
        // Device attached or detached event
        if (event == 0x00 || event == 0x01) {
            int deviceType = 0;
            if (data.length >= 8) {
                deviceType = ((data[6] & 0xFF) << 8) | (data[5] & 0xFF);
            }
            
            String eventType = (event == 0x00) ? "detached" : "attached";
            logDebug("Device " + eventType + " on port " + portNumber + ", type: 0x" + String.format("%04X", deviceType));
            
            // Report as port value change
            callback.onPortValue(portNumber, deviceType);
        } else {
            logDebug("Unhandled IO event: 0x" + String.format("%02X", event));
        }
    }
    
    /**
     * Handles an error message
     * 
     * @param data the message data
     */
    private void handleErrorMessage(byte[] data) {
        if (data.length < 5 || callback == null) {
            logDebug("Error message too short or no callback");
            return;
        }
        
        byte errorCode = data[3];
        String errorMessage = "Hub error code: 0x" + String.format("%02X", errorCode);
        
        logError(errorMessage);
        callback.onError(errorMessage);
    }
    
    /**
     * Handles a port value message
     * 
     * @param data the message data
     */
    private void handlePortValueMessage(byte[] data) {
        if (data.length < 6 || callback == null) {
            logDebug("Port value message too short or no callback");
            return;
        }
        
        int portNumber = data[3] & 0xFF;
        int portValue = data[5] & 0xFF;
        
        logDebug("Port " + portNumber + " value changed: " + portValue);
        callback.onPortValue(portNumber, portValue);
    }
    
    /**
     * Handles a port output feedback message
     * 
     * @param data the message data
     */
    private void handlePortOutputFeedbackMessage(byte[] data) {
        if (data.length < 6 || callback == null) {
            logDebug("Port output feedback message too short or no callback");
            return;
        }
        
        int portNumber = data[3] & 0xFF;
        byte feedback = data[5];
        
        if (feedback != 0x01) {
            // Non-success feedback
            String errorMessage = "Command failed on port " + portNumber + " with feedback 0x" + String.format("%02X", feedback);
            logError(errorMessage);
            callback.onError(errorMessage);
        } else {
            logDebug("Command succeeded on port " + portNumber);
        }
    }
    
    /**
     * Logs a debug message
     * 
     * @param message the message to log
     */
    private void logDebug(String message) {
        if (debugMode) {
            android.util.Log.d(LOG_TAG, message);
        }
    }
    
    /**
     * Logs an error message
     * 
     * @param message the error message
     */
    private void logError(String message) {
        android.util.Log.e(LOG_TAG, message);
    }
}
