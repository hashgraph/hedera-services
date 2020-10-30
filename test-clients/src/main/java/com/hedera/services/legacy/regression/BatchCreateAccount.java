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
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;


/**
 * Creates accounts in a batch and then fetches receipt for last account created
 *
 * @author Achal
 */
public class BatchCreateAccount {

  private static final Logger log = LogManager.getLogger(BatchCreateAccount.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static int BATCH_SIZE = 10000000;
  private boolean retrieveTxReceipt;
  private ManagedChannel channel;
  private String host;
  private int port;


  public BatchCreateAccount(int port, String host, int batchSize, boolean retrieveTxReceipt) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    this.stub = CryptoServiceGrpc.newBlockingStub(channel);
    this.BATCH_SIZE = batchSize;
    this.retrieveTxReceipt = retrieveTxReceipt;
    this.host = host;
    this.port = port;
  }

  public BatchCreateAccount() {
  }

  public static void main(String args[]) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    log.info("Connecting host = " + host + "; port = " + port);
    int numTransfer = 1000;
    boolean retrieveTxReceipt = true;
    BatchCreateAccount batch =
        new BatchCreateAccount(port, host, numTransfer, retrieveTxReceipt);
    for (int i = 0; i < 10; i++) {
      batch.demo();
    }

  }

  public void demo() throws Exception {

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

    List<TransactionID> txList = new ArrayList<>();
    long start = System.currentTimeMillis();
    for (int i = 0; i < 30; i++) {
      log.info("Create Account Request is ::" + (i + 1));
      KeyPair firstPair = new KeyPairGenerator().generateKeyPair();

      Transaction transaction = TestHelper
          .createAccount(payerAccount, nodeAccount3, firstPair, 10000l, 1000000l,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
      Transaction signTransaction = TransactionSigner
          .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

      long transactionFee = FeeClient.getCreateAccountFee(signTransaction, 1);

      transaction = TestHelper
          .createAccount(payerAccount, nodeAccount3, firstPair, 10000l, transactionFee,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
              TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
      signTransaction = TransactionSigner
          .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));

      TransactionResponse response = stub.createAccount(signTransaction);
      Assert.assertNotNull(response);
      Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
      TransactionBody body = TransactionBody.parseFrom(signTransaction.getBodyBytes());
      txList.add(i, body.getTransactionID());
    }
    for (int i = 0; i < 1; i++) {
      if (retrieveTxReceipt) {
        TransactionReceipt txReceipt = null;
        try {
          txReceipt = TestHelper.getTxReceipt(txList.get(txList.size() - 1), stub);
        } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
          invalidNodeTransactionPrecheckCode.printStackTrace();
        }
        Assert.assertNotNull(txReceipt);
        while ((txReceipt.getStatus()) != ResponseCodeEnum.SUCCESS) {
          try {
            Thread.sleep(1000);
            log.info("waiting for receipt for last account");
            txReceipt = TestHelper.getTxReceipt(txList.get(txList.size() - 1), stub);
          } catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
            invalidNodeTransactionPrecheckCode.printStackTrace();
          }
        }
        log.info("Success in fetching the receipts");

        Assert.assertEquals(txReceipt.getStatus(), ResponseCodeEnum.SUCCESS);
      }
    }
    long end = System.currentTimeMillis();
    log.info("Total time took for transfer is :: " + (end - start) + "nano seconds");
  }
}
