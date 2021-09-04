package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.UsageProperties;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.entities.TokenEntitySizes;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hederahashgraph.api.proto.java.CustomFee;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

@Singleton
public class TokenOpsUsage {
	/* Sizes of various fee types, _not_ including the collector entity id */
	private static final int FIXED_HBAR_REPR_SIZE = LONG_SIZE;
	private static final int FIXED_HTS_REPR_SIZE = LONG_SIZE + BASIC_ENTITY_ID_SIZE;
	private static final int FRACTIONAL_REPR_SIZE = 4 * LONG_SIZE;
	private static final int ROYALTY_NO_FALLBACK_REPR_SIZE = 2 * LONG_SIZE;
	private static final int ROYALTY_HBAR_FALLBACK_REPR_SIZE = ROYALTY_NO_FALLBACK_REPR_SIZE + FIXED_HBAR_REPR_SIZE;
	private static final int ROYALTY_HTS_FALLBACK_REPR_SIZE = ROYALTY_NO_FALLBACK_REPR_SIZE + FIXED_HTS_REPR_SIZE;

	private static final long LONG_BASIC_ENTITY_ID_SIZE = BASIC_ENTITY_ID_SIZE;
	protected static UsageProperties usageProperties = USAGE_PROPERTIES;

	static TokenEntitySizes tokenEntitySizes = TOKEN_ENTITY_SIZES;

	@Inject
	public TokenOpsUsage() {
	}

	public void feeScheduleUpdateUsage(
			SigUsage sigUsage,
			BaseTransactionMeta baseMeta,
			FeeScheduleUpdateMeta opMeta,
			ExtantFeeScheduleContext ctx,
			UsageAccumulator accumulator
	) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

		accumulator.addBpt(LONG_BASIC_ENTITY_ID_SIZE + opMeta.numBytesInNewFeeScheduleRepr());
		final var lifetime = Math.max(0, ctx.expiry() - opMeta.effConsensusTime());
		final var rbsDelta = ESTIMATOR_UTILS.changeInBsUsage(
				ctx.numBytesInFeeScheduleRepr(),
				lifetime,
				opMeta.numBytesInNewFeeScheduleRepr(),
				lifetime);
		accumulator.addRbs(rbsDelta);
	}

	public int bytesNeededToRepr(List<CustomFee> feeSchedule) {
		int numFixedHbarFees = 0;
		int numFixedHtsFees = 0;
		int numFractionalFees = 0;
		int numRoyaltyNoFallbackFees = 0;
		int numRoyaltyHtsFallbackFees = 0;
		int numRoyaltyHbarFallbackFees = 0;
		for (var fee : feeSchedule) {
			if (fee.hasFixedFee()) {
				if (fee.getFixedFee().hasDenominatingTokenId()) {
					numFixedHtsFees++;
				} else {
					numFixedHbarFees++;
				}
			} else if (fee.hasFractionalFee()) {
				numFractionalFees++;
			} else {
				final var royaltyFee = fee.getRoyaltyFee();
				if (royaltyFee.hasFallbackFee()) {
					if (royaltyFee.getFallbackFee().hasDenominatingTokenId()) {
						numRoyaltyHtsFallbackFees++;
					} else {
						numRoyaltyHbarFallbackFees++;
					}
				} else {
					numRoyaltyNoFallbackFees++;
				}
			}
		}
		return bytesNeededToRepr(
				numFixedHbarFees,
				numFixedHtsFees,
				numFractionalFees,
				numRoyaltyNoFallbackFees,
				numRoyaltyHtsFallbackFees,
				numRoyaltyHbarFallbackFees);
	}

	public int bytesNeededToRepr(
			int numFixedHbarFees,
			int numFixedHtsFees,
			int numFractionalFees,
			int numRoyaltyNoFallbackFees,
			int numRoyaltyHtsFallbackFees,
			int numRoyaltyHbarFallbackFees
	) {
		return numFixedHbarFees * plusCollectorSize(FIXED_HBAR_REPR_SIZE)
				+ numFixedHtsFees * plusCollectorSize(FIXED_HTS_REPR_SIZE)
				+ numFractionalFees * plusCollectorSize(FRACTIONAL_REPR_SIZE)
				+ numRoyaltyNoFallbackFees * plusCollectorSize(ROYALTY_NO_FALLBACK_REPR_SIZE)
				+ numRoyaltyHtsFallbackFees * plusCollectorSize(ROYALTY_HTS_FALLBACK_REPR_SIZE)
				+ numRoyaltyHbarFallbackFees * plusCollectorSize(ROYALTY_HBAR_FALLBACK_REPR_SIZE);

	}
	public void tokenCreateUsage(final SigUsage sigUsage,
			final BaseTransactionMeta baseMeta,
			final TokenCreateMeta tokenCreateMeta,
			final UsageAccumulator accumulator) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

		accumulator.addBpt(tokenCreateMeta.getBaseSize());
		accumulator.addRbs((tokenCreateMeta.getBaseSize() + tokenCreateMeta.getCustomFeeScheduleSize()) *
				tokenCreateMeta.getLifeTime());

		long tokenSizes = tokenEntitySizes.bytesUsedToRecordTokenTransfers(tokenCreateMeta.getNumTokens(),
				tokenCreateMeta.getFungibleNumTransfers() , tokenCreateMeta.getNftsTransfers()) *
				usageProperties.legacyReceiptStorageSecs();
		accumulator.addRbs(tokenSizes);

		accumulator.addNetworkRbs(tokenCreateMeta.getNetworkRecordRb() * usageProperties.legacyReceiptStorageSecs());
	}

	private int plusCollectorSize(int feeReprSize) {
		return feeReprSize + BASIC_ENTITY_ID_SIZE;
	}
}
