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
import com.hedera.services.legacy.regression.BaseFeeTests;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
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
import com.hedera.services.legacy.proto.utils.KeyExpansion;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Test Client for Crypto fee tests
 *
 * @author Tirupathi Mandala Created on 2019-06-12
 */
public class CryptoFeeTests extends BaseFeeTests {

  private static final Logger log = LogManager.getLogger(CryptoFeeTests.class);
  private static String testConfigFilePath = "config/umbrellaTest.properties";

  public CryptoFeeTests(String testConfigFilePath) {
    super(testConfigFilePath);
  }

  public static void main(String[] args) throws Throwable {
    CryptoFeeTests tester = new CryptoFeeTests(testConfigFilePath);
    tester.setup(args);
    tester.cryptoCreateAccountFeeTest();
    tester.cryptoCreateAccountMultiSigFeeTest();
    tester.cryptoUpdateAccountFeeTest();
 //   tester.cryptoUpdateAccountMultiSigFeeTest();
    tester.cryptoTransferFeeTest();
    tester.cryptoTransferFeeTest_insufficientBalance();
    tester.cryptoTransferMultiSigFeeTest();
    tester.cryptoDelAccountFeeTest();
    tester.cryptoDelAccountMultiSigFeeTest();
    log.info("------------ Test Results --------------");
    testResults.stream().forEach(a->log.info(a));
  }


