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

	// Default values
	public static long DEFAULT_FEE = 100000l;

	public static String EXPORTED_ACCOUNT_PATH = "exportedAccountPath";
	public static long EXCHANGE_RATE_FILE_ACCOUNT_NUM = 112;
	public static long DEFAULT_FILE_SHARD = 0;
	public static long DEFAULT_FILE_REALM = 0;

	public static String NO = "NO";
	public static int ZERO = 0;
	public static char P = 'p';
	public static char N = 'n';
	public static char A = 'a';

	//Default Values for Configuration Properties
	public static int RECORD_LOG_PERIOD = 2;
	public static String RECORD_LOG_DIR = "data/recordstreams/";
	public static int RECORD_STREAM_QU_CAP = 500;

	// Estimates for calculating fees for Smart Contract local calls
	public static String DEFAULT_LISTENING_NODE_ACCT = "0.0.3";

	public static long KEEP_ALIVE_TIME = 20;
	public static long KEEP_ALIVE_TIMEOUT = 5;
	public static long MAX_CONNECTION_AGE = 30;
	public static long MAX_CONNECTION_AGE_GRACE = 5;
	public static long MAX_CONNECTION_IDLE = 10;
	public static int MAX_CONCURRENT_CALLS = 100;
	public static int NETTY_FLOW_CONTROL_WINDOW = 65535;

	public static int BINARY_OBJECT_QUERY_RETRY_TIMES = 0;
}
