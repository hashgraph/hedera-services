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

import java.text.MessageFormat;
import java.util.MissingResourceException;

public class ApplicationConstants {
  public static final String PROPERTY_FILE = "data/config/application.properties";
  public static final String API_ACCESS_FILE = "data/config/api-permission.properties";
  public static final String INSUFFICIENT_BAL = "INSUFFICIENT_BALANCE";
  public static final String INSUFFICIENT_FEE = "INSUFFICIENT_FEE";
  public static final String PAYER_ACCOUNT_NOT_FOUND = "PAYER_ACCOUNT_NOT_FOUND";
  public static final String BAD_ENCODING = "BAD_ENCODING";
  public static final String KEY_REQUIRED = "KEY_REQUIRED";
  public static final String INSUFFICIENT_PAYER_BALANCE = "INSUFFICIENT_PAYER_BALANCE";
  public static final String INSUFFICIENT_ACCOUNT_BALANCE = "INSUFFICIENT_ACCOUNT_BALANCE";
  public static final String ACCOUNT_ETHADDRESS_NOT_FOUND = "ACCOUNT_ETHADDRESS_NOT_FOUND";
  public static final String ACCOUNT_NOT_FOUND = "ACCOUNT_NOT_FOUND";

  public static final  long APPLICATION_PROPERTIES_FILE_NUM = 121;
  public static final long API_PROPERTIES_FILE_NUM = 122;
  public static String NETTY_MODE_DEV = "DEV";

  // FSFC path constants
  public static String SYSTEM_TEMP_FILE_PATH = "/{0}/e{1}";
  public static String ADDRESS_PATH = "/{0}/s{1}";
  public static String ADDRESS_APENDED_PATH = "/{0}/d{1}";
  public static String LEDGER_PATH = "/{0}/";
  // Default values
  public static int APP_PORT = 50211;
  public static int APP_TLS_PORT = 50212;
  public static long DEFAULT_FEE = 100000l;
  public static int DEFAULT_EXCHANGE_RATE_ALLOWED_PERCENTAGE = 1;

  // Signature Algorithm Type Constants
  public static String GENESIS_ACCOUNT = "GENESIS_ACCOUNT";
  public static String INITIAL_ACCOUNTS = "INITIAL_ACCOUNTS";
  public static String START_ACCOUNT = "START_ACCOUNT";
  public static String EXPORTED_ACCOUNT_PATH = "exportedAccountPath";
  public static String FEE_FILE_PATH = "FeeSchedule.json";
  public static long FEE_FILE_ACCOUNT_NUM = 111;
  public static long ADDRESS_FILE_ACCOUNT_NUM = 101;
  public static long EXCHANGE_RATE_FILE_ACCOUNT_NUM = 112;
  public static long NODE_DETAILS_FILE = 102;
  public static long DEFAULT_FILE_SHARD = 0;
  public static long DEFAULT_FILE_REALM = 0;
  public static long HEDERA_START_SEQUENCE = 1001;

  // Constants related to protected entities
  public static final long MASTER_CONTROL_RANGE_MAX_NUM = 80;
  public static final long MASTER_CONTROL_RANGE_MIN_NUM = 50;
  public static final long ADDRESS_ACC_NUM = 55;
  public static final long FEE_ACC_NUM = 56;
  public static final long EXCHANGE_ACC_NUM = 57;
  public static final long FREEZE_ACC_NUM = 58;
  public static final long SYSTEM_DELETE_ACC_NUM = 59;
  public static final long SYSTEM_UNDELETE_ACC_NUM = 60;
  
  /**
   * Default value for the prefix for the virtual content file
   */
  public static String ARTIFACTS_PREFIX_FILE_CONTENT = "f";

  /**
   * Default value for the prefix for the virtual metadata file
   */
  public static String ARTIFACTS_PREFIX_FILE_INFO = "k";
  public static int CLAIM_HASH_SIZE = 48;
  public static long DEFAULT_SHARD = 0l;

  /** Default NO */
  public static String NO = "NO";
  /** Default YES */
  public static String YES = "YES";
  
