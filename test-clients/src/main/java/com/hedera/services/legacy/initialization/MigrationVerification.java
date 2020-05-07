package com.hedera.services.legacy.initialization;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.HexUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 1. Verify updated ExpirationTime of each account
 * 2. Verify MapValues of accounts which are created during migration
 *
 * @author Anurag
 */
public class MigrationVerification {
	private static final Logger log = LogManager.getLogger(MigrationVerification.class);

	// The file is needed for loading default key to validate account's key which are created during migration; and for signing transactions for testing after migration
	private static final String startUpAccountFilePath = "../HapiApp2.0/data/onboard/StartUpAccount.txt";

	public static void main(String[] args) {
		File updatedCsv = null;
		File createdCsv = null;

		if (args.length == 0) {
			updatedCsv = new File("../HapiApp2.0/data/onboard/afterMigration_updated.csv");
			createdCsv = new File("../HapiApp2.0/data/onboard/afterMigration_created.csv");
		} else if (args.length == 2) {
			updatedCsv = new File(args[0]);
			createdCsv = new File(args[1]);
		} else {
			log.info("input args should contain two string, the first string should be the path of afterMigration_updated.csv, and the second string should be the path of  afterMigration_created.csv");
			System.exit(1);
		}

		if (verifyUpdatedMapValue(updatedCsv)) {
			log.info(updatedCsv.getName() + " is valid");
		} else {
			log.info(updatedCsv.getName() + " is invalid");
			System.exit(1);
		}

		if (verifyCreatedMapValue(createdCsv)) {
			log.info(createdCsv.getName() + " is valid");
		} else {
			log.info(createdCsv.getName() + " is invalid");
			System.exit(1);
		}
	}

	static boolean verifyUpdatedMapValue(File updatedCsv) {
		try (BufferedReader br = new BufferedReader(new FileReader(updatedCsv))) {
			String line;
			//read the first line
			if ((line = br.readLine()) == null) {
				log.info(updatedCsv.getName() + " is empty");
				return false;
			}
			int lineNum = 1;
			assert line.equals("AccountID, ExpirationTime");
			//read each account's accountNum and ExpirationTime
			while ((line = br.readLine()) != null) {
				// process the line
				String[] strs = line.split(",");
				if (strs.length != 2) {
					log.info(String.format("line #%s in %s has invalid format: %s", lineNum, updatedCsv.getName(), line));
					return false;
				}
				long accountNum = Long.parseLong(strs[0]);
				long expTime = Long.parseLong(strs[1]);
				long expectedExpTime = getExpirationTime(accountNum);
				// check expirationTime
				if (expTime != expectedExpTime) {
					log.info(String.format("line #%s in %s has invalid expTime: %s, expTime should be %s", lineNum, updatedCsv.getName(), line, expectedExpTime));
					return false;
				}
			}
			return true;
		} catch (FileNotFoundException ex) {
			log.info(updatedCsv.getAbsolutePath() + " is not found.");
			return false;
		} catch (IOException ex) {
			log.info("MigrationVerification :: verifyUpdatedMapValue : " + ex.getStackTrace());
			return false;
		}
	}

