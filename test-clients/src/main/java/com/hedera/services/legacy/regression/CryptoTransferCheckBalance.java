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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

public class CryptoTransferCheckBalance extends Thread {

  private static final Logger log = LogManager.getLogger(CryptoTransferCheckBalance.class);
  private static final long MAX_TX_FEE = TestHelper.getCryptoMaxFee();

  public static String fileName = TestHelper.getStartUpFile();
  CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  private static int fetchReceipt = 0;

  public static int transferTimes = 100;
  public static long initialBalance = TestHelper.getContractMaxFee() * 5;
  private static int numberOfThreads = 1;

  static boolean isCheckEnable = false;

  ManagedChannel channel;

  PrivateKey genesisPrivateKey;
  KeyPair genKeyPair;
  AccountID genesisPayerAccount;
  KeyPair fromAccountKey;
  KeyPair toAccountKey;
  KeyPair payerAccountKey;
  KeyPair nodeAccountKeyPair;

  AccountID fromAccount;
  AccountID toAccount;
  AccountID payerAccount;
  AccountID nodeAccount;

  public CryptoTransferCheckBalance() {
    Properties properties = TestHelper.getApplicationProperties();

    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));

    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
    stub = CryptoServiceGrpc.newBlockingStub(channel);

    try {
      Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

      List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
      // get Private Key
      KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
      genesisPrivateKey = genKeyPairObj.getPrivateKey();
      genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
      genesisPayerAccount = genesisAccount.get(0).getAccountId();

      fromAccountKey = new KeyPairGenerator().generateKeyPair();
      toAccountKey = new KeyPairGenerator().generateKeyPair();
      payerAccountKey = new KeyPairGenerator().generateKeyPair();
      nodeAccountKeyPair = new KeyPairGenerator().generateKeyPair();

      fromAccount = Utilities
          .createSingleAccountAndReturnID(genesisPayerAccount, Utilities.getDefaultNodeAccount(),
              0l, 0l, initialBalance,
              genesisPrivateKey, stub, fromAccountKey);
      toAccount = Utilities
          .createSingleAccountAndReturnID(genesisPayerAccount, Utilities.getDefaultNodeAccount(),
              0l, 0l, initialBalance,
              genesisPrivateKey, stub, toAccountKey);
      payerAccount = Utilities
          .createSingleAccountAndReturnID(genesisPayerAccount, Utilities.getDefaultNodeAccount(),
              0l, 0l, initialBalance,
              genesisPrivateKey, stub, payerAccountKey);
      nodeAccount = RequestBuilder.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

      TestHelper.initializeFeeClient(channel, genesisPayerAccount, genKeyPair, nodeAccount);

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

    CryptoTransferCheckBalance[] cryptoTransferTests = new CryptoTransferCheckBalance[numberOfThreads];
    for (int k = 0; k < numberOfThreads; k++) {
      cryptoTransferTests[k] = new CryptoTransferCheckBalance();
    }

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
    for (int i = 0; i < transferTimes; i++) {

      transfer1[i] = MyCreateTransfer(fromAccount, fromAccountKey.getPrivate(), toAccount,
          payerAccount,
          payerAccountKey.getPrivate(), nodeAccount, 1L);

      log.info("Transferring 1 coin from 1st account to 2nd account....");
      while (true) {
        TransactionResponse response = stub.cryptoTransfer(transfer1[i]);
        if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
          break;
        } else if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY) {
          // Try again
        } else {
          Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
        }
      }
    }

    if (fetchReceipt == 1) { // ask for receipt
      for (int i = 0; i < transferTimes; i++) {
    	TransactionBody body = TransactionBody.parseFrom(transfer1[i].getBodyBytes());
        TransactionReceipt txReceipt = TestHelper
            .getTxReceipt(body.getTransactionID(), stub);
        Assert.assertNotNull(txReceipt);
        log.info("Get receipt");
      }
    } else if (fetchReceipt == 2) { // ask for record
      for (int i = 0; i < transferTimes; i++) {
    	  TransactionBody body = TransactionBody.parseFrom(transfer1[i].getBodyBytes());
        TransactionRecord record = Utilities
            .getTransactionRecord(body.getTransactionID(),
                genesisPayerAccount, genKeyPair, payerAccount, stub);

        log.info("Tx Record is successfully retrieve and asserted: " + i);

      }

    }

    long fromAccountBalance = Utilities
        .getAccountBalance(stub, fromAccount, genesisPayerAccount, genKeyPair,
            nodeAccount, MAX_TX_FEE);
    long toAccountBalance = Utilities
        .getAccountBalance(stub, toAccount, genesisPayerAccount, genKeyPair,
            nodeAccount, MAX_TX_FEE);

    long payerAccountBalance = Utilities
        .getAccountBalance(stub, payerAccount, genesisPayerAccount,
        genKeyPair, nodeAccount, MAX_TX_FEE);

    long nodeAccountBalance = Utilities
        .getAccountBalance(stub, nodeAccount, genesisPayerAccount, genKeyPair,
            nodeAccount, MAX_TX_FEE);

    Thread.sleep(8000);
    log.info("fromAccountBalance  " + fromAccountBalance);
    log.info("toAccountBalance    " + toAccountBalance);

    log.info("payerAccountBalance " + payerAccountBalance);
    log.info("nodeAccountBalance  " + nodeAccountBalance);

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

  private Transaction MyCreateTransfer(AccountID fromAccount, PrivateKey fromKey,
      AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount, long amount) {
    boolean generateRecord = (fetchReceipt == 2);
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), MAX_TX_FEE, timestamp,
        transactionDuration,
        generateRecord, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    List<PrivateKey> privKeysList = new ArrayList<>();
    privKeysList.add(payerAccountKey);
    privKeysList.add(fromKey);

    Transaction signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);

    return signedTx;
  }

}
