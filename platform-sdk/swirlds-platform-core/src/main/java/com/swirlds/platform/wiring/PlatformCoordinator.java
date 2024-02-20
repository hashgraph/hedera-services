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
import com.swirlds.platform.wiring.components.ConsensusRoundHandlerWiring;
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
    private final ConsensusEngineWiring consensusEngineWiring;
    private final EventCreationManagerWiring eventCreationManagerWiring;
    private final ApplicationTransactionPrehandlerWiring applicationTransactionPrehandlerWiring;
    private final StateSignatureCollectorWiring stateSignatureCollectorWiring;
    private final ConsensusRoundHandlerWiring consensusRoundHandlerWiring;

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
     * @param consensusEngineWiring                  the consensus engine wiring
     * @param eventCreationManagerWiring             the event creation manager wiring
     * @param applicationTransactionPrehandlerWiring the application transaction prehandler wiring
     * @param stateSignatureCollectorWiring          the system transaction prehandler wiring
     * @param consensusRoundHandlerWiring            the consensus round handler wiring
     */
    public PlatformCoordinator(
            @NonNull final ObjectCounter hashingObjectCounter,
            @NonNull final InternalEventValidatorWiring internalEventValidatorWiring,
            @NonNull final EventDeduplicatorWiring eventDeduplicatorWiring,
            @NonNull final EventSignatureValidatorWiring eventSignatureValidatorWiring,
            @NonNull final OrphanBufferWiring orphanBufferWiring,
            @NonNull final InOrderLinkerWiring inOrderLinkerWiring,
            @NonNull final ShadowgraphWiring shadowgraphWiring,
            @NonNull final ConsensusEngineWiring consensusEngineWiring,
            @NonNull final EventCreationManagerWiring eventCreationManagerWiring,
            @NonNull final ApplicationTransactionPrehandlerWiring applicationTransactionPrehandlerWiring,
            @NonNull final StateSignatureCollectorWiring stateSignatureCollectorWiring,
            @NonNull final ConsensusRoundHandlerWiring consensusRoundHandlerWiring) {

        this.hashingObjectCounter = Objects.requireNonNull(hashingObjectCounter);
        this.internalEventValidatorWiring = Objects.requireNonNull(internalEventValidatorWiring);
        this.eventDeduplicatorWiring = Objects.requireNonNull(eventDeduplicatorWiring);
        this.eventSignatureValidatorWiring = Objects.requireNonNull(eventSignatureValidatorWiring);
        this.orphanBufferWiring = Objects.requireNonNull(orphanBufferWiring);
        this.inOrderLinkerWiring = Objects.requireNonNull(inOrderLinkerWiring);
        this.shadowgraphWiring = Objects.requireNonNull(shadowgraphWiring);
        this.consensusEngineWiring = Objects.requireNonNull(consensusEngineWiring);
        this.eventCreationManagerWiring = Objects.requireNonNull(eventCreationManagerWiring);
        this.applicationTransactionPrehandlerWiring = Objects.requireNonNull(applicationTransactionPrehandlerWiring);
        this.stateSignatureCollectorWiring = Objects.requireNonNull(stateSignatureCollectorWiring);
        this.consensusRoundHandlerWiring = Objects.requireNonNull(consensusRoundHandlerWiring);
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
        consensusEngineWiring.flushRunnable().run();
        applicationTransactionPrehandlerWiring.flushRunnable().run();
    }

    /**
     * Safely clears the system in preparation for reconnect
     */
    public void clear() {
        // Phase 1: squelch
        // Break cycles in the system. Flush squelched components just in case there is a task being executed when
        // squelch is activated.
        consensusEngineWiring.startSquelchingRunnable().run();
        consensusEngineWiring.flushRunnable().run();
        eventCreationManagerWiring.startSquelching();
        eventCreationManagerWiring.flush();

        // Also squelch the consensus round handler. It isn't strictly necessary to do this to prevent dataflow through
        // the system, but it prevents the consensus round handler from wasting time handling rounds that don't need to
        // be handled.
        consensusRoundHandlerWiring.startSquelchingRunnable().run();
        consensusRoundHandlerWiring.flushRunnable().run();

        // Phase 2: flush
        // All cycles have been broken via squelching, so now it's time to flush everything out of the system.
        flushIntakePipeline();
        stateSignatureCollectorWiring.flush();
        consensusRoundHandlerWiring.flushRunnable().run();

        // Phase 3: stop squelching
        // Once everything has been flushed out of the system, it's safe to stop squelching.
        consensusEngineWiring.stopSquelchingRunnable().run();
        eventCreationManagerWiring.stopSquelching();
        consensusRoundHandlerWiring.stopSquelchingRunnable().run();

        // Phase 4: clear
        // Data is no longer moving through the system. Clear all the internal data structures in the wiring objects.
        eventDeduplicatorWiring.clearInput().inject(new ClearTrigger());
        orphanBufferWiring.clearInput().inject(new ClearTrigger());
        inOrderLinkerWiring.clearInput().inject(new ClearTrigger());
        stateSignatureCollectorWiring.getClearInput().inject(new ClearTrigger());
        eventCreationManagerWiring.clearInput().inject(new ClearTrigger());
    }
}
