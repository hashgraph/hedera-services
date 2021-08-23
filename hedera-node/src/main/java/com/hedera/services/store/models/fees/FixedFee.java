package com.hedera.services.store.models.fees;

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

import com.hedera.services.store.models.Id;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Represents a fixed fee - either a custom HTS fee or an HBar fee.
 * Segregation of both types is based on the presence of a denominating token.
 *
 * @author Yoan Sredkov (yoansredkov@gmail.com)
 */
public class FixedFee {
	private Id denominatingTokenId;

	private final long amount;

	public FixedFee(long amount, @Nullable Id denominatingTokenId) {
		this.amount = amount;
		this.denominatingTokenId = denominatingTokenId;
	}

	public long getAmount() {
		return amount;
	}

	public Optional<Id> getDenominatingTokenId() {
		return denominatingTokenId == null ? Optional.empty() : Optional.of(denominatingTokenId);
	}

	public void setDenominatingTokenId(final Id denominatingTokenId) {
		this.denominatingTokenId = denominatingTokenId;
	}
}
