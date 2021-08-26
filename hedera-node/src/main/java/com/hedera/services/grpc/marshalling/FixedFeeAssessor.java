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

import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class FixedFeeAssessor {
	private final HtsFeeAssessor htsFeeAssessor;
	private final HbarFeeAssessor hbarFeeAssessor;

	@Inject
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
