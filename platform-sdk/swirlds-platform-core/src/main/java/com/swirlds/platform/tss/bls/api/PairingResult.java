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

package com.swirlds.platform.tss.bls.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class represents the result of the pairing operation between two elements.
 *
 * <p>A signature is considered valid only if the pairing between “g1” and the signature “σ” is equal to the pairing between pk and the hash point “H(m)”.
 * The properties of pairings can be used to confirm this relationship. Specifically, we can calculate that:
 *         e(pk, H(m)) = e([sk]g1, H(m)) = e(g1, H(m))^(sk) = e(g1, [sk]H(m)) = e(g1, σ).
 * </p>
 */
public interface PairingResult extends ByteRepresentable<PairingResult>, UnderCurve {

    /**
     * Checks both pairings are equals in the mathematical sense
     */
    boolean isEquals(@NonNull PairingResult other);
}
