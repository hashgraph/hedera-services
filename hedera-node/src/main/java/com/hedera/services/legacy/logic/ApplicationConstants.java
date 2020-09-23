package com.hedera.services.legacy.logic;

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

public class ApplicationConstants {
	public static String NETTY_MODE_DEV = "DEV";

	public static String LEDGER_PATH = "/{0}/";
	// Default values
	public static long DEFAULT_FEE = 100000l;

	// Signature Algorithm Type Constants
	public static String EXPORTED_ACCOUNT_PATH = "exportedAccountPath";
	public static long EXCHANGE_RATE_FILE_ACCOUNT_NUM = 112;
	public static long DEFAULT_FILE_SHARD = 0;
	public static long DEFAULT_FILE_REALM = 0;

	// File number used to do system update
	public static long UPDATE_FEATURE_FILE_ACCOUNT_NUM = 150;

	public static String NO = "NO";
	public static int ZERO = 0;
	public static char P = 'p';
	public static char N = 'n';
	public static char A = 'a';

	//Default Values for Configuration Properties
	public static int RECORD_LOG_PERIOD = 2;
	public static String RECORD_LOG_DIR = "data/recordstreams/";
	public static int RECORD_STREAM_QU_CAP = 500;
	public static String ACCT_BAL_EXP_DIR = "data/accountBalances/";
	public static int ACCOUNT_BALANCE_EXPORT_PERIOD_MINUTES = 10;

	public static int KEY_EXPANSION_DEPTH = 100;
	// Tx record & receipt ttl setting in seconds
	public static int THRESH_REC_TTL = 90000;
	public static int TX_MIN_DUR = 5;
	public static int TX_MAX_DUR = 180;
	public static int TXN_MIN_VALIDITY_BUFFER_SECS = 10;

	public static long INITIAL_GENESIS_COINS = 5000000000000000000l;

	public static long DEFAULT_CONTRACT_DURATION_IN_SEC = 7890000;

	public static long MINIMUM_AUTORENEW_DURATION = 1l;
	public static long MAXIMUM_AUTORENEW_DURATION = 1000000000l;

	public static int TRANSFER_LST_LIMIT = 10;

	// Estimates for calculating fees for Smart Contract local calls
	public static int LOCAL_CALLEST_RET_BYTES = 64;
	public static String DEFAULT_LISTENING_NODE_ACCT = "0.0.3";

	public static long KEEP_ALIVE_TIME = 20;
	public static long KEEP_ALIVE_TIMEOUT = 5;
	public static long MAX_CONNECTION_AGE = 30;
	public static long MAX_CONNECTION_AGE_GRACE = 5;
	public static long MAX_CONNECTION_IDLE = 10;
	public static int MAX_CONCURRENT_CALLS = 100;
	public static int NETTY_FLOW_CONTROL_WINDOW = 65535;

	public static int BINARY_OBJECT_QUERY_RETRY_TIMES = 0;
	public static int MAX_GAS_LIMIT = 300000;
}
