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

/**
 * An asynchronous implementation of the platform status state machine
 */
public class AsyncPlatformStatusStateMachine implements PlatformStatusStateMachine {
    /**
     * A source of time
     */
    private final Time time;

    /**
     * Background work is performed on this thread.
     */
    private final QueueThread<PlatformStatusAction> handleThread;

    /**
     * The state machine that is being wrapped by this asynchronous implementation
     */
    private final PlatformStatusStateMachine stateMachine;

    /**
     * Constructor
     *
     * @param platformContext the platform context
     * @param time            a source of time
     * @param stateMachine    the state machine that is being wrapped by this asynchronous implementation
     * @param threadManager   the thread manager
     */
    public AsyncPlatformStatusStateMachine(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final PlatformStatusStateMachine stateMachine,
            @NonNull final ThreadManager threadManager) {

        this.time = Objects.requireNonNull(time);
        this.stateMachine = Objects.requireNonNull(stateMachine);

        this.handleThread = new QueueThreadConfiguration<PlatformStatusAction>(threadManager)
                .setComponent("platform-status")
                .setThreadName("platform-status-state-machine")
                .setHandler(this::processStatusAction)
                .setIdleCallback(this::triggerTimeElapsed)
                .setMetricsConfiguration(
                        new QueueThreadMetricsConfiguration(platformContext.getMetrics()).enableBusyTimeMetric())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        handleThread.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        handleThread.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processStatusAction(@NonNull final PlatformStatusAction action) {
        stateMachine.processStatusAction(action);
    }

    /**
     * Trigger a time elapsed action
     * <p>
     * This is the idle callback of the handle thread. It will be called when the handle thread isn't handling other
     * actions.
     */
    private void triggerTimeElapsed() {
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
    }
}
