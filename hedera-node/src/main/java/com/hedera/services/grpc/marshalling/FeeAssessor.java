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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.List;

import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class FeeAssessor {
	private final HtsFeeAssessor htsFeeAssessor;
	private final HbarFeeAssessor hbarFeeAssessor;
	private final FractionalFeeAssessor fractionalFeeAssessor;

	enum FixedFeeProcessingResult {
		ASSESSMENT_FINISHED,
		FRACTIONAL_FEE_ASSESSMENT_PENDING,
		ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED
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
			final BalanceChange change,
			final CustomSchedulesManager customSchedulesManager,
			final BalanceChangeManager balanceChangeManager,
			final List<FcAssessedCustomFee> accumulator,
			final ImpliedTransfersMeta.ValidationProps props,
			final HederaLedger ledger
	) {
		if (balanceChangeManager.getLevelNo() > props.getMaxNestedCustomFees()) {
			return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
		}
		final var feeMeta = customSchedulesManager.managedSchedulesFor(change.getToken());
		final var payer = change.getAccount();
		final var fees = feeMeta.getCustomFees();
		/* Token treasuries are exempt from all custom fees */
		if (fees.isEmpty() || feeMeta.getTreasuryId().equals(payer)) {
			return OK;
		}
		FixedFeeProcessingResult fixedFeeProcessingResult;
		final var maxBalanceChanges = props.getMaxXferBalanceChanges();

		fixedFeeProcessingResult = processFixedCustomFees(fees, payer, balanceChangeManager, accumulator, maxBalanceChanges);
		if(fixedFeeProcessingResult == FixedFeeProcessingResult.ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED) {
			return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
		}

		if (fixedFeeProcessingResult == FixedFeeProcessingResult.FRACTIONAL_FEE_ASSESSMENT_PENDING) {
			final var fractionalValidity =
					fractionalFeeAssessor.assessAllFractional(change, fees, balanceChangeManager, accumulator, ledger);
			if (fractionalValidity != OK) {
				return fractionalValidity;
			}
		}
		return (balanceChangeManager.numChangesSoFar() > maxBalanceChanges)
				? CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS : OK;
	}

	private FixedFeeProcessingResult processFixedCustomFees(final List<FcCustomFee> fees,
			final Id payer,
			final BalanceChangeManager balanceChangeManager,
			final List<FcAssessedCustomFee> accumulator,
			final int maxBalanceChanges
	) {
		FixedFeeProcessingResult result = FixedFeeProcessingResult.ASSESSMENT_FINISHED;
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
					htsFeeAssessor.assess(payer, fee, balanceChangeManager, accumulator);
				}
				if (balanceChangeManager.numChangesSoFar() > maxBalanceChanges) {
					return FixedFeeProcessingResult.ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED;
				}
			} else {
				 result = FixedFeeProcessingResult.FRACTIONAL_FEE_ASSESSMENT_PENDING;
			}
		}
		return result;
	}
}
