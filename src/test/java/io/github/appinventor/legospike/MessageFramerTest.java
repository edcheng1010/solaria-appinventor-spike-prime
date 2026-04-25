package io.github.appinventor.legospike;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies MessageFramer against:
 *   (a) round-trip: unpack(pack(data)) == data for multiple inputs
 *   (b) pack output starts with 0x01 and ends with 0x02
 *   (c) pack body contains no 0x02 bytes except the final one
 *
 * Exact packed bytes are cross-checked against the reference Python algorithm
 * (cobs.py + etomasfe framing) to catch any regression in the pipeline.
 */
public class MessageFramerTest {

    // ------------------------------------------------------------------
    // (a) round-trip tests
    // ------------------------------------------------------------------

    @Test
    public void roundTrip_simpleAscii() {
        byte[] data = "Hello".getBytes();
        assertArrayEquals("round-trip 'Hello'", data, MessageFramer.unpack(MessageFramer.pack(data)));
    }

    @Test
    public void roundTrip_containsAllEscapableBytes() {
        // Contains 0x00, 0x01, 0x02 — all three COBS escape targets
        byte[] data = {0x00, 0x01, 0x02, 0x03};
        assertArrayEquals("round-trip [00 01 02 03]", data, MessageFramer.unpack(MessageFramer.pack(data)));
    }

    @Test
    public void roundTrip_tunnelMessageExample() {
        // Section 5 protocol-doc example: TunnelMessage "A+050B+050"
        byte[] data = {0x32, 0x0a, 0x00, 0x41, 0x2b, 0x30, 0x35, 0x30,
                       0x42, 0x2b, 0x30, 0x35, 0x30};
        assertArrayEquals("round-trip TunnelMessage", data, MessageFramer.unpack(MessageFramer.pack(data)));
    }

    @Test
    public void roundTrip_singleZeroByte() {
        byte[] data = {0x00};
        assertArrayEquals("round-trip [00]", data, MessageFramer.unpack(MessageFramer.pack(data)));
    }

    @Test
    public void roundTrip_singleDelimiterByte() {
        byte[] data = {0x02};
        assertArrayEquals("round-trip [02]", data, MessageFramer.unpack(MessageFramer.pack(data)));
    }

    @Test
    public void roundTrip_longData() {
        // 170-byte COBS test-vector input also works as a framing round-trip
        byte[] data = hex(
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
        assertArrayEquals("round-trip 170-byte data", data, MessageFramer.unpack(MessageFramer.pack(data)));
    }

    // ------------------------------------------------------------------
    // (b) pack output starts with 0x01 and ends with 0x02
    // ------------------------------------------------------------------

    @Test
    public void packOutput_startsWithFrameStart() {
        for (byte[] input : testInputs()) {
            byte[] packed = MessageFramer.pack(input);
            assertEquals("pack() must start with 0x01",
                0x01, packed[0] & 0xFF);
        }
    }

    @Test
    public void packOutput_endsWithFrameEnd() {
        for (byte[] input : testInputs()) {
            byte[] packed = MessageFramer.pack(input);
            assertEquals("pack() must end with 0x02",
                0x02, packed[packed.length - 1] & 0xFF);
        }
    }

    // ------------------------------------------------------------------
    // (c) pack body contains no 0x02 bytes except the final one
    // ------------------------------------------------------------------

    @Test
    public void packBody_containsNoDelimiterExceptFinal() {
        for (byte[] input : testInputs()) {
            byte[] packed = MessageFramer.pack(input);
            // Body = bytes[1 .. len-2] (strip 0x01 header and 0x02 trailer)
            for (int i = 1; i < packed.length - 1; i++) {
                int b = packed[i] & 0xFF;
                assertNotEquals(
                    "pack() body must not contain 0x02 at index " + i
                        + " (input length " + input.length + ")",
                    0x02, b);
            }
        }
    }

    @Test
    public void packBody_containsNoFrameStartInBody() {
        // 0x01 must also not appear in the body — the COBS guarantee extends to it
        for (byte[] input : testInputs()) {
            byte[] packed = MessageFramer.pack(input);
            for (int i = 1; i < packed.length - 1; i++) {
                int b = packed[i] & 0xFF;
                assertNotEquals(
                    "pack() body must not contain 0x01 at index " + i,
                    0x01, b);
            }
        }
    }

    // ------------------------------------------------------------------
    // Exact byte output — cross-checked against Python reference
    // ------------------------------------------------------------------

    @Test
    public void exactBytes_tunnelMessage() {
        // Section 5 of BYTE_LEVEL_PROTOCOL.md: TunnelMessage "A+050B+050"
        // Verified against Python cobs.py + framing
        byte[] input    = {0x32, 0x0a, 0x00, 0x41, 0x2b, 0x30, 0x35, 0x30,
                           0x42, 0x2b, 0x30, 0x35, 0x30};
        byte[] expected = hex("01 06 31 09 0E 42 28 33 36 33 41 28 33 36 33 02");
        assertArrayEquals("exact pack bytes for TunnelMessage example", expected, MessageFramer.pack(input));
    }

    @Test
    public void exactBytes_hello() {
        byte[] input    = "Hello".getBytes();
        byte[] expected = hex("01 0B 4B 66 6F 6F 6C 02");
        assertArrayEquals("exact pack bytes for 'Hello'", expected, MessageFramer.pack(input));
    }

    @Test
    public void exactBytes_allEscapableBytes() {
        byte[] input    = {0x00, 0x01, 0x02, 0x03};
        byte[] expected = hex("01 00 54 A8 07 00 02");
        assertArrayEquals("exact pack bytes for [00 01 02 03]", expected, MessageFramer.pack(input));
    }

    @Test
    public void exactBytes_singleDelimiter() {
        byte[] input    = {0x02};
        byte[] expected = hex("01 A8 00 02");
        assertArrayEquals("exact pack bytes for [02]", expected, MessageFramer.pack(input));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static byte[][] testInputs() {
        return new byte[][]{
            "Hello".getBytes(),
            new byte[]{0x00, 0x01, 0x02, 0x03},
            new byte[]{0x32, 0x0a, 0x00, 0x41, 0x2b, 0x30, 0x35, 0x30,
                       0x42, 0x2b, 0x30, 0x35, 0x30},
            new byte[]{0x02},
            new byte[]{(byte)0xFF, (byte)0xFE, (byte)0xDF},
        };
    }

    private static byte[] hex(String hexStr) {
        String[] tokens = hexStr.trim().split("\\s+");
        byte[] result = new byte[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            result[i] = (byte) Integer.parseInt(tokens[i], 16);
        }
        return result;
    }
}
