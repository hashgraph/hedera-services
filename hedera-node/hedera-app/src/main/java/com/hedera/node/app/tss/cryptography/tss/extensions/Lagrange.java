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
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * This class uses Lagrange polynomial (which is a form of interpolation that allows to construct a polynomial passing through a given set of points)
 * to reconstruct the y-intercept value 𝑃(0), which equals 𝑎0, the constant term of the polynomial.
 */
public class Lagrange {

    /**
     * Private constructor for static access
     */
    private Lagrange() {}

    /**
     * This method calculates the Lagrange polynomial for a given set of data points,
     * represented by two consecutive lists of xs and ys values where each point is given by {@code (xs[i]; ys[i])}
     * the {@code ys} are instances of {@link FieldElement}'s; and recovers the y-intercept value 𝑃(0).
     *
     * @param xs the list of x coordinates of each point
     * @param ys the list of y coordinates of each point
     * @return the y-intercept value 𝑃(0) of the interpolated polynomial.
     * @throws NullPointerException if any of the parameters is null
     * @throws IllegalArgumentException if xs size doesn't match ys size
     */
    @NonNull
    public static FieldElement recoverFieldElement(
            @NonNull final List<FieldElement> xs, @NonNull final List<FieldElement> ys) {
        if (Objects.requireNonNull(xs, "xs must not be null").isEmpty()) {
            throw new IllegalArgumentException("xs must not be empty");
        }
        if (Objects.requireNonNull(ys, "ys must not be null").isEmpty()) {
            throw new IllegalArgumentException("ys must not be empty");
        }
        if (xs.size() != ys.size()) {
            throw new IllegalArgumentException("xs and ys must have the same size");
        }

        final List<FieldElement> weightedElements = IntStream.range(0, ys.size())
                .boxed()
                .map(i -> coefficient(xs, i).multiply(ys.get(i)))
                .toList();
        final Field field = ys.getFirst().field();
        return field.add(weightedElements);
    }

    /**
     * This method calculates the Lagrange polynomial for a given set of data points,
     * represented by two consecutive lists of xs and ys values where each point is given by {@code (xs[i]; ys[i])}
     * the {@code ys} are instances of {@link GroupElement}'s; and recovers the y-intercept value 𝑃(0).
     *
     * @param xs the list of x coordinates of each point
     * @param ys the list of y coordinates of each point
     * @return the y-intercept value 𝑃(0) of the interpolated polynomial.
     * @throws NullPointerException if any of the parameters is null
     * @throws IllegalArgumentException if xs size doesn't match ys size
     */
    @NonNull
    public static GroupElement recoverGroupElement(
            @NonNull final List<FieldElement> xs, @NonNull final List<GroupElement> ys) {
        if (Objects.requireNonNull(xs, "xs must not be null").isEmpty()) {
            throw new IllegalArgumentException("xs must not be empty");
        }
        if (Objects.requireNonNull(ys, "ys must not be null").isEmpty()) {
            throw new IllegalArgumentException("ys must not be empty");
        }
        if (xs.size() != ys.size()) {
            throw new IllegalArgumentException("xs and ys must have the same size");
        }

        final Group group = ys.getFirst().getGroup();
        final List<GroupElement> weightedElements = IntStream.range(0, ys.size())
                .boxed()
                .map(i -> ys.get(i).multiply(coefficient(xs, i)))
                .toList();
        return group.add(weightedElements);
    }

    /**
     * Computes the Lagrange coefficient given by {@code L_i(x) = Π ((x - x_j) / (x_i - x_j)) for j ≠ i}.
     * for the i-th {@code indexToCompute} point amongst the x-coordinates {@code xs};
     * @param xs   the x-coordinates
     * @param indexToCompute the index to compute the lagrange coefficient for
     * @return the result of evaluating the Lagrange polynomial at the point {@code indexToCompute}.
     */
    @NonNull
    private static FieldElement coefficient(@NonNull final List<FieldElement> xs, final int indexToCompute) {

        if (indexToCompute >= xs.size()) {
            throw new IllegalArgumentException("y-coordinate to compute must be within the range of x-coordinates");
        }

        final FieldElement xi = xs.get(indexToCompute);
        final Field field = xi.field();
        final FieldElement zeroElement = field.fromLong(0L);
        final FieldElement oneElement = field.fromLong(1L);

        FieldElement numerator = oneElement;
        FieldElement denominator = oneElement;
        for (int j = 0; j < xs.size(); j++) {
            if (j != indexToCompute) {
                final FieldElement xj = xs.get(j);
                numerator = numerator.multiply(zeroElement.subtract(xj));
                denominator = denominator.multiply(xi.subtract(xj));
            }
        }

        return numerator.multiply(denominator.inverse());
    }
}
