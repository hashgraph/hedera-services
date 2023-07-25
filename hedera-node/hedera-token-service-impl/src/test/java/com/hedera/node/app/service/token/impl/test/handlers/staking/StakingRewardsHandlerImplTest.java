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

package com.hedera.node.app.service.token.impl.test.handlers.staking;

import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtils.roundedToHbar;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtils.totalStake;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeInfoHelper;
import com.hedera.node.app.service.token.impl.handlers.staking.StakePeriodManager;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeRewardCalculatorImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandlerImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsPayer;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakingRewardsHandlerImplTest extends CryptoTokenHandlerTestBase {
    private static final Long NO_LAST_PERIOD_STAKE = -1L;
    private static final Long NO_BALANCE_DIFFERENCE = -1L;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    private StakingRewardsHandlerImpl subject;
    private StakePeriodManager stakePeriodManager;
    private StakingRewardsPayer rewardsPayer;
    private StakeInfoHelper stakeInfoHelper;
    private StakeRewardCalculatorImpl stakeRewardCalculator;
    private StakingRewardsHelper stakingRewardHelper;
    private final EntityNum node0Id = EntityNum.fromLong(0L);
    private final EntityNum node1Id = EntityNum.fromLong(1L);
    private final long stakingRewardAccountNum = 800L;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();

        given(configProvider.getConfiguration()).willReturn(versionedConfig);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        givenStoresAndConfig(handleContext);

        stakingRewardHelper = new StakingRewardsHelper();
        stakePeriodManager = new StakePeriodManager(configProvider);
        stakeRewardCalculator = new StakeRewardCalculatorImpl(stakePeriodManager);
        rewardsPayer = new StakingRewardsPayer(stakingRewardHelper, stakeRewardCalculator);
        stakeInfoHelper = new StakeInfoHelper();
        subject = new StakingRewardsHandlerImpl(rewardsPayer, stakingRewardHelper, stakePeriodManager, stakeInfoHelper);
    }

    @Test
    void changingKeyOnlyIsNotRewardSituation() {
        final var stakedToMeBefore = account.stakedToMe();
        final var stakePeriodStartBefore = account.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodBefore = account.stakeAtStartOfLastRewardedPeriod();

        noStakeChanges();

        final var rewards = subject.applyStakingRewards(handleContext);

        assertThat(rewards).isEmpty();
        final var modifiedAccount = writableAccountStore.get(payerId);
        final var stakedToMeAfter = modifiedAccount.stakedToMe();
        final var stakePeriodStartAfter = modifiedAccount.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodAfter = modifiedAccount.stakeAtStartOfLastRewardedPeriod();

        assertThat(stakedToMeAfter).isEqualTo(stakedToMeBefore);
        assertThat(stakePeriodStartAfter).isEqualTo(stakePeriodStartBefore);
        assertThat(stakeAtStartOfLastRewardedPeriodAfter).isEqualTo(stakeAtStartOfLastRewardedPeriodBefore);
    }

    @Test
    void rewardsWhenStakingFieldsModified() {
        final var stakedToMeBefore = account.stakedToMe();
        final var stakePeriodStartBefore = account.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodBefore = account.stakeAtStartOfLastRewardedPeriod();

        randomStakeNodeChanges();

        final var rewards = subject.applyStakingRewards(handleContext);

        // earned zero rewards due to zero stake
        assertThat(rewards).hasSize(1);
        assertThat(rewards).containsEntry(payerId, 0L);

        final var modifiedAccount = writableAccountStore.get(payerId);
        // stakedToMe will not change as this is not staked by another account
        final var stakedToMeAfter = modifiedAccount.stakedToMe();
        // These should change as staking is triggered
        final var stakePeriodStartAfter = modifiedAccount.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodAfter = modifiedAccount.stakeAtStartOfLastRewardedPeriod();

        final var expectedStakePeriodStart = stakePeriodManager.currentStakePeriod(consensusInstant);
        assertThat(stakedToMeAfter).isEqualTo(stakedToMeBefore);
        assertThat(stakePeriodStartAfter).isNotEqualTo(stakePeriodStartBefore);
        assertThat(stakePeriodStartAfter).isEqualTo(expectedStakePeriodStart);
        assertThat(stakeAtStartOfLastRewardedPeriodAfter).isEqualTo(stakeAtStartOfLastRewardedPeriodBefore);
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndHasntBeenRewardedUnclaimsStakeWhenChangingElection() {
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(555L * HBARS_TO_TINYBARS)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = LocalDate.ofEpochDay(stakePeriodStart + 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(writableAccountStore.get(payerId).tinybarBalance()).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedDaysAgoUnclaimsStakeWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(newBalance / 5)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = originalInstant.plus(2, ChronoUnit.DAYS);

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(newBalance).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedTodayUnclaimsStakeStartWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(newBalance / 5)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = originalInstant.plus(1, ChronoUnit.DAYS);

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(newBalance).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatStartedStakingAtCurrentPeriodDoesntUnclaimStakeWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(account.copyBuilder().stakedNodeId(0L).build());

        given(handleContext.consensusNow()).willReturn(stakePeriodStartInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatDeclineRewardsDoesntUnclaimStakeWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(true)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(
                writableAccountStore.get(payerId).copyBuilder().stakedNodeId(0L).build());

        given(handleContext.consensusNow()).willReturn(originalInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    //    @Test
    //    void anAutoCreatedAccountShouldNotHaveStakeStartUpdated() {
    //        final var newId = AccountID.newBuilder().accountNum(10000000000L).build();
    //        writableAccountStore.put(givenValidAccountBuilder().accountId(newId).build());
    //
    //        given(handleContext.consensusNow()).willReturn(stakePeriodStartInstant);
    //        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    //        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);
    //
    //        subject.applyStakingRewards(handleContext);
    //
    //        assertThat(0).isEqualTo(writableAccountStore.get(newId).stakeAtStartOfLastRewardedPeriod());
    //    }

    @Test
    void earningZeroRewardsWithStartBeforeLastNonRewardableStillUpdatesSASOLARP() {
        final var account = mock(Account.class);
        final var manager = mock(StakePeriodManager.class);
        given(manager.firstNonRewardableStakePeriod(readableRewardsStore, consensusInstant))
                .willReturn(3L);
        given(account.stakePeriodStart()).willReturn(2L);

        final StakingRewardsHandlerImpl impl =
                new StakingRewardsHandlerImpl(rewardsPayer, stakingRewardHelper, manager, stakeInfoHelper);

        assertThat(impl.shouldUpdateStakeStart(account, true, 0L, readableRewardsStore, consensusInstant))
                .isTrue();
    }

    @Test
    void anAccountWithAlreadyCollectedRewardShouldNotHaveStakeStartUpdated() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(newBalance - 1)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .tinybarBalance(2 * newBalance)
                .build());

        given(handleContext.consensusNow()).willReturn(stakePeriodStartInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(1L);

        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void calculatesRewardIfNeededStakingToNode() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var ownerBalance = 111L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        final var node1InfoBefore = writableStakingInfoState.get(1L);
        final var node0InfoBefore = writableStakingInfoState.get(0L);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());
        writableAccountStore.put(ownerAccount
                .copyBuilder()
                .tinybarBalance(ownerBalance + HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(1L);
        final var node0InfoAfter = writableStakingInfoState.get(0L);

        assertThat(rewards).hasSize(1);
        assertThat(rewards.get(payerId)).isEqualTo(55500L);
        assertThat(rewards.get(ownerId)).isEqualTo(null);

        assertThat(node1InfoAfter.stakeToReward()).isEqualTo(node1InfoBefore.stakeToReward() - accountBalance);

        final var modifiedAccount = writableAccountStore.get(payerId);
        assertThat(node0InfoAfter.stakeToReward())
                .isEqualTo(node0InfoBefore.stakeToReward() + roundedToHbar(totalStake(modifiedAccount)));

        assertThat(node1InfoAfter.unclaimedStakeRewardStart())
                .isEqualTo(node1InfoBefore.unclaimedStakeRewardStart() + accountBalance);

        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void doesNotAwardStakeFromDeletedAccount() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var ownerBalance = 111L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(true)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());
        writableAccountStore.put(ownerAccount
                .copyBuilder()
                .tinybarBalance(ownerBalance + HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        // TODO: this will change once transfer to beneficiary is implemented
        assertThatThrownBy(() -> subject.applyStakingRewards(handleContext)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChanges() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));
        final var initialStakePeriodStart = payerAccountBefore.stakePeriodStart();

        final var node1InfoBefore = writableStakingInfoState.get(1L);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(1L);

        assertThat(rewards).hasSize(1);
        assertThat(rewards.get(payerId)).isEqualTo(55500L);
        assertThat(rewards.get(ownerId)).isEqualTo(null);

        assertThat(node1InfoAfter.stake()).isEqualTo(node1InfoBefore.stake());
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(node1InfoBefore.unclaimedStakeRewardStart());
        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());

        final var modifiedAccount = writableAccountStore.get(payerId);
        assertThat(modifiedAccount.tinybarBalance())
                .isEqualTo(accountBalance - HBARS_TO_TINYBARS + rewards.get(payerId));
        assertThat(modifiedAccount.stakePeriodStart()).isNotEqualTo(initialStakePeriodStart);
        assertThat(modifiedAccount.stakePeriodStart()).isNotEqualTo(stakePeriodStart + 2);
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChangesAndNoReward() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));
        final var initialStakePeriodStart = payerAccountBefore.stakePeriodStart();

        final var node1InfoBefore = writableStakingInfoState.get(1L);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(1L);

        // No rewards rewarded
        assertThat(rewards).hasSize(1);
        assertThat(rewards.get(payerId)).isZero();

        assertThat(node1InfoAfter.stake()).isEqualTo(node1InfoBefore.stake());
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(node1InfoBefore.unclaimedStakeRewardStart());
        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());

        final var modifiedAccount = writableAccountStore.get(payerId);
        assertThat(modifiedAccount.tinybarBalance()).isEqualTo(accountBalance - HBARS_TO_TINYBARS);
        assertThat(modifiedAccount.stakePeriodStart()).isEqualTo(initialStakePeriodStart);
    }

    @Test
    void sasolarpMgmtWorksAsExpectedWhenStakingToNodeWithNoStakingMetaChangesAndNoReward() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(true)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        final var initialStakePeriodStart = payerAccountBefore.stakePeriodStart();

        final var node1InfoBefore = writableStakingInfoState.get(1L);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);
        // No rewards rewarded
        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(1L);

        assertThat(rewards).hasSize(1);
        assertThat(rewards.get(payerId)).isZero();

        assertThat(node1InfoAfter.stake()).isEqualTo(node1InfoBefore.stake());
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(node1InfoBefore.unclaimedStakeRewardStart());
        assertThat(0).isEqualTo(node1Info.unclaimedStakeRewardStart());

        final var modifiedAccount = writableAccountStore.get(payerId);
        assertThat(modifiedAccount.tinybarBalance()).isEqualTo(accountBalance - HBARS_TO_TINYBARS);
        assertThat(modifiedAccount.stakePeriodStart()).isEqualTo(initialStakePeriodStart);
        assertThat(modifiedAccount.stakeAtStartOfLastRewardedPeriod())
                .isEqualTo(modifiedAccount.tinybarBalance() + modifiedAccount.stakedToMe());
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToAccount() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var ownerBalance = 111L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        final var node1InfoBefore = writableStakingInfoState.get(1L);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .stakedAccountId(ownerId)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.writableStore(WritableStakingInfoStore.class)).willReturn(writableStakingInfoStore);

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(1L);

        assertThat(rewards).hasSize(1);
        assertThat(rewards.get(payerId)).isEqualTo(55500L);

        assertThat(node1InfoAfter.stakeToReward()).isEqualTo(node1InfoBefore.stakeToReward() - accountBalance);
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(accountBalance);
        // stake field of the account is updated once a day

        final var modifiedOwner = writableAccountStore.get(ownerId);
        assertThat(modifiedOwner.stakedToMe()).isEqualTo(ownerAccountBefore.stakedToMe() + accountBalance);
        // stakePeriodStart is updated only when reward is applied
        assertThat(modifiedOwner.stakePeriodStart()).isEqualTo(stakePeriodStart);

        final var modifiedPayer = writableAccountStore.get(payerId);
        assertThat(modifiedPayer.stakedToMe()).isEqualTo(payerAccountBefore.stakedToMe());
        assertThat(modifiedPayer.stakePeriodStart()).isEqualTo(stakePeriodStart + 2);
    }
    //
    //    @Test
    //    void rewardsUltimateBeneficiaryInsteadOfDeletedAccount() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var tbdReward = 1_234L;
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        beneficiary.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var pendingChanges = buildPendingNodeStakeChanges();
    //        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
    //        pendingChanges.changes(0).put(BALANCE, 0L);
    //
    //        final Map<AccountProperty, Object> firstBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
    //        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
    //        pendingChanges.include(partyId, party, firstBeneficiaryChanges);
    //
    //        final Map<AccountProperty, Object> secondBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        secondBeneficiaryChanges.put(AccountProperty.BALANCE, partyBalance + counterpartyBalance +
    // beneficiaryBalance);
    //        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);
    //
    //        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum())).willReturn(partyId.getAccountNum());
    //        given(txnCtx.getBeneficiaryOfDeleted(partyId.getAccountNum())).willReturn(beneficiaryId.getAccountNum());
    //        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(2);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(counterparty)).willReturn(tbdReward);
    //        given(rewardCalculator.computePendingReward(beneficiary)).willReturn(tbdReward);
    //        given(rewardCalculator.applyReward(tbdReward, beneficiary, pendingChanges.changes(2)))
    //                .willReturn(true);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.preview(pendingChanges);
    //        verify(rewardCalculator, times(2)).applyReward(tbdReward, beneficiary, pendingChanges.changes(2));
    //        verify(sideEffectsTracker, times(2)).trackRewardPayment(beneficiaryId.getAccountNum(), tbdReward);
    //    }
    //
    //    @Test
    //    void doesntTrackAnythingIfRedirectBeneficiaryDeclinedReward() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        final var tbdReward = 1_234L;
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        beneficiary.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var pendingChanges = buildPendingNodeStakeChanges();
    //        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
    //        pendingChanges.changes(0).put(BALANCE, 0L);
    //
    //        final Map<AccountProperty, Object> firstBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
    //        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
    //        pendingChanges.include(partyId, party, firstBeneficiaryChanges);
    //
    //        final Map<AccountProperty, Object> secondBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        secondBeneficiaryChanges.put(AccountProperty.BALANCE, partyBalance + counterpartyBalance +
    // beneficiaryBalance);
    //        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);
    //
    //        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum())).willReturn(partyId.getAccountNum());
    //        given(txnCtx.getBeneficiaryOfDeleted(partyId.getAccountNum())).willReturn(beneficiaryId.getAccountNum());
    //        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(2);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(counterparty)).willReturn(tbdReward);
    //        given(rewardCalculator.computePendingReward(beneficiary)).willReturn(tbdReward);
    //        given(rewardCalculator.applyReward(tbdReward, beneficiary, pendingChanges.changes(2)))
    //                .willReturn(false)
    //                .willReturn(true);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.preview(pendingChanges);
    //        verify(rewardCalculator, times(2)).applyReward(tbdReward, beneficiary, pendingChanges.changes(2));
    //        verify(sideEffectsTracker, times(1)).trackRewardPayment(beneficiaryId.getAccountNum(), tbdReward);
    //    }
    //
    //    @Test
    //    void failsHardIfMoreRedirectsThanDeletedEntitiesAreNeeded() {
    //        given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //        beneficiary.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var pendingChanges = buildPendingNodeStakeChanges();
    //        pendingChanges.changes(0).put(IS_DELETED, Boolean.TRUE);
    //        pendingChanges.changes(0).put(BALANCE, 0L);
    //
    //        final Map<AccountProperty, Object> firstBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        firstBeneficiaryChanges.put(IS_DELETED, Boolean.TRUE);
    //        firstBeneficiaryChanges.put(AccountProperty.BALANCE, 0L);
    //        pendingChanges.include(partyId, party, firstBeneficiaryChanges);
    //
    //        final Map<AccountProperty, Object> secondBeneficiaryChanges = new EnumMap<>(AccountProperty.class);
    //        secondBeneficiaryChanges.put(AccountProperty.BALANCE, partyBalance + counterpartyBalance +
    // beneficiaryBalance);
    //        pendingChanges.include(beneficiaryId, beneficiary, secondBeneficiaryChanges);
    //
    //        given(txnCtx.getBeneficiaryOfDeleted(counterpartyId.getAccountNum())).willReturn(partyId.getAccountNum());
    //        given(txnCtx.numDeletedAccountsAndContracts()).willReturn(1);
    //        given(networkCtx.areRewardsActivated()).willReturn(true);
    //        given(rewardCalculator.computePendingReward(any())).willReturn(123L);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        assertThrows(IllegalStateException.class, () -> subject.preview(pendingChanges));
    //    }
    //
    //    @Test
    //    void updatesStakedToMeSideEffects() {
    //        counterparty.setStakedId(1L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var pendingChanges = buildPendingAccountStakeChanges();
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        given(accounts.get(EntityNum.fromLong(1L))).willReturn(merkleAccount);
    //        given(merkleAccount.getStakedToMe()).willReturn(0L);
    //
    //        given(accounts.get(EntityNum.fromLong(2L))).willReturn(merkleAccount);
    //        given(merkleAccount.getStakedToMe()).willReturn(0L);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[1] = 0;
    //        subject.getRewardsEarned()[2] = 1;
    //        assertEquals(2, pendingChanges.size());
    //
    //        subject.setCurStakedId(1L);
    //        subject.setNewStakedId(2L);
    //        Arrays.fill(subject.getStakedToMeUpdates(), NA);
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //        assertEquals(-555L * HBARS_TO_TINYBARS, subject.getStakedToMeUpdates()[2]);
    //        assertEquals(100L * HBARS_TO_TINYBARS, subject.getStakedToMeUpdates()[3]);
    //    }
    //
    //    @Test
    //    void includesIndirectStakeeInChangesEvenIfTotalStakeUnchanged() {
    //        counterparty.setStakedId(1L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var pendingChanges = buildPendingAccountStakeChanges(counterpartyBalance + 1);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //        given(accounts.get(EntityNum.fromLong(1L))).willReturn(merkleAccount);
    //        given(merkleAccount.getStakedToMe()).willReturn(counterpartyBalance);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[1] = 0;
    //        subject.getRewardsEarned()[2] = 1;
    //        assertEquals(2, pendingChanges.size());
    //
    //        subject.setCurStakedId(1L);
    //        subject.setNewStakedId(1L);
    //        Arrays.fill(subject.getStakedToMeUpdates(), NA);
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //        assertEquals(counterpartyBalance, subject.getStakedToMeUpdates()[2]);
    //    }
    //
    //    @Test
    //    void doesntUpdateStakedToMeIfStakerBalanceIsExactlyTheSame() {
    //        counterparty.setStakedId(1L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final Map<AccountProperty, Object> stakingFundChanges = Map.of(AccountProperty.BALANCE, 100L);
    //        final var pendingChanges = buildPendingAccountStakeChanges(counterpartyBalance);
    //        pendingChanges.include(stakingFundId, stakingFund, stakingFundChanges);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[1] = 0;
    //        subject.getRewardsEarned()[2] = 1;
    //        assertEquals(2, pendingChanges.size());
    //
    //        subject.setCurStakedId(1L);
    //        subject.setNewStakedId(1L);
    //        Arrays.fill(subject.getStakedToMeUpdates(), NA);
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //        assertEquals(NA, subject.getStakedToMeUpdates()[2]);
    //    }
    //
    //    @Test
    //    void updatesStakedToMeSideEffectsPaysRewardsIfRewardable() {
    //        counterparty.setStakedId(123L);
    //        stakingFund.setStakePeriodStart(-1);
    //        counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //        final var stakingFundChanges = new HashMap<AccountProperty, Object>();
    //        stakingFundChanges.put(AccountProperty.BALANCE, 100L);
    //
    //        final var map = new HashMap<AccountProperty, Object>();
    //        map.put(AccountProperty.BALANCE, 100L);
    //        map.put(AccountProperty.STAKED_ID, 123L);
    //        map.put(AccountProperty.DECLINE_REWARD, false);
    //
    //        final var pendingChanges = new EntityChangeSet<AccountID, HederaAccount, AccountProperty>();
    //        pendingChanges.include(partyId, party, stakingFundChanges);
    //        pendingChanges.include(stakingFundId, stakingFund, new HashMap<>(stakingFundChanges));
    //        pendingChanges.include(counterpartyId, counterparty, map);
    //
    //        subject = new StakingAccountsCommitInterceptor(
    //                sideEffectsTracker,
    //                () -> networkCtx,
    //                dynamicProperties,
    //                rewardCalculator,
    //                new StakeChangeManager(
    //                        stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts))),
    //                stakePeriodManager,
    //                stakeInfoManager,
    //                accountNumbers,
    //                txnCtx,
    //                usageTracking);
    //
    //        subject.getRewardsEarned()[0] = -1;
    //        subject.getRewardsEarned()[1] = -1;
    //        subject.setCurStakedId(partyId.getAccountNum());
    //        subject.setNewStakedId(partyId.getAccountNum());
    //        assertEquals(3, pendingChanges.size());
    //        final var stakedToMeUpdates = subject.getStakedToMeUpdates();
    //        stakedToMeUpdates[0] = counterpartyBalance + 2 * HBARS_TO_TINYBARS;
    //        stakedToMeUpdates[1] = counterpartyBalance + 2 * HBARS_TO_TINYBARS;
    //        stakedToMeUpdates[2] = -1L;
    //        subject.updateStakedToMeSideEffects(
    //                counterparty, StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, pendingChanges.changes(0),
    // pendingChanges);
    //
    //        assertEquals(2 * HBARS_TO_TINYBARS, stakedToMeUpdates[0]);
    //        assertEquals(counterpartyBalance + 2 * HBARS_TO_TINYBARS, stakedToMeUpdates[1]);
    //    }
    //
    //        @Test
    //        void rewardAccountStakePeriodStartAlwaysReset() {
    //            given(dynamicProperties.isStakingEnabled()).willReturn(true);
    //
    //            final var changes = buildChanges();
    //            final var rewardPayment = 1L;
    //            final var expectedFundingI = 2;
    //            counterparty.setStakePeriodStart(stakePeriodStart - 2);
    //
    //            given(networkCtx.areRewardsActivated()).willReturn(true);
    //            given(rewardCalculator.computePendingReward(counterparty)).willReturn(rewardPayment);
    //            given(rewardCalculator.applyReward(rewardPayment, counterparty, changes.changes(1)))
    //                    .willReturn(true);
    //            given(rewardCalculator.rewardsPaidInThisTxn()).willReturn(rewardPayment);
    //            given(stakePeriodManager.startUpdateFor(anyLong(), anyLong(), anyBoolean(), anyBoolean()))
    //                    .willReturn(NA);
    //            given(stakeChangeManager.findOrAdd(anyLong(), any())).willAnswer(invocation -> {
    //                changes.include(
    //                        stakingFundId,
    //                        MerkleAccountFactory.newAccount().balance(123).get(),
    //                        new HashMap<>());
    //                return expectedFundingI;
    //            });
    //            subject.getStakePeriodStartUpdates()[expectedFundingI] = 666L;
    //
    //            subject.preview(changes);
    //            assertEquals(NA, subject.getStakePeriodStartUpdates()[expectedFundingI]);
    //        }

    private void randomStakeAccountChanges() {
        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(100L)
                .stakedAccountId(treasuryId)
                .declineReward(true)
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void randomStakeNodeChanges() {
        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(100L)
                .stakedNodeId(0L)
                .declineReward(true)
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void noStakeChanges() {
        writableAccountStore.put(account.copyBuilder().key(kycKey).build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void addToState(Map<AccountID, Account> idsToAccounts) {
        final var readableBuilder = emptyReadableAccountStateBuilder().value(stakingRewardId, stakingRewardAccount);
        final var writableBuilder = emptyWritableAccountStateBuilder().value(stakingRewardId, stakingRewardAccount);
        for (var entry : idsToAccounts.entrySet()) {
            readableBuilder.value(entry.getKey(), entry.getValue());
            writableBuilder.value(entry.getKey(), entry.getValue());
        }
        readableAccounts = readableBuilder.build();
        writableAccounts = writableBuilder.build();

        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        writableAccountStore = new WritableAccountStore(writableStates);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void stakingNotActivated() {
        configuration = HederaTestConfigBuilder.create()
                .withValue("staking.isEnabled", false)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
    }

    public static AccountCustomizer newBuilder() {
        return new AccountCustomizer();
    }

    /**
     * A builder for {@link Account} instances.
     */
    private static final class AccountCustomizer {
        private Account accountOfInterest;
        private Long amount;
        private Long stakeAtStartOfLastRewardPeriod;
        private Boolean declineReward;
        private Boolean deleted;
        private Long stakePeriodStart;

        private AccountCustomizer() {}

        public Account build() {
            final var copy = accountOfInterest.copyBuilder();
            if (amount != null) {
                copy.tinybarBalance(amount);
            }
            if (stakeAtStartOfLastRewardPeriod != null) {
                copy.stakeAtStartOfLastRewardedPeriod(stakeAtStartOfLastRewardPeriod);
            }
            if (declineReward != null) {
                copy.declineReward(declineReward);
            }
            if (deleted != null) {
                copy.deleted(deleted);
            }
            if (stakePeriodStart != null) {
                copy.stakePeriodStart(stakePeriodStart);
            }
            return copy.build();
        }

        public AccountCustomizer withAccount(final Account accountOfInterest) {
            this.accountOfInterest = accountOfInterest;
            return this;
        }

        public AccountCustomizer withBalance(final Long amount) {
            this.amount = amount;
            return this;
        }

        public AccountCustomizer withStakeAtStartOfLastRewardPeriod(final Long stakeAtStartOfLastRewardPeriod) {
            this.stakeAtStartOfLastRewardPeriod = stakeAtStartOfLastRewardPeriod;
            return this;
        }

        public AccountCustomizer withDeclineReward(final Boolean declineReward) {
            this.declineReward = declineReward;
            return this;
        }

        public AccountCustomizer withDeleted(final Boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public AccountCustomizer withStakePeriodStart(final Long stakePeriodStart) {
            this.stakePeriodStart = stakePeriodStart;
            return this;
        }
    }
}
