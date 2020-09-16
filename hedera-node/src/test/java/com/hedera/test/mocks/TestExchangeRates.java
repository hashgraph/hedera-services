package com.hedera.test.mocks;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.fees.HbarCentExchange;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.builder.RequestBuilder;

public enum TestExchangeRates implements HbarCentExchange {
	TEST_EXCHANGE;

	long EXPIRY_TIME = 4688462211l;

	ExchangeRateSet rates = RequestBuilder
			.getExchangeRateSetBuilder(
					1, 12,
					EXPIRY_TIME,
					1, 15,
					EXPIRY_TIME);
	@Override
	public ExchangeRate activeRate() {
		return rates.getCurrentRate();
	}

	@Override
	public ExchangeRateSet activeRates() {
		return rates;
	}

	@Override
	public ExchangeRate rate(Timestamp at) {
		return rates.getCurrentRate();
	}
}
