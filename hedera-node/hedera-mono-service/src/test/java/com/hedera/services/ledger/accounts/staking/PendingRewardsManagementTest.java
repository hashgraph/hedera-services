/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts.staking;

import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_RATE;
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingRewardsManagementTest {

    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EntityCreator creator;
    @Mock private PropertySource properties;
    @Mock private MerkleStakingInfo info;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private EndOfStakingPeriodCalculator subject;

    @BeforeEach
    void setUp() {
        subject =
                new EndOfStakingPeriodCalculator(
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        () -> stakingInfos,
                        () -> networkCtx,
                        syntheticTxnFactory,
                        recordsHistorian,
                        creator,
                        properties,
                        dynamicProperties);
    }

    @Test
    void pendingRewardsIsUpdatedBasedOnLastPeriodRewardRateAndStakeRewardStart() {
        given800Balance(1_000_000_000_000L);
        given(dynamicProperties.maxDailyStakeRewardThPerH()).willReturn(lastPeriodRewardRate);
        given(networkCtx.getTotalStakedRewardStart()).willReturn(totalStakedRewardStart);
        given(properties.getLongProperty(STAKING_REWARD_RATE)).willReturn(rewardRate);
        given(stakingInfos.keySet()).willReturn(Set.of(onlyNodeNum));
        given(stakingInfos.getForModify(onlyNodeNum)).willReturn(info);
        given(info.stakeRewardStartMinusUnclaimed())
                .willReturn(stakeRewardStart - unclaimedStakeRewardStart);
        given(dynamicProperties.requireMinStakeToReward()).willReturn(true);
        given(
                        info.updateRewardSumHistory(
                                rewardRate / (totalStakedRewardStart / HBARS_TO_TINYBARS),
                                lastPeriodRewardRate,
                                true))
                .willReturn(lastPeriodRewardRate);
        given(info.reviewElectionsAndRecomputeStakes()).willReturn(updatedStakeRewardStart);
        given(dynamicProperties.isStakingEnabled()).willReturn(true);

        subject.updateNodes(Instant.EPOCH.plusSeconds(123_456));

        verify(networkCtx)
                .increasePendingRewards(
                        (stakeRewardStart - unclaimedStakeRewardStart)
                                / 100_000_000
                                * lastPeriodRewardRate);
    }

    @Test
    void rewardRateIsZeroIfPendingRewardsExceed800Balance() {
        given800Balance(123);
        given(networkCtx.pendingRewards()).willReturn(124L);

        Assertions.assertEquals(0, subject.rewardRateForEndingPeriod());
    }

    private void given800Balance(final long balance) {
        given(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT)).willReturn(800L);
        given(accounts.get(EntityNum.fromLong(800)))
                .willReturn(MerkleAccountFactory.newAccount().balance(balance).get());
    }

    private static final long rewardRate = 100_000_000;
    private static final long stakeRewardStart = 666L * 100_000_000L;
    private static final long unclaimedStakeRewardStart = stakeRewardStart / 10;
    private static final long updatedStakeRewardStart = 777L * 100_000_000L;
    private static final long lastPeriodRewardRate = 100_000L;
    private static final long totalStakedRewardStart = 100_000_000_000L;
    private static final EntityNum onlyNodeNum = EntityNum.fromLong(123);
}
