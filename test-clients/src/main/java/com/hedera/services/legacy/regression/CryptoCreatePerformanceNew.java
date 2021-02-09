package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.proto.utils.AtomicCounter;

/**
 * A rewrite of CryptoCreatePerformance class to support different signature formats.
 * 
 * @author Hua Li Created on 2019-03-07
 */
public class CryptoCreatePerformanceNew extends BaseClient {
  private static final Logger log = LogManager.getLogger(CryptoCreatePerformance.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";
  private int BATCH_SIZE = 1000;
  private boolean retrieveTxReceipt = true;
  private AtomicCounter goodResponse = new AtomicCounter();
  private AtomicCounter badResponse = new AtomicCounter();
  private AtomicCounter goodReceipt = new AtomicCounter();
  private AtomicCounter badReceipt = new AtomicCounter();

  public CryptoCreatePerformanceNew(int batchSize, boolean retrieveTxReceipt) {
    super(testConfigFilePath);
    this.BATCH_SIZE = batchSize;
    this.retrieveTxReceipt = retrieveTxReceipt;
  }

  public static void main(String args[]) throws Throwable {
    int numTransfer = 10; // 1000000
    boolean retrieveTxReceipt = true;

    CryptoCreatePerformanceNew cryptoCreatePerformance =
        new CryptoCreatePerformanceNew(numTransfer, retrieveTxReceipt);
    cryptoCreatePerformance.init(args);
    cryptoCreatePerformance.demo();
  }

  public void demo() throws Exception {
    AccountID payerAccount = genesisAccountID;
    AccountID nodeAccount = defaultListeningNodeAccountID;

    long start = System.currentTimeMillis();
    int i = 0;
    for (; i < BATCH_SIZE; i++) {
      log.info("Create Account Request # " + (i + 1));
      AccountID accountID = createAccount(payerAccount, nodeAccount, 900000l,
          retrieveTxReceipt, goodResponse, badResponse, goodReceipt, badReceipt);
      if (retrieveTxReceipt) {
		  log.info("AccountID " + accountID);
	  } else {
		  log.info("Account creation tx submitted.");
	  }
    }

    long end = System.currentTimeMillis();
    log.info("Total time for " + i + " createAccount = " + (end - start) + " ms");
    log.info("Total Good Account Response = " + goodResponse);
    log.info("Total Good Account Receipts = " + goodReceipt);
    log.info("Total BAD Account Response = " + badResponse);
    log.info("Total BAD Account Receipts = " + badReceipt);
  }
}
