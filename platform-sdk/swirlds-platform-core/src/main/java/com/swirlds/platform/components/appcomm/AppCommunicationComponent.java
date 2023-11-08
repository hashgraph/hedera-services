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

package com.swirlds.platform.components.appcomm;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_3;
import static com.swirlds.common.metrics.Metrics.INTERNAL_CATEGORY;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.IssNotification;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.common.system.state.notifications.NewSignedStateNotification;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.platform.components.PlatformComponent;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.stats.AverageAndMax;
import com.swirlds.platform.stats.AverageStat;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This component responsible for notifying the application of various platform events
 */
public class AppCommunicationComponent
        implements PlatformComponent, StateToDiskAttemptConsumer, NewLatestCompleteStateConsumer, IssConsumer {
    private static final Logger logger = LogManager.getLogger(AppCommunicationComponent.class);

    private final NotificationEngine notificationEngine;
    /** A queue thread that asynchronously invokes NewLatestCompleteStateConsumers */
    private final QueueThread<ReservedSignedState> asyncLatestCompleteStateQueue;
    /**
     * The size of the queue holding tasks for
     * {@link com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer}s
     */
    private final AverageAndMax asyncLatestCompleteStateQueueSize;

    /**
     * Create a new instance
     * @param notificationEngine the notification engine
     * @param context the platform context
     */
    public AppCommunicationComponent(
            @NonNull final NotificationEngine notificationEngine, @NonNull final PlatformContext context) {
        this.notificationEngine = notificationEngine;
        this.asyncLatestCompleteStateQueueSize = new AverageAndMax(
                context.getMetrics(),
                INTERNAL_CATEGORY,
                "asyncLatestCompleteStateQueueSize",
                "average number of new latest complete state occurrences waiting to be sent to consumers",
                FORMAT_10_3,
                AverageStat.WEIGHT_VOLATILE);
        this.asyncLatestCompleteStateQueue = new QueueThreadConfiguration<ReservedSignedState>(getStaticThreadManager())
                .setThreadName("new-latest-complete-state-consumer-queue")
                .setComponent("wiring")
                .setCapacity(context.getConfiguration()
                        .getConfigData(WiringConfig.class)
                        .newLatestCompleteStateConsumerQueueSize())
                .setHandler(this::latestCompleteStateHandler)
                .build();
    }

    @Override
    public void stateToDiskAttempt(
            @NonNull final SignedState signedState, @NonNull final Path directory, final boolean success) {
        if (success) {
            // Synchronous notification, no need to take an extra reservation
            notificationEngine.dispatch(
                    StateWriteToDiskCompleteListener.class,
                    new StateWriteToDiskCompleteNotification(
                            signedState.getRound(),
                            signedState.getConsensusTimestamp(),
                            signedState.getSwirldState(),
                            directory,
                            signedState.isFreezeState()));
        }
    }

    @Override
    public void newLatestCompleteStateEvent(@NonNull final SignedState signedState) {
        // the state is reserved now before it is added to the queue
        // it will be released by the notification engine after the app consumes it
        // this is done by latestCompleteStateAppNotify()
        // if the state does not make into the queue, it will be released below
        final ReservedSignedState reservedSignedState =
                signedState.reserve("AppCommunicationComponent newLatestCompleteStateEvent");
        final boolean success = asyncLatestCompleteStateQueue.offer(reservedSignedState);
        if (!success) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Unable to add new latest complete state task (state round = {}) to {} because it is full",
                    signedState.getRound(),
                    asyncLatestCompleteStateQueue.getName());
            reservedSignedState.close();
        }
    }

    /**
     * Handler for {@link #asyncLatestCompleteStateQueue}
     */
    private void latestCompleteStateHandler(@NonNull final ReservedSignedState reservedSignedState) {
        final NewSignedStateNotification notification = new NewSignedStateNotification(
                reservedSignedState.get().getSwirldState(),
                reservedSignedState.get().getState().getSwirldDualState(),
                reservedSignedState.get().getRound(),
                reservedSignedState.get().getConsensusTimestamp());

        notificationEngine.dispatch(NewSignedStateListener.class, notification, r -> reservedSignedState.close());
    }

    @Override
    public void iss(
            final long round, @NonNull final IssNotification.IssType issType, @Nullable final NodeId otherNodeId) {
        final IssNotification notification = new IssNotification(round, issType, otherNodeId);
        notificationEngine.dispatch(IssListener.class, notification);
    }

    /**
     * Update the size of the task queue for
     * {@link com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer}s
     */
    public void updateLatestCompleteStateQueueSize() {
        asyncLatestCompleteStateQueueSize.update(asyncLatestCompleteStateQueue.size());
    }

    @Override
    public void start() {
        asyncLatestCompleteStateQueue.start();
    }

    @Override
    public void stop() {
        asyncLatestCompleteStateQueue.stop();
    }
}
