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

package com.hedera.node.app.service.mono.ledger.accounts.staking;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.node.app.service.token.Units.HBARS_TO_TINYBARS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RewardRateCalculationTest {
    private static final long TYPICAL_REWARD_RATE = 273972602739726L;
    private static final long LOW_REWARD_RATE_THRESHOLD = 1L;
    private static final long TYPICAL_800_BALANCE = 69_000_000L * 100_000_000L;
    private static final long HIGH_REWARD_RATE_THRESHOLD = 200_000_000L * 100_000_000L;
    private static final long TYPICAL_STAKED_TO_REWARD = 5000000000000000000L / 6 * 5;
    private static final long TYPICAL_PER_HBAR_REWARD_RATE =
            TYPICAL_REWARD_RATE / (TYPICAL_STAKED_TO_REWARD / HBARS_TO_TINYBARS);

    @Mock
    private MerkleAccount stakingRewardAccount;

    @Mock
    private MerkleMap<EntityNum, MerkleAccount> accounts;

    @Mock
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;

    @Mock
    private MerkleNetworkContext merkleNetworkContext;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private EntityCreator creator;

    @Mock
    private PropertySource properties;

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    private EndOfStakingPeriodCalculator subject;

    @BeforeEach
    void setup() {
        subject = new EndOfStakingPeriodCalculator(
                () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts)),
                () -> MerkleMapLike.from(stakingInfos),
                () -> merkleNetworkContext,
                syntheticTxnFactory,
                recordsHistorian,
                creator,
                properties,
                dynamicProperties);
    }

    @Test
    void rewardRateIsUnaffectedWithinNormalParameters() {
        given(dynamicProperties.maxStakeRewarded()).willReturn(TYPICAL_STAKED_TO_REWARD + 1);
        givenRewardBalanceThreshold(LOW_REWARD_RATE_THRESHOLD);
        givenTypicalRewardRate();

        given800Balance(TYPICAL_800_BALANCE);

        final var actualRate = subject.perHbarRewardRateForEndingPeriod(TYPICAL_STAKED_TO_REWARD);

        assertEquals(TYPICAL_PER_HBAR_REWARD_RATE, actualRate);
    }

    @Test
    void withFiftyPercentThresholdBalanceRateIsThreeFourthsOfNominal() {
        given(dynamicProperties.maxStakeRewarded()).willReturn(TYPICAL_STAKED_TO_REWARD + 1);
        givenRewardBalanceThreshold(HIGH_REWARD_RATE_THRESHOLD);
        givenTypicalRewardRate();

        given800BalanceAndPending(HIGH_REWARD_RATE_THRESHOLD, HIGH_REWARD_RATE_THRESHOLD / 2);

        final var actualRate = subject.perHbarRewardRateForEndingPeriod(TYPICAL_STAKED_TO_REWARD);

        assertEquals(3 * TYPICAL_PER_HBAR_REWARD_RATE / 4, actualRate);
    }

    @Test
    void withTwiceMaxStakedForRewardRateIsOneHalfOfNominal() {
        given(dynamicProperties.maxStakeRewarded()).willReturn(TYPICAL_STAKED_TO_REWARD);
        givenRewardBalanceThreshold(LOW_REWARD_RATE_THRESHOLD);
        givenTypicalRewardRate();

        given800BalanceAndPending(HIGH_REWARD_RATE_THRESHOLD, HIGH_REWARD_RATE_THRESHOLD / 2);

        final var actualRate = subject.perHbarRewardRateForEndingPeriod(2 * TYPICAL_STAKED_TO_REWARD);

        assertEquals(TYPICAL_PER_HBAR_REWARD_RATE / 2, actualRate);
    }

    private void givenRewardBalanceThreshold(final long amount) {
        given(dynamicProperties.stakingRewardBalanceThreshold()).willReturn(amount);
    }

    private void givenTypicalRewardRate() {
        given(dynamicProperties.stakingPerHbarRewardRate()).willReturn(TYPICAL_PER_HBAR_REWARD_RATE);
    }

    private void given800Balance(final long amount) {
        given800BalanceAndPending(amount, 0L);
    }

    private void given800BalanceAndPending(final long amount, final long pending) {
        given(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT)).willReturn(800L);
        given(stakingRewardAccount.getBalance()).willReturn(amount);
        given(accounts.get(EntityNum.fromLong(800L))).willReturn(stakingRewardAccount);
        given(merkleNetworkContext.pendingRewards()).willReturn(pending);
    }
}
