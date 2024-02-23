/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;

/**
 * Static holder of the {@link Cryptography} instance.
 *
 * @deprecated This class will be removed once the {@link com.swirlds.common.context.PlatformContext} is used.
 */
@Deprecated(forRemoval = true)
public final class CryptographyHolder {

    private static final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * The singleton instance of the {@link Cryptography} interface.
     */
    private static CryptoEngine cryptography;

    /**
     * Private constructor to prevent instantiation.
     */
    private CryptographyHolder() {
        throw new UnsupportedOperationException();
    }

    /**
     * By calling the method the interal {@link Cryptography} instance will be removed and a new instance will be
     * created when {@link #get()} is called the next time.
     */
    public static void reset() {
        try (final Locked ignored = lock.lock()) {
            cryptography = null;
        }
    }

    /**
     * Getter for the {@link Cryptography} singleton.
     *
     * @return the {@link Cryptography} singleton
     */
    public static Cryptography get() {
        if (cryptography == null) {
            try (final Locked ignored = lock.lock()) {
                if (cryptography == null) {
                    cryptography = new CryptoEngine(
                            getStaticThreadManager(),
                            ConfigurationHolder.getInstance().get().getConfigData(CryptoConfig.class));
                }
            }
        }
        return cryptography;
    }
}
