/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.ledger.accounts.staking.StakePeriodManager.ZONE_UTC;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakePeriodManager;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.Units;
import com.hedera.node.app.service.token.impl.staking.RewardCalculator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RewardCalculatorTest {
    private static final long TODAY_NUMBER =
            LocalDate.ofInstant(Instant.ofEpochSecond(12345678910L), ZONE_UTC).toEpochDay();
    private static final int REWARD_HISTORY_SIZE = 366;

    @Mock
    private StakePeriodManager stakePeriodManager;

    @Mock
    private ReadableStakingInfoStore stakingInfoStore;

    @Mock
    private StakingNodeInfo stakingNodeInfo;

    @Mock
    private Account account;

    private List<Long> rewardHistory;

    private RewardCalculator subject;

    @BeforeEach
    void setUp() {
        rewardHistory = newRewardHistory();
        subject = new RewardCalculator(stakePeriodManager, stakingInfoStore);
    }

    @Test
    void zeroRewardsForMissingNodeStakeInfo() {
        final var reward = subject.computeRewardFromDetails(Account.newBuilder().build(), null, 321, 123);
        assertEquals(0, reward);
    }

    @Test
    void delegatesEpochSecondAtStartOfPeriod() {
        given(stakePeriodManager.epochSecondAtStartOfPeriod(123)).willReturn(456L);
        assertEquals(456L, subject.epochSecondAtStartOfPeriod(123));
    }

    @Test
    void updatesRewardsAsExpected() {
        final var changes = new HashMap<AccountProperty, Object>();
        setUpMocks();
        given(stakingInfoStore.get(0L)).willReturn(stakingNodeInfo);
        given(stakePeriodManager.currentStakePeriod()).willReturn(TODAY_NUMBER);
        given(stakingNodeInfo.rewardSumHistory()).willReturn(rewardHistory);
        given(account.stakePeriodStart()).willReturn(TODAY_NUMBER - 2);
        given(account.stakeAtStartOfLastRewardedPeriod()).willReturn(-1L);
        // Staked node ID of -1 will return a node ID address of 0
        given(account.stakedNodeId()).willReturn(-1L);
        given(account.declineReward()).willReturn(false);
        given(account.tinybarBalance()).willReturn(50 * Units.HBARS_TO_TINYBARS);
        given(account.stakedToMe()).willReturn(50 * Units.HBARS_TO_TINYBARS);

        subject.setRewardsPaidInThisTxn(100L);
        final var reward = subject.computePendingReward(account);
        subject.applyReward(reward, account, changes);

        assertEquals(500, reward);
        assertEquals(account.tinybarBalance() + reward, changes.get(AccountProperty.BALANCE));
        assertEquals(100 + reward, subject.rewardsPaidInThisTxn());

        // resets all fields
        subject.reset();
        assertEquals(0, subject.rewardsPaidInThisTxn());
    }

    @Test
    void returnsZeroRewardsIfRewardSumHistoryIsEmpty() {
        final var changes = new HashMap<AccountProperty, Object>();

        setUpMocks();
        given(stakePeriodManager.currentStakePeriod()).willReturn(TODAY_NUMBER);
        given(stakingInfoStore.get(0L)).willReturn(stakingNodeInfo);
        given(stakingNodeInfo.rewardSumHistory()).willReturn(rewardHistory);
        given(account.stakePeriodStart()).willReturn(TODAY_NUMBER - 1);
        // Staked node ID of -1 will return a node ID address of 0
        given(account.stakedNodeId()).willReturn(-1L);

        subject.setRewardsPaidInThisTxn(100L);
        final var reward = subject.computePendingReward(account);
        subject.applyReward(reward, account, changes);

        assertEquals(0, reward);
        assertFalse(changes.containsKey(AccountProperty.BALANCE));
        assertEquals(100, subject.rewardsPaidInThisTxn());
    }

    @Test
    void doesntComputeRewardReturnsZeroIfNotInRange() {
        final var changes = new HashMap<AccountProperty, Object>();

        given(stakePeriodManager.effectivePeriod(anyLong())).willReturn(TODAY_NUMBER - 1);
        given(account.stakePeriodStart()).willReturn(TODAY_NUMBER - 1);
        willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());

        final var reward = subject.computePendingReward(account);
        subject.applyReward(reward, account, changes);

        assertEquals(0, reward);

        assertFalse(changes.containsKey(AccountProperty.BALANCE));
        assertEquals(0, subject.rewardsPaidInThisTxn());
    }

    @Test
    void calculatesRewardsAppropriatelyIfBalanceAtStartOfLastRewardedPeriodIsSet() {
        rewardHistory.set(0, 6L);
        rewardHistory.set(1, 3L);
        rewardHistory.set(2, 1L);
        setUpMocks();
        given(stakingInfoStore.get(0L)).willReturn(stakingNodeInfo);
        given(stakePeriodManager.currentStakePeriod()).willReturn(TODAY_NUMBER);
        given(stakingNodeInfo.rewardSumHistory()).willReturn(rewardHistory);
        // Staked node ID of -1 will return a node ID address of 0
        given(account.stakedNodeId()).willReturn(-1L);
        given(account.declineReward()).willReturn(false);
        given(account.stakedToMe()).willReturn(98 * Units.HBARS_TO_TINYBARS);
        given(account.tinybarBalance()).willReturn(2 * Units.HBARS_TO_TINYBARS);
        given(account.stakeAtStartOfLastRewardedPeriod()).willReturn(90 * Units.HBARS_TO_TINYBARS);

        given(account.stakePeriodStart()).willReturn(TODAY_NUMBER - 4);
        // (98+2) * (6-1) + 90 * (1-0) = 590;
        var reward = subject.computePendingReward(account);

        assertEquals(590, reward);

        given(account.stakePeriodStart()).willReturn(TODAY_NUMBER - 3);
        // (98+2) * (6-3) + 90 * (3-1) = 480;
        reward = subject.computePendingReward(account);

        assertEquals(480, reward);

        given(account.stakePeriodStart()).willReturn(TODAY_NUMBER - 2);
        // (98+2) * (6-6) + 90 * (6-3) = 270;
        reward = subject.computePendingReward(account);

        assertEquals(270, reward);
    }

    @Test
    void adjustsEffectiveStartIfBeforeAnYear() {
        setUpMocks();
        final var changes = new HashMap<AccountProperty, Object>();

        final var expectedStakePeriodStart = 19365L;

        final var account = Account.newBuilder()
                .stakePeriodStart(expectedStakePeriodStart - 500)
                // Staked node ID of -3 will return a node ID address of 2
                .stakedNodeId(-3L)
                .tinybarBalance(100 * Units.HBARS_TO_TINYBARS)
                .stakedToMe(100 * Units.HBARS_TO_TINYBARS)
                .build();

        given(stakingNodeInfo.rewardSumHistory()).willReturn(rewardHistory);
        given(stakePeriodManager.currentStakePeriod()).willReturn(expectedStakePeriodStart);
        given(stakePeriodManager.effectivePeriod(anyLong())).willReturn(expectedStakePeriodStart - 365);
        given(stakingInfoStore.get(2L)).willReturn(stakingNodeInfo);

        final var reward = subject.computePendingReward(account);
        subject.applyReward(reward, account, changes);

        assertEquals(1000, reward);
        assertEquals(account.tinybarBalance() + reward, changes.get(AccountProperty.BALANCE));
        assertEquals(reward, subject.rewardsPaidInThisTxn());
    }

    @Test
    void onlyAppliesRewardIfNotDeclined() {
        given(account.declineReward()).willReturn(true);

        assertDoesNotThrow(() -> subject.applyReward(123, account, Collections.emptyMap()));
    }

    @Test
    void doesntApplyRedirectedRewardToNewlyCreatedAccountWithExpectedDecline() {
        final Map<AccountProperty, Object> changes = new EnumMap<>(AccountProperty.class);
        changes.put(AccountProperty.DECLINE_REWARD, Boolean.TRUE);
        subject.applyReward(123, null, changes);
        assertEquals(1, changes.size());
    }

    @Test
    void estimatesPendingRewardsForStateView() {
        final var todayNum = 300L;

        given(stakePeriodManager.estimatedCurrentStakePeriod()).willReturn(todayNum);
        given(stakingNodeInfo.rewardSumHistory()).willReturn(rewardHistory);
        given(account.stakePeriodStart()).willReturn(todayNum - 2);
        given(account.stakeAtStartOfLastRewardedPeriod()).willReturn(-1L);
        given(account.declineReward()).willReturn(false);
        given(account.stakedToMe()).willReturn(100 * Units.HBARS_TO_TINYBARS);
        given(stakePeriodManager.effectivePeriod(todayNum - 2)).willReturn(todayNum - 2);
        given(stakePeriodManager.isEstimatedRewardable(todayNum - 2)).willReturn(true);

        subject.setRewardsPaidInThisTxn(100L);
        final long reward = subject.estimatePendingRewards(account, stakingNodeInfo);

        assertEquals(500, reward);

        // no changes to state
        assertEquals(100, subject.rewardsPaidInThisTxn());

        // if declinedReward
        given(account.declineReward()).willReturn(true);
        assertEquals(0L, subject.estimatePendingRewards(account, stakingNodeInfo));
    }

    @Test
    void onlyEstimatesPendingRewardsIfRewardable() {
        final var todayNum = 300L;

        given(account.stakePeriodStart()).willReturn(todayNum - 2);
        given(stakePeriodManager.effectivePeriod(todayNum - 2)).willReturn(todayNum - 2);

        final long reward = subject.estimatePendingRewards(account, stakingNodeInfo);

        assertEquals(0, reward);
    }

    private void setUpMocks() {
        given(stakePeriodManager.firstNonRewardableStakePeriod()).willReturn(TODAY_NUMBER);
        willCallRealMethod().given(stakePeriodManager).effectivePeriod(anyLong());
        willCallRealMethod().given(stakePeriodManager).isRewardable(anyLong());
    }

    private static List<Long> newRewardHistory() {
        final var rewardHistory = IntStream.range(0, REWARD_HISTORY_SIZE)
                .mapToObj(i -> 0L)
                .collect(Collectors.toCollection(() -> new ArrayList<>(REWARD_HISTORY_SIZE)));
        rewardHistory.set(0, 5L);
        return rewardHistory;
    }
}
