package io.github.appinventor.legospikeprime;

/**
 * Protocol implementation for LEGO SPIKE Prime hub communication
 * 
 * Enhanced with improved error handling and validation
 */
public class SpikeProtocol {
    // Message types
    public static final byte HUB_PROPERTIES = 0x01;
    public static final byte HUB_ACTIONS = 0x02;
    public static final byte HUB_ALERTS = 0x03;
    public static final byte HUB_ATTACHED_IO = 0x04;
    public static final byte GENERIC_ERROR_MESSAGES = 0x05;
    public static final byte PORT_INFORMATION = 0x06;
    public static final byte PORT_MODE_INFORMATION = 0x07;
    public static final byte PORT_VALUE = 0x08;
    public static final byte PORT_INPUT_FORMAT_SETUP = 0x09;
    public static final byte PORT_OUTPUT_COMMAND = 0x0A;
    public static final byte PORT_OUTPUT_COMMAND_FEEDBACK = 0x0B;
    public static final byte PORT_INFORMATION_REQUEST = 0x0C;
    
    // Aliases for compatibility with different naming conventions
    public static final byte HUB_INFO_RESPONSE = HUB_PROPERTIES;
    public static final byte PORT_OUTPUT_FEEDBACK = PORT_OUTPUT_COMMAND_FEEDBACK;
    public static final byte DEVICE_NOTIFICATION = HUB_ATTACHED_IO;
    public static final byte INFO_RESPONSE = HUB_PROPERTIES;
    
    // Hub property operations
    public static final byte HUB_PROPERTY_OPERATION_SET = 0x01;
    public static final byte HUB_PROPERTY_OPERATION_ENABLE_UPDATES = 0x02;
    public static final byte HUB_PROPERTY_OPERATION_DISABLE_UPDATES = 0x03;
    public static final byte HUB_PROPERTY_OPERATION_RESET = 0x04;
    public static final byte HUB_PROPERTY_OPERATION_REQUEST_UPDATE = 0x05;
    public static final byte HUB_PROPERTY_OPERATION_RESPONSE = 0x06;
    
    // Hub properties
    public static final byte HUB_PROPERTY_NAME = 0x01;
    public static final byte HUB_PROPERTY_BUTTON = 0x02;
    public static final byte HUB_PROPERTY_FW_VERSION = 0x03;
    public static final byte HUB_PROPERTY_HW_VERSION = 0x04;
    public static final byte HUB_PROPERTY_RSSI = 0x05;
    public static final byte HUB_PROPERTY_BATTERY_VOLTAGE = 0x06;
    public static final byte HUB_PROPERTY_BATTERY_TYPE = 0x07;
    public static final byte HUB_PROPERTY_MANUFACTURER_NAME = 0x08;
    public static final byte HUB_PROPERTY_RADIO_FIRMWARE_VERSION = 0x09;
    public static final byte HUB_PROPERTY_WIRELESS_PROTOCOL_VERSION = 0x0A;
    public static final byte HUB_PROPERTY_SYSTEM_TYPE_ID = 0x0B;
    public static final byte HUB_PROPERTY_HW_NETWORK_ID = 0x0C;
    public static final byte HUB_PROPERTY_PRIMARY_MAC_ADDRESS = 0x0D;
    public static final byte HUB_PROPERTY_SECONDARY_MAC_ADDRESS = 0x0E;
    public static final byte HUB_PROPERTY_HARDWARE_NETWORK_FAMILY = 0x0F;
    
    // Hub actions
    public static final byte HUB_ACTION_SWITCH_OFF = 0x01;
    public static final byte HUB_ACTION_DISCONNECT = 0x02;
    public static final byte HUB_ACTION_VCC_PORT_CONTROL_ON = 0x03;
    public static final byte HUB_ACTION_VCC_PORT_CONTROL_OFF = 0x04;
    public static final byte HUB_ACTION_ACTIVATE_BUSY_INDICATION = 0x05;
    public static final byte HUB_ACTION_RESET_BUSY_INDICATION = 0x06;
    
