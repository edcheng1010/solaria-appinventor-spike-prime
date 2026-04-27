package io.github.appinventor.legospikeprime;

/**
 * String handling utility for LEGO SPIKE Prime protocol
 * 
 * Implements string handling according to the SPIKE™ Prime Protocol 1.0 specification,
 * which requires null-terminated strings with specific length constraints.
 */
public class SpikeStringUtil {
    
    /**
     * Convert a Java string to a null-terminated byte array
     * 
     * @param str The string to convert
     * @return The null-terminated byte array
     */
    public static byte[] toNullTerminatedString(String str) {
        if (str == null) {
            return new byte[]{0};
        }
        
        byte[] bytes = str.getBytes();
        byte[] result = new byte[bytes.length + 1];
        System.arraycopy(bytes, 0, result, 0, bytes.length);
        result[bytes.length] = 0; // Null terminator
        return result;
    }
    
    /**
     * Convert a Java string to a null-terminated byte array with maximum length constraint
     * 
     * @param str The string to convert
     * @param maxLength The maximum length including null terminator
     * @return The null-terminated byte array
     */
    public static byte[] toNullTerminatedString(String str, int maxLength) {
        if (str == null) {
            return new byte[]{0};
        }
        
        byte[] bytes = str.getBytes();
        int effectiveLength = Math.min(bytes.length, maxLength - 1);
        byte[] result = new byte[effectiveLength + 1];
        System.arraycopy(bytes, 0, result, 0, effectiveLength);
        result[effectiveLength] = 0; // Null terminator
        return result;
    }
    
    /**
     * Convert a null-terminated byte array to a Java string
     * 
     * @param bytes The null-terminated byte array
     * @return The Java string
     */
    public static String fromNullTerminatedString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        
        // Find the null terminator
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        
        return new String(bytes, 0, length);
    }
    
    /**
     * Convert a null-terminated byte array to a Java string, starting at an offset
     * 
     * @param bytes The byte array containing a null-terminated string
     * @param offset The starting offset in the byte array
     * @return The Java string
     */
    public static String fromNullTerminatedString(byte[] bytes, int offset) {
        if (bytes == null || offset >= bytes.length) {
            return "";
        }
        
        // Find the null terminator
        int length = 0;
        while ((offset + length) < bytes.length && bytes[offset + length] != 0) {
            length++;
        }
        
        return new String(bytes, offset, length);
    }
    
    /**
     * Check if a string fits within the maximum length constraint (including null terminator)
     * 
     * @param str The string to check
     * @param maxLength The maximum length including null terminator
     * @return true if the string fits, false otherwise
     */
    public static boolean fitsInLength(String str, int maxLength) {
        return str == null || str.getBytes().length < maxLength;
    }
    
    /**
     * Get the effective length of a string in the protocol (length of string + 1 for null terminator)
     * 
     * @param str The string to measure
     * @return The effective length including null terminator
     */
    public static int getEffectiveLength(String str) {
        return str == null ? 1 : str.getBytes().length + 1;
    }
}
