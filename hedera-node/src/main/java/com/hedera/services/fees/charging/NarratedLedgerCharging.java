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
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.fcmap.FCMap;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Implements the {@link NarratedCharging} contract using a injected {@link HederaLedger}
 * to charge the requested fees.
 */
public class NarratedLedgerCharging implements NarratedCharging {
	private static final long UNKNOWN_ACCOUNT_BALANCE = -1L;

	private final NodeInfo nodeInfo;
	private final HederaLedger ledger;
	private final FeeExemptions feeExemptions;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	private long effPayerStartingBalance = UNKNOWN_ACCOUNT_BALANCE;
	private long nodeFee, networkFee, serviceFee;
	private long totalOfferedFee;
	private long totalCharged;
	private boolean payerExempt;
	private AccountID grpcPayerId;
	private MerkleEntityId nodeId;
	private MerkleEntityId payerId;

	public NarratedLedgerCharging(
			NodeInfo nodeInfo,
			HederaLedger ledger,
			FeeExemptions feeExemptions,
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.ledger = ledger;
		this.accounts = accounts;
		this.nodeInfo = nodeInfo;
		this.feeExemptions = feeExemptions;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public long totalFeesChargedToPayer() {
		return totalCharged;
	}

	@Override
	public void resetForTxn(TxnAccessor accessor, long submittingNodeId) {
		this.grpcPayerId = accessor.getPayer();
		this.payerId = MerkleEntityId.fromAccountId(grpcPayerId);
		this.totalOfferedFee = accessor.getOfferedFee();

		nodeId = nodeInfo.accountKeyOf(submittingNodeId);
		payerExempt = feeExemptions.hasExemptPayer(accessor);
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
		ledger.adjustBalance(nodeId.toAccountId(), +nodeFee);
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +(networkFee + serviceFee));
		totalCharged = nodeFee + networkFee + serviceFee;
		ledger.adjustBalance(payerId.toAccountId(), -totalCharged);
	}

	@Override
	public void chargePayerServiceFee() {
		if (payerExempt) {
			return;
		}
		ledger.adjustBalance(dynamicProperties.fundingAccount(), +serviceFee);
		totalCharged = serviceFee;
		ledger.adjustBalance(payerId.toAccountId(), -totalCharged);
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
