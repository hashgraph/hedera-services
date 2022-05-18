package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
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

@Singleton
public class EndOfStakingPeriodCalculator {
	public static final String NODE_STAKING_UPDATE_MEMO = "node staking info update";

	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier;
	private final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final RecordsHistorian recordsHistorian;
	private final PropertySource properties;

	@Inject
	public EndOfStakingPeriodCalculator(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier,
			final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier,
			final SyntheticTxnFactory syntheticTxnFactory,
			final RecordsHistorian recordsHistorian,
			@CompositeProps PropertySource properties
	) {
		this.accounts = accounts;
		this.stakingInfoSupplier = stakingInfoSupplier;
		this.merkleNetworkContextSupplier = merkleNetworkContextSupplier;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.recordsHistorian = recordsHistorian;
		this.properties = properties;
	}

	public void updateNodes(final Instant consensusTime) {
		final var stakingInfo = stakingInfoSupplier.get();
		final var merkleNetworkContext = merkleNetworkContextSupplier.get();

		// skip end of staking period calculations if the rewards are not yet activated.
		if (!merkleNetworkContext.areRewardsActivated()) {
			return;
		}

		final var maxHistory = properties.getIntProperty("staking.rewardHistory.numStoredPeriods");
		final var rewardRate = Math.min(accounts.get().get(EntityNum.fromInt(800)).getBalance(),
				properties.getDoubleProperty("staking.rewardRate"));

		long updatedTotalStakedRewardStart = 0L;
		long updatedTotalStakedStart = 0L;
		List<NodeStake> nodeStakingInfos = new ArrayList<>();

		for (final var nodeNum : stakingInfo.keySet()) {
			final var merkleStakingInfo = stakingInfo.getForModify(nodeNum);
			final var rewardSumHistory = merkleStakingInfo.getRewardSumHistory();
			for (int i = maxHistory; i > 0; i--) {
				rewardSumHistory[i] = rewardSumHistory[i-1];
				// TODO : check if node was active.
				/*
					if this node was "active", then it should give rewards for this staking period
					if (node.numRoundsWithJudge / numRoundsInPeriod >= activeThreshold)
				 */
				rewardSumHistory[0] = merkleNetworkContext.getTotalStakedRewardStart()== 0 ? 0 :
						(long) (rewardRate * merkleStakingInfo.getStakeRewardStart()
										/ merkleNetworkContext.getTotalStakedRewardStart() / 100_000_000);
			}

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
						Timestamp.newBuilder()
								.setSeconds(consensusTime.getEpochSecond())
								.setNanos(consensusTime.getNano())
								.build(),
						nodeStakingInfos);

		recordsHistorian.trackFollowingChildRecord(
				DEFAULT_SOURCE_ID, syntheticNodeStakeUpdateTxn, ExpirableTxnRecord.newBuilder());
	}
}
