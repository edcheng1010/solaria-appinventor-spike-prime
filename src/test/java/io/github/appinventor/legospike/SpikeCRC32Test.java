package io.github.appinventor.legospike;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies SpikeCRC32 against known-good values computed from the etomasfe
 * reference implementation (crc32Uint8Array in etomasfe-working-example.html),
 * which uses:
 *   - CRC-32/ISO-HDLC (reflected, polynomial 0xEDB88320)
 *   - Zero-padding to 4-byte alignment before CRC
 *   - Running CRC continuation: initialState = seed ^ 0xFFFFFFFF
 */
public class SpikeCRC32Test {

    // -----------------------------------------------------------------------
    // Test 1 — CRC of a known string matches expected value
    //
    // Input:    "Hello" = [0x48, 0x65, 0x6C, 0x6C, 0x6F]  (5 bytes)
    // Padded:   [0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x00, 0x00, 0x00]  (8 bytes)
    // Expected: 0x0665D74B  (computed by reference Python algorithm)
    // -----------------------------------------------------------------------
    @Test
    public void testKnownString_Hello() {
        byte[] input    = "Hello".getBytes();
        long   expected = 0x0665D74BL;
        long   actual   = SpikeCRC32.calculate(input);
        assertEquals("CRC of 'Hello' (padded to 8 bytes) must be 0x0665D74B",
            expected, actual);
    }

    // -----------------------------------------------------------------------
    // Test 2 — Running CRC across two 4-byte chunks equals CRC of combined data
    //
    // chunk1 = [0x01, 0x02, 0x03, 0x04]   (4 bytes, no padding needed)
    // chunk2 = [0x05, 0x06, 0x07, 0x08]   (4 bytes, no padding needed)
    // combined = [0x01..0x08]              (8 bytes, no padding needed)
    //
    // Expected chunk1 CRC:    0xB63CFBCD
    // Expected combined CRC:  0x3FCA88C5
    // -----------------------------------------------------------------------
    @Test
    public void testRunningCrc_equalsFullCombinedCrc() {
        byte[] chunk1    = {0x01, 0x02, 0x03, 0x04};
        byte[] chunk2    = {0x05, 0x06, 0x07, 0x08};
        byte[] combined  = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};

        long crc1    = SpikeCRC32.calculate(chunk1, 0L);         // first chunk, seed=0
        long running = SpikeCRC32.calculate(chunk2, crc1);       // continuation
        long full    = SpikeCRC32.calculate(combined, 0L);       // full data, seed=0

        assertEquals("CRC of chunk1 must be 0xB63CFBCD", 0xB63CFBCDL, crc1);
        assertEquals("Running CRC after chunk2 must match full combined CRC", full, running);
        assertEquals("Full combined CRC must be 0x3FCA88C5", 0x3FCA88C5L, full);
    }

    // -----------------------------------------------------------------------
    // Test 3 — Zero-padding to 4-byte alignment
    //
    // 3-byte input [0xAA, 0xBB, 0xCC] must be padded to [0xAA, 0xBB, 0xCC, 0x00]
    // before the CRC is computed.  The result must equal the CRC of the
    // explicitly-padded 4-byte array.
    // Expected: 0xADD6AFCE
    // -----------------------------------------------------------------------
    @Test
    public void testPadding_3ByteInputPaddedTo4() {
        byte[] raw     = {(byte)0xAA, (byte)0xBB, (byte)0xCC};
        byte[] padded  = {(byte)0xAA, (byte)0xBB, (byte)0xCC, 0x00};
        long   expected = 0xADD6AFCEL;

        long crcRaw    = SpikeCRC32.calculate(raw);
        long crcPadded = SpikeCRC32.calculate(padded);

        assertEquals("Auto-padded CRC must match expected value", expected, crcRaw);
        assertEquals("Auto-padded CRC must equal explicitly-padded CRC", crcPadded, crcRaw);
    }

    // -----------------------------------------------------------------------
    // Additional: already-aligned data must not be double-padded
    // -----------------------------------------------------------------------
    @Test
    public void testPadding_4ByteAlignedDataUnchanged() {
        byte[] aligned  = {0x01, 0x02, 0x03, 0x04};
        byte[] padded   = SpikeCRC32.padTo4Alignment(aligned);
        assertEquals("Already-aligned data must not be padded further", 4, padded.length);
    }

    // -----------------------------------------------------------------------
    // Additional: toByteArrayLE encodes the CRC in little-endian order
    // 0x3FCA88C5 -> [0xC5, 0x88, 0xCA, 0x3F]
    // -----------------------------------------------------------------------
    @Test
    public void testToByteArrayLE_littleEndianEncoding() {
        long crc      = 0x3FCA88C5L;
        byte[] actual = SpikeCRC32.toByteArrayLE(crc);
        assertArrayEquals("CRC must be encoded as little-endian",
            new byte[]{(byte)0xC5, (byte)0x88, (byte)0xCA, 0x3F},
            actual);
    }

    // -----------------------------------------------------------------------
    // Additional: seed=0 convenience overload equals two-arg call with seed 0
    // -----------------------------------------------------------------------
    @Test
    public void testSeedZeroOverloadEquivalence() {
        byte[] data = "Test".getBytes();
        assertEquals("calculate(data) must equal calculate(data, 0)",
            SpikeCRC32.calculate(data, 0L),
            SpikeCRC32.calculate(data));
    }
}
