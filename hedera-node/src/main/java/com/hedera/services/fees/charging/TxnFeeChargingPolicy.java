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

import java.util.function.Consumer;

import static com.hedera.services.fees.TxnFeeType.NETWORK;
import static com.hedera.services.fees.TxnFeeType.NODE;
import static com.hedera.services.fees.TxnFeeType.SERVICE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_FEE;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NETWORK_NODE_SERVICE_FEES;
import static com.hedera.services.fees.charging.ItemizableFeeCharging.NODE_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides the transaction fee-charging policy for the processing
 * logic. The policy offers three basic entry points:
 * <ol>
 *    <li>For a txn whose submitting node seemed to ignore due diligence
 *    (e.g. submitted a txn with an impermissible valid duration); and, </li>
 *    <li>For a txn that looks to have been submitted responsibly, but is
 *    a duplicate of a txn already submitted by a different node; and,</li>
 *    <li>For a txn that was submitted responsibly, and is believed unique.</li>
 * </ol>
 *
 * @author Michael Tinker
 */
public class TxnFeeChargingPolicy {
	private final Consumer<ItemizableFeeCharging> NO_DISCOUNT = c -> {};
	private final Consumer<ItemizableFeeCharging> DUPLICATE_TXN_DISCOUNT = c -> c.setFor(SERVICE, 0);

	/**
	 * Apply the fee charging policy to a txn that was submitted responsibly, and
	 * believed unique.
	 *
	 * @param charging the charging facility to use
	 * @param fee the fee to charge
	 * @return the outcome of applying the policy
	 */
	public ResponseCodeEnum apply(ItemizableFeeCharging charging, FeeObject fee) {
		return applyWithDiscount(charging, fee, NO_DISCOUNT);
	}

	/**
	 * Apply the fee charging policy to a txn that was submitted responsibly, but
	 * is a duplicate of a txn already submitted by a different node.
	 *
	 * @param charging the charging facility to use
	 * @param fee the fee to charge
	 * @return the outcome of applying the policy
	 */
	public ResponseCodeEnum applyForDuplicate(ItemizableFeeCharging charging, FeeObject fee) {
		return applyWithDiscount(charging, fee, DUPLICATE_TXN_DISCOUNT);
	}

	/**
	 * Apply the fee charging policy to a txn that looks to have been
	 * submitted without performing basic due diligence.
	 *
	 * @param charging the charging facility to use
	 * @param fee the fee to charge
	 * @return the outcome of applying the policy
	 */
	public ResponseCodeEnum applyForIgnoredDueDiligence(ItemizableFeeCharging charging, FeeObject fee) {
		charging.setFor(NETWORK, fee.getNetworkFee());
		charging.chargeSubmittingNodeUpTo(NETWORK_FEE);
		return OK;
	}

	private ResponseCodeEnum applyWithDiscount(
			ItemizableFeeCharging charging,
			FeeObject fee,
			Consumer<ItemizableFeeCharging> discount
	) {
		setStandardFees(charging, fee);

		if (!charging.isPayerWillingToCover(NETWORK_FEE)) {
			charging.chargeSubmittingNodeUpTo(NETWORK_FEE);
			return INSUFFICIENT_TX_FEE;
		} else if (!charging.canPayerAfford(NETWORK_FEE)) {
			charging.chargeSubmittingNodeUpTo(NETWORK_FEE);
			return INSUFFICIENT_PAYER_BALANCE;
		} else {
			return applyGivenNodeDueDiligence(charging, discount);
		}
	}

	private ResponseCodeEnum applyGivenNodeDueDiligence(
			ItemizableFeeCharging charging,
			Consumer<ItemizableFeeCharging> discount
	) {
		if (!charging.isPayerWillingToCover(NETWORK_NODE_SERVICE_FEES))	{
			penalizePayer(charging);
			return INSUFFICIENT_TX_FEE;
		} else if (!charging.canPayerAfford(NETWORK_NODE_SERVICE_FEES)) {
			penalizePayer(charging);
			return INSUFFICIENT_PAYER_BALANCE;
		} else {
			discount.accept(charging);
			charging.chargePayer(NETWORK_NODE_SERVICE_FEES);
			return OK;
		}
	}

	private void penalizePayer(ItemizableFeeCharging charging) {
		charging.chargePayer(NETWORK_FEE);
		charging.chargePayerUpTo(NODE_FEE);
	}

	private void setStandardFees(ItemizableFeeCharging charging, FeeObject fee) {
		charging.setFor(NODE, fee.getNodeFee());
		charging.setFor(NETWORK, fee.getNetworkFee());
		charging.setFor(SERVICE, fee.getServiceFee());
	}
}
