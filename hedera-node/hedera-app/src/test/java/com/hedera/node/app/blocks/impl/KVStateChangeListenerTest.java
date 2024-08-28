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

package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.block.stream.output.StateChange.ChangeOperationOneOfType.MAP_DELETE;
import static com.hedera.hapi.block.stream.output.StateChange.ChangeOperationOneOfType.MAP_UPDATE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KVStateChangeListenerTest {
    private static final int STATE_ID = 1;
    private static final AccountID KEY = AccountID.newBuilder().accountNum(1234).build();
    private static final Account VALUE = Account.newBuilder().accountId(KEY).build();
    private KVStateChangeListener listener;

    @BeforeEach
    void setUp() {
        listener = new KVStateChangeListener();
    }

    @Test
    void testGetStateChanges() {
        listener.mapUpdateChange(STATE_ID, KEY, VALUE);

        List<StateChange> stateChanges = listener.getStateChanges();
        assertEquals(1, stateChanges.size());
    }

    @Test
    void testResetStateChanges() {
        listener.mapUpdateChange(STATE_ID, KEY, VALUE);
        listener.resetStateChanges();

        List<StateChange> stateChanges = listener.getStateChanges();
        assertTrue(stateChanges.isEmpty());
    }

    @Test
    void testMapUpdateChange() {
        listener.mapUpdateChange(STATE_ID, KEY, VALUE);

        StateChange stateChange = listener.getStateChanges().getFirst();
        assertEquals(MAP_UPDATE, stateChange.changeOperation().kind());
        assertEquals(STATE_ID, stateChange.stateId());
        assertEquals(KEY, stateChange.mapUpdate().key().accountIdKey());
        assertEquals(VALUE, stateChange.mapUpdate().value().accountValue());
    }

    @Test
    void testMapDeleteChange() {
        listener.mapDeleteChange(STATE_ID, KEY);

        StateChange stateChange = listener.getStateChanges().getFirst();
        assertEquals(MAP_DELETE, stateChange.changeOperation().kind());
        assertEquals(STATE_ID, stateChange.stateId());
        assertEquals(KEY, stateChange.mapDelete().key().accountIdKey());
    }
}