    // Port output subcommands
    public static final byte PORT_OUTPUT_SUBCOMMAND_START_POWER = 0x01;
    public static final byte PORT_OUTPUT_SUBCOMMAND_START_SPEED = 0x02;
    public static final byte PORT_OUTPUT_SUBCOMMAND_START_SPEED_FOR_TIME = 0x03;
    public static final byte PORT_OUTPUT_SUBCOMMAND_START_SPEED_FOR_DEGREES = 0x04;
    public static final byte PORT_OUTPUT_SUBCOMMAND_GO_TO_ABSOLUTE_POSITION = 0x05;
    public static final byte PORT_OUTPUT_SUBCOMMAND_STOP = 0x06;
    
    // Port output startup and completion information
    public static final byte PORT_OUTPUT_STARTUP_EXECUTE_IMMEDIATELY = 0x00;
    public static final byte PORT_OUTPUT_STARTUP_BUFFER_IF_NEEDED = 0x10;
    public static final byte PORT_OUTPUT_COMPLETION_NO_ACTION = 0x00;
    public static final byte PORT_OUTPUT_COMPLETION_FEEDBACK = 0x01;
    
    // Motor end state (from protocol documentation)
    public static final byte MOTOR_END_STATE_COAST = 0x00;
    public static final byte MOTOR_END_STATE_BRAKE = 0x01;
    public static final byte MOTOR_END_STATE_HOLD = 0x02;
    public static final byte MOTOR_END_STATE_CONTINUE = 0x03;
    public static final byte MOTOR_END_STATE_COAST_SMART = 0x04;
    public static final byte MOTOR_END_STATE_BRAKE_SMART = 0x05;
    public static final byte MOTOR_END_STATE_DEFAULT = (byte)0xFF;
    
    // Motor move direction (from protocol documentation)
    public static final byte MOTOR_DIRECTION_CLOCKWISE = 0x00;
    public static final byte MOTOR_DIRECTION_COUNTER_CLOCKWISE = 0x01;
    public static final byte MOTOR_DIRECTION_SHORTEST_PATH = 0x02;
    public static final byte MOTOR_DIRECTION_LONGEST_PATH = 0x03;
    
    // Color values (from protocol documentation)
    public static final byte COLOR_BLACK = 0x00;
    public static final byte COLOR_MAGENTA = 0x01;
    public static final byte COLOR_PURPLE = 0x02;
    public static final byte COLOR_BLUE = 0x03;
    public static final byte COLOR_AZURE = 0x04;
    public static final byte COLOR_TURQUOISE = 0x05;
    public static final byte COLOR_GREEN = 0x06;
    public static final byte COLOR_YELLOW = 0x07;
    public static final byte COLOR_ORANGE = 0x08;
    public static final byte COLOR_RED = 0x09;
    public static final byte COLOR_WHITE = 0x0A;
    public static final byte COLOR_UNKNOWN = (byte)0xFF;
    
