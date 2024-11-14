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

package com.hedera.node.app.tss.cryptography.pairings.extensions;

import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * A polynomial, represented as a list of coefficients over the one of the groups of a defined EllipticCurve
 * where each {@code coefficients[i]} is an element of that field, and corresponds to the coefficient for {@code x^i}.
 *<p>
 * The degree of the polynomial is the size of the coefficients +1 such that: <br>
 *{@code p(x) = a_0 + a_1 * x + a_2 * x^2 + ... + a_d * x^d}
 *
 * @implNote it is responsibility of the user that the {@link GroupElement} instances are compatible with each-other.
 *  Otherwise, it might fail on the {@link #evaluate(FieldElement)} method depending on the implementation
 *  of {@link GroupElement} addition
 * @param coefficients {@code a_0, a_1, ..., a_d} the coefficients of the polynomial.
 */
public record EcPolynomial(@NonNull List<GroupElement> coefficients) {
    /**
     * Creates a polynomial that is represented as a list of {@link GroupElement} coefficients,
     *  where {@code coefficients[i]} corresponds to the coefficient for {@code x^i}.
     *
     * @param coefficients The commitment coefficients.
     */
    public EcPolynomial {
        if (Objects.requireNonNull(coefficients).isEmpty()) {
            throw new IllegalArgumentException("coefficients must not be empty");
        }
    }

    /**
     * Returns the degree of the polynomial.
     *
     * @return the degree of the polynomial.
     */
    public int degree() {
        return coefficients.size() - 1;
    }

    /**
     * Evaluates the polynomial at a specific value without reveling the polynomial.
     *
     * @param x the value to evaluate the commitment
     * @return a point on the curve
     */
    @NonNull
    public GroupElement evaluate(@NonNull final FieldElement x) {

        Objects.requireNonNull(x, "x must not be null");
        int n = coefficients.size() - 1;
        GroupElement result = coefficients.get(n);
        for (int i = n - 1; i >= 0; i--) {
            result = result.multiply(x).add(coefficients.get(i));
        }
        return result;
    }
}
