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

import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionID.Builder;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Negative tests for get info, get balance, and get tx record
 *
 * @author akshay Date 2019-04-19 11:01
 */
public class NegativeCryptoQueryTest {

  private static final Logger log = LogManager.getLogger(NegativeCryptoQueryTest.class);

  private static ManagedChannel channel;
  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private AccountID newlyCreateAccountId1;

  private NegativeCryptoQueryTest(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    NegativeCryptoQueryTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[])
      throws Exception {

    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    NegativeCryptoQueryTest multipleCryptoTransfers = new NegativeCryptoQueryTest(port, host);
    multipleCryptoTransfers.demo();
  }

  public void demo()
      throws Exception {

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

    TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

    TransactionBody body = createAccount(genKeyPair, payerAccount, defaultNodeAccount);

    log.info("Get Account Info By providing wrong account id...");
    AccountID accountIdBuild = RequestBuilder.getAccountIdBuild(120000L, 0L, 0L);
    Response accountInfoResponse = TestHelper.getCryptoGetAccountInfo(stub, accountIdBuild,
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountInfoResponse);
    Assert.assertTrue(accountInfoResponse.hasCryptoGetInfo());
    Assert.assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID,
        accountInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get Account Info By providing wrong payer account id...");
    accountInfoResponse = TestHelper.getCryptoGetAccountInfo(stub, newlyCreateAccountId1,
        accountIdBuild, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountInfoResponse);
    Assert.assertTrue(accountInfoResponse.hasCryptoGetInfo());
    Assert.assertEquals(
        accountInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode(),
        ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND);
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get Account Info By providing wrong private key of payer account...");
    KeyPair keyPair = new KeyPairGenerator().generateKeyPair();
  accountInfoResponse = TestHelper.getCryptoGetAccountInfo(stub, newlyCreateAccountId1,
        payerAccount, keyPair, defaultNodeAccount);
    Assert.assertNotNull(accountInfoResponse);
    Assert.assertTrue(accountInfoResponse.hasCryptoGetInfo());
    Assert.assertEquals(
        accountInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode(),
        ResponseCodeEnum.INVALID_SIGNATURE);
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get balance api by providing wrong account id");
    Response accountBalance = TestHelper.getCryptoGetBalance(stub, accountIdBuild,
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountBalance);
    Assert.assertTrue(accountBalance.hasCryptogetAccountBalance());
    Assert.assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID,
        accountBalance.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get balance api by providing wrong payer account id");
    accountBalance = TestHelper.getCryptoGetBalance(stub, newlyCreateAccountId1,
        accountIdBuild, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountBalance);
    Assert.assertTrue(accountBalance.hasCryptogetAccountBalance());
    // Since balance query is free, these assertions should be OK
   /* Assert.assertEquals(ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND,
        accountBalance.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode());*/
    Assert.assertEquals(ResponseCodeEnum.OK,
        accountBalance.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get balance api by providing wrong private key of payer account...");
    accountBalance = TestHelper.getCryptoGetBalance(stub, newlyCreateAccountId1,
        payerAccount, keyPair, defaultNodeAccount);
    Assert.assertNotNull(accountBalance);
    Assert.assertTrue(accountBalance.hasCryptogetAccountBalance());
    Assert.assertEquals(ResponseCodeEnum.OK,
        accountBalance.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get txRecordByTxID api by providing wrong account id in TxID");
    TransactionID transactionID = body.getTransactionID();
    Builder builder = transactionID.toBuilder();
    builder.setAccountID(accountIdBuild);
    Response recordByTxID = TestHelper.getTxRecordByTxID(stub, builder.build(),
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(recordByTxID);
    Assert.assertTrue(recordByTxID.hasTransactionGetRecord());
    Assert.assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID,
        recordByTxID.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get txRecordByTxID api by providing wrong payer account id");
    recordByTxID = TestHelper.getTxRecordByTxID(stub, transactionID,
        accountIdBuild, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(recordByTxID);
    Assert.assertTrue(recordByTxID.hasTransactionGetRecord());
    Assert.assertEquals(ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND,
        recordByTxID.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get txRecordByTxID api by providing wrong private key of payer account...");
    recordByTxID = TestHelper.getTxRecordByTxID(stub, transactionID,
        payerAccount, keyPair, defaultNodeAccount);
    Assert.assertNotNull(recordByTxID);
    Assert.assertTrue(recordByTxID.hasTransactionGetRecord());
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE,
        recordByTxID.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get accountRecords api by providing wrong account id");
    Response accountRecords = TestHelper.getAccountRecords(stub, accountIdBuild,
        payerAccount, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountRecords);
    Assert.assertTrue(accountRecords.hasCryptoGetAccountRecords());
    Assert.assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID,
        accountRecords.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get accountRecords api by providing wrong payer account id");
    accountRecords = TestHelper.getAccountRecords(stub, newlyCreateAccountId1,
        accountIdBuild, genKeyPair, defaultNodeAccount);
    Assert.assertNotNull(accountRecords);
    Assert.assertTrue(accountRecords.hasCryptoGetAccountRecords());
    Assert.assertEquals(ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND,
        accountRecords.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    log.info("Get accountRecords api by providing wrong private key of payer account...");
    accountRecords = TestHelper.getAccountRecords(stub, newlyCreateAccountId1,
        payerAccount, keyPair, defaultNodeAccount);
    Assert.assertNotNull(accountRecords);
    Assert.assertTrue(accountRecords.hasCryptoGetAccountRecords());
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE,
        accountRecords.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode());
    log.info("assertion completed...");
    log.info("--------------------------------------");

    // get tx record of payer account by txId
    log.info("Get Tx record by Tx Id...by providing wrong payer account id");
    long queryFeeForTxRecord = FeeClient.getCostForGettingTxRecord();
    Assert.assertEquals(0, queryFeeForTxRecord);
    log.info("The COST_ANSWER Transaction record query fee :: "+queryFeeForTxRecord);
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
  }


  private TransactionBody createAccount(KeyPair genesisKeyPair, AccountID payerAccount,
      AccountID defaultNodeAccount) throws Exception {
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper.createAccountWithSigMap(payerAccount, defaultNodeAccount,
        firstPair, 1000000l, genesisKeyPair);
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
    newlyCreateAccountId1 = txReceipt1.getAccountID();
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
    return body;
  }
}
