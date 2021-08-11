package com.hedera.services.grpc.marshalling;

import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.List;

public class FixedFeeAssessor {
	private final HtsFeeAssessor htsFeeAssessor;
	private final HbarFeeAssessor hbarFeeAssessor;

	public FixedFeeAssessor(
			HtsFeeAssessor htsFeeAssessor,
			HbarFeeAssessor hbarFeeAssessor
	) {
		this.htsFeeAssessor = htsFeeAssessor;
		this.hbarFeeAssessor = hbarFeeAssessor;
	}

	public ResponseCodeEnum assess(
			Id account,
			Id chargingToken,
			FcCustomFee fee,
			BalanceChangeManager changeManager,
			List<FcAssessedCustomFee> accumulator
	) {
		final var fixedSpec = fee.getFixedFeeSpec();
		if (fixedSpec.getTokenDenomination() == null) {
			return hbarFeeAssessor.assess(account, fee, changeManager, accumulator);
		} else {
			return htsFeeAssessor.assess(account, chargingToken, fee, changeManager, accumulator);
		}
	}
}
