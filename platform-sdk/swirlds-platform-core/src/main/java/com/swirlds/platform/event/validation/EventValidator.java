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

package com.swirlds.platform.event.validation;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.platform.event.GossipEvent;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates events received from peers
 */
public class EventValidator {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(EventValidator.class);

    private final GossipEventValidator gossipEventValidator;
    /** A consumer of valid events */
    private final Consumer<GossipEvent> eventIntake;

    private final Cryptography cryptography;

    public EventValidator(final GossipEventValidator gossipEventValidator, final Consumer<GossipEvent> eventIntake) {
        this.gossipEventValidator = gossipEventValidator;
        this.eventIntake = eventIntake;
        this.cryptography = CryptographyHolder.get();
    }

    /**
     * Hashes the event if it hasn't been hashed already, then checks the event's validity. If the event is invalid, it
     * is discarded. If it's valid, it is passed on.
     *
     * @param gossipEvent
     * 		event received from gossip
     */
    public void validateEvent(final GossipEvent gossipEvent) {
        try {
            if (gossipEvent.getHashedData().getHash() == null) {
                // only hash if it hasn't been already hashed
                cryptography.digestSync(gossipEvent.getHashedData());
                // we also need to build the descriptor once we have the hash
                gossipEvent.buildDescriptor();
            }
            if (!gossipEventValidator.isEventValid(gossipEvent)) {
                return;
            }
            eventIntake.accept(gossipEvent);
        } catch (final RuntimeException e) {
            logger.error(EXCEPTION.getMarker(), "Error while processing intake event", e);
        }
    }
}
