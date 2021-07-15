package com.hedera.services.grpc.marshalling;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.BalanceChange;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class FeeAssessor {
	private final HtsFeeAssessor htsFeeAssessor;
	private final HbarFeeAssessor hbarFeeAssessor;
	private final FractionalFeeAssessor fractionalFeeAssessor;
	private final GlobalDynamicProperties dynamicProperties;

	public FeeAssessor(
			HtsFeeAssessor htsFeeAssessor,
			HbarFeeAssessor hbarFeeAssessor,
			FractionalFeeAssessor fractionalFeeAssessor,
			GlobalDynamicProperties dynamicProperties
	) {
		this.htsFeeAssessor = htsFeeAssessor;
		this.hbarFeeAssessor = hbarFeeAssessor;
		this.dynamicProperties = dynamicProperties;
		this.fractionalFeeAssessor = fractionalFeeAssessor;
	}

	public ResponseCodeEnum assess(
			int level,
			BalanceChange change,
			CustomSchedulesManager customSchedulesManager,
			BalanceChangeManager balanceChangeManager
	) {
		if (level > dynamicProperties.maxCustomFeeDepth()) {
			return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
		}
		final var fees = customSchedulesManager.managedSchedulesFor(change.getToken().asEntityId());
		if (fees.isEmpty()) {
			return OK;
		}
		var numFractionalFees = 0;
		final var payer = change.getAccount();
		final var maxBalanceChanges = dynamicProperties.maxXferBalanceChanges();
		for (var fee : fees) {
			if (fee.getFeeType() == FIXED_FEE) {
				final var fixedSpec = fee.getFixedFeeSpec();
				if (fixedSpec.getTokenDenomination() == null) {
					hbarFeeAssessor.assess(payer, fee, balanceChangeManager);
				} else {
					htsFeeAssessor.assess(payer, fee, balanceChangeManager);
				}
				if (balanceChangeManager.changesSoFar() > maxBalanceChanges) {
					return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
				}
			} else {
				numFractionalFees++;
			}
		}
		if (numFractionalFees > 0) {
			fractionalFeeAssessor.assessAllFractional(change, fees, balanceChangeManager);
		}
		return (balanceChangeManager.changesSoFar() > maxBalanceChanges)
				? CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS : OK;
	}
}
