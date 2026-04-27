package io.github.appinventor.legospikeprime;

/**
 * Utility class for mapping between SPIKE™ Prime port letters and port numbers
 * 
 * Enhanced with improved validation and error handling
 */
public class SpikePortMap {
    // Port letter to number mapping
    public static final char PORT_A = 'A';
    public static final char PORT_B = 'B';
    public static final char PORT_C = 'C';
    public static final char PORT_D = 'D';
    public static final char PORT_E = 'E';
    public static final char PORT_F = 'F';
    
    // Special port values
    public static final int PORT_LED = 50;    // 0x32
    public static final int PORT_SPEAKER = 1; // 0x01
    
    /**
     * Converts a port letter to a port number
     * 
     * @param portLetter the port letter (A-F)
     * @return the port number (0-5), or -1 if invalid
     */
    public static int portLetterToNumber(char portLetter) {
        switch (Character.toUpperCase(portLetter)) {
            case PORT_A: return 0;
            case PORT_B: return 1;
            case PORT_C: return 2;
            case PORT_D: return 3;
            case PORT_E: return 4;
            case PORT_F: return 5;
            default: return -1;
        }
    }
    
    /**
     * Converts a port number to a port letter
     * 
     * @param portNumber the port number (0-5)
     * @return the port letter (A-F), or '?' if invalid
     */
    public static char portNumberToLetter(int portNumber) {
        switch (portNumber) {
            case 0: return PORT_A;
            case 1: return PORT_B;
            case 2: return PORT_C;
            case 3: return PORT_D;
            case 4: return PORT_E;
            case 5: return PORT_F;
            default: return '?';
        }
    }
    
    /**
     * Validates if a port letter is valid
     * 
     * @param portLetter the port letter to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPortLetter(char portLetter) {
        char upperPort = Character.toUpperCase(portLetter);
        return upperPort >= PORT_A && upperPort <= PORT_F;
    }
    
    /**
     * Validates if a port number is valid
     * 
     * @param portNumber the port number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidPortNumber(int portNumber) {
        return portNumber >= 0 && portNumber <= 5;
    }
    
    /**
     * Gets a human-readable port name from a port number
     * 
     * @param portNumber the port number
     * @return the port name (e.g., "Port A", "LED", "Speaker", "Unknown")
     */
    public static String getPortName(int portNumber) {
        if (isValidPortNumber(portNumber)) {
            return "Port " + portNumberToLetter(portNumber);
        } else if (portNumber == PORT_LED) {
            return "LED";
        } else if (portNumber == PORT_SPEAKER) {
            return "Speaker";
        } else {
            return "Unknown Port " + portNumber;
        }
    }
}
