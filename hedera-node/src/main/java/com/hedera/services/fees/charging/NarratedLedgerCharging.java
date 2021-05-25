package com.hedera.services.fees.charging;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.fcmap.FCMap;

import java.util.Optional;
import java.util.function.Supplier;

public class NarratedLedgerCharging implements NarratedCharging {
	private static final long UNKNOWN_ACCOUNT_BALANCE = -1L;

	private final NodeInfo nodeInfo;
	private final HederaLedger ledger;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	private long effPayerStartingBalance = UNKNOWN_ACCOUNT_BALANCE;

	private long nodeFee, networkFee, serviceFee;
	private long totalOfferedFee;
	private long totalCharged;
	private MerkleEntityId nodeId;
	private MerkleEntityId payerId;

	public NarratedLedgerCharging(
			NodeInfo nodeInfo,
			HederaLedger ledger,
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.nodeInfo = nodeInfo;
		this.ledger = ledger;
		this.accounts = accounts;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public long totalFeesChargedToPayer() {
		return totalCharged;
	}

	@Override
	public void resetForTxn(MerkleEntityId payerId, long submittingNodeId, long totalOfferedFee) {
		this.payerId = payerId;
		this.totalOfferedFee = totalOfferedFee;

		nodeId = nodeInfo.accountKeyOf(submittingNodeId);
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
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		return effPayerStartingBalance >= (nodeFee + networkFee + serviceFee);
	}

	@Override
	public boolean canPayerAffordNetworkFee() {
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		return effPayerStartingBalance >= networkFee;
	}

	@Override
	public boolean canPayerAffordServiceFee() {
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		return effPayerStartingBalance >= serviceFee;
	}

	@Override
	public boolean isPayerWillingToCoverAllFees() {
		return totalOfferedFee >= (nodeFee + networkFee + serviceFee);
	}

	@Override
	public boolean isPayerWillingToCoverNetworkFee() {
		return totalOfferedFee >= networkFee;
	}

	@Override
	public boolean isPayerWillingToCoverServiceFee() {
		return totalOfferedFee >= serviceFee;
	}

	@Override
	public void chargePayerAllFees() {
		ledger.adjustBalance(nodeId.toAccountId(), +nodeFee);
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +(networkFee + serviceFee));
		totalCharged = nodeFee + networkFee + serviceFee;
		ledger.adjustBalance(payerId.toAccountId(), -totalCharged);
	}

	@Override
	public void chargePayerServiceFee() {
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +serviceFee);
		totalCharged = serviceFee;
		ledger.adjustBalance(payerId.toAccountId(), -totalCharged);
	}

	@Override
	public void chargePayerNetworkAndUpToNodeFee() {
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(payerId);
		}
		long chargeableNodeFee = Math.min(nodeFee, effPayerStartingBalance - networkFee);
		ledger.adjustBalance(nodeId.toAccountId(), +chargeableNodeFee);
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +networkFee);
		totalCharged = networkFee + chargeableNodeFee;
		ledger.adjustBalance(payerId.toAccountId(), -totalCharged);
	}

	@Override
	public void chargeSubmittingNodeUpToNetworkFee() {
		if (effPayerStartingBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initEffPayerBalance(nodeId);
		}
		long chargeableNetworkFee = Math.min(networkFee, effPayerStartingBalance);
		ledger.adjustBalance(nodeId.toAccountId(), -chargeableNetworkFee);
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +chargeableNetworkFee);
	}

	private void initEffPayerBalance(MerkleEntityId effPayerId) {
		final var payerAccount = accounts.get().get(effPayerId);
		if (payerAccount == null) {
			throw new IllegalStateException("Invariant failure, effective payer account "
					+ Optional.ofNullable(effPayerId).map(MerkleEntityId::toAbbrevString).orElse("null")
					+ " is missing!");
		}
		effPayerStartingBalance = payerAccount.getBalance();
	}
}
