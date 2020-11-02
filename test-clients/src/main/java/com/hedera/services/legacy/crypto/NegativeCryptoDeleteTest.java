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
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Tests negative scenarios for Crypto Delete
 *
 * @author Achal
 */
public class NegativeCryptoDeleteTest {

  private static final Logger log = LogManager.getLogger(CryptoDeleteTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  public NegativeCryptoDeleteTest(int port, String host) {
    // connecting to the grpc server on the port
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    NegativeCryptoDeleteTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[]) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    NegativeCryptoDeleteTest negativeCryptoDeleteTest = new NegativeCryptoDeleteTest(port, host);
    negativeCryptoDeleteTest.demo();
  }

  public void demo() throws Exception {


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
        .createAccountWithFee(payerAccount, defaultNodeAccount, firstPair, 1000_000_000l,
            Collections.singletonList(genesisPrivateKey));
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = CommonUtils.extractTransactionBody(transaction);
    TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
    AccountID newlyCreateAccountId1 = txReceipt1.getAccountID();
    Assert.assertNotNull(newlyCreateAccountId1);
    log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    // create 2nd account
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, secondPair, 1000_000_000l,
            Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create second account :: " + response
        .getNodeTransactionPrecheckCode().name());
    body = CommonUtils.extractTransactionBody(transaction);
    AccountID newlyCreateAccountId2 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId2);
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    // try to delete account 1 with the wrong signature
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = RequestBuilder.getDuration(100);
    TransactionID transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(newlyCreateAccountId1).setTransferAccountID(payerAccount)
        .build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100_000_000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(true)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    Transaction signedTransaction = TransactionSigner
        .signTransaction(tx, Collections.singletonList(genesisPrivateKey));

    TransactionResponse response1 = stub.cryptoDelete(signedTransaction);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(response1.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
    TransactionReceipt txReceipt = null;
    txReceipt = TestHelper.getTxReceipt(transactionID, stub);
    log.info(txReceipt);
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE,
        txReceipt.getStatus());

    // crypto delete with 0 fee

    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transactionValidDuration = RequestBuilder.getDuration(100);
    transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(newlyCreateAccountId1)
        .setTransferAccountID(newlyCreateAccountId2)
        .build();
    transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(0l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(true)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    bodyBytesArr = transactionBody.toByteArray();
    bodyBytes = ByteString.copyFrom(bodyBytesArr);
    tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    signedTransaction = TransactionSigner
        .signTransaction(tx, Collections.singletonList(genesisPrivateKey));
    List<PrivateKey> list1 = new ArrayList<>();
    list1.add(secondPair.getPrivate());
    Transaction signedTransaction1 = TransactionSigner.signTransaction(signedTransaction, list1, true);

    response1 = stub.cryptoDelete(signedTransaction1);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE,
        response1.getNodeTransactionPrecheckCode());

    // transfer account ID not provided
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transactionValidDuration = RequestBuilder.getDuration(100);
    transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(newlyCreateAccountId2)
        .build();
    transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100000000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(true)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    bodyBytesArr = transactionBody.toByteArray();
    bodyBytes = ByteString.copyFrom(bodyBytesArr);
    tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    signedTransaction = TransactionSigner
        .signTransaction(tx, Collections.singletonList(genesisPrivateKey));
    signedTransaction1 = TransactionSigner.signTransaction(signedTransaction, list1, true);

    response1 = stub.cryptoDelete(signedTransaction1);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(response1.getNodeTransactionPrecheckCode(),
        ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST);

    // deleted accountID not provided
    // transfer account ID not provided
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transactionValidDuration = RequestBuilder.getDuration(100);
    transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(newlyCreateAccountId2)
        .build();
    transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100000000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(true)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    bodyBytesArr = transactionBody.toByteArray();
    bodyBytes = ByteString.copyFrom(bodyBytesArr);
    tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    signedTransaction = TransactionSigner
        .signTransaction(tx, Collections.singletonList(genesisPrivateKey));

    response1 = stub.cryptoDelete(signedTransaction);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(response1.getNodeTransactionPrecheckCode(),
        ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST);

    // check for signatures

    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transactionValidDuration = RequestBuilder.getDuration(100);
    transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(newlyCreateAccountId2)
        .setTransferAccountID(newlyCreateAccountId1)
        .build();
    transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100_000_000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(true)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    bodyBytesArr = transactionBody.toByteArray();
    bodyBytes = ByteString.copyFrom(bodyBytesArr);
    tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    signedTransaction = TransactionSigner
        .signTransaction(tx, Collections.singletonList(genesisPrivateKey));

    response1 = stub.cryptoDelete(signedTransaction);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(response1.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
    txReceipt = TestHelper.getTxReceipt(transactionID, stub);
    log.info(txReceipt);
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE,
        txReceipt.getStatus());

    // signed with the wrong key.
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transactionValidDuration = RequestBuilder.getDuration(100);
    transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(newlyCreateAccountId2)
        .setTransferAccountID(newlyCreateAccountId1)
        .build();
    transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100_000_000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(true)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    bodyBytesArr = transactionBody.toByteArray();
    bodyBytes = ByteString.copyFrom(bodyBytesArr);
    tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    signedTransaction = TransactionSigner
        .signTransaction(tx, Collections.singletonList(genesisPrivateKey));
    KeyPair thirdKeyPair = new KeyPairGenerator().generateKeyPair();
    List<PrivateKey> list5 = new ArrayList<>();
    list5.add(thirdKeyPair.getPrivate());
    signedTransaction1 = TransactionSigner.signTransaction(signedTransaction, list5, true);

    response1 = stub.cryptoDelete(signedTransaction1);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(response1.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
    txReceipt = TestHelper.getTxReceipt(transactionID, stub);
    log.info(txReceipt.getStatus());
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE, txReceipt.getStatus());

  }

}
