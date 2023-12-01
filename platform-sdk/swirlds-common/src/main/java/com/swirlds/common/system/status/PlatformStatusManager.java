/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.status;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.status.actions.PlatformStatusAction;
import com.swirlds.common.system.status.actions.TimeElapsedAction;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.framework.config.QueueThreadMetricsConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An asynchronous component which manages platform status.
 * <p>
 * This object wraps a {@link PlatformStatusStateMachine}, which contains the actual state machine logic.
 */
public class PlatformStatusManager implements PlatformStatusGetter, StatusActionSubmitter, Startable, Stoppable {
    /**
     * A source of time
     */
    private final Time time;

    /**
     * The queue to handle incoming {@link PlatformStatusAction}s
     */
    private final QueueThread<PlatformStatusAction> queue;

    /**
     * The platform status state machine that is being wrapped by this asynchronous component
     */
    private final PlatformStatusStateMachine stateMachine;

    /**
     * Constructor
     *
     * @param platformContext      the platform context
     * @param time                 a source of time
     * @param threadManager        the thread manager
     * @param statusChangeConsumer consumes any status changes
     */
    public PlatformStatusManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final ThreadManager threadManager,
            @NonNull final Consumer<PlatformStatus> statusChangeConsumer) {

        this.time = Objects.requireNonNull(time);
        Objects.requireNonNull(threadManager);
        Objects.requireNonNull(statusChangeConsumer);

        final PlatformStatusConfig config =
                platformContext.getConfiguration().getConfigData(PlatformStatusConfig.class);
        this.stateMachine = new PlatformStatusStateMachine(time, config, statusChangeConsumer);

        this.queue = new QueueThreadConfiguration<PlatformStatusAction>(threadManager)
                .setComponent("platform")
                .setThreadName("status-state-machine")
                .setHandler(this::processStatusAction)
                .setIdleCallback(this::triggerTimeElapsed)
                .setBatchHandledCallback(this::triggerTimeElapsed)
                .setWaitForWorkDuration(config.waitForWorkDuration())
                .setCapacity(config.statusActionQueueCapacity())
                .setMetricsConfiguration(new QueueThreadMetricsConfiguration(platformContext.getMetrics())
                        .enableMaxSizeMetric()
                        .enableBusyTimeMetric())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        queue.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        queue.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitStatusAction(@NonNull final PlatformStatusAction action) {
        Objects.requireNonNull(action);
        queue.add(action);
    }

    /**
     * Process the next status action in the queue
     *
     * @param action the action to process
     */
    private void processStatusAction(@NonNull final PlatformStatusAction action) {
        Objects.requireNonNull(action);
        stateMachine.processStatusAction(action);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PlatformStatus getCurrentStatus() {
        return stateMachine.getCurrentStatus();
    }

    /**
     * Trigger a time elapsed action
     * <p>
     * This is both the idle and batch-handled callback of the handle thread. It will be called when the handle thread
     * isn't handling other actions, and after a batch of actions has been handled.
     */
    private void triggerTimeElapsed() {
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
    }
}
