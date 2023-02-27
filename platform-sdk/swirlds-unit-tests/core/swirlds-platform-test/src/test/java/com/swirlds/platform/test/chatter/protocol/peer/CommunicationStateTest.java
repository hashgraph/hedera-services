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

package com.swirlds.platform.test.chatter.protocol.peer;

import com.swirlds.platform.chatter.protocol.peer.CommunicationState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommunicationStateTest {
    private final CommunicationState state = new CommunicationState();

    @BeforeEach
    public void reset() {
        state.reset();
    }

    @Test
    void defaultState() {
        assertSyncState(false, true, "default should be sync only");
    }

    @Test
    void expectedTransitions() {
        assertSyncState(false, true, "default should be sync only");
        assertCommState(false, false, "by default, no protocol should be running");
        state.chatterSyncStarted();
        assertCommState(false, true, "chatter sync should be running");
        state.chatterSyncStartingPhase3();
        assertSyncState(true, false, "we should now be chattering");
        state.chatterStarted();
        assertCommState(true, false, "chatter should be running");
        state.chatterEnded();
        assertCommState(false, false, "no protocol should be running");
        assertSyncState(false, true, "after chatter ended, we should sync");
        state.chatterSyncStarted();
        state.chatterSyncStartingPhase3();
        state.chatterSyncFailed();
        assertCommState(false, false, "no protocol should be running");
        assertSyncState(false, true, "if sync failed, we should sync again");
        state.chatterSyncStarted();
        state.chatterSyncStartingPhase3();
        assertSyncState(true, false, "we should now be chattering again");
        state.queueOverFlow();
        assertSyncState(false, true, "if queue overflown, we should sync again");
        state.chatterSyncStarted();
        state.chatterSyncStartingPhase3();
        assertSyncState(true, false, "we should now be chattering again");
        state.receivedEnd();
        state.chatterEnded();
        assertSyncState(false, true, "if peer ended, we should sync again");
        assertCommState(false, false, "no protocol should be running");
    }

    @Test
    void suspend() {
        state.suspend();
        assertSyncState(false, false, "when suspended, we should not sync or chatter");
        state.chatterSyncStartingPhase3();
        assertSyncState(false, false, "when suspended, regular operation should not alter state");
        state.chatterEnded();
        assertSyncState(false, false, "when suspended, regular operation should not alter state");
        state.chatterSyncFailed();
        assertSyncState(false, false, "when suspended, regular operation should not alter state");
        state.queueOverFlow();
        assertSyncState(false, false, "when suspended, regular operation should not alter state");
        state.receivedEnd();
        assertSyncState(false, false, "when suspended, regular operation should not alter state");

        state.unsuspend();
        assertSyncState(false, true, "unsuspend should return it to the default state");
    }

    private void assertSyncState(final boolean expectedChatter, final boolean expectedSync, final String message) {
        Assertions.assertEquals(expectedChatter, state.shouldChatter(), message);
        Assertions.assertEquals(expectedSync, state.isOutOfSync(), message);
    }

    private void assertCommState(final boolean expectedChatter, final boolean expectedSync, final String message) {
        Assertions.assertEquals(expectedChatter, state.isChattering(), message);
        Assertions.assertEquals(expectedSync, state.isChatterSyncing(), message);
        Assertions.assertEquals(expectedChatter || expectedSync, state.isAnyProtocolRunning(), message);
    }
}
