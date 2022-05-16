package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.interceptors.StakeAdjustment;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;

public class RewardCalculator {
	private static final EntityNum stakingFundAccount = EntityNum.fromLong(800L);
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleNetworkContext> networkContext;
	private final TransactionContext txnContext;
	private final EntityCreator creator;
	private final SideEffectsTracker sideEffectsFactory;
	private final RecordsHistorian recordsHistorian;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;

	public static final ZoneId zoneUTC = ZoneId.of("UTC");
	private static final String MEMO = "Staking reward transfer";
	private static final Logger log = LogManager.getLogger(RewardCalculator.class);

	@Inject
	public RewardCalculator(final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleNetworkContext> networkContext,
			final TransactionContext txnContext,
			final SideEffectsTracker sideEffectsFactory,
			final EntityCreator creator,
			final RecordsHistorian recordsHistorian,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.accounts = accounts;
		this.networkContext = networkContext;
		this.txnContext = txnContext;
		this.sideEffectsFactory = sideEffectsFactory;
		this.creator = creator;
		this.recordsHistorian = recordsHistorian;
		this.stakingInfo = stakingInfo;
	}

	// all the adjustments here will have accounts that are staked to node
	final void computeAndApply(List<StakeAdjustment> adjustments) {
		if (!networkContext.get().areRewardsActivated()) {
			return;
		}
		computeAndApplyRewards(adjustments);
	}

	final long computeAndApplyRewards(final List<StakeAdjustment> adjustments) {
		long todayNumber = LocalDate.now(zoneUTC).toEpochDay();
		for (var adjustment : adjustments) {
			final var account = accounts.get().getForModify(adjustment.account());
			final var stakePeriodStart = account.getStakePeriodStart();
			if (!noRewardToBeEarned(stakePeriodStart, todayNumber) && stakePeriodStart > -1) {
				if (stakePeriodStart < todayNumber - 365) {
					account.setStakePeriodStart(todayNumber - 365);
				}
				if (stakePeriodStart < todayNumber - 1) {
					final long reward = computeReward(account, account.getStakedId(), todayNumber);
					transferReward(account, reward);
					account.setStakePeriodStart(todayNumber - 1);
					return reward;
				}
			}
		}
		return 0;
	}

	private long computeReward(final MerkleAccount account, final long stakedNode, final long todayNumber) {
		final var stakedNodeAccount = stakingInfo.get().get(stakedNode);
		final var rewardSumHistory = stakedNodeAccount.getRewardSumHistory();
		final var stakePeriodStart = account.getStakePeriodStart();
		// stakedNode.rewardSumHistory[0] is the reward for all days up to and including the full day todayNumber - 1,
		// since today is not finished yet.
		return account.isDeclinedReward() ? 0 :
				account.getBalance() * (rewardSumHistory[0] - rewardSumHistory[(int) (todayNumber - 1 - (stakePeriodStart - 1))]);
	}

	private void transferReward(final MerkleAccount account, final long reward) {
		final var synthBody = synthStakingFundTransfer(reward, account);
		final var synthRecord = creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, sideEffectsFactory, MEMO);
		recordsHistorian.trackPrecedingChildRecord(DEFAULT_SOURCE_ID, synthBody, synthRecord);
		log.info("Transferred reward for staking from 0.0.800 to 0.0.{}", stakingFundAccount);
	}

	private TransactionBody.Builder synthStakingFundTransfer(final long reward, final MerkleAccount account) {
		final var transferList = TransferList.newBuilder()
				.addAccountAmounts(
						AccountAmount.newBuilder()
								.setAccountID(stakingFundAccount.toGrpcAccountId())
								.setAmount(-reward)
								.build()
				)
				.addAccountAmounts(AccountAmount.newBuilder()
						.setAccountID(EntityNum.fromLong(account.state().number()).toGrpcAccountId())
						.setAmount(-reward)
						.build()
				)
				.build();
		final var txnBody = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(transferList)
				.build();
		return TransactionBody.newBuilder().setCryptoTransfer(txnBody);
	}

	private boolean noRewardToBeEarned(final long stakePeriodStart, final long todayNumber) {
		// if stakePeriodStart = -1 then it is not staked or staked to an account
		// If it equals todayNumber, that means the staking changed today (later than the start of today),
		// so it had no effect on consensus weights today, and should never be rewarded for helping consensus
		// throughout today.  If it equals todayNumber-1, that means it either started yesterday or has already been
		// rewarded for yesterday. Either way, it might be rewarded for today after today ends, but shouldn't yet be
		// rewarded for today, because today hasn't finished yet.
		return stakePeriodStart == -1 || (stakePeriodStart == todayNumber - 365) || (stakePeriodStart == todayNumber - 1);
	}
}
