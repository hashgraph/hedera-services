package com.hedera.services.fees.charging;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityIdUtils.asAccount;

/**
 * Implements the {@link NarratedCharging} contract using a injected {@link HederaLedger}
 * to charge the requested fees.
 */
@Singleton
public class NarratedLedgerCharging implements NarratedCharging {
	private static final long UNKNOWN_ACCOUNT_BALANCE = -1L;

	private HederaLedger ledger;

	private final NodeInfo nodeInfo;
	private final FeeExemptions feeExemptions;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

	private long effPayerStartingBalance = UNKNOWN_ACCOUNT_BALANCE;
	private long nodeFee;
	private long networkFee;
	private long serviceFee;
	private long totalOfferedFee;
	private long totalCharged;
	private boolean payerExempt;
	private boolean serviceFeeCharged;
	private AccountID grpcNodeId;
	private AccountID grpcPayerId;
	private EntityNum nodeId;
	private EntityNum payerId;

	public static final AccountID STAKING_REWARD_FUND_ACCOUNT = asAccount(EntityId.fromIdentityCode(800));
	private static final AccountID NODE_REWARD_FUND_ACCOUNT = asAccount(EntityId.fromIdentityCode(801));

	@Inject
	public NarratedLedgerCharging(
			NodeInfo nodeInfo,
			FeeExemptions feeExemptions,
			GlobalDynamicProperties dynamicProperties,
			Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts
	) {
		this.accounts = accounts;
		this.nodeInfo = nodeInfo;
		this.feeExemptions = feeExemptions;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void setLedger(HederaLedger ledger) {
		this.ledger = ledger;
	}

	@Override
	public long totalFeesChargedToPayer() {
		return totalCharged;
	}

	@Override
	public void resetForTxn(TxnAccessor accessor, long submittingNodeId) {
		this.grpcPayerId = accessor.getPayer();
		this.payerId = EntityNum.fromAccountId(grpcPayerId);
		this.totalOfferedFee = accessor.getOfferedFee();

		nodeId = nodeInfo.accountKeyOf(submittingNodeId);
		grpcNodeId = nodeInfo.accountOf(submittingNodeId);
		payerExempt = feeExemptions.hasExemptPayer(accessor);
		serviceFeeCharged = false;
		totalCharged = 0L;
		effPayerStartingBalance = UNKNOWN_ACCOUNT_BALANCE;
	}

	@Override
	public void setFees(FeeObject fees) {
		this.nodeFee = fees.getNodeFee();
		this.networkFee = fees.getNetworkFee();
		this.serviceFee = fees.getServiceFee();
	}

	@Override
	public boolean canPayerAffordAllFees() {
		if (payerExempt) {
			return true;
		}
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		return effPayerStartingBalance >= (nodeFee + networkFee + serviceFee);
	}

	@Override
	public boolean canPayerAffordNetworkFee() {
		if (payerExempt) {
			return true;
		}
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		return effPayerStartingBalance >= networkFee;
	}

	@Override
	public boolean canPayerAffordServiceFee() {
		if (payerExempt) {
			return true;
		}
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		return effPayerStartingBalance >= serviceFee;
	}

	@Override
	public boolean isPayerWillingToCoverAllFees() {
		return payerExempt || totalOfferedFee >= (nodeFee + networkFee + serviceFee);
	}

	@Override
	public boolean isPayerWillingToCoverNetworkFee() {
		return payerExempt || totalOfferedFee >= networkFee;
	}

	@Override
	public boolean isPayerWillingToCoverServiceFee() {
		return payerExempt || totalOfferedFee >= serviceFee;
	}

	@Override
	public void chargePayerAllFees() {
		if (payerExempt) {
			return;
		}
		ledger.adjustBalance(grpcNodeId, +nodeFee);
		adjustFundingAndStakingBalances(+(networkFee + serviceFee));
		totalCharged = nodeFee + networkFee + serviceFee;
		ledger.adjustBalance(grpcPayerId, -totalCharged);
		serviceFeeCharged = true;
	}

	@Override
	public void chargePayerServiceFee() {
		if (payerExempt) {
			return;
		}
		adjustFundingAndStakingBalances(+serviceFee);
		totalCharged = serviceFee;
		ledger.adjustBalance(grpcPayerId, -totalCharged);
		serviceFeeCharged = true;
	}

	@Override
	public void refundPayerServiceFee() {
		if (payerExempt) {
			return;
		}
		if (!serviceFeeCharged) {
			throw new IllegalStateException("NarratedCharging asked to refund service fee to un-charged payer");
		}
		adjustFundingAndStakingBalances(-serviceFee);
		ledger.adjustBalance(grpcPayerId, +serviceFee);
		totalCharged -= serviceFee;
	}

	@Override
	public void chargePayerNetworkAndUpToNodeFee() {
		if (payerExempt) {
			return;
		}
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		long chargeableNodeFee = Math.min(nodeFee, effPayerStartingBalance - networkFee);
		ledger.adjustBalance(grpcNodeId, +chargeableNodeFee);
		adjustFundingAndStakingBalances(+networkFee);
		totalCharged = networkFee + chargeableNodeFee;
		ledger.adjustBalance(grpcPayerId, -totalCharged);
	}

	@Override
	public void chargeSubmittingNodeUpToNetworkFee() {
		initEffPayerBalance(nodeId);
		long chargeableNetworkFee = Math.min(networkFee, effPayerStartingBalance);
		ledger.adjustBalance(grpcNodeId, -chargeableNetworkFee);
		adjustFundingAndStakingBalances(+chargeableNetworkFee);
	}

	private void initEffPayerBalance(EntityNum effPayerId) {
		final var payerAccount = accounts.get().get(effPayerId);
		if (payerAccount == null) {
			throw new IllegalStateException("Invariant failure, effective payer account "
					+ Optional.ofNullable(effPayerId).map(EntityNum::toIdString).orElse("null")
					+ " is missing!");
		}
		effPayerStartingBalance = payerAccount.getBalance();
	}

	private void adjustFundingAndStakingBalances(final long totalFee) {
		final var stakingRewardFee = calculateStakingRewardFee(totalFee);
		final var nodeRewardFee = calculateNodeRewardFee(totalFee);
		final var fundingAccountFee = totalFee - stakingRewardFee - nodeRewardFee;

		ledger.adjustBalance(dynamicProperties.fundingAccount(), fundingAccountFee);
		ledger.adjustBalance(STAKING_REWARD_FUND_ACCOUNT, stakingRewardFee);
		ledger.adjustBalance(NODE_REWARD_FUND_ACCOUNT, nodeRewardFee);
	}

	private long calculateStakingRewardFee(long totalFee) {
		return (dynamicProperties.getStakingRewardPercent() * totalFee) / 100;
	}

	private long calculateNodeRewardFee(long totalFee) {
		return (dynamicProperties.getNodeRewardPercent() * totalFee) / 100;
	}
}
