package io.github.appinventor.legospikeprime;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

/**
 * Verifies ProgramUploader:
 *   (1) chunk count matches expected value for various program/chunk-size combos
 *   (2) running CRC is accumulated correctly across chunks
 *   (3) all messages can be unframed back to valid raw messages with correct IDs
 *   (+) structural checks on the raw message field layout
 */
public class ProgramUploaderTest {

    // ------------------------------------------------------------------
    // (1) Chunk-count tests
    // ------------------------------------------------------------------

    @Test
    public void chunkCount_singleChunk_exactFit() {
        // 4 bytes, chunk size 4 → exactly 1 chunk
        assertEquals(1, make("ABCD", 4).getChunkMessages().size());
    }

    @Test
    public void chunkCount_singleChunk_smallerThanMax() {
        // 4 bytes, chunk size 445 → 1 chunk (whole program fits)
        assertEquals(1, make("ABCD", 445).getChunkMessages().size());
    }

    @Test
    public void chunkCount_twoChunks_oneByteLarger() {
        // 5 bytes, chunk size 4 → 2 chunks (4 + 1)
        assertEquals(2, make("ABCDE", 4).getChunkMessages().size());
    }

    @Test
    public void chunkCount_threeChunks_etomasfeChunkSize() {
        // 1000 bytes / 445 → ceil = 3 chunks (445 + 445 + 110)
        assertEquals(3, make(repeat('X', 1000), 445).getChunkMessages().size());
    }

    @Test
    public void chunkCount_exactMultipleOf445() {
        // 890 bytes / 445 → 2 chunks exactly
        assertEquals(2, make(repeat('X', 890), 445).getChunkMessages().size());
    }

    @Test
    public void chunkCount_emptyProgram_returnsEmptyList() {
        assertTrue(make("", 445).getChunkMessages().isEmpty());
    }

    @Test
    public void chunkCount_singleBytProgram() {
        assertEquals(1, make("Z", 445).getChunkMessages().size());
    }

    // ------------------------------------------------------------------
    // (2) Running CRC accumulation
    //
    // We use a program whose length is an exact multiple of the chunk size,
    // and whose chunk size is a multiple of 4.  This ensures no padding is
    // inserted inside any chunk, making it possible to independently verify
    // each running CRC value via SpikeCRC32.calculate(chunk, prevSeed).
    // ------------------------------------------------------------------

    /** Extract the running CRC (uint32 LE at raw bytes[1..4]) from a framed chunk. */
    private static long extractRunningCRC(byte[] framedChunk) {
        byte[] raw = MessageFramer.unpack(framedChunk);
        // raw[0]=0x10, raw[1..4]=runningCRC (uint32 LE), raw[5..6]=chunkSize
        return (raw[1] & 0xFFL)
             | ((raw[2] & 0xFFL) <<  8)
             | ((raw[3] & 0xFFL) << 16)
             | ((raw[4] & 0xFFL) << 24);
    }

    /** Extract the declared chunk size (uint16 LE at raw bytes[5..6]). */
    private static int extractChunkSize(byte[] framedChunk) {
        byte[] raw = MessageFramer.unpack(framedChunk);
        return (raw[5] & 0xFF) | ((raw[6] & 0xFF) << 8);
    }

    /** Extract the chunk data (raw bytes[7..end]). */
    private static byte[] extractChunkData(byte[] framedChunk) {
        byte[] raw = MessageFramer.unpack(framedChunk);
        byte[] data = new byte[raw.length - 7];
        System.arraycopy(raw, 7, data, 0, data.length);
        return data;
    }

    @Test
    public void runningCRC_firstChunk_seedIsZero() {
        // chunk = "ABCD" (4 bytes, 4-byte aligned — no padding added)
        // runningCRC = SpikeCRC32.calculate("ABCD", seed=0)
        List<byte[]> chunks = make("ABCDEFGH", 4).getChunkMessages();

        byte[] chunk1Bytes = "ABCD".getBytes(StandardCharsets.UTF_8);
        long   expected    = SpikeCRC32.calculate(chunk1Bytes, 0L);

        assertEquals("running CRC of first chunk (seed=0)", expected, extractRunningCRC(chunks.get(0)));
    }

