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

import static org.mockito.Mockito.*;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TssMessageMapKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class TssStateManagerTest {

    @Mock
    private TssCryptographyManager mockTssCryptographyManager;

    private TssStateManager tssStateManager;
    public static final Key EMPTY_KEY =
            Key.newBuilder().setKeyList(KeyList.newBuilder().build()).build();

    @BeforeEach
    public void setUp() {
        mockTssCryptographyManager = mock(TssCryptographyManager.class);
        tssStateManager = new TssStateManager(mockTssCryptographyManager);
    }

    @Test
    public void testHandleStartup() throws Exception {
        Roster activeRoster = mock(Roster.class);
        Roster candidateRoster = mock(Roster.class);

        Map<Roster, TssMessageMapKey> tssMessageMap = new HashMap<>();
        Map<Roster, TssVoteMapKey> tssVoteMap = new HashMap<>();

        tssStateManager.handleStartup();
    }
}
