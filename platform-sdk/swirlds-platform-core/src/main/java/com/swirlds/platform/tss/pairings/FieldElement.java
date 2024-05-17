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
import java.math.BigInteger;

/**
 * An interface representing a generic field element
 *
 * @param <FE> the field element type
 * @param <F> the field type
 */
public interface FieldElement<FE extends FieldElement<FE, F>, F extends Field<FE, F>> {
    /**
     * Returns the field the element is in
     *
     * @return the field
     */
    @NonNull
    F field();

    /**
     * Serializes the field element to bytes
     *
     * @return the byte array representing the element
     */
    byte[] toBytes();

    /**
     * Adds another field element to this one
     *
     * @param other the other field element
     * @return a new field element which is the sum of this element and another
     */
    @NonNull
    FE add(@NonNull FE other);

    /**
     * Subtracts another field element from this one
     *
     * @param other the other field element
     * @return a new field element which is the difference of this element and another
     */
    @NonNull
    FE subtract(@NonNull FE other);

    /**
     * Multiplies another field element with this one
     *
     * @param other the other field element
     * @return a new field element which is the product of this element and another
     */
    @NonNull
    FE multiply(@NonNull FE other);

    /**
     * Divides the field element by another
     *
     * @param other the other field element
     * @return a new field element which is the quotient of this element and another
     */
    @NonNull
    FE divide(@NonNull FE other);

    /**
     * Takes the field element to the power of an integer
     *
     * @param exponent the exponent integer
     * @return a new field element which is the power
     */
    @NonNull
    FE power(@NonNull BigInteger exponent);

    /**
     * Checks whether the element bytes are valid
     *
     * @return true of the element bytes are valid, otherwise false
     */
    boolean isValid();
}
