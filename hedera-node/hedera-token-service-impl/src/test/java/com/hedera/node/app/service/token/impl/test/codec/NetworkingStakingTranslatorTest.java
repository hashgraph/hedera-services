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

package com.hedera.node.app.service.token.impl.test.codec;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_NETWORK_REWARDS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.service.token.impl.ReadableNetworkStakingRewardsStoreImpl;
import com.hedera.node.app.service.token.impl.codec.NetworkingStakingTranslator;
import com.swirlds.platform.state.spi.ReadableSingletonState;
import com.swirlds.platform.state.spi.ReadableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkingStakingTranslatorTest {

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
    void createNetworkStakingRewardsFromMerkleNetworkContext() {
        final com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext merkleNetworkContext =
                new com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext();

        merkleNetworkContext.setStakingRewardsActivated(true);
        merkleNetworkContext.setTotalStakedRewardStart(1L);
        merkleNetworkContext.setTotalStakedStart(2L);
        merkleNetworkContext.setPendingRewards(3L);

        final NetworkStakingRewards convertedNetworkStakingRewards =
                NetworkingStakingTranslator.networkStakingRewardsFromMerkleNetworkContext(merkleNetworkContext);

        assertEquals(getExpectedNetworkStakingRewards(), convertedNetworkStakingRewards);
    }

    private com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext getExpectedMerkleNetworkContext() {
        final com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext merkleNetworkContext =
                new com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext();

        merkleNetworkContext.setStakingRewardsActivated(true);
        merkleNetworkContext.setTotalStakedRewardStart(1L);
        merkleNetworkContext.setTotalStakedStart(2L);
        merkleNetworkContext.setPendingRewards(3L);
        return merkleNetworkContext;
    }

    private NetworkStakingRewards getExpectedNetworkStakingRewards() {
        return new NetworkStakingRewards(true, 1L, 2L, 3L);
    }
}
