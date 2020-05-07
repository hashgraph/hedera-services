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

import java.util.HashMap;
import java.util.Map;

import com.hedera.services.context.domain.security.PermissionedAccountsRange;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.logic.CustomProperties;

/**
 * This class reads and stores values from property file which may not be Synchronous
 * i.e. the value, which are assumed to be refreshed once handleTrsnaction method is invoked , but
 * they can be different on different Nodes.
 *
 */
public class AsyncPropertiesObject {
	/* ---- HCS Throttling ---- */
	private static double createTopicTps, createTopicBurstPeriod;
	private static double updateTopicTps, updateTopicBurstPeriod;
	private static double deleteTopicTps, deleteTopicBurstPeriod;
	private static double submitMessageTps, submitMessageBurstPeriod;
	private static double getTopicInfoTps, getTopicInfoBurstPeriod;

	// throttling properties - Default values are zero
	private static int throttlingTps;
	private static int simpletransferTps;
	private static int getReceiptTps;
	private static int queriesTps;
	
	// properties for RecordStream
	private static boolean enableRecordStreaming;
	private static long recordLogPeriod = ApplicationConstants.RECORD_LOG_PERIOD;
	private static String recordLogDir = ApplicationConstants.RECORD_LOG_DIR;
	private static int recordStreamQueueCapacity = ApplicationConstants.RECORD_STREAM_QU_CAP;
	
	// properties for Export of Accounts Balance
	private static boolean accountBalanceExportEnabled ;
	private static String accountBalanceExportDir = ApplicationConstants.ACCT_BAL_EXP_DIR;
	private static long accountBalanceExportPeriodMinutes = ApplicationConstants.ACCOUNT_BALANCE_EXPORT_PERIOD_MINUTES;
	
	// path for hedera startup
	private static String hederaStartupPath = ApplicationConstants.HED_START_PATH;
	// config property for account creation
	private static String initializeHederaLedgerFlag = ApplicationConstants.INITIALIZE_HEDERA_LEDGER_FLAG;
	// node account creation properties
	private static String genesisAccountPath = ApplicationConstants.GEN_ACCOUNT_PATH;
	private static String genesisPubKeyPath = ApplicationConstants.GEN_PUB_KEY_PATH;
	private static String genesisPrivKeyPath = ApplicationConstants.GEN_PRIV_KEY_PATH;
	private static String genesisPubKey32BytePath = ApplicationConstants.GEN_PUB_KEY_32BYTE_PATH;
	
	// system file creation node txt
	private static String addressBook ;
	
	// Server Properties
	private static int port = ApplicationConstants.APP_PORT;
	private static int tlsPort = ApplicationConstants.APP_TLS_PORT;
	private static int environment;
	private static String defaultListeningNodeAccount = ApplicationConstants.DEFAULT_LISTENING_NODE_ACCT;
	private static int uniqueListeningPortFlag;
	
	// Save Accounts on Startup
	private static String saveAccounts = ApplicationConstants.NO;
	private static String exportedAccountPath = ApplicationConstants.EXPORTED_ACCOUNT_PATH;
	
