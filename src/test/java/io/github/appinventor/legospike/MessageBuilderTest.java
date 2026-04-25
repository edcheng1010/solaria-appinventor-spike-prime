package io.github.appinventor.legospike;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies MessageBuilder raw message construction.
 *
 * Tests:
 *   (a) InfoRequest is exactly [0x00]
 *   (b) TunnelMessage for "A+050B+050" matches the protocol-doc example byte-for-byte
 *   (c) Every builder returns non-null output whose first byte is the correct message ID
 *   (+) Additional structural checks for each message format
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
        byte[] actual = MessageBuilder.buildTunnelMessage("A+050B+050");
        assertArrayEquals(
            "TunnelMessage('A+050B+050') must match protocol-doc Section 5 example",
            expected, actual);
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
        assertNotNull(MessageBuilder.buildExecuteProgramRequest(0));
        assertNotNull(MessageBuilder.buildTunnelMessage("test"));
    }

    @Test
    public void allBuilders_firstByteIsCorrectMessageId() {
        assertEquals("InfoRequest ID",
            MessageBuilder.MSG_INFO_REQUEST,
            MessageBuilder.buildInfoRequest()[0] & 0xFF);

        assertEquals("StartFileUpload ID",
            MessageBuilder.MSG_START_FILE_UPLOAD,
            MessageBuilder.buildStartFileUploadRequest(
                "program.py", 0, "print()".getBytes())[0] & 0xFF);

        assertEquals("TransferChunk ID",
            MessageBuilder.MSG_TRANSFER_CHUNK,
            MessageBuilder.buildTransferChunkRequest(new byte[]{0x42}, 0L)[0] & 0xFF);

        assertEquals("ExecuteProgram ID",
            MessageBuilder.MSG_EXECUTE_PROGRAM,
            MessageBuilder.buildExecuteProgramRequest(0)[0] & 0xFF);

        assertEquals("TunnelMessage ID",
            MessageBuilder.MSG_TUNNEL,
            MessageBuilder.buildTunnelMessage("hi")[0] & 0xFF);
    }

    // -----------------------------------------------------------------------
    // Additional structural tests
    // -----------------------------------------------------------------------

    @Test
    public void startFileUploadRequest_structureIsCorrect() {
        byte[] content  = "print('hello')".getBytes();
        byte[] msg      = MessageBuilder.buildStartFileUploadRequest("program.py", 3, content);

        // [0x0E] [name+\0] [slotId] [fileSize:4 LE] [fileCRC:4 LE] [fileType=0x04]
        // name "program.py" = 10 bytes + 1 null = 11
        int expectedLen = 1 + 11 + 1 + 4 + 4 + 1;
        assertEquals("StartFileUploadRequest total length", expectedLen, msg.length);

        // slotId byte is at offset 1 + 11 = 12
        int slotOffset = 1 + "program.py".getBytes().length + 1;
        assertEquals("slotId must be 3", 3, msg[slotOffset] & 0xFF);

        // fileSize at offset 13, little-endian
        int sizeOffset = slotOffset + 1;
        long fileSize = (msg[sizeOffset] & 0xFFL)
                      | ((msg[sizeOffset+1] & 0xFFL) << 8)
                      | ((msg[sizeOffset+2] & 0xFFL) << 16)
                      | ((msg[sizeOffset+3] & 0xFFL) << 24);
        assertEquals("fileSize must equal content length", content.length, fileSize);

        // fileCRC: verify it matches SpikeCRC32.calculate(content)
        long expectedCRC = SpikeCRC32.calculate(content);
        int crcOffset = sizeOffset + 4;
        long actualCRC = (msg[crcOffset] & 0xFFL)
                       | ((msg[crcOffset+1] & 0xFFL) << 8)
                       | ((msg[crcOffset+2] & 0xFFL) << 16)
                       | ((msg[crcOffset+3] & 0xFFL) << 24);
        assertEquals("fileCRC must match SpikeCRC32.calculate(content)", expectedCRC, actualCRC);

        // fileType last byte = 0x04
        assertEquals("fileType must be 0x04 (Python)", 0x04, msg[msg.length - 1] & 0xFF);
    }

    @Test
    public void transferChunkRequest_structureIsCorrect() {
        byte[] chunk      = {0x41, 0x42, 0x43};
        long   runningCRC = 0xDEADBEEFL;
        byte[] msg        = MessageBuilder.buildTransferChunkRequest(chunk, runningCRC);

        // [0x10] [crc:4 LE] [chunk...]
        assertEquals("TransferChunkRequest length", 1 + 4 + chunk.length, msg.length);

        // running CRC at offset 1, little-endian
        long actualCRC = (msg[1] & 0xFFL)
                       | ((msg[2] & 0xFFL) << 8)
                       | ((msg[3] & 0xFFL) << 16)
                       | ((msg[4] & 0xFFL) << 24);
        assertEquals("runningCRC encoded correctly", runningCRC, actualCRC);

        // chunk bytes follow immediately
        assertArrayEquals("chunk data at end of message",
            chunk, java.util.Arrays.copyOfRange(msg, 5, msg.length));
    }

    @Test
    public void executeProgramRequest_structureIsCorrect() {
        byte[] msg = MessageBuilder.buildExecuteProgramRequest(7);
        assertEquals("ExecuteProgramRequest must be 2 bytes", 2, msg.length);
        assertEquals("slotId must be 7", 7, msg[1] & 0xFF);
    }

    @Test
    public void tunnelMessage_payloadSizeEncodedAsUint16LE() {
        // A 300-byte payload (> 255) exercises the high byte of the size field
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('X');
        String payload = sb.toString();

        byte[] msg = MessageBuilder.buildTunnelMessage(payload);

        // size = 300 = 0x012C  →  low=0x2C, high=0x01
        assertEquals("size_low  for 300-byte payload", 0x2C, msg[1] & 0xFF);
        assertEquals("size_high for 300-byte payload", 0x01, msg[2] & 0xFF);
        assertEquals("total message length", 1 + 2 + 300, msg.length);
    }

    @Test
    public void tunnelMessage_payloadIsUtf8() {
        byte[] msg = MessageBuilder.buildTunnelMessage("AB");
        // 'A'=0x41, 'B'=0x42
        assertEquals("payload byte 0 = 'A'", 0x41, msg[3] & 0xFF);
        assertEquals("payload byte 1 = 'B'", 0x42, msg[4] & 0xFF);
    }

    @Test
    public void infoRequest_survivesFullFramingPipeline() {
        // Smoke-test: InfoRequest can be packed and unpacked without error
        byte[] raw    = MessageBuilder.buildInfoRequest();
        byte[] framed = MessageFramer.pack(raw);
        byte[] back   = MessageFramer.unpack(framed);
        assertArrayEquals("InfoRequest round-trips through MessageFramer", raw, back);
    }

    @Test
    public void tunnelMessage_survivesFullFramingPipeline() {
        byte[] raw    = MessageBuilder.buildTunnelMessage("A+050B+050");
        byte[] framed = MessageFramer.pack(raw);
        byte[] back   = MessageFramer.unpack(framed);
        assertArrayEquals("TunnelMessage round-trips through MessageFramer", raw, back);
    }
}