    // Device types
    public static final byte DEVICE_TYPE_MOTOR = 0x0001;
    public static final byte DEVICE_TYPE_SYSTEM_TRAIN_MOTOR = 0x0002;
    public static final byte DEVICE_TYPE_BUTTON = 0x0005;
    public static final byte DEVICE_TYPE_LED_LIGHT = 0x0008;
    public static final byte DEVICE_TYPE_VOLTAGE = 0x0014;
    public static final byte DEVICE_TYPE_CURRENT = 0x0015;
    public static final byte DEVICE_TYPE_PIEZO_TONE = 0x0016;
    public static final byte DEVICE_TYPE_RGB_LIGHT = 0x0017;
    public static final byte DEVICE_TYPE_TILT_SENSOR = 0x0022;
    public static final byte DEVICE_TYPE_MOTION_SENSOR = 0x0023;
    public static final byte DEVICE_TYPE_COLOR_DISTANCE_SENSOR = 0x0025;
    public static final byte DEVICE_TYPE_MEDIUM_LINEAR_MOTOR = 0x0026;
    public static final byte DEVICE_TYPE_MOVE_HUB_MEDIUM_LINEAR_MOTOR = 0x0027;
    public static final byte DEVICE_TYPE_MOVE_HUB_TILT_SENSOR = 0x0028;
    public static final byte DEVICE_TYPE_DUPLO_TRAIN_BASE_MOTOR = 0x0029;
    public static final byte DEVICE_TYPE_DUPLO_TRAIN_BASE_SPEAKER = 0x002A;
    public static final byte DEVICE_TYPE_DUPLO_TRAIN_BASE_COLOR_SENSOR = 0x002B;
    public static final byte DEVICE_TYPE_DUPLO_TRAIN_BASE_SPEEDOMETER = 0x002C;
    public static final byte DEVICE_TYPE_TECHNIC_LARGE_LINEAR_MOTOR = 0x002E;
    public static final byte DEVICE_TYPE_TECHNIC_XLARGE_LINEAR_MOTOR = 0x002F;
    public static final byte DEVICE_TYPE_TECHNIC_MEDIUM_ANGULAR_MOTOR = 0x0030;
    public static final byte DEVICE_TYPE_TECHNIC_LARGE_ANGULAR_MOTOR = 0x0031;
    public static final byte DEVICE_TYPE_TECHNIC_COLOR_SENSOR = 0x0036;
    public static final byte DEVICE_TYPE_TECHNIC_DISTANCE_SENSOR = 0x0037;
    public static final byte DEVICE_TYPE_TECHNIC_FORCE_SENSOR = 0x0038;
    public static final byte DEVICE_TYPE_TECHNIC_3X3_COLOR_LIGHT_MATRIX = 0x0039;
    public static final byte DEVICE_TYPE_TECHNIC_SMALL_ANGULAR_MOTOR = 0x003A;
    public static final byte DEVICE_TYPE_MARIO_ACCELEROMETER = 0x003B;
    public static final byte DEVICE_TYPE_MARIO_BARCODE_SENSOR = 0x003C;
    public static final byte DEVICE_TYPE_MARIO_PANTS_SENSOR = 0x003D;
    
    /**
     * Creates a command to run a motor at a specified power level
     * 
     * @param portLetter the port letter (A-F)
     * @param power the power level (-100 to 100)
     * @return the command bytes
     * @throws IllegalArgumentException if the port letter is invalid
     */
    public static byte[] createMotorPowerCommand(char portLetter, int power) {
        int portNumber = SpikePortMap.portLetterToNumber(portLetter);
        if (portNumber < 0) {
            throw new IllegalArgumentException("Invalid port letter: " + portLetter);
        }
        
        // Validate power range
        if (power < -100 || power > 100) {
            throw new IllegalArgumentException("Power must be between -100 and 100");
        }
        
        byte[] command = new byte[8];
        command[0] = 0x08; // Length
        command[1] = 0x00; // Hub ID (0 = default)
        command[2] = PORT_OUTPUT_COMMAND; // Message type
        command[3] = (byte) portNumber; // Port number
        command[4] = 0x00; // Startup/completion information
        command[5] = PORT_OUTPUT_SUBCOMMAND_START_POWER; // Subcommand
        command[6] = (byte) power; // Power
        command[7] = 0x00; // End of command
        return command;
    }
    
