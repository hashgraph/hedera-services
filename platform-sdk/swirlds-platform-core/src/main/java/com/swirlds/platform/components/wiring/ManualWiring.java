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

package com.swirlds.platform.components.wiring;

import static com.swirlds.common.system.SystemExitCode.FATAL_ERROR;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SystemExitCode;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatusGetter;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.logging.payloads.FatalErrorPayload;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.components.PlatformComponent;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.components.appcomm.DefaultAppCommunicationComponentFactory;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import com.swirlds.platform.components.state.DefaultStateManagementComponentFactory;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.components.state.StateManagementComponentFactory;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.metrics.WiringMetrics;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.util.PlatformComponents;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Manually wires platform components, connecting data producers to all the interested consumers.
 * <p>
 * At some point, wiring will be automated. As more platform components are formalized, code will move from
 * {@link SwirldsPlatform} to the wiring class(es) and fewer arguments will need to be passed in.
 */
public class ManualWiring {
    private static final Logger logger = LogManager.getLogger(ManualWiring.class);
    private final PlatformContext platformContext;
    private final ThreadManager threadManager;
    private final AddressBook addressBook;
    private final Shutdown shutdown;
    /** A list of all formal platform components */
    private final List<PlatformComponent> platformComponentList = new ArrayList<>();
    /** A list of all informal platform components that need to be started and/or registered as dispatch observers. */
    private final List<Object> otherComponents = new ArrayList<>();

    /** Metrics tracked by this class */
    private final WiringMetrics wiringMetrics;

    /** A queue thread that asynchronously invokes NewLatestCompleteStateConsumers */
    private final QueueThread<Runnable> asyncLatestCompleteStateQueue;

    private final DispatchBuilder dispatchBuilder;

    public ManualWiring(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final DispatchBuilder dispatchBuilder,
            @NonNull final AddressBook addressBook) {

        this.platformContext = platformContext;
        this.threadManager = threadManager;
        this.addressBook = addressBook;
        this.wiringMetrics = new WiringMetrics(platformContext.getMetrics());
        this.shutdown = new Shutdown();
        this.dispatchBuilder = Objects.requireNonNull(dispatchBuilder);

        final WiringConfig wiringConfig = platformContext.getConfiguration().getConfigData(WiringConfig.class);
        asyncLatestCompleteStateQueue = new QueueThreadConfiguration<Runnable>(threadManager)
                .setThreadName("new-latest-complete-state-consumer-queue")
                .setComponent("wiring")
                .setCapacity(wiringConfig.newLatestCompleteStateConsumerQueueSize())
                .setHandler(Runnable::run)
                .build();
        otherComponents.add(asyncLatestCompleteStateQueue);
    }

    /**
     * Creates and wires the {@link AppCommunicationComponent}.
     *
     * @param notificationEngine passes notifications between the platform and the application
     * @return a fully wired {@link AppCommunicationComponent}
     */
    public AppCommunicationComponent wireAppCommunicationComponent(final NotificationEngine notificationEngine) {
        final AppCommunicationComponent appCommunicationComponent =
                new DefaultAppCommunicationComponentFactory(notificationEngine).build();
        platformComponentList.add(appCommunicationComponent);
        return appCommunicationComponent;
    }

