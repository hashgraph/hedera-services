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
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * This test created 5 account by using genesis as a payer account and do transfer between first two
 * account. Also execute call against newly created account : get account info, accountUpdate and
 * get account records.
 *
 * @author Akshay
 * @Date : 8/13/2018
 */

public class MultipleCryptoTransfers {

  private static final Logger log = LogManager.getLogger(MultipleCryptoTransfers.class);

  private static ManagedChannel channel;
  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  private long NEW_ACCOUNT_BALANCE = 1000000000;

  public MultipleCryptoTransfers(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    MultipleCryptoTransfers.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public MultipleCryptoTransfers() {
  }

  public static void main(String args[])
      throws Exception {

    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    MultipleCryptoTransfers multipleCryptoTransfers = new MultipleCryptoTransfers(port, host);
    AccountID accountID = multipleCryptoTransfers.demo();
    //Thread.sleep(60000);
    //  multipleCryptoTransfers.runSingleTransaction(RequestBuilder.getAccountIdBuild(1005l,0l,0l));
  }

  public AccountID demo()
      throws Exception {

 //   Path path = Paths.get(fileName);
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

    // create 1st account by payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    String privkey = Common.bytes2Hex(firstPair.getPrivate().getEncoded());
    String publicKey = Common.bytes2Hex(firstPair.getPublic().getEncoded());
    String genPrivateKey = Common.bytes2Hex(genesisPrivateKey.getEncoded());
    String genPubKey = Common.bytes2Hex(genKeyPair.getPublic().getEncoded());
    log.info("private key :: " + privkey);
    log.info("public key :: " + publicKey);
    log.info("gen private key :: " + genPrivateKey);
    log.info("gen public key :: " + genPubKey);
    Transaction transaction = TestHelper.createAccountWithSigMap(payerAccount, defaultNodeAccount,
        firstPair, 1000000l, genKeyPair);
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
    AccountID newlyCreateAccountId1 = txReceipt1.getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getHbarEquiv() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getCentEquiv() > 0);
    Assert.assertTrue(
        txReceipt1.getExchangeRate().getCurrentRate().getExpirationTime().getSeconds() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getHbarEquiv() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getCentEquiv() > 0);
    Assert.assertTrue(
        txReceipt1.getExchangeRate().getNextRate().getExpirationTime().getSeconds() > 0);
    log.info("--------------------------------------");

    // get tx record of payer account by txId
    log.info("Get Tx record by Tx Id...");
    long queryFeeForTxRecord = FeeClient.getCostForGettingTxRecord();

    Query query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, queryFeeForTxRecord,
        ResponseType.COST_ANSWER);
    Response transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertEquals(ResponseCodeEnum.OK,
        transactionRecord.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    log.info("The cost of getting Transaction Record ===> " +
        transactionRecord.getTransactionGetRecord().getHeader().getCost());

    long txRecordFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, txRecordFee, ResponseType.ANSWER_ONLY);
    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    TransactionRecord transactionRecordResponse = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
            > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
            > 0);
    Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
        .getExpirationTime().getSeconds() > 0);
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    log.info("Tx Record is successfully retrieve and asserted.");
    log.info("--------------------------------------");

    // create 2nd account by payer as genesis
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, secondPair, 1000000l,
            genKeyPair);
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

    log.info("Get Tx record by Tx Id...");
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, queryFeeForTxRecord, ResponseType.COST_ANSWER);
    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);

    log.info("The cost of getting Transaction Record ===>" +
        transactionRecord.getTransactionGetRecord().getHeader().getCost());

    txRecordFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, txRecordFee, ResponseType.ANSWER_ONLY);
    transactionRecord = stub.getTxRecordByTxID(query);

    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    transactionRecordResponse = transactionRecord.getTransactionGetRecord().getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
            > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
            > 0);
    Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
        .getExpirationTime().getSeconds() > 0);
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    log.info("Tx Record is successfully retrieve and asserted.");
    log.info("--------------------------------------");

    // create 3rd account by payer as genesis
    KeyPair thirdPair = new KeyPairGenerator().generateKeyPair();

    transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, thirdPair, NEW_ACCOUNT_BALANCE,
            genKeyPair);
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create Third account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId3 =
        TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId3);
    Assert
        .assertTrue(newlyCreateAccountId3.getAccountNum() > newlyCreateAccountId2.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId3.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Get Tx record by Tx Id...");

    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, TestHelper.getCryptoMaxFee(),
        ResponseType.ANSWER_ONLY);
    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    transactionRecordResponse = transactionRecord.getTransactionGetRecord().getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
            > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
            > 0);
    Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
        .getExpirationTime().getSeconds() > 0);
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    log.info("Tx Record is successfully retrieve and asserted.");
    log.info("--------------------------------------");

    // create 4th account by payer as genesis
    KeyPair fourthPair = new KeyPairGenerator().generateKeyPair();

    transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, fourthPair, 1000000l,
            genKeyPair);

    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create fourth account :: " + response
        .getNodeTransactionPrecheckCode().name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId4 =
        TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId4);
    Assert
        .assertTrue(newlyCreateAccountId4.getAccountNum() > newlyCreateAccountId3.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId4.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Get Tx record by Tx Id...");

    //	  Thread.sleep(5000);
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, TestHelper.getCryptoMaxFee(), ResponseType.ANSWER_ONLY);

    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    transactionRecordResponse = transactionRecord.getTransactionGetRecord().getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
            > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
            > 0);
    Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
        .getExpirationTime().getSeconds() > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getNextRate().getHbarEquiv() > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getNextRate().getCentEquiv() > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getNextRate().getExpirationTime()
            .getSeconds() > 0);
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    log.info("Tx Record is successfully retrieve and asserted.");
    log.info("--------------------------------------");

    // create 5th account by payer as genesis
    KeyPair fifthPair = new KeyPairGenerator().generateKeyPair();

    transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, fifthPair, 1000000l,
            genKeyPair);
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create fifth account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId5 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId5);
    Assert
        .assertTrue(newlyCreateAccountId5.getAccountNum() > newlyCreateAccountId4.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId5.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Get Tx record by Tx Id...");

    // Thread.sleep(5000);
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, TestHelper.getCryptoMaxFee(), ResponseType.ANSWER_ONLY);

    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    transactionRecordResponse = transactionRecord.getTransactionGetRecord().getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
            > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
            > 0);
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    log.info("Tx Record is successfully retrieve and asserted.");
    log.info("--------------------------------------");

    // transfer between 1st to 2nd account by using payer account as 3rd account
    Transaction transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
        newlyCreateAccountId2, newlyCreateAccountId3,
        thirdPair, defaultNodeAccount, 1000L);

    log.info("Transferring 1000 coin from 1st account to 2nd account....");
    TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
    TransactionReceipt txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    Assert.assertEquals(txReceipt.getStatus(), ResponseCodeEnum.SUCCESS);
    log.info("-----------------------------------------");

    log.info("Get Tx record by Tx Id...");
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, queryFeeForTxRecord, ResponseType.COST_ANSWER);
    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);

    txRecordFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, txRecordFee, ResponseType.ANSWER_ONLY);
    transactionRecord = stub.getTxRecordByTxID(query);

    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    transactionRecordResponse = transactionRecord.getTransactionGetRecord().getTransactionRecord();
    Thread.sleep(5000);
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
            > 0);
    Assert.assertTrue(
        transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
            > 0);
    Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
        .getExpirationTime().getSeconds() > 0);
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    Assert.assertTrue(transactionRecordResponse.hasTransferList());
    log.info("Tx Record is successfully retrieve and asserted.");
    log.info("--------------------------------------");

    // Get account Info of 1st Account
    Response accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, newlyCreateAccountId1, newlyCreateAccountId3,
            thirdPair, defaultNodeAccount);
    log.info(
        "Balance ac-1 = " + accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());

    Assert
        .assertEquals(999000, accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());
    assertAccountInfoDetails(newlyCreateAccountId1, accountInfoResponse);

    // Get account Info of 2nd Account
    accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, newlyCreateAccountId2, newlyCreateAccountId3,
            thirdPair, defaultNodeAccount);
    log.info(
        "Balance ac-2= " + accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());
    Assert.assertEquals(1001000,
        accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());
    assertAccountInfoDetails(newlyCreateAccountId2, accountInfoResponse);
    log.info("-----------------------------------------");

    // update account
    //  log.info("updating duration of account :: " + newlyCreateAccountId1);
    //  AccountID acctID = newlyCreateAccountId1;

    Duration autoRenew = RequestBuilder.getDuration(CustomPropertiesSingleton.getInstance().getAccountDuration());
    Transaction updateaccount1 = TestHelper.updateAccount(newlyCreateAccountId1, payerAccount,
        genesisPrivateKey, defaultNodeAccount, autoRenew);
    List<PrivateKey> privateKeyList = new ArrayList<>();
    privateKeyList.add(genesisPrivateKey);
    privateKeyList.add(firstPair.getPrivate());
    Transaction signUpdate = TransactionSigner.signTransaction(updateaccount1, privateKeyList);
    log.info("updateAccount request=" + signUpdate);
    response = stub.updateAccount(signUpdate);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody updateAccountBody = TransactionBody.parseFrom(updateaccount1.getBodyBytes());
    txReceipt = TestHelper.getTxReceipt(updateAccountBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, newlyCreateAccountId1, payerAccount,
            genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountInfoResponse);
    log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());
    Assert.assertEquals(CustomPropertiesSingleton.getInstance().getAccountDuration(), accountInfoResponse.getCryptoGetInfo().getAccountInfo()
        .getAutoRenewPeriod().getSeconds());
    log.info("updating successful" + "\n");
    log.info("--------------------------------------");

    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        newlyCreateAccountId1);
    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        newlyCreateAccountId2);
    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        newlyCreateAccountId3);
    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        newlyCreateAccountId4);
    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        newlyCreateAccountId5);
    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        payerAccount);
    log.info("-----------------¯\\_(ツ)_/¯---------------------");
    return newlyCreateAccountId3;
  }

  private void getTransactionRecordsByAccountId(KeyPair genesisKeyPair,
      AccountID payerAccount, AccountID defaultNodeAccount, AccountID accountID) throws Exception {
    log.info("Get Tx records by account Id...");
    long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetAccountRecords);
    Query query = TestHelper
        .getTxRecordByAccountId(accountID, payerAccount, genesisKeyPair, defaultNodeAccount,
            fee, ResponseType.COST_ANSWER);
    Response transactionRecord = stub.getAccountRecords(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getCryptoGetAccountRecords());

    fee = transactionRecord.getCryptoGetAccountRecords().getHeader().getCost();
    query = TestHelper.getTxRecordByAccountId(accountID, payerAccount, genesisKeyPair,
        defaultNodeAccount, fee, ResponseType.ANSWER_ONLY);
    transactionRecord = stub.getAccountRecords(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertEquals(ResponseCodeEnum.OK,
        transactionRecord.getCryptoGetAccountRecords().getHeader()
            .getNodeTransactionPrecheckCode());
    Assert.assertNotNull(transactionRecord.getCryptoGetAccountRecords());
    Assert.assertEquals(accountID, transactionRecord.getCryptoGetAccountRecords().getAccountID());
    List<TransactionRecord> recordList = transactionRecord.getCryptoGetAccountRecords()
        .getRecordsList();
    log.info(
        "Tx Records List for account ID " + accountID.getAccountNum() + " :: " + recordList.size());
    log.info("--------------------------------------");
  }

  private void assertAccountInfoDetails(AccountID newlyCreateAccountId1,
      Response accountInfoResponse) {
    Assert.assertNotNull(accountInfoResponse);
    Assert.assertNotNull(accountInfoResponse.getCryptoGetInfo());
    CryptoGetInfoResponse.AccountInfo accountInfo1 = accountInfoResponse.getCryptoGetInfo()
        .getAccountInfo();
    log.info("Account Info of Account ID " + newlyCreateAccountId1.getAccountNum());
    log.info(accountInfo1);
    Assert.assertNotNull(accountInfo1);
    Assert.assertEquals(newlyCreateAccountId1, accountInfo1.getAccountID());
    //  Assert.assertEquals(firstPair.getPublic().toString(), accountInfo1.getKey().getKeyList().getKeys(0).getEd25519().toStringUtf8());
    Assert.assertEquals(5000000000000000000l, accountInfo1.getGenerateReceiveRecordThreshold());
    Assert.assertEquals(5000000000000000000l, accountInfo1.getGenerateSendRecordThreshold());
    Assert.assertFalse(accountInfo1.getReceiverSigRequired());
    Duration renewal = RequestBuilder.getDuration(5000);
    //  Assert.assertEquals(renewal, accountInfo1.getAutoRenewPeriod());
    //z  Assert.assertEquals(RequestBuilder.getExpirationTime(renewal), accountInfo1.getExpirationTime());
  }

  private void runSingleTransaction(AccountID accountID) throws Exception {
 //
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();
    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        payerAccount);
    CommonUtils.nap(3);
    getTransactionRecordsByAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        accountID);
  }


}
