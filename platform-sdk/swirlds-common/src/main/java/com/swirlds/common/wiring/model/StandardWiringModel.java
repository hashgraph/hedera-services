/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.NO_OP;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.SEQUENTIAL;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.SEQUENTIAL_THREAD;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.diagram.HyperlinkBuilder;
import com.swirlds.common.wiring.model.internal.monitor.HealthMonitor;
import com.swirlds.common.wiring.model.internal.standard.HeartbeatScheduler;
import com.swirlds.common.wiring.model.internal.standard.JvmAnchor;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.builders.internal.StandardTaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.internal.SequentialThreadTaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * A standard implementation of a wiring model suitable for production use.
 */
public class StandardWiringModel extends TraceableWiringModel {

    /**
     * The platform context.
     */
    private final PlatformContext platformContext;

    /**
     * Schedules heartbeats. Not created unless needed.
     */
    private HeartbeatScheduler heartbeatScheduler = null;

    /**
     * The scheduler that the health monitor runs on.
     */
    private final TaskScheduler<Duration> healthMonitorScheduler;

    /**
     * The health monitor.
     */
    private HealthMonitor healthMonitor;

    /**
     * The input wire for the health monitor.
     */
    private final BindableInputWire<Instant, Duration> healthMonitorInputWire;

    /**
     * Thread schedulers need to have their threads started/stopped.
     */
    private final List<SequentialThreadTaskScheduler<?>> threadSchedulers = new ArrayList<>();

    /**
     * The default fork join pool, schedulers not explicitly assigned a pool will use this one.
     */
    private final ForkJoinPool defaultPool;

    /**
     * Used to prevent the JVM from prematurely exiting.
     */
    private final JvmAnchor anchor;

    /**
     * The amount of time that must pass before we start logging health information.
     */
    private final Duration healthLogThreshold;

    /**
     * The period at which we log health information.
     */
    private final Duration healthLogPeriod;

    /**
     * Constructor.
     *
     * @param builder the builder for this model, contains all needed configuration
     */
    StandardWiringModel(@NonNull final WiringModelBuilder builder) {
        super(builder.isHardBackpressureEnabled());

        this.platformContext = Objects.requireNonNull(builder.getPlatformContext());
        this.defaultPool = Objects.requireNonNull(builder.getDefaultPool());

        final TaskSchedulerBuilder<Duration> healthMonitorSchedulerBuilder = this.schedulerBuilder("HealthMonitor");
        healthMonitorSchedulerBuilder.withHyperlink(HyperlinkBuilder.platformCoreHyperlink(HealthMonitor.class));
        if (builder.isHealthMonitorEnabled()) {
            healthMonitorSchedulerBuilder
                    .withType(SEQUENTIAL)
                    .withUnhandledTaskMetricEnabled(true)
                    .withUnhandledTaskCapacity(builder.getHealthMonitorCapacity());
        } else {
            healthMonitorSchedulerBuilder.withType(NO_OP);
        }

        healthLogThreshold = builder.getHealthLogThreshold();
        healthLogPeriod = builder.getHealthLogPeriod();
        healthMonitorScheduler = healthMonitorSchedulerBuilder.build();
        healthMonitorInputWire = healthMonitorScheduler.buildInputWire("check system health");
        buildHeartbeatWire(builder.getHealthMonitorPeriod()).solderTo(healthMonitorInputWire);

        if (builder.isJvmAnchorEnabled()) {
            anchor = new JvmAnchor();
        } else {
            anchor = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public final <O> TaskSchedulerBuilder<O> schedulerBuilder(@NonNull final String name) {
        throwIfStarted();
        return new StandardTaskSchedulerBuilder<>(platformContext, this, name, defaultPool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        throwIfStarted();
        return getHeartbeatScheduler().buildHeartbeatWire(period);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public OutputWire<Duration> getHealthMonitorWire() {
        return healthMonitorScheduler.getOutputWire();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Duration getUnhealthyDuration() {
        throwIfNotStarted();
        return healthMonitor.getUnhealthyDuration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public OutputWire<Instant> buildHeartbeatWire(final double frequency) {
        throwIfStarted();
        return getHeartbeatScheduler().buildHeartbeatWire(frequency);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerScheduler(@NonNull final TaskScheduler<?> scheduler, @Nullable final String hyperlink) {
        super.registerScheduler(scheduler, hyperlink);
        if (scheduler.getType() == SEQUENTIAL_THREAD) {
            threadSchedulers.add((SequentialThreadTaskScheduler<?>) scheduler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfStarted();

        if (anchor != null) {
            anchor.start();
        }

        healthMonitor = new HealthMonitor(platformContext, schedulers, healthLogThreshold, healthLogPeriod);
        healthMonitorInputWire.bind(healthMonitor::checkSystemHealth);

        markAsStarted();

        // We don't have to do anything with the output of these sanity checks.
        // The methods below will log errors if they find problems.
        checkForCyclicalBackpressure();
        checkForIllegalDirectSchedulerUsage();
        checkForUnboundInputWires();

        if (heartbeatScheduler != null) {
            heartbeatScheduler.start();
        }

        for (final SequentialThreadTaskScheduler<?> threadScheduler : threadSchedulers) {
            threadScheduler.start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotStarted();

        if (heartbeatScheduler != null) {
            heartbeatScheduler.stop();
        }

        for (final SequentialThreadTaskScheduler<?> threadScheduler : threadSchedulers) {
            threadScheduler.stop();
        }

        if (anchor != null) {
            anchor.stop();
        }
    }

    /**
     * Get the heartbeat scheduler, creating it if necessary.
     *
     * @return the heartbeat scheduler
     */
    @NonNull
    private HeartbeatScheduler getHeartbeatScheduler() {
        if (heartbeatScheduler == null) {
            heartbeatScheduler = new HeartbeatScheduler(this, platformContext.getTime(), "Heartbeat");
        }
        return heartbeatScheduler;
    }
}
