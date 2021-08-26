package com.hedera.services.fees;

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

import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class BasicHbarCentExchange implements HbarCentExchange {
	private ExchangeRates fcRates = null;
	private ExchangeRateSet grpcRates = null;

	@Inject
	public BasicHbarCentExchange() {
	}

	@Override
	public ExchangeRate activeRate(Instant now) {
		return rateAt(now.getEpochSecond());
	}

	@Override
	public ExchangeRateSet activeRates() {
		return grpcRates;
	}

	@Override
	public ExchangeRate rate(Timestamp now) {
		return rateAt(now.getSeconds());
	}

	@Override
	public void updateRates(ExchangeRateSet rates) {
		this.grpcRates = rates;
		this.fcRates = ExchangeRates.fromGrpc(rates);
	}

	@Override
	public ExchangeRates fcActiveRates() {
		return fcRates;
	}

	private ExchangeRate rateAt(long now) {
		var currentRate = grpcRates.getCurrentRate();
		long currentExpiry = currentRate.getExpirationTime().getSeconds();
		return (now < currentExpiry) ? currentRate : grpcRates.getNextRate();
	}
}
