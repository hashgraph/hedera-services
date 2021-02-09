package com.hedera.services.legacy.crypto;

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
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.legacy.regression.BaseClient;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Transactions Record Tests.
 *
 * @author Tirupathi Mandala Created on 2019-06-04
 */
public class ThresholdRecordTests extends BaseClient {

  private static final Logger log = LogManager.getLogger(ThresholdRecordTests.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";

  public ThresholdRecordTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  /**
   * before running this Test please update HGCApp applicatoin.properties
   * --thresholdTxRecordTTL=240
   */
  public static void main(String[] args) throws Throwable {
    ThresholdRecordTests tester = new ThresholdRecordTests(testConfigFilePath);
    tester.init(args);
    tester.cryptoTransferThresholdRecordTest();
  }

  /**
   * Tests all fields of crypto create API.
   */
  public void cryptoTransferThresholdRecordTest() throws Throwable {
    // create accounts
    receiverSigRequired = false;
    payerAccounts = accountCreatBatch(3);
    AccountID payerID = payerAccounts[0];
    AccountID nodeID = defaultListeningNodeAccountID;

    AccountID fromAccount = payerAccounts[1];
    Assert.assertNotNull(fromAccount);
    AccountID toAccountID = payerAccounts[2];
    Assert.assertNotNull(toAccountID);

    // get account content
    AccountInfo accInfo = getAccountInfo(fromAccount, payerID, nodeID);
    boolean recvSigRequired = accInfo.getReceiverSigRequired();
    Assert.assertEquals(false, recvSigRequired);
    long balance = accInfo.getBalance();

    Key accKey = acc2ComplexKeyMap.get(fromAccount);
    Key payerKey = acc2ComplexKeyMap.get(payerID);
    long amount = 100L;

    acc2ComplexKeyMap.put(fromAccount, accKey); // right the account key

    // sendRecordThreshold and recvRecordThreshold fields: 
    // scenario A: a transfer with amount less than the thresholds, then no record is available
    long sendRecordThreshold = balance / 1000;
    long recvRecordThreshold = sendRecordThreshold / 10;
    AccountInfo accInfoMod = updateAccount(fromAccount, payerID, nodeID, null, null,
        sendRecordThreshold,
        recvRecordThreshold, null, null, null);
    Assert.assertEquals(sendRecordThreshold, accInfoMod.getGenerateSendRecordThreshold());
    Assert.assertEquals(recvRecordThreshold, accInfoMod.getGenerateReceiveRecordThreshold());

    amount = sendRecordThreshold;
    TransactionReceipt receipt = transfer(payerID, nodeID, fromAccount, toAccountID,
        amount); // fromAccount as sender
    amount = recvRecordThreshold;
    receipt = transfer(payerID, nodeID, toAccountID, fromAccount,
        amount); // fromAccount as receiver
    Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

    // get all fromAccountRecords of fromAccount, size should be zero
    List<TransactionRecord> fromAccountRecords = getTransactionRecordsByAccountId(fromAccount,
        payerID, nodeID);
    Assert.assertEquals(0, fromAccountRecords.size());

    // scenario B: a transfer with amount no less than thresholds, then fromAccountRecords are available
    amount = sendRecordThreshold + 10;
    receipt = transfer(payerID, nodeID, fromAccount, toAccountID, amount); // fromAccount as sender
    Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
    amount = recvRecordThreshold + 10;
    receipt = transfer(payerID, nodeID, toAccountID, fromAccount,
        amount); // fromAccount as receiver
    Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
    // get all fromAccountRecords of fromAccount, size should be zero
    fromAccountRecords = getTransactionRecordsByAccountId(fromAccount, payerID, nodeID);
    // get all fromAccountRecords of fromAccount, size should be 2
    Assert.assertEquals(2, fromAccountRecords.size());
    Thread.sleep(
        300000); // wait for 5 minutes , should be graterThan thresholdTxRecordTTL in HGCApp application.properties
    receipt = transfer(payerID, nodeID, payerID, toAccountID,
        100); // a simple transaction to create an event on plantform
    Thread.sleep(10000); //Wait for 10 seconds
    fromAccountRecords = getTransactionRecordsByAccountId(fromAccount, payerID, nodeID);
    Assert.assertEquals(0, fromAccountRecords.size());

    log.info(LOG_PREFIX + "cryptoCreateTests: PASSED! :)");
  }

}