    /**
     * Creates and wires the {@link StateManagementComponent}.
     *
     * @param platformSigner                     signer capable of signing with this node's private key
     * @param mainClassName                      the class that extends {@link com.swirlds.common.system.SwirldMain}
     * @param selfId                             this node's id
     * @param swirldName                         the name of the swirld this node is in
     * @param prioritySystemTransactionSubmitter submits priority system transactions
     * @param haltRequestedConsumer              consumer to invoke when a halt is requested
     * @param appCommunicationComponent          the {@link AppCommunicationComponent}
     * @param preconsensusEventWriter            writes preconsensus events to disk
     * @param platformStatusGetter               gets the current platform status
     * @param statusActionSubmitter              enables submitting platform status actions
     * @return a fully wired {@link StateManagementComponent}
     */
    public @NonNull StateManagementComponent wireStateManagementComponent(
            @NonNull final PlatformSigner platformSigner,
            @NonNull final String mainClassName,
            @NonNull final NodeId selfId,
            @NonNull final String swirldName,
            @NonNull final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter,
            @NonNull final HaltRequestedConsumer haltRequestedConsumer,
            @NonNull final AppCommunicationComponent appCommunicationComponent,
            @NonNull final PreconsensusEventWriter preconsensusEventWriter,
            @NonNull final PlatformStatusGetter platformStatusGetter,
            @NonNull final StatusActionSubmitter statusActionSubmitter) {

        Objects.requireNonNull(platformSigner, "platformSigner");
        Objects.requireNonNull(mainClassName, "mainClassName");
        Objects.requireNonNull(selfId, "selfId");
        Objects.requireNonNull(swirldName, "swirldName");
        Objects.requireNonNull(prioritySystemTransactionSubmitter, "prioritySystemTransactionSubmitter");
        Objects.requireNonNull(haltRequestedConsumer, "haltRequestedConsumer");
        Objects.requireNonNull(appCommunicationComponent, "appCommunicationComponent");
        Objects.requireNonNull(preconsensusEventWriter, "preconsensusEventWriter");
        Objects.requireNonNull(platformStatusGetter);
        Objects.requireNonNull(statusActionSubmitter);

        final StateManagementComponentFactory stateManagementComponentFactory =
                new DefaultStateManagementComponentFactory(
                        platformContext,
                        threadManager,
                        dispatchBuilder,
                        addressBook,
                        platformSigner,
                        mainClassName,
                        selfId,
                        swirldName,
                        platformStatusGetter,
                        statusActionSubmitter);

        stateManagementComponentFactory.newLatestCompleteStateConsumer(ss -> {
            final ReservedSignedState reservedSignedState = ss.reserve("ManualWiring newLatestCompleteStateConsumer");

            boolean success = asyncLatestCompleteStateQueue.offer(() -> {
                try (reservedSignedState) {
                    appCommunicationComponent.newLatestCompleteStateEvent(reservedSignedState.get());
                }
            });
            if (!success) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Unable to add new latest complete state task " + "(state round = {}) to {} because it is full",
                        ss.getRound(),
                        asyncLatestCompleteStateQueue.getName());
                reservedSignedState.close();
            }
        });

        // FUTURE WORK: make the call to the app communication component asynchronous
        stateManagementComponentFactory.stateToDiskConsumer(appCommunicationComponent);

        stateManagementComponentFactory.prioritySystemTransactionConsumer(prioritySystemTransactionSubmitter);
        stateManagementComponentFactory.haltRequestedConsumer(haltRequestedConsumer);
        // FUTURE WORK: make this asynchronous
        stateManagementComponentFactory.issConsumer(appCommunicationComponent);
        stateManagementComponentFactory.fatalErrorConsumer(this::handleFatalError);
        stateManagementComponentFactory.setPreconsensusEventWriter(preconsensusEventWriter);

        final StateManagementComponent stateManagementComponent = stateManagementComponentFactory.build();
        platformComponentList.add(stateManagementComponent);
        return stateManagementComponent;
    }

    /**
     * Inform all components that a fatal error has occurred, log the error, and shutdown the JVM.
     */
    public void handleFatalError(
            @Nullable final String msg, @Nullable final Throwable throwable, @NonNull final SystemExitCode exitCode) {

        // Log this fatal error first, to make sure it ends up in the logs, no matter what else happens
        logFatalError(msg, throwable);

        // Let each platform component attempt to handle the fatal error
        platformComponentList.forEach(PlatformComponent::onFatalError);

        // It may be that null was passed in, despite our compiler warnings. In that case, we want to log this fact
        // with a stack trace for who called this method, so we can track down what code is passing in null.
        //noinspection ConstantValue
        if (exitCode == null) {
            try {
                throw new NullPointerException("exitCode was null");
            } catch (final NullPointerException e) {
                logger.error("exitCode was null. Exiting anyway with FATAL_ERROR", e);
            }
        }

        // We will either exit with the code given to us, or FATAL_ERROR if somebody failed to give us a code
        //noinspection ConstantValue
        shutdown.shutdown(msg, exitCode == null ? FATAL_ERROR : exitCode);
    }

    private static void logFatalError(final String msg, final Throwable throwable) {
        final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        final StringBuilder sb = new StringBuilder();
        sb.append(new FatalErrorPayload("Fatal error, node will shut down. Reason: " + msg))
                .append("\n");

        for (final StackTraceElement element : stackTrace) {
            sb.append("   ").append(element).append("\n");
        }

        if (throwable == null) {
            logger.fatal(EXCEPTION.getMarker(), sb);
        } else {
            logger.fatal(EXCEPTION.getMarker(), sb, throwable);
        }
    }

    /**
     * Registers all components created by this class.
     *
     * @param platformComponents the class that manages startables and registers dispatch observers.
     */
    public void registerComponents(final PlatformComponents platformComponents) {
        otherComponents.forEach(platformComponents::add);
        this.platformComponentList.forEach(platformComponents::add);
    }

    /**
     * Updates metrics tracked by this class
     */
    public void updateMetrics() {
        wiringMetrics.updateLatestCompleteStateQueueSize(asyncLatestCompleteStateQueue.size());
    }
}
