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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * Builds a {@link WiringModel}.
 */
public class WiringModelBuilder {

    private final PlatformContext platformContext;

    private boolean deterministicModeEnabled;
    private ForkJoinPool defaultPool = ForkJoinPool.commonPool();
    private boolean healthMonitorEnabled = true;
    private boolean hardBackpressureEnabled = false;
    private boolean jvmAnchorEnabled = false;
    private int healthMonitorCapacity = 500;
    private Duration healthMonitorPeriod = Duration.ofMillis(100);
    private Duration healthLogThreshold = Duration.ofSeconds(5);
    private Duration healthLogPeriod = Duration.ofMinutes(10);

    /**
     * Create a new builder.
     *
     * @param platformContext the platform context
     * @return the builder
     */
    @NonNull
    public static WiringModelBuilder create(@NonNull final PlatformContext platformContext) {
        return new WiringModelBuilder(platformContext);
    }

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     */
    private WiringModelBuilder(@NonNull final PlatformContext platformContext) {
        this.platformContext = Objects.requireNonNull(platformContext);
    }

    /**
     * Specify the fork join pool to use for schedulers that don't specify a fork join pool. Schedulers not explicitly
     * assigned a pool will use this one. Default is the common pool.
     *
     * @param defaultPool the default fork join pool
     * @return this
     */
    @NonNull
    public WiringModelBuilder withDefaultPool(@NonNull final ForkJoinPool defaultPool) {
        this.defaultPool = Objects.requireNonNull(defaultPool);
        return this;
    }

    /**
     * Set if deterministic mode should be enabled. If enabled, the wiring model will be deterministic (and much
     * slower). Suitable for simulations and testing. Default false.
     *
     * @param deterministicModeEnabled whether to enable deterministic mode
     * @return this
     */
    @NonNull
    public WiringModelBuilder withDeterministicModeEnabled(final boolean deterministicModeEnabled) {
        this.deterministicModeEnabled = deterministicModeEnabled;
        return this;
    }

    /**
     * Set if the health monitor should be enabled. Default is true.
     *
     * @param healthMonitorEnabled whether to enable the health monitor
     * @return this
     */
    @NonNull
    public WiringModelBuilder withHealthMonitorEnabled(final boolean healthMonitorEnabled) {
        this.healthMonitorEnabled = healthMonitorEnabled;
        return this;
    }

    /**
     * Set if hard backpressure should be enabled. Default is false.
     *
     * @param hardBackpressureEnabled whether to enable hard backpressure
     * @return this
     */
    @NonNull
    public WiringModelBuilder withHardBackpressureEnabled(final boolean hardBackpressureEnabled) {
        this.hardBackpressureEnabled = hardBackpressureEnabled;
        return this;
    }

    /**
     * Set if the JVM anchor should be enabled. Default is false. If enabled and {@link WiringModel#start()} has been
     * called, the JVM will not automatically exit due to lack of non-daemon threads until {@link WiringModel#stop()} is
     * called.
     *
     * @param jvmAnchorEnabled whether to enable the JVM anchor
     * @return this
     */
    @NonNull
    public WiringModelBuilder withJvmAnchorEnabled(final boolean jvmAnchorEnabled) {
        this.jvmAnchorEnabled = jvmAnchorEnabled;
        return this;
    }

    /**
     * Set the capacity of the health monitor's task scheduler's unhandled task capacity. Default is 500.
     *
     * @param healthMonitorCapacity the capacity of the health monitor
     * @return this
     */
    @NonNull
    public WiringModelBuilder withHealthMonitorCapacity(final int healthMonitorCapacity) {
        this.healthMonitorCapacity = healthMonitorCapacity;
        return this;
    }

    /**
     * Set the period of the health monitor's task scheduler. Default is 100ms.
     *
     * @param healthMonitorPeriod the period of the health monitor
     * @return this
     */
    @NonNull
    public WiringModelBuilder withHealthMonitorPeriod(@NonNull final Duration healthMonitorPeriod) {
        this.healthMonitorPeriod = Objects.requireNonNull(healthMonitorPeriod);
        return this;
    }

    /**
     * Set the amount of time a scheduler may be unhealthy before the platform is considered to be unhealthy. When a
     * scheduler crosses this threshold, the health monitor will log a warning. Default is 5 seconds.
     *
     * @param healthThreshold the amount of time a scheduler may be unhealthy
     * @return this
     */
    @NonNull
    public WiringModelBuilder withHealthLogThreshold(@NonNull final Duration healthThreshold) {
        this.healthLogThreshold = Objects.requireNonNull(healthThreshold);
        return this;
    }

    /**
     * Set the minimum amount of time that must pass between health log messages for the same scheduler. Default is 10
     * minutes.
     *
     * @param healthLogPeriod the minimum amount of time that must pass between health log messages
     * @return this
     */
    @NonNull
    public WiringModelBuilder withHealthLogPeriod(@NonNull final Duration healthLogPeriod) {
        this.healthLogPeriod = Objects.requireNonNull(healthLogPeriod);
        return this;
    }

    /**
     * Build the wiring model.
     *
     * @param <T> the type of wiring model
     * @return the wiring model
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <T extends WiringModel> T build() {
        if (deterministicModeEnabled) {
            return (T) new DeterministicWiringModel(platformContext);
        } else {
            return (T) new StandardWiringModel(this);
        }
    }

    /**
     * Get the platform context.
     *
     * @return the platform context
     */
    @NonNull
    PlatformContext getPlatformContext() {
        return platformContext;
    }

    /**
     * Get the default fork join pool for schedulers. Schedulers that have not been assigned a fork join pool should use
     * this one.
     *
     * @return the default fork join pool
     */
    @NonNull
    ForkJoinPool getDefaultPool() {
        return defaultPool;
    }

    /**
     * Check if the health monitor is enabled.
     *
     * @return true if the health monitor is enabled
     */
    boolean isHealthMonitorEnabled() {
        return healthMonitorEnabled;
    }

    /**
     * Check if hard backpressure is enabled.
     *
     * @return true if hard backpressure is enabled
     */
    boolean isHardBackpressureEnabled() {
        return hardBackpressureEnabled;
    }

    /**
     * Check if the JVM anchor is enabled.
     *
     * @return true if the JVM anchor is enabled
     */
    boolean isJvmAnchorEnabled() {
        return jvmAnchorEnabled;
    }

    /**
     * Get the capacity of the health monitor's task scheduler's unhandled task capacity.
     *
     * @return the capacity of the health monitor
     */
    int getHealthMonitorCapacity() {
        return healthMonitorCapacity;
    }

    /**
     * Get the period of the health monitor's task scheduler.
     *
     * @return the period of the health monitor
     */
    @NonNull
    Duration getHealthMonitorPeriod() {
        return healthMonitorPeriod;
    }

    /**
     * Get the amount of time a scheduler may be unhealthy before the platform is considered to be unhealthy.
     *
     * @return the amount of time a scheduler may be unhealthy
     */
    @NonNull
    Duration getHealthLogThreshold() {
        return healthLogThreshold;
    }

    /**
     * Get the minimum amount of time that must pass between health log messages for the same scheduler.
     *
     * @return the minimum amount of time that must pass between health log messages
     */
    @NonNull
    Duration getHealthLogPeriod() {
        return healthLogPeriod;
    }
}
