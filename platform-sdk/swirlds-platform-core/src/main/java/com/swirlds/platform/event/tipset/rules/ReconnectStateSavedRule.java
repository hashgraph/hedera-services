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

import static com.swirlds.common.system.UptimeData.NO_ROUND;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * This rule ensures that event creation is not permitted after a reconnect until the reconnect state has been saved.
 * <p>
 * After the platform status refactor is completed, this functionality will be superseded by platform status logic.
 */
public class ReconnectStateSavedRule implements TipsetEventCreationRule {

    private final Supplier<Long> latestReconnectRound;
    private final Supplier<Long> latestSavedStateRound;

    /**
     * Constructor.
     *
     * @param latestReconnectRound  provides the latest reconnect round, or
     *                              {@link com.swirlds.common.system.UptimeData#NO_ROUND} if there have been no
     *                              reconnects since booting up
     * @param latestSavedStateRound provides the latest saved state round, or
     *                              {@link com.swirlds.common.system.UptimeData#NO_ROUND} if there are no saved states
     *                              since booting up
     */
    public ReconnectStateSavedRule(
            @NonNull final Supplier<Long> latestReconnectRound, @NonNull final Supplier<Long> latestSavedStateRound) {
        this.latestReconnectRound = Objects.requireNonNull(latestReconnectRound);
        this.latestSavedStateRound = Objects.requireNonNull(latestSavedStateRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventCreationPermitted() {
        final long latestReconnectRound = this.latestReconnectRound.get();
        if (latestReconnectRound == NO_ROUND) {
            // There have been no reconnects since booting up
            return true;
        }

        final long latestSavedStateRound = this.latestSavedStateRound.get();
        if (latestSavedStateRound == NO_ROUND) {
            // We have no saved states on disk.
            return false;
        }

        return latestSavedStateRound >= latestReconnectRound;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventWasCreated() {
        // no-op
    }
}