    /**
     * Creates a command to run a motor for a specific time duration
     * 
     * @param portLetter the port letter (A-F)
     * @param power the power level (-100 to 100)
     * @param milliseconds the duration in milliseconds
     * @return the command bytes
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static byte[] createMotorTimeCommand(char portLetter, int power, int milliseconds) {
        int portNumber = SpikePortMap.portLetterToNumber(portLetter);
        if (portNumber < 0) {
            throw new IllegalArgumentException("Invalid port letter: " + portLetter);
        }
        
        // Validate power range
        if (power < -100 || power > 100) {
            throw new IllegalArgumentException("Power must be between -100 and 100");
        }
        
        // Validate duration
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0");
        }
        
        byte[] command = new byte[12];
        command[0] = 0x0C;  // Length
        command[1] = 0x00;  // Hub ID
        command[2] = PORT_OUTPUT_COMMAND;
        command[3] = (byte) (portNumber & 0xFF);
        command[4] = PORT_OUTPUT_STARTUP_EXECUTE_IMMEDIATELY;
        command[5] = PORT_OUTPUT_COMPLETION_FEEDBACK;
        command[6] = PORT_OUTPUT_SUBCOMMAND_START_SPEED_FOR_TIME;
        command[7] = (byte) (milliseconds & 0xFF);
        command[8] = (byte) ((milliseconds >> 8) & 0xFF);
        command[9] = (byte) ((milliseconds >> 16) & 0xFF);
        command[10] = (byte) ((milliseconds >> 24) & 0xFF);
        command[11] = (byte) (power & 0xFF);
        return command;
    }
    
    /**
     * Creates a command to run a motor for a specific number of degrees
     * 
     * @param portLetter the port letter (A-F)
     * @param power the power level (-100 to 100)
     * @param degrees the number of degrees to rotate
     * @return the command bytes
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static byte[] createMotorDegreesCommand(char portLetter, int power, int degrees) {
        int portNumber = SpikePortMap.portLetterToNumber(portLetter);
        if (portNumber < 0) {
            throw new IllegalArgumentException("Invalid port letter: " + portLetter);
        }
        
        // Validate power range
        if (power < -100 || power > 100) {
            throw new IllegalArgumentException("Power must be between -100 and 100");
        }
        
        // Validate degrees
        if (degrees == 0) {
            throw new IllegalArgumentException("Degrees cannot be 0");
        }
        
        byte[] command = new byte[12];
        command[0] = 0x0C;  // Length
        command[1] = 0x00;  // Hub ID
        command[2] = PORT_OUTPUT_COMMAND;
        command[3] = (byte) (portNumber & 0xFF);
        command[4] = PORT_OUTPUT_STARTUP_EXECUTE_IMMEDIATELY;
        command[5] = PORT_OUTPUT_COMPLETION_FEEDBACK;
        command[6] = PORT_OUTPUT_SUBCOMMAND_START_SPEED_FOR_DEGREES;
        command[7] = (byte) (degrees & 0xFF);
        command[8] = (byte) ((degrees >> 8) & 0xFF);
        command[9] = (byte) ((degrees >> 16) & 0xFF);
        command[10] = (byte) ((degrees >> 24) & 0xFF);
        command[11] = (byte) (power & 0xFF);
        return command;
    }
    
    /**
     * Creates a command to set the hub's LED color
     * 
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return the command bytes
     * @throws IllegalArgumentException if color components are invalid
     */
    public static byte[] createSetLEDCommand(int r, int g, int b) {
        // Validate color components
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException("Color components must be between 0 and 255");
        }
        
