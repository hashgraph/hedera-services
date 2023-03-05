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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.platform.event.ThreadSafeSelfEventStorage;
import com.swirlds.platform.internal.EventImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SelfEventStorageTest {
    @Test
    void testThreadSafeSelfEventStorage() {
        final ThreadSafeSelfEventStorage eventStorage = new ThreadSafeSelfEventStorage();
        assertNull(eventStorage.getMostRecentSelfEvent());
        final EventImpl event = Mockito.mock(EventImpl.class);
        eventStorage.setMostRecentSelfEvent(event);
        assertSame(event, eventStorage.getMostRecentSelfEvent());
    }
}
