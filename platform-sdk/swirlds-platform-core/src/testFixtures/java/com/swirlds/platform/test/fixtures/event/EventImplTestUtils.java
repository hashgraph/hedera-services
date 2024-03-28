/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.event;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Utility methods for creating {@link EventImpl} instances for testing.
 */
public class EventImplTestUtils {
    /**
     * Hidden constructor
     */
    private EventImplTestUtils() {}

    /**
     * Create an {@link EventImpl} with the given {@link TestingEventBuilder} as a starting point, a self parent, and
     * an other parent.
     * <p>
     * The {@link TestingEventBuilder} passed into this method shouldn't have parents set. This method will set the
     * parents on the builder, and will also set the parents on the {@link EventImpl} that is created.
     *
     * @param gossipEventBuilder the {@link TestingEventBuilder} to use
     * @param selfParent         the self parent to use
     * @param otherParent        the other parent to use
     * @return the created {@link EventImpl}
     */
    public static EventImpl createEventImpl(
            @NonNull final TestingEventBuilder gossipEventBuilder,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent) {

        final GossipEvent selfParentGossipEvent = selfParent == null ? null : selfParent.getBaseEvent();
        final GossipEvent otherParentGossipEvent = otherParent == null ? null : otherParent.getBaseEvent();

        final GossipEvent gossipEvent = gossipEventBuilder
                .setSelfParent(selfParentGossipEvent)
                .setOtherParent(otherParentGossipEvent)
                .build();

        return new EventImpl(gossipEvent, selfParent, otherParent);
    }
}
