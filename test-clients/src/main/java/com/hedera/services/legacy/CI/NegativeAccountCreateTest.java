package com.hedera.services.legacy.CI;

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
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * This tests negative scenario on create account api
 * 1. send null transaction request with valid sig list
 * 2. send empty sig list with valid tx body
 * 3. send both body and sig list empty
 * 4. send request with invalid payer account
 * 5. send invalid node account id
 * 6. send invalid start time
 * 7. send invalid duration time
 * 8. send memo more than 100 byte size
 * 9. invalid signature
 *
 * @author Akshay
 * @Date : 8/15/2018
 */
public class NegativeAccountCreateTest {

  private static final Logger log = LogManager.getLogger(NegativeAccountCreateTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

  public NegativeAccountCreateTest(int port, String host) {
    // connecting to the grpc server on the port
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    NegativeAccountCreateTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[])
      throws InterruptedException, IOException, URISyntaxException {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    NegativeAccountCreateTest negativeAccountCreateTest = new NegativeAccountCreateTest(port, host);
    negativeAccountCreateTest.demo();
  }

  public void demo() throws IOException, URISyntaxException, InterruptedException {

//
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    // fetching the genesis key for signatures
    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    PrivateKey genesisPrivateKey = genesisAccount.get(0).getKeyPairList().get(0).getPrivateKey();
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    // 1. null transaction test case
    Transaction transaction = testCreateAccountNullTransactionBody();
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_BODY,
        response.getNodeTransactionPrecheckCode());
    log.info("null transaction test case");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    //2.  null signature valid transaction body test case
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper.createAccount(payerAccount, defaultNodeAccount, firstPair, 10000l, 0,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY,
        response.getNodeTransactionPrecheckCode());
    log.info("null signature valid transaction body test case");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // 3. transaction and signature both null
    response = stub.createAccount(null);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_BODY,
        response.getNodeTransactionPrecheckCode());
    log.info("transaction and signature both null");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    /// 4. validate transaction body test case. invalid payer account
    transaction = createAccountValidateTxTestCase(
        AccountID.newBuilder().setAccountNum(0).build(), defaultNodeAccount, firstPair, 1000l);
    Transaction txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND,
        response.getNodeTransactionPrecheckCode());
    log.info("validate transaction body test case. invalid transaction id");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // 5. validate node account test
    transaction = createAccountValidateTxTestCase(payerAccount, AccountID.newBuilder()
        .setAccountNum(0).build(), firstPair, 1000l);
    txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_NODE_ACCOUNT,
        response.getNodeTransactionPrecheckCode());
    log.info("validate node account test");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // 6. start time validate test
    Timestamp startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).plusSeconds(50));
    int txDurationSec = 30;
    transaction = createAccountValidateTxTestCaseStartTimeValidation(payerAccount,
        defaultNodeAccount, firstPair, 100000L, startTime, txDurationSec);
    txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_START,
        response.getNodeTransactionPrecheckCode());
    log.info("start time validate test");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // 6a. start time + duration validate test
    startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).plusSeconds(-13));
    txDurationSec = 10;
    transaction = createAccountValidateTxTestCaseStartTimeValidation(payerAccount,
        defaultNodeAccount, firstPair, 100000L, startTime, txDurationSec);
    txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.TRANSACTION_EXPIRED,
        response.getNodeTransactionPrecheckCode());
    log.info("start time validate test");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // 7. duration test (sending duration value more than 180 sec)
    startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).plusSeconds(-10));
    txDurationSec = 182;
    transaction = createAccountValidateTxTestCaseStartTimeValidation(payerAccount,
        defaultNodeAccount, firstPair, 100000L, startTime, txDurationSec);
    txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_DURATION,
        response.getNodeTransactionPrecheckCode());
    log.info("duration test");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // 7a. duration test (sending duration value less than 0 sec)
    startTime = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).plusSeconds(-10));
    txDurationSec = -1;
    transaction = createAccountValidateTxTestCaseStartTimeValidation(payerAccount,
        defaultNodeAccount, firstPair, 100000L, startTime, txDurationSec);
    txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_DURATION,
        response.getNodeTransactionPrecheckCode());
    log.info("duration test");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    //8. get memo test
    transaction = createAccountValidateMemo(payerAccount, defaultNodeAccount, firstPair, 1000l);
    txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.MEMO_TOO_LONG, response.getNodeTransactionPrecheckCode());
    log.info(" memo test");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // 9. validate signature
    KeyPair secondKeyPair = new KeyPairGenerator().generateKeyPair();
    transaction = createAccountValidateTxBodyThresholdTestCase(payerAccount,
        defaultNodeAccount, firstPair, 1000l);
    txSigned = TransactionSigner
        .signTransaction(transaction, Collections.singletonList(secondKeyPair.getPrivate()));
    response = stub.createAccount(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE,
        response.getNodeTransactionPrecheckCode());
    log.info("Invalid signature test");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    log.info("Negative Create Account Test Finished");

  }

  public static Transaction testCreateAccountNullTransactionBody() {
    ///// when the stub receives a null transaction body.
    return null;
  }

  private static Transaction createAccountValidateTxTestCase(AccountID payerAccount,
      AccountID nodeAccount, KeyPair pair, long initialBalance) {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyStr = Hex.encodeHexString(pubKey);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    List<Key> keyList = Collections.singletonList(key);

    long transactionFee = 100l;
    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = 100l;
    long receiveRecordThreshold = 100l;
    boolean receiverSigRequired = true;
    Duration autoRenewPeriod = RequestBuilder.getDuration(5000);

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
  }

  private static Transaction createAccountValidateTxTestCaseStartTimeValidation(
      AccountID payerAccount, AccountID nodeAccount, KeyPair pair, long initialBalance,
      Timestamp timestamp, int durationSec) {
    Duration transactionDuration = RequestBuilder.getDuration(durationSec);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyStr = Hex.encodeHexString(pubKey);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    List<Key> keyList = Collections.singletonList(key);

    long transactionFee = 100000000;
    boolean generateRecord = true;
    String memo = "Create Account Test";
    long sendRecordThreshold = 100l;
    long receiveRecordThreshold = 100l;
    boolean receiverSigRequired = true;
    Duration autoRenewPeriod = RequestBuilder.getDuration(5000);

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
  }


  public static Transaction createAccountValidateMemo(AccountID payerAccount, AccountID nodeAccount,
      KeyPair pair, long initialBalance) {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyStr = Hex.encodeHexString(pubKey);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    List<Key> keyList = Collections.singletonList(key);

    long transactionFee = 100l;
    boolean generateRecord = true;
    String memo = "Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test Create Account Test";
    long sendRecordThreshold = 100l;
    long receiveRecordThreshold = 100l;
    boolean receiverSigRequired = true;
    Duration autoRenewPeriod = RequestBuilder.getDuration(5000);

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, keyList.size(), keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
  }

  private static Transaction createAccountValidateTxBodyThresholdTestCase(
      AccountID payerAccount, AccountID nodeAccount, KeyPair pair, long initialBalance) {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
    String pubKeyStr = Hex.encodeHexString(pubKey);
    Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyStr)).build();
    List<Key> keyList = Collections.singletonList(key);

    long transactionFee = 100l;
    boolean generateRecord = true;
    String memo = "Create Account Test";
    int threshold = -1000;
    long sendRecordThreshold = -100l;
    long receiveRecordThreshold = -100l;
    boolean receiverSigRequired = true;
    Duration autoRenewPeriod = RequestBuilder.getDuration(5000);

    return RequestBuilder
        .getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(),
            nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            transactionFee, timestamp, transactionDuration, generateRecord,
            memo, threshold, keyList, initialBalance, sendRecordThreshold,
            receiveRecordThreshold, receiverSigRequired, autoRenewPeriod);
  }


}
