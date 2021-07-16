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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.grpc.marshalling.AdjustmentUtils.adjustedChange;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class FractionalFeeAssessor {
	public ResponseCodeEnum assessAllFractional(
			BalanceChange change,
			List<FcCustomFee> feesWithFractional,
			BalanceChangeManager changeManager,
			List<FcAssessedCustomFee> accumulator
	) {
		final var initialUnits = -change.units();
		if (initialUnits < 0) {
			throw new IllegalArgumentException("Cannot assess fees to a credit");
		}

		var unitsLeft = initialUnits;
		final var denom = change.getToken();
		for (var fee : feesWithFractional) {
			if (fee.getFeeType() == FRACTIONAL_FEE) {
				var assessedAmount = 0L;
				try {
					assessedAmount = amountOwedGiven(initialUnits, fee.getFractionalFeeSpec());
				} catch (ArithmeticException ignore) {
					return CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
				}

				unitsLeft -= assessedAmount;
				if (unitsLeft < 0) {
					return INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
				}
				change.adjustUnits(assessedAmount);

				final var collector = fee.getFeeCollectorAsId();
				adjustedChange(collector, denom, assessedAmount, changeManager);
				final var assessed = new FcAssessedCustomFee(collector.asEntityId(), denom.asEntityId(), assessedAmount);
				accumulator.add(assessed);
			}
		}
		return OK;
	}

	long amountOwedGiven(long initialUnits, FcCustomFee.FractionalFeeSpec spec) {
		final var nominalFee = safeFractionMultiply(spec.getNumerator(), spec.getDenominator(), initialUnits);
		long effectiveFee = Math.max(nominalFee, spec.getMinimumAmount());
		if (spec.getMaximumUnitsToCollect() > 0) {
			effectiveFee = Math.min(effectiveFee, spec.getMaximumUnitsToCollect());
		}
		return effectiveFee;
	}

	long safeFractionMultiply(long n, long d, long v) {
		if (v != 0 && n > Long.MAX_VALUE / v) {
			return BigInteger.valueOf(v).multiply(BigInteger.valueOf(n)).divide(BigInteger.valueOf(d)).longValueExact();
		} else {
			return n * v / d;
		}
	}
}
