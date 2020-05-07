package com.hedera.services.legacy.regression;

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

import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Transfers between two accounts in a batch and then fetches receipt for last transfer. This test
 * is TPS controlled.
 *
 * @author Achal
 */
public class BatchThreadTransfer {

  private static final Logger log = LogManager.getLogger(BatchThreadTransfer.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static int BATCH_SIZE;
  private static int COUNT_SIZE;
  // BATCH_SIZE * COUNT_SIZE gives the total number of transfers as well as TPS
  private static long TPS; // Transactions per second
  private ManagedChannel channel;
  List<TransactionID> txList = new ArrayList<>();


  public BatchThreadTransfer(int port, String host, int batchSize) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    this.stub = CryptoServiceGrpc.newBlockingStub(channel);
    this.BATCH_SIZE = batchSize;
  }


  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    BATCH_SIZE = Integer.parseInt(properties.getProperty("BATCH_SIZE"));
    COUNT_SIZE = Integer.parseInt(properties.getProperty("COUNT_SIZE"));
    TPS = Integer.parseInt(properties.getProperty("TPS"));
    int port = Integer.parseInt(properties.getProperty("port"));
    log.info("Connecting host = " + host + "; port = " + port);
    int numTransfer = BATCH_SIZE;
    BatchThreadTransfer batch =
        new BatchThreadTransfer(port, host, numTransfer);
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    AccountID accountID = batch.createAccount(firstPair);
    Timer timer = new Timer("Timer");
    Timer timer1 = new Timer("Timer1");
    TimerTask timerTask = new TimerTask() {
      int count = 0;

      public void run() {
        if (count > (COUNT_SIZE - 2)) {
          timer.cancel();
          timer.purge();
        }
        try {
          batch.transfer(accountID, firstPair);
          count++;
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };

    TimerTask timerTask1 = new TimerTask() {
      int count1 = 0;

      public void run() {
        if (count1 > ((COUNT_SIZE) * BATCH_SIZE) - 2) {
          timer1.cancel();
          timer1.purge();
        }
        try {
//                    log.info(count1);
          batch.receipt(count1);
          count1++;
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    };

    Thread transferThread = new Thread(() -> timer.scheduleAtFixedRate(timerTask, 10l, TPS / 10));

    Thread receiptThread = new Thread(() -> timer1.scheduleAtFixedRate(timerTask1, 100l, 100l));

    transferThread.start();
    Thread.sleep(20000l);
    receiptThread.start();
  }

  public void transfer(AccountID newlyCreateAccountId1, KeyPair firstPair)
      throws Exception {

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();
    AccountID nodeAccount3 = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);
    long start = System.currentTimeMillis();

    for (int i = 0; i < BATCH_SIZE; i++) {
      Transaction transfer1 = TestHelper.createTransferSigMap(payerAccount, genKeyPair,
          newlyCreateAccountId1, newlyCreateAccountId1,
          firstPair, nodeAccount3, 10l);
      log.info("Transfer #" + (i + 1)
          + ": Transferring 100 coin from genesis account to 2nd account....");
//      StopWatch stopWatch = new Log4JStopWatch("RoundTrip:transferBatch");
      TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
      Assert.assertNotNull(transferRes);
      Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
//      stopWatch.stop();
          
      TransactionBody body = TransactionBody.parseFrom(transfer1.getBodyBytes());
      txList.add(i, body.getTransactionID());
    }
    long end = System.currentTimeMillis();
    log.info("Total time took for transfer is :: " + (end - start) + "milli seconds");
  }

  public void receipt(int i) {
    TransactionReceipt txReceipt = null;

    try {
//      StopWatch stopWatch = new Log4JStopWatch("RoundTrip:receipt");
      txReceipt = TestHelper.getTxReceipt(txList.get(i), stub);
//      stopWatch.stop();
      Assert.assertEquals(txReceipt.getStatus(), ResponseCodeEnum.SUCCESS);
    } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
      invalidNodeTransactionPrecheckCode.printStackTrace();
    }
    Assert.assertNotNull(txReceipt);
  }

  public AccountID createAccount(KeyPair firstPair)
      throws Exception {


    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();
    AccountID nodeAccount3 = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    TestHelper.initializeFeeClient(channel, payerAccount, genesisKeyPair, nodeAccount3);

    Transaction transaction = TestHelper
        .createAccount(payerAccount, nodeAccount3, firstPair, 10000000000000000l, 1000000l,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    Transaction signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

    long transactionFee = FeeClient.getCreateAccountFee(signTransaction, 1);

    transaction = TestHelper
        .createAccount(payerAccount, nodeAccount3, firstPair, 10000000000000000l, transactionFee,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

    TransactionResponse response = stub.createAccount(signTransaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    stub = CryptoServiceGrpc.newBlockingStub(channel);
    AccountID newlyCreateAccountId1 = null;
    Thread.sleep(5000);
    try {
    	TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
      newlyCreateAccountId1 = TestHelper
          .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
      invalidNodeTransactionPrecheckCode.printStackTrace();
    }
    return newlyCreateAccountId1;
  }

}
