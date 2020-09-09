package com.hedera.services.legacy.config;

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

import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.logic.CustomProperties;

/**
 * This class reads and stores values from property file which should be Synchronous
 * i.e. the value, which are assumed to be refreshed once handleTrsnaction method is invoked and 
 * they need to be same in all Nodes, if these values are not same , it will result in ISS exception
 * In other words, they impact state of system.
 */
public class SyncPropertiesObject {
	// Tx record & receipt ttl setting in seconds
	private static int thresholdTxRecordTTL = ApplicationConstants.THRESH_REC_TTL;
	private static int txMinDuration = ApplicationConstants.TX_MIN_DUR;
	private static int txMaxDuration = ApplicationConstants.TX_MAX_DUR;
	private static int txMinRemaining = ApplicationConstants.TXN_MIN_VALIDITY_BUFFER_SECS;

	private static int keyExpansionDepth = ApplicationConstants.KEY_EXPANSION_DEPTH;

	private static long initialGenesisCoins = ApplicationConstants.INITIAL_GENESIS_COINS;
	private static long defaultContractDurationSec = ApplicationConstants.DEFAULT_CONTRACT_DURATION_IN_SEC;

	private static long minimumAutoRenewDuration = ApplicationConstants.MINIMUM_AUTORENEW_DURATION;
	private static long maximumAutoRenewDuration= ApplicationConstants.MAXIMUM_AUTORENEW_DURATION;

	private static int transferListSizeLimit = ApplicationConstants.TRANSFER_LST_LIMIT;

	// if a node account's balance is less than this value tinybars, we should log
	// Insufficient Node Balance warning;
	private static long nodeAccountBalanceValidity = ApplicationConstants.ZERO;

	// Estimates for calculating fees for Smart Contract local calls
	private static int localCallEstReturnBytes = ApplicationConstants.LOCAL_CALLEST_RET_BYTES;

	// Max storage allowed to a contract, in KiB
	private static int maxContractStateSize = ApplicationConstants.MAX_CONTRACT_STATE_SIZE;

	/**
	 * This percentage setting means you can increase or decrease Exchange Rate by
	 * this many percent. Suppose its value is p, then you can increase Exchange
	 * Rate up to 1+p/100 times the original, or decrease by the inverse
	 * (1/(1+p/100) times the original. The amount must always be a positive
	 * integer, never 0 or negative or bigger than Integer.MAX.
	 */
	private static int exchangeRateAllowedPercentage = ApplicationConstants.DEFAULT_EXCHANGE_RATE_ALLOWED_PERCENTAGE;
	
	private static int maxGasLimit = ApplicationConstants.MAX_GAS_LIMIT;

	// load Synch Properties
	public static void loadSynchProperties(CustomProperties appConfig) {
		keyExpansionDepth = appConfig.getInt("keyExpansionDepth", ApplicationConstants.KEY_EXPANSION_DEPTH);
		// Tx record & receipt ttl setting in seconds
		thresholdTxRecordTTL = appConfig.getInt("thresholdTxRecordTTL", ApplicationConstants.THRESH_REC_TTL);
		txMinDuration = appConfig.getInt("txMinimumDuration", ApplicationConstants.TX_MIN_DUR);
		txMaxDuration = appConfig.getInt("txMaximumDuration", ApplicationConstants.TX_MAX_DUR);
		txMinRemaining = appConfig.getInt("txMinimumRemaining", ApplicationConstants.TXN_MIN_VALIDITY_BUFFER_SECS);

		initialGenesisCoins = appConfig.getLong("initialGenesisCoins", ApplicationConstants.INITIAL_GENESIS_COINS);
		// default valid duration of the contract in seconds
		defaultContractDurationSec = appConfig.getLong("defaultContractDurationSec", ApplicationConstants.DEFAULT_CONTRACT_DURATION_IN_SEC);

		minimumAutoRenewDuration = appConfig.getLong("minimumAutoRenewDuration", ApplicationConstants.MINIMUM_AUTORENEW_DURATION);
		maximumAutoRenewDuration = appConfig.getLong("maximumAutoRenewDuration", ApplicationConstants.MAXIMUM_AUTORENEW_DURATION);

		transferListSizeLimit = appConfig.getInt("transferListSizeLimit", ApplicationConstants.TRANSFER_LST_LIMIT);

		// if a node account's balance is less than this value tinybars, we should log
		// Insufficient Node Balance warning;
		nodeAccountBalanceValidity = appConfig.getLong("nodeAccountBalanceValidity", ApplicationConstants.ZERO);

		// Estimates for calculating fees for Smart Contract local calls
		localCallEstReturnBytes = appConfig.getInt("localCallEstReturnBytes", ApplicationConstants.LOCAL_CALLEST_RET_BYTES);

		maxContractStateSize = appConfig.getInt("maxContractStateSize", ApplicationConstants.MAX_CONTRACT_STATE_SIZE);

		/**
		 * This percentage setting means you can increase or decrease Exchange Rate by
		 * this many percent. Suppose its value is p, then you can increase Exchange
		 * Rate up to 1+p/100 times the original, or decrease by the inverse
		 * (1/(1+p/100) times the original. The amount must always be a positive
		 * integer, never 0 or negative or bigger than Integer.MAX.
		 */
		exchangeRateAllowedPercentage = appConfig.getInt("exchangeRateAllowedPercentage",
				ApplicationConstants.DEFAULT_EXCHANGE_RATE_ALLOWED_PERCENTAGE);
		maxGasLimit = appConfig.getInt("maxGasLimit", ApplicationConstants.MAX_GAS_LIMIT);
	}

	static int getThresholdTxRecordTTL() {
		return thresholdTxRecordTTL;
	}

	static int getTxMinDuration() {
		return txMinDuration;
	}

	static int getTxMaxDuration() {
		return txMaxDuration;
	}

	static int getTxMinRemaining() {
		return txMinRemaining;
	}

	static int getKeyExpansionDepth() {
		return keyExpansionDepth;
	}

	static long getInitialGenesisCoins() {
		return initialGenesisCoins;
	}

	static long getDefaultContractDurationSec() {
		return defaultContractDurationSec;
	}

	static long getMINIMUM_AUTORENEW_DURATION() {
		return minimumAutoRenewDuration;
	}

	static long getMAXIMUM_AUTORENEW_DURATION() {
		return maximumAutoRenewDuration;
	}

	static int getTransferListSizeLimit() {
		return transferListSizeLimit;
	}

	static long getNodeAccountBalanceValidity() {
		return nodeAccountBalanceValidity;
	}

	static int getLocalCallEstReturnBytes() {
		return localCallEstReturnBytes;
	}

	static int getMaxContractStateSize() {
		return maxContractStateSize;
	}

	static int getExchangeRateAllowedPercentage() {
		return exchangeRateAllowedPercentage;
	}

	static boolean validExchangeRateAllowedPercentage() {
		if (exchangeRateAllowedPercentage <= 0) {
			exchangeRateAllowedPercentage = ApplicationConstants.DEFAULT_EXCHANGE_RATE_ALLOWED_PERCENTAGE;
			return false;
		}
		return true;
	}
	
	static int getMaxGasLimit() {
	    return maxGasLimit;
	}
}
