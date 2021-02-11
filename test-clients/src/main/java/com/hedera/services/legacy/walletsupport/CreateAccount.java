package com.hedera.services.legacy.walletsupport;

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

import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Create account for wallet
 *
 * @author Achal
 */
public class CreateAccount {

  private static final Logger log = LogManager.getLogger(CreateAccount.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static ManagedChannel channel;

  public CreateAccount(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CreateAccount.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public CreateAccount() {
  }

  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    CreateAccount multipleCryptoTransfers = new CreateAccount(port, host);
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
    Transaction transaction = TestHelper.createAccountWallet(payerAccount, defaultNodeAccount,
        "9a47731df70b20e1562addfce8c3cb30a4cf58d5a5cb1a8102648ba111fdd936", 10000000l);
    Transaction signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    TransactionResponse response = stub.createAccount(signTransaction);
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
    // get tx record of payer account by txId
    log.info("Get Tx record by Tx Id...");
    long queryFee = FeeClient.getCostForGettingTxRecord();
    Query query = TestHelper
        .getTxRecordByTxId(body.getTransactionID(), payerAccount,
            genKeyPair, defaultNodeAccount, queryFee, ResponseType.COST_ANSWER);
    Response transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);

    queryFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, queryFee, ResponseType.ANSWER_ONLY);
    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);

    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    TransactionRecord transactionRecordResponse = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    log.info("Tx Record is successfully retrieve and asserted.");
    log.info("--------------------------------------");

  }

}
