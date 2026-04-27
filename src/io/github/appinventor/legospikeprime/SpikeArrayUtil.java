package io.github.appinventor.legospikeprime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Array handling utility for LEGO SPIKE Prime protocol
 * 
 * Implements array handling according to the SPIKE™ Prime Protocol 1.0 specification,
 * which requires specific formats for arrays of different types.
 */
public class SpikeArrayUtil {
    
    /**
     * Convert a byte array to a protocol-compliant format
     * 
     * @param array The byte array
     * @return The protocol-formatted byte array
     */
    public static byte[] encodeByteArray(byte[] array) {
        if (array == null) {
            return new byte[]{0};
        }
        
        // For byte arrays, we just need to include the length as a byte
        byte[] result = new byte[array.length + 1];
        result[0] = (byte) array.length;
        System.arraycopy(array, 0, result, 1, array.length);
        return result;
    }
    
    /**
     * Extract a byte array from protocol-formatted data
     * 
     * @param data The protocol-formatted data
     * @param offset The offset in the data
     * @return The extracted byte array
     */
    public static byte[] decodeByteArray(byte[] data, int offset) {
        if (data == null || offset >= data.length) {
            return new byte[0];
        }
        
        int length = data[offset] & 0xFF;
        if (offset + 1 + length > data.length) {
            // Not enough data
            return new byte[0];
        }
        
        byte[] result = new byte[length];
        System.arraycopy(data, offset + 1, result, 0, length);
        return result;
    }
    
    /**
     * Convert an int array to a protocol-compliant format
     * 
     * @param array The int array
     * @return The protocol-formatted byte array
     */
    public static byte[] encodeIntArray(int[] array) {
        if (array == null) {
            return new byte[]{0};
        }
        
        // For int arrays, we need to include the length as a byte
        // and then each int as 4 bytes
        byte[] result = new byte[1 + array.length * 4];
        result[0] = (byte) array.length;
        
        ByteBuffer buffer = ByteBuffer.wrap(result, 1, result.length - 1);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int value : array) {
            buffer.putInt(value);
        }
        
        return result;
    }
    
    /**
     * Extract an int array from protocol-formatted data
     * 
     * @param data The protocol-formatted data
     * @param offset The offset in the data
     * @return The extracted int array
     */
    public static int[] decodeIntArray(byte[] data, int offset) {
        if (data == null || offset >= data.length) {
            return new int[0];
        }
        
        int length = data[offset] & 0xFF;
        if (offset + 1 + length * 4 > data.length) {
            // Not enough data
            return new int[0];
        }
        
        int[] result = new int[length];
        ByteBuffer buffer = ByteBuffer.wrap(data, offset + 1, length * 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int i = 0; i < length; i++) {
            result[i] = buffer.getInt();
        }
        
        return result;
    }
    
    /**
     * Convert a short array to a protocol-compliant format
     * 
     * @param array The short array
     * @return The protocol-formatted byte array
     */
    public static byte[] encodeShortArray(short[] array) {
        if (array == null) {
            return new byte[]{0};
        }
        
        // For short arrays, we need to include the length as a byte
        // and then each short as 2 bytes
        byte[] result = new byte[1 + array.length * 2];
        result[0] = (byte) array.length;
        
        ByteBuffer buffer = ByteBuffer.wrap(result, 1, result.length - 1);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (short value : array) {
            buffer.putShort(value);
        }
        
        return result;
    }
    
    /**
     * Extract a short array from protocol-formatted data
     * 
     * @param data The protocol-formatted data
     * @param offset The offset in the data
     * @return The extracted short array
     */
    public static short[] decodeShortArray(byte[] data, int offset) {
        if (data == null || offset >= data.length) {
            return new short[0];
        }
        
        int length = data[offset] & 0xFF;
        if (offset + 1 + length * 2 > data.length) {
            // Not enough data
            return new short[0];
        }
        
        short[] result = new short[length];
        ByteBuffer buffer = ByteBuffer.wrap(data, offset + 1, length * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        for (int i = 0; i < length; i++) {
            result[i] = buffer.getShort();
        }
        
        return result;
    }
    
    /**
     * Convert a string array to a protocol-compliant format
     * 
     * @param array The string array
     * @return The protocol-formatted byte array
     */
    public static byte[] encodeStringArray(String[] array) {
        if (array == null) {
            return new byte[]{0};
        }
        
        // First, calculate the total size needed
        int totalSize = 1; // For the length byte
        for (String str : array) {
            totalSize += SpikeStringUtil.getEffectiveLength(str);
        }
        
        byte[] result = new byte[totalSize];
        result[0] = (byte) array.length;
        
        int offset = 1;
        for (String str : array) {
            byte[] strBytes = SpikeStringUtil.toNullTerminatedString(str);
            System.arraycopy(strBytes, 0, result, offset, strBytes.length);
            offset += strBytes.length;
        }
        
        return result;
    }
    
    /**
     * Extract a string array from protocol-formatted data
     * 
     * @param data The protocol-formatted data
     * @param offset The offset in the data
     * @return The extracted string array
     */
    public static String[] decodeStringArray(byte[] data, int offset) {
        if (data == null || offset >= data.length) {
            return new String[0];
        }
        
        int length = data[offset] & 0xFF;
        String[] result = new String[length];
        
        int currentOffset = offset + 1;
        for (int i = 0; i < length; i++) {
            if (currentOffset >= data.length) {
                // Not enough data
                return new String[0];
            }
            
            result[i] = SpikeStringUtil.fromNullTerminatedString(data, currentOffset);
            currentOffset += result[i].getBytes().length + 1; // +1 for null terminator
        }
        
        return result;
    }
    
    /**
     * Get the total size of a protocol-formatted array
     * 
     * @param data The protocol-formatted data
     * @param offset The offset in the data
     * @return The total size of the array including length byte
     */
    public static int getArraySize(byte[] data, int offset) {
        if (data == null || offset >= data.length) {
            return 0;
        }
        
        int length = data[offset] & 0xFF;
        return 1 + length; // 1 for length byte
    }
}
