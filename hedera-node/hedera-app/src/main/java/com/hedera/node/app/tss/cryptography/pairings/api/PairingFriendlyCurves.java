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

package com.hedera.node.app.tss.cryptography.pairings.api;

import com.hedera.node.app.tss.cryptography.pairings.spi.PairingFriendlyCurveProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Utility class for finding implementations of a {@link PairingFriendlyCurve}.
 */
public final class PairingFriendlyCurves {

    /**
     * An inner class to hold and lazy initialize constant value
     */
    protected static class InstanceHolder {
        /**
         * lazy initialize a loader and hold the instance so that every time findInstance is called returns the same instance
         */
        public static final ServiceLoader<PairingFriendlyCurveProvider> LOADER =
                ServiceLoader.load(PairingFriendlyCurveProvider.class);
    }

    /**
     * Obtain an instance of a {@link PairingFriendlyCurve} implementation which provides the given {@code curve}.
     *
     * @param curve the curve name for which to locate an implementation.
     * @return an instance of the {@link PairingFriendlyCurve} implementing the requested {@link Curve}
     */
    @NonNull
    public static PairingFriendlyCurve instanceFor(@NonNull final Curve curve) {
        return findInstance(curve).pairingFriendlyCurve();
    }

    /**
     * Locates a {@link PairingFriendlyCurveProvider} instance based on the searched {@link Curve}.
     * The provider is initialized before being returned.
     *
     * @param curve the curve value for which to locate an implementation.
     * @throws NullPointerException if the {@code curve} argument is a {@code null} reference.
     * @throws IllegalArgumentException if the {@code curve} argument is a blank or empty string.
     * @throws NoSuchElementException if no {@link PairingFriendlyCurve} implementation was found via the {@link ServiceLoader}
     *                                  mechanism.
     * @throws IllegalStateException if there was a problem initializing the provider.
     * @return the BilinearPairingProvider implementation corresponding to the curve.
     */
    @NonNull
    public static PairingFriendlyCurveProvider findInstance(@NonNull final Curve curve) {
        Objects.requireNonNull(curve, "curve must not be null");
        for (final PairingFriendlyCurveProvider provider : InstanceHolder.LOADER) {
            if (curve == provider.curve()) {
                try {
                    return provider.init();
                } catch (final Exception e) {
                    throw new IllegalStateException("Could not initialize provider " + provider, e);
                }
            }
        }

        throw new NoSuchElementException(
                "A PairingFriendlyCurveProvider implementation for " + curve + " was not found.");
    }

    /**
     * Returns all loaded and supported curves.
     *
     * @return all loaded and supported curves
     */
    @NonNull
    public static Collection<Curve> allSupportedCurves() {
        List<Curve> supportedCurves = new ArrayList<>();
        for (final PairingFriendlyCurveProvider provider : InstanceHolder.LOADER) {
            supportedCurves.add(provider.curve());
        }
        return supportedCurves;
    }
}
