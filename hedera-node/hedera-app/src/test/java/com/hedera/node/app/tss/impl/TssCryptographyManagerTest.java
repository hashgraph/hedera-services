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
import com.hedera.hapi.platform.state.NodeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssCryptographyManagerTest {
    private TssCryptographyManager tssCryptographyManager;

    @BeforeEach
    void setUp() {
        tssCryptographyManager = new TssCryptographyManager(new NodeId(1L), 10, true);
    }

    @Test
    void testSetActiveRoster() {
        assertDoesNotThrow(() -> tssCryptographyManager.setActiveRoster(Roster.DEFAULT));
    }

    @Test
    void testKeyCandidateRoster() {
        assertDoesNotThrow(() -> tssCryptographyManager.keyCandidateRoster(Roster.DEFAULT));
    }
}