    @Test
    public void runningCRC_secondChunk_usesPreviousCrcAsSeed() {
        // chunk1 = "ABCD", chunk2 = "EFGH"  (both 4-byte aligned)
        List<byte[]> chunks = make("ABCDEFGH", 4).getChunkMessages();

        byte[] c1 = "ABCD".getBytes(StandardCharsets.UTF_8);
        byte[] c2 = "EFGH".getBytes(StandardCharsets.UTF_8);
        long   crc1     = SpikeCRC32.calculate(c1, 0L);
        long   expected = SpikeCRC32.calculate(c2, crc1);

        assertEquals("running CRC of second chunk", expected, extractRunningCRC(chunks.get(1)));
    }

    @Test
    public void runningCRC_threeChunks_eachStepAccumulates() {
        // 12 bytes split into 3 × 4-byte chunks
        String program = "ABCDEFGHIJKL";
        List<byte[]> chunks = make(program, 4).getChunkMessages();
        assertEquals(3, chunks.size());

        byte[] c1 = "ABCD".getBytes(StandardCharsets.UTF_8);
        byte[] c2 = "EFGH".getBytes(StandardCharsets.UTF_8);
        byte[] c3 = "IJKL".getBytes(StandardCharsets.UTF_8);

        long crc1 = SpikeCRC32.calculate(c1, 0L);
        long crc2 = SpikeCRC32.calculate(c2, crc1);
        long crc3 = SpikeCRC32.calculate(c3, crc2);

        assertEquals("chunk 1 CRC", crc1, extractRunningCRC(chunks.get(0)));
        assertEquals("chunk 2 CRC", crc2, extractRunningCRC(chunks.get(1)));
        assertEquals("chunk 3 CRC", crc3, extractRunningCRC(chunks.get(2)));
    }

    @Test
    public void runningCRC_finalCrcEqualsFullProgramCrc_whenChunksAligned() {
        // When every chunk is a multiple of 4 bytes, the running CRC after
        // the last chunk equals SpikeCRC32.calculate(wholeProgram, 0).
        String program = "ABCDEFGHIJKL";   // 12 bytes = 3 × 4-byte chunks
        List<byte[]> chunks = make(program, 4).getChunkMessages();

        byte[] allBytes    = program.getBytes(StandardCharsets.UTF_8);
        long   fullCRC     = SpikeCRC32.calculate(allBytes, 0L);
        long   lastRunning = extractRunningCRC(chunks.get(chunks.size() - 1));

        assertEquals("final running CRC == whole-program CRC (aligned chunks)", fullCRC, lastRunning);
    }

    // ------------------------------------------------------------------
    // (3) All messages can be unframed back to valid raw messages
    // ------------------------------------------------------------------

    @Test
    public void startUploadMessage_unframesToCorrectId() {
        byte[] raw = MessageFramer.unpack(make("print('hi')", 445).getStartUploadMessage());
        assertNotNull(raw);
        assertEquals("StartFileUpload ID", MessageBuilder.MSG_START_FILE_UPLOAD, raw[0] & 0xFF);
    }

    @Test
    public void chunkMessages_eachUnframesToCorrectId() {
        List<byte[]> msgs = make("ABCDEFGHIJKL", 4).getChunkMessages();
        for (int i = 0; i < msgs.size(); i++) {
            byte[] raw = MessageFramer.unpack(msgs.get(i));
            assertNotNull("chunk " + i + " unframe returned null", raw);
            assertEquals("chunk " + i + " message ID",
                MessageBuilder.MSG_TRANSFER_CHUNK, raw[0] & 0xFF);
        }
    }

