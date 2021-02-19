package com.hedera.services.legacy.crypto;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * This test create account by genesis with 1000 hbars as initial balance and create one more
 * account by the newly created account with initial balance as 2000 hbars
 * <p>
 * Expected Behaviour :: Account shouldn't be created and receipt status should be
 * INSUFFICIENT_PAYER_BALANCE
 */
public class CreateAccountPayerScenario {

  private static final Logger log = LogManager.getLogger(CreateAccountPayerScenario.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  public CreateAccountPayerScenario(int port, String host) {
    // connecting to the grpc server on the port
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CreateAccountPayerScenario.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public CreateAccountPayerScenario() {
  }

  public static void main(String args[])
      throws Exception {

    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    CreateAccountPayerScenario multipleCryptoTransfers = new CreateAccountPayerScenario(port, host);
    multipleCryptoTransfers.demo();
  }

  public void demo() throws Exception {

 //
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    PrivateKey genesisPrivateKey = genesisAccount.get(0).getKeyPairList().get(0).getPrivateKey();
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    // create 1st account by payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, firstPair, 100000,
            Collections.singletonList(genesisPrivateKey));
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

    log.info(
        "create second account with payer account as :: " + newlyCreateAccountId1.getAccountNum());

    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithFee(newlyCreateAccountId1, defaultNodeAccount, secondPair, 200000,
            Collections.singletonList(firstPair.getPrivate()));
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    log.info("Pre Check Response of Create second account :: " + response
        .getNodeTransactionPrecheckCode().name());
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE,
        response.getNodeTransactionPrecheckCode());
    log.info("Payer account has not enough money to create account...");

    log.info("--------------------------------------");
    log.info("Create one more account to check sequence number is preserved...");
    // create 3rd account by payer as genesis
    KeyPair thirdPair = new KeyPairGenerator().generateKeyPair();

    transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, thirdPair, 1000000l,
            Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create Third account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId3 =
        TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId3);
    Assert.assertEquals(newlyCreateAccountId3.getAccountNum(),
        newlyCreateAccountId1.getAccountNum() + 1);
    log.info("Account ID " + newlyCreateAccountId3.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");
  }

  private TransactionRecord getFastTransactionRecord(TransactionID transactionID) {
    TransactionRecord fastTxRecord;
    int cnt = 0;
    do {
      CommonUtils.nap(1);
      cnt++;
      fastTxRecord = TestHelper.getFastTxRecord(transactionID, stub);
      Assert.assertNotNull(fastTxRecord);
      Assert.assertTrue(fastTxRecord.hasReceipt());
    } while (fastTxRecord.getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN) && cnt <= 10);
    return fastTxRecord;
  }
}
