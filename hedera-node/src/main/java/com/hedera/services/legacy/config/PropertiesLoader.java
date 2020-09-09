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

import com.hedera.services.context.domain.security.PermissionedAccountsRange;
import com.hedera.services.legacy.logic.CustomProperties;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PropertiesLoader {
	public static final Logger log = LogManager.getLogger(PropertiesLoader.class);

	public static CustomProperties applicationProps;
	public static CustomProperties apiProperties;
	public static List<Runnable> updateCallbacks = new ArrayList<>();

	public static void registerUpdateCallback(Runnable cb) {
		updateCallbacks.add(cb);
	}

	public static void populateApplicationPropertiesWithProto(ServicesConfigurationList serviceConfigList) {
		Properties properties = new Properties();
		serviceConfigList.getNameValueList().forEach(setting -> {
			properties.setProperty(setting.getName(), setting.getValue());
		});
		applicationProps = new CustomProperties(properties);
		SyncPropertiesObject.loadSynchProperties(applicationProps);
		AsyncPropertiesObject.loadAsynchProperties(applicationProps);
		log.info("Application Properties Populated with these values :: " + applicationProps.getCustomProperties());
		updateCallbacks.forEach(Runnable::run);
	}

	public static void populateAPIPropertiesWithProto(ServicesConfigurationList serviceConfigList) {
		Properties properties = new Properties();
		serviceConfigList.getNameValueList().forEach(setting -> {
			properties.setProperty(setting.getName(), setting.getValue());
		});
		apiProperties = new CustomProperties(properties);
		AsyncPropertiesObject.loadApiProperties(apiProperties);
		log.info("API Properties Populated with these values :: " + apiProperties.getCustomProperties());
	}

	public static double getCreateTopicTps() {
		return AsyncPropertiesObject.getCreateTopicTps();
	}

	public static double getCreateTopicBurstPeriod() {
		return AsyncPropertiesObject.getCreateTopicBurstPeriod();
	}

	public static double getUpdateTopicTps() {
		return AsyncPropertiesObject.getUpdateTopicTps();
	}

	public static double getUpdateTopicBurstPeriod() {
		return AsyncPropertiesObject.getUpdateTopicBurstPeriod();
	}

	public static double getDeleteTopicTps() {
		return AsyncPropertiesObject.getDeleteTopicTps();
	}

	public static double getDeleteTopicBurstPeriod() {
		return AsyncPropertiesObject.getDeleteTopicBurstPeriod();
	}

	public static double getSubmitMessageTps() {
		return AsyncPropertiesObject.getSubmitMessageTps();
	}

	public static double getSubmitMessageBurstPeriod() {
		return AsyncPropertiesObject.getSubmitMessageBurstPeriod();
	}

	public static double getGetTopicInfoTps() {
		return AsyncPropertiesObject.getGetTopicInfoTps();
	}

	public static double getGetTopicInfoBurstPeriod() {
		return AsyncPropertiesObject.getGetTopicInfoBurstPeriod();
	}

	public static int getTransferAccountListSize() {
		return SyncPropertiesObject.getTransferListSizeLimit();
	}

	public static long getInitialGenesisCoins() {
		return SyncPropertiesObject.getInitialGenesisCoins();
	}

	public static long getDefaultContractDurationInSec() {
		return SyncPropertiesObject.getDefaultContractDurationSec();
	}

	public static int getKeyExpansionDepth() {
		return SyncPropertiesObject.getKeyExpansionDepth();
	}

	public static int getThresholdTxRecordTTL() {
		return SyncPropertiesObject.getThresholdTxRecordTTL();
	}

	public static int getThrottlingTps() {
		return AsyncPropertiesObject.getThrottlingTps();
	}

	public static int getSimpleTransferTps() {
		return AsyncPropertiesObject.getSimpletransferTps();
	}

	public static int getGetReceiptTps() {
		return AsyncPropertiesObject.getGetReceiptTps();
	}

	public static int getQueriesTps() {
		return AsyncPropertiesObject.getQueriesTps();
	}

	public static long getMinimumAutorenewDuration() {
		return SyncPropertiesObject.getMINIMUM_AUTORENEW_DURATION();
	}

	public static long getMaximumAutorenewDuration() {
		return SyncPropertiesObject.getMAXIMUM_AUTORENEW_DURATION();
	}

	public static long getRecordLogPeriod() {
		return AsyncPropertiesObject.getRecordLogPeriod();
	}

	public static String getRecordLogDir() {
		return AsyncPropertiesObject.getRecordLogDir();
	}

	public static boolean isAccountBalanceExportEnabled() {
		return AsyncPropertiesObject.isAccountBalanceExportEnabled();
	}

	public static String getAccountBalanceExportDir() {
		return AsyncPropertiesObject.getAccountBalanceExportDir();
	}

	public static long accountBalanceExportPeriodMinutes() {
		return AsyncPropertiesObject.accountBalanceExportPeriodMinutes();
	}

	public static long getNodeAccountBalanceValidity() {
		return SyncPropertiesObject.getNodeAccountBalanceValidity();
	}

	public static int getRecordStreamQueueCapacity() {
		return AsyncPropertiesObject.getRecordStreamQueueCapacity();
	}

	public static int getlocalCallEstReturnBytes() {
		return SyncPropertiesObject.getLocalCallEstReturnBytes();
	}

	public static int getExchangeRateAllowedPercentage() {
		return SyncPropertiesObject.getExchangeRateAllowedPercentage();
	}

	public static int getTxMinRemaining() {
		return SyncPropertiesObject.getTxMinRemaining();
	}

	public static int getTxMinDuration() {
		return SyncPropertiesObject.getTxMinDuration();
	}

	public static int getTxMaxDuration() {
		return SyncPropertiesObject.getTxMaxDuration();
	}

	public static int getMaxContractStateSize() {
		return SyncPropertiesObject.getMaxContractStateSize();
	}

	/**
	 * If Exchange_Rate_Allowed_Percentage in application.properties is invalid,
	 * i.e. <=0, we set it to be DEFAULT_EXCHANGE_RATE_ALLOWED_PERCENTAGE, and
	 * return false; else return true;
	 *
	 * @return
	 */
	public static boolean validExchangeRateAllowedPercentage() {
		return SyncPropertiesObject.validExchangeRateAllowedPercentage();
	}

	public static boolean isEnableRecordStreaming() {
		return AsyncPropertiesObject.isEnableRecordStreaming();
	}

	public static Map<String, PermissionedAccountsRange> getApiPermission() {
		return AsyncPropertiesObject.getApiPermission();
	}

	public static int getEnvironment() {
		return AsyncPropertiesObject.getEnvironment();
	}

	public static String getDefaultListeningNodeAccount() {
		return AsyncPropertiesObject.getDefaultListeningNodeAccount();
	}

	public static int getUniqueListeningPortFlag() {
		return AsyncPropertiesObject.getUniqueListeningPortFlag();
	}

	public static String getSaveAccounts() {
		return AsyncPropertiesObject.getSaveAccounts();
	}

	public static String getExportedAccountPath() {
		return AsyncPropertiesObject.getExportedAccountPath();
	}

	public static long getNettyKeepAliveTime() {
		return AsyncPropertiesObject.getNettyKeepAliveTime();
	}

	public static long getNettyKeepAliveTimeOut() {
		return AsyncPropertiesObject.getNettyKeepAliveTimeOut();
	}

	public static long getNettyMaxConnectionAge() {
		return AsyncPropertiesObject.getNettyMaxConnectionAge();
	}

	public static long getNettyMaxConnectionAgeGrace() {
		return AsyncPropertiesObject.getNettyMaxConnectionAgeGrace();
	}

	public static long getNettyMaxConnectionIdle() {
		return AsyncPropertiesObject.getNettyMaxConnectionIdle();
	}

	public static int getNettyMaxConcurrentCalls() {
		return AsyncPropertiesObject.getNettyMaxConcurrentCalls();
	}

	public static int getNettyFlowControlWindow() {
		return AsyncPropertiesObject.getNettyFlowControlWindow();
	}

	public static String getNettyMode() {
		return AsyncPropertiesObject.getNettyMode();
	}

	public static int getMaxGasLimit() {
		return SyncPropertiesObject.getMaxGasLimit();
	}

	public static boolean getStartStatsDumpTimer() {
		return AsyncPropertiesObject.getStartStatsDumpTimer();
	}

	public static int getStatsDumpTimerValue() {
		return AsyncPropertiesObject.getStatsDumpTimerValue();
	}

	public static int getBinaryObjectQueryRetryTimes() {
		return AsyncPropertiesObject.getBinaryObjectQueryRetryTimes();
	}
}
