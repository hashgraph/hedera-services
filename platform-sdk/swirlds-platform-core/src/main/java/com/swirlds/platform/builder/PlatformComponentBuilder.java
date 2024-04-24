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

package com.swirlds.platform.builder;

import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getGlobalMetrics;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;

import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.components.consensus.ConsensusEngine;
import com.swirlds.platform.components.consensus.DefaultConsensusEngine;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.event.creation.DefaultEventCreationManager;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.creation.EventCreator;
import com.swirlds.platform.event.creation.rules.AggregateEventCreationRules;
import com.swirlds.platform.event.creation.rules.BackpressureRule;
import com.swirlds.platform.event.creation.rules.EventCreationRule;
import com.swirlds.platform.event.creation.rules.MaximumRateRule;
import com.swirlds.platform.event.creation.rules.PlatformStatusRule;
import com.swirlds.platform.event.creation.tipset.TipsetEventCreator;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.deduplication.StandardEventDeduplicator;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.linking.GossipLinker;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.DefaultOrphanBuffer;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.DefaultPcesSequencer;
import com.swirlds.platform.event.preconsensus.DefaultPcesWriter;
import com.swirlds.platform.event.preconsensus.PcesFileManager;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.preconsensus.durability.DefaultRoundDurabilityBuffer;
import com.swirlds.platform.event.preconsensus.durability.RoundDurabilityBuffer;
import com.swirlds.platform.event.runninghash.DefaultRunningEventHasher;
import com.swirlds.platform.event.runninghash.RunningEventHasher;
import com.swirlds.platform.event.signing.DefaultSelfEventSigner;
import com.swirlds.platform.event.signing.SelfEventSigner;
import com.swirlds.platform.event.stream.ConsensusEventStream;
import com.swirlds.platform.event.stream.DefaultConsensusEventStream;
import com.swirlds.platform.event.validation.DefaultEventSignatureValidator;
import com.swirlds.platform.event.validation.DefaultInternalEventValidator;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.DefaultTransactionPrehandler;
import com.swirlds.platform.eventhandling.TransactionPrehandler;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.DefaultStateGarbageCollector;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.StateGarbageCollector;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * The advanced platform builder is responsible for constructing platform components. This class is exposed so that
 * individual components can be replaced with alternate implementations.
 * <p>
 * In order to be considered a "component", an object must meet the following criteria:
 * <ul>
 *     <li>A component must not require another component as a constructor argument.</li>
 *     <li>A component's constructor should only use things from the {@link PlatformBuildingBlocks} or things derived
 *     from things from the {@link PlatformBuildingBlocks}.</li>
 *     <li>A component must not communicate with other components except through the wiring framework
 *         (with a very small number of exceptions due to tech debt that has not yet been paid off).</li>
 *     <li>A component should have an interface and at default implementation.</li>
 *     <li>A component should use {@link com.swirlds.common.wiring.component.ComponentWiring ComponentWiring} to define
 *         wiring API.</li>
 *     <li>The order in which components are constructed should not matter.</li>
 *     <li>A component must not be a static singleton or use static stateful variables in any way.</li>
 * </ul>
 */
public class PlatformComponentBuilder {

    private final PlatformBuildingBlocks blocks;

    private EventHasher eventHasher;
    private InternalEventValidator internalEventValidator;
    private EventDeduplicator eventDeduplicator;
    private EventSignatureValidator eventSignatureValidator;
    private SelfEventSigner selfEventSigner;
    private StateGarbageCollector stateGarbageCollector;
    private OrphanBuffer orphanBuffer;
    private RunningEventHasher runningEventHasher;
    private EventCreationManager eventCreationManager;
    private InOrderLinker inOrderLinker;
    private ConsensusEngine consensusEngine;
    private ConsensusEventStream consensusEventStream;
    private PcesSequencer pcesSequencer;
    private RoundDurabilityBuffer roundDurabilityBuffer;
    private TransactionPrehandler transactionPrehandler;
    private PcesWriter pcesWriter;

    /**
     * False if this builder has not yet been used to build a platform (or platform component builder), true if it has.
     */
    private boolean used;

