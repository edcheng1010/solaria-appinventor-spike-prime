package io.github.appinventor.legospikeprime;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Produces the complete sequence of framed BLE messages required to upload
 * and start a Python program on a LEGO SPIKE Prime 3.x hub.
 *
 * Upload sequence (mirrors app.py exactly):
 * <ol>
 *   <li>{@link #getStartUploadMessage()} — one StartFileUploadRequest (0x0C)
 *       carrying the file name, slot, and CRC of the whole program.</li>
 *   <li>{@link #getChunkMessages()} — one TransferChunkRequest (0x10) per
 *       chunk; each carries the running CRC accumulated since chunk 0.</li>
 *   <li>{@link #getExecuteMessage()} — one ProgramFlowRequest (0x1E, stop=false).</li>
 * </ol>
 *
 * Every message returned is already framed (MessageFramer.pack applied),
 * ready to write directly to the hub's RX characteristic (splitting by
 * max_packet_size is the BLE layer's responsibility).
 *
 * Running-CRC semantics (from app.py):
 * <pre>
 *   running_crc = 0
 *   for chunk in chunks:
 *       running_crc = crc(chunk, running_crc)   // seed = previous result
 *       send TransferChunkRequest(running_crc, chunk)
 * </pre>
 */
public class ProgramUploader {

    private final String fileName;
    private final int    slotId;
    private final byte[] programBytes;
    private final int    maxChunkSize;

    /**
     * @param fileName     file name stored on the hub (e.g. {@code "program.py"})
     * @param slotId       program slot (0–19)
     * @param programCode  Python source code as a Java String (UTF-8 encoded internally)
     * @param maxChunkSize maximum bytes per TransferChunkRequest payload;
     *                     use the value from InfoResponse.maxChunkSize (typically 445)
     */
    public ProgramUploader(String fileName, int slotId,
                           String programCode, int maxChunkSize) {
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be > 0, got " + maxChunkSize);
        }
        this.fileName     = fileName;
        this.slotId       = slotId;
        this.programBytes = programCode.getBytes(StandardCharsets.UTF_8);
        this.maxChunkSize = maxChunkSize;
    }

    /**
     * Returns the framed StartFileUploadRequest.
     * The CRC field is computed over the entire program (with 4-byte padding).
     */
    public byte[] getStartUploadMessage() {
        return MessageFramer.pack(
            MessageBuilder.buildStartFileUploadRequest(fileName, slotId, programBytes));
    }

    /**
     * Splits the program into chunks of at most {@code maxChunkSize} bytes,
     * accumulates the running CRC across all chunks (seed 0 for the first),
     * and returns the list of framed TransferChunkRequest messages in order.
     *
     * <p>Returns an empty list if the program is empty.
     */
    public List<byte[]> getChunkMessages() {
        if (programBytes.length == 0) {
            return Collections.emptyList();
        }

        int chunkCount = (programBytes.length + maxChunkSize - 1) / maxChunkSize;
        List<byte[]> messages = new ArrayList<>(chunkCount);

        long runningCRC = 0L;
        int  offset     = 0;

        while (offset < programBytes.length) {
            int   end   = Math.min(offset + maxChunkSize, programBytes.length);
            byte[] chunk = Arrays.copyOfRange(programBytes, offset, end);

            runningCRC = SpikeCRC32.calculate(chunk, runningCRC);
            messages.add(MessageFramer.pack(
                MessageBuilder.buildTransferChunkRequest(chunk, runningCRC)));

            offset = end;
        }

        return messages;
    }

    /**
     * Returns the framed ProgramFlowRequest that starts the uploaded program.
     * Equivalent to {@code ProgramFlowRequest(stop=False, slot=slotId)}.
     */
    public byte[] getExecuteMessage() {
        return MessageFramer.pack(
            MessageBuilder.buildProgramFlowRequest(false, slotId));
    }

    // -----------------------------------------------------------------------
    // Package-private accessors for testing
    // -----------------------------------------------------------------------

    /** The UTF-8 encoded program bytes this uploader operates on. */
    byte[] getProgramBytes() { return programBytes; }

    /** The maxChunkSize this uploader was constructed with. */
    int getMaxChunkSize() { return maxChunkSize; }
}
