package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;

public class StakePeriodManager {
	private final TransactionContext txnCtx;
	private final Supplier<MerkleNetworkContext> networkCtx;

	public static final ZoneId zoneUTC = ZoneId.of("UTC");

	@Inject
	public StakePeriodManager(final TransactionContext txnCtx,
			final Supplier<MerkleNetworkContext> networkCtx) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
	}

	public long currentStakePeriod() {
		return LocalDate.ofInstant(txnCtx.consensusTime(), zoneUTC).toEpochDay();
	}

	public long latestRewardableStakePeriodStart() {
		return networkCtx.get().areRewardsActivated() ? currentStakePeriod() - 1 : Long.MIN_VALUE;
	}
}
