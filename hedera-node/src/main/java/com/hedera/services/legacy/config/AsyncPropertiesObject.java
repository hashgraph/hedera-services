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
	// properties for RecordStream
	private static boolean enableRecordStreaming;
	private static long recordLogPeriod = ApplicationConstants.RECORD_LOG_PERIOD;
	private static String recordLogDir = ApplicationConstants.RECORD_LOG_DIR;
	private static int recordStreamQueueCapacity = ApplicationConstants.RECORD_STREAM_QU_CAP;

	// Server Properties
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
	private static Map<String, PermissionedAccountsRange> apiPermission = new HashMap<>();

	// Re-try times for querying binary object store (for file services)
	private static int binaryObjectQueryRetryTimes = ApplicationConstants.BINARY_OBJECT_QUERY_RETRY_TIMES;

	public static void loadAsynchProperties(CustomProperties appConfig) {
		// Server properties
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

		// properties for RecordStream
		 enableRecordStreaming = appConfig.getBoolean("enableRecordStreaming", false);
		 recordLogPeriod = appConfig.getLong("recordLogPeriod", ApplicationConstants.RECORD_LOG_PERIOD);
		 recordLogDir = appConfig.getString("recordLogDir", ApplicationConstants.RECORD_LOG_DIR);
		 recordStreamQueueCapacity = appConfig.getInt("recordStreamQueueCapacity", ApplicationConstants.RECORD_STREAM_QU_CAP);

		// Re-try times for querying binary object store (for file services)
		binaryObjectQueryRetryTimes = appConfig.getInt("binary.object.query.retry.times", 3);
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
	 
	static int getBinaryObjectQueryRetryTimes() {
		return binaryObjectQueryRetryTimes;
	}
}
