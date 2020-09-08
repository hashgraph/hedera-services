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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
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
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Test duplicate transactions
 *
 * @author Akshay
 * @Date : 8/15/2018
 */
public class DuplicateTransactionTest {


  private static final Logger log = LogManager.getLogger(DuplicateTransactionTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  public DuplicateTransactionTest(int port, String host) {
    // connecting to the grpc server on the port
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    DuplicateTransactionTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    DuplicateTransactionTest duplicateTransactionTest = new DuplicateTransactionTest(port, host);
    duplicateTransactionTest.demo();

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

    // create first account
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();

    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 10000000l,
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

    // create account duplicate transaction

    KeyPair FourthPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, FourthPair, 10000000l,
            genKeyPair);
    int size = 2;
    ExecutorService threads = Executors.newFixedThreadPool(size);
    List<Callable<TransactionResponse>> torun = new ArrayList<>(size);
    Transaction finalTransaction = transaction;
    torun.add(() -> stub.createAccount(finalTransaction));
    Transaction finalTransaction1 = transaction;
    torun.add(() -> stub.createAccount(finalTransaction1));
    // all tasks executed in different threads, at 'once'.
    List<Future<TransactionResponse>> futures = threads.invokeAll(torun);
    // no more need for the threadpool
    threads.shutdown();

    // submitting same transaction to same node from two different threads

    try {
      TransactionResponse response0 = futures.get(0).get();
      TransactionResponse response1 = futures.get(1).get();
      Assert.assertNotNull(response0);
      System.out.println("The transaction response is ::");
      System.out.println(response0.getNodeTransactionPrecheckCode());
      System.out.println(response1.getNodeTransactionPrecheckCode());
      Assert.assertEquals(ResponseCodeEnum.OK, response0.getNodeTransactionPrecheckCode());
      Assert.assertNotNull(response1);
      Assert.assertEquals(ResponseCodeEnum.OK, response1.getNodeTransactionPrecheckCode());

      log.info("Pre Check Response of Create fourth account :: " + response
          .getNodeTransactionPrecheckCode().name());

      TransactionBody finalTransactionBody = TransactionBody.parseFrom(finalTransaction.getBodyBytes());
      AccountID newlyCreateAccountId2 = TestHelper
          .getTxReceipt(finalTransactionBody.getTransactionID(), stub).getAccountID();
      Assert.assertNotNull(newlyCreateAccountId2);
      System.out.println(newlyCreateAccountId2);
      log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
      log.info("--------------------------------------");

      TransactionBody finalTransactionBody1 = TransactionBody.parseFrom(finalTransaction1.getBodyBytes());
      AccountID newlyCreateAccountId2rejected = TestHelper
          .getTxReceipt(finalTransactionBody1.getTransactionID(), stub).getAccountID();
      Assert.assertNotNull(newlyCreateAccountId2rejected);
      System.out.println(newlyCreateAccountId2rejected);
      log.info(
          "Account ID " + newlyCreateAccountId2rejected.getAccountNum() + " created successfully.");

      // assert that the

    } catch (ExecutionException e) {
      e.printStackTrace();
    }

    // needs to be fixed

    // same transaction ID but parameters in request are different. create account request
    // same transaction IDs being passed to two different nodes . 2 transactions differ in balance
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
//        Transaction transaction2 = createAccountSameTxnIDDiffReq1(timestamp,payerAccount,defaultNodeAccount,firstPair,1000l,Collections.singletonList(genesisPrivateKey));
//        Transaction transaction3 = createAccountSameTxnIDDiffReq1(timestamp,payerAccount,defaultNodeAccount,firstPair,10l,Collections.singletonList(genesisPrivateKey));

//        ExecutorService threads23 = Executors.newFixedThreadPool(size);
//        torun = new ArrayList<>(size);
//        torun.add(() -> stub.createAccount(transaction2));
//        torun.add(() -> stub.createAccount(transaction3));
//        // all tasks executed in different threads, at 'once'.
//        List<Future<TransactionResponse>> futures23 = threads23.invokeAll(torun);
//        // no more need for the threadpool
//        threads23.shutdown();
//
//        try {
//            TransactionResponse response2 = futures23.get(0).get();
//            Assert.assertNotNull(response2);
//            System.out.println("The transaction response is ::");
//            System.out.println(response2.getNodeTransactionPrecheckCode());
//            Assert.assertEquals(ResponseCodeEnum.OK, response2.getNodeTransactionPrecheckCode());
//            AccountID newlyCreatedAccoundID2 = TestHelper.getTxReceipt(transaction2.getBody().getTransactionID(),stub).getAccountID();
//            Assert.assertNotNull(newlyCreatedAccoundID2);
//            log.info("The newly created account id is ::"+ newlyCreatedAccoundID2.getAccountNum());
//            Response accountInfo2 = TestHelper.getCryptoGetAccountInfo(stub,newlyCreatedAccoundID2,payerAccount,genesisPrivateKey,defaultNodeAccount);
//            Assert.assertNotNull(accountInfo2);
//
//
//            TransactionResponse response3 = futures23.get(1).get();
//            Assert.assertNotNull(response3);
//            Assert.assertEquals(ResponseCodeEnum.OK, response3.getNodeTransactionPrecheckCode());
//            AccountID newlyCreatedAccoundID3 = TestHelper.getTxReceipt(transaction3.getBody().getTransactionID(),stub).getAccountID();
//            Assert.assertNotNull(newlyCreatedAccoundID3);
//            log.info("The newly created account ID is ::" + newlyCreatedAccoundID3.getAccountNum());
//            Response accountInfo3 = TestHelper.getCryptoGetAccountInfo(stub,newlyCreatedAccoundID3,payerAccount,genesisPrivateKey,defaultNodeAccount);
//            Assert.assertNotNull(accountInfo3);
//
//            if ((accountInfo2.getCryptoGetInfo().getAccountInfo().getBalance())==10){
//                log.info("request for balance 10l is picked first");
//                Assert.assertEquals(10l,accountInfo2.getCryptoGetInfo().getAccountInfo().getBalance());
//                log.info("request for balance 1000l is rejected by the platform");
//                Assert.assertNotEquals(1000l,accountInfo3.getCryptoGetInfo().getAccountInfo().getBalance());
//            }else{
//                log.info("request for balance 1000l is picked first");
//                Assert.assertEquals(1000l,accountInfo2.getCryptoGetInfo().getAccountInfo().getBalance());
//                log.info("request for balance 10l is rejected by the platform");
//                Assert.assertNotEquals(10l,accountInfo3.getCryptoGetInfo().getAccountInfo().getBalance());
//            }
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }

