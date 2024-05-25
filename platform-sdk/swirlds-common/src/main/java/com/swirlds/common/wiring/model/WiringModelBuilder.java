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
    private boolean hardBackpressureEnabled = false; // TODO this will break tests, fix then remove TODO

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
            return (T) new StandardWiringModel(
                    platformContext, defaultPool, healthMonitorEnabled, hardBackpressureEnabled);
        }
    }
}
