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
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Test class for Multiple Crypto Transferes between multiple accounts and validates transfer amounts
 * @author Achal Created on 2018-12-17
 */
public class MultiTransferConfigrableTest {

  private static final Logger log = LogManager.getLogger(MultiTransferConfigrableTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static int BATCH_SIZE = 10000000;
  private boolean retrieveTxReceipt;
  private ManagedChannel channel;
  private String host;
  private int port;
  private static long defaultAccount;


  public MultiTransferConfigrableTest(int port, String host, int batchSize,
      boolean retrieveTxReceipt) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    this.stub = CryptoServiceGrpc.newBlockingStub(channel);
    this.BATCH_SIZE = batchSize;
    this.retrieveTxReceipt = retrieveTxReceipt;
    this.host = host;
    this.port = port;
  }


  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host;
    if ((args.length) > 0) {
      host = args[0];
    }
    else
    {
      host = properties.getProperty("host");
    }

    if ((args.length) > 1) {
      try {
        defaultAccount = Long.parseLong(args[1]);
        log.info("Got Node Account as " + defaultAccount);
      }
      catch(Exception ex){
        log.info("Invalid data passed for node id");
        defaultAccount = Utilities.getDefaultNodeAccount();
      }
    }
    else
    {
      defaultAccount = Utilities.getDefaultNodeAccount();
    }


    int port = Integer.parseInt(properties.getProperty("port"));
    log.info("Connecting host = " + host + "; port = " + port);
    int numTransfer = 10;
    boolean retrieveTxReceipt = true;
    MultiTransferConfigrableTest multipleCryptoTransfers =
        new MultiTransferConfigrableTest(port, host, numTransfer, retrieveTxReceipt);
    multipleCryptoTransfers.demo();

  }

  public void demo() throws Exception {

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    PrivateKey genesisPrivateKey = genesisAccount.get(0).getKeyPairList().get(0).getPrivateKey();
    KeyPair genesisKeyPair = new KeyPair(genesisAccount.get(0).getKeyPairList().get(0).getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID nodeAccount3 = RequestBuilder
        .getAccountIdBuild(defaultAccount, 0l, 0l);

    TestHelper.initializeFeeClient(channel, payerAccount, genesisKeyPair, nodeAccount3);

    // create 1st account by payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccount(payerAccount, nodeAccount3, firstPair, 100000000000000l, 100000l,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    Transaction signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    Long feeRequired = FeeClient.getCreateAccountFee(signTransaction, 1);

    transaction = TestHelper
        .createAccount(payerAccount, nodeAccount3, firstPair, 100000000000000l, feeRequired,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

    TransactionResponse response = stub.createAccount(signTransaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "PreCheck Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    stub = CryptoServiceGrpc.newBlockingStub(channel);
    AccountID newlyCreateAccountId1 = null;
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    try {
      newlyCreateAccountId1 = TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
    }
    catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
      invalidNodeTransactionPrecheckCode.printStackTrace();
    }
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account-ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");
    log.info("Initiate transfer from genesis account to newly created account...");

    // 2nd account
    // create 1st account by payer as genesis
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccount(payerAccount, nodeAccount3, secondPair, 100000000000000l, 1000000l,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    feeRequired = FeeClient.getCreateAccountFee(signTransaction, 1);

    transaction = TestHelper
        .createAccount(payerAccount, nodeAccount3, firstPair, 100000000000000l, feeRequired,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

    response = stub.createAccount(signTransaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    stub = CryptoServiceGrpc.newBlockingStub(channel);
    AccountID newlyCreateAccountId2 = null;
    try {
      newlyCreateAccountId2 = TestHelper
          .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
      invalidNodeTransactionPrecheckCode.printStackTrace();
    }
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");
    log.info("Initiate transfer from genesis account to newly created account...");
    Thread.sleep(180);

    long start = System.currentTimeMillis();
    long sentAmtToAcc1 = 0l;
    long sentAmtToAcc2 = 0l;
    int totalGoodReceipts=0;
    int totalBadReceipts=0;
    long ts,te=0l;
    for (int i = 0; i < 4000; i++) {
      ts = System.nanoTime();
      Transaction transfer1;
      if (i % 2 == 0) {
        transfer1 = TestHelper
            .createTransferSigMap(newlyCreateAccountId2, secondPair,
                newlyCreateAccountId1, newlyCreateAccountId2, secondPair, nodeAccount3, 9999l);

        sentAmtToAcc1 += 9999l;
      } else {
        transfer1 = TestHelper
            .createTransferSigMap(newlyCreateAccountId1, secondPair,
                newlyCreateAccountId2, newlyCreateAccountId1, firstPair, nodeAccount3, 999l);
        sentAmtToAcc2 += 999l;
      }

      try {
        stub = CryptoServiceGrpc.newBlockingStub(channel);

        TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
        Assert.assertNotNull(transferRes);

        if (ResponseCodeEnum.OK == transferRes.getNodeTransactionPrecheckCode()) {

          TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
          if (retrieveTxReceipt) {

            TransactionReceipt txReceipt = null;
            try {
              txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), stub);
            } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
              log.info("InvalidNodeTransactionPrecheckCode" + invalidNodeTransactionPrecheckCode);
              totalBadReceipts++;
            }
            Assert.assertNotNull(txReceipt);
            totalGoodReceipts++;
          }


        } else {
          log.error("Got Bad Precheck Stuff ** MOVING ON ** " + transferRes
              .getNodeTransactionPrecheckCode());
          totalBadReceipts++;
        }
        te = System.nanoTime();
        log.info("T # " + i + "in " + (te - ts));
      }catch(Throwable thx){
        log.info("ERROR * " + thx.getMessage());
    }

    }


    long end = System.currentTimeMillis();
    log.info("Total time took for transfer is :: " + (end - start) + "ms");
    log.info("Total Good Receipts " + totalGoodReceipts);
    log.info("Total BAD Receipts " + totalBadReceipts);
    log.info("Total Amount sent from Account 1 to 2 " + sentAmtToAcc1);
    log.info("Total Amount sent from Account 2 to 1 " + sentAmtToAcc2);
  }

}