    /**
     * Constructor.
     *
     * @param blocks the build context for the platform under construction, contains all data needed to construct
     *               platform components
     */
    public PlatformComponentBuilder(@NonNull final PlatformBuildingBlocks blocks) {
        this.blocks = Objects.requireNonNull(blocks);
    }

    /**
     * Get the build context for this platform. Contains all data needed to construct platform components.
     *
     * @return the build context
     */
    @NonNull
    public PlatformBuildingBlocks getBuildingBlocks() {
        return blocks;
    }

    /**
     * Throw an exception if this builder has been used to build a platform or a platform factory.
     */
    private void throwIfAlreadyUsed() {
        if (used) {
            throw new IllegalStateException("PlatformBuilder has already been used");
        }
    }

    /**
     * Build the platform.
     *
     * @return the platform
     */
    @NonNull
    public Platform build() {
        throwIfAlreadyUsed();
        used = true;

        try (final ReservedSignedState initialState = blocks.initialState()) {
            return new SwirldsPlatform(this);
        } finally {

            // Future work: eliminate the static variables that require this code to exist
            if (blocks.firstPlatform()) {
                MetricsDocUtils.writeMetricsDocumentToFile(
                        getGlobalMetrics(),
                        getPlatforms(),
                        blocks.platformContext().getConfiguration());
                getMetricsProvider().start();
            }
        }
    }

    /**
     * Provide an event hasher in place of the platform's default event hasher.
     *
     * @param eventHasher the event hasher to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withEventHasher(@NonNull final EventHasher eventHasher) {
        throwIfAlreadyUsed();
        if (this.eventHasher != null) {
            throw new IllegalStateException("Event hasher has already been set");
        }
        this.eventHasher = Objects.requireNonNull(eventHasher);
        return this;
    }

    /**
     * Build the event hasher if it has not yet been built. If one has been provided via
     * {@link #withEventHasher(EventHasher)}, that hasher will be used. If this method is called more than once, only
     * the first call will build the event hasher. Otherwise, the default hasher will be created and returned.
     *
     * @return the event hasher
     */
    @NonNull
    public EventHasher buildEventHasher() {
        if (eventHasher == null) {
            eventHasher = new DefaultEventHasher(blocks.platformContext());
        }
        return eventHasher;
    }

    /**
     * Provide an internal event validator in place of the platform's default internal event validator.
     *
     * @param internalEventValidator the internal event validator to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withInternalEventValidator(
            @NonNull final InternalEventValidator internalEventValidator) {
        throwIfAlreadyUsed();
        if (this.internalEventValidator != null) {
            throw new IllegalStateException("Internal event validator has already been set");
        }
        this.internalEventValidator = Objects.requireNonNull(internalEventValidator);
        return this;
    }

    /**
     * Build the internal event validator if it has not yet been built. If one has been provided via
     * {@link #withInternalEventValidator(InternalEventValidator)}, that validator will be used. If this method is
     * called more than once, only the first call will build the internal event validator. Otherwise, the default
     * validator will be created and returned.
     *
     * @return the internal event validator
     */
    @NonNull
    public InternalEventValidator buildInternalEventValidator() {
        if (internalEventValidator == null) {
            final boolean singleNodeNetwork = blocks.initialState()
                            .get()
                            .getState()
                            .getPlatformState()
                            .getAddressBook()
                            .getSize()
                    == 1;
            internalEventValidator = new DefaultInternalEventValidator(
                    blocks.platformContext(), singleNodeNetwork, blocks.intakeEventCounter());
        }
        return internalEventValidator;
    }

