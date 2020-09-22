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

import com.hedera.services.legacy.client.util.Common;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * This test covers : 1. Create a/c with generate Record = true and assert response from tx record
 * by tx id and account record api 2. Create a/c with generate Record = false and assert response
 * from tx record by tx id and account record api
 *
 * @author Akshay
 * @Date : 10/9/2018
 */
public class TxRecordTest {

  private static final Logger log = LogManager.getLogger(TxRecordTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static ManagedChannel channel;
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  private TxRecordTest(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String[] args)
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    TxRecordTest txRecordTest = new TxRecordTest(port, host);
    txRecordTest.demo();
  }

  public void TxRecordTest() throws Exception {
    List<AccountKeyListObj> genesisAccount = TestHelper.getGenAccountKey();
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = TestHelper.getDefaultNodeAccount();
    log.info("Starting  Long Running test on Tx Record ");
    log.info("Creating account with generating tx record...");
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 50000000l,
            genKeyPair);
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId1 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);

    log.info("Get Tx record by Tx Id....");
    Response transactionRecord = TestHelper.getTxRecordByTxID(stub, body.getTransactionID(),
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    TransactionRecord transactionRecordResponse = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    Assert.assertNotNull(transactionRecordResponse.getTransactionHash());
    log.info(
        "Tx Record is successfully retrieve and asserted. record....");
    Thread.sleep(200000);
    //Create some other account to trigger record eviction
    Transaction transaction_new = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 50000l,
            genKeyPair);
    TransactionResponse response_new = stub.createAccount(transaction_new);
    log.info("New Account Created just to create some event on platform ");
    Assert.assertNotNull(response_new);
    Assert.assertEquals(ResponseCodeEnum.OK, response_new.getNodeTransactionPrecheckCode());
    Thread.sleep(30000); //Sleep for 30 seconds more
    log.info("Get Tx record by Tx Id....After 4 Minutes");
    transactionRecord = TestHelper.getTxRecordByTxID(stub, body.getTransactionID(),
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    transactionRecordResponse = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();
    log.info("Tx Record after 4 minutes: " + transactionRecordResponse.getReceipt().getStatus());
    log.info("Tx Record response: " + transactionRecord);
    Assert.assertEquals(ResponseCodeEnum.RECORD_NOT_FOUND,
        transactionRecord.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    log.info("------------------¯\\_(ツ)_/¯---------------------");
  }

  public void demo()
      throws Exception {
    List<Key> keyList;
    HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
    List<AccountKeyListObj> genesisAccount = TestHelper.getGenAccountKey();
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = TestHelper.getDefaultNodeAccount();

    TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

    log.info("Creating account with generating tx record...");
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 500_000_000_000L,
            genKeyPair);
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId1 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Get Tx record by Tx Id....");
    Response transactionRecord = TestHelper.getTxRecordByTxID(stub, body.getTransactionID(),
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    TransactionRecord transactionRecordResponse = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    Assert.assertNotNull(transactionRecordResponse.getTransactionHash());
    Assert.assertEquals(transactionRecordResponse.getTransactionHash(),
            com.hedera.services.legacy.proto.utils.CommonUtils.sha384HashOf(transaction));
    Assert.assertTrue(transactionRecordResponse.getTransactionFee() >= 0);
    log.info("Tx Record is successfully retrieve and asserted. record....");

    log.info("--------------------------------------");

    log.info("Creating account without generating tx record....");
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper.createAccountWithSigMap(payerAccount, defaultNodeAccount, secondPair,
        50000000000L, genKeyPair);
    Transaction.Builder tx = transaction.toBuilder();

    TransactionBody txBody = TransactionBody.parseFrom(tx.getBodyBytes());
    TransactionBody.Builder builder = txBody.toBuilder();
    CryptoCreateTransactionBody.Builder createBuilder = builder.getCryptoCreateAccount()
        .toBuilder();
    createBuilder.setSendRecordThreshold(1100000L);
    TransactionBody newTransactionBody = builder.setGenerateRecord(false)
        .setCryptoCreateAccount(createBuilder).build();
    transaction = tx.setBodyBytes(newTransactionBody.toByteString()).build();

    keyList = Collections.singletonList(Common.PrivateKeyToKey(genesisPrivateKey));
    Common.addKeyMap(genKeyPair, pubKey2privKeyMap);
    transaction = TransactionSigner
        .signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);

    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create second account :: " + response
        .getNodeTransactionPrecheckCode().name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId2 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId2);
    Assert
        .assertTrue(newlyCreateAccountId2.getAccountNum() > newlyCreateAccountId1.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Get Tx record by Tx Id....");
    transactionRecord = TestHelper.getTxRecordByTxID(stub, body.getTransactionID(),
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    Assert.assertEquals(ResponseCodeEnum.OK,
        transactionRecord.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    TransactionRecord txRecordObject = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, txRecordObject.getReceipt().getStatus());
    Assert.assertEquals(body.getTransactionID(),
        txRecordObject.getTransactionID());
    Assert.assertEquals(body.getMemo(), txRecordObject.getMemo());
    Assert.assertNotNull(txRecordObject.getTransactionHash());
    Assert.assertEquals(txRecordObject.getTransactionHash(),
            com.hedera.services.legacy.proto.utils.CommonUtils.sha384HashOf(transaction));
    Assert.assertTrue(txRecordObject.getTransactionFee() >= 0);
    log.info("Tx record is retrieved from FCM...");

    log.info("--------------------------------------");
    log.info(
        "Creating account by using payer account as : " + newlyCreateAccountId1.getAccountNum());
    KeyPair thirdPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithSigMap(newlyCreateAccountId1, defaultNodeAccount, thirdPair, 10000000000L,
            genKeyPair);
    tx = transaction.toBuilder();

    txBody = TransactionBody.parseFrom(tx.getBodyBytes());
    builder = txBody.toBuilder();
    createBuilder = builder.getCryptoCreateAccount().toBuilder();
    createBuilder.setReceiveRecordThreshold(1100000L);
    newTransactionBody = builder.setGenerateRecord(false)
        .setCryptoCreateAccount(createBuilder).build();
    transaction = tx.setBodyBytes(newTransactionBody.toByteString()).build();

    keyList = Collections.singletonList(Common.PrivateKeyToKey(firstPair.getPrivate()));
    Common.addKeyMap(firstPair, pubKey2privKeyMap);
    transaction = TransactionSigner.signTransactionComplexWithSigMap(transaction,
        keyList, pubKey2privKeyMap);

    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create third account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId3 = TestHelper.getTxReceipt(body.getTransactionID(), stub)
        .getAccountID();
    Assert.assertNotNull(newlyCreateAccountId3);
    Assert.assertTrue(newlyCreateAccountId3.getAccountNum() > 0);
    log.info("Account ID " + newlyCreateAccountId3.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Get Account Tx record list for account :: " + newlyCreateAccountId1.getAccountNum());
    CommonUtils.nap(2);
    Response accountRecords = TestHelper
        .getAccountRecords(stub, newlyCreateAccountId1, payerAccount, genKeyPair,
            defaultNodeAccount);
    Assert.assertNotNull(accountRecords);
    Assert.assertNotNull(accountRecords.getCryptoGetAccountRecords());
    Assert.assertEquals(0, accountRecords.getCryptoGetAccountRecords().getRecordsList().size());
    log.info("--------------------------------------");

    log.info("Assert threshold for account id :: " + newlyCreateAccountId2.getAccountNum()
        + " & account id :: " + newlyCreateAccountId3.getAccountNum());
    Response accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, newlyCreateAccountId2, payerAccount, genKeyPair,
            defaultNodeAccount);
    Assert
        .assertEquals(1100000L, accountInfoResponse.getCryptoGetInfo().getAccountInfo()
            .getGenerateSendRecordThreshold());
    accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, newlyCreateAccountId3, payerAccount, genKeyPair,
            defaultNodeAccount);
    Assert
        .assertEquals(1100000L, accountInfoResponse.getCryptoGetInfo().getAccountInfo()
            .getGenerateReceiveRecordThreshold());
    log.info("--------------------------------------");

    log.info("Transfer between account id :: " + newlyCreateAccountId2.getAccountNum()
        + " & Account ID :: " + newlyCreateAccountId3.getAccountNum() + " with payer account as :: "
        + newlyCreateAccountId2.getAccountNum());

    Transaction transfer1 = TestHelper
        .createTransferSigMap(newlyCreateAccountId2, secondPair,
            newlyCreateAccountId3, newlyCreateAccountId2, secondPair,
            defaultNodeAccount, 1200000L);

    log.info("Transferring 120000 coin from 1st account to 2nd account....");
    TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    TransactionReceipt txReceipt = TestHelper
        .getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info("--------------------------------------");
    log.info("assert account id : " + newlyCreateAccountId2.getAccountNum() +
        " has generate two record with one as payer record and one as threshold record.."
        + "But getAccount record only get record from FCM so size will be 1. ");
    List<TransactionRecord> transactionRecords = testAccountRecords(newlyCreateAccountId2);
    log.info("transactionRecords--->>>" + transactionRecords);
    Assert.assertEquals(1, transactionRecords.size());
    log.info("------------------¯\\_(ツ)_/¯---------------------");
  }

  private List<TransactionRecord> testAccountRecords(AccountID accountID)
      throws Exception {
    log.info("Calling Account records which got created before restart...");
    List<AccountKeyListObj> genesisAccount = TestHelper.getGenAccountKey();
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = TestHelper.getDefaultNodeAccount();
    log.info("Get Account Tx record list for account :: " + accountID.getAccountNum());
    Response accountRecords = TestHelper
        .getAccountRecords(stub, accountID, payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountRecords);
    Assert.assertNotNull(accountRecords.getCryptoGetAccountRecords());
    return accountRecords.getCryptoGetAccountRecords().getRecordsList();
  }

}
