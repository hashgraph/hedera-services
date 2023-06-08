/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle.adapters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduledTransactionsAdapterTest {

    @Mock
    private MerkleScheduledTransactionsState state;

    @Mock
    private MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> byId;

    @Mock
    private MerkleMapLike<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirySec;

    @Mock
    private MerkleMapLike<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality;

    private ScheduledTransactionsAdapter subject;

    @BeforeEach
    void setUp() {
        subject = new ScheduledTransactionsAdapter(state, byId, byExpirySec, byEquality);
    }

    @Test
    void delegatesSetsMinSecond() {
        subject.setCurrentMinSecond(1234L);
        Mockito.verify(state).setCurrentMinSecond(1234L);
    }

    @Test
    void delegatesGetsMinSecond() {
        given(state.currentMinSecond()).willReturn(1234L);

        assertEquals(1234L, subject.getCurrentMinSecond());
    }

    @Test
    void delegatesNumSchedulesToIds() {
        given(byId.size()).willReturn(1234);

        assertEquals(1234, subject.getNumSchedules());
    }

    @Test
    void gettersWork() {
        assertSame(byEquality, subject.byEquality());
        assertSame(byExpirySec, subject.byExpirationSecond());
        assertSame(byId, subject.byId());
        assertSame(state, subject.state());
    }
}
