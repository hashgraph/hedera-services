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

import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStoreImpl;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.WritableStates;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WritableStakingInfoStoreImplTest {
    private static final long NODE_ID_1 = 1;

    private WritableStakingInfoStoreImpl subject;

    @BeforeEach
    void setUp() {
        final var wrappedState = MapWritableKVState.<Long, StakingNodeInfo>builder(TokenServiceImpl.STAKING_INFO_KEY)
                .value(
                        NODE_ID_1,
                        StakingNodeInfo.newBuilder()
                                .nodeNumber(NODE_ID_1)
                                .stake(25)
                                .stakeRewardStart(15)
                                .unclaimedStakeRewardStart(5)
                                .build())
                .build();
        subject = new WritableStakingInfoStoreImpl(
                new MapWritableStates(Map.of(TokenServiceImpl.STAKING_INFO_KEY, wrappedState)));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void constructorWithNullArg() {
        Assertions.assertThatThrownBy(() -> new WritableStakingInfoStoreImpl(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorWithNonNullArg() {
        Assertions.assertThatCode(() -> new WritableStakingInfoStoreImpl(mock(WritableStates.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void getForModifyNodeIdNotFound() {
        Assertions.assertThat(subject.get(-1)).isNull();
        Assertions.assertThat(subject.get(NODE_ID_1 + 1)).isNull();
    }

    @Test
    void getForModifyInfoFound() {
        Assertions.assertThat(subject.get(NODE_ID_1)).isNotNull().isInstanceOf(StakingNodeInfo.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void putWithNullArg() {
        Assertions.assertThatThrownBy(() -> subject.put(NODE_ID_1 + 1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void putSuccess() {
        final var newNodeId = NODE_ID_1 + 1;
        final var newStakingInfo =
                StakingNodeInfo.newBuilder().nodeNumber(newNodeId).stake(20).build();
        subject.put(newNodeId, newStakingInfo);

        Assertions.assertThat(subject.get(2)).isEqualTo(newStakingInfo);
    }

    @Test
    void increaseUnclaimedStartToLargerThanCurrentStakeReward() {
        assertUnclaimedStakeRewardStartPrecondition();

        subject.increaseUnclaimedStakeRewardStart(NODE_ID_1, 20);

        final var savedStakeInfo = subject.get(NODE_ID_1);
        Assertions.assertThat(savedStakeInfo).isNotNull();
        // The passed in amount, 20, is greater than the stake reward start, 15, so the unclaimed stake reward start
        // value should be the current stake reward start value
        Assertions.assertThat(savedStakeInfo.unclaimedStakeRewardStart()).isEqualTo(15);
    }

    @Test
    void increaseUnclaimedStartToLessThanCurrentStakeReward() {
        assertUnclaimedStakeRewardStartPrecondition();

        subject.increaseUnclaimedStakeRewardStart(NODE_ID_1, 9);

        final var savedStakeInfo = subject.get(NODE_ID_1);
        Assertions.assertThat(savedStakeInfo).isNotNull();
        // The result should be the stake reward start + the unclaimed stake reward start, 5 + 9 = 14
        Assertions.assertThat(savedStakeInfo.unclaimedStakeRewardStart()).isEqualTo(14);
    }

    @Test
    void increaseUnclaimedStartToExactlyCurrentStakeReward() {
        assertUnclaimedStakeRewardStartPrecondition();

        subject.increaseUnclaimedStakeRewardStart(NODE_ID_1, 10);

        final var savedStakeInfo = subject.get(NODE_ID_1);
        Assertions.assertThat(savedStakeInfo).isNotNull();
        // Stake reward start + unclaimed stake reward start, 5 + 10 = 15
        Assertions.assertThat(savedStakeInfo.unclaimedStakeRewardStart()).isEqualTo(15);
    }

    private void assertUnclaimedStakeRewardStartPrecondition() {
        final var existingStakeInfo = subject.get(NODE_ID_1);
        Assertions.assertThat(existingStakeInfo).isNotNull();
        Assertions.assertThat(existingStakeInfo.unclaimedStakeRewardStart()).isEqualTo(5);
    }
}
