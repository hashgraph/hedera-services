/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.exports;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateNotification;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportingRecoveredStateListenerTest {
    @Mock
    private RecordStreamManager recordStreamManager;

    @Mock
    private BalancesExporter balancesExporter;

    @Mock
    private NewRecoveredStateNotification notification;

    @Mock
    private ServicesState signedState;

    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
    private final NodeId nodeId = new NodeId(0);

    private ExportingRecoveredStateListener subject;

    @BeforeEach
    void setup() {
        subject = new ExportingRecoveredStateListener(recordStreamManager, balancesExporter, nodeId);
    }

    @Test
    void exportsBalancesAndFreezesRecordStream() {
        given(notification.getConsensusTimestamp()).willReturn(consensusNow);
        given(notification.getSwirldState()).willReturn(signedState);

        subject.notify(notification);

        verify(recordStreamManager).setInFreeze(true);
        verify(balancesExporter).exportBalancesFrom(signedState, consensusNow, nodeId);
    }
}
