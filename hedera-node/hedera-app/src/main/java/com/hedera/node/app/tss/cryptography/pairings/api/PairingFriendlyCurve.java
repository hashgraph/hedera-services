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
 * <p>This class provides access to each of the groups (G₁, G₂) for a specific pairing friendly curve and the FiniteField associated
 * with the curves.
 *
 * @see Group
 * @see Field
 */
public interface PairingFriendlyCurve {

    /**
     * Returns the curve type/ Name
     *
     * @return the field
     */
    @NonNull
    Curve curve();

    /**
     * Returns the finite field “Fq” associated with the curves of G₁ and G₂.
     *
     * @return the field
     */
    @NonNull
    Field field();

    /**
     * Returns the G₁ group associated with the pairing.
     *
     * @return the G₁ group
     */
    @NonNull
    Group group1();

    /**
     * Returns the G₂ group associated with the pairing.
     *
     * @return the G₂ group
     */
    @NonNull
    Group group2();

    /**
     * Returns G₁ if input is G₂, and vice versa.
     *
     * @param group the group to get the "other group" of
     * @return the other group
     */
    @NonNull
    Group getOtherGroup(@NonNull Group group);

    /**
     * Returns a pairing between elements from G₁ and G₂
     * <p>
     * The order of the elements is not important, element1 can be from G₁ and element2 from G₂, or vice versa.
     *
     * @param element1 one element of the pairing
     * @param element2 the other element of the pairing
     * @return the PairingResult
     */
    @NonNull
    BilinearPairing pairingBetween(@NonNull GroupElement element1, @NonNull GroupElement element2);
}
