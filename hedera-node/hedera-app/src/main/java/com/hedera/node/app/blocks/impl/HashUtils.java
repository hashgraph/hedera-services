/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.impl;

import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.HASH_SIZE;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
    public static Bytes appendHash(@NonNull final Bytes hash, @NonNull final Bytes hashes, final int maxHashes) {
        final var limit = HASH_SIZE * maxHashes;
        final byte[] bytes = hashes.toByteArray();
        final byte[] newBytes;
        if (bytes.length < limit) {
            newBytes = new byte[bytes.length + HASH_SIZE];
            System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
            hash.getBytes(0, newBytes, newBytes.length - HASH_SIZE, HASH_SIZE);
        } else {
            newBytes = bytes;
            System.arraycopy(newBytes, HASH_SIZE, newBytes, 0, newBytes.length - HASH_SIZE);
            hash.getBytes(0, newBytes, newBytes.length - HASH_SIZE, HASH_SIZE);
        }
        return Bytes.wrap(newBytes);
    }

    public static byte[] combine(final byte[] leftHash, final byte[] rightHash) {
        try {
            final var digest = MessageDigest.getInstance("SHA-384");
            digest.update(leftHash);
            digest.update(rightHash);
            return digest.digest();
        } catch (final NoSuchAlgorithmException fatal) {
            throw new IllegalStateException(fatal);
        }
    }
}
