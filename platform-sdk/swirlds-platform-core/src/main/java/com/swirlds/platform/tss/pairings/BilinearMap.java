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

package com.swirlds.platform.tss.pairings;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An object for computing bilinear pairings
 *
 * @param <C>   the curve type
 * @param <FE>  the field element type
 * @param <GE1> the group 1 element type
 * @param <GE2> the group 2 element type
 */
public interface BilinearMap<
        C extends Curve<C, FE, GE1, GE2>,
        FE extends FieldElement<C, FE, GE1, GE2>,
        GE1 extends Group1Element<C, FE, GE1, GE2>,
        GE2 extends Group2Element<C, FE, GE1, GE2>> {

    /**
     * Returns the field of the bilinear map
     *
     * @return the field
     */
    @NonNull
    FE field();

    /**
     * Computes 2 pairings, and then checks the equality of the result
     *
     * @param group1Element1 TODO
     * @param group2Element1
     * @param group1Element2
     * @param group2Element2
     * @return true if the 2 pairings have the same result, otherwise false
     */
    boolean comparePairing(
            @NonNull GE1 group1Element1,
            @NonNull GE2 group2Element1,
            @NonNull GE1 group1Element2,
            @NonNull GE2 group2Element2);

    /**
     * Computes a pairing, and returns a byte array representing the result
     *
     * @param group1Element TODO
     * @param group2Element
     * @return a byte array representing the pairing
     */
    byte[] displayPairing(@NonNull GE1 group1Element, @NonNull GE2 group2Element);
}
