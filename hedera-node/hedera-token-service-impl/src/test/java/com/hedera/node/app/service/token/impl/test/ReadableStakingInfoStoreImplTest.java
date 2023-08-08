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

package com.hedera.node.app.service.token.impl.test;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.ReadableStakingInfoStoreImpl;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableStakingInfoStoreImplTest {
    private static final long NODE_ID_10 = 10L, NODE_ID_20 = 20L;

    @Mock
    private ReadableStates states;

    @Mock
    private StakingNodeInfo stakingNodeInfo;

    private ReadableStakingInfoStoreImpl subject;

    @BeforeEach
    void setUp() {
        final var readableStakingNodes = MapReadableKVState.<Long, StakingNodeInfo>builder(STAKING_INFO_KEY)
                .value(NODE_ID_10, stakingNodeInfo)
                .build();
        given(states.<Long, StakingNodeInfo>get(STAKING_INFO_KEY)).willReturn(readableStakingNodes);

        subject = new ReadableStakingInfoStoreImpl(states);
    }

    @Test
    void testNullConstructorArgs() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new ReadableStakingInfoStoreImpl(null));
    }

    @Test
    void testGet() {
        final var result = subject.get(NODE_ID_10);
        Assertions.assertThat(result).isEqualTo(stakingNodeInfo);
    }

    @Test
    void testGetEmpty() {
        final var result = subject.get(NODE_ID_20);
        Assertions.assertThat(result).isNull();
    }

    @Test
    void getAllReturnsAllKeys() {
        final var readableStakingNodes = MapReadableKVState.<Long, StakingNodeInfo>builder(STAKING_INFO_KEY)
                .value(NODE_ID_10, stakingNodeInfo)
                .value(NODE_ID_20, mock(StakingNodeInfo.class))
                .build();
        given(states.<Long, StakingNodeInfo>get(STAKING_INFO_KEY)).willReturn(readableStakingNodes);
        subject = new ReadableStakingInfoStoreImpl(states);

        final var result = subject.getAll();
        Assertions.assertThat(result).containsExactlyInAnyOrder(NODE_ID_10, NODE_ID_20);
    }

    @Test
    void getAllReturnsEmptyKeys() {
        final var readableStakingNodes = MapReadableKVState.<Long, StakingNodeInfo>builder(STAKING_INFO_KEY)
                .build(); // Intentionally empty
        given(states.<Long, StakingNodeInfo>get(STAKING_INFO_KEY)).willReturn(readableStakingNodes);
        subject = new ReadableStakingInfoStoreImpl(states);

        final var result = subject.getAll();
        Assertions.assertThat(result).isEmpty();
    }
}
