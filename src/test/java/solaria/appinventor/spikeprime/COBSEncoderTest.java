package solaria.appinventor.spikeprime;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies COBSEncoder.pack() against the three authoritative test vectors
 * from the LEGO official test_cobs.py (reproduced in docs/deep_analysis/04_cobs_test_vectors.md).
 *
 * Also checks that unpack(pack(x)) round-trips correctly.
 */
public class COBSEncoderTest {

    // ------------------------------------------------------------------
    // Test Case 1 – contains 0x00, 0x01, 0x02 bytes that must be escaped
    // ------------------------------------------------------------------
    private static final byte[] TC1_INPUT = hex(
        "00 01 02 03 04 05 06 00 01 02 03 04 05 54 EA 36 00 2D 17 0C"
    );
    private static final byte[] TC1_EXPECTED_PACK = hex(
        "00 54 A8 04 00 07 06 05 54 A8 0A 00 07 06 57 E9 35 05 2E 14 0F 02"
    );

    // ------------------------------------------------------------------
    // Test Case 2 – no bytes <= 0x02, so no escaping needed
    // ------------------------------------------------------------------
    private static final byte[] TC2_INPUT = hex(
        "FF FE DF D5 7D AF 64 61 36 15 41 2D"
    );
    private static final byte[] TC2_EXPECTED_PACK = hex(
        "0C FC FD DC D6 7E AC 67 62 35 16 42 2E 02"
    );

    // ------------------------------------------------------------------
    // Test Case 3 – complex 170-byte input, expected output 180 bytes
    // (computed from the LEGO official cobs.py reference implementation)
    // ------------------------------------------------------------------
    private static final byte[] TC3_INPUT = hex(
        "0A 03 00 3D 5B 97 00 B9 D9 57 70 C3 DD CF D8 28 " +
        "3F DC FD 2A F8 55 C3 AF 06 7E B5 32 17 AE FA FF " +
        "03 B7 1E E0 0E 02 C7 56 39 E3 00 F2 EA FF C2 F3 " +
        "6B A2 69 EB FB B1 4D 49 5D BB 7A 95 EB AB D5 07 " +
        "5D B1 4F B3 2B F4 00 31 F3 0A 2E D3 12 62 6B 45 " +
        "86 8A C4 13 86 60 5F 8C 36 95 BB 95 1B 46 D8 4F " +
        "75 05 7B ED F9 C4 CF A7 72 36 E7 A6 D5 CD CB 76 " +
        "3D E0 76 59 6B 2C 0B 8D 44 6C 17 5B 19 12 47 2A " +
        "32 D4 97 4A 4C 88 96 98 1C 2D 91 BE AC E0 81 A3 " +
        "52 A2 ED B5 47 6F 5C 9A B2 D0 00 65 6C 50 0B AD " +
        "21 5E 05 FD B7 C0 0E D7 16 DA 7F F5 29 75 6B 1F 75 2C"
    );
    private static final byte[] TC3_EXPECTED_PACK = hex(
        "06 09 00 05 3E 58 94 CA BA DA 54 73 C0 DE CC DB " +
        "2B 3C DF FE 29 FB 56 C0 AC 05 7D B6 31 14 AD F9 " +
        "FC 00 B4 1D E3 0D 04 C4 55 3A E0 1D F1 E9 FC C1 " +
        "F0 68 A1 6A E8 F8 B2 4E 4A 5E B8 79 96 E8 A8 D6 " +
        "04 5E B2 4C B0 28 F7 55 32 F0 09 2D D0 11 61 68 " +
        "46 85 89 C7 10 85 63 5C 8F 35 96 B8 96 18 45 DB " +
        "4C 76 06 78 EE FA C7 CC A4 71 35 E4 A5 D6 CE C8 " +
        "75 3E E3 75 5A 68 2F 08 8E 47 6F 14 58 1A 11 44 " +
        "29 31 D7 94 49 4F 8B 95 9B 1F 2E 92 BD AF E3 82 " +
        "A0 51 A1 EE B6 44 6C 5F 99 B1 D3 19 66 6F 53 08 " +
        "AE 22 5D 06 FE B4 C3 0D D4 15 D9 7C F6 2A 76 68 " +
        "1C 76 2F 02"
    );

    // ------------------------------------------------------------------

    @Test
    public void testPack_TC1_containsEscapableBytes() {
        byte[] actual = COBSEncoder.pack(TC1_INPUT);
        assertArrayEquals("TC1 pack() mismatch", TC1_EXPECTED_PACK, actual);
    }

    @Test
    public void testPack_TC2_noEscapableBytes() {
        byte[] actual = COBSEncoder.pack(TC2_INPUT);
        assertArrayEquals("TC2 pack() mismatch", TC2_EXPECTED_PACK, actual);
    }

    @Test
    public void testPack_TC3_complex170Bytes() {
        byte[] actual = COBSEncoder.pack(TC3_INPUT);
        assertEquals("TC3 pack() output length should be 180", 180, actual.length);
        assertArrayEquals("TC3 pack() mismatch", TC3_EXPECTED_PACK, actual);
    }

    @Test
    public void testRoundTrip_TC1() {
        assertArrayEquals("TC1 unpack(pack(x)) != x", TC1_INPUT, COBSEncoder.unpack(COBSEncoder.pack(TC1_INPUT)));
    }

    @Test
    public void testRoundTrip_TC2() {
        assertArrayEquals("TC2 unpack(pack(x)) != x", TC2_INPUT, COBSEncoder.unpack(COBSEncoder.pack(TC2_INPUT)));
    }

    @Test
    public void testRoundTrip_TC3() {
        assertArrayEquals("TC3 unpack(pack(x)) != x", TC3_INPUT, COBSEncoder.unpack(COBSEncoder.pack(TC3_INPUT)));
    }

    @Test
    public void testPackOutputEndsWithDelimiter() {
        byte[] packed = COBSEncoder.pack(TC1_INPUT);
        assertEquals("pack() output must end with DELIMITER (0x02)",
            COBSEncoder.DELIMITER, packed[packed.length - 1] & 0xFF);
    }

    @Test
    public void testPackOutputContainsNoDelimiterInBody() {
        for (byte[] input : new byte[][]{TC1_INPUT, TC2_INPUT, TC3_INPUT}) {
            byte[] packed = COBSEncoder.pack(input);
            // Every byte except the final one must be != DELIMITER
            for (int i = 0; i < packed.length - 1; i++) {
                int b = packed[i] & 0xFF;
                assertNotEquals("pack() body must not contain DELIMITER (0x02) at index " + i,
                    COBSEncoder.DELIMITER, b);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helper: parse a hex string like "00 1A FF ..." into a byte array
    // ------------------------------------------------------------------
    private static byte[] hex(String hexStr) {
        String[] tokens = hexStr.trim().split("\\s+");
        byte[] result = new byte[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = (byte) Integer.parseInt(tokens[i], 16);
        }
        return result;
    }
}
