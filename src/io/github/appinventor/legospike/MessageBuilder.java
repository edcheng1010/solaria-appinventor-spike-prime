package io.github.appinventor.legospike;

import java.nio.charset.StandardCharsets;

/**
 * Builds raw message byte arrays for the LEGO SPIKE Prime 3.x BLE protocol.
 *
 * Each method returns the unframed payload (Message ID + fields). The caller
 * must pass the result through {@link MessageFramer#pack(byte[])} before
 * writing to the RX characteristic.
 *
 * All message IDs and field layouts match the official LEGO spike-prime-docs
 * Python SDK (messages.py).
 *
 * TunnelMessage field layout: [0x32] [size_low:uint8] [size_high:uint8] [payload].
 * The two bytes after the ID are a uint16 little-endian payload length.
 */
public class MessageBuilder {

    // -----------------------------------------------------------------------
    // Message IDs (App → Hub)  — all match messages.py
    // -----------------------------------------------------------------------
    public static final int MSG_INFO_REQUEST      = 0x00;
    public static final int MSG_START_FILE_UPLOAD = 0x0C;
    public static final int MSG_TRANSFER_CHUNK    = 0x10;
    public static final int MSG_PROGRAM_FLOW      = 0x1E;
    public static final int MSG_TUNNEL            = 0x32;

    // -----------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------

    /**
     * InfoRequest — always the first message sent after connecting.
     *
     * Structure: [0x00]
     */
    public static byte[] buildInfoRequest() {
        return new byte[]{(byte) MSG_INFO_REQUEST};
    }

    /**
     * StartFileUploadRequest — begins a Python program upload to a hub slot.
     *
     * Structure (matches messages.py StartFileUploadRequest.serialize):
     *   [0x0C]
     *   [fileName : null-terminated UTF-8]
     *   [slotId   : uint8]
     *   [fileCRC  : uint32 little-endian, CRC32 of fileContent]
     *
     * @param fileName    name stored on the hub (e.g. "program.py")
     * @param slotId      program slot (0–19)
     * @param fileContent the complete UTF-8 encoded Python program bytes
     */
    public static byte[] buildStartFileUploadRequest(
            String fileName, int slotId, byte[] fileContent) {

        byte[] nameBytes = toNullTerminated(fileName);
        long   fileCRC   = SpikeCRC32.calculate(fileContent);
        byte[] crcLE     = SpikeCRC32.toByteArrayLE(fileCRC);

        // 1 (ID) + nameBytes.length + 1 (slot) + 4 (crc)
        byte[] msg = new byte[1 + nameBytes.length + 1 + 4];
        int pos = 0;

        msg[pos++] = (byte) MSG_START_FILE_UPLOAD;

        System.arraycopy(nameBytes, 0, msg, pos, nameBytes.length);
        pos += nameBytes.length;

        msg[pos++] = (byte)(slotId & 0xFF);

        System.arraycopy(crcLE, 0, msg, pos, 4);

        return msg;
    }

    /**
     * TransferChunkRequest — sends one chunk of program data.
     *
     * Structure (matches messages.py TransferChunkRequest.serialize):
     *   [0x10]
     *   [runningCRC : uint32 little-endian]
     *   [chunkSize  : uint16 little-endian]
     *   [chunk      : raw bytes]
     *
     * @param chunk      the chunk bytes to transfer
     * @param runningCRC the accumulated CRC after this chunk
     *                   (seed=0 for first chunk; previous result for later ones)
     */
    public static byte[] buildTransferChunkRequest(byte[] chunk, long runningCRC) {
        byte[] crcLE  = SpikeCRC32.toByteArrayLE(runningCRC);
        int    size   = chunk.length;
        byte[] msg    = new byte[1 + 4 + 2 + size];
        int pos = 0;

        msg[pos++] = (byte) MSG_TRANSFER_CHUNK;

        System.arraycopy(crcLE, 0, msg, pos, 4);
        pos += 4;

        msg[pos++] = (byte)( size       & 0xFF);   // chunkSize low
        msg[pos++] = (byte)((size >> 8) & 0xFF);   // chunkSize high

        System.arraycopy(chunk, 0, msg, pos, size);

        return msg;
    }

    /**
     * ProgramFlowRequest — start or stop the program in the given slot.
     *
     * Structure (matches messages.py ProgramFlowRequest.serialize):
     *   [0x1E]
     *   [stop   : uint8]   0x00 = start, 0x01 = stop
     *   [slotId : uint8]
     *
     * @param stop   true to stop the program, false to start it
     * @param slotId program slot (0–19)
     */
    public static byte[] buildProgramFlowRequest(boolean stop, int slotId) {
        return new byte[]{
            (byte) MSG_PROGRAM_FLOW,
            stop ? (byte) 0x01 : (byte) 0x00,
            (byte)(slotId & 0xFF)
        };
    }

    /**
     * TunnelMessage — send a command string to the running Python program.
     *
     * Structure:
     *   [0x32]
     *   [payloadSize_low  : uint8]   — uint16 little-endian payload length
     *   [payloadSize_high : uint8]
     *   [payload          : UTF-8 bytes]
     *
     * @param payload the command string (e.g. "A+050B+050")
     */
    public static byte[] buildTunnelMessage(String payload) {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        int    size         = payloadBytes.length;

        byte[] msg = new byte[1 + 2 + size];
        int pos = 0;

        msg[pos++] = (byte) MSG_TUNNEL;
        msg[pos++] = (byte)( size       & 0xFF);   // size low
        msg[pos++] = (byte)((size >> 8) & 0xFF);   // size high

        System.arraycopy(payloadBytes, 0, msg, pos, size);

        return msg;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /** Returns the UTF-8 bytes of {@code s} followed by a null terminator. */
    private static byte[] toNullTerminated(String s) {
        byte[] src    = s.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[src.length + 1];
        System.arraycopy(src, 0, result, 0, src.length);
        return result;
    }
}
