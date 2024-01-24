/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.platform.wiring.components.ApplicationTransactionPrehandlerWiring;
import com.swirlds.platform.wiring.components.EventCreationManagerWiring;
import com.swirlds.platform.wiring.components.EventHasherWiring;
import com.swirlds.platform.wiring.components.PostHashCollectorWiring;
import com.swirlds.platform.wiring.components.ShadowgraphWiring;
import com.swirlds.platform.wiring.components.StateSignatureCollectorWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Responsible for coordinating the clearing of the platform wiring objects.
 */
public class PlatformCoordinator {
    /**
     * The object counter which spans the {@link EventHasherWiring} and the {@link PostHashCollectorWiring}
     * <p>
     * Used to flush the pair of components together.
     */
    private final ObjectCounter hashingObjectCounter;

    private final InternalEventValidatorWiring internalEventValidatorWiring;
    private final EventDeduplicatorWiring eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final ShadowgraphWiring shadowgraphWiring;
    private final LinkedEventIntakeWiring linkedEventIntakeWiring;
    private final EventCreationManagerWiring eventCreationManagerWiring;
    private final ApplicationTransactionPrehandlerWiring applicationTransactionPrehandlerWiring;
    private final StateSignatureCollectorWiring stateSignatureCollectorWiring;

    /**
     * Constructor
     *
     * @param hashingObjectCounter                   the hashing object counter
     * @param internalEventValidatorWiring           the internal event validator wiring
     * @param eventDeduplicatorWiring                the event deduplicator wiring
     * @param eventSignatureValidatorWiring          the event signature validator wiring
     * @param orphanBufferWiring                     the orphan buffer wiring
     * @param inOrderLinkerWiring                    the in order linker wiring
     * @param shadowgraphWiring                      the shadowgraph wiring
     * @param linkedEventIntakeWiring                the linked event intake wiring
     * @param eventCreationManagerWiring             the event creation manager wiring
     * @param applicationTransactionPrehandlerWiring the application transaction prehandler wiring
     * @param stateSignatureCollectorWiring          the system transaction prehandler wiring
     */
    public PlatformCoordinator(
            @NonNull final ObjectCounter hashingObjectCounter,
            @NonNull final InternalEventValidatorWiring internalEventValidatorWiring,
            @NonNull final EventDeduplicatorWiring eventDeduplicatorWiring,
            @NonNull final EventSignatureValidatorWiring eventSignatureValidatorWiring,
            @NonNull final OrphanBufferWiring orphanBufferWiring,
            @NonNull final InOrderLinkerWiring inOrderLinkerWiring,
            @NonNull final ShadowgraphWiring shadowgraphWiring,
            @NonNull final LinkedEventIntakeWiring linkedEventIntakeWiring,
            @NonNull final EventCreationManagerWiring eventCreationManagerWiring,
            @NonNull final ApplicationTransactionPrehandlerWiring applicationTransactionPrehandlerWiring,
            @NonNull final StateSignatureCollectorWiring stateSignatureCollectorWiring) {

        this.hashingObjectCounter = Objects.requireNonNull(hashingObjectCounter);
        this.internalEventValidatorWiring = Objects.requireNonNull(internalEventValidatorWiring);
        this.eventDeduplicatorWiring = Objects.requireNonNull(eventDeduplicatorWiring);
        this.eventSignatureValidatorWiring = Objects.requireNonNull(eventSignatureValidatorWiring);
        this.orphanBufferWiring = Objects.requireNonNull(orphanBufferWiring);
        this.inOrderLinkerWiring = Objects.requireNonNull(inOrderLinkerWiring);
        this.shadowgraphWiring = Objects.requireNonNull(shadowgraphWiring);
        this.linkedEventIntakeWiring = Objects.requireNonNull(linkedEventIntakeWiring);
        this.eventCreationManagerWiring = Objects.requireNonNull(eventCreationManagerWiring);
        this.applicationTransactionPrehandlerWiring = Objects.requireNonNull(applicationTransactionPrehandlerWiring);
        this.stateSignatureCollectorWiring = Objects.requireNonNull(stateSignatureCollectorWiring);
    }

    /**
     * Flushes the intake pipeline
     */
    public void flushIntakePipeline() {
        // it isn't possible to flush the event hasher and the post hash collector independently, since the framework
        // currently doesn't support flushing if multiple components share the same object counter. As a workaround,
        // we just wait for the shared object counter to be empty, which is equivalent to flushing both components.
        hashingObjectCounter.waitUntilEmpty();

        internalEventValidatorWiring.flushRunnable().run();
        eventDeduplicatorWiring.flushRunnable().run();
        eventSignatureValidatorWiring.flushRunnable().run();
        orphanBufferWiring.flushRunnable().run();
        eventCreationManagerWiring.flush();
        inOrderLinkerWiring.flushRunnable().run();
        shadowgraphWiring.flushRunnable().run();
        linkedEventIntakeWiring.flushRunnable().run();
        applicationTransactionPrehandlerWiring.flushRunnable().run();
    }

    /**
     * Safely clears the intake pipeline
     * <p>
     * Future work: this method should be expanded to coordinate the clearing of the entire system
     */
    public void clear() {
        // Phase 1: pause
        // Pause the linked event intake and event creator, to prevent any new events from making it through the intake
        // pipeline.
        linkedEventIntakeWiring.pauseInput().inject(true);
        eventCreationManagerWiring.pauseInput().inject(true);
        linkedEventIntakeWiring.flushRunnable().run();
        eventCreationManagerWiring.flush();

        // Phase 2: flush
        // Flush everything remaining in the intake pipeline out into the void.
        flushIntakePipeline();
        stateSignatureCollectorWiring.flush();

        // Phase 3: clear
        // Data is no longer moving through the system. clear all the internal data structures in the wiring objects.
        eventDeduplicatorWiring.clearInput().inject(new ClearTrigger());
        eventDeduplicatorWiring.flushRunnable().run();
        orphanBufferWiring.clearInput().inject(new ClearTrigger());
        orphanBufferWiring.flushRunnable().run();
        inOrderLinkerWiring.clearInput().inject(new ClearTrigger());
        inOrderLinkerWiring.flushRunnable().run();
        stateSignatureCollectorWiring.getClearInput().inject(new ClearTrigger());

        // Phase 4: unpause
        // Once everything has been flushed out of the system, it's safe to unpause event intake and creation.
        linkedEventIntakeWiring.pauseInput().inject(false);
        eventCreationManagerWiring.pauseInput().inject(false);
    }
}
