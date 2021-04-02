package com.hedera.services.legacy.config;

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

import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.legacy.logic.CustomProperties;

/**
 * This class reads and stores values from property file which may not be Synchronous
 * i.e. the value, which are assumed to be refreshed once handleTrsnaction method is invoked , but
 * they can be different on different Nodes.
 *
 */
public class AsyncPropertiesObject {
	// Server Properties
	private static String defaultListeningNodeAccount = ApplicationConstants.DEFAULT_LISTENING_NODE_ACCT;
	private static int uniqueListeningPortFlag;
	
	// Save Accounts on Startup
	private static String saveAccounts = ApplicationConstants.NO;
	private static String exportedAccountPath = ApplicationConstants.EXPORTED_ACCOUNT_PATH;
	
	public static void loadAsynchProperties(CustomProperties appConfig) {
		// Server properties
		defaultListeningNodeAccount = appConfig.getString("defaultListeningNodeAccount",ApplicationConstants.DEFAULT_LISTENING_NODE_ACCT);
		uniqueListeningPortFlag = appConfig.getInt("uniqueListeningPortFlag", ApplicationConstants.ZERO);
		saveAccounts = appConfig.getString("saveAccounts", ApplicationConstants.NO);
		exportedAccountPath = appConfig.getString("exportedAccountPath", ApplicationConstants.EXPORTED_ACCOUNT_PATH);
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
}
