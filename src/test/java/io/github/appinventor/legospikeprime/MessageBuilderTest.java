package io.github.appinventor.legospikeprime;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies MessageBuilder raw message construction against the official
 * LEGO spike-prime-docs Python SDK (messages.py).
 *
 * Tests:
 *   (a) InfoRequest is exactly [0x00]
 *   (b) TunnelMessage for "A+050B+050" matches the protocol-doc example byte-for-byte
 *   (c) Every builder returns non-null output whose first byte is the correct message ID
 *   (+) Structural checks for all three corrected message formats
 */
public class MessageBuilderTest {

    // -----------------------------------------------------------------------
    // (a) InfoRequest is exactly [0x00]
    // -----------------------------------------------------------------------

    @Test
    public void infoRequest_isExactlyOneByte0x00() {
        byte[] msg = MessageBuilder.buildInfoRequest();
        assertNotNull(msg);
        assertEquals("InfoRequest must be exactly 1 byte", 1, msg.length);
        assertEquals("InfoRequest must be [0x00]", 0x00, msg[0] & 0xFF);
    }

    // -----------------------------------------------------------------------
    // (b) TunnelMessage for "A+050B+050" matches protocol-doc example exactly
    //     Expected: [0x32, 0x0A, 0x00, 0x41, 0x2B, 0x30, 0x35, 0x30,
    //                0x42, 0x2B, 0x30, 0x35, 0x30]
    // -----------------------------------------------------------------------

    @Test
    public void tunnelMessage_exactBytesForProtocolDocExample() {
        byte[] expected = {
            0x32,                                           // message ID
            0x0A, 0x00,                                     // payload size = 10, uint16 LE
            0x41, 0x2B, 0x30, 0x35, 0x30,                 // "A+050"
            0x42, 0x2B, 0x30, 0x35, 0x30                  // "B+050"
        };
        assertArrayEquals(
            "TunnelMessage('A+050B+050') must match protocol-doc Section 5 example",
            expected, MessageBuilder.buildTunnelMessage("A+050B+050"));
    }

    // -----------------------------------------------------------------------
    // (c) All builders: non-null, correct message ID as first byte
    // -----------------------------------------------------------------------

    @Test
    public void allBuilders_returnNonNull() {
        assertNotNull(MessageBuilder.buildInfoRequest());
        assertNotNull(MessageBuilder.buildStartFileUploadRequest(
            "program.py", 0, "print('hello')".getBytes()));
        assertNotNull(MessageBuilder.buildTransferChunkRequest(
            new byte[]{0x01, 0x02}, 0L));
        assertNotNull(MessageBuilder.buildProgramFlowRequest(false, 0));
        assertNotNull(MessageBuilder.buildTunnelMessage("test"));
    }

    @Test
    public void allBuilders_firstByteIsCorrectMessageId() {
        assertEquals("InfoRequest ID",
            MessageBuilder.MSG_INFO_REQUEST,
            MessageBuilder.buildInfoRequest()[0] & 0xFF);

        assertEquals("StartFileUpload ID must be 0x0C",
            MessageBuilder.MSG_START_FILE_UPLOAD,
            MessageBuilder.buildStartFileUploadRequest(
                "program.py", 0, "print()".getBytes())[0] & 0xFF);

        assertEquals("TransferChunk ID",
            MessageBuilder.MSG_TRANSFER_CHUNK,
            MessageBuilder.buildTransferChunkRequest(new byte[]{0x42}, 0L)[0] & 0xFF);

        assertEquals("ProgramFlow ID must be 0x1E",
            MessageBuilder.MSG_PROGRAM_FLOW,
            MessageBuilder.buildProgramFlowRequest(false, 0)[0] & 0xFF);

        assertEquals("TunnelMessage ID",
            MessageBuilder.MSG_TUNNEL,
            MessageBuilder.buildTunnelMessage("hi")[0] & 0xFF);
    }

    // -----------------------------------------------------------------------
    // (fix 1) StartFileUploadRequest — corrected structure
    //   [0x0C] [fileName+\0] [slotId:1] [fileCRC:4 LE]
    //   NO fileSize field, NO fileType field
    // -----------------------------------------------------------------------

