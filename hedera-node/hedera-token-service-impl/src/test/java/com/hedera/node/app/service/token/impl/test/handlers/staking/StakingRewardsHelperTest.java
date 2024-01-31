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

package com.hedera.node.app.service.token.impl.test.handlers.staking;

import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.requiresExternalization;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StakingRewardsHelperTest {
    @Test
    void onlyNonZeroRewardsIncludedInAccountAmounts() {
        final var zeroRewardId = AccountID.newBuilder().accountNum(1234L).build();
        final var nonZeroRewardId = AccountID.newBuilder().accountNum(4321L).build();
        final var someRewards = Map.of(zeroRewardId, 0L, nonZeroRewardId, 1L);
        final var paidStakingRewards = StakingRewardsHelper.asAccountAmounts(someRewards);
        assertThat(paidStakingRewards)
                .containsExactly(AccountAmount.newBuilder()
                        .accountID(nonZeroRewardId)
                        .amount(1L)
                        .build());
    }

    @Test
    void emptyRewardsPaidDoesNotNeedExternalizing() {
        assertThat(requiresExternalization(Map.of())).isFalse();
    }

    @Test
    void onlyZeroRewardPaidDoesNotNeedExternalizing() {
        final var zeroRewardId = AccountID.newBuilder().accountNum(1234L).build();
        assertThat(requiresExternalization(Map.of(zeroRewardId, 0L))).isFalse();
    }

    @Test
    void nonZeroRewardsPaidNeedsExternalizing() {
        final var zeroRewardId = AccountID.newBuilder().accountNum(1234L).build();
        final var nonZeroRewardId = AccountID.newBuilder().accountNum(4321L).build();
        final var someRewards = Map.of(zeroRewardId, 0L, nonZeroRewardId, 1L);
        assertThat(requiresExternalization(someRewards)).isTrue();
    }
}
