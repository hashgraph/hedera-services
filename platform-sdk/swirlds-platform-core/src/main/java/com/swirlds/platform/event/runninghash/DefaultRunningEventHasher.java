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

package com.swirlds.platform.event.runninghash;

import static com.swirlds.common.utility.ByteUtils.intToByteArray;
import static com.swirlds.common.utility.ByteUtils.longToByteArray;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * A standard implementation of the {@link RunningEventHasher}.
 */
public class DefaultRunningEventHasher implements RunningEventHasher {

    private static final DigestType DIGEST_TYPE = DigestType.SHA_384;
    private final HashingOutputStream hashingOutputStream;

    private Hash runningEventHash;

    /**
     * Constructor.
     */
    public DefaultRunningEventHasher() {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(DIGEST_TYPE.algorithmName());
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        this.hashingOutputStream = new HashingOutputStream(digest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void computeRunningEventHash(@NonNull final ConsensusRound round) {
        final long roundNumber = round.getRoundNum();
        if (runningEventHash == null) {
            throw new IllegalStateException(
                    "Prior running event hash must be set before computing the running event hash for round "
                            + roundNumber);
        }

        try {
            hashingOutputStream.resetDigest();
            hashingOutputStream.write(runningEventHash.getBytes().toByteArray());
            hashingOutputStream.write(longToByteArray(roundNumber));

            for (final EventImpl event : round.getConsensusEvents()) {
                final Hash eventHash = event.getBaseEvent().getHashedData().getHash();
                final Instant consensusTimestamp = event.getConsensusData().getConsensusTimestamp();

                hashingOutputStream.write(eventHash.getBytes().toByteArray());
                hashingOutputStream.write(longToByteArray(event.getConsensusOrder()));
                hashingOutputStream.write(longToByteArray(consensusTimestamp.getEpochSecond()));
                hashingOutputStream.write(intToByteArray(consensusTimestamp.getNano()));
            }

            runningEventHash = new Hash(hashingOutputStream.getDigest(), DIGEST_TYPE);

            round.setRunningEventHash(runningEventHash);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void overrideRunningEventHash(@NonNull final RunningEventHashOverride runningEventHashOverride) {
        runningEventHash = runningEventHashOverride.runningEventHash();
    }
}
