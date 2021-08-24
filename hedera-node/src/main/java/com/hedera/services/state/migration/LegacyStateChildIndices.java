package com.hedera.services.state.migration;

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

/**
 *  Recalls the Services 0.16.0 Merkle node children with indices different from 0.17.0, used for migration.
 */
public class LegacyStateChildIndices {
	public static final int ADDRESS_BOOK = 0;
	public static final int NETWORK_CTX = 1;
	public static final int TOKEN_ASSOCIATIONS = 6;
	public static final int UNIQUE_TOKENS = 10;

	public static final int NUM_0160_CHILDREN = 11;

	LegacyStateChildIndices() {
		throw new IllegalStateException("Utility Class");
	}
}
