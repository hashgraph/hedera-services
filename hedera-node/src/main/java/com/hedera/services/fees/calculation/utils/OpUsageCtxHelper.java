package com.hedera.services.fees.calculation.utils;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;
import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FRACTIONAL_FEE;

public class OpUsageCtxHelper {
	private static final ExtantFeeScheduleContext MISSING_FEE_SCHEDULE_UPDATE_CTX =
			new ExtantFeeScheduleContext(0, 0);

	private final TokenOpsUsage tokenOpsUsage = new TokenOpsUsage();

	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	public OpUsageCtxHelper(Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens) {
		this.tokens = tokens;
	}

	public ExtantFeeScheduleContext ctxForFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody op) {
		final var key = MerkleEntityId.fromTokenId(op.getTokenId());
		final var token = tokens.get().get(key);
		if (token == null) {
			return MISSING_FEE_SCHEDULE_UPDATE_CTX;
		}
		return new ExtantFeeScheduleContext(token.expiry(), curFeeScheduleReprSize(token.customFeeSchedule()));
	}

	private int curFeeScheduleReprSize(List<FcCustomFee> feeSchedule) {
		int numFixedHbarFees = 0;
		int numFixedHtsFees = 0;
		int numFractionalFees = 0;
		int numRoyaltyNoFallbackFees = 0;
		int numRoyaltyHtsFallbackFees = 0;
		int numRoyaltyHbarFallbackFees = 0;
		for (var fee : feeSchedule) {
			if (fee.getFeeType() == FIXED_FEE) {
				if (fee.getFixedFeeSpec().getTokenDenomination() != null) {
					numFixedHtsFees++;
				} else {
					numFixedHbarFees++;
				}
			} else if (fee.getFeeType() == FRACTIONAL_FEE) {
				numFractionalFees++;
			} else {
				final var royaltyFee = fee.getRoyaltyFeeSpec();
				final var fallbackFee = royaltyFee.getFallbackFee();
				if (fallbackFee != null) {
					if (fallbackFee.getTokenDenomination() != null) {
						numRoyaltyHtsFallbackFees++;
					} else {
						numRoyaltyHbarFallbackFees++;
					}
				} else {
					numRoyaltyNoFallbackFees++;
				}
			}
		}
		return tokenOpsUsage.bytesNeededToRepr(
				numFixedHbarFees,
				numFixedHtsFees,
				numFractionalFees,
				numRoyaltyNoFallbackFees,
				numRoyaltyHtsFallbackFees,
				numRoyaltyHbarFallbackFees);
	}
}
