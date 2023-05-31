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

package com.swirlds.common.metrics.platform;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.statistics.StatsBuffered;
import com.swirlds.common.statistics.StatsSpeedometer;
import com.swirlds.common.time.OSTime;
import com.swirlds.common.time.Time;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Platform-implementation of {@link SpeedometerMetric}
 */
public class DefaultSpeedometerMetric extends AbstractDistributionMetric implements SpeedometerMetric {

    @SuppressWarnings("removal")
    private final StatsSpeedometer speedometer;

    public DefaultSpeedometerMetric(final SpeedometerMetric.Config config) {
        this(config, OSTime.getInstance());
    }

    /**
     * This constructor should only be used for testing.
     */
    @SuppressWarnings("removal")
    public DefaultSpeedometerMetric(final SpeedometerMetric.Config config, final Time time) {
        super(config, config.getHalfLife());
        this.speedometer = new StatsSpeedometer(halfLife, time);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("removal")
    @Override
    public StatsBuffered getStatsBuffered() {
        return speedometer;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("removal")
    @Override
    public void update(final double value) {
        speedometer.update(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void cycle() {
        update(1);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("removal")
    @Override
    public double get() {
        return speedometer.getCyclesPerSecond();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("halfLife", halfLife)
                .append("value", get())
                .toString();
    }
}