    @Test
    public void startFileUploadRequest_idIs0x0C() {
        byte[] msg = MessageBuilder.buildStartFileUploadRequest("x.py", 0, new byte[0]);
        assertEquals("StartFileUpload ID must be 0x0C", 0x0C, msg[0] & 0xFF);
    }

    @Test
    public void startFileUploadRequest_structureIsCorrect() {
        byte[] content = "print('hello')".getBytes();
        byte[] msg     = MessageBuilder.buildStartFileUploadRequest("program.py", 3, content);

        // "program.py" = 10 UTF-8 bytes + 1 null = 11 bytes name field
        // Total: 1 (ID) + 11 (name) + 1 (slot) + 4 (crc) = 17
        int nameFieldLen = "program.py".getBytes().length + 1;
        int expectedLen  = 1 + nameFieldLen + 1 + 4;
        assertEquals("StartFileUploadRequest total length", expectedLen, msg.length);

        // slotId at offset 1 + nameFieldLen
        int slotOffset = 1 + nameFieldLen;
        assertEquals("slotId must be 3", 3, msg[slotOffset] & 0xFF);

        // fileCRC immediately after slotId
        int crcOffset = slotOffset + 1;
        long actualCRC = (msg[crcOffset    ] & 0xFFL)
                       | ((msg[crcOffset + 1] & 0xFFL) << 8)
                       | ((msg[crcOffset + 2] & 0xFFL) << 16)
                       | ((msg[crcOffset + 3] & 0xFFL) << 24);
        assertEquals("fileCRC must match SpikeCRC32.calculate(content)",
            SpikeCRC32.calculate(content), actualCRC);

        // message ends right after CRC — no fileSize or fileType appended
        assertEquals("message must end immediately after CRC (no fileSize/fileType)",
            crcOffset + 4, msg.length);
    }

    @Test
    public void startFileUploadRequest_nullTerminatesFileName() {
        byte[] msg = MessageBuilder.buildStartFileUploadRequest("ab", 0, new byte[0]);
        // [0x0C] ['a'=0x61] ['b'=0x62] [0x00] [slot] [crc x4]
        assertEquals("'a'", 0x61, msg[1] & 0xFF);
        assertEquals("'b'", 0x62, msg[2] & 0xFF);
        assertEquals("null terminator", 0x00, msg[3] & 0xFF);
    }

    // -----------------------------------------------------------------------
    // (fix 2) TransferChunkRequest — chunkSize uint16 LE added between CRC and data
    //   [0x10] [runningCRC:4 LE] [chunkSize:2 LE] [chunk...]
    // -----------------------------------------------------------------------

    @Test
    public void transferChunkRequest_structureIsCorrect() {
        byte[] chunk      = {0x41, 0x42, 0x43};
        long   runningCRC = 0xDEADBEEFL;
        byte[] msg        = MessageBuilder.buildTransferChunkRequest(chunk, runningCRC);

        // 1 (ID) + 4 (crc) + 2 (chunkSize) + 3 (chunk) = 10
        assertEquals("TransferChunkRequest length", 1 + 4 + 2 + chunk.length, msg.length);

        // runningCRC at offset 1, uint32 LE
        long actualCRC = (msg[1] & 0xFFL)
                       | ((msg[2] & 0xFFL) << 8)
                       | ((msg[3] & 0xFFL) << 16)
                       | ((msg[4] & 0xFFL) << 24);
        assertEquals("runningCRC encoded correctly", runningCRC, actualCRC);

        // chunkSize at offset 5, uint16 LE
        int actualChunkSize = (msg[5] & 0xFF) | ((msg[6] & 0xFF) << 8);
        assertEquals("chunkSize must equal chunk.length", chunk.length, actualChunkSize);

        // chunk data at offset 7
        assertArrayEquals("chunk data starts at offset 7",
            chunk, java.util.Arrays.copyOfRange(msg, 7, msg.length));
    }