	// Netty Server Properties
	private static long nettyKeepAliveTime = ApplicationConstants.KEEP_ALIVE_TIME;
	private static long nettyKeepAliveTimeOut = ApplicationConstants.KEEP_ALIVE_TIMEOUT;
	private static long nettyMaxConnectionAge = ApplicationConstants.MAX_CONNECTION_AGE;
	private static long nettyMaxConnectionAgeGrace = ApplicationConstants.MAX_CONNECTION_AGE_GRACE;
	private static long nettyMaxConnectionIdle = ApplicationConstants.MAX_CONNECTION_IDLE;
	private static int nettyMaxConcurrentCalls = ApplicationConstants.MAX_CONCURRENT_CALLS;
	private static String nettyMode = ApplicationConstants.NETTY_MODE_DEV;
	private static int nettyFlowControlWindow = ApplicationConstants.NETTY_FLOW_CONTROL_WINDOW;
	private static String skipExitOnStartupFailures = ApplicationConstants.NO;
	private static Map<String, PermissionedAccountsRange> apiPermission = new HashMap<String, PermissionedAccountsRange>();
	public static void loadAsynchProperties(CustomProperties appConfig) {
		// Server properties
		port = appConfig.getInt("port", ApplicationConstants.APP_PORT);
		tlsPort = appConfig.getInt("tlsPort", ApplicationConstants.APP_TLS_PORT);
		environment = appConfig.getInt("environment", ApplicationConstants.ZERO);
		defaultListeningNodeAccount = appConfig.getString("defaultListeningNodeAccount",ApplicationConstants.DEFAULT_LISTENING_NODE_ACCT);
		uniqueListeningPortFlag = appConfig.getInt("uniqueListeningPortFlag", ApplicationConstants.ZERO);
		
		 nettyKeepAliveTime = appConfig.getLong("nettyKeepAliveTime",ApplicationConstants.KEEP_ALIVE_TIME) ;
		 nettyKeepAliveTimeOut = appConfig.getLong("nettyKeepAliveTimeOut",ApplicationConstants.KEEP_ALIVE_TIMEOUT) ;
		 nettyMaxConnectionAge = appConfig.getLong("maxConnectionAge",ApplicationConstants.MAX_CONNECTION_AGE) ;
		 nettyMaxConnectionAgeGrace = appConfig.getLong("maxConnectionAgeGrace",ApplicationConstants.MAX_CONNECTION_AGE_GRACE) ;
		 nettyMaxConnectionIdle = appConfig.getLong("maxConnectionIdle",ApplicationConstants.MAX_CONNECTION_AGE_GRACE) ;
		 nettyMaxConcurrentCalls = appConfig.getInt("maxConcurrentCalls",ApplicationConstants.MAX_CONCURRENT_CALLS) ;
         nettyFlowControlWindow = appConfig.getInt("nettyFlowControlWindow",ApplicationConstants.NETTY_FLOW_CONTROL_WINDOW) ;
         nettyMode = appConfig.getString("nettyMode",ApplicationConstants.NETTY_MODE_DEV);
		
		saveAccounts = appConfig.getString("saveAccounts", ApplicationConstants.NO);
		exportedAccountPath = appConfig.getString("exportedAccountPath", ApplicationConstants.EXPORTED_ACCOUNT_PATH);

		/* ---- HCS Throttling ---- */
		createTopicTps = appConfig.getDouble("throttling.hcs.createTopic.tps", 1000.0);
		createTopicBurstPeriod = appConfig.getDouble("throttling.hcs.createTopic.burstPeriod", 1.0);
		updateTopicTps = appConfig.getDouble("throttling.hcs.updateTopic.tps", 1000.0);
		updateTopicBurstPeriod = appConfig.getDouble("throttling.hcs.updateTopic.burstPeriod", 1.0);
		deleteTopicTps = appConfig.getDouble("throttling.hcs.deleteTopic.tps", 1000.0);
		deleteTopicBurstPeriod = appConfig.getDouble("throttling.hcs.deleteTopic.burstPeriod", 1.0);
		submitMessageTps = appConfig.getDouble("throttling.hcs.submitMessage.tps", 1000.0);
		submitMessageBurstPeriod = appConfig.getDouble("throttling.hcs.submitMessage.burstPeriod", 1.0);
		getTopicInfoTps = appConfig.getDouble("throttling.hcs.getTopicInfo.tps", 1000.0);
		getTopicInfoBurstPeriod = appConfig.getDouble("throttling.hcs.getTopicInfo.burstPeriod", 1.0);

		// throttling properties
		throttlingTps = appConfig.getInt("throttlingTps", ApplicationConstants.ZERO);
		simpletransferTps = appConfig.getInt("simpletransferTps", ApplicationConstants.ZERO);
		getReceiptTps = appConfig.getInt("getReceiptTps", ApplicationConstants.ZERO);
		queriesTps = appConfig.getInt("queriesTps", ApplicationConstants.ZERO);
		// properties for RecordStream
		 enableRecordStreaming = appConfig.getBoolean("enableRecordStreaming", false);
		 recordLogPeriod = appConfig.getLong("recordLogPeriod", ApplicationConstants.RECORD_LOG_PERIOD);
		 recordLogDir = appConfig.getString("recordLogDir", ApplicationConstants.RECORD_LOG_DIR);
		 recordStreamQueueCapacity = appConfig.getInt("recordStreamQueueCapacity", ApplicationConstants.RECORD_STREAM_QU_CAP);

		// properties for Export of Accounts Balance
		accountBalanceExportEnabled = appConfig.getBoolean("accountBalanceExportEnabled",false);

		 accountBalanceExportDir = appConfig.getString("accountBalanceExportDir",
				 ApplicationConstants.ACCT_BAL_EXP_DIR);

		 accountBalanceExportPeriodMinutes = appConfig
				.getLong("accountBalanceExportPeriodMinutes", ApplicationConstants.ACCOUNT_BALANCE_EXPORT_PERIOD_MINUTES);

		// path for hedera startup
		 hederaStartupPath = appConfig.getString("hederaStartupPath",
				 ApplicationConstants.HED_START_PATH);
		// config property for account creation
		 initializeHederaLedgerFlag = appConfig.getString("initializeHederaLedgerFlag",
				 ApplicationConstants.INITIALIZE_HEDERA_LEDGER_FLAG);
		// node account creation properties
		 genesisAccountPath = appConfig.getString("genesisAccountPath",
				 ApplicationConstants.GEN_ACCOUNT_PATH);
		 genesisPubKeyPath = appConfig.getString("genesisPubKeyPath",
				 ApplicationConstants.GEN_PUB_KEY_PATH);
		 genesisPrivKeyPath = appConfig.getString("genesisPrivKeyPath",
				 ApplicationConstants.GEN_PRIV_KEY_PATH);
		 genesisPubKey32BytePath = appConfig.getString("genesisPubKey32BytePath",
				 ApplicationConstants.GEN_PUB_KEY_32BYTE_PATH);	
		// system file creation node txt
		 addressBook = appConfig.getString("addressBook", ApplicationConstants.ADDRESS_BOOK);
		//skipping system exit on startup validations 
		 skipExitOnStartupFailures =  appConfig.getString("skipExitOnStartupFailures", ApplicationConstants.NO);
	}
	
	
	public static void loadApiProperties(CustomProperties apiPermissionProp) {
		apiPermission.clear();
		apiPermissionProp.getCustomProperties().forEach((key, value) ->
				  apiPermission.put(String.valueOf(key),
				  PermissionedAccountsRange.from(value.toString())));
	}

