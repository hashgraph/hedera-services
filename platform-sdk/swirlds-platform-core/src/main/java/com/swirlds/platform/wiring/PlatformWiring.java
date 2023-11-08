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

import static com.swirlds.common.wiring.SolderType.INJECT;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.platform.components.LinkedEventIntake;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.validation.AddressBookUpdate;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * Encapsulates wiring for {@link com.swirlds.platform.SwirldsPlatform}.
 */
public class PlatformWiring implements Startable, Stoppable {

    private final WiringModel model;

    private final InternalEventValidatorScheduler internalEventValidatorScheduler;
    private final EventDeduplicatorScheduler eventDeduplicatorScheduler;
    private final EventSignatureValidatorScheduler eventSignatureValidatorScheduler;
    private final OrphanBufferScheduler orphanBufferScheduler;
    private final InOrderLinkerScheduler inOrderLinkerScheduler;
    private final LinkedEventIntakeScheduler linkedEventIntakeScheduler;

    private final boolean cyclicalBackpressurePresent;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    public PlatformWiring(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        model = WiringModel.create(platformContext, time);

        internalEventValidatorScheduler = new InternalEventValidatorScheduler(model);
        eventDeduplicatorScheduler = new EventDeduplicatorScheduler(model);
        eventSignatureValidatorScheduler = new EventSignatureValidatorScheduler(model);
        orphanBufferScheduler = new OrphanBufferScheduler(model);
        inOrderLinkerScheduler = new InOrderLinkerScheduler(model);
        linkedEventIntakeScheduler = new LinkedEventIntakeScheduler(model);

        wire();

        // Logs if there is cyclical back pressure.
        // Do not throw -- in theory we might survive this, so no need to crash.
        cyclicalBackpressurePresent = model.checkForCyclicalBackpressure();
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
     * Check if cyclical backpressure is present in the model.
     *
     * @return true if cyclical backpressure is present, false otherwise
     */
    public boolean isCyclicalBackpressurePresent() {
        return cyclicalBackpressurePresent;
    }

    /**
     * Wire the components together.
     */
    private void wire() {
        internalEventValidatorScheduler.getEventOutput().solderTo(eventDeduplicatorScheduler.getEventInput());
        eventDeduplicatorScheduler.getEventOutput().solderTo(eventSignatureValidatorScheduler.getEventInput());
        eventSignatureValidatorScheduler.getEventOutput().solderTo(orphanBufferScheduler.getEventInput());
        orphanBufferScheduler.getEventOutput().solderTo(inOrderLinkerScheduler.getEventInput());
        inOrderLinkerScheduler.getEventOutput().solderTo(linkedEventIntakeScheduler.getEventInput());

        final OutputWire<Long> minimumGenerationNonAncientOutput = linkedEventIntakeScheduler
                .getEventOutput()
                .buildTransformer(
                        "getMinimumGenerationNonAncient",
                        consensusRound -> consensusRound.getGenerations().getMinGenerationNonAncient());

        minimumGenerationNonAncientOutput.solderTo(
                eventDeduplicatorScheduler.getMinimumGenerationNonAncientInput(), INJECT);
        minimumGenerationNonAncientOutput.solderTo(
                eventSignatureValidatorScheduler.getMinimumGenerationNonAncientInput(), INJECT);
        minimumGenerationNonAncientOutput.solderTo(orphanBufferScheduler.getMinimumGenerationNonAncientInput(), INJECT);
        minimumGenerationNonAncientOutput.solderTo(
                inOrderLinkerScheduler.getMinimumGenerationNonAncientInput(), INJECT);

        // FUTURE WORK: solder all the things!
    }

    /**
     * Bind schedulers to their components.
     *
     * @param internalEventValidator  the internal event validator to bind
     * @param eventDeduplicator       the event deduplicator to bind
     * @param eventSignatureValidator the event signature validator to bind
     * @param orphanBuffer            the orphan buffer to bind
     * @param inOrderLinker           the in order linker to bind
     * @param linkedEventIntake       the linked event intake to bind
     */
    public void bind(
            @NonNull final InternalEventValidator internalEventValidator,
            @NonNull final EventDeduplicator eventDeduplicator,
            @NonNull final EventSignatureValidator eventSignatureValidator,
            @NonNull final OrphanBuffer orphanBuffer,
            @NonNull final InOrderLinker inOrderLinker,
            @NonNull final LinkedEventIntake linkedEventIntake) {

        internalEventValidatorScheduler.bind(internalEventValidator);
        eventDeduplicatorScheduler.bind(eventDeduplicator);
        eventSignatureValidatorScheduler.bind(eventSignatureValidator);
        orphanBufferScheduler.bind(orphanBuffer);
        inOrderLinkerScheduler.bind(inOrderLinker);
        linkedEventIntakeScheduler.bind(linkedEventIntake);

        // FUTURE WORK: bind all the things!
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
        return internalEventValidatorScheduler.getEventInput()::put;
    }

    /**
     * Get the input method for the address book update.
     * <p>
     * Future work: this is a temporary hook to update the address book in the new intake pipeline.
     *
     * @return the input method for the address book update
     */
    public Consumer<AddressBookUpdate> getAddressBookUpdateInput() {
        return eventSignatureValidatorScheduler.getAddressBookUpdateInput()::inject;
    }

    /**
     * Inject a new minimum generation non-ancient on all components that need it.
     * <p>
     * Future work: this is a temporary hook to allow the components to get the minimum generation non-ancient
     * during startup. This method will be removed once the components are wired together.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void forceUpdateMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        eventDeduplicatorScheduler.getMinimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
        eventSignatureValidatorScheduler.getMinimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
        orphanBufferScheduler.getMinimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
        inOrderLinkerScheduler.getMinimumGenerationNonAncientInput().inject(minimumGenerationNonAncient);
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
}
