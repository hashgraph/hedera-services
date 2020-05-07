package com.hedera.services.legacy.services.fees;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.HbarCentExchange;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.service.GlobalFlag;

public class DefaultHbarCentExchange implements HbarCentExchange {
	private final TransactionContext txnCtx;

	public DefaultHbarCentExchange(TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
	}

	@Override
	public ExchangeRateSet activeRates() {
		return GlobalFlag.getInstance().getExchangeRateSet();
	}

	@Override
	public ExchangeRate activeRate() {
		var now = txnCtx.accessor().getTxn().getTransactionID().getTransactionValidStart();
		return rate(now);
	}

	@Override
	public ExchangeRate rate(Timestamp at) {
		var now = at.getSeconds();
		var rates = GlobalFlag.getInstance().getExchangeRateSet();
		var currentRateExpiry = rates.getCurrentRate().getExpirationTime().getSeconds();
		return (now < currentRateExpiry) ? rates.getCurrentRate() : rates.getNextRate();
	}
}
