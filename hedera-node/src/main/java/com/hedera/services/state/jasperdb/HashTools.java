package com.hedera.services.state.jasperdb;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.nio.ByteBuffer;

/**
 * Some helpers for dealing with hashes
 */
public class HashTools {
    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;
    public static final int HASH_SIZE_BYTES = DEFAULT_DIGEST.digestLength();


    public static ByteBuffer hashToByteBuffer(Hash hash) {
        ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE_BYTES);
        buf.put(hash.getValue());
        return buf.flip();
    }

    public static void hashToByteBuffer(Hash hash, ByteBuffer buf) {
        buf.put(hash.getValue());
    }

    public static Hash byteBufferToHash(ByteBuffer buffer) {
        byte[] hashBytes = new byte[HASH_SIZE_BYTES];
        buffer.get(hashBytes);
        return new NoCopyHash(hashBytes);
    }

    public static Hash byteBufferToHashNoCopy(ByteBuffer buffer) {
        return new NoCopyHash(buffer.array());
    }

    public static void hashToByteArray(Hash hash, byte[] array,int offset) {
        System.arraycopy(hash.getValue(),0,array,offset,HASH_SIZE_BYTES);
    }

    public static Hash byteArrayToHash(byte[] array) {
        return new NoCopyHash(array);
    }

    public static Hash byteArrayToHash(byte[] array, int offset) {
        byte[] hashBytes = new byte[HASH_SIZE_BYTES];
        System.arraycopy(array,offset,hashBytes,0,HASH_SIZE_BYTES);
        return new NoCopyHash(hashBytes);
    }

    public static final class NoCopyHash extends Hash {
        public NoCopyHash(byte[] bytes) {
            super(bytes, DEFAULT_DIGEST, true, false);
        }
    }
}
