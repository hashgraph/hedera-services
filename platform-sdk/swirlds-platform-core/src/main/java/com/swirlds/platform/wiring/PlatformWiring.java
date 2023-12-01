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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.system.status.PlatformStatusManager;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.wiring.components.EventCreationManagerWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring implements Startable, Stoppable {
    private final PlatformContext platformContext;
    private final WiringModel model;

    private final InternalEventValidatorWiring internalEventValidatorWiring;
    private final EventDeduplicatorWiring eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final LinkedEventIntakeWiring linkedEventIntakeWiring;
    private final EventCreationManagerWiring eventCreationManagerWiring;

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

        internalEventValidatorWiring =
                InternalEventValidatorWiring.create(schedulers.internalEventValidatorScheduler());
        eventDeduplicatorWiring = EventDeduplicatorWiring.create(schedulers.eventDeduplicatorScheduler());
        eventSignatureValidatorWiring =
                EventSignatureValidatorWiring.create(schedulers.eventSignatureValidatorScheduler());
        orphanBufferWiring = OrphanBufferWiring.create(schedulers.orphanBufferScheduler());
        inOrderLinkerWiring = InOrderLinkerWiring.create(schedulers.inOrderLinkerScheduler());
        linkedEventIntakeWiring = LinkedEventIntakeWiring.create(schedulers.linkedEventIntakeScheduler());
        eventCreationManagerWiring = EventCreationManagerWiring.create(schedulers.eventCreationManagerScheduler());

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
        internalEventValidatorWiring.eventOutput().solderTo(eventDeduplicatorWiring.eventInput());
        eventDeduplicatorWiring.eventOutput().solderTo(eventSignatureValidatorWiring.eventInput());
        eventSignatureValidatorWiring.eventOutput().solderTo(orphanBufferWiring.eventInput());
        orphanBufferWiring.eventOutput().solderTo(inOrderLinkerWiring.eventInput());
        orphanBufferWiring.eventOutput().solderTo(eventCreationManagerWiring.eventInput());
        inOrderLinkerWiring.eventOutput().solderTo(linkedEventIntakeWiring.eventInput());

        eventCreationManagerWiring.newEventOutput().solderTo(internalEventValidatorWiring.eventInput(), INJECT);

        solderMinimumGenerationNonAncient();
    }

    /**
     * Bind components to the wiring.
     *
     * @param internalEventValidator  the internal event validator to bind
     * @param eventDeduplicator       the event deduplicator to bind
     * @param eventSignatureValidator the event signature validator to bind
     * @param orphanBuffer            the orphan buffer to bind
     * @param inOrderLinker           the in order linker to bind
     * @param linkedEventIntake       the linked event intake to bind
     * @param eventCreationManager    the event creation manager to bind
     */
    public void bind(
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
     * Get the input method for the internal event validator.
     * <p>
     * Future work: this is a temporary hook to allow events from gossip to use the new intake pipeline. This method
     * will be removed once gossip is moved to the new framework
     *
     * @return the input method for the internal event validator, which is the first step in the intake pipeline
     */
    public InterruptableConsumer<GossipEvent> getEventInput() {
        return internalEventValidatorWiring.eventInput()::put;
    }

    /**
     * Get the input method for the address book update.
     * <p>
     * Future work: this is a temporary hook to update the address book in the new intake pipeline.
     *
     * @return the input method for the address book update
     */
    public Consumer<AddressBookUpdate> getAddressBookUpdateInput() {
        return eventSignatureValidatorWiring.addressBookUpdateInput()::inject;
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
     * Flush all the wiring objects
     */
    private void flushAll() {
        internalEventValidatorWiring.flushRunnable().run();
        eventDeduplicatorWiring.flushRunnable().run();
        eventSignatureValidatorWiring.flushRunnable().run();
        orphanBufferWiring.flushRunnable().run();
        eventCreationManagerWiring.flush();
        inOrderLinkerWiring.flushRunnable().run();
        linkedEventIntakeWiring.flushRunnable().run();
    }

    /**
     * Clear all the wiring objects in preparation for a reconnect.
     * <p>
     * This doesn't guarantee that all objects will have nothing in their internal storage, but it does guarantee that
     * the objects will no longer be emitting any events or rounds.
     */
    public void clear(@NonNull final PlatformStatusManager platformStatusManager) {
        if (!platformContext.getConfiguration().getConfigData(EventConfig.class).useLegacyIntake()) {

            // Prior to clearing, we need to make sure that the proper platform status has become effective.
            // If due to some crazy race condition we do not have the proper status when we unpause the event
            // creator then we may accidentally create a new event after flushing out pipelines.
            while (platformStatusManager.getCurrentStatus() != PlatformStatus.BEHIND) {
                try {
                    MILLISECONDS.sleep(1);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // pause the orphan buffer to break the cycle, and flush the pause through
            orphanBufferWiring.pauseInput().inject(true);
            orphanBufferWiring.flushRunnable().run();

            eventCreationManagerWiring.pauseInput().inject(true);
            eventCreationManagerWiring.flush();

            // now that no cycles exist, flush all the wiring objects
            flushAll();

            // once everything has been flushed through the system, it's safe to unpause the orphan buffer
            orphanBufferWiring.pauseInput().inject(false);
            eventCreationManagerWiring.pauseInput().inject(false);
        }
    }
}
