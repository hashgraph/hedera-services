/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.chatter.protocol;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import com.swirlds.common.sequence.Shiftable;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.EventDescriptor;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.chatter.protocol.heartbeat.HeartbeatMessage;
import com.swirlds.platform.gossip.chatter.protocol.heartbeat.HeartbeatSendReceive;
import com.swirlds.platform.gossip.chatter.protocol.input.InputDelegate;
import com.swirlds.platform.gossip.chatter.protocol.input.InputDelegateBuilder;
import com.swirlds.platform.gossip.chatter.protocol.input.MessageTypeHandlerBuilder;
import com.swirlds.platform.gossip.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.gossip.chatter.protocol.output.MessageOutput;
import com.swirlds.platform.gossip.chatter.protocol.output.OtherEventDelay;
import com.swirlds.platform.gossip.chatter.protocol.output.PriorityOutputAggregator;
import com.swirlds.platform.gossip.chatter.protocol.output.SendAction;
import com.swirlds.platform.gossip.chatter.protocol.output.VariableTimeDelay;
import com.swirlds.platform.gossip.chatter.protocol.output.queue.QueueOutputMain;
import com.swirlds.platform.gossip.chatter.protocol.peer.CommunicationState;
import com.swirlds.platform.gossip.chatter.protocol.peer.PeerGossipState;
import com.swirlds.platform.gossip.chatter.protocol.peer.PeerInstance;
import com.swirlds.platform.gossip.chatter.protocol.processing.ProcessingTimeMessage;
import com.swirlds.platform.gossip.chatter.protocol.processing.ProcessingTimeSendReceive;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Links all components of chatter together. Constructs and keeps track of peer instances.
 *
 * @param <E> the type of {@link ChatterEvent} used
 */
public class ChatterCore<E extends ChatterEvent> implements Shiftable, LoadableFromSignedState {
    /** the number of milliseconds to sleep while waiting for the chatter protocol to stop */
    private static final int STOP_WAIT_SLEEP_MILLIS = 10;
    /** the name of the metrics category */
    private static final String METRICS_CATEGORY = "chatter";

    private final Class<E> eventClass;
    private final MessageHandler<E> prepareReceivedEvent;
    private final ChatterConfig config;
    private final BiConsumer<NodeId, Long> pingConsumer;
    private final MessageOutput<E> selfEventOutput;
    private final MessageOutput<E> otherEventOutput;
    private final MessageOutput<EventDescriptor> hashOutput;
    private final Map<NodeId, PeerInstance> peerInstances;

    private final CountPerSecond msgsPerSecRead;
    private final CountPerSecond msgsPerSecWrit;
    /** this node's event processing time */
    private final DurationGauge selfProcessingTime;

    private final Time time;

    /**
     * @param time                 provides wall clock time
     * @param eventClass           the class of the type of event used
     * @param prepareReceivedEvent the first handler to be called when an event is received, this should do any
     *                             preparation work that might be needed by other handlers (such as hashing)
     * @param config               chatter config
     * @param pingConsumer         consumer of the reported ping time for a given peer. accepts the ID of the peer and
     *                             the number of nanoseconds it took for the peer to respond
     * @param metrics              reference to the metrics-system
     */
    public ChatterCore(
            @NonNull final Time time,
            @NonNull final Class<E> eventClass,
            @NonNull final MessageHandler<E> prepareReceivedEvent,
            @NonNull final ChatterConfig config,
            @NonNull final BiConsumer<NodeId, Long> pingConsumer,
            @NonNull final Metrics metrics) {
        Objects.requireNonNull(metrics);

        this.time = Objects.requireNonNull(time);
        this.eventClass = Objects.requireNonNull(eventClass);
        this.prepareReceivedEvent = Objects.requireNonNull(prepareReceivedEvent);
        this.config = Objects.requireNonNull(config);
        this.pingConsumer = Objects.requireNonNull(pingConsumer);

        this.selfEventOutput = new QueueOutputMain<>("selfEvent", config.selfEventQueueCapacity(), metrics);
        this.otherEventOutput = new QueueOutputMain<>("otherEvent", config.otherEventQueueCapacity(), metrics);
        this.hashOutput = new QueueOutputMain<>("descriptor", config.descriptorQueueCapacity(), metrics);
        this.peerInstances = new HashMap<>();

        this.msgsPerSecRead = new CountPerSecond(
                metrics,
                new CountPerSecond.Config(METRICS_CATEGORY, "msgsPerSecRead")
                        .withDescription("number of chatter messages read per second")
                        .withUnit("messages/second"));
        this.msgsPerSecWrit = new CountPerSecond(
                metrics,
                new CountPerSecond.Config(METRICS_CATEGORY, "msgsPerSecWrit")
                        .withDescription("number of chatter messages written per second")
                        .withUnit("messages/second"));
        selfProcessingTime =
                metrics.getOrCreate(new DurationGauge.Config(METRICS_CATEGORY, "eventProcTime", ChronoUnit.MILLIS)
                        .withDescription("the time it takes to process and validate an event"));
    }

