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

import static com.hedera.node.app.service.token.impl.test.WritableStakingInfoStoreImplTest.NODE_ID_1;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeInfoHelper;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StakeInfoHelperTest {
    private StakeInfoHelper subject;
    private WritableStakingInfoStore store;

    @BeforeEach
    void setUp() {
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
        store = new WritableStakingInfoStore(new MapWritableStates(Map.of(V0490TokenSchema.STAKING_INFO_KEY, state)));
        subject = new StakeInfoHelper();
    }

    @ParameterizedTest
    @CsvSource({
        "20, 15", "9, 14", "10, 15",
    })
    void increaseUnclaimedStartToLargerThanCurrentStakeReward(int amount, int expectedResult) {
        assertUnclaimedStakeRewardStartPrecondition();

        subject.increaseUnclaimedStakeRewards(NODE_ID_1.number(), amount, store);

        final var savedStakeInfo = store.get(NODE_ID_1.number());
        Assertions.assertThat(savedStakeInfo).isNotNull();
        // Case 1: The passed in amount, 20, is greater than the stake reward start, 15, so the unclaimed stake reward
        // value should be the current stake reward start value
        // Case 2: The result should be the stake reward start + the unclaimed stake reward start, 5 + 9 = 14
        // Case 3: Stake reward start + unclaimed stake reward start, 5 + 10 = 15
        Assertions.assertThat(savedStakeInfo.unclaimedStakeRewardStart()).isEqualTo(expectedResult);
    }

    private void assertUnclaimedStakeRewardStartPrecondition() {
        final var existingStakeInfo = store.get(NODE_ID_1.number());
        Assertions.assertThat(existingStakeInfo).isNotNull();
        Assertions.assertThat(existingStakeInfo.unclaimedStakeRewardStart()).isEqualTo(5);
    }
}
