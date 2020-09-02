package com.hedera.services.context.properties;

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

public class GlobalDynamicProperties {
	private final PropertySource properties;

	private int maxTokensPerAccount;
	private int maxTokensSymbolLength;
	private long maxAccountNum;
	private long defaultContractSendThreshold;
	private long defaultContractReceiveThreshold;

	public GlobalDynamicProperties(PropertySource properties) {
		this.properties = properties;

		reload();
	}

	public void reload() {
		maxTokensPerAccount = properties.getIntProperty("tokens.maxPerAccount");
		maxTokensSymbolLength = properties.getIntProperty("tokens.maxSymbolLength");
		maxAccountNum = properties.getLongProperty("ledger.maxAccountNum");
		defaultContractSendThreshold = properties.getLongProperty("contracts.defaultSendThreshold");
		defaultContractReceiveThreshold = properties.getLongProperty("contracts.defaultReceiveThreshold");
	}

	public long defaultContractSendThreshold() {
		return defaultContractSendThreshold;
	}

	public long defaultContractReceiveThreshold() {
		return defaultContractReceiveThreshold;
	}

	public int maxTokensPerAccount() {
		return maxTokensPerAccount;
	}

	public int maxTokenSymbolLength() {
		return maxTokensSymbolLength;
	}

	public long maxAccountNum() {
		return maxAccountNum;
	}
}
