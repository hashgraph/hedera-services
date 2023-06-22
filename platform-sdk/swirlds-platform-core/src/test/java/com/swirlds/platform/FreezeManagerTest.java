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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.platform.state.signed.SignedState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FreezeManagerTest {
    private FreezeManager freezeManager;
    private SignedState freezeState;
    private SignedState nonFreezeState;

    private void assertFrozen() {
        assertTrue(freezeManager.isEventCreationFrozen());
        assertEquals(DONT_CREATE, freezeManager.shouldCreateEvent());
    }

    private void assertNotFrozen() {
        assertFalse(freezeManager.isEventCreationFrozen());
        assertEquals(PASS, freezeManager.shouldCreateEvent());
    }

    @BeforeEach
    public void setup() {
        freezeManager = new FreezeManager();

        freezeState = mock(SignedState.class);
        nonFreezeState = mock(SignedState.class);
        when(freezeState.isFreezeState()).thenReturn(true);
        when(nonFreezeState.isFreezeState()).thenReturn(false);
    }

    @Test
    @DisplayName("Test that freezeEventCreation works as expected")
    void freezeEventCreation() {
        assertNotFrozen();
        freezeManager.freezeEventCreation();
        assertFrozen();
    }

    @Test
    @DisplayName("Test that freeze occurs when signed state has enough signatures")
    void signedStateHasEnoughSignatures() {
        assertNotFrozen();
        freezeManager.stateHasEnoughSignatures(freezeState);
        assertFrozen();
    }

    @Test
    @DisplayName("Test that freeze occurs when signed state doesn't have enough signatures")
    void signedStateLacksSignatures() {
        assertNotFrozen();
        freezeManager.stateLacksSignatures(freezeState);
        assertFrozen();
    }

    @Test
    @DisplayName("signed states that aren't freeze states have no effect on state")
    void nonFreezeStates() {
        assertNotFrozen();
        freezeManager.stateHasEnoughSignatures(nonFreezeState);
        assertNotFrozen();
        freezeManager.stateLacksSignatures(nonFreezeState);
        assertNotFrozen();
    }
}
