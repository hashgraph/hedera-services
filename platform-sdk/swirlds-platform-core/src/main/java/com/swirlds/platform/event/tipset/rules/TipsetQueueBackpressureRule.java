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

package com.swirlds.platform.event.tipset.rules;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * Prevents event creations when the system is stressed and unable to keep up with its work load. Stops event creation
 * whenever a queue gets too big.
 */
public class TipsetQueueBackpressureRule implements TipsetEventCreationRule {

    /**
     * Prevent new events from being created if the provided queue size meets or exceeds this size.
     */
    private final int threshold;

    private final IntSupplier queueSize;

    /**
     * Constructor.
     *
     * @param queueSize provides the size of a queue
     * @param threshold the threshold at which backpressure should be applied
     */
    public TipsetQueueBackpressureRule(@NonNull final IntSupplier queueSize, final int threshold) {
        this.threshold = threshold;
        this.queueSize = Objects.requireNonNull(queueSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        return queueSize.getAsInt() < threshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        // no-op
    }
}
