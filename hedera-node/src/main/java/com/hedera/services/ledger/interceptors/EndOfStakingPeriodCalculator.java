package com.hedera.services.ledger.interceptors;

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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;

@Singleton
public class EndOfStakingPeriodCalculator {
	public static final String END_OF_STAKING_PERIOD_CALCULATIONS_MEMO = "End of Staking Period Calculation record";

	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier;
	private final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final RecordsHistorian recordsHistorian;
	private final EntityCreator creator;
	private final PropertySource properties;

	@Inject
	public EndOfStakingPeriodCalculator(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier,
			final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier,
			final SyntheticTxnFactory syntheticTxnFactory,
			final RecordsHistorian recordsHistorian,
			final EntityCreator creator,
			@CompositeProps PropertySource properties
	) {
		this.accounts = accounts;
		this.stakingInfoSupplier = stakingInfoSupplier;
		this.merkleNetworkContextSupplier = merkleNetworkContextSupplier;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.recordsHistorian = recordsHistorian;
		this.creator = creator;
		this.properties = properties;
	}

	public void updateNodes(final Instant consensusTime) {
		final var stakingInfo = stakingInfoSupplier.get();
		final var merkleNetworkContext = merkleNetworkContextSupplier.get();

		// skip end of staking period calculations if the rewards are not yet activated.
		if (!merkleNetworkContext.areRewardsActivated()) {
			return;
		}

		final var rewardRate = Math.min(stakingRewardsAccountBalance(),
				properties.getLongProperty("staking.rewardRate"));

		long updatedTotalStakedRewardStart = 0L;
		long updatedTotalStakedStart = 0L;
		List<NodeStake> nodeStakingInfos = new ArrayList<>();

		for (final var nodeNum : stakingInfo.keySet()) {
			final var merkleStakingInfo = stakingInfo.getForModify(nodeNum);
			merkleStakingInfo.updateRewardSumHistory(rewardRate, merkleNetworkContext.getTotalStakedRewardStart());

			final var totalStake = merkleStakingInfo.getStakeToReward() + merkleStakingInfo.getStakeToNotReward();
			final var minStake = merkleStakingInfo.getMinStake();
			final var maxStake = merkleStakingInfo.getMaxStake();

			if (totalStake > maxStake) {
				merkleStakingInfo.setStake(maxStake);
			} else if (totalStake < minStake) {
				merkleStakingInfo.setStake(0);
			} else {
				merkleStakingInfo.setStake(totalStake);
			}

			final var stakedRewardUsed = Math.min(merkleStakingInfo.getStakeToReward(), merkleStakingInfo.getStake());
			updatedTotalStakedRewardStart += stakedRewardUsed;
			updatedTotalStakedStart += merkleStakingInfo.getStake();

			nodeStakingInfos.add(NodeStake.newBuilder()
					.setStake(merkleStakingInfo.getStake())
					.setStakeRewarded(merkleStakingInfo.getStakeToReward())
					.build());
		}
		merkleNetworkContext.setTotalStakedRewardStart(updatedTotalStakedRewardStart);
		merkleNetworkContext.setTotalStakedStart(updatedTotalStakedStart);

		// create a synthetic txn with this computed data
		final var syntheticNodeStakeUpdateTxn =
				syntheticTxnFactory.nodeStakeUpdate(
						getMidnightTime(consensusTime),
						rewardRate,
						nodeStakingInfos);

		recordsHistorian.trackFollowingChildRecord(
				DEFAULT_SOURCE_ID, syntheticNodeStakeUpdateTxn,
				creator.createSuccessfulSyntheticRecord(
						NO_CUSTOM_FEES,
						new SideEffectsTracker(),
						END_OF_STAKING_PERIOD_CALCULATIONS_MEMO));
	}

	private Timestamp getMidnightTime(Instant consensusTime) {
		final var secsInDay = 86400L;
		final var originalSecs = consensusTime.getEpochSecond();
		final long midNightSecs = (originalSecs / secsInDay) * secsInDay;

		return Timestamp.newBuilder()
				.setSeconds(midNightSecs)
				.build();
	}
	private long stakingRewardsAccountBalance() {
		return accounts.get().get(EntityNum.fromInt(800)).getBalance();
	}
}
