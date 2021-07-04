package com.hedera.services.usage.token;

import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hederahashgraph.api.proto.java.CustomFee;

import java.util.List;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

public class TokenOpsUsage {
	private static final int FIXED_HBAR_REPR_SIZE = LONG_SIZE + BASIC_ENTITY_ID_SIZE;
	private static final int FIXED_HTS_REPR_SIZE = LONG_SIZE + 2 * BASIC_ENTITY_ID_SIZE;
	private static final int FRACTIONAL_REPR_SIZE = 4 * LONG_SIZE;

	private static final long LONG_BASIC_ENTITY_ID_SIZE = BASIC_ENTITY_ID_SIZE;

	public void feeScheduleUpdateUsage(
			SigUsage sigUsage,
			BaseTransactionMeta baseMeta,
			FeeScheduleUpdateMeta opMeta,
			ExtantFeeScheduleContext ctx,
			UsageAccumulator accumulator
	) {
		accumulator.resetForTransaction(baseMeta, sigUsage);

		accumulator.addBpt(LONG_BASIC_ENTITY_ID_SIZE + opMeta.numBytesInGrpcFeeScheduleRepr());
		final var lifetime = ctx.getExpiry() - opMeta.effConsensusTime();
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
		for (var fee : feeSchedule) {
			if (fee.hasFixedFee()) {
				if (fee.getFixedFee().hasDenominatingTokenId()) {
					numFixedHtsFees++;
				} else {
					numFixedHbarFees++;
				}
			} else {
				numFractionalFees++;
			}
		}
		return bytesNeededToRepr(numFixedHbarFees, numFixedHtsFees, numFractionalFees);
	}

	int bytesNeededToRepr(int numFixedHbarFees, int numFixedHtsFees, int numFractionalFees) {
		return numFixedHbarFees * FIXED_HBAR_REPR_SIZE
				+ numFixedHtsFees * FIXED_HTS_REPR_SIZE
				+ numFractionalFees * FRACTIONAL_REPR_SIZE;
	}
}
