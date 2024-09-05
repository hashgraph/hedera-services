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

package com.hedera.cryptography.pairings.spi;

import com.hedera.cryptography.pairings.api.BilinearPairing;
import com.hedera.cryptography.pairings.api.Curve;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loader for accessing an implementation of the {@link BilinearPairingProvider} SPI.
 */
public final class BilinearPairingService {

    /**
     * Atomic boolean so that we don't repeatedly attempt to reload the resource.
     */
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Private Constructor since this class should not be instantiated.
     */
    private BilinearPairingService() {}

    /**
     * Obtain an instance of a {@link BilinearPairing} implementation which provides the given algorithm name/identifier.
     *
     * @param algorithm the algorithm name for which to locate an implementation.
     * @return an instance of the {@link BilinearPairing} implementing the requested {@code algorithm} which was found via
     * the {@link ServiceLoader} scan.
     */
    public static BilinearPairing instanceOf(final Curve algorithm) {
        return findInstance(algorithm).init().pairing();
    }

    /**
     * Forces a service loader refresh and causes the {@link ServiceLoader} implementation to evict the internal cache
     * and lazily rescan.
     */
    public static void refresh() {
        loader.reload();
    }

    /**
     * Locates a {@link BilinearPairingProvider} instance based on the search criteria provided.
     *
     * @param algorithm    the algorithm value for which to locate an implementation.
     * @throws NullPointerException     if the {@code algorithm} argument is a {@code null} reference.
     * @throws IllegalArgumentException if the {@code algorithm} argument is a blank or empty string.
     * @throws NoSuchElementException   if no {@link BilinearPairing} implementation was found via the {@link ServiceLoader}
     *                                  mechanism.
     */
    private static BilinearPairingProvider findInstance(final Curve algorithm) {

        final ServiceLoader<BilinearPairingProvider> serviceLoader =
                ServiceLoader.load(BilinearPairingProvider.class, classloader);

        final Iterator<BilinearPairingProvider> iterator = serviceLoader.iterator();

        while (iterator.hasNext()) {
            final BilinearPairingProvider provider : iterator.next();
            if (algorithm == provider.curve()) {
                return provider;
            }
        }

        throw new NoSuchElementException("The requested algorithm was not found.");
    }
}