  public static int ZERO = 0;
  /**
   * constant char used to serialize and deserialize empty objects
   */
  public static char P = 'p';

  /**
   * constant char used to serialize and deserialize empty objects that is null
   */
  public static char N = 'n';
  public static char A = 'a';

  public static String buildPath(String path, Object... params) {
    try {
      return MessageFormat.format(path, params);
    } catch (final MissingResourceException e) {
      e.printStackTrace();
    }
    return path;
  }
  
//Default Values for Configuration Properties
	public static int RECORD_LOG_PERIOD = 60;
	public static String RECORD_LOG_DIR = "data/recordstreams/";
	public static int RECORD_STREAM_QU_CAP = 500;
	public static String ACCT_BAL_EXP_DIR = "data/accountBalances/";
	public static int ACCOUNT_BALANCE_EXPORT_PERIOD_MINUTES = 10;
	public static String HED_START_PATH = "data/onboard/StartUpAccount.txt";
	public static String INITIALIZE_HEDERA_LEDGER_FLAG = "NO";
	public static String GEN_ACCOUNT_PATH = "data/onboard/StartUpAccount.txt";
	public static String GEN_PUB_KEY_PATH = "data/onboard/GenesisPubKey.txt";
	public static String GEN_PRIV_KEY_PATH = "data/onboard/GenesisPrivKey.txt";
	public static String GEN_PUB_KEY_32BYTE_PATH = "data/onboard/GenesisPub32Key.txt";
	public static String ADDRESS_BOOK = "data/onboard/addressBook.txt";

	public static int KEY_EXPANSION_DEPTH = 100;
	// Tx record & receipt ttl setting in seconds
	public static int TX_RECEIPT_TTL = 180;
	public static int THRESH_REC_TTL = 90000;
	public static int TX_MIN_DUR = 5;
	public static int TX_MAX_DUR =  180;
	public static int TXN_MIN_VALIDITY_BUFFER_SECS = 10;

	// currentTime(// 08/21/2018 10.00am) and expiryTime(// 100 years from
	// 08/21/2018)
	public static long CURRENT_TIME = 1534861917l;
	public static long EXPIRY_TIME = 4688462211l;
	public static int CURRENT_HBAR_EQ = 1;
	public static int CURRENT_CENT_EQ = 12;

	public static long INITIAL_GENESIS_COINS = 5000000000000000000l;

	public static String DEFAULT_FEE_COLLECTION_ACCOUNT = "0.0.98";
	public static long DEFAULT_CONTRACT_DURATION_IN_SEC = 7890000;

	public static long MINIMUM_AUTORENEW_DURATION = 1l;
	public static long MAXIMUM_AUTORENEW_DURATION = 1000000000l;

	public static int TRANSFER_LST_LIMIT = 10;

	public static long DEF_CONT_SEND_THRESHOLD = 1000000000000000000L;
	public static long DEF_CONT_RECEIVE_THRESHOLD = 1000000000000000000L;

	public static int GEN_ACCT_NUM = 2;
	public static int MASTER_ACCT_NUM = 50;
	public static int PROTECT_ENT_MAX_NUM = 1000;
	public static int PROTECT_ENT_MIN_NUM = 1;

	public static long CONFIG_ACCT_NUM = 100_000_000;

	// Estimates for calculating fees for Smart Contract local calls
	public static int LOCAL_CALLEST_RET_BYTES = 64;
	public static int MAX_CONTRACT_STATE_SIZE = 1024;
	public static int MAX_FILE_SIZE = 1024;
	public static String DEFAULT_LISTENING_NODE_ACCT = "0.0.3";
	
	public static long KEEP_ALIVE_TIME = 20;
	public static long KEEP_ALIVE_TIMEOUT = 5;
	public static long MAX_CONNECTION_AGE = 30;
	public static long MAX_CONNECTION_AGE_GRACE = 5;
	public static long MAX_CONNECTION_IDLE = 10;
	public static int MAX_CONCURRENT_CALLS = 100;
	public static int NETTY_FLOW_CONTROL_WINDOW = 65535;
	
	public static int MAX_GAS_LIMIT = 300000;
}
