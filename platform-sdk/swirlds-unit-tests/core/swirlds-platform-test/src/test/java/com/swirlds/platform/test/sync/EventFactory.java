/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.EventImpl;
import com.swirlds.platform.event.EventConstants;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import java.time.Instant;
import java.util.Random;

/**
 * A simple, deterministic factory for Event instances
 */
public class EventFactory {

    public static ShadowEvent makeShadow() {
        return makeShadow(null);
    }

    public static ShadowEvent makeShadow(final ShadowEvent selfParent) {
        return makeShadow(selfParent, null);
    }

    public static ShadowEvent makeShadow(final ShadowEvent selfParent, final ShadowEvent otherParent) {
        final EventImpl e = makeEvent(
                selfParent == null ? null : selfParent.getEvent(), otherParent == null ? null : otherParent.getEvent());
        return new ShadowEvent(e, selfParent, otherParent);
    }

    public static EventImpl makeEventWithRandomHash() {
        return makeEvent(null, null);
    }

    public static EventImpl makeEvent(final EventImpl selfParent, final EventImpl otherParent) {
        final BaseEventHashedData hashedEventData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                new NodeId(selfParent != null ? selfParent.getCreatorId().id() : 0),
                (selfParent != null ? selfParent.getGeneration() : EventConstants.GENERATION_UNDEFINED),
                (otherParent != null ? otherParent.getGeneration() : EventConstants.GENERATION_UNDEFINED),
                (selfParent != null ? selfParent.getBaseHash() : null),
                (otherParent != null ? otherParent.getBaseHash() : null),
                Instant.EPOCH,
                null);

        final BaseEventUnhashedData unhashedEventData = new BaseEventUnhashedData(
                otherParent != null ? otherParent.getCreatorId() : new NodeId(Math.abs(new Random().nextLong())),
                HashGenerator.random().getValue());

        final EventImpl e = new EventImpl(hashedEventData, unhashedEventData, selfParent, otherParent);
        e.getBaseEventHashedData().setHash(HashGenerator.random());

        assertNotNull(e.getBaseHash(), "null base hash");

        return e;
    }
}
