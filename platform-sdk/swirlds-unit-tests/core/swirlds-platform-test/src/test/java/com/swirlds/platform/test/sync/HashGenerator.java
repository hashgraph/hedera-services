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

package com.swirlds.platform.test.sync;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple, factory for Hashes, to create a  Hash with random bytes, or a
 * Hash which encodes an integer value as a sequence of bytes. This type is only a
 * convenience, for unit testing.
 */
public final class HashGenerator {

    private static final Random gen = new Random();

    private static final AtomicInteger sequentialCount = new AtomicInteger(0);
    private static final AtomicInteger randomCount = new AtomicInteger(0);

    private static final DigestType hashType = DigestType.SHA_384;

    /**
     * Private ctor. This is a static factory type.
     */
    private HashGenerator() {
        // No ctor does nothing.
    }

    /**
     * Construct a {@link Hash} from a sequence of random bytes.
     *
     * @return the constructed {@link Hash}
     */
    public static Hash random() {
        randomCount.addAndGet(1);

        final byte[] bytes = new byte[hashType.digestLength()];
        gen.nextBytes(bytes);

        return new Hash(bytes, hashType);
    }
}
