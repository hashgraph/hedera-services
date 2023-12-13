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

package com.swirlds.platform.wiring;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.system.status.PlatformStatusManager;
import com.swirlds.platform.wiring.components.EventCreationManagerWiring;
import com.swirlds.platform.wiring.components.EventHasherWiring;
import com.swirlds.platform.wiring.components.PcesReplayerWiring;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

import static com.swirlds.common.wiring.wires.SolderType.INJECT;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring implements Startable, Stoppable, Clearable {
    private final PlatformContext platformContext;
    private final WiringModel model;

    private final EventHasherWiring eventHasherWiring;
    private final InternalEventValidatorWiring internalEventValidatorWiring;
    private final EventDeduplicatorWiring eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final LinkedEventIntakeWiring linkedEventIntakeWiring;
    private final EventCreationManagerWiring eventCreationManagerWiring;
    private final SignedStateFileManagerWiring signedStateFileManagerWiring;
    private final StateSignerWiring stateSignerWiring;
    private final PcesReplayerWiring pcesReplayerWiring;

    private final PlatformCoordinator platformCoordinator;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    public PlatformWiring(@NonNull final PlatformContext platformContext, @NonNull final Time time) {

        this.platformContext = Objects.requireNonNull(platformContext);
        model = WiringModel.create(platformContext, time);

        final PlatformSchedulers schedulers = PlatformSchedulers.create(platformContext, model);

        // the new intake pipeline components must only be constructed if they are enabled
        // this ensures that no exception will arise for unbound wires
        if (!platformContext.getConfiguration().getConfigData(EventConfig.class).useLegacyIntake()) {
            internalEventValidatorWiring =
                    InternalEventValidatorWiring.create(schedulers.internalEventValidatorScheduler());
            eventDeduplicatorWiring = EventDeduplicatorWiring.create(schedulers.eventDeduplicatorScheduler());
            eventSignatureValidatorWiring =
                    EventSignatureValidatorWiring.create(schedulers.eventSignatureValidatorScheduler());
            orphanBufferWiring = OrphanBufferWiring.create(schedulers.orphanBufferScheduler());
            inOrderLinkerWiring = InOrderLinkerWiring.create(schedulers.inOrderLinkerScheduler());
            linkedEventIntakeWiring = LinkedEventIntakeWiring.create(schedulers.linkedEventIntakeScheduler());
            eventCreationManagerWiring =
                    EventCreationManagerWiring.create(platformContext, schedulers.eventCreationManagerScheduler());
            platformCoordinator = new PlatformCoordinator(
                    internalEventValidatorWiring,
                    eventDeduplicatorWiring,
                    eventSignatureValidatorWiring,
                    orphanBufferWiring,
                    inOrderLinkerWiring,
                    linkedEventIntakeWiring,
                    eventCreationManagerWiring);
        } else {
            internalEventValidatorWiring = null;
            eventDeduplicatorWiring = null;
            eventSignatureValidatorWiring = null;
            orphanBufferWiring = null;
            inOrderLinkerWiring = null;
            linkedEventIntakeWiring = null;
            eventCreationManagerWiring = null;
            platformCoordinator = null;
        }

        eventHasherWiring = EventHasherWiring.create(schedulers.eventHasherScheduler());
        signedStateFileManagerWiring =
                SignedStateFileManagerWiring.create(schedulers.signedStateFileManagerScheduler());
        stateSignerWiring = StateSignerWiring.create(schedulers.stateSignerScheduler());
        pcesReplayerWiring = PcesReplayerWiring.create(schedulers.pcesReplayerScheduler());

        wire();
    }

    /**
     * Get the wiring model.
     *
     * @return the wiring model
     */
    @NonNull
    public WiringModel getModel() {
        return model;
    }

    /**
     * Solder the minimum generation non-ancient output to all components that need it.
     */
    private void solderMinimumGenerationNonAncient() {
        final OutputWire<Long> minimumGenerationNonAncientOutput =
                linkedEventIntakeWiring.minimumGenerationNonAncientOutput();

        minimumGenerationNonAncientOutput.solderTo(eventDeduplicatorWiring.minimumGenerationNonAncientInput(), INJECT);
        minimumGenerationNonAncientOutput.solderTo(
                eventSignatureValidatorWiring.minimumGenerationNonAncientInput(), INJECT);
        minimumGenerationNonAncientOutput.solderTo(orphanBufferWiring.minimumGenerationNonAncientInput(), INJECT);
        minimumGenerationNonAncientOutput.solderTo(inOrderLinkerWiring.minimumGenerationNonAncientInput(), INJECT);
        minimumGenerationNonAncientOutput.solderTo(
                eventCreationManagerWiring.minimumGenerationNonAncientInput(), INJECT);
    }

    /**
     * Wire the components together.
     */
    private void wire() {
        final boolean useLegacyIntake = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .useLegacyIntake();

        if (!useLegacyIntake) {
            eventHasherWiring.eventOutput().solderTo(internalEventValidatorWiring.eventInput());
            internalEventValidatorWiring.eventOutput().solderTo(eventDeduplicatorWiring.eventInput());
            eventDeduplicatorWiring.eventOutput().solderTo(eventSignatureValidatorWiring.eventInput());
            eventSignatureValidatorWiring.eventOutput().solderTo(orphanBufferWiring.eventInput());
            orphanBufferWiring.eventOutput().solderTo(inOrderLinkerWiring.eventInput());
            inOrderLinkerWiring.eventOutput().solderTo(linkedEventIntakeWiring.eventInput());
            orphanBufferWiring.eventOutput().solderTo(eventCreationManagerWiring.eventInput());
            eventCreationManagerWiring.newEventOutput().solderTo(internalEventValidatorWiring.eventInput(), INJECT);

            solderMinimumGenerationNonAncient();
        }
    }

    /**
     * Wire components that adhere to the framework to components that don't
     * <p>
     * Future work: as more components are moved to the framework, this method should shrink, and eventually be
     * removed.
     *
     * @param statusManager             the status manager to wire
     * @param appCommunicationComponent the app communication component to wire
     * @param transactionPool           the transaction pool to wire
     */
    public void wireExternalComponents(
            @NonNull final PlatformStatusManager statusManager,
            @NonNull final AppCommunicationComponent appCommunicationComponent,
            @NonNull final TransactionPool transactionPool,
            @NonNull final QueueThread<GossipEvent> intakeQueue) {

        signedStateFileManagerWiring
                .stateWrittenToDiskOutputWire()
                .solderTo("status manager", statusManager::submitStatusAction);
        signedStateFileManagerWiring
                .stateSavingResultOutputWire()
                .solderTo("app communication", appCommunicationComponent::stateSavedToDisk);
        stateSignerWiring.stateSignature().solderTo("transaction pool", transactionPool::submitSystemTransaction);

        final boolean useLegacyIntake = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .useLegacyIntake();

        if (useLegacyIntake) {
            // if the new intake pipeline is enabled, this output is soldered in the wiring
            // in this case where legacy intake is active, we need to solder the hasher output into the intake queue
            eventHasherWiring.eventOutput().solderTo("intake queue", intakeQueue::add);
        }
    }

    /**
     * Bind the intake components to the wiring.
     * <p>
     * Future work: this method should be merged with {@link #bind} once the feature flag for the new intake pipeline
     * has been removed
     *
     * @param internalEventValidator  the internal event validator to bind
     * @param eventDeduplicator       the event deduplicator to bind
     * @param eventSignatureValidator the event signature validator to bind
     * @param orphanBuffer            the orphan buffer to bind
     * @param inOrderLinker           the in order linker to bind
     * @param linkedEventIntake       the linked event intake to bind
     * @param eventCreationManager    the event creation manager to bind
     */
    public void bindIntake(
            @NonNull final InternalEventValidator internalEventValidator,
            @NonNull final EventDeduplicator eventDeduplicator,
            @NonNull final EventSignatureValidator eventSignatureValidator,
            @NonNull final OrphanBuffer orphanBuffer,
            @NonNull final InOrderLinker inOrderLinker,
            @NonNull final LinkedEventIntake linkedEventIntake,
            @NonNull final EventCreationManager eventCreationManager) {

        internalEventValidatorWiring.bind(internalEventValidator);
        eventDeduplicatorWiring.bind(eventDeduplicator);
        eventSignatureValidatorWiring.bind(eventSignatureValidator);
        orphanBufferWiring.bind(orphanBuffer);
        inOrderLinkerWiring.bind(inOrderLinker);
        linkedEventIntakeWiring.bind(linkedEventIntake);
        eventCreationManagerWiring.bind(eventCreationManager);
    }

    /**
     * Bind components to the wiring.
     *
     * @param eventHasher            the event hasher to bind
     * @param signedStateFileManager the signed state file manager to bind
     * @param stateSigner            the state signer to bind
     * @param pcesReplayer           the PCES replayer to bind
     */
    public void bind(
            @NonNull final EventHasher eventHasher, @NonNull final SignedStateFileManager signedStateFileManager, @NonNull final StateSigner stateSigner, @NonNull final PcesReplayer pcesReplayer) {
        eventHasherWiring.bind(eventHasher);
        signedStateFileManagerWiring.bind(signedStateFileManager);
        stateSignerWiring.bind(stateSigner);
        pcesReplayerWiring.bind(pcesReplayer);

        // FUTURE WORK: bind all the things!
    }

    /**
     * Get the input wire for the internal event validator.
     * <p>
     * Future work: this is a temporary hook to allow events from gossip to use the new intake pipeline. This method
     * will be removed once gossip is moved to the new framework
     *
     * @return the input method for the internal event validator, which is the first step in the intake pipeline
     */
    @NonNull
    public InputWire<GossipEvent> getEventInput() {
        return internalEventValidatorWiring.eventInput();
    }

    /**
     * Get the input wire for the address book update.
     * <p>
     * Future work: this is a temporary hook to update the address book in the new intake pipeline.
     *
     * @return the input method for the address book update
     */
    @NonNull
    public InputWire<AddressBookUpdate> getAddressBookUpdateInput() {
        return eventSignatureValidatorWiring.addressBookUpdateInput();
    }

    /**
     * Get the input wire for saving a state to disk
     * <p>
     * Future work: this is a temporary hook to allow the components to save state a state to disk, prior to the whole
     * system being migrated to the new framework.
     *
     * @return the input wire for saving a state to disk
     */
    @NonNull
    public InputWire<ReservedSignedState> getSaveStateToDiskInput() {
        return signedStateFileManagerWiring.saveStateToDisk();
    }

    /**
     * Get the input wire for dumping a state to disk
     * <p>
     * Future work: this is a temporary hook to allow the components to dump a state to disk, prior to the whole system
     * being migrated to the new framework.
     *
     * @return the input wire for dumping a state to disk
     */
    @NonNull
    public InputWire<StateDumpRequest> getDumpStateToDiskInput() {
        return signedStateFileManagerWiring.dumpStateToDisk();
    }

    /**
     * Get the input wire for signing a state
     * <p>
     * Future work: this is a temporary hook to allow the components to sign a state, prior to the whole
     * system being migrated to the new framework.
     *
     * @return the input wire for signing a state
     */
    @NonNull
    public InputWire<ReservedSignedState> getSignStateInput() {
        return stateSignerWiring.signState();
    }

    /**
     * Get the input wire for passing a PCES iterator to the replayer.
     *
     * @return the input wire for passing a PCES iterator to the replayer
     */
    public InputWire<IOIterator<GossipEvent>> getPcesReplayerIteratorInput() {
        return pcesReplayerWiring.pcesIteratorInputWire();
    }

    /**
     * Get the output wire that the replayer uses to pass events from file into the intake pipeline.
     *
     * @return the output wire that the replayer uses to pass events from file into the intake pipeline
     */
    public OutputWire<GossipEvent> getPcesReplayerEventOutput() {
        return pcesReplayerWiring.eventOutputWire();
    }

    /**
     * Inject a new minimum generation non-ancient on all components that need it.
     * <p>
     * Future work: this is a temporary hook to allow the components to get the minimum generation non-ancient during
     * startup. This method will be removed once the components are wired together.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void updateMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        eventDeduplicatorWiring.minimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
        eventSignatureValidatorWiring.minimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
        orphanBufferWiring.minimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
        inOrderLinkerWiring.minimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
        eventCreationManagerWiring.minimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
    }

    /**
     * Flush the intake pipeline.
     */
    public void flushIntakePipeline() {
        platformCoordinator.flushIntakePipeline();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        model.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        model.stop();
    }

    /**
     * Clear all the wiring objects.
     */
    @Override
    public void clear() {
        final boolean useLegacyIntake = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .useLegacyIntake();

        if (!useLegacyIntake) {
            platformCoordinator.clear();
        }
    }
}
