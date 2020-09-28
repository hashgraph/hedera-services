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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
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

public class CryptoCreatePerformance {

  private static final Logger log = LogManager.getLogger(CryptoCreatePerformance.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static int BATCH_SIZE = 200;
  private boolean retrieveTxReceipt;
  private ManagedChannel channel;
  private String host;
  private int port;
 private static long nodeAccount;

  public CryptoCreatePerformance(int port, String host, int batchSize, boolean retrieveTxReceipt) {
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

  public CryptoCreatePerformance() {
  }

  public static void main(String args[])
      throws Exception {
    String host;
    Properties properties = TestHelper.getApplicationProperties();

    if ((args.length) > 0) {
      host = args[0];
    }
    else
    {
      host = properties.getProperty("host");
    }

    if ((args.length) > 1) {
      try {
        nodeAccount = Long.parseLong(args[1]);
      }
      catch(Exception ex){
        log.info("Invalid data passed for node id");
        nodeAccount = Utilities.getDefaultNodeAccount();
      }
    }
    else
    {
      nodeAccount = Utilities.getDefaultNodeAccount();
    }

    int port = Integer.parseInt(properties.getProperty("port"));
    log.info("Connecting host = " + host + "; port = " + port);
    int numTransfer = 1000000;
    boolean retrieveTxReceipt = true;
    CryptoCreatePerformance cryptoCreatePerformance =
        new CryptoCreatePerformance(port, host, numTransfer, retrieveTxReceipt);
    cryptoCreatePerformance.demo();
  }

  public void demo() throws Exception {

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    PrivateKey genesisPrivateKey = genesisAccount.get(0).getKeyPairList().get(0).getPrivateKey();
    KeyPair genesisKeyPair = new KeyPair(genesisAccount.get(0).getKeyPairList().get(0).getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();
    AccountID nodeAccount3 = RequestBuilder
        .getAccountIdBuild(nodeAccount, 0l, 0l);

    TestHelper.initializeFeeClient(channel, payerAccount, genesisKeyPair, nodeAccount3);

    long start = System.currentTimeMillis();
    int goodResponse =0;
    int badResponse=0;
    int goodReceipt=0;
    int badReceipt=0;
    for (int i = 0; i < 1000; i++) {
      // create 1st account by payer as genesis
      log.info("Create Req " + (i + 1));
      KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
      Transaction transaction = TestHelper
          .createAccount(payerAccount, nodeAccount3, firstPair, 900000l, 1000000l,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
      Transaction signTransaction = TransactionSigner
          .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

      long transactionFee = FeeClient.getCreateAccountFee(signTransaction, 1);

      transaction = TestHelper
          .createAccount(payerAccount, nodeAccount3, firstPair, 900000l, transactionFee,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
      signTransaction = TransactionSigner
          .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

      TransactionResponse response = stub.createAccount(signTransaction);
      Assert.assertNotNull(response);
      if(ResponseCodeEnum.OK == response.getNodeTransactionPrecheckCode()){
        goodResponse++;

      }else
      {
        badResponse++;
        log.info("Got a bad response " + response.getNodeTransactionPrecheckCode());
      }
      stub = CryptoServiceGrpc.newBlockingStub(channel);
      TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
      AccountID newlyCreateAccountId1 = null;
      try {
        newlyCreateAccountId1 = TestHelper
            .getTxReceipt(body.getTransactionID(), stub).getAccountID();
        log.info("AccountID " + newlyCreateAccountId1.getAccountNum());
        goodReceipt++;
      } catch (Exception ex) {
        log.info("Error Happened " + ex.getMessage());
        badReceipt++;

      }

    }
    long end = System.currentTimeMillis();
    log.info("Total time for 800 createAccount  " + (end - start) + "Mil seconds");
    log.info("Total Good Account Response" + goodResponse);
    log.info("Total Good Account Receipts" + goodReceipt);
    log.info("Total BAD Account Response" + badResponse);
    log.info("Total Good Account Receipts" + badReceipt);
  }
}
