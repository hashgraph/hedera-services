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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;

public class AwareHbarCentExchange implements HbarCentExchange {
	private final TransactionContext txnCtx;

	private ExchangeRates fcRates = null;
	private ExchangeRateSet grpcRates = null;

	public AwareHbarCentExchange(TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
	}

	@Override
	public ExchangeRate activeRate() {
		var now = txnCtx.accessor().getTxn().getTransactionID().getTransactionValidStart();
		return rate(now);
	}

	@Override
	public ExchangeRateSet activeRates() {
		return grpcRates;
	}

	@Override
	public ExchangeRate rate(Timestamp at) {
		var currentRate = grpcRates.getCurrentRate();
		long currentExpiry = currentRate.getExpirationTime().getSeconds();
		return (at.getSeconds() < currentExpiry) ? currentRate : grpcRates.getNextRate();
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
}
