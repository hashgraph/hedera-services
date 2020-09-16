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
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

public class CryptoDuplicatedTransaction extends Thread {

  private static final Logger log = LogManager.getLogger(CryptoDuplicatedTransaction.class);
  private static final long CRYPTO_TRANSFER_TX_FEE = TestHelper.getCryptoMaxFee();

  public static String fileName = TestHelper.getStartUpFile();
  CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  CryptoServiceGrpc.CryptoServiceBlockingStub stub2;

  private static int fetchReceipt = 0;

  public static int transferTimes = 100;
  public static long initialBalance = 1000000000l;
  private static int numberOfThreads = 1;

  static boolean isCheckEnable = false;

  ManagedChannel channel;

  ManagedChannel channel2;

  PrivateKey genesisPrivateKey;
  KeyPair genKeyPair;
  AccountID payerAccount;
  KeyPair keyPair2;
  KeyPair keyPair3;
  KeyPair keyPair4;
  KeyPair keyPair5;

  AccountID nodeAccount2;
  AccountID nodeAccount3;
  AccountID nodeAccount4;
  AccountID nodeAccount;

  AccountID secondNdeAccount;

  public CryptoDuplicatedTransaction() {
    Properties properties = TestHelper.getApplicationProperties();

    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));

    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
    stub = CryptoServiceGrpc.newBlockingStub(channel);

    channel2 = ManagedChannelBuilder.forAddress(properties.getProperty("host2"),
            Integer.parseInt(properties.getProperty("port2"))).usePlaintext(true).build();
    stub2 = CryptoServiceGrpc.newBlockingStub(channel2);

    try {
      Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

      List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
      // get Private Key
      KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
      genesisPrivateKey = genKeyPairObj.getPrivateKey();
      genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
      payerAccount = genesisAccount.get(0).getAccountId();

      keyPair2 = new KeyPairGenerator().generateKeyPair();
      keyPair3 = new KeyPairGenerator().generateKeyPair();
      keyPair4 = new KeyPairGenerator().generateKeyPair();
      keyPair5 = new KeyPairGenerator().generateKeyPair();

      nodeAccount2 = Utilities
              .createSingleAccountAndReturnID(payerAccount, Utilities.getDefaultNodeAccount(), 0l, 0l,
                      initialBalance,
                      genesisPrivateKey, stub, keyPair2);
      nodeAccount3 = Utilities
              .createSingleAccountAndReturnID(payerAccount, Utilities.getDefaultNodeAccount(), 0l, 0l,
                      initialBalance,
                      genesisPrivateKey, stub, keyPair3);
      nodeAccount4 = Utilities
              .createSingleAccountAndReturnID(payerAccount, Utilities.getDefaultNodeAccount(), 0l, 0l,
                      initialBalance,
                      genesisPrivateKey, stub, keyPair4);
      nodeAccount = RequestBuilder.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

      secondNdeAccount = RequestBuilder.getAccountIdBuild(nodeAccount.getAccountNum()+1, 0l, 0l);

    } catch (URISyntaxException | IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  @Override
  public void run() {
    try {
      demo();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    } catch (InvalidNodeTransactionPrecheckCode e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String args[])
          throws InterruptedException, IOException, URISyntaxException {

    log.info("create account");
    log.info(args.length);
    if ((args.length) > 0) {
      transferTimes = Integer.parseInt(args[0]);
    }
    if ((args.length) > 1) {
      fetchReceipt = Integer.parseInt(args[1]);
    }
    if ((args.length) > 2) {
      numberOfThreads = Integer.parseInt(args[2]);
    }
    if ((args.length) > 3) {
      isCheckEnable = Boolean.parseBoolean(args[3]);
      log.info("isCheckingEnabled ");
    }

    CryptoDuplicatedTransaction[] cryptoTransferTests = new CryptoDuplicatedTransaction[numberOfThreads];
    for (int k = 0; k < numberOfThreads; k++) {
      cryptoTransferTests[k] = new CryptoDuplicatedTransaction();
      cryptoTransferTests[k].setName("thread" + k);
    }

    System.out.println("Finish account creating");

    System.out.println("Press Enter key to continue...");
    try
    {
      System.in.read();
    }
    catch(Exception e)
    {}

    long startTime = System.currentTimeMillis();

    for (int k = 0; k < numberOfThreads; k++) {
      cryptoTransferTests[k].start();
    }
    for (int k = 0; k < numberOfThreads; k++) {
      cryptoTransferTests[k].join();
    }

    long endTime = System.currentTimeMillis();
    log.info("Transfer balance total time is " + (endTime - startTime) + " millisecond ");

  }

  private void demo()
          throws Exception {

    Transaction[] transfer1 = new Transaction[transferTimes];

    Transaction[] transfer2 = new Transaction[transferTimes];

    for (int i = 0; i < transferTimes; i++) {

      Pair<Transaction, Transaction> pair = CreateTransferToDiffNodes(nodeAccount2, keyPair2.getPrivate(), nodeAccount3,
              nodeAccount4,
              keyPair4.getPrivate(), nodeAccount, secondNdeAccount, 1L);

      transfer1[i] = pair.getLeft();
      transfer2[i] = pair.getRight();


      while (true) {
        TransactionResponse response = stub.cryptoTransfer(transfer1[i]);

        TransactionResponse response2 = stub2.cryptoTransfer(transfer2[i]);

        if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
          break;
        } else if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY || response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED) {
          // Try again
          i--;
          log.info(getName() + " busy with tran " + i);
          Thread.sleep(50);
          break;
        } else {
          Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
        }


        if (response2.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
          break;
        } else if (response2.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY || response2.getNodeTransactionPrecheckCode() == ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED) {
          // Try again
          i--;
          log.info(getName() + " busy with tran " + i);
          Thread.sleep(50);
          break;
        } else {
          Assert.assertEquals(ResponseCodeEnum.OK, response2.getNodeTransactionPrecheckCode());
        }
      }
    }

    if (fetchReceipt == 1) { // ask for receipt
      for (int i = 0; i < transferTimes; i++) {
        TransactionBody body = TransactionBody.parseFrom(transfer1[i].getBodyBytes());
        TransactionReceipt txReceipt = TestHelper
                .getTxReceipt(body.getTransactionID(), stub);
        log.info("Get receipt from first node, receipt = " + txReceipt);

        TransactionBody body2 = TransactionBody.parseFrom(transfer2[i].getBodyBytes());
        TransactionReceipt txReceipt2 = TestHelper
                .getTxReceipt(body2.getTransactionID(), stub2);
        log.info("Get receipt from second node, receipt = " + txReceipt2);

      }
    } else if (fetchReceipt == 2) { // ask for record
      for (int i = 0; i < transferTimes; i++) {
        TransactionBody body = TransactionBody.parseFrom(transfer1[i].getBodyBytes());
        TransactionRecord record = Utilities
                .getTransactionRecord(body.getTransactionID(),
                        payerAccount, genKeyPair, nodeAccount, stub);

        log.info(this.getName() + " Tx Record is successfully retrieve and asserted: " + i + " record = " + record);

        TransactionBody body2 = TransactionBody.parseFrom(transfer2[i].getBodyBytes());
        TransactionRecord record2 = Utilities
                .getTransactionRecord(body2.getTransactionID(),
                        payerAccount, genKeyPair, secondNdeAccount, stub2);

        log.info(this.getName() + " Tx Record is successfully retrieve and asserted: " + i + " record = " + record2);


      }

    }

    if (isCheckEnable) {

      getAccountBalance(stub, nodeAccount2, nodeAccount4, keyPair4, nodeAccount,
              initialBalance - transferTimes);
      getAccountBalance(stub, nodeAccount3, nodeAccount4, keyPair4, nodeAccount,
              initialBalance + transferTimes);
      log.info("Checking result done.");

    }

    channel.shutdown();

  }

  private void getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
          AccountID accountID,
          AccountID payerAccount, KeyPair payerKeyPair, AccountID nodeAccount, long expectedBalance) throws Exception {
    Response accountInfoResponse = TestHelper.getCryptoGetAccountInfo(stub, accountID,
            payerAccount, payerKeyPair, nodeAccount);
    assertAccountBalance(accountID, accountInfoResponse, expectedBalance);

  }

  private void assertAccountBalance(AccountID accountID, Response accountInfoResponse,
          long expectedBalance) {

    Assert.assertNotNull(accountInfoResponse);
    Assert.assertNotNull(accountInfoResponse.getCryptoGetInfo());
    CryptoGetInfoResponse.AccountInfo accountInfo1 = accountInfoResponse.getCryptoGetInfo()
            .getAccountInfo();
    log.info("Check balance of Account ID " + accountID.getAccountNum());
    Assert.assertNotNull(accountInfo1);
    Assert.assertEquals(accountID, accountInfo1.getAccountID());
    Assert.assertEquals(TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            accountInfo1.getGenerateReceiveRecordThreshold());
    Assert.assertEquals(TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            accountInfo1.getGenerateSendRecordThreshold());
    Assert.assertFalse(accountInfo1.getReceiverSigRequired());

    Assert.assertEquals(accountInfo1.getBalance(), accountInfo1.getBalance());

  }

  private Pair<Transaction, Transaction> CreateTransferToDiffNodes(AccountID fromAccount, PrivateKey fromKey,
          AccountID toAccount,
          AccountID payerAccount, PrivateKey payerAccountKey,
          AccountID nodeAccount,
          AccountID secondNodeAccount,
          long amount) {
    boolean generateRecord = (fetchReceipt == 2);
    Timestamp timestamp = RequestBuilder
            .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    SignatureList sigList = SignatureList.getDefaultInstance();
    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(), CRYPTO_TRANSFER_TX_FEE, timestamp,
            transactionDuration,
            generateRecord, "Test Transfer", sigList, fromAccount.getAccountNum(), -amount,
            toAccount.getAccountNum(), amount);

    Transaction transferTx2 = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
            payerAccount.getRealmNum(), payerAccount.getShardNum(), secondNodeAccount.getAccountNum(),
            secondNodeAccount.getRealmNum(), secondNodeAccount.getShardNum(), CRYPTO_TRANSFER_TX_FEE, timestamp,
            transactionDuration,
            generateRecord, "Test Transfer", sigList, fromAccount.getAccountNum(), -amount,
            toAccount.getAccountNum(), amount);

    // sign the tx
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    List<PrivateKey> payerPrivKeyList = new ArrayList<>();
    payerPrivKeyList.add(payerAccountKey);
    privKeysList.add(payerPrivKeyList);

    List<PrivateKey> fromPrivKeyList = new ArrayList<>();
    fromPrivKeyList.add(fromKey);
    privKeysList.add(fromPrivKeyList);

    Transaction signedTx = TransactionSigner.signTransactionNew(transferTx, privKeysList);

    Transaction signedTx2 = TransactionSigner.signTransactionNew(transferTx2, privKeysList);

    return Pair.of(signedTx, signedTx2);
  }

}