    // duplicate transaction tests for crypto transfers

    long genesisbalancebeforeTransfer = TestHelper
        .getCryptoGetAccountInfo(stub, payerAccount, payerAccount, genKeyPair,
            defaultNodeAccount).getCryptoGetInfo().getAccountInfo().getBalance();

    timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Transaction transaction4 = createTransfer(timestamp, payerAccount, genKeyPair,
        newlyCreateAccountId1, payerAccount, genKeyPair, defaultNodeAccount, 100l);
    Transaction transaction5 = createTransfer(timestamp, payerAccount, genKeyPair,
        newlyCreateAccountId1, payerAccount, genKeyPair, defaultNodeAccount, 1000l);

    ExecutorService threads45 = Executors.newFixedThreadPool(size);
    torun = new ArrayList<>(size);
    torun.add(() -> stub.cryptoTransfer(transaction4));
    torun.add(() -> stub.createAccount(transaction5));
    // all tasks executed in different threads, at 'once'.
    List<Future<TransactionResponse>> futures45 = threads45.invokeAll(torun);
    // no more need for the threadpool
    threads45.shutdown();

    try {

      TransactionResponse response4 = futures45.get(0).get();
      TransactionResponse response5 = futures45.get(1).get();
      Assert.assertEquals(ResponseCodeEnum.OK, response4.getNodeTransactionPrecheckCode());
      Assert.assertEquals(ResponseCodeEnum.OK, response5.getNodeTransactionPrecheckCode());
      TransactionBody transactionBody4 = TransactionBody.parseFrom(transaction4.getBodyBytes());
      TransactionReceipt txReceipt4 = TestHelper
          .getTxReceipt(transactionBody4.getTransactionID(), stub);
      Assert.assertNotNull(txReceipt4);

//            System.out.println(txReceipt4);

      long genesisbalanceAfterTransfer = TestHelper
          .getCryptoGetAccountInfo(stub, payerAccount, payerAccount, genKeyPair,
              defaultNodeAccount).getCryptoGetInfo().getAccountInfo().getBalance();
      Assert.assertNotEquals(genesisbalancebeforeTransfer, genesisbalanceAfterTransfer);
      if ((genesisbalancebeforeTransfer - genesisbalanceAfterTransfer) == 160) {
        log.info("the transaction 4 was selected. Transaction 5 was rejected by the platform");
        Assert.assertNotEquals(1060, (genesisbalancebeforeTransfer - genesisbalanceAfterTransfer));
      } else {
        log.info("the transaction 5 was selected. Transaction 4 was rejected by the platform");
        Assert.assertNotEquals(160, (genesisbalancebeforeTransfer - genesisbalanceAfterTransfer));
      }


    } catch (ExecutionException e) {
      e.printStackTrace();
    }


  }

  public static Transaction createTransfer(Timestamp timestamp, AccountID fromAccount,
      KeyPair fromKeyPair, AccountID toAccount,
      AccountID payerAccount, KeyPair payerKeyPair, AccountID nodeAccount,
      long amount) throws Exception {
    Transaction rv = TestHelper.createTransferSigMap(fromAccount, fromKeyPair,
        toAccount, payerAccount,
        payerKeyPair, nodeAccount, amount);
    return rv;
  }


