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

package com.hedera.node.app.tss.cryptography.pairings.spi;

import com.hedera.node.app.tss.cryptography.pairings.api.Curve;
import com.hedera.node.app.tss.cryptography.pairings.api.PairingFriendlyCurve;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This should return a {@link PairingFriendlyCurve} for a given {@link Curve} the implementation supports.
 * Spi will return a new instance of this class everytime is requested with {@link java.util.ServiceLoader}
 */
public abstract class PairingFriendlyCurveProvider {
    /**
     * Atomic boolean to avoid repeated attempts to reload the resource.
     */
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Implementations should include here all the steps necessary to load the library, e.g.,
     * perform native library loads.
     * This method will be called only once per instance and thread-safe guaranteed invocation.
     */
    protected abstract void doInit();

    /**
     * Performs any initialization steps.
     * Implementations should consider that the Init method will be every time the instance is requested.
     * @return the same instance that received the call but after being initialized if applicable.
     */
    public PairingFriendlyCurveProvider init() {
        if (!initialized.get()) {
            synchronized (this) {
                if (initialized.get()) {
                    return this;
                }
                this.doInit();
                initialized.set(true);
            }
        }
        return this;
    }

    /**
     * Returns the {@link Curve} supported by the Pairing API implementation
     *
     * @return the supported {@link Curve}
     */
    public abstract Curve curve();

    /**
     * Returns the instance of the {@link PairingFriendlyCurve}
     *
     * @return the instance of the {@link PairingFriendlyCurve}
     */
    public abstract PairingFriendlyCurve pairingFriendlyCurve();
}
