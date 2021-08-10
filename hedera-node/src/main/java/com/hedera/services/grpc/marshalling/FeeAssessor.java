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

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.List;

import static com.hedera.services.grpc.marshalling.FeeAssessor.FixedFeeResult.NO_MORE_FEES;
import static com.hedera.services.grpc.marshalling.FeeAssessor.FixedFeeResult.FRACTIONAL_FEES_PENDING;
import static com.hedera.services.grpc.marshalling.FeeAssessor.FixedFeeResult.TOO_MANY_CHANGES_REQUIRED_FOR_FIXED_FEES;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class FeeAssessor {
	private final HtsFeeAssessor htsFeeAssessor;
	private final HbarFeeAssessor hbarFeeAssessor;
	private final FractionalFeeAssessor fractionalFeeAssessor;

	enum FixedFeeResult {
		NO_MORE_FEES,
		FRACTIONAL_FEES_PENDING,
		TOO_MANY_CHANGES_REQUIRED_FOR_FIXED_FEES
	}

	public FeeAssessor(
			HtsFeeAssessor htsFeeAssessor,
			HbarFeeAssessor hbarFeeAssessor,
			FractionalFeeAssessor fractionalFeeAssessor
	) {
		this.htsFeeAssessor = htsFeeAssessor;
		this.hbarFeeAssessor = hbarFeeAssessor;
		this.fractionalFeeAssessor = fractionalFeeAssessor;
	}

	public ResponseCodeEnum assess(
			BalanceChange change,
			CustomSchedulesManager customSchedulesManager,
			BalanceChangeManager balanceChangeManager,
			List<FcAssessedCustomFee> accumulator,
			ImpliedTransfersMeta.ValidationProps props
	) {
		if (balanceChangeManager.getLevelNo() > props.getMaxNestedCustomFees()) {
			return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
		}
		final var chargingToken = change.getToken();

		final var feeMeta = customSchedulesManager.managedSchedulesFor(chargingToken);
		final var payer = change.getAccount();
		final var fees = feeMeta.getCustomFees();
		/* Token treasuries are exempt from all custom fees */
		if (fees.isEmpty() || feeMeta.getTreasuryId().equals(payer)) {
			return OK;
		}

		final var maxBalanceChanges = props.getMaxXferBalanceChanges();

		FixedFeeResult result;
		result = processFixedCustomFees(
				chargingToken, fees, payer, balanceChangeManager, accumulator, maxBalanceChanges);
		if (result == TOO_MANY_CHANGES_REQUIRED_FOR_FIXED_FEES) {
			return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
		}

		if (result == FRACTIONAL_FEES_PENDING) {
			final var fractionalValidity =
					fractionalFeeAssessor.assessAllFractional(change, fees, balanceChangeManager, accumulator);
			if (fractionalValidity != OK) {
				return fractionalValidity;
			}
		}
		return (balanceChangeManager.numChangesSoFar() > maxBalanceChanges)
				? CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS : OK;
	}

	private FixedFeeResult processFixedCustomFees(
			Id chargingToken,
			List<FcCustomFee> fees,
			Id payer,
			BalanceChangeManager balanceChangeManager,
			List<FcAssessedCustomFee> accumulator,
			int maxBalanceChanges
	) {
		var result = NO_MORE_FEES;
		for (var fee : fees) {
			final var collector = fee.getFeeCollectorAsId();
			if (payer.equals(collector)) {
				continue;
			}
			if (fee.getFeeType() == FIXED_FEE) {
				final var fixedSpec = fee.getFixedFeeSpec();
				if (fixedSpec.getTokenDenomination() == null) {
					hbarFeeAssessor.assess(payer, fee, balanceChangeManager, accumulator);
				} else {
					htsFeeAssessor.assess(payer, chargingToken, fee, balanceChangeManager, accumulator);
				}
				if (balanceChangeManager.numChangesSoFar() > maxBalanceChanges) {
					return TOO_MANY_CHANGES_REQUIRED_FOR_FIXED_FEES;
				}
			} else {
				result = FRACTIONAL_FEES_PENDING;
			}
		}
		return result;
	}
}
