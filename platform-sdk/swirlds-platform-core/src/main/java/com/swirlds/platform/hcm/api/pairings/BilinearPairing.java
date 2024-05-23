/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.hcm.api.pairings;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a bilinear pairing operation used in cryptographic protocols.
 *
 * <p>A bilinear pairing is a function that takes two elements from two groups and maps them to an element
 * in a third group, satisfying certain properties that are useful in various cryptographic schemes
 * such as identity-based encryption, short signatures, and more.</p>
 *
 * <p>This class provides access to each of the groups (G₁, G₂) for a specific Pairing and the FiniteField associated
 * with the curves.</p>
 * <p>
 * A pairing is a map: e : G₁ × G₂ -> Gₜ which can satisfy these properties:
 *  <ul>
 *   <li> Bilinearity: “a”, “b” member of “Fq” (Finite Field), “P” member of “G₁”, and “Q” member of “G₂”,
 *        then e(a×P, b×Q) = e(ab×P, Q) = e(P, ab×Q) = e(P, Q)^(ab)
 *   <li> Non-degeneracy: e != 1
 *   <li> Computability: There should be an efficient way to compute “e”.
 * </ul>
 *
 * @see Group
 * @see Field
 */
public interface BilinearPairing {
    /**
     * Returns the finite field “Fq” associated with the curves of G₁ and G₂.
     *
     * @return the field
     */
    @NonNull
    Field getField();

    /**
     * Returns the G₁ group associated with the pairing.
     *
     * @return the G₁ group
     */
    @NonNull
    Group getGroup1();

    /**
     * Returns the G₂ group associated with the pairing.
     *
     * @return the G₂ group
     */
    @NonNull
    Group getGroup2();

    /**
     * Returns G1 if input is G2, and vice versa.
     *
     * @param group the group to get the "other group" of
     * @return the other group
     */
    Group getOtherGroup(@NonNull Group group);

    /**
     * Returns a pairing between elements from G₁ and G₂
     * <p>
     * The order of the elements is not important, elementA can be from G₁ and elementB from G₂, or vice versa.
     *
     * @param elementA one element of the pairing
     * @param elementB the other element of the pairing
     * @return the PairingResult
     */
    @NonNull
    PairingResult pairingBetween(@NonNull GroupElement elementA, @NonNull GroupElement elementB);
}
