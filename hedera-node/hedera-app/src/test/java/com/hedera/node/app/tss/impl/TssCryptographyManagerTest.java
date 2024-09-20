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

package com.hedera.node.app.tss.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TssCryptographyManagerTest {

    @Mock
    private Roster roster;

    @Mock
    private RosterEntry rosterEntry;

    private TssCryptographyManager tssCryptographyManager;
    public static final Key EMPTY_KEY =
            Key.newBuilder().setKeyList(KeyList.newBuilder().build()).build();

    @BeforeEach
    void setUp() {
        when(roster.rosterEntries()).thenReturn(List.of(rosterEntry));
        when(rosterEntry.nodeId()).thenReturn(1L);
        when(rosterEntry.tssEncryptionKey()).thenReturn(Bytes.wrap(EMPTY_KEY.toByteArray()));
        tssCryptographyManager = new TssCryptographyManager(new NodeId(1L), 10, true);
    }

    @Test
    void testSetActiveRoster() {
        tssCryptographyManager.setActiveRoster(roster);
        assertNotNull(tssCryptographyManager.getTssEncryptionKey(1L));
        assertThrows(IllegalStateException.class, () -> tssCryptographyManager.setActiveRoster(roster));
    }

    @Test
    void testKeyCandidateRoster() {
        tssCryptographyManager.keyCandidateRoster(roster);
        assertNotNull(tssCryptographyManager.getTssEncryptionKey(1L));
    }
}
