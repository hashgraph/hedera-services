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
 * Prevents a {@link ChatterEvent} from being sent unless a variable amount of time has passed since we have received
 * this event. This excludes self events which should be sent immediately.
 *
 * @param <E>
 * 		the type of event
 */
public class VariableTimeDelay<E extends ChatterEvent> implements SendCheck<E> {
    private final Supplier<Duration> waitTimeSupplier;
    private final PeerGossipState state;
    private final Supplier<Instant> now;

    /**
     * @param waitTimeSupplier
     * 		A supplier of the amount of time to wait before sending an event. If the supplier returns null, the event
     * 		is not sent
     * @param state
     * 		the peer's state
     * @param now
     * 		supplies the current time
     */
    public VariableTimeDelay(
            final Supplier<Duration> waitTimeSupplier, final PeerGossipState state, final Supplier<Instant> now) {
        this.waitTimeSupplier = waitTimeSupplier;
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
        final Duration timeToWait = waitTimeSupplier.get();
        final Duration timeElapsed = Duration.between(event.getTimeReceived(), now.get());
        if (timeToWait == null || timeElapsed.toMillis() < timeToWait.toMillis()) {
            return SendAction.WAIT;
        }
        return SendAction.SEND;
    }
}
