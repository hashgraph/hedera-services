/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.pairings.bls12381.impl.spi;

import com.swirlds.pairings.api.BilinearPairing;
import com.swirlds.pairings.api.Curve;
import com.swirlds.pairings.bls12381.impl.Bls12381BilinearPairing;
import com.swirlds.pairings.spi.BilinearPairingProvider;

/*
 * An implementation of {@link BilinearPairingProvider} which returns an instance of {@link Bls12381BilinearPairing}
 */
public class Bls12381Provider implements BilinearPairingProvider {

    /**
     * {@inheritDoc}
     */
    @Override
    public Curve curve() {
        return Curve.BLS12_381;
    }

    @Override
    public BilinearPairing pairing() {
        return Bls12381BilinearPairing.getInstance();
    }
}
