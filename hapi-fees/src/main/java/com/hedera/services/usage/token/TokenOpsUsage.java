package com.hedera.services.usage.token;

import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public class TokenOpsUsage {
	private static final int FIXED_HBAR_REPR_SIZE = LONG_SIZE + BASIC_ENTITY_ID_SIZE;
	private static final int FIXED_HTS_REPR_SIZE = LONG_SIZE + 2 * BASIC_ENTITY_ID_SIZE;
	private static final int FRACTIONAL_REPR_SIZE = 4 * LONG_SIZE;

	public void feeScheduleUpdateUsage(
			SigUsage sigUsage,
			BaseTransactionMeta baseMeta,
			ExtantFeeScheduleContext ctx,
			UsageAccumulator accumulator
	) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

//		long incBpt = weightedTokensInvolved * LONG_BASIC_ENTITY_ID_SIZE;
//		incBpt += (weightedTokenXfers + totalXfers) * LONG_ACCOUNT_AMOUNT_BYTES;
//		accumulator.addBpt(incBpt);

//		long incRb = totalXfers * LONG_ACCOUNT_AMOUNT_BYTES;
//		incRb += TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(weightedTokensInvolved, weightedTokenXfers, 0);
//		accumulator.addRbs(incRb * USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	public int bytesNeededToRepr(int numFixedHbarFees, int numFixedHtsFees, int numFractionalFees) {
		return numFixedHbarFees * FIXED_HBAR_REPR_SIZE
				+ numFixedHtsFees * FIXED_HTS_REPR_SIZE
				+ numFractionalFees * FRACTIONAL_REPR_SIZE;
	}
}
