/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto.engine;

import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Calculates and updates a running Hash each time a new RunningHashable is added.
 */
public class RunningHashProvider extends CachingOperationProvider<Hash, Hash, Hash, HashBuilder, DigestType> {

    private static final Logger logger = LogManager.getLogger(RunningHashProvider.class);

    /**
     * A constant log message used to indicate that a {@code null} value was added as a hash which results in a {@link
     * IllegalArgumentException} being thrown.
     */
    private static final String NEW_HASH_NULL = "RunningHashProvider :: newHashToAdd is null";

    /**
     * update the digest using the given hash
     *
     * @param hashBuilder
     * 		for building hash
     * @param hash
     * 		a hash to be digested
     */
    private static void updateForHash(final HashBuilder hashBuilder, final Hash hash) {
        hashBuilder.update(hash.getClassId());
        hashBuilder.update(hash.getVersion());
        hashBuilder.update(hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HashBuilder handleAlgorithmRequired(DigestType algorithmType) throws NoSuchAlgorithmException {
        return new HashBuilder(MessageDigest.getInstance(algorithmType.algorithmName()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Hash handleItem(
            final HashBuilder hashBuilder, final DigestType algorithmType, final Hash runningHash, Hash newHashToAdd) {
        // newHashToAdd should not be null
        if (newHashToAdd == null) {
            logger.trace(TESTING_EXCEPTIONS.getMarker(), NEW_HASH_NULL);
            throw new IllegalArgumentException(NEW_HASH_NULL);
        }

        hashBuilder.reset();

        // we only digest current hash when it is not null
        if (runningHash != null) {
            updateForHash(hashBuilder, runningHash);
        }
        // digest new hash
        updateForHash(hashBuilder, newHashToAdd);

        return hashBuilder.build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash compute(final Hash runningHash, final Hash newHashToAdd, final DigestType algorithmType)
            throws NoSuchAlgorithmException {
        return handleItem(loadAlgorithm(algorithmType), algorithmType, runningHash, newHashToAdd);
    }
}