    /**
     * Provide an event deduplicator in place of the platform's default event deduplicator.
     *
     * @param eventDeduplicator the event deduplicator to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withEventDeduplicator(@NonNull final EventDeduplicator eventDeduplicator) {
        throwIfAlreadyUsed();
        if (this.eventDeduplicator != null) {
            throw new IllegalStateException("Event deduplicator has already been set");
        }
        this.eventDeduplicator = Objects.requireNonNull(eventDeduplicator);
        return this;
    }

    /**
     * Build the event deduplicator if it has not yet been built. If one has been provided via
     * {@link #withEventDeduplicator(EventDeduplicator)}, that deduplicator will be used. If this method is called more
     * than once, only the first call will build the event deduplicator. Otherwise, the default deduplicator will be
     * created and returned.
     *
     * @return the event deduplicator
     */
    @NonNull
    public EventDeduplicator buildEventDeduplicator() {
        if (eventDeduplicator == null) {
            eventDeduplicator = new StandardEventDeduplicator(blocks.platformContext(), blocks.intakeEventCounter());
        }
        return eventDeduplicator;
    }

    /**
     * Provide an event signature validator in place of the platform's default event signature validator.
     *
     * @param eventSignatureValidator the event signature validator to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withEventSignatureValidator(
            @NonNull final EventSignatureValidator eventSignatureValidator) {
        throwIfAlreadyUsed();
        if (this.eventSignatureValidator != null) {
            throw new IllegalStateException("Event signature validator has already been set");
        }
        this.eventSignatureValidator = Objects.requireNonNull(eventSignatureValidator);

        return this;
    }

    /**
     * Build the event signature validator if it has not yet been built. If one has been provided via
     * {@link #withEventSignatureValidator(EventSignatureValidator)}, that validator will be used. If this method is
     * called more than once, only the first call will build the event signature validator. Otherwise, the default
     * validator will be created and returned.
     */
    @NonNull
    public EventSignatureValidator buildEventSignatureValidator() {
        if (eventSignatureValidator == null) {
            eventSignatureValidator = new DefaultEventSignatureValidator(
                    blocks.platformContext(),
                    CryptoStatic::verifySignature,
                    blocks.appVersion(),
                    blocks.initialState().get().getState().getPlatformState().getPreviousAddressBook(),
                    blocks.initialState().get().getState().getPlatformState().getAddressBook(),
                    blocks.intakeEventCounter());
        }
        return eventSignatureValidator;
    }

    /**
     * Provide a state garbage collector in place of the platform's default state garbage collector.
     *
     * @param stateGarbageCollector the state garbage collector to use
     * @return this builder
     */
    public PlatformComponentBuilder withStateGarbageCollector(
            @NonNull final StateGarbageCollector stateGarbageCollector) {
        throwIfAlreadyUsed();
        if (this.stateGarbageCollector != null) {
            throw new IllegalStateException("State garbage collector has already been set");
        }
        this.stateGarbageCollector = Objects.requireNonNull(stateGarbageCollector);
        return this;
    }

    /**
     * Build the state garbage collector if it has not yet been built. If one has been provided via
     * {@link #withStateGarbageCollector(StateGarbageCollector)}, that garbage collector will be used. If this method is
     * called more than once, only the first call will build the state garbage collector. Otherwise, the default garbage
     * collector will be created and returned.
     *
     * @return the state garbage collector
     */
    @NonNull
    public StateGarbageCollector buildStateGarbageCollector() {
        if (stateGarbageCollector == null) {
            stateGarbageCollector = new DefaultStateGarbageCollector(blocks.platformContext());
        }
        return stateGarbageCollector;
    }

    /**
     * Provide a self event signer in place of the platform's default self event signer.
     *
     * @param selfEventSigner the self event signer to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withSelfEventSigner(@NonNull final SelfEventSigner selfEventSigner) {
        throwIfAlreadyUsed();
        if (this.selfEventSigner != null) {
            throw new IllegalStateException("Self event signer has already been set");
        }
        this.selfEventSigner = Objects.requireNonNull(selfEventSigner);
        return this;
    }

    /**
     * Build the self event signer if it has not yet been built. If one has been provided via
     * {@link #withSelfEventSigner(SelfEventSigner)}, that signer will be used. If this method is called more than once,
     * only the first call will build the self event signer. Otherwise, the default signer will be created and
     * returned.
     *
     * @return the self event signer
     */
    @NonNull
    public SelfEventSigner buildSelfEventSigner() {
        if (selfEventSigner == null) {
            selfEventSigner = new DefaultSelfEventSigner(blocks.keysAndCerts());
        }
        return selfEventSigner;
    }