	static Map<String , PermissionedAccountsRange> getApiPermission(){
		return apiPermission;
	}

	public static double getCreateTopicTps() {
		return createTopicTps;
	}

	public static double getCreateTopicBurstPeriod() {
		return createTopicBurstPeriod;
	}

	public static double getUpdateTopicTps() {
		return updateTopicTps;
	}

	public static double getUpdateTopicBurstPeriod() {
		return updateTopicBurstPeriod;
	}

	public static double getDeleteTopicTps() {
		return deleteTopicTps;
	}

	public static double getDeleteTopicBurstPeriod() {
		return deleteTopicBurstPeriod;
	}

	public static double getSubmitMessageTps() {
		return submitMessageTps;
	}

	public static double getSubmitMessageBurstPeriod() {
		return submitMessageBurstPeriod;
	}

	public static double getGetTopicInfoTps() {
		return getTopicInfoTps;
	}

	public static double getGetTopicInfoBurstPeriod() {
		return getTopicInfoBurstPeriod;
	}

	static int getThrottlingTps() {
		return throttlingTps;
	}

	static int getSimpletransferTps() {
		return simpletransferTps;
	}

	static int getGetReceiptTps() {
		return getReceiptTps;
	}

	static int getQueriesTps() {
		return queriesTps;
	}

	static boolean isEnableRecordStreaming() {
		return enableRecordStreaming;
	}

	static long getRecordLogPeriod() {
		return recordLogPeriod;
	}

	static String getRecordLogDir() {
		return recordLogDir;
	}

	static int getRecordStreamQueueCapacity() {
		return recordStreamQueueCapacity;
	}

	static boolean isAccountBalanceExportEnabled() {
		return accountBalanceExportEnabled;
	}

	static String getAccountBalanceExportDir() {
		return accountBalanceExportDir;
	}

	static long accountBalanceExportPeriodMinutes() {
		return accountBalanceExportPeriodMinutes;
	}

	static String getHederaStartupPath() {
		return hederaStartupPath;
	}

	static String getInitializeHederaLedgerFlag() {
		return initializeHederaLedgerFlag;
	}

	static String getGenesisAccountPath() {
		return genesisAccountPath;
	}

	static String getGEN_PUB_KEY_PATH() {
		return genesisPubKeyPath;
	}

	static String getGenesisPrivKeyPath() {
		return genesisPrivKeyPath;
	}

	static String getGenesisPubKey32BytePath() {
		return genesisPubKey32BytePath;
	}

	static String getAddressBook() {
		return addressBook;
	}
	
	static int getPort() {
		return port;
	}

	static int getTlsPort() {
		return tlsPort;
	}

	static int getEnvironment() {
		return environment;
	}

	static String getDefaultListeningNodeAccount() {
		return defaultListeningNodeAccount;
	}

	static int getUniqueListeningPortFlag() {
		return uniqueListeningPortFlag;
	}

	static String getSaveAccounts() {
		return saveAccounts;
	}

	static String getExportedAccountPath() {
		return exportedAccountPath;
	}

	 static long getNettyKeepAliveTime() {
		return nettyKeepAliveTime;
	}

	 static long getNettyKeepAliveTimeOut() {
		return nettyKeepAliveTimeOut;
	}

	 static long getNettyMaxConnectionAge() {
		return nettyMaxConnectionAge;
	}

	 static long getNettyMaxConnectionAgeGrace() {
		return nettyMaxConnectionAgeGrace;
	}

	 static long getNettyMaxConnectionIdle() {
		return nettyMaxConnectionIdle;
	}

	 static int getNettyMaxConcurrentCalls() {
		return nettyMaxConcurrentCalls;
	}

	 static int getNettyFlowControlWindow() {
		return nettyFlowControlWindow;
	}
	
	 static String getNettyMode() {
		return nettyMode;
	}
	 
	 static String getSkipExitOnStartupFailures() {
	   return skipExitOnStartupFailures;
	 }

}
