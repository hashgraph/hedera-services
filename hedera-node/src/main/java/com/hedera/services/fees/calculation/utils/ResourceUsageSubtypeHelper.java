package com.hedera.services.fees.calculation.utils;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenType;

import java.util.Optional;

public class ResourceUsageSubtypeHelper {
	public SubType determineTokenType(Optional<TokenType> tokenType) {
		if (tokenType.isPresent()) {
			switch (tokenType.get()) {
				case FUNGIBLE_COMMON:
					return SubType.TOKEN_FUNGIBLE_COMMON;
				case NON_FUNGIBLE_UNIQUE:
					return SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
				default:
					return SubType.DEFAULT;
			}
		} else {
			return SubType.DEFAULT;
		}
	}
}
