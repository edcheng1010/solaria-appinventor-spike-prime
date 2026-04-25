package io.github.appinventor.legospike;

import java.nio.charset.StandardCharsets;

/**
 * Builds raw message byte arrays for the LEGO SPIKE Prime 3.x BLE protocol.
 *
 * Each method returns the unframed payload (Message ID + fields). The caller
 * must pass the result through {@link MessageFramer#pack(byte[])} before
 * writing to the RX characteristic.
 *
 * Message IDs used here follow the task specification. Two deviations from
 * the official LEGO spike-prime-docs are noted inline:
 *   • StartFileUploadRequest uses 0x0E here vs 0x0C in the official Python SDK.
 *   • ExecuteProgramRequest uses 0x12 here vs ProgramFlowRequest (0x1E) in
 *     the official Python SDK.
 * If hub communication fails for these two messages, change the IDs to 0x0C
 * and 0x1E respectively and align field layouts with messages.py.
 *
 * TunnelMessage field layout: [0x32] [size_low:uint8] [size_high:uint8] [payload].
 * The two bytes after the ID are a uint16 little-endian payload length, NOT a
 * port ID — the task description's "portId=0x0A" is the payload-size byte that
 * happens to equal 10 for the 10-character "A+050B+050" test string.
 */
public class MessageBuilder {

    // -----------------------------------------------------------------------
    // Message IDs (App → Hub)
    // -----------------------------------------------------------------------
    public static final int MSG_INFO_REQUEST        = 0x00;
    /** NOTE: official SDK uses 0x0C; see class javadoc */
    public static final int MSG_START_FILE_UPLOAD   = 0x0E;
    public static final int MSG_TRANSFER_CHUNK      = 0x10;
    /** NOTE: official SDK uses ProgramFlowRequest (0x1E); see class javadoc */
    public static final int MSG_EXECUTE_PROGRAM     = 0x12;
    public static final int MSG_TUNNEL              = 0x32;

    // File type byte for Python scripts
    private static final byte FILE_TYPE_PYTHON = 0x04;

    // -----------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------

    /**
     * InfoRequest — always the first message sent after connecting.
     * Structure: [0x00]
     */
    public static byte[] buildInfoRequest() {
        return new byte[]{(byte) MSG_INFO_REQUEST};
    }

    /**
     * StartFileUploadRequest — begins a Python program upload to a hub slot.
     *
     * Structure:
     *   [0x0E]
     *   [fileName : null-terminated UTF-8]
     *   [slotId   : uint8]
     *   [fileSize : uint32 little-endian]
     *   [fileCRC  : uint32 little-endian, CRC32 of fileContent]
     *   [fileType : uint8 = 0x04 (Python)]
     *
     * @param fileName    name stored on the hub (e.g. "program.py")
     * @param slotId      program slot (0–19)
     * @param fileContent the complete UTF-8 encoded Python program bytes
     */
    public static byte[] buildStartFileUploadRequest(
            String fileName, int slotId, byte[] fileContent) {

        byte[] nameBytes = toNullTerminated(fileName);
        long   fileSize  = fileContent.length;
        long   fileCRC   = SpikeCRC32.calculate(fileContent);
        byte[] crcLE     = SpikeCRC32.toByteArrayLE(fileCRC);

        //  1 (ID) + nameBytes.length + 1 (slot) + 4 (size) + 4 (crc) + 1 (type)
        byte[] msg = new byte[1 + nameBytes.length + 1 + 4 + 4 + 1];
        int pos = 0;

        msg[pos++] = (byte) MSG_START_FILE_UPLOAD;

        System.arraycopy(nameBytes, 0, msg, pos, nameBytes.length);
        pos += nameBytes.length;

        msg[pos++] = (byte)(slotId & 0xFF);

        msg[pos++] = (byte)( fileSize        & 0xFF);
        msg[pos++] = (byte)((fileSize >>  8) & 0xFF);
        msg[pos++] = (byte)((fileSize >> 16) & 0xFF);
        msg[pos++] = (byte)((fileSize >> 24) & 0xFF);

        System.arraycopy(crcLE, 0, msg, pos, 4);
        pos += 4;

        msg[pos] = FILE_TYPE_PYTHON;

        return msg;
    }

    /**
     * TransferChunkRequest — sends one chunk of program data.
     *
     * Structure:
     *   [0x10]
     *   [runningCRC : uint32 little-endian]
     *   [chunk      : raw bytes]
     *
     * @param chunk      the chunk bytes to transfer
     * @param runningCRC the accumulated CRC after encoding this chunk
     *                   (seed=0 for the first chunk; previous result for later ones)
     */
    public static byte[] buildTransferChunkRequest(byte[] chunk, long runningCRC) {
        byte[] crcLE = SpikeCRC32.toByteArrayLE(runningCRC);
        byte[] msg   = new byte[1 + 4 + chunk.length];
        int pos = 0;

        msg[pos++] = (byte) MSG_TRANSFER_CHUNK;

        System.arraycopy(crcLE, 0, msg, pos, 4);
        pos += 4;

        System.arraycopy(chunk, 0, msg, pos, chunk.length);

        return msg;
    }

    /**
     * ExecuteProgramRequest — starts the program in the given slot.
     *
     * Structure:
     *   [0x12]
     *   [slotId : uint8]
     *
     * @param slotId program slot (0–19)
     */
    public static byte[] buildExecuteProgramRequest(int slotId) {
        return new byte[]{(byte) MSG_EXECUTE_PROGRAM, (byte)(slotId & 0xFF)};
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
        // result[src.length] is already 0
        return result;
    }
}