    @Test
    public void executeMessage_unframesToCorrectIdAndFields() {
        ProgramUploader u   = new ProgramUploader("prog.py", 3, "pass", 445);
        byte[]          raw = MessageFramer.unpack(u.getExecuteMessage());
        assertNotNull(raw);
        assertEquals("ProgramFlow ID",   MessageBuilder.MSG_PROGRAM_FLOW, raw[0] & 0xFF);
        assertEquals("stop = false → 0", 0x00, raw[1] & 0xFF);
        assertEquals("slotId",           3,    raw[2] & 0xFF);
    }

    // ------------------------------------------------------------------
    // Additional structural checks
    // ------------------------------------------------------------------

    @Test
    public void startUploadMessage_crcMatchesFullProgram() {
        String program = "Hello, hub!";
        byte[] programBytes = program.getBytes(StandardCharsets.UTF_8);
        long   expectedCRC  = SpikeCRC32.calculate(programBytes);

        ProgramUploader u   = new ProgramUploader("p.py", 0, program, 445);
        byte[]          raw = MessageFramer.unpack(u.getStartUploadMessage());

        // raw layout: [0x0C][name+\0][slotId][fileCRC:4 LE]
        // name = "p.py" (4 bytes) + null = 5 bytes; slotId at offset 6; CRC at offset 7
        int nameLen   = "p.py".getBytes(StandardCharsets.UTF_8).length + 1;
        int crcOffset = 1 + nameLen + 1;   // ID + name+\0 + slotId

        long actualCRC = (raw[crcOffset    ] & 0xFFL)
                       | ((raw[crcOffset + 1] & 0xFFL) <<  8)
                       | ((raw[crcOffset + 2] & 0xFFL) << 16)
                       | ((raw[crcOffset + 3] & 0xFFL) << 24);

        assertEquals("StartFileUpload CRC == full-program CRC", expectedCRC, actualCRC);
    }

    @Test
    public void chunkMessages_chunkSizeFieldMatchesActualChunkLength() {
        // 10-byte program, chunkSize=4 → chunks of 4, 4, 2
        List<byte[]> msgs = make("0123456789", 4).getChunkMessages();
        assertEquals("chunk count", 3, msgs.size());

        assertEquals("chunk 0 declared size", 4, extractChunkSize(msgs.get(0)));
        assertEquals("chunk 1 declared size", 4, extractChunkSize(msgs.get(1)));
        assertEquals("chunk 2 declared size", 2, extractChunkSize(msgs.get(2)));
    }

    @Test
    public void chunkMessages_chunkDataMatchesProgramSlice() {
        String program = "ABCDEFGH";  // 8 bytes, 2 × 4-byte chunks
        List<byte[]> msgs = make(program, 4).getChunkMessages();

        assertArrayEquals("chunk 0 data",
            "ABCD".getBytes(StandardCharsets.UTF_8),
            extractChunkData(msgs.get(0)));
        assertArrayEquals("chunk 1 data",
            "EFGH".getBytes(StandardCharsets.UTF_8),
            extractChunkData(msgs.get(1)));
    }

    @Test
    public void allMessages_areFramedWithStartAndEndMarkers() {
        ProgramUploader u = make("test program", 4);

        byte[] start = u.getStartUploadMessage();
        assertEquals("start msg begins 0x01", 0x01, start[0] & 0xFF);
        assertEquals("start msg ends   0x02", 0x02, start[start.length - 1] & 0xFF);

        for (byte[] chunk : u.getChunkMessages()) {
            assertEquals("chunk begins 0x01", 0x01, chunk[0] & 0xFF);
            assertEquals("chunk ends   0x02", 0x02, chunk[chunk.length - 1] & 0xFF);
        }

        byte[] exec = u.getExecuteMessage();
        assertEquals("exec msg begins 0x01", 0x01, exec[0] & 0xFF);
        assertEquals("exec msg ends   0x02", 0x02, exec[exec.length - 1] & 0xFF);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_rejectsZeroChunkSize() {
        new ProgramUploader("p.py", 0, "code", 0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ProgramUploader make(String program, int chunkSize) {
        return new ProgramUploader("program.py", 0, program, chunkSize);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
