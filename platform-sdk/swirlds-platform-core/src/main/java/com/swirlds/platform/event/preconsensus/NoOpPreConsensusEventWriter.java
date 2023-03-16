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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.platform.internal.EventImpl;
import java.time.Duration;

/**
 * A {@link PreConsensusEventWriter} that does nothing. Once we decide to enable this in production, we will
 * remove this implementation and will not support disabling the pre-consensus event writer.
 */
public class NoOpPreConsensusEventWriter implements PreConsensusEventWriter {

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeEvent(final EventImpl event) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinimumGenerationToStore(final long minimumGenerationToStore) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventDurable(final EventImpl event) {
        // If we are not writing events, then we should never block on events becoming durable.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilDurable(final EventImpl event) {
        // If we are not writing events, then we should never block on events becoming durable.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean waitUntilDurable(final EventImpl event, final Duration timeToWait) {
        // If we are not writing events, then we should never block on events becoming durable.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestFlush(final EventImpl event) {
        // no-op
    }
}