	static boolean verifyCreatedMapValue(File createdCsv) {
		try (BufferedReader br = new BufferedReader(new FileReader(createdCsv))) {
			String line;
			//read the first line
			if ((line = br.readLine()) == null) {
				log.info(createdCsv.getName() + " is empty");
				return false;
			}
			int lineNum = 1;
			assert line.equals("AccountID, Balance, ReceiverThreshold, SenderThreshold, ReceiverSigRequired, AccountKeys, ExpirationTime");
			long expectedAccountNum = 900;
			long expectedBalance = 0;
			long expectedReceiverThreshold = 50_000_000_000__00_000_000l;
			long expectedSenderThreshold = 50_000_000_000__00_000_000l;
			boolean expectedReceiverSigRequired = false;
			String defaultKeyString = getDefaultKeyString();
			//read each account's info
			while ((line = br.readLine()) != null) {
				// process the line
				String[] strs = line.split(",");
				if (strs.length != 7) {
					log.info(String.format("line #%s in %s has invalid format: %s", lineNum, createdCsv.getName(), line));
					return false;
				}

				long accountNum = Long.parseLong(strs[0]);
				if (!verifyValue(accountNum, expectedAccountNum, "AccountNum", createdCsv.getName(), accountNum)) {
					return false;
				}
				expectedAccountNum++;

				long balance = Long.parseLong(strs[1]);
				if (!verifyValue(balance, expectedBalance, "Balance", createdCsv.getName(), accountNum)) {
					return false;
				}

				long receiverThreshold = Long.parseLong(strs[2]);
				if (!verifyValue(receiverThreshold, expectedReceiverThreshold, "ReceiverThreshold", createdCsv.getName(), accountNum)) {
					return false;
				}

				long senderThreshold = Long.parseLong(strs[3]);
				if (!verifyValue(senderThreshold, expectedSenderThreshold, "SenderThreshold", createdCsv.getName(), accountNum)) {
					return false;
				}

				boolean receiverSigRequired = Boolean.parseBoolean(strs[4]);
				if (!verifyValue(receiverSigRequired, expectedReceiverSigRequired, "ReceiverSigRequired", createdCsv.getName(), accountNum)) {
					return false;
				}

				String accountKeys = strs[5];
				if (!verifyValue(accountKeys, defaultKeyString, "AccountKeys", createdCsv.getName(), accountNum)) {
					return false;
				}

				long expTime = Long.parseLong(strs[6]);
				long expectedExpTime = getExpirationTime(accountNum);
				// check expirationTime
				if (!verifyValue(expTime, expectedExpTime, "ExpirationTime", createdCsv.getName(), accountNum)) {
					return false;
				}
			}
			return true;
		} catch (FileNotFoundException ex) {
			log.info(createdCsv.getAbsolutePath() + " is not found.");
		} catch (IOException ex) {
			log.info("MigrationVerification :: verifyCreatedMapValue : " + ex.getStackTrace());
		}
		return false;
	}

	/**
	 * Get expiration value of migrated or created account
	 * accounts below or equal to 1000 - expiration is Long.MAX_VALUE
	 * accounts above 1000 :
	 *    1001 to expire on Nov 1st 2019 00:01
	 *    1002 to expire on Nov 1st 2019 00:02
	 *    ...
	 * @param accountNum
	 * @return
	 */
	public static long getExpirationTime(long accountNum) {
		if (accountNum <= 1000) {
			return Long.MAX_VALUE;
		} else {
			Instant instant = Instant.parse("2019-11-01T00:00:00Z");
			return instant.plus(java.time.Duration.ofMinutes(accountNum - 1000)).getEpochSecond();
		}
	}

	static String getDefaultKeyString() {
		GenesisCreateAndTransfer genesisCreateAndTransfer = new GenesisCreateAndTransfer();
		try {
			Map<String, List<AccountKeyListObj>> accountMap = genesisCreateAndTransfer.getStartupAccountMap(startUpAccountFilePath);

			List<AccountKeyListObj> startAccountList = accountMap.get("START_ACCOUNT");

			// get the Public Key
			String publicKeyStartAcct =
					startAccountList.get(0).getKeyPairList().get(0).getPublicKeyAbyteStr();

			Key key = Key.newBuilder()
					.setKeyList(KeyList.newBuilder()
							.addKeys(Key.newBuilder()
									.setEd25519(ByteString.copyFrom(HexUtils.hexToBytes(publicKeyStartAcct))).build())
							.build())
					.build();
			return TextFormat.shortDebugString(key);
		} catch (Exception ex) {
			log.info("MigrationVerification :: getDefaultKeyString : fail to read default key. " + ex.getStackTrace());
		}
		return null;
	}

	static boolean verifyValue(Object actual, Object expected, String fieldName, String fileName, long accountNum) {
		if (actual.equals(expected)) {
			return true;
		} else {
			log.info(String.format("Account #%s in %s has invalid %s. Actual value: %s, Expected value: %s", accountNum, fileName, fieldName, actual, expected));
			return false;
		}
	}
}
