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

import com.hedera.services.config.HederaNumbers;
import com.hederahashgraph.api.proto.java.AccountID;

public class GlobalDynamicProperties {
	private final HederaNumbers hederaNums;
	private final PropertySource properties;

	private int maxTokensPerAccount;
	private int maxTokensSymbolLength;
	private int maxTokensNameLength;
	private int maxFileSizeKb;
	private int cacheRecordsTtl;
	private int maxContractStorageKb;
	private int balancesExportPeriodSecs;
	private int ratesIntradayChangeLimitPercent;
	private long maxAccountNum;
	private long defaultContractSendThreshold;
	private long defaultContractReceiveThreshold;
	private long nodeBalanceWarningThreshold;
	private String pathToBalancesExportDir;
	private boolean shouldCreateThresholdRecords;
	private boolean shouldExportBalances;
	private boolean shouldExportTokenBalances;
	private AccountID fundingAccount;
	private int maxTransfersLen;
	private int maxTokenTransfersLen;

	public GlobalDynamicProperties(
			HederaNumbers hederaNums,
			PropertySource properties
	) {
		this.hederaNums = hederaNums;
		this.properties = properties;

		reload();
	}

	public void reload() {
		shouldCreateThresholdRecords = properties.getBooleanProperty("ledger.createThresholdRecords");
		maxTokensPerAccount = properties.getIntProperty("tokens.maxPerAccount");
		maxTokensSymbolLength = properties.getIntProperty("tokens.maxSymbolLength");
		maxTokensNameLength = properties.getIntProperty("tokens.maxTokenNameLength");
		maxAccountNum = properties.getLongProperty("ledger.maxAccountNum");
		defaultContractSendThreshold = properties.getLongProperty("contracts.defaultSendThreshold");
		defaultContractReceiveThreshold = properties.getLongProperty("contracts.defaultReceiveThreshold");
		maxFileSizeKb = properties.getIntProperty("files.maxSizeKb");
		fundingAccount = AccountID.newBuilder()
				.setShardNum(hederaNums.shard())
				.setRealmNum(hederaNums.realm())
				.setAccountNum(properties.getLongProperty("ledger.fundingAccount"))
				.build();
		cacheRecordsTtl = properties.getIntProperty("cache.records.ttl");
		maxContractStorageKb = properties.getIntProperty("contracts.maxStorageKb");
		ratesIntradayChangeLimitPercent = properties.getIntProperty("rates.intradayChangeLimitPercent");
		balancesExportPeriodSecs = properties.getIntProperty("balances.exportPeriodSecs");
		shouldExportBalances = properties.getBooleanProperty("balances.exportEnabled");
		nodeBalanceWarningThreshold = properties.getLongProperty("balances.nodeBalanceWarningThreshold");
		pathToBalancesExportDir = properties.getStringProperty("balances.exportDir.path");
		shouldExportTokenBalances = properties.getBooleanProperty("balances.exportTokenBalances");
		maxTransfersLen = properties.getIntProperty("ledger.transfers.maxLen");
		maxTokenTransfersLen = properties.getIntProperty("ledger.tokenTransfers.maxLen");
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

	public int maxTokensNameLength() {
		return maxTokensNameLength;
	}

	public int maxFileSizeKb() {
		return maxFileSizeKb;
	}

	public AccountID fundingAccount() {
		return fundingAccount;
	}

	public int cacheRecordsTtl() {
		return cacheRecordsTtl;
	}

	public int maxContractStorageKb() {
		return maxContractStorageKb;
	}

	public int ratesIntradayChangeLimitPercent() {
		return ratesIntradayChangeLimitPercent;
        }

	public boolean shouldCreateThresholdRecords() {
		return shouldCreateThresholdRecords;
	}

	public int balancesExportPeriodSecs() {
		return balancesExportPeriodSecs;
	}

	public boolean shouldExportBalances() {
		return shouldExportBalances;
	}

	public long nodeBalanceWarningThreshold() {
		return nodeBalanceWarningThreshold;
	}

	public String pathToBalancesExportDir() {
		return pathToBalancesExportDir;
	}

	public boolean shouldExportTokenBalances() {
		return shouldExportTokenBalances;
	}

	public int maxTransferListSize() {
		return maxTransfersLen;
	}

	public int maxTokenTransferListSize() {
		return maxTokenTransfersLen;
	}
}
