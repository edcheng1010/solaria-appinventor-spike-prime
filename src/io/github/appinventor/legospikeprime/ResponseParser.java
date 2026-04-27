package io.github.appinventor.legospikeprime;

/**
 * Parses raw hub response messages (after COBS decoding and deframing).
 *
 * All parse methods receive the unframed message bytes starting with the
 * message-ID byte, i.e. exactly what {@link MessageFramer#unpack(byte[])}
 * returns from data received on the TX characteristic.
 *
 * InfoResponse wire layout — struct {@code <BBBHBBHHHHH} (17 bytes total):
 * <pre>
 *  Offset  Size  Field
 *    0       1   msgId            (0x01)
 *    1       1   rpcMajor         uint8
 *    2       1   rpcMinor         uint8
 *    3–4     2   rpcBuild         uint16 LE
 *    5       1   fwMajor          uint8
 *    6       1   fwMinor          uint8
 *    7–8     2   fwBuild          uint16 LE
 *    9–10    2   maxPacketSize    uint16 LE
 *   11–12    2   maxMessageSize   uint16 LE
 *   13–14    2   maxChunkSize     uint16 LE
 *   15–16    2   productGroupDevice uint16 LE
 * </pre>
 *
 * StatusResponse wire layout — struct {@code <BB} (≥2 bytes):
 * <pre>
 *  Offset  Size  Field
 *    0       1   msgId    (varies: 0x0D, 0x11, 0x1F, 0x29, 0x47, …)
 *    1       1   status   0x00 = acknowledged/success, 0x01 = not-acknowledged
 * </pre>
 */
public class ResponseParser {

    /** Message ID for InfoResponse. */
    public static final int MSG_INFO_RESPONSE = 0x01;

    /** Total byte length of a valid InfoResponse message. */
    public static final int INFO_RESPONSE_LENGTH = 17;

    // -----------------------------------------------------------------------
    // Public inner class: InfoResponse
    // -----------------------------------------------------------------------

    /**
     * Parsed InfoResponse fields.
     * All uint8 values are stored as {@code int} (0–255);
     * all uint16 values are stored as {@code int} (0–65535).
     */
    public static class InfoResponse {
        public int rpcMajor;
        public int rpcMinor;
        public int rpcBuild;
        public int fwMajor;
        public int fwMinor;
        public int fwBuild;
        public int maxPacketSize;
        public int maxMessageSize;
        public int maxChunkSize;
        public int productGroupDevice;

        @Override
        public String toString() {
            return "InfoResponse{"
                + "rpc=" + rpcMajor + "." + rpcMinor + "." + rpcBuild
                + ", fw=" + fwMajor + "." + fwMinor + "." + fwBuild
                + ", maxPacket=" + maxPacketSize
                + ", maxMsg=" + maxMessageSize
                + ", maxChunk=" + maxChunkSize
                + ", product=0x" + Integer.toHexString(productGroupDevice)
                + "}";
        }
    }

    // -----------------------------------------------------------------------
    // Parse methods
    // -----------------------------------------------------------------------

    /**
     * Parse an InfoResponse message.
     *
     * @param data raw unframed message bytes (must be exactly
     *             {@link #INFO_RESPONSE_LENGTH} bytes, starting with 0x01)
     * @return populated {@link InfoResponse}, or {@code null} if the data is
     *         null, too short, or does not start with the InfoResponse ID
     */
    public static InfoResponse parseInfoResponse(byte[] data) {
        if (data == null || data.length < INFO_RESPONSE_LENGTH) {
            return null;
        }
        if ((data[0] & 0xFF) != MSG_INFO_RESPONSE) {
            return null;
        }

        InfoResponse r = new InfoResponse();
        r.rpcMajor          = data[1] & 0xFF;
        r.rpcMinor          = data[2] & 0xFF;
        r.rpcBuild          = readUint16LE(data, 3);
        r.fwMajor           = data[5] & 0xFF;
        r.fwMinor           = data[6] & 0xFF;
        r.fwBuild           = readUint16LE(data, 7);
        r.maxPacketSize     = readUint16LE(data, 9);
        r.maxMessageSize    = readUint16LE(data, 11);
        r.maxChunkSize      = readUint16LE(data, 13);
        r.productGroupDevice= readUint16LE(data, 15);
        return r;
    }

    /**
     * Parse the status byte from any two-byte status-response message.
     *
     * @param data raw unframed message bytes (must be at least 2 bytes;
     *             data[1] is the status byte)
     * @return {@code true} if data[1] == 0x00 (acknowledged/success),
     *         {@code false} for any other value or if data is invalid
     */
    public static boolean parseStatusResponse(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        return (data[1] & 0xFF) == 0x00;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /** Read a uint16 little-endian value from {@code data} at {@code offset}. */
    private static int readUint16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