    /**
     * Build the orphan buffer if it has not yet been built. If one has been provided via
     * {@link #withOrphanBuffer(OrphanBuffer)}, that orphan buffer will be used. If this method is called more than
     * once, only the first call will build the orphan buffer. Otherwise, the default orphan buffer will be created and
     * returned.
     *
     * @return the orphan buffer
     */
    @NonNull
    public OrphanBuffer buildOrphanBuffer() {
        if (orphanBuffer == null) {
            orphanBuffer = new DefaultOrphanBuffer(blocks.platformContext(), blocks.intakeEventCounter());
        }
        return orphanBuffer;
    }

    /**
     * Provide an orphan buffer in place of the platform's default orphan buffer.
     *
     * @param orphanBuffer the orphan buffer to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withOrphanBuffer(@NonNull final OrphanBuffer orphanBuffer) {
        throwIfAlreadyUsed();
        if (this.orphanBuffer != null) {
            throw new IllegalStateException("Orphan buffer has already been set");
        }
        this.orphanBuffer = Objects.requireNonNull(orphanBuffer);

        return this;
    }

    /**
     * Provide a running event hasher in place of the platform's default running event hasher.
     *
     * @param runningEventHasher the running event hasher to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withRunningEventHasher(@NonNull final RunningEventHasher runningEventHasher) {
        throwIfAlreadyUsed();
        if (this.runningEventHasher != null) {
            throw new IllegalStateException("Running event hasher has already been set");
        }
        this.runningEventHasher = Objects.requireNonNull(runningEventHasher);
        return this;
    }

    /**
     * Build the running event hasher if it has not yet been built. If one has been provided via
     * {@link #withRunningEventHasher(RunningEventHasher)}, that hasher will be used. If this method is called more than
     * once, only the first call will build the running event hasher. Otherwise, the default hasher will be created and
     * returned.
     *
     * @return the running event hasher
     */
    @NonNull
    public RunningEventHasher buildRunningEventHasher() {
        if (runningEventHasher == null) {
            runningEventHasher = new DefaultRunningEventHasher();
        }
        return runningEventHasher;
    }

    /**
     * Provide an event creation manager in place of the platform's default event creation manager.
     *
     * @param eventCreationManager the event creation manager to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withEventCreationManager(@NonNull final EventCreationManager eventCreationManager) {
        throwIfAlreadyUsed();
        if (this.eventCreationManager != null) {
            throw new IllegalStateException("Event creation manager has already been set");
        }
        this.eventCreationManager = Objects.requireNonNull(eventCreationManager);
        return this;
    }

    /**
     * Build the event creation manager if it has not yet been built. If one has been provided via
     * {@link #withEventCreationManager(EventCreationManager)}, that manager will be used. If this method is called more
     * than once, only the first call will build the event creation manager. Otherwise, the default manager will be
     * created and returned.
     *
     * @return the event creation manager
     */
    @NonNull
    public EventCreationManager buildEventCreationManager() {
        if (eventCreationManager == null) {
            final EventCreator eventCreator = new TipsetEventCreator(
                    blocks.platformContext(),
                    blocks.randomBuilder().buildNonCryptographicRandom(),
                    data -> new PlatformSigner(blocks.keysAndCerts()).sign(data),
                    blocks.initialState().get().getState().getPlatformState().getAddressBook(),
                    blocks.selfId(),
                    blocks.appVersion(),
                    blocks.transactionPool());

            final EventCreationRule eventCreationRules = AggregateEventCreationRules.of(
                    new MaximumRateRule(blocks.platformContext()),
                    new BackpressureRule(
                            blocks.platformContext(),
                            () -> blocks.intakeQueueSizeSupplierSupplier().get().getAsLong()),
                    new PlatformStatusRule(() -> blocks.currentPlatformStatus().get(), blocks.transactionPool()));

            eventCreationManager =
                    new DefaultEventCreationManager(blocks.platformContext(), eventCreator, eventCreationRules);
        }
        return eventCreationManager;
    }

