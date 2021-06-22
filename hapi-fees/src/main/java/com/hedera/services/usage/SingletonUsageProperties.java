package com.hedera.services.usage;

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

import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;

/**
 * Enum implements {@link UsageProperties} that returns the account amount, nft transfer usage in terms of bytes and
 * storage of receipt in ledger in seconds.
 * */
public enum SingletonUsageProperties implements UsageProperties {
	USAGE_PROPERTIES;

	@Override
	/**
	 * Gives the bytes usage for account amount
	 * */
	public int accountAmountBytes() {
		return LONG_SIZE + BASIC_ENTITY_ID_SIZE;
	}

	/**
	 * Gives the bytes usage for nft transfers
	 * */
	@Override
	public int nftTransferBytes() {
		return LONG_SIZE + 2 * BASIC_ENTITY_ID_SIZE;
	}

	/**
	 * Gives the length in seconds for storage of receipt
	 * */
	@Override
	public long legacyReceiptStorageSecs() {
		return 180;
	}
}
