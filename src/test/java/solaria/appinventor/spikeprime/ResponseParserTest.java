package solaria.appinventor.spikeprime;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies ResponseParser against manually-constructed wire bytes.
 *
 * InfoResponse byte layout (struct {@code <BBBHBBHHHHH}, 17 bytes):
 *   [0]        0x01  msgId
 *   [1]        B     rpcMajor
 *   [2]        B     rpcMinor
 *   [3–4]      H LE  rpcBuild
 *   [5]        B     fwMajor
 *   [6]        B     fwMinor
 *   [7–8]      H LE  fwBuild
 *   [9–10]     H LE  maxPacketSize
 *   [11–12]    H LE  maxMessageSize
 *   [13–14]    H LE  maxChunkSize
 *   [15–16]    H LE  productGroupDevice
 */
public class ResponseParserTest {

    // ------------------------------------------------------------------
    // Synthetic InfoResponse with carefully chosen values so every
    // uint16 LE pair is non-trivial (both bytes non-zero where possible).
    //
    //   rpcMajor          =   3         → 0x03
    //   rpcMinor          =   5         → 0x05
    //   rpcBuild          = 258 (0x0102)→ LE: 0x02, 0x01
    //   fwMajor           =   1         → 0x01
    //   fwMinor           =   0         → 0x00
    //   fwBuild           = 515 (0x0203)→ LE: 0x03, 0x02
    //   maxPacketSize     = 400 (0x0190)→ LE: 0x90, 0x01
    //   maxMessageSize    = 512 (0x0200)→ LE: 0x00, 0x02
    //   maxChunkSize      = 445 (0x01BD)→ LE: 0xBD, 0x01  (etomasfe hardcoded value)
    //   productGroupDevice=  64 (0x0040)→ LE: 0x40, 0x00
    // ------------------------------------------------------------------
    private static final byte[] VALID_INFO = {
        (byte)0x01,               // msgId
        (byte)0x03,               // rpcMajor = 3
        (byte)0x05,               // rpcMinor = 5
        (byte)0x02, (byte)0x01,   // rpcBuild = 258
        (byte)0x01,               // fwMajor  = 1
        (byte)0x00,               // fwMinor  = 0
        (byte)0x03, (byte)0x02,   // fwBuild  = 515
        (byte)0x90, (byte)0x01,   // maxPacketSize  = 400
        (byte)0x00, (byte)0x02,   // maxMessageSize = 512
        (byte)0xBD, (byte)0x01,   // maxChunkSize   = 445
        (byte)0x40, (byte)0x00    // productGroupDevice = 64
    };

    // ------------------------------------------------------------------
    // parseInfoResponse — happy path
    // ------------------------------------------------------------------

    @Test
    public void parseInfoResponse_returnsNonNull_forValidData() {
        assertNotNull(ResponseParser.parseInfoResponse(VALID_INFO));
    }

    @Test
    public void parseInfoResponse_rpcMajor() {
        assertEquals(3, ResponseParser.parseInfoResponse(VALID_INFO).rpcMajor);
    }

    @Test
    public void parseInfoResponse_rpcMinor() {
        assertEquals(5, ResponseParser.parseInfoResponse(VALID_INFO).rpcMinor);
    }

    @Test
    public void parseInfoResponse_rpcBuild_uint16LE() {
        // 0x0102 little-endian stored as [0x02, 0x01]
        assertEquals(258, ResponseParser.parseInfoResponse(VALID_INFO).rpcBuild);
    }

    @Test
    public void parseInfoResponse_fwMajor() {
        assertEquals(1, ResponseParser.parseInfoResponse(VALID_INFO).fwMajor);
    }

    @Test
    public void parseInfoResponse_fwMinor() {
        assertEquals(0, ResponseParser.parseInfoResponse(VALID_INFO).fwMinor);
    }

    @Test
    public void parseInfoResponse_fwBuild_uint16LE() {
        // 0x0203 LE → [0x03, 0x02]
        assertEquals(515, ResponseParser.parseInfoResponse(VALID_INFO).fwBuild);
    }

    @Test
    public void parseInfoResponse_maxPacketSize_uint16LE() {
        // 400 = 0x0190, LE → [0x90, 0x01]
        assertEquals(400, ResponseParser.parseInfoResponse(VALID_INFO).maxPacketSize);
    }