    /**
     * Build the in-order linker if it has not yet been built. If one has been provided via
     * {@link #withInOrderLinker(InOrderLinker)}, that in-order linker will be used. If this method is called more than
     * once, only the first call will build the in-order linker. Otherwise, the default in-order linker will be created
     * and returned.
     *
     * @return the in-order linker
     */
    @NonNull
    public InOrderLinker buildInOrderLinker() {
        if (inOrderLinker == null) {
            inOrderLinker = new GossipLinker(blocks.platformContext(), blocks.intakeEventCounter());
        }
        return inOrderLinker;
    }

    /**
     * Provide an in-order linker in place of the platform's default in-order linker.
     *
     * @param inOrderLinker the in-order linker to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withInOrderLinker(@NonNull final InOrderLinker inOrderLinker) {
        throwIfAlreadyUsed();
        if (this.inOrderLinker != null) {
            throw new IllegalStateException("In-order linker has already been set");
        }
        this.inOrderLinker = Objects.requireNonNull(inOrderLinker);

        return this;
    }

    /**
     * Provide a consensus engine in place of the platform's default consensus engine.
     *
     * @param consensusEngine the consensus engine to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withConsensusEngine(@NonNull final ConsensusEngine consensusEngine) {
        throwIfAlreadyUsed();
        if (this.consensusEngine != null) {
            throw new IllegalStateException("Consensus engine has already been set");
        }
        this.consensusEngine = Objects.requireNonNull(consensusEngine);
        return this;
    }

    /**
     * Build the consensus engine if it has not yet been built. If one has been provided via
     * {@link #withConsensusEngine(ConsensusEngine)}, that engine will be used. If this method is called more than once,
     * only the first call will build the consensus engine. Otherwise, the default engine will be created and returned.
     *
     * @return the consensus engine
     */
    @NonNull
    public ConsensusEngine buildConsensusEngine() {
        if (consensusEngine == null) {
            consensusEngine = new DefaultConsensusEngine(
                    blocks.platformContext(), blocks.initialState().get().getAddressBook(), blocks.selfId());
        }
        return consensusEngine;
    }

    /**
     * Provide a consensus event stream in place of the platform's default consensus event stream.
     *
     * @param consensusEventStream the consensus event stream to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withConsensusEventStream(@NonNull final ConsensusEventStream consensusEventStream) {
        throwIfAlreadyUsed();
        if (this.consensusEventStream != null) {
            throw new IllegalStateException("Consensus event stream has already been set");
        }
        this.consensusEventStream = Objects.requireNonNull(consensusEventStream);
        return this;
    }

    /**
     * Build the consensus event stream if it has not yet been built. If one has been provided via
     * {@link #withConsensusEventStream(ConsensusEventStream)}, that stream will be used. If this method is called more
     * than once, only the first call will build the consensus event stream. Otherwise, the default stream will be
     * created and returned.
     *
     * @return the consensus event stream
     */
    @NonNull
    public ConsensusEventStream buildConsensusEventStream() {
        if (consensusEventStream == null) {
            consensusEventStream = new DefaultConsensusEventStream(
                    blocks.platformContext(),
                    blocks.selfId(),
                    (byte[] data) -> new PlatformSigner(blocks.keysAndCerts()).sign(data),
                    "" + blocks.selfId().id(),
                    (EventImpl event) -> event.isLastInRoundReceived()
                            && blocks.isInFreezePeriodReference().get().test(event.getConsensusTimestamp()));
        }
        return consensusEventStream;
    }

    /**
     * Provide a PCES sequencer in place of the platform's default PCES sequencer.
     *
     * @param pcesSequencer the PCES sequencer to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withPcesSequencer(@NonNull final PcesSequencer pcesSequencer) {
        throwIfAlreadyUsed();
        if (this.pcesSequencer != null) {
            throw new IllegalStateException("PCES sequencer has already been set");
        }
        this.pcesSequencer = Objects.requireNonNull(pcesSequencer);
        return this;
    }

    /**
     * Build the PCES sequencer if it has not yet been built. If one has been provided via
     * {@link #withPcesSequencer(PcesSequencer)}, that sequencer will be used. If this method is called more than once,
     * only the first call will build the PCES sequencer. Otherwise, the default sequencer will be created and
     * returned.
     *
     * @return the PCES sequencer
     */
    @NonNull
    public PcesSequencer buildPcesSequencer() {
        if (pcesSequencer == null) {
            pcesSequencer = new DefaultPcesSequencer();
        }
        return pcesSequencer;
    }

