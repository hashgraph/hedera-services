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

package com.swirlds.platform.hcm.impl.tss.groth21;

import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A polynomial, represented as a list of coefficients.
 *
 * @param coefficients the coefficients of the polynomial
 */
public record DensePolynomial(@NonNull List<FieldElement> coefficients) {
    /**
     * Sample a random polynomial with a fixed point at x = 0.
     * <p>
     * A t degree polynomial is defined by t + 1 coefficients: a_0, a_1, ..., a_t
     * such that p(x) = a_0 + a_1 * x + a_2 * x^2 + ... + a_t * x^t
     * <p>
     * The polynomial generated here has t = threshold - 1, so it has threshold number of coefficients.
     *
     * @param random    a source of randomness
     * @param secret    the secret to embed in the polynomial
     * @param threshold the number of coefficients the polynomial should have
     * @return a random polynomial of degree threshold - 1, with the given secret embedded at x = 0
     */
    public static DensePolynomial fromSecret(
            @NonNull final Random random, @NonNull final FieldElement secret, final int threshold) {

        final Field field = secret.getField();
        final List<FieldElement> coefficients = new ArrayList<>(threshold);

        // the secret is embedded at x = 0
        coefficients.set(0, secret);

        for (int i = 1; i < threshold; i++) {
            coefficients.set(i, field.randomElement(random));
        }

        return new DensePolynomial(coefficients);
    }

    /**
     * Evaluate the polynomial at a given point.
     * <p>
     * This uses Horner's method for polynomial evaluation.
     *
     * @param point the point at which to evaluate the polynomial
     * @return the value of the polynomial at the given point
     */
    public FieldElement evaluate(@NonNull final FieldElement point) {
        final Field field = point.getField();

        FieldElement result = field.zeroElement();
        for (final FieldElement coefficient : coefficients) {
            result = result.multiply(point).add(coefficient);
        }

        return result;
    }
}
