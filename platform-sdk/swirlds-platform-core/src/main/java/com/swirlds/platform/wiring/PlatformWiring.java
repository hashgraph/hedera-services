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

import static com.swirlds.common.wiring.wires.SolderType.INJECT;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
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
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.PreconsensusEventWriter;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.state.SwirldStateManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.state.signed.StateDumpRequest;
import com.swirlds.platform.system.status.PlatformStatusManager;
import com.swirlds.platform.wiring.components.ApplicationTransactionPrehandlerWiring;
import com.swirlds.platform.wiring.components.EventCreationManagerWiring;
import com.swirlds.platform.wiring.components.StateSignatureCollectorWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring implements Startable, Stoppable, Clearable {
    private final PlatformContext platformContext;
    private final WiringModel model;

    private final InternalEventValidatorWiring internalEventValidatorWiring;
    private final EventDeduplicatorWiring eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final LinkedEventIntakeWiring linkedEventIntakeWiring;
    private final EventCreationManagerWiring eventCreationManagerWiring;
    private final SignedStateFileManagerWiring signedStateFileManagerWiring;
    private final StateSignerWiring stateSignerWiring;
    private final ApplicationTransactionPrehandlerWiring applicationTransactionPrehandlerWiring;
    private final StateSignatureCollectorWiring stateSignatureCollectorWiring;

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

            applicationTransactionPrehandlerWiring = ApplicationTransactionPrehandlerWiring.create(
                    schedulers.applicationTransactionPrehandlerScheduler());
            stateSignatureCollectorWiring =
                    StateSignatureCollectorWiring.create(model, schedulers.stateSignatureCollectorScheduler());

            platformCoordinator = new PlatformCoordinator(
                    internalEventValidatorWiring,
                    eventDeduplicatorWiring,
                    eventSignatureValidatorWiring,
                    orphanBufferWiring,
                    inOrderLinkerWiring,
                    linkedEventIntakeWiring,
                    eventCreationManagerWiring,
                    applicationTransactionPrehandlerWiring,
                    stateSignatureCollectorWiring);
        } else {
            internalEventValidatorWiring = null;
            eventDeduplicatorWiring = null;
            eventSignatureValidatorWiring = null;
            orphanBufferWiring = null;
            inOrderLinkerWiring = null;
            linkedEventIntakeWiring = null;
            eventCreationManagerWiring = null;
            platformCoordinator = null;
            applicationTransactionPrehandlerWiring = null;
            stateSignatureCollectorWiring = null;
        }

        signedStateFileManagerWiring =
                SignedStateFileManagerWiring.create(schedulers.signedStateFileManagerScheduler());
        stateSignerWiring = StateSignerWiring.create(schedulers.stateSignerScheduler());

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
        if (!platformContext.getConfiguration().getConfigData(EventConfig.class).useLegacyIntake()) {
            internalEventValidatorWiring.eventOutput().solderTo(eventDeduplicatorWiring.eventInput());
            eventDeduplicatorWiring.eventOutput().solderTo(eventSignatureValidatorWiring.eventInput());
            eventSignatureValidatorWiring.eventOutput().solderTo(orphanBufferWiring.eventInput());
            orphanBufferWiring.eventOutput().solderTo(inOrderLinkerWiring.eventInput());
            inOrderLinkerWiring.eventOutput().solderTo(linkedEventIntakeWiring.eventInput());
            orphanBufferWiring.eventOutput().solderTo(eventCreationManagerWiring.eventInput());
            eventCreationManagerWiring.newEventOutput().solderTo(internalEventValidatorWiring.eventInput(), INJECT);
            orphanBufferWiring
                    .eventOutput()
                    .solderTo(applicationTransactionPrehandlerWiring.appTransactionsToPrehandleInput());
            orphanBufferWiring.eventOutput().solderTo(stateSignatureCollectorWiring.preconsensusEventInput());

            solderMinimumGenerationNonAncient();
        }
    }

    /**
     * Wire components that adhere to the framework to components that don't
     * <p>
     * Future work: as more components are moved to the framework, this method should shrink, and eventually be
     * removed.
     *
     * @param preconsensusEventWriter   the preconsensus event writer to wire
     * @param statusManager             the status manager to wire
     * @param appCommunicationComponent the app communication component to wire
     * @param transactionPool           the transaction pool to wire
     */
    public void wireExternalComponents(
            @NonNull final PreconsensusEventWriter preconsensusEventWriter,
            @NonNull final PlatformStatusManager statusManager,
            @NonNull final AppCommunicationComponent appCommunicationComponent,
            @NonNull final TransactionPool transactionPool) {

        signedStateFileManagerWiring
                .oldestMinimumGenerationOnDiskOutputWire()
                .solderTo(
                        "PCES minimum generation to store",
                        preconsensusEventWriter::setMinimumGenerationToStoreUninterruptably);
        signedStateFileManagerWiring
                .stateWrittenToDiskOutputWire()
                .solderTo("status manager", statusManager::submitStatusAction);
        signedStateFileManagerWiring
                .stateSavingResultOutputWire()
                .solderTo("app communication", appCommunicationComponent::stateSavedToDisk);
        stateSignerWiring.stateSignature().solderTo("transaction pool", transactionPool::submitSystemTransaction);
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
     * @param swirldStateManager      the swirld state manager to bind
     * @param signedStateManager      the signed state manager to bind
     */
    public void bindIntake(
            @NonNull final InternalEventValidator internalEventValidator,
            @NonNull final EventDeduplicator eventDeduplicator,
            @NonNull final EventSignatureValidator eventSignatureValidator,
            @NonNull final OrphanBuffer orphanBuffer,
            @NonNull final InOrderLinker inOrderLinker,
            @NonNull final LinkedEventIntake linkedEventIntake,
            @NonNull final EventCreationManager eventCreationManager,
            @NonNull final SwirldStateManager swirldStateManager,
            @NonNull final SignedStateManager signedStateManager) {

        internalEventValidatorWiring.bind(internalEventValidator);
        eventDeduplicatorWiring.bind(eventDeduplicator);
        eventSignatureValidatorWiring.bind(eventSignatureValidator);
        orphanBufferWiring.bind(orphanBuffer);
        inOrderLinkerWiring.bind(inOrderLinker);
        linkedEventIntakeWiring.bind(linkedEventIntake);
        eventCreationManagerWiring.bind(eventCreationManager);
        applicationTransactionPrehandlerWiring.bind(swirldStateManager);
        stateSignatureCollectorWiring.bind(signedStateManager);
    }

    /**
     * Bind components to the wiring.
     *
     * @param signedStateFileManager the signed state file manager to bind
     * @param stateSigner            the state signer to bind
     */
    public void bind(
            @NonNull final SignedStateFileManager signedStateFileManager, @NonNull final StateSigner stateSigner) {
        signedStateFileManagerWiring.bind(signedStateFileManager);
        stateSignerWiring.bind(stateSigner);

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
     * Future work: this is a temporary hook to allow the components to sign a state, prior to the whole system being
     * migrated to the new framework.
     *
     * @return the input wire for signing a state
     */
    @NonNull
    public InputWire<ReservedSignedState> getSignStateInput() {
        return stateSignerWiring.signState();
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