    @Test
    public void transferChunkRequest_chunkSizeEncodedAsUint16LE() {
        // chunk size 300 = 0x012C → low=0x2C, high=0x01
        byte[] chunk = new byte[300];
        byte[] msg   = MessageBuilder.buildTransferChunkRequest(chunk, 0L);
        assertEquals("chunkSize low  for 300 bytes", 0x2C, msg[5] & 0xFF);
        assertEquals("chunkSize high for 300 bytes", 0x01, msg[6] & 0xFF);
    }

    // -----------------------------------------------------------------------
    // (fix 3) ProgramFlowRequest — renamed, ID=0x1E, stop byte before slotId
    //   [0x1E] [stop:1] [slotId:1]
    // -----------------------------------------------------------------------

    @Test
    public void programFlowRequest_idIs0x1E() {
        byte[] msg = MessageBuilder.buildProgramFlowRequest(false, 0);
        assertEquals("ProgramFlow ID must be 0x1E", 0x1E, msg[0] & 0xFF);
    }

    @Test
    public void programFlowRequest_structureIsCorrect() {
        // start (stop=false)
        byte[] start = MessageBuilder.buildProgramFlowRequest(false, 7);
        assertEquals("ProgramFlowRequest must be 3 bytes", 3, start.length);
        assertEquals("stop=false → 0x00", 0x00, start[1] & 0xFF);
        assertEquals("slotId must be 7",  7,    start[2] & 0xFF);

        // stop (stop=true)
        byte[] stop = MessageBuilder.buildProgramFlowRequest(true, 2);
        assertEquals("stop=true → 0x01", 0x01, stop[1] & 0xFF);
        assertEquals("slotId must be 2",  2,    stop[2] & 0xFF);
    }

    @Test
    public void programFlowRequest_stopByteBeforeSlotId() {
        byte[] msg = MessageBuilder.buildProgramFlowRequest(true, 5);
        // stop is at index 1, slotId at index 2 — not the other way round
        assertEquals("stop byte at index 1", 0x01, msg[1] & 0xFF);
        assertEquals("slotId at index 2",    5,    msg[2] & 0xFF);
    }

    // -----------------------------------------------------------------------
    // Pipeline smoke tests
    // -----------------------------------------------------------------------

    @Test
    public void infoRequest_survivesFullFramingPipeline() {
        byte[] raw = MessageBuilder.buildInfoRequest();
        assertArrayEquals("InfoRequest round-trips through MessageFramer",
            raw, MessageFramer.unpack(MessageFramer.pack(raw)));
    }

    @Test
    public void tunnelMessage_survivesFullFramingPipeline() {
        byte[] raw = MessageBuilder.buildTunnelMessage("A+050B+050");
        assertArrayEquals("TunnelMessage round-trips through MessageFramer",
            raw, MessageFramer.unpack(MessageFramer.pack(raw)));
    }

    @Test
    public void programFlowRequest_survivesFullFramingPipeline() {
        byte[] raw = MessageBuilder.buildProgramFlowRequest(false, 0);
        assertArrayEquals("ProgramFlowRequest round-trips through MessageFramer",
            raw, MessageFramer.unpack(MessageFramer.pack(raw)));
    }

    // -----------------------------------------------------------------------
    // Tunnel message additional
    // -----------------------------------------------------------------------

    @Test
    public void tunnelMessage_payloadSizeEncodedAsUint16LE() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('X');
        byte[] msg = MessageBuilder.buildTunnelMessage(sb.toString());
        assertEquals("size_low  for 300-byte payload", 0x2C, msg[1] & 0xFF);
        assertEquals("size_high for 300-byte payload", 0x01, msg[2] & 0xFF);
        assertEquals("total message length", 1 + 2 + 300, msg.length);
    }

    @Test
    public void tunnelMessage_payloadIsUtf8() {
        byte[] msg = MessageBuilder.buildTunnelMessage("AB");
        assertEquals("payload byte 0 = 'A'", 0x41, msg[3] & 0xFF);
        assertEquals("payload byte 1 = 'B'", 0x42, msg[4] & 0xFF);
    }
}
