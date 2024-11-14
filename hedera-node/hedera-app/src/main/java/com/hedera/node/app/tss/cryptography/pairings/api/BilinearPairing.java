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

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a bilinear pairing operation used in cryptographic protocols.
 * <p>A pairing is a map: e : G₁ × G₂ -> Gₜ which can satisfy these properties:
 *  <ul>
 *   <li> Bilinearity: “a”, “b” member of “Fq” (Finite Field), “P” member of “G₁”, and “Q” member of “G₂”,
 *        then e(a×P, b×Q) = e(ab×P, Q) = e(P, ab×Q) = e(P, Q)^(ab)
 *   <li> Non-degeneracy: e != 1
 *   <li> Computability: There should be an efficient way to compute “e”.
 * </ul>
 * @see Group
 * @see GroupElement
 * @see PairingFriendlyCurve
 */
public interface BilinearPairing {

    /**
     * Get the first input element. This element is in the opposite group of the second input element
     *
     * @return the first input element
     */
    @NonNull
    GroupElement first();

    /**
     * Get the second input element. This element is in the opposite group of the first input element
     *
     * @return the second input element
     */
    @NonNull
    GroupElement second();

    /**
     * Compares two pairings.
     * <p>
     * The 2 elements of each pairing must be in opposite groups.
     * <p>
     * The order of the elements in each pairing is not important.
     *
     * @param other the other pairing to compare to
     * @return true if the pairings are equal, otherwise false
     */
    boolean compare(BilinearPairing other);
}
