package com.hedera.services.grpc.marshalling;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.List;

public class RoyaltyFeeAssessor {
	private final HtsFeeAssessor htsFeeAssessor;
	private final HbarFeeAssessor hbarFeeAssessor;

	public RoyaltyFeeAssessor(HtsFeeAssessor htsFeeAssessor, HbarFeeAssessor hbarFeeAssessor) {
		this.htsFeeAssessor = htsFeeAssessor;
		this.hbarFeeAssessor = hbarFeeAssessor;
	}

	public ResponseCodeEnum assessAllRoyalties(
			BalanceChange change,
			List<FcCustomFee> feesWithRoyalties,
			BalanceChangeManager changeManager,
			List<FcAssessedCustomFee> accumulator
	) {
		throw new AssertionError("Not implemented!");
	}
}
