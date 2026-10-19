// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.blocks.impl;

import static com.hedera.node.app.hapi.utils.CommonUtils.sha256DigestOrThrow;
import static com.hedera.node.app.hapi.utils.CommonUtils.sha384DigestOrThrow;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class BlockImplUtilsTest {
    @Test
    void testCombineNormalCase() throws NoSuchAlgorithmException {
        byte[] leftHash = MessageDigest.getInstance("SHA-256").digest("left".getBytes());
        byte[] rightHash = MessageDigest.getInstance("SHA-256").digest("right".getBytes());
        byte[] combinedHash = BlockImplUtils.combine(sha384DigestOrThrow(), leftHash, rightHash);

        assertNotNull(combinedHash);
        assertEquals(48, combinedHash.length); // no digest specified defaults to SHA-384, 48-byte hash
    }

    @Test
    void testCombineEmptyHashes() throws NoSuchAlgorithmException {
        byte[] emptyHash = MessageDigest.getInstance("SHA-256").digest(new byte[0]);
        byte[] combinedHash = BlockImplUtils.combine(sha384DigestOrThrow(), emptyHash, emptyHash);

        assertNotNull(combinedHash);
        assertEquals(48, combinedHash.length); // no digest specified defaults to SHA-384, 48-byte hash
    }

    @Test
    void testCombineDifferentHashes() throws NoSuchAlgorithmException {
        byte[] leftHash = MessageDigest.getInstance("SHA-256").digest("left".getBytes());
        byte[] rightHash = MessageDigest.getInstance("SHA-256").digest("right".getBytes());
        byte[] combinedHash1 = BlockImplUtils.combine(sha384DigestOrThrow(), leftHash, rightHash);
        byte[] combinedHash2 = BlockImplUtils.combine(sha384DigestOrThrow(), rightHash, leftHash);

        assertNotNull(combinedHash1);
        assertNotNull(combinedHash2);
        assertNotEquals(new String(combinedHash1), new String(combinedHash2));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCombineWithNull() {
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.combine(sha384DigestOrThrow(), null, new byte[0]));
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.combine(sha384DigestOrThrow(), new byte[0], null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashLeafByteArrayWithNullParamsThrows() {
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashLeaf(sha384DigestOrThrow(), (byte[]) null));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashLeaf(sha256DigestOrThrow(), (byte[]) null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashLeafBytesWithNullParamsThrows() {
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashLeaf(sha384DigestOrThrow(), (Bytes) null));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashLeaf(sha256DigestOrThrow(), (Bytes) null));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashLeaf(null, Bytes.EMPTY));
    }

    @Test
    void hashLeafAppendsLeafPrefix() throws NoSuchAlgorithmException {
        final Bytes expected = Bytes.fromBase64("PC20jsnX7c+XYzhwCGLaSo/loMpfqYLL6vxvzycqJD0=");
        final Bytes expectedDefaultDigest = Bytes.fromHex(
                "cf40581b3ee9cdc254dad0000fa8e588f4fd44a907fed453ac697415f1bb5a017c6eb99832f407a08503e05a75e3c302");

        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final Bytes data = Bytes.fromHex("2a120816120c08d9d5d2c90610ffa8bba5033a00");
        digest.update(BlockImplUtils.LEAF_PREFIX);
        final Bytes computed = Bytes.wrap(digest.digest(data.toByteArray()));
        // Precondition: verify expected matches computed value
        assertEquals(expected, computed);

        // Test the Bytes overload (no digest specified defaults to SHA-384)
        final Bytes actual = BlockImplUtils.hashLeaf(sha384DigestOrThrow(), data);
        assertEquals(expectedDefaultDigest, actual);

        // Test the byte array overload (no digest specified defaults to SHA-384)
        final byte[] actualArray = BlockImplUtils.hashLeaf(sha384DigestOrThrow(), data.toByteArray());
        assertArrayEquals(expectedDefaultDigest.toByteArray(), actualArray);

        // Test byte array + digest overload
        digest.reset(); // Not necessary, but specifies intent
        final byte[] actualWithDigest = BlockImplUtils.hashLeaf(digest, data.toByteArray());
        assertArrayEquals(expected.toByteArray(), actualWithDigest);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashInternalNodeBytesWithNullParamsThrows() {
        assertThrows(
                NullPointerException.class,
                () -> BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), null, Bytes.EMPTY));
        assertThrows(
                NullPointerException.class,
                () -> BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), Bytes.EMPTY, (Bytes) null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashInternalNodeByteArrayWithNullParamsThrows() {
        assertThrows(
                NullPointerException.class,
                () -> BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), (byte[]) null, new byte[0]));
        assertThrows(
                NullPointerException.class,
                () -> BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), new byte[0], null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashInternalNodeMixedWithNullParamsThrows() {
        assertThrows(
                NullPointerException.class,
                () -> BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), (Bytes) null, new byte[0]));
        assertThrows(
                NullPointerException.class,
                () -> BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), Bytes.EMPTY, (byte[]) null));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashInternalNodeWithDigestWithNullParamsThrows() {
        final var digest = sha256DigestOrThrow();
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashInternalNode(null, new byte[0], new byte[0]));
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.hashInternalNode(digest, (byte[]) null, new byte[0]));
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.hashInternalNode(digest, new byte[0], (byte[]) null));
    }

    @Test
    void hashInternalNodeAppendsInternalNodePrefix() {
        final Bytes expected = Bytes.fromHex("784e119f0efa9ff049e2da4370e15e2a24f2658c542322fa3805a9976b5ecbae");
        final Bytes expectedDefaultDigest = Bytes.fromHex(
                "754ceb6301824804cd0488b2ed7a32e4594302f274c8363aa6696b427b3f586438ee367ba99320320e8df2d896425cd7");

        final MessageDigest digest = sha256DigestOrThrow();
        final Bytes data1 = Bytes.fromBase64("z0BYGz7pzcJU2tAAD6jliPT9RKkH/tRTrGl0FfG7WgF8brmYMvQHoIUD4Fp148MC");
        final Bytes data2 = Bytes.fromHex(
                "877a7ee7919309a359ee656d07e42504a2ab42c16089c235de87719c5ace1f00203c07a679d653d8d20458bf6c0ed143");
        BlockImplUtils.INTERNAL_NODE_PREFIX_BYTES.writeTo(digest);
        data1.writeTo(digest);
        data2.writeTo(digest);
        final Bytes computed = Bytes.wrap(digest.digest());
        // Precondition: verify expected matches computed value
        assertEquals(expected, computed);

        // Test the Bytes overload (no digest specified defaults to SHA-384)
        final Bytes actualFromBytes = BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), data1, data2);
        assertEquals(expectedDefaultDigest, actualFromBytes);

        // Test the byte arrays overload (no digest specified defaults to SHA-384)
        final byte[] data1Array = data1.toByteArray();
        final byte[] data2Array = data2.toByteArray();
        final byte[] actualFromArrays = BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), data1Array, data2Array);
        assertArrayEquals(expectedDefaultDigest.toByteArray(), actualFromArrays);

        // Test the explicit digest overload
        digest.reset(); // Not necessary, but specifies intent
        final byte[] actual = BlockImplUtils.hashInternalNode(digest, data1Array, data2Array);
        assertArrayEquals(expected.toByteArray(), actual);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashInternalNodeBytesWithDigestThrowsOnNull() {
        final var digest = sha256DigestOrThrow();
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashInternalNode(null, Bytes.EMPTY, Bytes.EMPTY));
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.hashInternalNode(digest, (Bytes) null, Bytes.EMPTY));
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.hashInternalNode(digest, Bytes.EMPTY, (Bytes) null));
    }

    @Test
    void hashInternalNodeBytesWithDigestUsesProvidedAlgorithm() {
        final Bytes left = Bytes.wrap(new byte[] {1, 2, 3});
        final Bytes right = Bytes.wrap(new byte[] {4, 5, 6});

        final var referenceDigest = sha256DigestOrThrow();
        BlockImplUtils.INTERNAL_NODE_PREFIX_BYTES.writeTo(referenceDigest);
        left.writeTo(referenceDigest);
        right.writeTo(referenceDigest);
        final Bytes expected = Bytes.wrap(referenceDigest.digest());

        assertEquals(expected, BlockImplUtils.hashInternalNode(sha256DigestOrThrow(), left, right));
        assertEquals(32, expected.length());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void hashInternalNodeMixedWithDigestThrowsOnNull() {
        final var digest = sha256DigestOrThrow();
        assertThrows(NullPointerException.class, () -> BlockImplUtils.hashInternalNode(null, Bytes.EMPTY, new byte[0]));
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.hashInternalNode(digest, (Bytes) null, new byte[0]));
        assertThrows(
                NullPointerException.class, () -> BlockImplUtils.hashInternalNode(digest, Bytes.EMPTY, (byte[]) null));
    }

    @Test
    void hashInternalNodeMixedWithDigestUsesProvidedAlgorithm() {
        final Bytes left = Bytes.wrap(new byte[] {1, 2, 3});
        final byte[] right = new byte[] {4, 5, 6};

        final var referenceDigest = sha256DigestOrThrow();
        BlockImplUtils.INTERNAL_NODE_PREFIX_BYTES.writeTo(referenceDigest);
        left.writeTo(referenceDigest);
        final Bytes expected = Bytes.wrap(referenceDigest.digest(right));

        assertEquals(expected, BlockImplUtils.hashInternalNode(sha256DigestOrThrow(), left, right));
        assertEquals(32, expected.length());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void combineByteArraysWithDigestThrowsOnNull() {
        final var digest = sha256DigestOrThrow();
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(null, new byte[0], new byte[0]));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(digest, (byte[]) null, new byte[0]));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(digest, new byte[0], (byte[]) null));
    }

    @Test
    void combineByteArraysWithDigestUsesProvidedAlgorithm() {
        final var digest = sha256DigestOrThrow();
        final byte[] left = {1, 2, 3};
        final byte[] right = {4, 5, 6};

        final var referenceDigest = sha256DigestOrThrow();
        referenceDigest.update(left);
        final byte[] expected = referenceDigest.digest(right);

        assertArrayEquals(expected, BlockImplUtils.combine(digest, left, right));
        assertEquals(32, expected.length);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void combineBytesWithDigestThrowsOnNull() {
        final var digest = sha256DigestOrThrow();
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(null, Bytes.EMPTY, Bytes.EMPTY));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(digest, (Bytes) null, Bytes.EMPTY));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(digest, Bytes.EMPTY, (Bytes) null));
    }

    @Test
    void combineBytesWithDigestUsesProvidedAlgorithm() {
        final Bytes left = Bytes.wrap(new byte[] {1, 2, 3});
        final Bytes right = Bytes.wrap(new byte[] {4, 5, 6});

        final var referenceDigest = sha256DigestOrThrow();
        referenceDigest.update(left.toByteArray());
        final Bytes expected = Bytes.wrap(referenceDigest.digest(right.toByteArray()));

        assertEquals(expected, BlockImplUtils.combine(sha256DigestOrThrow(), left, right));
        assertEquals(32, expected.length());
    }

    @Test
    void hashedUnprefixedDoesNotMatchHashedPrefixed() {
        final var digest = sha256DigestOrThrow();
        final Bytes data = Bytes.wrap(new byte[] {9, 8, 7, 6});
        data.writeTo(digest);
        // 63d987d1c6d69751c17297f410f5b3547a65d096a8993b35bcb4f9cad054f176
        final var computedNoPrefix = Bytes.wrap(digest.digest());

        digest.update(BlockImplUtils.LEAF_PREFIX);
        // 70de4281b61ccc51ce0d1ef69cd28a4e28c2e6be36dc0be230d9d090ce07c94a
        final var computedLeafPrefix = Bytes.wrap(digest.digest(data.toByteArray()));
        // BlockImplUtils.hashLeaf(sha384DigestOrThrow(), Bytes) with no digest specified defaults to SHA-384, not
        // SHA-256
        final var actualLeafPrefix = BlockImplUtils.hashLeaf(sha384DigestOrThrow(), data);
        assertNotEquals(computedLeafPrefix, actualLeafPrefix);
        assertNotEquals(computedNoPrefix, actualLeafPrefix);

        digest.update(BlockImplUtils.INTERNAL_NODE_PREFIX);
        // The internal node hash calculation requires two inputs, so use data twice
        data.writeTo(digest);
        data.writeTo(digest);
        // f7396629d18804df928e70c1c54085a482ecba86e676349433d6eb2d357ba252
        final var computedInternalNodePrefix = Bytes.wrap(digest.digest());
        // BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), Bytes, Bytes) with no digest specified defaults to
        // SHA-384, not SHA-256
        final var actualInternalNodePrefix = BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), data, data);
        assertNotEquals(computedInternalNodePrefix, actualInternalNodePrefix);
        assertNotEquals(computedNoPrefix, actualInternalNodePrefix);

        // Test the mixed param types variant
        final var actualInternalMixedPrefix =
                BlockImplUtils.hashInternalNode(sha384DigestOrThrow(), data, data.toByteArray());
        // Only equality check needed, as previous checks already guarantee the no prefix case is different
        assertEquals(actualInternalNodePrefix, actualInternalMixedPrefix);
    }

    @Test
    void blockHashByBlockNumberSliceLengthFollowsHashSize() {
        // Three consecutive block hashes, laid out back-to-back. The caller selects the per-hash stride via
        // hashSize: 48 bytes for SHA-384 (the digestType=SHA_384 path) or 32 bytes for SHA-256 (digestType=SHA_256).
        // This is the mechanism BlockStreamInfoImpl relies on to honor the digestType setting.
        final long lastBlockNo = 5L;
        final long requestedBlockNo = 4L; // the middle of the three available hashes

        final byte[] sha384Bytes = new byte[48 * 3];
        for (int i = 0; i < sha384Bytes.length; i++) {
            sha384Bytes[i] = (byte) i;
        }
        final var sha384Slice =
                BlockImplUtils.blockHashByBlockNumber(Bytes.wrap(sha384Bytes), lastBlockNo, requestedBlockNo, 48);
        assertNotNull(sha384Slice);
        assertEquals(48L, sha384Slice.length());

        final byte[] sha256Bytes = new byte[32 * 3];
        for (int i = 0; i < sha256Bytes.length; i++) {
            sha256Bytes[i] = (byte) i;
        }
        final var sha256Slice =
                BlockImplUtils.blockHashByBlockNumber(Bytes.wrap(sha256Bytes), lastBlockNo, requestedBlockNo, 32);
        assertNotNull(sha256Slice);
        assertEquals(32L, sha256Slice.length());

        // The middle slice starts at a different offset under each stride (48 vs 32), so the bytes differ too.
        assertNotEquals(sha384Slice, sha256Slice);
    }

    @Test
    void leftPadToPrependsZeroBytesForShorterHash() {
        final byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 1); // non-zero so padding is distinguishable
        }
        final var padded = BlockImplUtils.leftPadTo(Bytes.wrap(raw), 48);
        assertEquals(48L, padded.length());
        final byte[] out = padded.toByteArray();
        for (int i = 0; i < 16; i++) {
            assertEquals(0, out[i], "leading byte " + i + " should be zero padding");
        }
        assertArrayEquals(raw, java.util.Arrays.copyOfRange(out, 16, 48));
    }

    @Test
    void leftPadToReturnsHashUnchangedWhenAlreadyStoredSize() {
        final byte[] raw = new byte[48];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 1);
        }
        final var padded = BlockImplUtils.leftPadTo(Bytes.wrap(raw), 48);
        assertEquals(48L, padded.length());
        assertArrayEquals(raw, padded.toByteArray());
    }

    @Test
    void leftPadToThrowsWhenHashLongerThanStoredSize() {
        final var tooLong = Bytes.wrap(new byte[64]);
        assertThrows(IllegalArgumentException.class, () -> BlockImplUtils.leftPadTo(tooLong, 48));
    }

    @Test
    void stripLeadingPadReturnsRealHashWhenLeadingBytesAreZero() {
        final byte[] stored = new byte[48];
        for (int i = 16; i < 48; i++) {
            stored[i] = (byte) (i + 1); // 16 leading zeros, then a 32-byte real hash
        }
        final var real = BlockImplUtils.stripLeadingPad(Bytes.wrap(stored), 32);
        assertEquals(32L, real.length());
        assertArrayEquals(java.util.Arrays.copyOfRange(stored, 16, 48), real.toByteArray());
    }

    @Test
    void stripLeadingPadReturnsGenuineFullSizeEntryUnchanged() {
        // A genuine 48-byte SHA-384 hash whose leading bytes are non-zero must not be truncated to 32 bytes.
        final byte[] genuine = new byte[48];
        for (int i = 0; i < genuine.length; i++) {
            genuine[i] = (byte) (i + 1);
        }
        final var result = BlockImplUtils.stripLeadingPad(Bytes.wrap(genuine), 32);
        assertEquals(48L, result.length());
        assertArrayEquals(genuine, result.toByteArray());
    }

    @Test
    void stripLeadingPadReturnsEntryUnchangedWhenAlreadyRealSize() {
        final byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) (i + 1);
        }
        final var result = BlockImplUtils.stripLeadingPad(Bytes.wrap(raw), 32);
        assertEquals(32L, result.length());
        assertArrayEquals(raw, result.toByteArray());
    }

    // --- realBlockHashByBlockNumber: stride derived from entry count, reads real (unpadded) hash ---

    private static byte[] seq(final int len, final int start) {
        final byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            out[i] = (byte) (start + i + 1); // non-zero so padding is distinguishable
        }
        return out;
    }

    private static byte[] padded48(final byte[] real32) {
        final byte[] out = new byte[48];
        System.arraycopy(real32, 0, out, 16, 32);
        return out;
    }

    @Test
    void realBlockHashByBlockNumberReadsNativeWindowAtActiveStride() {
        // Three native 32-byte entries for blocks 0,1,2 (post-compaction / all-native era). The stride is derived
        // from the entry count, which the design pins to min(lastBlockNo + 1, maxHashes).
        final byte[] b0 = seq(32, 0);
        final byte[] b1 = seq(32, 100);
        final byte[] b2 = seq(32, 200);
        final byte[] window = new byte[96];
        System.arraycopy(b0, 0, window, 0, 32);
        System.arraycopy(b1, 0, window, 32, 32);
        System.arraycopy(b2, 0, window, 64, 32);

        final var got = BlockImplUtils.realBlockHashByBlockNumber(Bytes.wrap(window), 2L, 1L, 256, 32);
        assertNotNull(got);
        assertEquals(32L, got.length());
        assertArrayEquals(b1, got.toByteArray());
    }

    @Test
    void realBlockHashByBlockNumberStripsPaddingFromAllPaddedWindow() {
        // Three padded 48-byte entries (each a padded 32-byte hash) for blocks 0,1,2.
        final byte[] r0 = seq(32, 0);
        final byte[] r1 = seq(32, 100);
        final byte[] r2 = seq(32, 200);
        final byte[] window = new byte[144];
        System.arraycopy(padded48(r0), 0, window, 0, 48);
        System.arraycopy(padded48(r1), 0, window, 48, 48);
        System.arraycopy(padded48(r2), 0, window, 96, 48);

        final var got = BlockImplUtils.realBlockHashByBlockNumber(Bytes.wrap(window), 2L, 0L, 256, 32);
        assertNotNull(got);
        assertEquals(32L, got.length());
        assertArrayEquals(r0, got.toByteArray());
    }

    @Test
    void realBlockHashByBlockNumberReadsMixedWindowGenuineAndPadded() {
        // Mixed 48-byte era: genuine SHA-384 hash (non-zero leading bytes) at block 0, padded 32 at block 1.
        final byte[] genuine48 = seq(48, 0); // leading bytes non-zero -> genuine
        final byte[] real1 = seq(32, 200);
        final byte[] window = new byte[96];
        System.arraycopy(genuine48, 0, window, 0, 48);
        System.arraycopy(padded48(real1), 0, window, 48, 48);

        final var genuine = BlockImplUtils.realBlockHashByBlockNumber(Bytes.wrap(window), 1L, 0L, 256, 32);
        assertNotNull(genuine);
        assertEquals(48L, genuine.length());
        assertArrayEquals(genuine48, genuine.toByteArray());

        final var padded = BlockImplUtils.realBlockHashByBlockNumber(Bytes.wrap(window), 1L, 1L, 256, 32);
        assertNotNull(padded);
        assertEquals(32L, padded.length());
        assertArrayEquals(real1, padded.toByteArray());
    }

    @Test
    void realBlockHashByBlockNumberReturnsNullWhenOutOfRange() {
        final byte[] window = new byte[64]; // two native 32-byte entries for blocks 0,1
        assertEquals(null, BlockImplUtils.realBlockHashByBlockNumber(Bytes.wrap(window), 1L, 2L, 256, 32));
        assertEquals(null, BlockImplUtils.realBlockHashByBlockNumber(Bytes.wrap(window), 1L, -1L, 256, 32));
    }

    // --- appendTrailingBlockHash: uniform window, one-time compaction once no genuine hashes remain ---

    @Test
    void appendTrailingBlockHashPadsNewEntryWhileGenuineRemains() {
        // Mixed era, window not yet full: one genuine 48-byte entry for block 0, appending native hash for block 1.
        final byte[] genuine48 = seq(48, 0);
        final byte[] window = genuine48.clone(); // one entry, newBlockNo=1 so countExisting=1 matches one entry
        final byte[] newNative = seq(32, 200);

        final var result =
                BlockImplUtils.appendTrailingBlockHash(Bytes.wrap(newNative), Bytes.wrap(window), 256, 1L, 48, 32);
        assertEquals(96L, result.length()); // grew by one 48-byte padded entry
        final byte[] out = result.toByteArray();
        // genuine entry unchanged
        assertArrayEquals(genuine48, java.util.Arrays.copyOfRange(out, 0, 48));
        // new entry left-padded to 48
        for (int i = 48; i < 64; i++) {
            assertEquals(0, out[i], "new entry leading byte should be zero padding");
        }
        assertArrayEquals(newNative, java.util.Arrays.copyOfRange(out, 64, 96));
    }

    @Test
    void appendTrailingBlockHashRollsOffWhileGenuineRemainsAtFullWindow() {
        // Full window of maxHashes=2 padded 48-byte entries, oldest genuine -> stays 48-mode, rolls off left.
        final byte[] genuine48 = seq(48, 0); // oldest, genuine
        final byte[] padded = padded48(seq(32, 100));
        final byte[] window = new byte[96];
        System.arraycopy(genuine48, 0, window, 0, 48);
        System.arraycopy(padded, 0, window, 48, 48);
        final byte[] newNative = seq(32, 200);

        final var result =
                BlockImplUtils.appendTrailingBlockHash(Bytes.wrap(newNative), Bytes.wrap(window), 2, 2L, 48, 32);
        assertEquals(96L, result.length()); // stays maxHashes*48
        final byte[] out = result.toByteArray();
        // oldest genuine rolled off; previously-second entry now first
        assertArrayEquals(padded, java.util.Arrays.copyOfRange(out, 0, 48));
        // new entry padded to 48, rightmost
        assertArrayEquals(newNative, java.util.Arrays.copyOfRange(out, 64, 96));
    }

    @Test
    void appendTrailingBlockHashCompactsWhenNoGenuineRemain() {
        // Full window of maxHashes=2 entries, both padded (no genuine) -> compact to 32-mode then native append.
        final byte[] r0 = seq(32, 50);
        final byte[] r1 = seq(32, 100);
        final byte[] window = new byte[96];
        System.arraycopy(padded48(r0), 0, window, 0, 48);
        System.arraycopy(padded48(r1), 0, window, 48, 48);
        final byte[] newNative = seq(32, 200);

        final var result =
                BlockImplUtils.appendTrailingBlockHash(Bytes.wrap(newNative), Bytes.wrap(window), 2, 2L, 48, 32);
        assertEquals(64L, result.length()); // compacted to maxHashes*32
        final byte[] out = result.toByteArray();
        // oldest (r0) rolled off; r1 compacted to native, new entry rightmost native
        assertArrayEquals(r1, java.util.Arrays.copyOfRange(out, 0, 32));
        assertArrayEquals(newNative, java.util.Arrays.copyOfRange(out, 32, 64));
    }

    @Test
    void appendTrailingBlockHashAppendsNativelyInPostCompactionEra() {
        // Already-native 32-mode window grows/rolls at 32.
        final byte[] r0 = seq(32, 50);
        final byte[] window = r0.clone(); // one native entry, countExisting = min(1,256)=1
        final byte[] newNative = seq(32, 200);

        final var result =
                BlockImplUtils.appendTrailingBlockHash(Bytes.wrap(newNative), Bytes.wrap(window), 256, 1L, 48, 32);
        assertEquals(64L, result.length());
        final byte[] out = result.toByteArray();
        assertArrayEquals(r0, java.util.Arrays.copyOfRange(out, 0, 32));
        assertArrayEquals(newNative, java.util.Arrays.copyOfRange(out, 32, 64));
    }

    @Test
    void appendTrailingBlockHashNeverCompactsWhenPreSizeEqualsActiveSize() {
        // Degenerate SHA-384 case (preSize == activeSize == 48): always 48, never compacts.
        final byte[] e0 = seq(48, 0);
        final byte[] window = e0.clone();
        final byte[] newHash = seq(48, 100);

        final var result =
                BlockImplUtils.appendTrailingBlockHash(Bytes.wrap(newHash), Bytes.wrap(window), 256, 1L, 48, 48);
        assertEquals(96L, result.length());
        final byte[] out = result.toByteArray();
        assertArrayEquals(e0, java.util.Arrays.copyOfRange(out, 0, 48));
        assertArrayEquals(newHash, java.util.Arrays.copyOfRange(out, 48, 96));
    }
}
