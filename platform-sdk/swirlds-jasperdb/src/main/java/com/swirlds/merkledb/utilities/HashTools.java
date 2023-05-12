/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.merkledb.utilities;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import java.nio.ByteBuffer;

/**
 * Some helpers for dealing with hashes.
 */
public final class HashTools {
    private static final int CURRENT_SERIALIZATION_VERSION = 1;

    public static final DigestType DEFAULT_DIGEST = DigestType.SHA_384;
    public static final int HASH_SIZE_BYTES = DEFAULT_DIGEST.digestLength();

    /**
     * Gets the version for all hashes written. May represent both the storage format and the digest.
     *
     * @return the serialization version being used
     */
    public static int getSerializationVersion() {
        return CURRENT_SERIALIZATION_VERSION;
    }

    /**
     * Creates a new {@link ByteBuffer} whose contents are the digest of the given hash.
     *
     * @param hash
     * 		the hash with the digest to put in a byte buffer
     * @return the byte buffer with the digest of the hash
     */
    public static ByteBuffer hashToByteBuffer(final Hash hash) {
        final ByteBuffer buf = ByteBuffer.allocate(HASH_SIZE_BYTES);
        buf.put(hash.getValue());
        return buf.flip();
    }

    /**
     * Copies the digest of the given hash into the given {@link ByteBuffer}.
     *
     * @param hash
     * 		the hash with the digest to copy
     * @param buf
     * 		the byte buffer to receive the digest of the hash
     */
    public static void hashToByteBuffer(final Hash hash, final ByteBuffer buf) {
        buf.put(hash.getValue());
    }

    /**
     * Returns a SHA-384 hash whose digest is the contents of the given {@link Hash}.
     *
     * @param buffer
     * 		the byte buffer whose contents are the desired digest
     * @param serializationVersion
     * 		the version of serialization used to create the byte buffer
     * @return a SHA-384 hash whose digest is the contents of the given buffer
     */
    public static Hash byteBufferToHash(final ByteBuffer buffer, final int serializationVersion) {
        if (serializationVersion != CURRENT_SERIALIZATION_VERSION) {
            throw new IllegalArgumentException(
                    "Current version is " + CURRENT_SERIALIZATION_VERSION + ", got " + serializationVersion);
        }
        return new Hash(buffer, DEFAULT_DIGEST);
    }

    private HashTools() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