    /**
     * Provide a round durability buffer in place of the platform's default round durability buffer.
     *
     * @param roundDurabilityBuffer the RoundDurabilityBuffer to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withRoundDurabilityBuffer(
            @NonNull final RoundDurabilityBuffer roundDurabilityBuffer) {
        throwIfAlreadyUsed();
        if (this.roundDurabilityBuffer != null) {
            throw new IllegalStateException("RoundDurabilityBuffer has already been set");
        }
        this.roundDurabilityBuffer = Objects.requireNonNull(roundDurabilityBuffer);
        return this;
    }

    /**
     * Build the round durability buffer if it has not yet been built. If one has been provided via
     * {@link #withRoundDurabilityBuffer(RoundDurabilityBuffer)}, that round durability buffer will be used. If this
     * method is called more than once, only the first call will build the round durability buffer. Otherwise, the
     * default round durability buffer will be created and returned.
     *
     * @return the RoundDurabilityBuffer
     */
    @NonNull
    public RoundDurabilityBuffer buildRoundDurabilityBuffer() {
        if (roundDurabilityBuffer == null) {
            roundDurabilityBuffer = new DefaultRoundDurabilityBuffer(blocks.platformContext());
        }
        return roundDurabilityBuffer;
    }

    /**
     * Provide a transaction prehandler in place of the platform's default transaction prehandler.
     *
     * @param transactionPrehandler the transaction prehandler to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withTransactionPrehandler(
            @NonNull final TransactionPrehandler transactionPrehandler) {
        throwIfAlreadyUsed();
        if (this.transactionPrehandler != null) {
            throw new IllegalStateException("Transaction prehandler has already been set");
        }
        this.transactionPrehandler = Objects.requireNonNull(transactionPrehandler);
        return this;
    }

    /**
     * Build the transaction prehandler if it has not yet been built. If one has been provided via
     * {@link #withTransactionPrehandler(TransactionPrehandler)}, that transaction prehandler will be used. If this
     * method is called more than once, only the first call will build the transaction prehandler. Otherwise, the
     * default transaction prehandler will be created and returned.
     *
     * @return the transaction prehandler
     */
    @NonNull
    public TransactionPrehandler buildTransactionPrehandler() {
        if (transactionPrehandler == null) {
            transactionPrehandler = new DefaultTransactionPrehandler(
                    blocks.platformContext(),
                    () -> blocks.latestImmutableStateProviderReference().get().apply("transaction prehandle"));
        }
        return transactionPrehandler;
    }

    /**
     * Provide a PCES writer in place of the platform's default PCES writer.
     *
     * @param pcesWriter the PCES writer to use
     * @return this builder
     */
    @NonNull
    public PlatformComponentBuilder withPcesWriter(@NonNull final PcesWriter pcesWriter) {
        throwIfAlreadyUsed();
        if (this.pcesWriter != null) {
            throw new IllegalStateException("PCES writer has already been set");
        }
        this.pcesWriter = Objects.requireNonNull(pcesWriter);
        return this;
    }

    /**
     * Build the PCES writer if it has not yet been built. If one has been provided via
     * {@link #withPcesWriter(PcesWriter)}, that writer will be used. If this method is called more than once, only the
     * first call will build the PCES writer. Otherwise, the default writer will be created and returned.
     *
     * @return the PCES writer
     */
    @NonNull
    public PcesWriter buildPcesWriter() {
        if (pcesWriter == null) {
            try {
                final PcesFileManager preconsensusEventFileManager = new PcesFileManager(
                        blocks.platformContext(),
                        blocks.initialPcesFiles(),
                        blocks.selfId(),
                        blocks.initialState().get().getRound());
                pcesWriter = new DefaultPcesWriter(blocks.platformContext(), preconsensusEventFileManager);

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return pcesWriter;
    }
}
