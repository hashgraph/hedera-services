/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.system.EventCreationRuleResponse.DONT_CREATE;
import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FreezeManagerTest {

    private FreezeManager freezeManager;
    private final AtomicInteger checkStatusCounter = new AtomicInteger(0);

    @BeforeEach
    public void setup() {
        freezeManager = new FreezeManager(checkStatusCounter::incrementAndGet);
    }

    @Test
    void testEventCreationFrozen() {
        assertFalse(freezeManager.isEventCreationFrozen(), "EventCreationFrozen should be false by default");

        freezeManager.freezeEventCreation();
        assertTrue(freezeManager.isEventCreationFrozen(), "EventCreationFrozen should be true");
    }

    @Test
    void shouldCreateEventTest() {
        assertFalse(freezeManager.isEventCreationFrozen(), "EventCreationFrozen should be false by default");
        assertEquals(PASS, freezeManager.shouldCreateEvent(), "should PASS when event creation is not frozen");

        freezeManager.freezeEventCreation();
        assertEquals(
                DONT_CREATE,
                freezeManager.shouldCreateEvent(),
                "should not create events during event creation frozen");
    }

    @Test
    void freezeStateTest() {
        assertFalse(freezeManager.isFreezeStarted(), "Freeze status should initialize to NOT_IN_FREEZE");
        assertFalse(freezeManager.isFreezeComplete(), "Freeze status should initialize to NOT_IN_FREEZE");
        assertEquals(0, checkStatusCounter.get(), "Status should not have been updated yet.");

        assertThrows(
                IllegalStateException.class,
                freezeManager::freezeComplete,
                "Cannot transaction from not in freeze to freeze complete.");
        freezeManager.freezeStarted();

        assertTrue(freezeManager.isFreezeStarted(), "inFreeze should be true after inFreeze() is called.");
        assertFalse(freezeManager.isFreezeComplete(), "Freeze status should not be FREEZE_COMPLETE");
        assertEquals(1, checkStatusCounter.get(), "Status should have been updated once.");

        freezeManager.freezeComplete();

        assertFalse(freezeManager.isFreezeStarted(), "inFreeze should be true after inFreeze() is called.");
        assertTrue(freezeManager.isFreezeComplete(), "Freeze status should not be FREEZE_COMPLETE");
        assertEquals(2, checkStatusCounter.get(), "Status should have been updated twice.");
    }
}