        byte[] command = new byte[10];
        command[0] = 0x0A; // Length
        command[1] = 0x00; // Hub ID (0 = default)
        command[2] = PORT_OUTPUT_COMMAND; // Message type
        command[3] = 0x32; // Port number for LED (50)
        command[4] = 0x00; // Startup/completion information
        command[5] = 0x03; // Subcommand for RGB LED
        command[6] = (byte) r; // Red
        command[7] = (byte) g; // Green
        command[8] = (byte) b; // Blue
        command[9] = 0x00; // End of command
        return command;
    }
    
    /**
     * Creates a command to play a tone on the hub's speaker
     * 
     * @param frequency the frequency in Hz
     * @param duration the duration in milliseconds
     * @return the command bytes
     * @throws IllegalArgumentException if parameters are invalid
     */
    public static byte[] createPlayToneCommand(int frequency, int duration) {
        // Validate frequency and duration
        if (frequency <= 0) {
            throw new IllegalArgumentException("Frequency must be greater than 0");
        }
        
        if (duration <= 0) {
            throw new IllegalArgumentException("Duration must be greater than 0");
        }
        
        byte[] command = new byte[12];
        command[0] = 0x0C; // Length
        command[1] = 0x00; // Hub ID (0 = default)
        command[2] = PORT_OUTPUT_COMMAND; // Message type
        command[3] = 0x01; // Port number for speaker
        command[4] = 0x00; // Startup/completion information
        command[5] = 0x03; // Subcommand for tone
        command[6] = (byte) (frequency & 0xFF); // Frequency (LSB)
        command[7] = (byte) ((frequency >> 8) & 0xFF); // Frequency (MSB)
        command[8] = (byte) (duration & 0xFF); // Duration (LSB)
        command[9] = (byte) ((duration >> 8) & 0xFF); // Duration (MSB)
        command[10] = 0x01; // Volume (0-10)
        command[11] = 0x00; // End of command
        return command;
    }
    
    /**
     * Creates a command to request hub information
     * 
     * @return the command bytes
     */
    public static byte[] createHubInfoCommand() {
        byte[] command = new byte[6];
        command[0] = 0x06; // Length
        command[1] = 0x00; // Hub ID (0 = default)
        command[2] = HUB_PROPERTIES; // Message type
        command[3] = HUB_PROPERTY_OPERATION_REQUEST_UPDATE; // Operation
        command[4] = HUB_PROPERTY_FW_VERSION; // Property
        command[5] = 0x00; // End of command
        return command;
    }
    
    /**
     * Creates a command to request the hub's battery level
     * 
     * @return the command bytes
     */
    public static byte[] createBatteryLevelCommand() {
        byte[] command = new byte[6];
        command[0] = 0x06; // Length
        command[1] = 0x00; // Hub ID (0 = default)
        command[2] = HUB_PROPERTIES; // Message type
        command[3] = HUB_PROPERTY_OPERATION_REQUEST_UPDATE; // Operation
        command[4] = HUB_PROPERTY_BATTERY_VOLTAGE; // Property
        command[5] = 0x00; // End of command
        return command;
    }
    
    /**
     * Class to hold hub information
     */
    public static class HubInfo {
        private int hubType;
        private String hubName;
        private String firmwareVersion;
        private int batteryLevel;
        
        public HubInfo(int hubType, String hubName, String firmwareVersion, int batteryLevel) {
            this.hubType = hubType;
            this.hubName = hubName;
            this.firmwareVersion = firmwareVersion;
            this.batteryLevel = batteryLevel;
        }
        
        public int getHubType() {
            return hubType;
        }
        
        public String getHubName() {
            return hubName;
        }
        
        public String getFirmwareVersion() {
            return firmwareVersion;
        }
        
        public int getBatteryLevel() {
            return batteryLevel;
        }
    }
    
    /**
     * Parses a hub info response message
     * 
     * @param data the message data
     * @return the hub info, or null if the message is invalid
     */
    public static HubInfo parseHubInfoResponse(byte[] data) {
        if (data == null || data.length < 10) {
            return null;
        }
        
        if (data[2] != HUB_PROPERTIES || data[3] != HUB_PROPERTY_OPERATION_RESPONSE || data[4] != HUB_PROPERTY_FW_VERSION) {
            return null;
        }
        
        int hubType = data[5] & 0xFF;
        
        // Parse firmware version
        StringBuilder firmwareVersion = new StringBuilder();
        firmwareVersion.append(data[6] & 0xFF).append(".");
        firmwareVersion.append(data[7] & 0xFF).append(".");
        firmwareVersion.append(data[8] & 0xFF).append(".");
        firmwareVersion.append(data[9] & 0xFF);
        
        // Default values for name and battery
        String hubName = "LEGO Prime Hub";
        int batteryLevel = 100;
        
        return new HubInfo(hubType, hubName, firmwareVersion.toString(), batteryLevel);
    }
}
