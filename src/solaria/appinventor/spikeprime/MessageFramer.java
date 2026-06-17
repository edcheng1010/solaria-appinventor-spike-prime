package solaria.appinventor.spikeprime;

/**
 * Wire-format framing for the LEGO SPIKE Prime 3.x BLE protocol.
 *
 * Encoding pipeline (Section 5, BYTE_LEVEL_PROTOCOL.md):
 *   raw message
 *     -> COBSEncoder.encode()        (escapes 0x00, 0x01, 0x02)
 *     -> XOR every byte with 0x03    (removes Ctrl+C 0x03 from wire)
 *     -> prepend 0x01                (frame-start marker)
 *     -> append  0x02                (frame-end / delimiter)
 *
 * Decoding pipeline (Section 9, BYTE_LEVEL_PROTOCOL.md):
 *   received frame
 *     -> strip leading 0x01
 *     -> strip trailing 0x02
 *     -> XOR every byte with 0x03
 *     -> COBSEncoder.decode()
 *
 * Property guaranteed by the encoding: the frame body (bytes 1 .. len-2)
 * contains no 0x01 or 0x02 bytes, so the start/end markers are unambiguous.
 */
public class MessageFramer {

    private static final int FRAME_START = 0x01;
    private static final int FRAME_END   = 0x02;
    private static final int XOR_MASK    = 0x03;

    /**
     * Encode a raw message for transmission over BLE.
     *
     * @param rawMessage the message bytes (Message ID + payload)
     * @return framed bytes: [0x01] + COBS(rawMessage)^XOR + [0x02]
     */
    public static byte[] pack(byte[] rawMessage) {
        byte[] cobsEncoded = COBSEncoder.encode(rawMessage);
        byte[] frame = new byte[cobsEncoded.length + 2];
        frame[0] = (byte) FRAME_START;
        for (int i = 0; i < cobsEncoded.length; i++) {
            frame[i + 1] = (byte)((cobsEncoded[i] & 0xFF) ^ XOR_MASK);
        }
        frame[frame.length - 1] = (byte) FRAME_END;
        return frame;
    }

    /**
     * Decode a received BLE frame back to the original message bytes.
     *
     * @param frame bytes received on the TX characteristic
     * @return original message bytes (Message ID + payload)
     * @throws IllegalArgumentException if the frame is too short to be valid
     */
    public static byte[] unpack(byte[] frame) {
        if (frame == null || frame.length < 2) {
            throw new IllegalArgumentException(
                "Frame too short: " + (frame == null ? 0 : frame.length));
        }
        int start = ((frame[0] & 0xFF) == FRAME_START) ? 1 : 0;
        int bodyLen = frame.length - start - 1;
        if (bodyLen < 0) bodyLen = 0;
        byte[] unxored = new byte[bodyLen];
        for (int i = 0; i < bodyLen; i++) {
            unxored[i] = (byte)((frame[start + i] & 0xFF) ^ XOR_MASK);
        }
        return COBSEncoder.decode(unxored);
    }
}
