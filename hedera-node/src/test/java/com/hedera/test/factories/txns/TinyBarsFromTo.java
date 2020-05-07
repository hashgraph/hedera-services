package com.hedera.test.factories.txns;

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

public class TinyBarsFromTo {
	private final String payer;
	private final String payee;
	private final long amount;

	private TinyBarsFromTo(String payer, String payee, long amount) {
		this.payer = payer;
		this.payee = payee;
		this.amount = amount;
	}

	public static TinyBarsFromTo tinyBarsFromTo(String payer, String payee, long amount) {
		return new TinyBarsFromTo(payer, payee, amount);
	}

	public String getPayer() {
		return payer;
	}

	public String getPayee() {
		return payee;
	}

	public long getAmount() {
		return amount;
	}
}
