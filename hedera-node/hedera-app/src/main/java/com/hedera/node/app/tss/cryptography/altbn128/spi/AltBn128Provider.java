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

package com.hedera.node.app.tss.cryptography.altbn128.spi;

import com.hedera.node.app.tss.cryptography.altbn128.AltBn128;
import com.hedera.node.app.tss.cryptography.altbn128.adapter.jni.ArkBn254Adapter;
import com.hedera.node.app.tss.cryptography.pairings.api.Curve;
import com.hedera.node.app.tss.cryptography.pairings.api.PairingFriendlyCurve;
import com.hedera.node.app.tss.cryptography.pairings.spi.PairingFriendlyCurveProvider;

import java.util.concurrent.atomic.AtomicReference;

/**
 * An SPI provider for {@link AltBn128}
 */
public class AltBn128Provider extends PairingFriendlyCurveProvider {

    /**
     * The instance being provided.
     */
    final AtomicReference<PairingFriendlyCurve> pairingFriendlyCurve = new AtomicReference<>();

    /**
     * Initializes the library.
     * @implNote This method is only called once.
     */
    @Override
    protected void doInit() {
        // We force the library loading in the init method
        ArkBn254Adapter.getInstance();
        pairingFriendlyCurve.set(new AltBn128());
    }

    /**
     * Returns the implemented curve
     * @return the implemented curve.
     */
    @Override
    public Curve curve() {
        return Curve.ALT_BN128;
    }

    /**
     * The instance of {@link AltBn128}
     * @return the instance of {@link AltBn128}
     */
    @Override
    public PairingFriendlyCurve pairingFriendlyCurve() {
        return pairingFriendlyCurve.get();
    }
}
