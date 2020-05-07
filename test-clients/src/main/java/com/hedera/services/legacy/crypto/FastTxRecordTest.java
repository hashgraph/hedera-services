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
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.MessageDigest;
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
 * The tested feature is not yet implemented.
 *
 * @author Akshay
 * @Date : 10/9/2018
 */
public class FastTxRecordTest {

  private static final Logger log = LogManager.getLogger(FastTxRecordTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  public FastTxRecordTest(int port, String host) {
    // connecting to the grpc server on the port
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public FastTxRecordTest() {
  }

  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    FastTxRecordTest fastTxRecordTest = new FastTxRecordTest(port, host);
    fastTxRecordTest.demo();
  }

  public void demo() throws Exception {

    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);
    log.info("Creating account with generating tx record...");
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, firstPair, 10000l,
            Collections.singletonList(genesisPrivateKey));

    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionRecord fastTxRecord = getFastTransactionRecord(
        body.getTransactionID());
    AccountID newlyCreateAccountId1 = fastTxRecord.getReceipt().getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");
    log.info("Get Tx record by Tx Id...");
    long queryFeeForGettingTxRecordFee = FeeClient.getCostForGettingTxRecord();
    Query query = TestHelper
        .getTxRecordByTxId(body.getTransactionID(), payerAccount,
            genKeyPair, defaultNodeAccount, queryFeeForGettingTxRecordFee,
            ResponseType.COST_ANSWER);
    Response transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);

    long txRecordFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
    query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, txRecordFee, ResponseType.ANSWER_ONLY);
    transactionRecord = stub.getTxRecordByTxID(query);

    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    TransactionRecord transactionRecordResponse = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();
    Assert
        .assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
    Assert.assertEquals(body.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
    Assert.assertNotNull(transactionRecordResponse.getTransactionHash());
    Assert.assertEquals(transactionRecordResponse.getTransactionHash(),
        ByteString
            .copyFrom(MessageDigest.getInstance("SHA-384").digest(transaction.toByteArray())));
    Assert.assertTrue(transactionRecordResponse.getTransactionFee() >= 0);
    Assert.assertEquals(fastTxRecord, transactionRecordResponse);
    log.info(
        "Tx Record is successfully retrieve and asserted. record=" + transactionRecordResponse);

    log.info("--------------------------------------");
    log.info("Creating account without generating tx record...");
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper.createAccount(payerAccount, defaultNodeAccount, secondPair, 100000, 0,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    Transaction signTransaction = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    //Since Genesis is single key
    long createAccountFee = FeeClient.getCreateAccountFee(signTransaction,1);
    transaction = TestHelper
        .createAccount(payerAccount, defaultNodeAccount, secondPair, 100000, createAccountFee,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
            TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionBody.Builder txBody = body.toBuilder();
    txBody.setGenerateRecord(false);
    Transaction transaction1 = Transaction.newBuilder().setBodyBytes(txBody.build().toByteString())
        .setSigs(transaction.getSigs()).build();
    signTransaction = TransactionSigner
        .signTransaction(transaction1, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(signTransaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create second account :: " + response
        .getNodeTransactionPrecheckCode().name());
    TransactionRecord fastTxRecord2 = getFastTransactionRecord(
        body.getTransactionID());
    AccountID newlyCreateAccountId2 = fastTxRecord2.getReceipt().getAccountID();
    Assert.assertNotNull(newlyCreateAccountId2);
    Assert
        .assertTrue(newlyCreateAccountId2.getAccountNum() > newlyCreateAccountId1.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");
    log.info("Get nonexistent Tx record by Tx Id...");
    query = TestHelper.getTxRecordByTxId(txBody.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, queryFeeForGettingTxRecordFee,
        ResponseType.COST_ANSWER);
    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    log.info("Precheck response get nonexistent Tx record :: " + transactionRecord
        .getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode().name());
    Assert.assertEquals(ResponseCodeEnum.RECORD_NOT_FOUND,
        transactionRecord.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    TransactionBody bodyTr = TransactionBody.parseFrom(transaction.getBodyBytes());
    // Skip fee data, since there was no Tx it will be zero.
//		  txRecordFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
    query = TestHelper.getTxRecordByTxId(bodyTr.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, txRecordFee, ResponseType.ANSWER_ONLY);
    // log.info("getTxRecordByTxID: query=" + query);
    transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    Assert.assertEquals(ResponseCodeEnum.RECORD_NOT_FOUND,
        transactionRecord.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
    Assert.assertNotNull("",
        transactionRecord.getTransactionGetRecord().getTransactionRecord().toString());
    log.info("No Tx record found...");
    log.info("------------------¯\\_(ツ)_/¯---------------------");
  }

  private TransactionRecord getFastTransactionRecord(TransactionID transactionID) {
//		  CommonUtils.nap(2); // When records are fast, restore this and remove sleep in the loop
    TransactionRecord fastTxRecord;
    int cnt = 0;
    do {
      CommonUtils.nap(1);
      cnt++;
      fastTxRecord = TestHelper.getFastTxRecord(transactionID, stub);
      Assert.assertNotNull(fastTxRecord);
      Assert.assertTrue(fastTxRecord.hasReceipt());
    } while (fastTxRecord.getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN) && cnt <= 10);
    log.info("Number of polls: " + cnt);
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, fastTxRecord.getReceipt().getStatus());
    return fastTxRecord;
  }

}
