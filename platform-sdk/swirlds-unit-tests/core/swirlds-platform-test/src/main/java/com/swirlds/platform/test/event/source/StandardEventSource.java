/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.source;

import com.swirlds.common.test.TransactionGenerator;
import com.swirlds.platform.test.event.IndexedEvent;
import java.util.LinkedList;
import java.util.Random;

/**
 * An event source that simulates a standard, honest node.
 */
public class StandardEventSource extends AbstractEventSource<StandardEventSource> {

    private LinkedList<IndexedEvent> latestEvents;

    public StandardEventSource(final boolean useFakeHashes) {
        this(useFakeHashes, DEFAULT_TRANSACTION_GENERATOR, DEFAULT_STAKE);
    }

    public StandardEventSource(final boolean useFakeHashes, final long stake) {
        this(useFakeHashes, DEFAULT_TRANSACTION_GENERATOR, stake);
    }

    public StandardEventSource(final long stake) {
        this(true, DEFAULT_TRANSACTION_GENERATOR, stake);
    }

    public StandardEventSource(final boolean useFakeHashes, final TransactionGenerator transactionGenerator) {
        this(useFakeHashes, transactionGenerator, DEFAULT_STAKE);
    }

    public StandardEventSource(
            final boolean useFakeHashes, final TransactionGenerator transactionGenerator, final long stake) {
        super(useFakeHashes, transactionGenerator, stake);
        latestEvents = new LinkedList<>();
    }

    public StandardEventSource() {
        this(true);
    }

    private StandardEventSource(final StandardEventSource that) {
        super(that);
        latestEvents = new LinkedList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandardEventSource copy() {
        return new StandardEventSource(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        super.reset();
        latestEvents = new LinkedList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndexedEvent getRecentEvent(final Random random, final int index) {
        if (latestEvents.size() == 0) {
            return null;
        }

        if (index >= latestEvents.size()) {
            return latestEvents.getLast();
        }

        return latestEvents.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLatestEvent(final Random random, final IndexedEvent event) {
        latestEvents.addFirst(event);
        pruneEventList(latestEvents);
    }
}
