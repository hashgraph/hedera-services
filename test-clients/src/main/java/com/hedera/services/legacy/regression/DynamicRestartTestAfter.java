package com.hedera.services.legacy.regression;

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

import com.hederahashgraph.api.proto.java.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.*;

/**
 * Adds freeze related functions for regression tests.
 * Perform After Restart Tests by fetching state of Crypto , File and Smartcontracts refer DynamicRestartTest and
 * DynamicRestartBefore
 *
 * @author Tirupathi Mandala Created on 2019-08-07
 */
public class DynamicRestartTestAfter extends DynamicRestartTest {

	private static final Logger log = LogManager.getLogger(DynamicRestartTestAfter.class);

	public DynamicRestartTestAfter() {
	}

	public static void main(String[] args) throws Throwable {
		if (args.length > 0) {
			host = args[0];
		}
		DynamicRestartTestAfter test = new DynamicRestartTestAfter();
		test.setUp();

		accountInfoMap = (Map<AccountID, CryptoGetInfoResponse.AccountInfo>) readFromFile("crypto_account_map.txt");
		accountInfoMap.keySet().stream().forEach(a -> {
			//Validate after Freeze service completed
			Response afterFreezeGetInfoResponse = null;
			try {
				afterFreezeGetInfoResponse = getAccountInfo(a);
			} catch (Exception e) {
				log.info("Error while getting account info: ", e);
			}
			CryptoGetInfoResponse.AccountInfo beforeFreezeAccountInfo =
					afterFreezeGetInfoResponse.getCryptoGetInfo().getAccountInfo();
			CryptoGetInfoResponse.AccountInfo afterFreezeAccountInfo = accountInfoMap.get(a);
			log.info("afterFreezeAccountInfo.getAccountID() = " + afterFreezeAccountInfo.getAccountID());
			log.info("afterFreezeAccountInfo.getBalance()=" + afterFreezeAccountInfo.getBalance());
			Assert.assertEquals(beforeFreezeAccountInfo.getAccountID(), afterFreezeAccountInfo.getAccountID());
			Assert.assertEquals(beforeFreezeAccountInfo.getBalance(), afterFreezeAccountInfo.getBalance());
			Assert.assertEquals(beforeFreezeAccountInfo.getKey(), afterFreezeAccountInfo.getKey());
			Assert.assertEquals(beforeFreezeAccountInfo.getExpirationTime(),
					afterFreezeAccountInfo.getExpirationTime());
		});

		//File validation after Freeze service
		fileInfoMap = (Map<FileID, FileGetInfoResponse.FileInfo>) readFromFile(FILE_MAP_FILE);
		fileInfoMap.keySet().stream().forEach(a -> {
			//Validate after Freeze service completed
			FileGetInfoResponse.FileInfo beforeFreezeFileInfo = null;
			try {
				beforeFreezeFileInfo = getFileInfo(a);
			} catch (Exception e) {
				log.info("Error while getting account info: ", e);
			}
			FileGetInfoResponse.FileInfo afterFreezeFileInfo = fileInfoMap.get(a);
			log.info("afterFreezeFileInfo.getFileID() = " + afterFreezeFileInfo.getFileID());
			log.info("afterFreezeFileInfo.getExpirationTime()=" + afterFreezeFileInfo.getExpirationTime());
			Assert.assertEquals(beforeFreezeFileInfo.getFileID(), afterFreezeFileInfo.getFileID());
			Assert.assertEquals(beforeFreezeFileInfo.getExpirationTime(), afterFreezeFileInfo.getExpirationTime());
			Assert.assertEquals(beforeFreezeFileInfo.getKeys(), afterFreezeFileInfo.getKeys());
			Assert.assertEquals(beforeFreezeFileInfo.getSize(), afterFreezeFileInfo.getSize());
		});

		//SmartContract validation after Freeze service
		contractInfoMap = (Map<ContractID, ContractGetInfoResponse.ContractInfo>) readFromFile(SMART_CONTRACT_MAP_FILE);
		contractInfoMap.keySet().stream().forEach(a -> {
			//Validate after Freeze service completed
			Response contractResponse = null;
			try {
				contractResponse = getContractInfo(a);
			} catch (Exception e) {
				log.info("Error while getting Contract Info: ", e);
			}
			ContractGetInfoResponse.ContractInfo beforeFreezeContractInfo =
					contractResponse.getContractGetInfo().getContractInfo();

			ContractGetInfoResponse.ContractInfo afterFreezeContractInfo = contractInfoMap.get(a);
			log.info("afterFreezeContractInfo.getContractID() = " + afterFreezeContractInfo.getContractID());
			log.info("afterFreezeContractInfo.getExpirationTime()=" + afterFreezeContractInfo.getExpirationTime());
			Assert.assertEquals(beforeFreezeContractInfo.getContractID(), afterFreezeContractInfo.getContractID());
			Assert.assertEquals(beforeFreezeContractInfo.getExpirationTime(),
					afterFreezeContractInfo.getExpirationTime());
			Assert.assertEquals(beforeFreezeContractInfo.getMemo(), afterFreezeContractInfo.getMemo());
			Assert.assertEquals(beforeFreezeContractInfo.getAutoRenewPeriod(),
					afterFreezeContractInfo.getAutoRenewPeriod());
			Assert.assertEquals(beforeFreezeContractInfo.getStorage(), afterFreezeContractInfo.getStorage());
			Assert.assertEquals(beforeFreezeContractInfo.getAdminKey(), afterFreezeContractInfo.getAdminKey());
		});
	}

}
