package com.hedera.services.legacy.keychange;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class provides utilites and tests for changing keys for gensis, node, and system accounts.
 *
 * @author Hua Li Created on 2018-12-11
 */
public class AccountReKey extends CryptoServiceTest {

	private static final Logger log = LogManager.getLogger(AccountReKey.class);
	private String prefix = "\n\n======>>>> ";

	public static void main(String[] args) throws Throwable {
		AccountReKey tester = new AccountReKey();
		tester.setUp();
		tester.changeGenesisKey();
		tester.changeNodeKeysTest(startUpKey);
		tester.changeSystemAccountKeysTest(startUpKey);
		tester.revertSystemAccountKeysTest(startUpKey);
		tester.revertNodeKeysTest(startUpKey);
		tester.revertGenesisKey();
	}

	/**
	 * Changes all node account keys.
	 *
	 * @param startUpKey
	 * 		the initial key used when system started up
	 */
	public void changeNodeKeysTest(Key startUpKey) throws Throwable {
		for (AccountID nodeAcc : nodeAccounts) {
			AccountID payerID = genesisAccountID;
			AccountID nodeID = defaultListeningNodeAccountID;
			log.info(prefix + "change key for node account " + nodeAcc);
			acc2ComplexKeyMap.put(nodeAcc, startUpKey);
			try {
				Key newKey = genComplexKey("thresholdKey");
				AccountInfo accInfo = updateAccountKey(nodeAcc, payerID, nodeID, newKey);
				log.info("change node key success :) updated account info = " + accInfo);
			} catch (Exception e) {
				log.warn("cryptoUpdate change node key error!", e);
			}
		}
	}

	/**
	 * Changes the system account keys.
	 *
	 * @param startUpKey
	 * 		the initial key used when system started up
	 */
	public void changeSystemAccountKeysTest(Key startUpKey) throws Throwable {
		int startCount = 2 + nodeAccounts.length + 1;
		for (int i = startCount; i <= 100; i++) {
			AccountID sysAccountID = AccountID.newBuilder().setAccountNum(i).setRealmNum(0l)
					.setShardNum(0l).build();
			log.info(prefix + "change key for system account " + sysAccountID);
			AccountID payerID = genesisAccountID;
			AccountID nodeID = defaultListeningNodeAccountID;
			acc2ComplexKeyMap.put(sysAccountID, startUpKey);
			try {
				Key newKey = genComplexKey("thresholdKey");
				AccountInfo accInfo = updateAccountKey(sysAccountID, payerID, nodeID, newKey);
				log.info("change system key success :) updated account info = " + accInfo);
			} catch (Exception e) {
				log.warn("cryptoUpdate change system key error!", e);
			}
		}
	}

	/**
	 * Reverts all node account keys to start up key.
	 *
	 * @param startUpKey
	 * 		the initial key used when system started up
	 */
	public void revertNodeKeysTest(Key startUpKey) throws Throwable {
		for (AccountID nodeAcc : nodeAccounts) {
			AccountID payerID = genesisAccountID;
			AccountID nodeID = defaultListeningNodeAccountID;
			log.info(prefix + "revert key for node account " + nodeAcc);
			try {
				AccountInfo accInfo = updateAccountKey(nodeAcc, payerID, nodeID, startUpKey);
				log.info("revert node key success :) updated account info = " + accInfo);
			} catch (Exception e) {
				log.warn("cryptoUpdate revert node key error!", e);
			}
		}
	}

	/**
	 * Reverts the system account keys.
	 *
	 * @param startUpKey
	 * 		the initial key used when system started up
	 */
	public void revertSystemAccountKeysTest(Key startUpKey) throws Throwable {
		int startCount = 2 + nodeAccounts.length + 1;
		for (int i = startCount; i <= 100; i++) {
			AccountID sysAccountID = AccountID.newBuilder().setAccountNum(i).setRealmNum(0l)
					.setShardNum(0l).build();
			log.info(prefix + "revert key for system account " + sysAccountID);
			AccountID payerID = genesisAccountID;
			AccountID nodeID = defaultListeningNodeAccountID;
			try {
				AccountInfo accInfo = updateAccountKey(sysAccountID, payerID, nodeID, startUpKey);
				log.info("revert system key success :) updated account info = " + accInfo);
			} catch (Exception e) {
				log.warn("cryptoUpdate revert system key error!", e);
			}

		}
	}

}
