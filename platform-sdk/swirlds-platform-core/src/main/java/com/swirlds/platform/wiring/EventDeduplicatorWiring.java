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

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link EventDeduplicator}.
 *
 * @param eventInput                       the input wire for events to be deduplicated
 * @param minimumGenerationNonAncientInput the input wire for the minimum generation non-ancient
 * @param eventOutput                      the output wire for deduplicated events
 * @param flushRunnable                    the runnable to flush the deduplicator
 */
public record EventDeduplicatorWiring(
        @NonNull InputWire<GossipEvent> eventInput,
        @NonNull InputWire<Long> minimumGenerationNonAncientInput,
        @NonNull OutputWire<GossipEvent> eventOutput,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this deduplicator
     * @return the new wiring instance
     */
    public static EventDeduplicatorWiring create(@NonNull final TaskScheduler<GossipEvent> taskScheduler) {
        return new EventDeduplicatorWiring(
                taskScheduler.buildInputWire("non-deduplicated events"),
                taskScheduler.buildInputWire("minimum generation non ancient"),
                taskScheduler.getOutputWire(),
                taskScheduler::flush);
    }

    /**
     * Bind a deduplicator to this wiring.
     *
     * @param deduplicator the deduplicator to bind
     */
    public void bind(@NonNull final EventDeduplicator deduplicator) {
        ((BindableInputWire<GossipEvent, GossipEvent>) eventInput).bind(deduplicator::handleEvent);
        ((BindableInputWire<Long, GossipEvent>) minimumGenerationNonAncientInput)
                .bind(deduplicator::setMinimumGenerationNonAncient);
    }
}
