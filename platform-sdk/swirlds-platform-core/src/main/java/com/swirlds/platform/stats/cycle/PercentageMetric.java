/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.stats.cycle;

import com.swirlds.common.metrics.FloatFormats;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.CommonUtils;
import java.util.function.BiFunction;

/**
 * A metric that is used to output a percentage 0-100%
 */
public class PercentageMetric {
    private static final String APPENDIX = " (%)";
    private static final int PERCENT = 100;

    private final IntegerPairAccumulator<Double> container;

    protected PercentageMetric(
            final Metrics metrics,
            final String category,
            final String name,
            final String description,
            final BiFunction<Integer, Integer, Double> resultFunction) {

        CommonUtils.throwArgNull(name, "name");
        container = metrics.getOrCreate(
                new IntegerPairAccumulator.Config<>(category, name + APPENDIX, Double.class, resultFunction)
                        .withDescription(description)
                        .withFormat(FloatFormats.FORMAT_3_1));
    }

    protected PercentageMetric(
            final Metrics metrics, final String category, final String name, final String description) {

        this(metrics, category, name, description, PercentageMetric::calculatePercentage);
    }

    /**
     * Atomically update the left and right value
     *
     * @param leftValue the left value
     * @param rightValue the right value
     */
    protected void update(final int leftValue, final int rightValue) {
        container.update(leftValue, rightValue);
    }

    public static double calculatePercentage(final int total, final int part) {
        if (total == 0) {
            return 0;
        }
        return (((double) part) / total) * PERCENT;
    }

    /**
     * Returns the current value
     *
     * @return the current value
     */
    public double get() {
        return container.get();
    }
}
