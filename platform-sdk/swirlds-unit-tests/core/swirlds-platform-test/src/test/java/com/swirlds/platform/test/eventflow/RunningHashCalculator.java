/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.eventflow;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.platform.internal.EventImpl;

/**
 * Calculates a running hash on the calling thread.
 */
public class RunningHashCalculator {

    private Hash runningHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);
    private final Cryptography cryptography = CryptographyHolder.get();

    public void calculateRunningHash(final EventImpl event) {
        if (event.getHash() == null) {
            cryptography.digestSync(event);
        }
        final Hash newHashToAdd = event.getHash();
        // calculates and updates runningHash
        runningHash = cryptography.calcRunningHash(runningHash, newHashToAdd, DigestType.SHA_384);
        event.getRunningHash().setHash(runningHash);
    }
}
