package io.github.appinventor.legospikeprime;

import java.util.Arrays;

/**
 * COBS encoding for LEGO SPIKE Prime 3.x BLE protocol.
 *
 * Faithfully translated from the LEGO official reference implementation (cobs.py).
 * Escapes all bytes <= DELIMITER (i.e. 0x00, 0x01, 0x02).
 *
 * Pipeline for sending:  encode() -> XOR each byte with XOR -> append DELIMITER
 *                        (all wrapped in pack())
 * Pipeline for receiving: strip leading 0x01 + trailing 0x02 -> XOR -> decode()
 *                        (all wrapped in unpack())
 */
public class COBSEncoder {

    public static final int DELIMITER       = 0x02;
    public static final int NO_DELIMITER    = 0xFF;
    public static final int MAX_BLOCK_SIZE  = 84;
    public static final int COBS_CODE_OFFSET = 0x02;  // == DELIMITER
    public static final int XOR             = 0x03;

    /**
     * COBS-encode data so no byte <= DELIMITER appears in the output.
     * Does NOT apply XOR and does NOT append the delimiter byte.
     */
    public static byte[] encode(byte[] data) {
        // Worst case: every byte is a delimiter, doubling the size
        byte[] buf = new byte[data.length * 2 + 2];
        int bufLen = 0;

        // begin_block
        int codeIndex = bufLen;
        buf[bufLen++] = (byte) NO_DELIMITER;
        int block = 1;

        for (byte rawByte : data) {
            int b = rawByte & 0xFF;

            if (b > DELIMITER) {
                buf[bufLen++] = (byte) b;
                block++;
            }

            if (b <= DELIMITER || block > MAX_BLOCK_SIZE) {
                if (b <= DELIMITER) {
                    int delimiterBase = b * MAX_BLOCK_SIZE;
                    int blockOffset   = block + COBS_CODE_OFFSET;
                    buf[codeIndex]    = (byte)(delimiterBase + blockOffset);
                }
                // begin_block
                codeIndex = bufLen;
                buf[bufLen++] = (byte) NO_DELIMITER;
                block = 1;
            }
        }

        // finalise last block's code word
        buf[codeIndex] = (byte)(block + COBS_CODE_OFFSET);

        return Arrays.copyOf(buf, bufLen);
    }

    /**
     * COBS-decode data produced by encode().
     * Does NOT expect XOR to have been applied; caller must un-XOR first if needed.
     */
    public static byte[] decode(byte[] data) {
        byte[] buf = new byte[data.length];
        int bufLen = 0;

        int[] valueAndBlock = unescape(data[0] & 0xFF);
        int value = valueAndBlock[0];   // -1 means NO_DELIMITER (None in Python)
        int block = valueAndBlock[1];

        for (int i = 1; i < data.length; i++) {
            int b = data[i] & 0xFF;
            block--;
            if (block > 0) {
                buf[bufLen++] = (byte) b;
                continue;
            }
            // block completed
            if (value != -1) {
                buf[bufLen++] = (byte) value;
            }
            valueAndBlock = unescape(b);
            value = valueAndBlock[0];
            block = valueAndBlock[1];
        }

        return Arrays.copyOf(buf, bufLen);
    }

    /**
     * Encode, XOR every byte with XOR mask, then append the DELIMITER.
     * This is the full "wire format" pipeline for outbound messages.
     */
    public static byte[] pack(byte[] data) {
        byte[] encoded = encode(data);
        byte[] result  = new byte[encoded.length + 1];
        for (int i = 0; i < encoded.length; i++) {
            result[i] = (byte)((encoded[i] & 0xFF) ^ XOR);
        }
        result[encoded.length] = (byte) DELIMITER;
        return result;
    }

    /**
     * Strip optional leading 0x01 priority byte and trailing DELIMITER,
     * un-XOR every byte, then COBS-decode.
     * This is the full "wire format" pipeline for inbound messages.
     */
    public static byte[] unpack(byte[] frame) {
        int start = 0;
        if (frame.length > 0 && (frame[0] & 0xFF) == 0x01) {
            start = 1;
        }
        // strip trailing delimiter, un-XOR
        int payloadLen = frame.length - start - 1; // -1 for trailing DELIMITER
        byte[] unframed = new byte[payloadLen];
        for (int i = 0; i < payloadLen; i++) {
            unframed[i] = (byte)((frame[start + i] & 0xFF) ^ XOR);
        }
        return decode(unframed);
    }

    // -------------------------------------------------------------------------

    /**
     * Translate a code word into (delimiterValue, blockSize).
     * delimiterValue == -1 signals NO_DELIMITER (Python None).
     */
    private static int[] unescape(int code) {
        if (code == NO_DELIMITER) {
            // no delimiter in this block; block spans MAX_BLOCK_SIZE + 1 bytes (incl. code word)
            return new int[]{-1, MAX_BLOCK_SIZE + 1};
        }
        int div = code - COBS_CODE_OFFSET;
        int value = div / MAX_BLOCK_SIZE;
        int block = div % MAX_BLOCK_SIZE;
        if (block == 0) {
            // maximum block size ending with delimiter
            block = MAX_BLOCK_SIZE;
            value -= 1;
        }
        return new int[]{value, block};
    }
}
