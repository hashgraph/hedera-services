package com.hedera.services.legacy.crypto;

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

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * This test create three account with following balance : payer account = genesis account 1. 1st
 * account = 1000 2. 2nd account = 1000 3. 3rd account = 1000
 *
 * Behaviour : transfer 1000 coins between 1st and 2nd account  and at the same time send request
 * for transfer 1000 coins between 1st and 3rd account. Expected : 1st transfer should be success
 * and 2nd transfer should return INSUFFICIENT_ACCOUNT_BALANCE
 *
 * @author Akshay
 * @Date : 8/15/2018
 */
public class DoubleSpendingTest {

  private static final Logger log = LogManager.getLogger(DoubleSpendingTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static ManagedChannel channel;
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static String host;

  public DoubleSpendingTest(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    DoubleSpendingTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    DoubleSpendingTest multipleCryptoTransfers = new DoubleSpendingTest(port, host);
    multipleCryptoTransfers.demo();
  }

  public void demo()
      throws Exception {


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
    Transaction transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, firstPair, 100000l,
            Collections.singletonList(genesisPrivateKey));
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = CommonUtils.extractTransactionBody(transaction);
    AccountID newlyCreateAccountId1 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    // create 2nd account by payer as genesis
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, secondPair, 1000l,
            Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create second account :: " + response
        .getNodeTransactionPrecheckCode().name());

    body = CommonUtils.extractTransactionBody(transaction);
    AccountID newlyCreateAccountId2 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId2);
    Assert
        .assertTrue(newlyCreateAccountId2.getAccountNum() > newlyCreateAccountId1.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    // create 3rd account by payer as genesis
    KeyPair thirdPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, thirdPair, 1000l,
            Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create Third account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    body = CommonUtils.extractTransactionBody(transaction);
    AccountID newlyCreateAccountId3 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId3);
    Assert
        .assertTrue(newlyCreateAccountId3.getAccountNum() > newlyCreateAccountId2.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId3.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    // transfer between 1st to 2nd account 1000 coin
    Transaction transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
        newlyCreateAccountId2, payerAccount,
        genKeyPair, defaultNodeAccount, 100000);
    // transfer 1st and 3rd account coin
    Transaction transfer2 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
        newlyCreateAccountId3, payerAccount,
        genKeyPair, defaultNodeAccount, 1000);

    int size = 2;
    ExecutorService threads = Executors.newFixedThreadPool(size);
    List<Callable<TransactionResponse>> torun = new ArrayList<>(size);
    torun.add(() -> stub.cryptoTransfer(transfer1));
    torun.add(() -> stub.cryptoTransfer(transfer2));
    // all tasks executed in different threads, at 'once'.
    List<Future<TransactionResponse>> futures = threads.invokeAll(torun);
    // no more need for the threadpool
    threads.shutdown();

    TransactionResponse transferRes1 = null;
    TransactionResponse transferRes2 = null;
    try {
      log.info("1. Transferring 1000 coin from 1st account to 2nd account....");
      transferRes1 = futures.get(0).get();
      log.info("2. Transferring 1000 coin from 1st account to 3rd account....");
      transferRes2 = futures.get(1).get();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    Assert.assertNotNull(transferRes1);
    Assert.assertNotNull(transferRes2);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes2.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer1 :: " + transferRes1.getNodeTransactionPrecheckCode().name());
    log.info(
        "Pre Check Response transfer2 :: " + transferRes1.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody1 = CommonUtils.extractTransactionBody(transfer1);
    Query query = Query.newBuilder().setTransactionGetReceipt(
        RequestBuilder.getTransactionGetReceiptQuery(transferBody1.getTransactionID(),
            ResponseType.ANSWER_ONLY)).build();
    TransactionReceipt txReceipt1 =
        TestHelper.fetchReceipts(query, stub, null, host).getTransactionGetReceipt().getReceipt();
    log.info("Tx Receipt of 1st Transfer request :: " + txReceipt1.getStatus());
    TransactionBody transferBody2 = CommonUtils.extractTransactionBody(transfer2);
    query = Query.newBuilder().setTransactionGetReceipt(
        RequestBuilder.getTransactionGetReceiptQuery(transferBody2.getTransactionID(),
            ResponseType.ANSWER_ONLY)).build();
    TransactionReceipt txReceipt2 =
        TestHelper.fetchReceipts(query, stub, null, host).getTransactionGetReceipt().getReceipt();
    Assert.assertNotEquals(txReceipt1.getStatus(), txReceipt2.getStatus());
    log.info("Tx Receipt of 2nd Transfer request :: " + txReceipt2.getStatus());
    log.info("-------------------¯\\_(ツ)_/¯------------------------");
  }

}
