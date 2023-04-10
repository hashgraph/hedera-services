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

package com.swirlds.common.merkle.crypto;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.merkle.crypto.internal.MerkleCryptoEngine;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;

/**
 * Public factory implementation from which all {@link MerkleCryptography} instances should be acquired.
 *
 * @deprecated We will remove this static class in near future after {@link CryptographyHolder} has been removed
 */
@Deprecated(forRemoval = true)
public class MerkleCryptoFactory {

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
     * Getter for the {@link MerkleCryptography} singleton.
     *
     * @return the {@link MerkleCryptography} singleton
     */
    public static MerkleCryptography getInstance() {
        if (merkleCryptography == null) {
            try (final Locked ignored = lock.lock()) {
                if (merkleCryptography == null) {
                    merkleCryptography = new MerkleCryptoEngine(
                            getStaticThreadManager(),
                            CryptographyHolder.get(),
                            ConfigurationHolder.getInstance().get().getConfigData(CryptoConfig.class));
                }
            }
        }
        return merkleCryptography;
    }
}
