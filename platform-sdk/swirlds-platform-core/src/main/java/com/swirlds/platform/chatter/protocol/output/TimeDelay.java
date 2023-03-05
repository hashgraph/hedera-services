/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.chatter.protocol.output;

import com.swirlds.platform.chatter.protocol.messages.ChatterEvent;
import com.swirlds.platform.chatter.protocol.peer.PeerGossipState;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Prevents a {@link ChatterEvent} from being sent unless a specified amount of time has passed since we have received
 * this event. This excludes self events which should be sent immediately.
 *
 * @param <E>
 * 		the type of event
 */
public class TimeDelay<E extends ChatterEvent> implements SendCheck<E> {
    private final long waitMillis;
    private final PeerGossipState state;
    private final Supplier<Instant> now;

    /**
     * @param delayTime
     * 		wait this much time from receiving an event to sending it
     * @param state
     * 		the peer's state
     * @param now
     * 		supplies the current time
     */
    public TimeDelay(final Duration delayTime, final PeerGossipState state, final Supplier<Instant> now) {
        this.waitMillis = delayTime.toMillis();
        this.state = state;
        this.now = now;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SendAction shouldSend(final E event) {
        if (state.getPeerKnows(event.getDescriptor())) {
            return SendAction.DISCARD;
        }
        if (Duration.between(event.getTimeReceived(), now.get()).toMillis() < waitMillis) {
            return SendAction.WAIT;
        }
        return SendAction.SEND;
    }
}
