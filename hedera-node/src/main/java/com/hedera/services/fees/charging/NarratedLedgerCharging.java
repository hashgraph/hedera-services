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

	private long startingPayerBalance = UNKNOWN_ACCOUNT_BALANCE, startingNodeBalance = UNKNOWN_ACCOUNT_BALANCE;

	private long nodeFee, networkFee, serviceFee;
	private long totalOfferedFee;
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
	public void resetForTxn(MerkleEntityId payerId, long submittingNodeId, long totalOfferedFee) {
		this.payerId = payerId;
		this.totalOfferedFee = totalOfferedFee;

//		nodeId = nodeInfo.accountOf(submittingNodeId);

		startingNodeBalance = startingPayerBalance = UNKNOWN_ACCOUNT_BALANCE;
	}

	@Override
	public void setFees(FeeObject fees) {
		this.nodeFee = fees.getNodeFee();
		this.networkFee = fees.getNetworkFee();
		this.serviceFee = fees.getServiceFee();
	}

	@Override
	public boolean canPayerAffordAllFees() {
		if (startingPayerBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initPayerBalance();
		}
		return startingPayerBalance >= (nodeFee + networkFee + serviceFee);
	}

	@Override
	public boolean canPayerAffordNetworkFee() {
		if (startingPayerBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initPayerBalance();
		}
		return startingPayerBalance >= networkFee;
	}

	@Override
	public boolean canPayerAffordServiceFee() {
		if (startingPayerBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initPayerBalance();
		}
		return startingPayerBalance >= serviceFee;
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
		ledger.adjustBalance(payerId.toAccountId(), -(nodeFee + networkFee + serviceFee));
		ledger.adjustBalance(nodeId.toAccountId(), +nodeFee);
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +(networkFee + serviceFee));
	}

	@Override
	public void chargePayerServiceFee() {
		ledger.adjustBalance(payerId.toAccountId(), -serviceFee);
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +serviceFee);
	}

	@Override
	public void chargePayerNetworkAndUpToNodeFee() {
		if (startingPayerBalance == UNKNOWN_ACCOUNT_BALANCE) {
			initPayerBalance();
		}
		long chargeableNodeFee = Math.min(nodeFee, startingPayerBalance - networkFee);
		ledger.adjustBalance(payerId.toAccountId(), -(networkFee + chargeableNodeFee));
		ledger.adjustBalance(nodeId.toAccountId(), +chargeableNodeFee);
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +networkFee);
	}

	@Override
	public void chargeSubmittingNodeUpToNetworkFee() {

	}

	private void initPayerBalance() {
		final var payerAccount = accounts.get().get(payerId);
		if (payerAccount == null) {
			throw new IllegalStateException("Invariant failure, payer account "
					+ Optional.ofNullable(payerId).map(MerkleEntityId::toAbbrevString).orElse("null")
					+ " is missing!");
		}
		startingPayerBalance = payerAccount.getBalance();
	}
}
