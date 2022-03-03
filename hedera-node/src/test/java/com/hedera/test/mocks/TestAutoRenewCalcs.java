package com.hedera.test.mocks;

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

import com.hedera.services.fees.calculation.AutoRenewCalcs;
import com.hedera.services.fees.calculation.RenewAssessment;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import org.apache.commons.lang3.tuple.Triple;

import java.time.Instant;
import java.util.Map;

public class TestAutoRenewCalcs extends AutoRenewCalcs {
	public TestAutoRenewCalcs() {
		super(null);
	}

	@Override
	public RenewAssessment maxRenewalAndFeeFor(
			MerkleAccount expiredAccount,
			long reqPeriod,
			Instant at,
			ExchangeRate active
	) {
		return new RenewAssessment(0L, Long.MAX_VALUE);
	}

	@Override
	public void setCryptoAutoRenewPriceSeq(
			Triple<Map<SubType, FeeData>, Instant, Map<SubType, FeeData>> cryptoAutoRenewPriceSeq) {
		/* No-op */
	}
}
