package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

@ExtendWith(MockitoExtension.class)
class EndOfStakingPeriodCalculatorTest {
	@Mock
	MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;
	@Mock
	MerkleNetworkContext merkleNetworkContext;
	@Mock
	SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	RecordsHistorian recordsHistorian;
	@Mock
	PropertySource properties;

	private EndOfStakingPeriodCalculator subject;

	@BeforeEach
	void setup() {
		subject = new EndOfStakingPeriodCalculator(
				() -> accounts,
				() -> stakingInfos,
				() -> merkleNetworkContext,
				syntheticTxnFactory,
				recordsHistorian,
				properties
		);
	}

	@Test
	void calculatesNewTotalStakesAsExpected() {
		final var consensusTime = Instant.now();
		final var historyMax = 2;
		final var balance_800 = 100_000_000_000L;
		final var account_800 = mock(MerkleAccount.class);
		final var nodeNum1 = EntityNum.fromInt(0);
		final var nodeNum2 = EntityNum.fromInt(1);
		final var stakingInfo1 = mock(MerkleStakingInfo.class);
		final var stakingInfo2 = mock(MerkleStakingInfo.class);
		final var nodeStakingInfos = List.of(
				NodeStake.newBuilder()
						.setStake(750L)
						.setStakeRewarded(300L)
						.build(),
				NodeStake.newBuilder()
						.setStake(2000L)
						.setStakeRewarded(700L)
						.build()
		);

		given(merkleNetworkContext.areRewardsActivated()).willReturn(true);
		given(properties.getIntProperty("staking.rewardHistory.numStoredPeriods")).willReturn(historyMax);
		given(properties.getDoubleProperty("staking.rewardRate")).willReturn(10_000_000_000.0);
		given(accounts.get(EntityNum.fromInt(800))).willReturn(account_800);
		given(account_800.getBalance()).willReturn(balance_800);
		given(stakingInfos.keySet()).willReturn(Set.of(nodeNum1, nodeNum2));
		given(stakingInfos.getForModify(nodeNum1)).willReturn(stakingInfo1);
		given(stakingInfos.getForModify(nodeNum2)).willReturn(stakingInfo2);
		given(stakingInfo1.getStakeToNotReward()).willReturn(300L);
		given(stakingInfo1.getStakeToReward()).willReturn(700L);
		given(stakingInfo1.getMinStake()).willReturn(100L);
		given(stakingInfo1.getMaxStake()).willReturn(800L);
		given(stakingInfo1.getStake()).willReturn(2_000L);
		given(stakingInfo2.getStakeToNotReward()).willReturn(200L);
		given(stakingInfo2.getStakeToReward()).willReturn(300L);
		given(stakingInfo2.getMinStake()).willReturn(100L);
		given(stakingInfo2.getMaxStake()).willReturn(800L);
		given(stakingInfo2.getStake()).willReturn(750L);
		given(merkleNetworkContext.getTotalStakedRewardStart()).willReturn(10_000L);

		subject.updateNodes(consensusTime);

		verify(merkleNetworkContext).setTotalStakedRewardStart(1000L);
		verify(merkleNetworkContext).setTotalStakedStart(2750L);
		verify(stakingInfo1).setStake(800L);
		verify(stakingInfo2).setStake(500L);
	}
}
