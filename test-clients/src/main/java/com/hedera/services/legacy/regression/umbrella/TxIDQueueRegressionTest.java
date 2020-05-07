package com.hedera.services.legacy.regression.umbrella;

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
import com.hedera.services.legacy.core.CommonUtils;

/**
 * Regression test for getting receipts and records with cached transaction IDs.
 *
 * @author Hua Li Created on 2018-11-18
 */
public class TxIDQueueRegressionTest extends UmbrellaSequentialTest {

  public static void main(String[] args) throws Throwable {
    runSequentialTests();
  }

  public static void runSequentialTests() throws Throwable {
    SmartContractServiceTest rt = new SmartContractServiceTest(testConfigFilePath);
    rt.setUp();
    rt.accountCreatBatch(CryptoServiceTest.numPayerAccounts);

    AccountID payer = FileServiceTest.payerAccounts[0];
    AccountID node = FileServiceTest.nodeAccounts[0];
    for (int i = 0; i < 1; i++) {
      runOp(1, UmbrellaServiceRunnable.ops.cryptoCreate, payer, node, rt);
      runOp(2, UmbrellaServiceRunnable.ops.cryptoTransfer, payer, node, rt);

      log.info("***...both receipts and record tx should be available");
      runOp(3, UmbrellaServiceRunnable.ops.getTxReceipt, payer, node, rt);
      runOp(4, UmbrellaServiceRunnable.ops.getTxFastRecord, payer, node, rt);
      runOp(5, UmbrellaServiceRunnable.ops.getTxRecord, payer, node, rt);

      CommonUtils.nap(TransactionIDCache.txReceiptTTL);
      log.info("***zzz " + TransactionIDCache.txReceiptTTL
          + " seconds...no receipts tx should be available");
      runOp(6, UmbrellaServiceRunnable.ops.getTxReceipt, payer, node, rt);
      runOp(7, UmbrellaServiceRunnable.ops.getTxFastRecord, payer, node, rt);
      runOp(8, UmbrellaServiceRunnable.ops.getTxRecord, payer, node, rt);

      CommonUtils.nap(TransactionIDCache.txReceiptTTL);
      log.info("***zzz " + TransactionIDCache.txReceiptTTL
          + " seconds...no receipt/records tx should be available");
      runOp(9, UmbrellaServiceRunnable.ops.getTxReceipt, payer, node, rt);
      runOp(10, UmbrellaServiceRunnable.ops.getTxFastRecord, payer, node, rt);
      runOp(11, UmbrellaServiceRunnable.ops.getTxRecord, payer, node, rt);
    }
  }
}
