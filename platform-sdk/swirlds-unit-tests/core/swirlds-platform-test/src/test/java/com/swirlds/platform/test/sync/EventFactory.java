/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import java.time.Instant;
import java.util.Collections;

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
        final NodeId selfId = selfParent != null ? selfParent.getCreatorId() : new NodeId(0);
        final NodeId otherId = otherParent != null ? otherParent.getCreatorId() : new NodeId(0);
        final EventDescriptor selfDescriptor = selfParent == null
                ? null
                : new EventDescriptor(selfParent.getBaseHash(), selfId, selfParent.getGeneration(), -1);
        final EventDescriptor otherDescriptor = otherParent == null
                ? null
                : new EventDescriptor(otherParent.getBaseHash(), otherId, otherParent.getGeneration(), -1);
        final BaseEventHashedData hashedEventData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                selfId,
                selfDescriptor,
                otherDescriptor == null ? Collections.emptyList() : Collections.singletonList(otherDescriptor),
                EventConstants.BIRTH_ROUND_UNDEFINED,
                Instant.EPOCH,
                null);

        final BaseEventUnhashedData unhashedEventData =
                new BaseEventUnhashedData(otherId, HashGenerator.random().getValue());

        final EventImpl e = new EventImpl(hashedEventData, unhashedEventData, selfParent, otherParent);
        e.getBaseEventHashedData().setHash(HashGenerator.random());

        assertNotNull(e.getBaseHash(), "null base hash");

        return e;
    }
}
