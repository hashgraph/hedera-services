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

package com.swirlds.common.wiring.model;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * Configures and builds a {@link WiringModel}.
 */
public class WiringModelBuilder {

    private ForkJoinPool defaultPool = ForkJoinPool.commonPool();
    private boolean enableHealthMonitoring = false;
    private double healthMonitoringFrequency = 10.0;
    private int healthMonitoringRunningAverageSize = 10;

    private final PlatformContext platformContext;

    /**
     * Constructor.
     */
    WiringModelBuilder(@NonNull final PlatformContext platformContext) {
        this.platformContext = Objects.requireNonNull(platformContext);
    }

    /**
     * Set the default fork join pool. Schedulers not explicitly assigned a pool will use this one.
     *
     * @param defaultPool the default fork join pool
     * @return this builder
     */
    @NonNull
    public WiringModelBuilder withDefaultPool(@NonNull final ForkJoinPool defaultPool) {
        this.defaultPool = Objects.requireNonNull(defaultPool);
        return this;
    }

    /**
     * Set if the health monitoring should be enabled. Health monitoring is used to detect when there are bottlenecks in
     * the system so that corrective action can be taken automatically.
     *
     * @return this builder
     */
    @NonNull
    public WiringModelBuilder withHealthMonitoringEnabled(final boolean enableHealthMonitoring) {
        this.enableHealthMonitoring = enableHealthMonitoring;
        return this;
    }

    /**
     * Set the frequency at which the health monitor should run. Higher frequencies will have a higher overhead. Ignored
     * if the health monitoring is not enabled.
     *
     * @param healthMonitoringFrequency the frequency at which the health monitor should run
     * @return this builder
     */
    @NonNull
    public WiringModelBuilder withHealthMonitoringFrequency(final double healthMonitoringFrequency) {
        if (healthMonitoringFrequency <= 0.0) {
            throw new IllegalArgumentException("Health monitoring frequency must be positive");
        }
        this.healthMonitoringFrequency = healthMonitoringFrequency;
        return this;
    }

    /**
     * Set the running average size for the health monitor. The health monitor uses a running average to determine if a
     * scheduler is stressed. This is the number of measurements to average. Ignored if the health monitoring is not
     * enabled.
     *
     * @param healthMonitoringRunningAverageSize the running average size for the health monitor
     * @return this builder
     */
    @NonNull
    public WiringModelBuilder withHealthMonitoringRunningAverageSize(final int healthMonitoringRunningAverageSize) {
        if (healthMonitoringRunningAverageSize <= 0) {
            throw new IllegalArgumentException("Health monitoring running average size must be positive");
        }
        this.healthMonitoringRunningAverageSize = healthMonitoringRunningAverageSize;
        return this;
    }

    /**
     * Build a new wiring model instance.
     *
     * @return a new wiring model instance
     */
    @NonNull
    public WiringModel build() {
        return new StandardWiringModel(
                platformContext,
                defaultPool,
                enableHealthMonitoring,
                healthMonitoringFrequency,
                healthMonitoringRunningAverageSize);
    }
}
