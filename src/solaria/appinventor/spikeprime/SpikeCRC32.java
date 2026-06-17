package solaria.appinventor.spikeprime;

/**
 * CRC32 checksum for the LEGO SPIKE Prime 3.x BLE protocol.
 *
 * Algorithm: standard CRC-32/ISO-HDLC (reflected, polynomial 0xEDB88320),
 * identical to zlib/zip CRC32 — but with two protocol-specific rules:
 *
 *   1. Data is zero-padded to 4-byte alignment before the checksum is computed.
 *   2. A running (chunked) CRC is supported: pass the previous result as `seed`
 *      and the internal state resumes from where it left off.  The first call
 *      always uses seed = 0 (standard init).
 *
 * Usage in the upload flow:
 *   long fileCrc   = SpikeCRC32.calculate(entireProgram);           // StartFileUploadRequest
 *   long running   = SpikeCRC32.calculate(chunk1, 0);               // TransferChunkRequest #1
 *   running        = SpikeCRC32.calculate(chunk2, running);         // TransferChunkRequest #2
 *   ...
 *
 * Wire format: CRC value is sent little-endian (use toByteArrayLE).
 */
public class SpikeCRC32 {

    // Pre-computed reflected CRC32 lookup table (polynomial 0xEDB88320)
    private static final long[] TABLE = buildTable();

    private static long[] buildTable() {
        long[] table = new long[256];
        for (int n = 0; n < 256; n++) {
            long c = n & 0xFFFFFFFFL;
            for (int k = 0; k < 8; k++) {
                c = ((c & 1L) != 0) ? (0xEDB88320L ^ (c >>> 1)) : (c >>> 1);
            }
            table[n] = c;
        }
        return table;
    }

    /**
     * Calculate the CRC32 of data with an optional running seed.
     *
     * @param data the bytes to checksum (will be zero-padded to 4-byte alignment)
     * @param seed 0 for the first chunk; the previous return value for subsequent chunks
     * @return unsigned 32-bit CRC as a long (0 – 0xFFFFFFFFL)
     */
    public static long calculate(byte[] data, long seed) {
        byte[] padded = padTo4Alignment(data);
        // Un-finalize the previous CRC value to restore internal state
        long state = (seed ^ 0xFFFFFFFFL) & 0xFFFFFFFFL;
        for (byte b : padded) {
            state = (state >>> 8) ^ TABLE[(int)((state ^ (b & 0xFFL)) & 0xFF)];
        }
        return (state ^ 0xFFFFFFFFL) & 0xFFFFFFFFL;
    }

    /**
     * Calculate the CRC32 of data (seed = 0, i.e. standard CRC32 with padding).
     */
    public static long calculate(byte[] data) {
        return calculate(data, 0L);
    }

    /**
     * Return the CRC32 as a 4-byte little-endian array, ready to embed in a
     * StartFileUploadRequest or TransferChunkRequest payload.
     */
    public static byte[] toByteArrayLE(long crc) {
        return new byte[]{
            (byte)( crc        & 0xFF),
            (byte)((crc >>  8) & 0xFF),
            (byte)((crc >> 16) & 0xFF),
            (byte)((crc >> 24) & 0xFF)
        };
    }

    // -------------------------------------------------------------------------
    // Backward-compatible aliases used by the rest of the legospike package
    // -------------------------------------------------------------------------

    /** @deprecated Use {@link #calculate(byte[])} */
    public static long calculateCRC32(byte[] data) {
        return calculate(data);
    }

    /** @deprecated Use {@link #toByteArrayLE(long)} */
    public static byte[] calculateCRC32Bytes(byte[] data) {
        return toByteArrayLE(calculate(data));
    }

    // -------------------------------------------------------------------------

    /**
     * Zero-pad data to the next multiple of 4 bytes.
     * If data is already aligned, returns a copy (never mutates the input).
     */
    static byte[] padTo4Alignment(byte[] data) {
        int rem = data.length % 4;
        if (rem == 0) {
            // Return a copy so callers can't mutate the internal padded buffer
            byte[] copy = new byte[data.length];
            System.arraycopy(data, 0, copy, 0, data.length);
            return copy;
        }
        byte[] padded = new byte[data.length + (4 - rem)];
        System.arraycopy(data, 0, padded, 0, data.length);
        // remaining bytes are already 0 (Java default)
        return padded;
    }
}