    @Test
    public void parseInfoResponse_maxMessageSize_uint16LE() {
        // 512 = 0x0200, LE → [0x00, 0x02]
        assertEquals(512, ResponseParser.parseInfoResponse(VALID_INFO).maxMessageSize);
    }

    @Test
    public void parseInfoResponse_maxChunkSize_uint16LE() {
        // 445 = 0x01BD, LE → [0xBD, 0x01]  (etomasfe hardcoded chunk size)
        assertEquals(445, ResponseParser.parseInfoResponse(VALID_INFO).maxChunkSize);
    }

    @Test
    public void parseInfoResponse_productGroupDevice_uint16LE() {
        // 64 = 0x0040, LE → [0x40, 0x00]
        assertEquals(64, ResponseParser.parseInfoResponse(VALID_INFO).productGroupDevice);
    }

    // ------------------------------------------------------------------
    // parseInfoResponse — invalid input guards
    // ------------------------------------------------------------------

    @Test
    public void parseInfoResponse_returnsNull_forNull() {
        assertNull(ResponseParser.parseInfoResponse(null));
    }

    @Test
    public void parseInfoResponse_returnsNull_forTooShort() {
        byte[] short16 = new byte[16];         // one byte shy of the 17-byte minimum
        short16[0] = (byte) 0x01;
        assertNull(ResponseParser.parseInfoResponse(short16));
    }

    @Test
    public void parseInfoResponse_returnsNull_forEmptyArray() {
        assertNull(ResponseParser.parseInfoResponse(new byte[0]));
    }

    @Test
    public void parseInfoResponse_returnsNull_forWrongMsgId() {
        byte[] wrongId = VALID_INFO.clone();
        wrongId[0] = (byte) 0x00;              // InfoRequest ID, not InfoResponse
        assertNull(ResponseParser.parseInfoResponse(wrongId));
    }

    @Test
    public void parseInfoResponse_acceptsExtraTrailingBytes() {
        // Hub may send longer messages in future firmware; parser must not choke
        byte[] longer = new byte[VALID_INFO.length + 4];
        System.arraycopy(VALID_INFO, 0, longer, 0, VALID_INFO.length);
        ResponseParser.InfoResponse r = ResponseParser.parseInfoResponse(longer);
        assertNotNull(r);
        assertEquals("maxChunkSize still correct with extra bytes", 445, r.maxChunkSize);
    }

    // ------------------------------------------------------------------
    // parseStatusResponse
    // ------------------------------------------------------------------

    @Test
    public void parseStatusResponse_trueWhenStatusByteIsZero() {
        // 0x00 at byte[1] means acknowledged/success
        byte[] ack = {(byte)0x0D, (byte)0x00};   // StartFileUploadResponse, success
        assertTrue(ResponseParser.parseStatusResponse(ack));
    }

    @Test
    public void parseStatusResponse_falseWhenStatusByteIsNonZero() {
        byte[] nack = {(byte)0x0D, (byte)0x01};  // StartFileUploadResponse, failure
        assertFalse(ResponseParser.parseStatusResponse(nack));
    }

    @Test
    public void parseStatusResponse_falseForNull() {
        assertFalse(ResponseParser.parseStatusResponse(null));
    }

    @Test
    public void parseStatusResponse_falseForOneByte() {
        assertFalse(ResponseParser.parseStatusResponse(new byte[]{(byte)0x11}));
    }

    @Test
    public void parseStatusResponse_worksForAllKnownStatusMessages() {
        // TransferChunkResponse (0x11) success
        assertTrue(ResponseParser.parseStatusResponse(
            new byte[]{(byte)0x11, (byte)0x00}));
        // ProgramFlowResponse (0x1F) success
        assertTrue(ResponseParser.parseStatusResponse(
            new byte[]{(byte)0x1F, (byte)0x00}));
        // ClearSlotResponse (0x47) failure
        assertFalse(ResponseParser.parseStatusResponse(
            new byte[]{(byte)0x47, (byte)0x01}));
    }

    // ------------------------------------------------------------------
    // Endianness sanity: max-value uint16 (0xFFFF = 65535)
    // ------------------------------------------------------------------

    @Test
    public void parseInfoResponse_maxUint16_parsedCorrectly() {
        byte[] data = VALID_INFO.clone();
        // overwrite maxPacketSize bytes [9–10] with 0xFF, 0xFF
        data[9]  = (byte) 0xFF;
        data[10] = (byte) 0xFF;
        assertEquals(65535, ResponseParser.parseInfoResponse(data).maxPacketSize);
    }
}
