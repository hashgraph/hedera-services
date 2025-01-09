/*
 * Copyright (C) 2016-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.state.notifications;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.platform.system.StateEventHandler;
import java.time.Instant;

/**
 * A {@link com.swirlds.common.notification.Notification Notification} that a new signed state has been completed. Not
 * guaranteed to be called for every round, and not guaranteed to be called in order. State is guaranteed to hold a
 * reservation until callback completes.
 */
public class NewSignedStateNotification extends AbstractNotification {

    private final StateEventHandler stateEventHandler;
    private final long round;
    private final Instant consensusTimestamp;

    /**
     * Create a notification for a newly signed state.
     *
     * @param stateEventHandler        the swirld state from the round that is now fully signed
     * @param round              the round that is now fully signed
     * @param consensusTimestamp the consensus timestamp of the round that is now fully signed
     */
    public NewSignedStateNotification(
            final StateEventHandler stateEventHandler, final long round, final Instant consensusTimestamp) {

        this.stateEventHandler = stateEventHandler;
        this.round = round;
        this.consensusTimestamp = consensusTimestamp;
    }

    /**
     * Get the swirld state from the round that is now fully signed. Guaranteed to hold a reservation in the scope of
     * this notification.
     */
    @SuppressWarnings("unchecked")
    public <T extends StateEventHandler> T getSwirldState() {
        return (T) stateEventHandler;
    }

    /**
     * Get The round that is now fully signed.
     */
    public long getRound() {
        return round;
    }

    /**
     * Get the consensus timestamp of the round that is now fully signed.
     */
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }
}
