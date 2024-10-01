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

package com.hedera.node.app.service.token.impl.test.handlers.staking;

import static com.hedera.hapi.node.base.HederaFunctionality.NODE_STAKE_UPDATE;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
import static com.hedera.node.app.service.token.impl.test.WritableStakingInfoStoreImplTest.NODE_ID_1;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_1;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_2;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_3;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_4;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.NODE_NUM_8;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.STAKING_INFO_1;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.STAKING_INFO_2;
import static com.hedera.node.app.service.token.impl.test.handlers.staking.EndOfStakingPeriodUpdaterTest.STAKING_INFO_3;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeInfoHelper;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.service.token.records.NodeStakeUpdateStreamBuilder;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.time.Instant;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakeInfoHelperTest {
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();

    private WritableStakingInfoStore infoStore;

    @Mock
    private TokenContext tokenContext;

    @Mock
    private NodeStakeUpdateStreamBuilder streamBuilder;

    @Mock
    private WritableNetworkStakingRewardsStore rewardsStore;

    private final StakeInfoHelper subject = new StakeInfoHelper();

    @ParameterizedTest
    @CsvSource({
        "20, 15", "9, 14", "10, 15",
    })
    void increaseUnclaimedStartToLargerThanCurrentStakeReward(int amount, int expectedResult) {
        final var state = MapWritableKVState.<EntityNumber, StakingNodeInfo>builder(V0490TokenSchema.STAKING_INFO_KEY)
                .value(
                        NODE_ID_1,
                        StakingNodeInfo.newBuilder()
                                .nodeNumber(NODE_ID_1.number())
                                .stake(25)
                                .stakeRewardStart(15)
                                .unclaimedStakeRewardStart(5)
                                .build())
                .build();
        infoStore =
                new WritableStakingInfoStore(new MapWritableStates(Map.of(V0490TokenSchema.STAKING_INFO_KEY, state)));
        assertUnclaimedStakeRewardStartPrecondition();

        subject.increaseUnclaimedStakeRewards(NODE_ID_1.number(), amount, infoStore);

        final var savedStakeInfo = infoStore.get(NODE_ID_1.number());
        Assertions.assertThat(savedStakeInfo).isNotNull();
        // Case 1: The passed in amount, 20, is greater than the stake reward start, 15, so the unclaimed stake reward
        // value should be the current stake reward start value
        // Case 2: The result should be the stake reward start + the unclaimed stake reward start, 5 + 9 = 14
        // Case 3: Stake reward start + unclaimed stake reward start, 5 + 10 = 15
        Assertions.assertThat(savedStakeInfo.unclaimedStakeRewardStart()).isEqualTo(expectedResult);
    }

    @Test
    void marksNonExistingNodesToDeletedInStateAndAddsNewNodesToState() {
        // State has nodeIds 1, 2, 3
        final var stakingInfosState = new MapWritableKVState.Builder<EntityNumber, StakingNodeInfo>(STAKING_INFO_KEY)
                .value(NODE_NUM_1, STAKING_INFO_1)
                .value(NODE_NUM_2, STAKING_INFO_2)
                .value(NODE_NUM_3, STAKING_INFO_3)
                .build();
        final var newStates = newStatesInstance(stakingInfosState);
        infoStore = new WritableStakingInfoStore(newStates);
        // Platform address book has node Ids 2, 4, 8
        final var networkInfo = new FakeNetworkInfo();
        given(tokenContext.consensusTime()).willReturn(Instant.EPOCH);
        given(tokenContext.addPrecedingChildRecordBuilder(NodeStakeUpdateStreamBuilder.class, NODE_STAKE_UPDATE))
                .willReturn(streamBuilder);
        given(streamBuilder.transaction(any())).willReturn(streamBuilder);
        given(streamBuilder.memo(any())).willReturn(streamBuilder);

        // Should update the state to mark node 1 and 3 as deleted
        subject.adjustPostUpgradeStakes(tokenContext, networkInfo, DEFAULT_CONFIG, infoStore, rewardsStore);
        final var updatedStates = newStates.get(STAKING_INFO_KEY);
        // marks nodes 1, 2 as deleted
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_1)).deleted()).isTrue();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_2)).deleted()).isFalse();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_3)).deleted()).isTrue();
        // Also adds node 4 to the state
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).deleted()).isFalse();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).weight()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).minStake()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_4)).maxStake()).isEqualTo(1666666666666666666L);
        // Also adds node 8 to the state
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).deleted()).isFalse();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).weight()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).minStake()).isZero();
        assertThat(((StakingNodeInfo) updatedStates.get(NODE_NUM_8)).maxStake()).isEqualTo(1666666666666666666L);
    }

    private MapWritableStates newStatesInstance(final MapWritableKVState<EntityNumber, StakingNodeInfo> stakingInfo) {
        //noinspection ReturnOfNull
        return MapWritableStates.builder()
                .state(stakingInfo)
                .state(new WritableSingletonStateBase<>(STAKING_NETWORK_REWARDS_KEY, () -> null, c -> {}))
                .build();
    }

    private void assertUnclaimedStakeRewardStartPrecondition() {
        final var existingStakeInfo = infoStore.get(NODE_ID_1.number());
        Assertions.assertThat(existingStakeInfo).isNotNull();
        Assertions.assertThat(existingStakeInfo.unclaimedStakeRewardStart()).isEqualTo(5);
    }
}
