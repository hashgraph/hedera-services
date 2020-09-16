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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.proto.utils.AtomicCounter;

/**
 * A rewrite of CryptoCreatePerformance class to support different signature formats.
 *
 * @author Hua Li Created on 2019-03-07
 */
public class MultiTransferConfigrableTestNew extends BaseClient {
	private static final Logger log = LogManager.getLogger(MultiTransferConfigrableTest.class);
	private static String testConfigFilePath = "config/umbrellaTest.properties";
	private int BATCH_SIZE = 1000000;
	private boolean retrieveTxReceipt = true;

	public MultiTransferConfigrableTestNew(int batchSize, boolean retrieveTxReceipt) {
		super(testConfigFilePath);
		this.BATCH_SIZE = batchSize;
		this.retrieveTxReceipt = retrieveTxReceipt;
	}

	public static void main(String args[]) throws Throwable {
		int numTransfer = 4000;
		boolean retrieveTxReceipt = true;

		MultiTransferConfigrableTestNew multiTransferConfigrableTestNew =
				new MultiTransferConfigrableTestNew(numTransfer, retrieveTxReceipt);
		multiTransferConfigrableTestNew.init(args);
		multiTransferConfigrableTestNew.demo();
	}

	public void demo() throws Exception {
		AccountID payerAccount = genesisAccountID;
		AccountID nodeAccount = defaultListeningNodeAccountID;

		// create 1st account by payer as genesis
		AccountID newlyCreateAccountId1 =
				createAccount(payerAccount, nodeAccount, 100000000000000l);
		Assert.assertNotNull(newlyCreateAccountId1);
		log.info("Account-ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");

		// 2nd account created with payer as genesis
		AccountID newlyCreateAccountId2 =
				createAccount(payerAccount, nodeAccount, 100000000000000l);
		Assert.assertNotNull(newlyCreateAccountId2);
		log.info("Account-ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
		Thread.sleep(180);

		long start = System.currentTimeMillis();
		long sentAmtToAcc1 = 0l;
		long sentAmtToAcc2 = 0l;
		AtomicCounter goodResponse = new AtomicCounter();
		AtomicCounter badResponse = new AtomicCounter();
		AtomicCounter goodReceipt = new AtomicCounter();
		AtomicCounter badReceipt = new AtomicCounter();
		long ts, te = 0l;
		int i = 0;
		for (; i < BATCH_SIZE; i++) {
			try {
				ts = System.nanoTime();
				if (i % 2 == 0) {
					transfer(newlyCreateAccountId2, nodeAccount, newlyCreateAccountId2, newlyCreateAccountId1,
							9999l, retrieveTxReceipt, goodResponse, badResponse, goodReceipt, badReceipt);
					sentAmtToAcc1 += 9999l;
				} else {
					transfer(newlyCreateAccountId1, nodeAccount, newlyCreateAccountId1, newlyCreateAccountId2,
							999l, retrieveTxReceipt, goodResponse, badResponse, goodReceipt, badReceipt);
					sentAmtToAcc2 += 999l;
				}
				te = System.nanoTime();
				log.info("Transfer # " + i + " in " + (te - ts) / 1000000 + " ms");
			} catch (Throwable thx) {
				log.info("ERROR: " + thx.getMessage());
			}
		}

		long end = System.currentTimeMillis();
		log.info("Total time took for " + i + " transfers is :: " + (end - start) + " ms");
		log.info("Total Good Account Response = " + goodResponse);
		log.info("Total Good Account Receipts = " + goodReceipt);
		log.info("Total BAD Account Response = " + badResponse);
		log.info("Total BAD Account Receipts = " + badReceipt);
		log.info("Total Amount sent from Account 1 to 2 = " + sentAmtToAcc1);
		log.info("Total Amount sent from Account 2 to 1 = " + sentAmtToAcc2);
	}
}
