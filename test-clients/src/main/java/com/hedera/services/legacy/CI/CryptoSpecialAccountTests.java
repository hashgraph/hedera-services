package com.hedera.services.legacy.CI;

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
import com.hedera.services.legacy.regression.BaseClient;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.core.TestHelper;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * A client base class that supports different signature formats.
 *
 * @author Hua Li Created on 2019-03-07
 */

/**
 * Crypt Test for Special Account operations.
 * 1. Special Account Delete Success when payer account
 * is Genesis Account
 * 2. Special Account Delete Negative when payer account is NOT Genesis Account
 * 3. Special Account Delete Negative when payer account is it self (Special Account)
 * 4. Special Account update Success when payer account is Genesis Account
 * 5. Special Account update Negative when payer account is NOT Genesis Account
 * 6. Special Account update Negative when payer account
 * is it self (Special Account)
 *
 * @
 */
public class CryptoSpecialAccountTests extends BaseClient {

  private static final Logger log = LogManager.getLogger(CryptoSpecialAccountTests.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";

  public CryptoSpecialAccountTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  protected AccountID[] commonAccounts = null; // payer accounts, size determined by numCryptoAccounts
  AccountID payerID;
  AccountID nodeID;
  AccountID toID;
  AccountID accID;
  AccountID specialAccount55;


  public void specialAccountsInit() throws Throwable {
    // create accounts
    commonAccounts = accountCreatBatch(3);
    payerID = commonAccounts[0];
    nodeID = CryptoServiceTest.nodeAccounts[0];
    toID = commonAccounts[1];
    Assert.assertNotNull(toID);
    accID = commonAccounts[2];
    Assert.assertNotNull(accID);
    specialAccount55 = AccountID.newBuilder().setShardNum(0).setRealmNum(0)
        .setAccountNum(CryptoServiceTest.specialAccountNum).build();
    Assert.assertNotNull(specialAccount55);
  }

  /**
   * Test crypto update normal account.
   */
  public void cryptoUpdate_NormalAccount() throws Throwable {

    // get account content
    AccountInfo accInfo = getAccountInfo(accID, payerID, nodeID);

    Key oldKey = accInfo.getKey();
    AccountID proxyAccountID = accInfo.getProxyAccountID();
    long sendRecordThreshold = accInfo.getGenerateSendRecordThreshold();
    long recvRecordThreshold = accInfo.getGenerateReceiveRecordThreshold();
    Duration autoRenewPeriod = accInfo.getAutoRenewPeriod();
    Timestamp expirationTime = accInfo.getExpirationTime();
    boolean receiverSigRequired = accInfo.getReceiverSigRequired();
    // create update tx based on content: change all fields
    Key newKey = genComplexKey("thresholdKey");
    Assert.assertNotEquals(newKey, oldKey);
    AccountID proxyAccountIDMod = CryptoServiceTest.nodeAccounts[1];
    Assert.assertNotEquals(proxyAccountIDMod, proxyAccountID);
    long sendRecordThresholdMod = sendRecordThreshold * 2;
    long recvRecordThresholdMod = recvRecordThreshold * 2;
    Duration autoRenewPeriodMod = Duration.newBuilder().setSeconds(autoRenewPeriod.getSeconds() + 30)
        .build();
    Timestamp expirationTimeMod = Timestamp.newBuilder().setSeconds(expirationTime.getSeconds() + 30)
        .setNanos(expirationTime.getNanos()).build();
    boolean receiverSigRequiredMod = !receiverSigRequired;

    // submit update tx and get acc content
    AccountInfo accInfoMod = updateAccount(accID, payerID, nodeID, newKey, proxyAccountIDMod,
        sendRecordThresholdMod, recvRecordThresholdMod,
        autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);
    log.info("Account Info before update : " + accInfo);
    log.info("Account Info after update : " + accInfoMod);

    // verify content
    Assert.assertEquals(newKey, accInfoMod.getKey());
    Assert.assertEquals(proxyAccountIDMod, accInfoMod.getProxyAccountID());
    Assert.assertEquals(sendRecordThresholdMod, accInfoMod.getGenerateSendRecordThreshold());
    Assert.assertEquals(recvRecordThresholdMod, accInfoMod.getGenerateReceiveRecordThreshold());
    Assert.assertEquals(autoRenewPeriodMod, accInfoMod.getAutoRenewPeriod());
    Assert.assertEquals(expirationTimeMod, accInfoMod.getExpirationTime());

    log.info(LOG_PREFIX + "Crypto Update Normal Account Tests: PASSED! :)");
  }

  /**
   * Test crypto update Special Account and Payer is Genesis Account Success Case.
   */
  public void cryptoUpdate_SpecialAccount_Success() throws Throwable {

    AccountID payerID = CryptoServiceTest.genesisAccountID;

    // get account content
    AccountInfo accInfo = getAccountInfo(specialAccount55, payerID, nodeID);
    Key oldKey = accInfo.getKey();
    AccountID proxyAccountID = accInfo.getProxyAccountID();
    long sendRecordThreshold = accInfo.getGenerateSendRecordThreshold();
    long recvRecordThreshold = accInfo.getGenerateReceiveRecordThreshold();
    Duration autoRenewPeriod = accInfo.getAutoRenewPeriod();
    Timestamp expirationTime = accInfo.getExpirationTime();
    boolean receiverSigRequired = accInfo.getReceiverSigRequired();
    // create update tx based on content: change all fields
    Key newKey = genComplexKey("thresholdKey");
    Assert.assertNotEquals(newKey, oldKey);
    AccountID proxyAccountIDMod = commonAccounts[1];
    Assert.assertNotEquals(proxyAccountIDMod, proxyAccountID);
    long sendRecordThresholdMod = sendRecordThreshold * 2;
    long recvRecordThresholdMod = recvRecordThreshold * 2;
    Duration autoRenewPeriodMod = Duration.newBuilder().setSeconds(autoRenewPeriod.getSeconds() * 2)
        .build();
    Timestamp expirationTimeMod = Timestamp.newBuilder().setSeconds(expirationTime.getSeconds() * 2)
        .setNanos(expirationTime.getNanos()).build();
    boolean receiverSigRequiredMod = !receiverSigRequired;

    // submit update tx and get acc content
    AccountInfo accInfoMod = updateAccount(accInfo.getAccountID(), payerID, nodeID, newKey,
        proxyAccountIDMod,
        sendRecordThresholdMod, recvRecordThresholdMod,
        autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);
    log.info("Account Info before update : " + accInfo);
    log.info("Account Info after update : " + accInfoMod);

    // verify content
    Assert.assertEquals(newKey, accInfoMod.getKey());
    Assert.assertEquals(proxyAccountIDMod, accInfoMod.getProxyAccountID());
    Assert.assertEquals(sendRecordThresholdMod, accInfoMod.getGenerateSendRecordThreshold());
    Assert.assertEquals(recvRecordThresholdMod, accInfoMod.getGenerateReceiveRecordThreshold());
    Assert.assertEquals(autoRenewPeriodMod, accInfoMod.getAutoRenewPeriod());
    //acc2ComplexKeyMap.put(accInfo.getAccountID(),newKey); //update map with new Key for subsequent tests
    // submit update back to Old Key
    AccountInfo secondUpdateInfoMod = updateAccount(accInfo.getAccountID(), payerID, nodeID, oldKey,
        proxyAccountIDMod,
        sendRecordThresholdMod, recvRecordThresholdMod,
        autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);
    Assert.assertEquals(oldKey, secondUpdateInfoMod.getKey());
    Thread.sleep(1000); //Wait for 1 second before starting Delete Test
    log.info(LOG_PREFIX + "Crypto Update Special Account with Payer is Genesis Tests: PASSED! :)");
  }

  /**
   * Test crypto update Special Account and Payer is not Genesis Account.
   */
  public void cryptoUpdate_SpecialAccount_Payer_NOT_Genesis() throws Throwable {

    // get account content
    AccountInfo accInfo = getAccountInfo(specialAccount55, payerID, nodeID);
    Key oldKey = accInfo.getKey();
    AccountID proxyAccountID = accInfo.getProxyAccountID();
    long sendRecordThreshold = accInfo.getGenerateSendRecordThreshold();
    long recvRecordThreshold = accInfo.getGenerateReceiveRecordThreshold();
    Duration autoRenewPeriod = accInfo.getAutoRenewPeriod();
    Timestamp expirationTime = accInfo.getExpirationTime();
    boolean receiverSigRequired = accInfo.getReceiverSigRequired();
    // create update tx based on content: change all fields
    Key newKey = genComplexKey("thresholdKey");
    Assert.assertNotEquals(newKey, oldKey);
    AccountID proxyAccountIDMod = commonAccounts[1];
    Assert.assertNotEquals(proxyAccountIDMod, proxyAccountID);
    long sendRecordThresholdMod = sendRecordThreshold * 2;
    long recvRecordThresholdMod = recvRecordThreshold * 2;
    Duration autoRenewPeriodMod = Duration.newBuilder().setSeconds(autoRenewPeriod.getSeconds() * 2)
        .build();
    Timestamp expirationTimeMod = Timestamp.newBuilder().setSeconds(expirationTime.getSeconds() * 2)
        .setNanos(expirationTime.getNanos()).build();
    boolean receiverSigRequiredMod = !receiverSigRequired;

    // submit update tx and get acc content
    TransactionResponse response = updateSpecialAccount(accInfo.getAccountID(), payerID, nodeID,
        newKey,
        proxyAccountIDMod,
        sendRecordThresholdMod, recvRecordThresholdMod,
        autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);

    Assert.assertEquals(ResponseCodeEnum.AUTHORIZATION_FAILED,
        response.getNodeTransactionPrecheckCode());

    log.info(LOG_PREFIX + "Crypto Update Special Account Payer NOT Genesis Tests: PASSED! :)");
  }

  /**
   * Test crypto update Special Account and Payer is it self.
   */
  public void cryptoUpdate_SpecialAccount_Payer_it_self() throws Throwable {
    AccountInfo toAccInfo_55 = getAccountInfo(specialAccount55, payerID, nodeID);
    Transaction paymentTx = CryptoServiceTest.getUnSignedTransferTx(payerID, nodeID, accID,
        toAccInfo_55.getAccountID(), 100000L,
        "Transaction with Updated signMap");
    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key fromKey = TestHelperComplex.acc2ComplexKeyMap.get(accID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    keys.add(fromKey);
    Key toKey = TestHelperComplex.acc2ComplexKeyMap.get(CryptoServiceTest.genesisAccountID); // Account from 1 to 100 has same key
    keys.add(toKey);
    Transaction transferTxSigned = TransactionSigner
        .signTransactionComplex(paymentTx, keys, TestHelperComplex.pubKey2privKeyMap);
    TransactionReceipt receipt = transfer(transferTxSigned);
    Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

    // get account content
    Key oldKey = toAccInfo_55.getKey();
    AccountID proxyAccountID = toAccInfo_55.getProxyAccountID();
    long sendRecordThreshold = toAccInfo_55.getGenerateSendRecordThreshold();
    long recvRecordThreshold = toAccInfo_55.getGenerateReceiveRecordThreshold();
    Duration autoRenewPeriod = toAccInfo_55.getAutoRenewPeriod();
    Timestamp expirationTime = toAccInfo_55.getExpirationTime();
    boolean receiverSigRequired = toAccInfo_55.getReceiverSigRequired();
    // create update tx based on content: change all fields
    Key newKey = genComplexKey("thresholdKey");
    Assert.assertNotEquals(newKey, oldKey);
    AccountID proxyAccountIDMod = commonAccounts[1];
    Assert.assertNotEquals(proxyAccountIDMod, proxyAccountID);
    long sendRecordThresholdMod = sendRecordThreshold * 2;
    long recvRecordThresholdMod = recvRecordThreshold * 2;
    Duration autoRenewPeriodMod = Duration.newBuilder().setSeconds(autoRenewPeriod.getSeconds() * 2)
        .build();
    Timestamp expirationTimeMod = Timestamp.newBuilder().setSeconds(expirationTime.getSeconds() * 2)
        .setNanos(expirationTime.getNanos()).build();
    boolean receiverSigRequiredMod = !receiverSigRequired;

    // submit update tx and get acc content
    AccountInfo accInfoMod = updateAccount(toAccInfo_55.getAccountID(), payerID, nodeID, newKey,
        proxyAccountIDMod,
        sendRecordThresholdMod, recvRecordThresholdMod,
        autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);
    log.info("Account Info before update : " + toAccInfo_55);
    log.info("Account Info after update : " + accInfoMod);

    // verify content
    Assert.assertNotEquals(newKey, accInfoMod.getKey());
    Assert.assertNotEquals(proxyAccountIDMod, accInfoMod.getProxyAccountID());
    Assert.assertNotEquals(sendRecordThresholdMod, accInfoMod.getGenerateSendRecordThreshold());
    Assert.assertNotEquals(recvRecordThresholdMod, accInfoMod.getGenerateReceiveRecordThreshold());
    Assert.assertNotEquals(autoRenewPeriodMod, accInfoMod.getAutoRenewPeriod());

    log.info(LOG_PREFIX + "Crypto Update Special Account -Self Account as Payer Tests: PASSED! :)");
  }


  public void cryptoDelete_SpecialAccount_Payer_NOT_Genesis() throws Throwable {

    AccountInfo toAccInfo_55 = getAccountInfo(specialAccount55, payerID, nodeID);

    // Crypto Delete Request

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    TransactionID transactionID = TransactionID.newBuilder().setAccountID(payerID)
        .setTransactionValidStart(timestamp).build();
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(toAccInfo_55.getAccountID()).setTransferAccountID(payerID)
        .build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeID)
        .setTransactionFee(100000)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(false)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    Transaction signDelete = TransactionSigner
        .signTransactionComplex(tx, keys, TestHelperComplex.pubKey2privKeyMap);

    TransactionResponse response1 = CryptoServiceTest.cstub.cryptoDelete(signDelete);
    log.info(response1.getNodeTransactionPrecheckCode());
//    Assert.assertEquals(ResponseCodeEnum.ACCOUNT_IS_NOT_GENESIS_ACCOUNT, response1.getNodeTransactionPrecheckCode() );
    Assert.assertEquals(ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE, response1.getNodeTransactionPrecheckCode() );
  }

  public void cryptoDelete_SpecialAccount_Success() throws Throwable {

    // create accounts
    CryptoServiceTest.payerAccounts = accountCreatBatch(3);
    AccountID payerID = CryptoServiceTest.genesisAccountID;
    AccountID nodeID = CryptoServiceTest.nodeAccounts[0];

    AccountID toID = CryptoServiceTest.payerAccounts[1];
    Assert.assertNotNull(toID);
    AccountID accID = CryptoServiceTest.payerAccounts[2];
    Assert.assertNotNull(accID);
    AccountID specialAccount55 = AccountID.newBuilder().setShardNum(0).setRealmNum(0)
        .setAccountNum(CryptoServiceTest.specialAccountNum).build();
    Assert.assertNotNull(specialAccount55);
    AccountInfo toAccInfo_55 = getAccountInfo(specialAccount55, payerID, nodeID);

    // Crypto Delete Request

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    TransactionID transactionID = TransactionID.newBuilder().setAccountID(CryptoServiceTest.genesisAccountID)
        .setTransactionValidStart(timestamp).build();
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(toAccInfo_55.getAccountID())
        .setTransferAccountID(CryptoServiceTest.genesisAccountID)
        .build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeID)
        .setTransactionFee(100000)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(false)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(CryptoServiceTest.genesisAccountID);
    List<Key> keys = new ArrayList<Key>();
    keys.add(payerKey);
    Transaction signDelete = TransactionSigner
        .signTransactionComplex(tx, keys, TestHelperComplex.pubKey2privKeyMap);

    TransactionResponse response1 = CryptoServiceTest.cstub.cryptoDelete(signDelete);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(response1.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
    TransactionReceipt txReceipt = null;
    txReceipt = TestHelper.getTxReceipt(transactionID, CryptoServiceTest.cstub);
    log.info(txReceipt);

    // Get account Info of 1st Account
    Transaction paymentTxSigned = CryptoServiceTest.getPaymentSigned(CryptoServiceTest.genesisAccountID.getAccountNum(),
        nodeID.getAccountNum(), "getCryptoGetAccountInfo");
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(toAccInfo_55.getAccountID(), paymentTxSigned,
            ResponseType.ANSWER_ONLY);
    Response getInfoResponse = CryptoServiceTest.cstub.getAccountInfo(cryptoGetInfoQuery);
    log.info("Pre Check Response of getAccountInfo:: "
        + getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
    Assert.assertNotNull(getInfoResponse);
    Assert.assertNotNull(getInfoResponse.getCryptoGetInfo());
    Assert.assertEquals(ResponseCodeEnum.ACCOUNT_DELETED,
        getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
  }

  public static void main(String[] args) throws Throwable {
    CryptoSpecialAccountTests tester = new CryptoSpecialAccountTests(testConfigFilePath);
    tester.init(args);
    tester.specialAccountsInit();
    tester.cryptoUpdate_NormalAccount();
    tester.cryptoUpdate_SpecialAccount_Payer_NOT_Genesis();
    tester.cryptoUpdate_SpecialAccount_Payer_it_self();
    tester.cryptoUpdate_SpecialAccount_Success();
    tester.cryptoDelete_SpecialAccount_Payer_NOT_Genesis();
  }

}
