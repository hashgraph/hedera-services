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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the transaction fee-charging policy for the processing
 * logic. The policy offers four basic entry points:
 * <ol>
 *    <li>For a txn whose submitting node seemed to ignore due diligence
 *    (e.g. submitted a txn with an impermissible valid duration); and, </li>
 *    <li>For a txn that looks to have been submitted responsibly, but is
 *    a duplicate of a txn already submitted by a different node; and,</li>
 *    <li>For a triggered txn; and,</li>
 *    <li>For a txn that was submitted responsibly, and is believed unique.</li>
 * </ol>
 *
 * @author Michael Tinker
 */
public class TxnFeeChargingPolicy {
	private final StagedCharging stagedCharging;

	public TxnFeeChargingPolicy() {
		stagedCharging = null;
	}

	public TxnFeeChargingPolicy(StagedCharging stagedCharging) {
		this.stagedCharging = stagedCharging;
	}

	/**
	 * Apply the fee charging policy to a txn that was submitted responsibly, and
	 * believed unique.
	 *
	 * @param charging the charging facility to use
	 * @param fees the fee to charge
	 * @return the outcome of applying the policy
	 */
	public ResponseCodeEnum apply(ItemizableFeeCharging charging, FeeObject fees) {
		return chargePendingSolvency(fees);
	}

	/**
	 * Apply the fee charging policy to a txn that was submitted responsibly, but
	 * is a duplicate of a txn already submitted by a different node.
	 *
	 * @param charging the charging facility to use
	 * @param fees the fee to charge
	 * @return the outcome of applying the policy
	 */
	public ResponseCodeEnum applyForDuplicate(ItemizableFeeCharging charging, FeeObject fees) {
		final var feesForDuplicate = new FeeObject(fees.getNodeFee(), fees.getNetworkFee(), 0L);

		return chargePendingSolvency(feesForDuplicate);
	}

	/**
	 * Apply the fee charging policy to a txn that was submitted responsibly, but
	 * is a triggered txn rather than a parent txn requiring node precheck work.
	 *
	 * @param charging the charging facility to use
	 * @param fees the fee to charge
	 * @return the outcome of applying the policy
	 */
	public ResponseCodeEnum applyForTriggered(ItemizableFeeCharging charging, FeeObject fees) {
		stagedCharging.setFees(fees);

		if (!stagedCharging.isPayerWillingToCoverServiceFee()) {
			return INSUFFICIENT_TX_FEE;
		} else if (!stagedCharging.canPayerAffordServiceFee()) {
			return INSUFFICIENT_PAYER_BALANCE;
		} else {
			stagedCharging.chargePayerServiceFee();
			return OK;
		}
	}

	/**
	 * Apply the fee charging policy to a txn that looks to have been
	 * submitted without performing basic due diligence.
	 *
	 * @param charging the charging facility to use
	 * @param fees the fee to charge
	 * @return the outcome of applying the policy
	 */
	public ResponseCodeEnum applyForIgnoredDueDiligence(ItemizableFeeCharging charging, FeeObject fees) {
		stagedCharging.setFees(fees);
		stagedCharging.chargeSubmittingNodeUpToNetworkFee();
		return OK;
	}

	private ResponseCodeEnum chargePendingSolvency(FeeObject fees) {
		stagedCharging.setFees(fees);

		if (!stagedCharging.isPayerWillingToCoverNetworkFee()) {
			stagedCharging.chargeSubmittingNodeUpToNetworkFee();
			return INSUFFICIENT_TX_FEE;
		} else if (!stagedCharging.canPayerAffordNetworkFee()) {
			stagedCharging.chargeSubmittingNodeUpToNetworkFee();
			return INSUFFICIENT_PAYER_BALANCE;
		} else {
			return chargeGivenNodeDueDiligence();
		}
	}

	private ResponseCodeEnum chargeGivenNodeDueDiligence() {
		if (!stagedCharging.isPayerWillingToCoverAllFees()) {
			stagedCharging.chargePayerNetworkAndUpToNodeFee();
			return INSUFFICIENT_TX_FEE;
		} else if (!stagedCharging.canPayerAffordAllFees()) {
			stagedCharging.chargePayerNetworkAndUpToNodeFee();
			return INSUFFICIENT_PAYER_BALANCE;
		} else {
			stagedCharging.chargePayerAllFees();
			return OK;
		}
	}
}
