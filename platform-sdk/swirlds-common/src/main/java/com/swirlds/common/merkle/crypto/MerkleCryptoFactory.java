/*
 * Copyright (C) 2016-2025 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.crypto;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Public factory implementation from which all {@link MerkleCryptography} instances should be acquired.
 *
 * @deprecated We will remove this static class in near future after {@link CryptographyHolder} has been removed
 */
@Deprecated(forRemoval = true)
public class MerkleCryptoFactory {

    private static final Logger logger = LogManager.getLogger(MerkleCryptoFactory.class);

    /**
     * Internal lock
     */
    private static final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * the single {@link MerkleCryptography} instance
     */
    private static MerkleCryptography merkleCryptography;

    private MerkleCryptoFactory() {}

    /**
     * Setup cryptography. Only needed to support unit tests that do not go through the proper setup procedures.
     */
    private static void init() {
        // This exception is intended to intentionally fail validators for deployments that do not set up
        // this class correctly. This won't cause a problem during unit tests, which are the biggest offender
        // w.r.t. not setting this up correctly.
        logger.error(EXCEPTION.getMarker(), "MerkleCryptoFactory not initialized, using default config");

        final Configuration defaultConfiguration = ConfigurationBuilder.create()
                .withConfigDataType(CryptoConfig.class)
                .build();
        merkleCryptography = MerkleCryptographyFactory.create(defaultConfiguration, CryptographyHolder.get());
    }

    /**
     * Set the {@link MerkleCryptography} singleton.
     *
     * @param merkleCryptography the {@link MerkleCryptography} to use
     */
    public static void set(@NonNull final MerkleCryptography merkleCryptography) {
        try (final Locked ignored = lock.lock()) {
            MerkleCryptoFactory.merkleCryptography = merkleCryptography;
        }
    }

    /**
     * Getter for the {@link MerkleCryptography} singleton.
     *
     * @return the {@link MerkleCryptography} singleton
     */
    public static MerkleCryptography getInstance() {
        try (final Locked ignored = lock.lock()) {
            if (merkleCryptography == null) {
                init();
            }
            return merkleCryptography;
        }
    }
}
