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

package com.swirlds.platform.hcm.impl.internal;

import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Utility methods for Lagrange interpolation.
 */
public final class LagrangeInterpolationUtils {
    /**
     * Compute the lagrange coefficient at a specific index.
     * <p>
     * The output of this method is the evaluation of the lagrange polynomial x = 0
     *
     * @param xCoordinates   the x-coordinates
     * @param indexToCompute the index to compute the lagrange coefficient for
     * @return the lagrange coefficient, which is the evaluation of the lagrange polynomial at x = 0
     */
    @NonNull
    public static FieldElement computeLagrangeCoefficient(
            @NonNull final List<FieldElement> xCoordinates, final int indexToCompute) {

        if (indexToCompute >= xCoordinates.size()) {
            throw new IllegalArgumentException("y-coordinate to compute must be within the range of x-coordinates");
        }

        final FieldElement xi = xCoordinates.get(indexToCompute);

        final Field field = xi.getField();
        final FieldElement zeroElement = field.zeroElement();
        final FieldElement oneElement = field.oneElement();

        FieldElement numerator = oneElement;
        FieldElement denominator = oneElement;
        for (int j = 0; j < xCoordinates.size(); j++) {
            if (j != indexToCompute) {
                final FieldElement xj = xCoordinates.get(j);
                numerator = numerator.multiply(zeroElement.subtract(xj));
                denominator = denominator.multiply(xi.subtract(xj));
            }
        }

        final FieldElement denominatorInverse = denominator.multiply(zeroElement.subtract(oneElement));

        return numerator.multiply(denominatorInverse);
    }
}
