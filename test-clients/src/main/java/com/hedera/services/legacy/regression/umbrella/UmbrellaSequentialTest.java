package com.hedera.services.legacy.regression.umbrella;

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

import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple test for executing Hedera API's sequentially.
 *
 * @author Hua Li Created on 2018-11-18
 */
public class UmbrellaSequentialTest {

  protected static final Logger log = LogManager.getLogger(UmbrellaSequentialTest.class);
  protected static String testConfigFilePath = "config/umbrellaTest.properties";

  public static void main(String[] args) throws Throwable {
    runSequentialTests();
  }

  public static void runSequentialTests() throws Throwable {
    SmartContractServiceTest rt = new SmartContractServiceTest(testConfigFilePath);
    rt.setUp();
    CryptoServiceTest.payerAccounts = rt.accountCreatBatch4Payer(CryptoServiceTest.numPayerAccounts); // accounts as payers
    CryptoServiceTest.transferAccounts = rt.accountCreatBatch(CryptoServiceTest.numTransferAccounts); // accounts for transfer's from and to parties
    CryptoServiceTest.initLocalLedgerWithAccountBalances();

    AccountID payer = FileServiceTest.payerAccounts[0];
    AccountID node = FileServiceTest.nodeAccounts[0];
    for (int i = 0; i < 1; i++) {
      runOp(1, UmbrellaServiceRunnable.ops.cryptoCreate, payer, node, rt);
      runOp(2, UmbrellaServiceRunnable.ops.cryptoTransfer, payer, node, rt);
      runOp(3, UmbrellaServiceRunnable.ops.cryptoGetInfo, payer, node, rt);
      runOp(4, UmbrellaServiceRunnable.ops.cryptoGetBalance, payer, node, rt);
      runOp(5, UmbrellaServiceRunnable.ops.cryptoGetRecords, payer, node, rt);
      runOp(6, UmbrellaServiceRunnable.ops.cryptoUpdate, payer, node, rt);
      runOp(7, UmbrellaServiceRunnable.ops.getTxReceipt, payer, node, rt);
      runOp(9, UmbrellaServiceRunnable.ops.getTxFastRecord, payer, node, rt);
      runOp(10, UmbrellaServiceRunnable.ops.getTxRecord, payer, node, rt);
      runOp(10, UmbrellaServiceRunnable.ops.fileUpload, payer, node, rt);
      runOp(11, UmbrellaServiceRunnable.ops.fileUpdate, payer, node, rt);
      runOp(12, UmbrellaServiceRunnable.ops.fileGetInfo, payer, node, rt);
      runOp(13, UmbrellaServiceRunnable.ops.fileGetContent, payer, node, rt);
      runOp(14, UmbrellaServiceRunnable.ops.fileDelete, payer, node, rt);
      runOp(21, UmbrellaServiceRunnable.ops.createContract, payer, node, rt);
      runOp(22, UmbrellaServiceRunnable.ops.contractCallMethod, payer, node, rt);
      runOp(23, UmbrellaServiceRunnable.ops.contractCallLocalMethod, payer, node, rt);
      runOp(24, UmbrellaServiceRunnable.ops.getTxRecordByContractID, payer, node, rt);
      runOp(25, UmbrellaServiceRunnable.ops.contractGetBytecode, payer, node, rt);
      runOp(26, UmbrellaServiceRunnable.ops.getContractInfo, payer, node, rt);
      runOp(27, UmbrellaServiceRunnable.ops.getBySolidityID, payer, node, rt);
      runOp(28, UmbrellaServiceRunnable.ops.updateContract, payer, node, rt);
    }
  }

  /**
   * Executes a single Hedera API operation.
   *
   * @param i operation sequence number
   * @param op API to run
   */
  protected static void runOp(int i, UmbrellaServiceRunnable.ops op, AccountID payer, AccountID node,
                              SmartContractServiceTest rt) {
    UmbrellaServiceRunnable task = new UmbrellaServiceRunnable(i, rt);
    task.exec(op, payer, node);
    log.info("Finished ..op=" + op.name());
  }
}