  public void cryptoCreateAccountFeeTest() throws Throwable {

    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    long durationSeconds = 30 * 24 * 60 * 60; //1 Month (30 Days)
    CryptoServiceTest.accountKeyTypes = new String[]{"single"};
    COMPLEX_KEY_SIZE = 1;
    Key key = genComplexKey("single");
    Transaction createAccountRequest = TestHelperComplex
        .createAccount(payerID, payerKey, nodeID, key, 1000L, TestHelper.getCryptoMaxFee(),
            false, 1, durationSeconds);
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    if (body.getTransactionID() == null || !body.getTransactionID().hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    TransactionResponse response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
    Thread.sleep(NAP);
    long transactionFee = getTransactionFee(createAccountRequest);
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info("Keys Size : " + key.toByteArray().length);
    log.info("Transaction Size with single Key: " + createAccountRequest.toByteArray().length);
    String result = "Crypto Create Account Sig=1, memo=1, Duration=30 :" + transactionFee;
    testResults.add(result);
    log.info(result);

    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_CREATE_MEMO_1_KEY_1_DUR_30 + feeVariance;
    long minTransactionFee = CRYPTO_CREATE_MEMO_1_KEY_1_DUR_30 - feeVariance;
    if(transactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    //Assign back key Configuration
    CryptoServiceTest.accountKeyTypes = new String[]{"single", "keylist", "thresholdKey"};
    COMPLEX_KEY_SIZE = 3;

  }

  public void cryptoCreateAccountMultiSigFeeTest() throws Throwable {

    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    long durationSeconds = 90 * 24 * 60 * 60; //1 Month (30 Days)
    CryptoServiceTest.accountKeyTypes = new String[]{"keylist"};
    COMPLEX_KEY_SIZE = 10;
    Key key = genComplexKey("keylist");
    Transaction createAccountRequest = TestHelperComplex
        .createAccount(payerID, payerKey, nodeID, key, 1000L, TestHelper.getCryptoMaxFee(),
            false, 10, durationSeconds);
    TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    if (body.getTransactionID() == null || !body.getTransactionID().hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    TransactionResponse response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
    Thread.sleep(NAP);
    long transactionFee = getTransactionFee(createAccountRequest);
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info("Keys Size : " + key.getKeyList().getKeysCount());
    log.info("Transaction Size with 10 Keys: " + createAccountRequest.toByteArray().length);
    String result = "Crypto Create Account Sig=10, memo=10, Duration=90 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_CREATE_MEMO_10_KEY_10_DUR_90 + feeVariance;
    long minTransactionFee = CRYPTO_CREATE_MEMO_10_KEY_10_DUR_90 - feeVariance;
    if(transactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
    //Assign back key Configuration
    CryptoServiceTest.accountKeyTypes = new String[]{"single", "keylist", "thresholdKey"};
    COMPLEX_KEY_SIZE = 3;

  }


  public void cryptoUpdateAccountFeeTest() throws Throwable {
    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);

    long durationSeconds = 30 * 24 * 60 * 60; //1 Month (30 Days)
    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key accKey = TestHelperComplex.acc2ComplexKeyMap.get(account_1);
    List<Key> keys = new ArrayList<>();
    keys.add(payerKey);
    keys.add(accKey);
    Duration duration = RequestBuilder.getDuration(durationSeconds);
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction updateTx = TestHelperComplex.updateAccount(account_1, payerID,
        nodeID, duration, memo);
    Transaction signUpdate = TransactionSigner
        .signTransactionComplex(updateTx, keys, TestHelperComplex.pubKey2privKeyMap);
    TransactionResponse response = CryptoServiceTest.cstub.updateAccount(signUpdate);
    Thread.sleep(NAP);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signUpdate);
    TransactionID transactionID = body.getTransactionID();
    if (transactionID == null || !transactionID.hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    cache.addTransactionID(transactionID);
    Thread.sleep(NAP);
    long transactionFee = getTransactionFee(signUpdate);
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKey.getKeyList().getKeysCount());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(payerID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + updateTx.getSigMap().getSigPairCount());

    String result = "Crypto Update Account Sig=1, memo=1, Duration=30 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_UPDATE_MEMO_1_KEY_1_DUR_30 + feeVariance;
    long minTransactionFee = CRYPTO_UPDATE_MEMO_1_KEY_1_DUR_30 - feeVariance;
    if(transactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  public void cryptoUpdateAccountMultiSigFeeTest() throws Throwable {

    List<KeyPair> keyPairList = getKeyPairList(1);
    List<Key> queryPayerKeyList = new ArrayList<>();
    queryPayerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(queryPayerId));

//    Transaction createAccountRequest = TestHelperComplex
//        .createAccountMultiSig(queryPayerId, nodeID, keyPairList, 10000000000l,
//            queryPayerKeyList, 1, 5000);
//    TransactionResponse response = cstub.createAccount(createAccountRequest);

    AccountID multiKeyAccountId = createAccountWithListKey(CryptoServiceTest.genesisAccountID, nodeID,
        CryptoServiceTest.DEFAULT_INITIAL_ACCOUNT_BALANCE, true, true, 10);
    //  AccountID multiKeyAccountId = getAccountID(createAccountRequest);

    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);

    long durationSeconds = 30 * 24 * 60 * 60; //1 Month (30 Days)
    //   Key payerKey = acc2ComplexKeyMap.get(payerID);
    //  Key accKey = acc2ComplexKeyMap.get(account_1);
    List<PrivateKey> keys = new ArrayList<>();
    keys.add(CryptoServiceTest.getAccountPrivateKeys(payerID).get(0));
    for (KeyPair keyPair : keyPairList) {
      keys.add(keyPair.getPrivate());
    }
    Duration duration = RequestBuilder.getDuration(durationSeconds);
    String memo = TestHelperComplex.getStringMemo(1);
    Transaction updateTx = TestHelperComplex.updateAccount(multiKeyAccountId, payerID,
        nodeID, duration, memo);
    Transaction signUpdate = TransactionSigner
        .signTransaction(updateTx, keys);
    log.info("updateTransaction : " + signUpdate);
    TransactionResponse updateResponse = CryptoServiceTest.cstub.updateAccount(signUpdate);

    Assert.assertNotNull(updateResponse);
//    Assert.assertEquals(ResponseCodeEnum.OK, updateResponse.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + updateResponse.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signUpdate);
    TransactionID transactionID = body.getTransactionID();
    if (transactionID == null || !transactionID.hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    cache.addTransactionID(transactionID);

    long transactionFee = getTransactionFee(signUpdate);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_UPDATE_MEMO_10_KEY_10_DUR_90 + feeVariance;
    long minTransactionFee = CRYPTO_UPDATE_MEMO_10_KEY_10_DUR_90 - feeVariance;
    if(transactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    String result = "Crypto Update Account Sig=10, memo=10, Duration=90 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }

  public void cryptoTransferFeeTest() throws Exception {

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    long durationSeconds = 30 * 24 * 60 * 60; //1 Month (30 Days)
    CryptoServiceTest.accountKeyTypes = new String[]{"single"};
    COMPLEX_KEY_SIZE = 1;
    Key key = genComplexKey("single");
    Transaction createAccountRequest = TestHelperComplex
        .createAccount(payerID, payerKey, nodeID, key, 1000000000L, TestHelper.getCryptoMaxFee(), false,
            10, durationSeconds);
    Thread.sleep(NAP);
    TransactionResponse response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
        .extractTransactionBody(createAccountRequest);
    TransactionID transactionID = body.getTransactionID();
    if (transactionID == null || !body.hasTransactionID()) {
      log.info("Transaction is null");
      return;
    }
    long transactionFee = getTransactionFee(createAccountRequest);
    AccountID newlyCreateAccountId1 = getAccountID(createAccountRequest);
    TestHelperComplex.acc2ComplexKeyMap.put(newlyCreateAccountId1, key);
    // PrivateKey payerPrivateKey = pubKey2privKeyMap.get(payerKey);
    String memo = TestHelperComplex.getStringMemo(10);
    Transaction transfer1 = CryptoServiceTest.getSignedTransferTx(queryPayerId, nodeID, newlyCreateAccountId1,
        account_2,
        1000, memo);
    log.info("Transferring 1000 coin from 1st account to 2nd account....request=" + transfer1);
    TransactionResponse transferRes = CryptoServiceTest.cstub.cryptoTransfer(transfer1);
    Thread.sleep(NAP);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    log.info("TransactionID=" + transferBody.getTransactionID());
    if (transferBody.getTransactionID() == null || !transferBody.hasTransactionID()) {
      return;
    }
    TransactionReceipt txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), CryptoServiceTest.cstub);
    Assert.assertNotNull(txReceipt);
    log.info("txReceipt=" + txReceipt);
    long transferTransactionFee = getTransactionFee(transfer1);
    String result = "Crypto Transfer Sig=1, memo=1 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (transferTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_TRANSFER_MEMO_1_KEY_1_DUR_30 + feeVariance;
    long minTransactionFee = CRYPTO_TRANSFER_MEMO_1_KEY_1_DUR_30 - feeVariance;
    if(transferTransactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transferTransactionFee);
      Assert.assertTrue(minTransactionFee < transferTransactionFee);
    }
    CryptoServiceTest.accountKeyTypes = new String[]{"single", "keylist", "thresholdKey"};
    COMPLEX_KEY_SIZE = 3;
  }

  public void cryptoTransferFeeTest_insufficientBalance() throws Throwable {

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    long durationSeconds = 30 * 24 * 60 * 60; //1 Month (30 Days)
    CryptoServiceTest.accountKeyTypes = new String[]{"single"};
    COMPLEX_KEY_SIZE = 1;
  //  Key key = genComplexKey("single");
    Key key = KeyExpansion.genSingleEd25519KeyByteEncodePubKey(TestHelperComplex.pubKey2privKeyMap);
    Transaction createAccountRequest = TestHelperComplex
            .createAccount(payerID, payerKey, nodeID, key, 100000, TestHelper.getCryptoMaxFee(), false,
                    10, durationSeconds);
    Thread.sleep(NAP);
    TransactionResponse response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
            .extractTransactionBody(createAccountRequest);
    TransactionID transactionID = body.getTransactionID();
    if (transactionID == null || !body.hasTransactionID()) {
      log.info("Transaction is null");
      return;
    }

    AccountID newlyCreateAccountId1 = getAccountID(createAccountRequest);
    TestHelperComplex.acc2ComplexKeyMap.put(newlyCreateAccountId1, key);
    // PrivateKey payerPrivateKey = pubKey2privKeyMap.get(payerKey);
    String memo = TestHelperComplex.getStringMemo(10);
    Transaction transfer1 = CryptoServiceTest.getSignedTransferTx(newlyCreateAccountId1, nodeID, newlyCreateAccountId1,
            account_2,
            1000, memo, 83740);
    log.info("Transferring 1000000 coin from 1st account to 2nd account....request=" + transfer1);
    long beforeBalance = getAccountBalance(newlyCreateAccountId1, queryPayerId, nodeID);
    long beforeBalance_ac2 = getAccountBalance(account_2, queryPayerId, nodeID);
    TransactionResponse transferRes = CryptoServiceTest.cstub.cryptoTransfer(transfer1);
    Thread.sleep(NAP);

    log.info("Before Balance = "+beforeBalance);
    Assert.assertNotNull(transferRes);
    log.info(
            "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    log.info("TransactionID=" + transferBody.getTransactionID());

    Query recordQuery = TestHelper.getTxRecordByTxId(transferBody.getTransactionID(), queryPayerId,
            queryPayerKeyPair, nodeID, TestHelper.getCryptoMaxFee(),
            ResponseType.ANSWER_ONLY);
    Response transactionRecord = CryptoServiceTest.cstub.getTxRecordByTxID(recordQuery);
    while(transactionRecord.getTransactionGetRecord().getTransactionRecord().getReceipt().getStatus() ==ResponseCodeEnum.UNKNOWN) {
      transactionRecord = CryptoServiceTest.cstub.getTxRecordByTxID(recordQuery);
    }
    log.info("transactionRecord="+transactionRecord);
    long transactionFee = transactionRecord.getTransactionGetRecord().getTransactionRecord()
            .getTransactionFee();
    String result = "Crypto Transfer Sig=1, memo=1 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    Thread.sleep(NAP);
    long afterBalance = getAccountBalance(newlyCreateAccountId1, queryPayerId, nodeID);
    long afterBalance_ac2 = getAccountBalance(account_2, queryPayerId, nodeID);
    log.info(
            "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    log.info("Before Balance = "+beforeBalance);
    log.info("After Balance = "+afterBalance);
    log.info("Before Balance ac2= "+beforeBalance_ac2);
    log.info("After Balance ac2  = "+afterBalance_ac2);
    Thread.sleep(NAP);
    if(ResponseCodeEnum.INSUFFICIENT_TX_FEE!=transferRes.getNodeTransactionPrecheckCode()) {
      Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE, transactionRecord.getTransactionGetRecord().getTransactionRecord().getReceipt().getStatus());
      Assert.assertEquals(897, afterBalance);
    }
    CryptoServiceTest.accountKeyTypes = new String[]{"single", "keylist", "thresholdKey"};
    COMPLEX_KEY_SIZE = 3;
  }

  public void cryptoTransferMultiSigFeeTest() throws Exception {

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    long durationSeconds = 90 * 24 * 60 * 60; //1 Month (30 Days)
    CryptoServiceTest.accountKeyTypes = new String[]{"keylist"};
    COMPLEX_KEY_SIZE = 10;
    Key key = genComplexKey("keylist");
    Transaction createAccountRequest = TestHelperComplex
        .createAccount(payerID, payerKey, nodeID, key, 100000000L, TestHelper.getCryptoMaxFee(), false,
            10, durationSeconds);

    TransactionResponse response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
    Thread.sleep(NAP);
    TransactionBody transferBody = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
    log.info("TransactionID=" + transferBody.getTransactionID());
    if (transferBody.getTransactionID() == null || !transferBody.hasTransactionID()) {
      return;
    }
    long transactionFee = getTransactionFee(createAccountRequest);
    AccountID newlyCreateAccountId1 = getAccountID(createAccountRequest);
    TestHelperComplex.acc2ComplexKeyMap.put(newlyCreateAccountId1, key);
    String memo = TestHelperComplex.getStringMemo(10);
    Transaction transfer1 = CryptoServiceTest.getSignedTransferTx(queryPayerId, nodeID, newlyCreateAccountId1,
        account_2,
        1000, memo);

    log.info("Transferring 1000 coin from 1st account to 2nd account....request=" + transfer1);
    TransactionResponse transferRes = CryptoServiceTest.cstub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionReceipt txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), CryptoServiceTest.cstub);
    Assert.assertNotNull(txReceipt);
    log.info("txReceipt=" + txReceipt);
    long transferTransactionFee = getTransactionFee(transfer1);
    String result = "Crypto Transfer Sig=10, memo=10 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (transferTransactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_TRANSFER_MEMO_10_KEY_10_DUR_90 + feeVariance;
    long minTransactionFee = CRYPTO_TRANSFER_MEMO_10_KEY_10_DUR_90 - feeVariance;
    if(transferTransactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transferTransactionFee);
      Assert.assertTrue(minTransactionFee < transferTransactionFee);
    }
    CryptoServiceTest.accountKeyTypes = new String[]{"single", "keylist", "thresholdKey"};
    COMPLEX_KEY_SIZE = 3;
  }
  



