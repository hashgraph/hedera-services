package com.hedera.services.throttling;

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

import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.Optional;

import static com.hedera.services.utils.MiscUtils.functionalityOfTxn;

public class TransactionThrottling {
	private final FunctionalityThrottling throttles;

	public TransactionThrottling(FunctionalityThrottling throttles) {
		this.throttles = throttles;
	}

	public boolean shouldThrottle(TransactionBody txn) {
		Optional<HederaFunctionality> function = functionToThrottle(txn);

		return function.map(throttles::shouldThrottle).orElse(true);
	}

	private Optional<HederaFunctionality> functionToThrottle(TransactionBody txn) {
		try {
			return Optional.of(functionalityOfTxn(txn));
		} catch (UnknownHederaFunctionality ignore) {}
		return Optional.empty();
	}
}
