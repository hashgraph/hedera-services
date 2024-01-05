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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmoothedStakingTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

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

    private static final long V_040_MAX_STAKE_REWARDED = 650_000_000_000_000_000L;
    private static final long V_040_REWARD_BALANCE_THRESHOLD = 8_500_000_000_000_000L;
    private static final long MAX_REWARD_RATE_PER_HBAR = 6_849L;
    private static final long TINYBAR_PER_HBAR = 100_000_000L;

    @CsvSource({
        "7_100_000_000,73_000_000",
        "7_200_000_000,70_000_000",
        "7_300_000_000,65_000_000",
        "7_400_000_000,60_000_000",
        "7_600_000_000,55_000_000",
    })
    @ParameterizedTest
    void rewardsWithV40PropertiesTrackAsExpected(final long stakeRewardedHbars, final long unreserved800BalanceHbars) {
        given(dynamicProperties.stakingPerHbarRewardRate()).willReturn(MAX_REWARD_RATE_PER_HBAR);
        given(dynamicProperties.maxStakeRewarded()).willReturn(V_040_MAX_STAKE_REWARDED);
        given(dynamicProperties.stakingRewardBalanceThreshold()).willReturn(V_040_REWARD_BALANCE_THRESHOLD);
        given(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT)).willReturn(800L);

        final long stakeRewarded = stakeRewardedHbars * TINYBAR_PER_HBAR;
        final long unreserved800Balance = unreserved800BalanceHbars * TINYBAR_PER_HBAR;
        given(accounts.get(EntityNum.fromLong(800L))).willReturn(accountWith(unreserved800Balance));

        final var impliedBalanceRatio = subject.ratioOf(unreserved800Balance, V_040_REWARD_BALANCE_THRESHOLD);
        final var desiredTinybarsToPay =
                subject.rescaledPerHbarRewardRate(impliedBalanceRatio, stakeRewarded, MAX_REWARD_RATE_PER_HBAR);

        final var actualTinybarsToPay = subject.perHbarRewardRateForEndingPeriod(stakeRewarded);
        System.out.println("\nWith " + asBillions(stakeRewarded)
                + " stake rewarded, and "
                + asMillions(unreserved800Balance) + " unreserved 800 balance:");
        System.out.println("  - Exact HIP-782 calculation: "
                + desiredTinybarsToPay + " tinybars disbursed in period for a yearly reward rate of "
                + asInRangeRate(desiredTinybarsToPay, stakeRewarded));
        System.out.println("  - Actual calculation      : "
                + actualTinybarsToPay + " tinybars disbursed in period for a yearly reward rate of "
                + asInRangeRate(actualTinybarsToPay, stakeRewarded));
        Assertions.assertEquals(desiredTinybarsToPay, actualTinybarsToPay);
    }

    private String asInRangeRate(final long tinybarsDisbursed, final long stakeRewarded) {
        return String.format(
                "~%.5f%%", 100.0 * tinybarsDisbursed / (stakeRewarded / TINYBAR_PER_HBAR) * 365 / TINYBAR_PER_HBAR);
    }

    private String asBillions(final long value) {
        return String.format("%.1fB hbar", value / TINYBAR_PER_HBAR / 1_000_000_000.0);
    }

    private String asMillions(final long value) {
        return String.format("%.1fM hbar", value / TINYBAR_PER_HBAR / 1_000_000.0);
    }

    private MerkleAccount accountWith(final long balance) {
        final var account = new MerkleAccount();
        account.setBalanceUnchecked(balance);
        return account;
    }
}
