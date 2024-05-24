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
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StakingRewardsHelperTest extends CryptoTokenHandlerTestBase {
    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();
    }

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

    @Test
    void getsAllRewardReceiversForStakeMetaChanges() {
        final var stakeToMeRewardReceiver = AccountID.newBuilder()
                .accountNum(account.accountId().accountNum())
                .build();
        final var explicitRewardReceiver =
                AccountID.newBuilder().accountNum(1234567L).build();

        final var stakeToMeRewardReceivers = Set.of(stakeToMeRewardReceiver);
        final var explicitRewardReceivers = Set.of(explicitRewardReceiver);
        writableAccountStore.put(account.copyBuilder().stakedNodeId(0L).build());
        final var rewardReceivers = StakingRewardsHelper.getAllRewardReceivers(
                writableAccountStore, stakeToMeRewardReceivers, explicitRewardReceivers);
        assertThat(rewardReceivers).contains(stakeToMeRewardReceiver);
    }

    @Test
    void getsAllRewardReceiversForBalanceChanges() {
        final var stakeToMeRewardReceiver = AccountID.newBuilder()
                .accountNum(account.accountId().accountNum())
                .build();
        final var explicitRewardReceiver =
                AccountID.newBuilder().accountNum(1234567L).build();

        final var stakeToMeRewardReceivers = Set.of(stakeToMeRewardReceiver);
        final var explicitRewardReceivers = Set.of(explicitRewardReceiver);
        writableAccountStore.put(account.copyBuilder().tinybarBalance(1000L).build());
        final var rewardReceivers = StakingRewardsHelper.getAllRewardReceivers(
                writableAccountStore, stakeToMeRewardReceivers, explicitRewardReceivers);
        assertThat(rewardReceivers).contains(stakeToMeRewardReceiver);
    }

    @Test
    void getsAllRewardReceiversIfAlreadyStakedToNode() {
        final var stakeToMeRewardReceiver = AccountID.newBuilder()
                .accountNum(account.accountId().accountNum())
                .build();
        final var explicitRewardReceiver =
                AccountID.newBuilder().accountNum(1234567L).build();

        final var stakeToMeRewardReceivers = Set.of(stakeToMeRewardReceiver);
        final var explicitRewardReceivers = Set.of(explicitRewardReceiver);
        final var rewardReceivers = StakingRewardsHelper.getAllRewardReceivers(
                writableAccountStore, stakeToMeRewardReceivers, explicitRewardReceivers);
        assertThat(rewardReceivers).contains(stakeToMeRewardReceiver);
    }

    @Test
    void getsAllRewardReceiversIfExplicitlyStakedToNode() {
        final var alreadyStakedToNodeRewardReceiver =
                AccountID.newBuilder().accountNum(payerId.accountNum()).build();
        final var explicitRewardReceiver =
                AccountID.newBuilder().accountNum(1234567L).build();

        final var stakeToMeRewardReceivers = Set.of(alreadyStakedToNodeRewardReceiver);
        final var explicitRewardReceivers = Set.of(explicitRewardReceiver);
        final var rewardReceivers = StakingRewardsHelper.getAllRewardReceivers(
                writableAccountStore, stakeToMeRewardReceivers, explicitRewardReceivers);
        assertThat(rewardReceivers).contains(alreadyStakedToNodeRewardReceiver);
    }

    @Test
    void decreasesPendingRewardsAccurately() {
        final var subject = new StakingRewardsHelper();
        assertThat(writableRewardsStore.get().pendingRewards()).isEqualTo(1000L);
        subject.decreasePendingRewardsBy(writableStakingInfoStore, writableRewardsStore, 100L, node0Id.number());
        assertThat(writableRewardsStore.get().pendingRewards()).isEqualTo(900L);
    }

    @Test
    void increasesPendingRewardsAccurately() {
        final var subject = new StakingRewardsHelper();
        assertThat(writableRewardsStore.get().pendingRewards()).isEqualTo(1000L);
        subject.increasePendingRewardsBy(writableRewardsStore, 100L, writableStakingInfoStore.get(0L));
        assertThat(writableRewardsStore.get().pendingRewards()).isEqualTo(1100L);
    }
}
