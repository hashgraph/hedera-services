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
		if (feesWithFractional.isEmpty()) {
			throw new IllegalArgumentException("Custom fees can't be empty here");
		}
		final var payer = change.getAccount();

		// Check all fractional fee schedules, for transfer may pay multiple fractional fees to different collector(?)
		// we need to process the combo (change, fractional fee schedule) one by one
		for (var fee : feesWithFractional) {
			final var collector = fee.getFeeCollectorAsId();
			if (fee.getFeeType() != FRACTIONAL_FEE || payer.equals(collector)) {
				continue;
			}

			ResponseCodeEnum result = assessOneFractionalForThisChange(change, fee, changeManager, accumulator);
			if(result != OK) {
				return result;
			}
		}
		return OK;
	}

	private ResponseCodeEnum assessOneFractionalForThisChange(final BalanceChange change,
			final FcCustomFee fee,
			BalanceChangeManager changeManager,
			List<FcAssessedCustomFee> accumulator) {

		final boolean chargeSender = fee.getFractionalFeeSpec().getNetOfTransfers();
		final var collector = fee.getFeeCollectorAsId();
		if(chargeSender) {
			if (change.units() < 0) { //     // charge sender
				//if(chargeSender && change.units() < 0) { // ? this doesn't work
				final var initialUnits = -change.units();

				var totalCharge = initialUnits;
				final var payer = change.getAccount();
				final var denom = change.getToken();

				var assessedAmount = 0L;
				try {
					assessedAmount = amountOwedGiven(initialUnits, fee.getFractionalFeeSpec());
				} catch (ArithmeticException ignore) {
					return CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
				}

				System.out.println("token fee: " + assessedAmount);

//			totalCharge += assessedAmount;
//			check if its account balance is sufficient
//			if (totalCharge > payer.asMerkle()) {
//				return INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
//			}
				// If charging sender, simply adjust the debit total
				adjustedChange(payer, denom, -assessedAmount, changeManager, chargeSender);
				adjustedChange(collector, denom, assessedAmount, changeManager, chargeSender);
				final var assessed = new FcAssessedCustomFee(collector.asEntityId(), denom.asEntityId(),
						assessedAmount);
				accumulator.add(assessed);
			}
		}
		else {
			if (change.units() > 0 ) { //  charge receiver
               // else if (change.units() > 0 && !chargeSender) { //

				final var initialUnits = change.units();

				System.out.println("Processing charge receiver: " + change.getAccount());

				var unitsLeft = initialUnits;
				final var denom = change.getToken();

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
				final var creditsToReclaimFrom = changeManager.creditsInCurrentLevel(denom);
				try {
					reclaim(assessedAmount, creditsToReclaimFrom);
				} catch (ArithmeticException ignore) {
					return CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
				}

				adjustedChange(collector, denom, assessedAmount, changeManager, chargeSender);
				final var assessed = new FcAssessedCustomFee(collector.asEntityId(), denom.asEntityId(), assessedAmount);
				accumulator.add(assessed);
			}
		}
		return OK;
	}

	void reclaim(long amount, List<BalanceChange> credits) {
		var availableToReclaim = 0L;
		for (var credit : credits) {
			availableToReclaim += credit.units();
			if (availableToReclaim < 0L) {
				throw new ArithmeticException();
			}
		}

		var amountReclaimed = 0L;
		for (var credit : credits) {
			var toReclaimHere = safeFractionMultiply(credit.units(), availableToReclaim, amount);
			credit.adjustUnits(-toReclaimHere);
			amountReclaimed += toReclaimHere;
		}

		if (amountReclaimed < amount) {
			var leftToReclaim = amount - amountReclaimed;
			for (var credit : credits) {
				final var toReclaimHere = Math.min(credit.units(), leftToReclaim);
				credit.adjustUnits(-toReclaimHere);
				leftToReclaim -= toReclaimHere;
				if (leftToReclaim == 0) {
					break;
				}
			}
		}
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
