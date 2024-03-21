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

package com.hedera.node.app.service.token.impl.test;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.service.token.impl.ReadableNetworkStakingRewardsStoreImpl;
import com.swirlds.platform.state.spi.ReadableSingletonState;
import com.swirlds.platform.state.spi.ReadableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableNetworkStakingRewardsStoreImplTest {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableStates states;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableSingletonState stakingRewardsState;

    private ReadableNetworkStakingRewardsStoreImpl subject;

    @BeforeEach
    void setUp() {
        given(states.getSingleton(STAKING_NETWORK_REWARDS_KEY)).willReturn(stakingRewardsState);
        given(stakingRewardsState.get()).willReturn(new NetworkStakingRewards(true, 1L, 2L, 3L));
        subject = new ReadableNetworkStakingRewardsStoreImpl(states);
    }

    @Test
    void testNullConstructorArgs() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new ReadableNetworkStakingRewardsStoreImpl(null));
    }

    @Test
    void gettersWork() {
        assertThat(subject.isStakingRewardsActivated()).isTrue();
        assertThat(subject.totalStakeRewardStart()).isEqualTo(1L);
        assertThat(subject.totalStakedStart()).isEqualTo(2L);
        assertThat(subject.pendingRewards()).isEqualTo(3L);
    }
}