//  public static Transaction createAccountWithFee(AccountID payerAccount, AccountID nodeAccount,
//      KeyPair pair, long initialBalance, List<PrivateKey> privKey) throws Exception {
//
//    Transaction transaction = TestHelper
//        .createAccount(payerAccount, nodeAccount, pair, initialBalance, 0,
//            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
//            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
//    Transaction signTransaction = TransactionSigner.signTransaction(transaction, privKey);
//    long createAccountFee = FeeClient.getCreateAccountFee(signTransaction,privKey.size());
//    transaction = TestHelper
//        .createAccount(payerAccount, nodeAccount, pair, initialBalance, createAccountFee,
//            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
//            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
//    signTransaction = TransactionSigner.signTransaction(transaction, privKey);
//    return signTransaction;
//
//
//  }

  public static Transaction createAccountSameTxnIDDiffReq1(Timestamp timestamp,
      AccountID payerAccount, AccountID nodeAccount, KeyPair pair, long initialBalance,
      List<PrivateKey> privKey) throws Exception {
    Duration transactionDuration = RequestBuilder.getDuration(30);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyStr = Hex.encodeHexString(pubKey);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    List<Key> keyList = Collections.singletonList(key);

    long transactionFee = 0;
    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = 100l;
    long receiveRecordThreshold = 100l;
    boolean receiverSigRequired = false;
    Duration autoRenewPeriod = RequestBuilder.getDuration(5000);

    Transaction transaction = RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);

    transaction = TransactionSigner.signTransaction(transaction, privKey);
    transactionFee = FeeClient.getCreateAccountFee(transaction,privKey.size());

    transaction = RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
    transaction = TransactionSigner.signTransaction(transaction, privKey);
    return transaction;

  }


}
