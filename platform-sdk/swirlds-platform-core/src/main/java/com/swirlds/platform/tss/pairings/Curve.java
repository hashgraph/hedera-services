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

package com.swirlds.platform.tss.pairings;

public interface Curve<
        C extends Curve<C, FE, GE1, GE2>,
        FE extends FieldElement<C, FE, GE1, GE2>,
        GE1 extends Group1Element<C, FE, GE1, GE2>,
        GE2 extends Group2Element<C, FE, GE1, GE2>> {
    /**
     * Get the byte representation of the curve
     * <p>
     * The purpose of this method is to allow a serialized instance of an object on the curve to be deserialized,
     * without needing to know in advance what curve the object is on.
     *
     * @return the byte representation of the curve
     */
    byte idByte();

    /**
     * Returns the finite field associated with the curve.
     *
     * @return the field
     */
    Field<C, FE, GE1, GE2> getField();

    /**
     * Returns the public key group associated with the curve.
     *
     * @return the public key group
     */
    Group1<C, FE, GE1, GE2> getGroup1();

    /**
     * Returns the signature group associated with the curve.
     *
     * @return the signature group
     */
    Group2<C, FE, GE1, GE2> getGroup2();

    BilinearMap<C, FE, GE1, GE2> getBilinearMap();
}
