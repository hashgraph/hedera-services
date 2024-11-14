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

package com.hedera.node.app.tss.cryptography.tss.extensions;

import com.hedera.node.app.tss.cryptography.pairings.api.Field;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.extensions.EcPolynomial;
import com.hedera.node.app.tss.cryptography.pairings.extensions.FiniteFieldPolynomial;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * This class uses allows creating an interpolation polynomial as used in shamir-secret-sharing.
 * And a commitment to that polynomial
 */
public class ShamirUtils {

    private ShamirUtils() {}

    /**
     * Creates a random degree d polynomial with a fixed point at x = 0.
     * The polynomial generated here has d+1 number of coefficients: {@code a_0, a_1, ..., a_d} such that: <br>
     * {@code p(x) = a_0 + a_1 * x + a_2 * x^2 + ... + a_d * x^d}
     *
     * @param random    a source of randomness
     * @param fixedValue the fixedValue to embed in the polynomial
     * @param degree    the degree of the polynomial.
     * @return a random polynomial of the given degree, with the given fixedValue embedded at x = 0
     * @throws NullPointerException if any of the parameters is null
     * @throws IllegalArgumentException if the degree is not a positive number
     */
    @NonNull
    public static FiniteFieldPolynomial interpolationPolynomial(
            @NonNull final Random random, @NonNull final FieldElement fixedValue, final int degree) {

        Objects.requireNonNull(random, "random must not be null");
        final Field field = Objects.requireNonNull(fixedValue, "fixedValue must not be null")
                .field();
        if (degree <= 0) {
            throw new IllegalArgumentException("degree must be positive");
        }
        final List<FieldElement> coefficients = new ArrayList<>(degree + 1);

        // the fixedValue is embedded at x = 0
        coefficients.add(fixedValue);

        for (int i = 0; i < degree; i++) {
            coefficients.add(field.random(random));
        }

        return new FiniteFieldPolynomial(coefficients);
    }

    /**
     * Creates a FeldmanCommitment which is a {@link EcPolynomial} where every
     * coefficient consists of the group generator, raised to the power of a coefficient of the {@link FiniteFieldPolynomial} being committed to.
     *
     * @param group the group that elements of the commitment are in
     * @param finiteFieldPolynomial the polynomial to commit to
     * @return the FeldmanCommitment
     */
    @NonNull
    public static EcPolynomial feldmanCommitment(
            @NonNull final Group group, @NonNull final FiniteFieldPolynomial finiteFieldPolynomial) {
        final GroupElement generator = Objects.requireNonNull(group).generator();
        Objects.requireNonNull(finiteFieldPolynomial, "finiteFieldPolynomial must not be null");
        final List<GroupElement> commitmentCoefficients = new ArrayList<>();
        for (final FieldElement polynomialCoefficient : finiteFieldPolynomial.coefficients()) {
            commitmentCoefficients.add(generator.multiply(polynomialCoefficient));
        }

        return new EcPolynomial(commitmentCoefficients);
    }
}
