/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.internal.EventImpl;

public class EventMocks {
    /**
     * Utility method that creates a mock event with the needed data.
     */
    public static EventImpl createMockEvent(final long nodeId, final long generation, final EventImpl otherParent) {

        final EventImpl event = mock(EventImpl.class);

        when(event.getCreatorId()).thenReturn(nodeId);
        when(event.getGeneration()).thenReturn(generation);
        when(event.getOtherParent()).thenReturn(otherParent);
        when(event.getBaseHash()).thenReturn(RandomUtils.randomHash());

        return event;
    }

    /**
     * Create a mock event
     *
     * @param hashedData
     * 		the hashed data inside {@link EventImpl}
     * @return a mock {@link EventImpl}
     */
    public static EventImpl mockEvent(final BaseEventHashedData hashedData) {
        final EventImpl e = mock(EventImpl.class);
        when(e.getHashedData()).thenReturn(hashedData);
        when(e.getBaseEventHashedData()).thenReturn(hashedData);
        return e;
    }
}
