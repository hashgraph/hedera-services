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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;


/**
 * Creates two accounts and does 52 transfers between them
 *
 * @author Achal
 */
public class CryptoTwoToFifty {

  private static final Logger log = LogManager.getLogger(CryptoTwoToFifty.class);

  private static ManagedChannel channel;
  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  public CryptoTwoToFifty(int port, String host) {
    // connecting to the grpc server on the port
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoTwoToFifty.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public CryptoTwoToFifty() {
  }

  public static void main(String args[])
      throws Exception {
    log.info("create account");
    log.info("command line arguments length = " + args.length);
    int numberOfAccounts = 10;
    if ((args.length) > 0) {
      numberOfAccounts = Integer.parseInt(args[0]);
    }

    Properties properties = TestHelper.getApplicationProperties();

    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));

    log.info("Connecting host = " + host + "; port = " + port);
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoTwoToFifty.stub = CryptoServiceGrpc.newBlockingStub(channel);

    for (int i = 0; i < numberOfAccounts; i++) {
      log.info("***** Round #" + (i + 1) + " *****");
      CryptoTwoToFifty cryptoTwoToTwo = new CryptoTwoToFifty();
      cryptoTwoToTwo.demo();
    }

  }

  public void demo()
      throws Exception {

    Random r = new Random();
    int Low = 1;
    int High = 10;
    int result = 1;
    result = r.nextInt(High - Low) + Low;


    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genKeyPairObj.getPrivateKey());
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID nodeAccount3 = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, nodeAccount3);

    // create 1st account by payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, nodeAccount3, firstPair, 1000000l,
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
    log.info("First account: Account ID " + newlyCreateAccountId1.getAccountNum()
        + " created successfully.");
    log.info("--------------------------------------");

    // create 2nd account by payer as genesis
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper.createAccountWithSigMap(payerAccount, nodeAccount3, secondPair, 10_000_000L,
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
    log.info("Second account: Account ID " + newlyCreateAccountId2.getAccountNum()
        + " created successfully.");
    log.info("--------------------------------------");

    for (int i = 0; i < 20; i++) {
      Transaction transfer1 = TestHelper
          .createTransferSigMap(newlyCreateAccountId1, firstPair,
              newlyCreateAccountId2, newlyCreateAccountId2, secondPair, nodeAccount3, 1000l);

      log.info(
          "Transfer #" + (i + 1) + ": Transferring 1000 coin from 1st account to 2nd account....");
      TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
      Assert.assertNotNull(transferRes);
      Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
      log.info(
          "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
      TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
      TransactionReceipt txReceipt = TestHelper
          .getTxReceipt(transferBody.getTransactionID(), stub);

      Assert.assertNotNull(txReceipt);
      log.info("-----------------------------------------");
      // Get account Info of 1st Account
      Response accountInfoResponse = TestHelper
          .getCryptoGetAccountInfo(stub, newlyCreateAccountId1, newlyCreateAccountId2,
              secondPair, nodeAccount3);
      assertAccountInfoDetails(newlyCreateAccountId1, accountInfoResponse);
    }
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
    Assert.assertEquals(TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
        accountInfo1.getGenerateReceiveRecordThreshold());
    Assert.assertEquals(TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
        accountInfo1.getGenerateSendRecordThreshold());
    Assert.assertFalse(accountInfo1.getReceiverSigRequired());
    Duration renewal = RequestBuilder.getDuration(5000);
  }
}