  public void cryptoDelAccountFeeTest() throws Throwable {
    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);

    Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
    Key accKey = TestHelperComplex.acc2ComplexKeyMap.get(account_1);
    List<Key> keys = new ArrayList<>();
    keys.add(payerKey);
    keys.add(accKey);
       
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(account_1).setTransferAccountID(payerID)
        .build();
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    TransactionID  transactionID = TransactionID.newBuilder().setAccountID(payerID)
        .setTransactionValidStart(timestamp).build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100000000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(false)
        .setMemo("Crypto Delete")        
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction deletetx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();   
   
    Transaction signDelete = TransactionSigner
        .signTransactionComplex(deletetx, keys, TestHelperComplex.pubKey2privKeyMap);
    TransactionResponse response = CryptoServiceTest.cstub.cryptoDelete(signDelete);
    Thread.sleep(NAP);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signDelete);
     transactionID = body.getTransactionID();
    if (transactionID == null || !transactionID.hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    cache.addTransactionID(transactionID);
    Thread.sleep(NAP);
    long transactionFee = getTransactionFee(signDelete);
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    log.info(" Payer KeyList Count on List : " + payerKey.getKeyList().getKeysCount());
    log.info(" Payer KeyList Count on Key: " + TestHelperComplex.acc2ComplexKeyMap.get(payerID).getKeyList()
        .getKeysCount());
    log.info("Signature Pair Count: " + signDelete.getSigMap().getSigPairCount());
    
    
    log.info("Crypto Delete Fee: " + transactionFee);
    String result = "Crypto Delete Account Sig=1, memo=1 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_DELETE_MEMO_13_KEY_1 + feeVariance;
    long minTransactionFee = CRYPTO_DELETE_MEMO_13_KEY_1 - feeVariance;
    if(transactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }
  
  
  public void cryptoDelAccountMultiSigFeeTest() throws Throwable {

    List<Key> queryPayerKeyList = new ArrayList<>();
    queryPayerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(queryPayerId));

//    Transaction createAccountRequest = TestHelperComplex
//        .createAccountMultiSig(queryPayerId, nodeID, keyPairList, 10000000000l,
//            queryPayerKeyList, 1, 5000);
//    TransactionResponse response = cstub.createAccount(createAccountRequest);

    AccountID multiKeyAccountId = createAccountWithListKey(CryptoServiceTest.genesisAccountID, nodeID,
        CryptoServiceTest.DEFAULT_INITIAL_ACCOUNT_BALANCE, true, true, 10);
    //  AccountID multiKeyAccountId = getAccountID(createAccountRequest);

    long payerAccountBalance_before = getAccountBalance(payerID, queryPayerId, nodeID);

    //   Key payerKey = acc2ComplexKeyMap.get(payerID);
    //  Key accKey = acc2ComplexKeyMap.get(account_1);
    List<Key> keys = new ArrayList<>();
    Key keyMultiSigAcct = TestHelperComplex.acc2ComplexKeyMap.get(multiKeyAccountId);
    keys.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));
    keys.add(keyMultiSigAcct);   
    
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(multiKeyAccountId).setTransferAccountID(payerID)
        .build();
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    TransactionID  transactionID = TransactionID.newBuilder().setAccountID(payerID)
        .setTransactionValidStart(timestamp).build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100000000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(false)
        .setMemo("Crypto Delete")        
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction deletetx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    
    Transaction signDelete = TransactionSigner
        .signTransactionComplex(deletetx, keys, TestHelperComplex.pubKey2privKeyMap);
    
    log.info("deleteTransaction : " + signDelete);
    TransactionResponse deleteResponse = CryptoServiceTest.cstub.cryptoDelete(signDelete);

    Assert.assertNotNull(deleteResponse);
    Assert.assertEquals(ResponseCodeEnum.OK, deleteResponse.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + deleteResponse.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signDelete);
     transactionID = body.getTransactionID();
    if (transactionID == null || !transactionID.hasTransactionValidStart()) {
      log.info("Transaction is null");
      return;
    }
    cache.addTransactionID(transactionID);
    Thread.sleep(NAP);
    long transactionFee = getTransactionFee(signDelete);
    long feeVariance = (transactionFee * FEE_VARIANCE_PERCENT) / 100;
    long maxTransactionFee = CRYPTO_DELETE_MEMO_13_KEY_10 + feeVariance;
    long minTransactionFee = CRYPTO_DELETE_MEMO_13_KEY_10 - feeVariance;
    if(transactionFee!=0) {
      Assert.assertTrue(maxTransactionFee > transactionFee);
      Assert.assertTrue(minTransactionFee < transactionFee);
    }
    long payerAccountBalance_after = getAccountBalance(payerID, queryPayerId, nodeID);
    String result = "Crypto Delete Account Total Sig=11, memo=13 :" + transactionFee;
    testResults.add(result);
    log.info(result);
    log.info("payerAccountBalance_before=" + payerAccountBalance_before);
    // Amount will be increased as balance of deleted acount is transferred to payer
    log.info("payerAccountBalance_after=" + payerAccountBalance_after);
  }




}
