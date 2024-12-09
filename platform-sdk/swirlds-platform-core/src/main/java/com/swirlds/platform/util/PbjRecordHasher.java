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

package com.swirlds.platform.util;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * A hasher for PBJ records.
 *
 * An instance of this class can be used to hash multiple records sequentially,
 * on a single thread or with external synchronization.
 * The implementation is not thread-safe. The caller code is responsible for synchronization.
 */
public class PbjRecordHasher {
    // The choice of the digest type may be reevaluated in the future based on hashing capabilities of various runtimes
    // (e.g. the EVM.) If necessary, the digest type may become a parameter to this class at that time.
    // However, currently we use the same digest type that is used throughout the majority of this code base.
    private static final DigestType DIGEST_TYPE = DigestType.SHA_384;

    private final MessageDigest digest = DIGEST_TYPE.buildDigest();
    private final WritableSequentialData stream = new WritableStreamingData(new HashingOutputStream(digest));

    /**
     * Computes a Hash object for a given PBJ record and its codec.
     *
     * The caller code must ensure the codec is appropriate for the given record. For convenience,
     * PBJ offers a public static final PROTOBUF reference to a Codec instance in every model class.
     *
     * It is expected that the codec produces a deterministic representation for the record
     * to make the computed hash value stable and useful. PBJ's Protobuf codecs are deterministic.
     *
     * @param <T> the type of the records to hash. Must be a PBJ model type that extends Record.
     * @param record a PBJ model
     * @param codec a codec for the given model
     * @return a Hash object
     */
    @NonNull
    public <T extends Record> Hash hash(@NonNull final T record, @NonNull final Codec<T> codec) {
        try {
            codec.write(record, stream);
        } catch (final IOException e) {
            throw new RuntimeException("An exception occurred while trying to hash a record!", e);
        }
        // Reminder, MessageDigest.digest resets the digest, so subsequent writes
        // will calculate an independent hash value.
        return new Hash(digest.digest(), DIGEST_TYPE);
    }
}
