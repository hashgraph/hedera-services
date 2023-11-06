/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.stats;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.StatEntry;
import java.time.temporal.ChronoUnit;

/**
 * A metrics object to track an average time period, without history. This class uses an {@link AtomicAverage} so
 * it is both thread safe and performant.
 */
public class AverageTimeStat {
    private static final String FORMAT_DEFAULT = "%,10.3f";
    private static final String FORMAT_SECONDS = "%,5.4f";
    private static final String FORMAT_MILLIS = "%,10.1f";

    private final ChronoUnit unit;
    private final AtomicAverage average;
    private final StatEntry avgEntry;

    public AverageTimeStat(
            final Metrics metrics, final ChronoUnit unit, final String category, final String name, final String desc) {
        this(metrics, unit, category, name, desc, AverageStat.WEIGHT_SMOOTH);
    }

    public AverageTimeStat(
            final Metrics metrics,
            final ChronoUnit unit,
            final String category,
            final String name,
            final String desc,
            final double weight) {
        this.unit = unit;
        average = new AtomicAverage(weight);

        final String format;
        switch (unit) {
            case MILLIS:
                format = FORMAT_MILLIS;
                break;
            case SECONDS:
                format = FORMAT_SECONDS;
                break;
            default:
                format = FORMAT_DEFAULT;
        }
        avgEntry = metrics.getOrCreate(new StatEntry.Config<>(category, name, Double.class, this::getAvg)
                .withDescription(desc)
                .withFormat(format)
                .withReset(this::resetAvg));
    }

    private double convert(final double nanos) {
        return nanos / unit.getDuration().toNanos();
    }

    private double getAvg() {
        return convert(average.get());
    }

    private void resetAvg(final double unused) {
        average.reset();
    }

    public void update(final long startTime) {
        update(startTime, System.nanoTime());
    }

    public void update(final long start, final long end) {
        // the value is stored as nanos and converted upon retrieval
        final long nanos = end - start;
        average.update(nanos);
    }

    public StatEntry getAverageStat() {
        return avgEntry;
    }
}
