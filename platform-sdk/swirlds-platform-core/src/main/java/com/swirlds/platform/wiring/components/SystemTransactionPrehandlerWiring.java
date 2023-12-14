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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.SwirldStateManager;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for system transaction prehandling.
 *
 * @param systemTransactionsToPrehandleInput the input wire containing events where system transactions should be
 *                                           prehandled
 * @param flushRunnable                      the runnable that will flush the system transaction prehandler
 */
public record SystemTransactionPrehandlerWiring(
        @NonNull InputWire<GossipEvent> systemTransactionsToPrehandleInput, @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler that will perform the prehandling
     * @return the new wiring instance
     */
    @NonNull
    public static SystemTransactionPrehandlerWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new SystemTransactionPrehandlerWiring(
                taskScheduler.buildInputWire("system transactions to prehandle"), taskScheduler::flush);
    }

    /**
     * Bind the preconsensus event handler to the input wire.
     *
     * @param swirldStateManager manages operations on the current state, and also transaction prehandling on recent
     *                           immutable states why not
     */
    public void bind(@NonNull final SwirldStateManager swirldStateManager) {
        ((BindableInputWire<GossipEvent, Void>) systemTransactionsToPrehandleInput).bind(event -> {
            // As a temporary work around, convert to EventImpl.
            // Once we remove the legacy pathway, we can remove this.
            final EventImpl eventImpl = new EventImpl(event, null, null);
            swirldStateManager.prehandleSystemTransactions(eventImpl);
        });
    }
}