    /**
     * Creates an instance that will handle all communication with a peer
     * <p>
     *
     * @param peerId       the peer's ID
     * @param eventHandler a handler that will send the event outside of chatter
     */
    public void newPeerInstance(final NodeId peerId, final MessageHandler<E> eventHandler) {

        final PeerGossipState state = new PeerGossipState(config.futureGenerationLimit());
        final CommunicationState communicationState = new CommunicationState();
        final HeartbeatSendReceive heartbeat =
                new HeartbeatSendReceive(time, peerId, pingConsumer, config.heartbeatInterval());

        final ProcessingTimeSendReceive processingTimeSendReceive =
                new ProcessingTimeSendReceive(time, config.processingTimeInterval(), selfProcessingTime::getNanos);

        final MessageProvider hashPeerInstance = hashOutput.createPeerInstance(
                communicationState, d -> SendAction.SEND // always send hashes
                );
        final MessageProvider selfEventPeerInstance = selfEventOutput.createPeerInstance(
                communicationState, d -> SendAction.SEND // always send self events
                );
        final MessageProvider otherEventPeerInstance = otherEventOutput.createPeerInstance(
                communicationState,
                new VariableTimeDelay<>(
                        new OtherEventDelay(
                                heartbeat::getLastRoundTripNanos,
                                processingTimeSendReceive::getPeerProcessingTime,
                                config.otherEventDelay())::getOtherEventDelay,
                        state,
                        time::now));
        final PriorityOutputAggregator outputAggregator = new PriorityOutputAggregator(
                List.of(
                        // heartbeat is first so that responses are not delayed
                        heartbeat,
                        processingTimeSendReceive,
                        hashPeerInstance,
                        selfEventPeerInstance,
                        otherEventPeerInstance),
                msgsPerSecWrit);
        final InputDelegate inputDelegate = InputDelegateBuilder.builder()
                .addHandler(MessageTypeHandlerBuilder.builder(eventClass)
                        .addHandler(prepareReceivedEvent)
                        .addHandler(state::handleEvent)
                        .addHandler(eventHandler)
                        .build())
                .addHandler(MessageTypeHandlerBuilder.builder(EventDescriptor.class)
                        .addHandler(state::handleDescriptor)
                        .build())
                .addHandler(MessageTypeHandlerBuilder.builder(HeartbeatMessage.class)
                        .addHandler(heartbeat)
                        .build())
                .addHandler(MessageTypeHandlerBuilder.builder(ProcessingTimeMessage.class)
                        .addHandler(processingTimeSendReceive)
                        .build())
                .setStat(msgsPerSecRead)
                .build();
        final PeerInstance peerInstance = new PeerInstance(communicationState, state, outputAggregator, inputDelegate);
        peerInstances.put(peerId, peerInstance);
    }

    /**
     * @param id the ID of the peer
     * @return the instance responsible for all communication with a peer
     */
    @Nullable
    public PeerInstance getPeerInstance(@Nullable final NodeId id) {
        return peerInstances.get(id);
    }

    /**
     * @return the instances responsible for all communication with all peers
     */
    public Collection<PeerInstance> getPeerInstances() {
        return peerInstances.values();
    }

    /**
     * Notify chatter that a new event has been created
     * <p>
     *
     * @param event the new event
     */
    public void eventCreated(final E event) {
        selfEventOutput.send(event);
        recordProcessingTime(event);
    }

    /**
     * Notify chatter that an event has been received and validated
     * <p>
     *
     * @param event the event received
     */
    public void eventReceived(final E event) {
        hashOutput.send(event.getDescriptor());
        otherEventOutput.send(event);
        recordProcessingTime(event);
    }

    private void recordProcessingTime(final E event) {
        selfProcessingTime.set(Duration.between(event.getTimeReceived(), time.now()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shiftWindow(final long firstSequenceNumberInWindow) {
        for (final PeerInstance peer : peerInstances.values()) {
            peer.state().shiftWindow(firstSequenceNumberInWindow);
        }
    }

    @Override
    public void loadFromSignedState(final SignedState signedState) {
        shiftWindow(signedState.getMinRoundGeneration());
    }

    /**
     * Stop chattering with all peers
     */
    public void stopChatter() {
        // set the chatter state to suspended for all peers
        for (final PeerInstance peer : peerInstances.values()) {
            peer.communicationState().suspend();
        }
        // wait for all communication to end
        for (final PeerInstance peer : peerInstances.values()) {
            while (peer.communicationState().isAnyProtocolRunning()) {
                try {
                    // we assume the thread calling this will never be interrupted
                    Thread.sleep(STOP_WAIT_SLEEP_MILLIS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        // clear all queues
        for (final PeerInstance peer : peerInstances.values()) {
            peer.outputAggregator().clear();
        }
    }

    /**
     * Start chatter if it has been previously stopped
     */
    public void startChatter() {
        // allow chatter to start
        for (final PeerInstance peer : peerInstances.values()) {
            peer.communicationState().unsuspend();
        }
    }
}
