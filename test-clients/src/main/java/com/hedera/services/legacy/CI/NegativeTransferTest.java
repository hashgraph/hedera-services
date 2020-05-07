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
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
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

/**
 * This Test class covers following test cases
 *
 * Payer account not found test case :: PAYER_ACCOUNT_NOT_FOUND Account not found test case response
 * :: INVALID_ACCOUNT_ID Null transaction test case :: INVALID_TRANSACTION_BODY Null signature test
 * case :: INVALID_SIGNATURE Transaction and signature both null :: INVALID_TRANSACTION_BODY Pre
 * Check Response failure response is :: INVALID_SIGNATURE Transfer more than balance ::
 * INSUFFICIENT_ACCOUNT_BALANCE Invalid account amount. result for sum of account amount is not zero
 * :: INVALID_ACCOUNT_AMOUNTS Invalid signature :: INVALID_SIGNATURE
 *
 * @author Akshay/Tirupathi
 * @Date : 8/15/2018
 */
public class NegativeTransferTest {

  private static final Logger log = LogManager.getLogger(NegativeTransferTest.class);
  private static final long INITIAL_ACCOUNT_BALANCE = 10_000_000L;

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static ManagedChannel channel;

  public NegativeTransferTest(int port, String host) {
    // connecting to the grpc server on the port
    NegativeTransferTest.channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    NegativeTransferTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[])
      throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    NegativeTransferTest negativeTransferTest = new NegativeTransferTest(port, host);
    negativeTransferTest.demo();
  }

  public void demo()
      throws Exception {

//
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    // fetching the genesis key for signatures
    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genKeyPairObj.getPrivateKey());
    AccountID payerAccount = genesisAccount.get(0).getAccountId();
    AccountID invalidAccount = RequestBuilder.getAccountIdBuild(123L, 0L, 0L);
    AccountID defaultNodeAccount = RequestBuilder
        .getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0L, 0L);

    TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

    // creating two test accounts

    // create 1st account by payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair,
            INITIAL_ACCOUNT_BALANCE, genKeyPair);
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

    // create 2nd account by payer as genesis
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
      .createAccountWithSigMap(payerAccount, defaultNodeAccount, secondPair,
            INITIAL_ACCOUNT_BALANCE, genKeyPair);
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
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Two Test Accounts Created. The Transfer tests start below");

    // negative transfer tests start

    // payer account not found
    transaction = TestHelper
        .createTransferSigMap(newlyCreateAccountId1, firstPair, newlyCreateAccountId2,
            invalidAccount, firstPair, defaultNodeAccount, 100L);
    response = stub.cryptoTransfer(transaction);
    Assert.assertNotNull(response);
    log.info("payer account not found test case");
    log.info("Pre Check Response is :: " + response.getNodeTransactionPrecheckCode().name());
    Assert.assertEquals(ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND,
        response.getNodeTransactionPrecheckCode());

    // To account not found
    transaction = TestHelper
        .createTransferSigMap(newlyCreateAccountId1, firstPair, invalidAccount,
            newlyCreateAccountId1, firstPair, defaultNodeAccount, 100L);
    response = stub.cryptoTransfer(transaction);
    Assert.assertNotNull(response);
    log.info("account not found test case");
    log.info("Pre Check Response is :: " + response.getNodeTransactionPrecheckCode().name());
     Assert.assertEquals(ResponseCodeEnum.OK,
        response.getNodeTransactionPrecheckCode());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt2 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub);
    Assert.assertEquals(ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST, txReceipt2.getStatus());
    log.info("Post Check Response failure response is :: " + txReceipt2.getStatus().name());

    // null transaction test case
    transaction = testTransferAccountNullTransactionBody();
    response = stub.cryptoTransfer(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_BODY,
        response.getNodeTransactionPrecheckCode());
    log.info("null transaction test case");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());
    // Invalid Payer Account test case
    Transaction transfer = createTransferNullSig(newlyCreateAccountId1, firstPair.getPrivate(),
        newlyCreateAccountId2, newlyCreateAccountId1,
        firstPair.getPrivate(), defaultNodeAccount, 1000L);
    response = stub.cryptoTransfer(transfer);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE,
        response.getNodeTransactionPrecheckCode());
    log.info("null signature valid transaction body test case");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    // invalid transaction body test case
    response = stub.cryptoTransfer(null);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_TRANSACTION_BODY,
        response.getNodeTransactionPrecheckCode());
    log.info("transaction and signature both null");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    /// validate transaction body test case. invalid transaction id
    transaction = createTransferNInvalidTransactionBodySig(
        AccountID.newBuilder().setAccountNum(0).build(), firstPair.getPrivate(),
        newlyCreateAccountId2, newlyCreateAccountId1,
        firstPair.getPrivate(), defaultNodeAccount, 1000L);
    response = stub.cryptoTransfer(transaction);
    Assert.assertNotNull(response);
    log.info("validate transaction body test case. invalid transaction id");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    /// transfer more than balance
    transaction = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
        newlyCreateAccountId2, newlyCreateAccountId1, firstPair, defaultNodeAccount,
        INITIAL_ACCOUNT_BALANCE * 100);
    response = stub.cryptoTransfer(transaction);
    Assert.assertNotNull(response);
    log.info("transfer more than balance");
    log.info("Pre Check Response code is :: " + response.getNodeTransactionPrecheckCode().name());
    // Issue 1741 moved this to the precheck from the receipt.
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE,
        response.getNodeTransactionPrecheckCode());

    // invalid account amount. result for sum of account amount is not zero
    transaction = createTransferNInvalidAccountsAmountsDifference(newlyCreateAccountId1,
        firstPair.getPrivate(),
        newlyCreateAccountId2, newlyCreateAccountId1,
        firstPair.getPrivate(), defaultNodeAccount, 100L);

    response = stub.cryptoTransfer(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("invalid account amount. result for sum of account amount is not zero");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());
    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionReceipt txReceipt1 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub);
    Assert.assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS, txReceipt1.getStatus());
    log.info("Post Check Response failure response is :: " + txReceipt1.getStatus().name());

    // invalid signature
    transaction = createTransferNInvalidSignature(newlyCreateAccountId1, firstPair.getPrivate(),
        newlyCreateAccountId2, newlyCreateAccountId2,
        secondPair.getPrivate(), defaultNodeAccount, 100L);
    response = stub.cryptoTransfer(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE_TYPE_MISMATCHING_KEY,
        response.getNodeTransactionPrecheckCode());
    log.info("invalid signature");
    log.info(
        "Pre Check Response failure response is :: " + response.getNodeTransactionPrecheckCode()
            .name());

    log.info("Negative Transfer Test Ended");

    log.info("transaction fee is not being checked currently");


  }

  private static Transaction testTransferAccountNullTransactionBody() {
    ///// when the stub receives a null transaction body.
    return null;
  }

  private static Transaction createTransferNullSig(AccountID fromAccount, PrivateKey fromKey,
      AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount,
      long amount) {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    SignatureList sigList = SignatureList.getDefaultInstance();
    Transaction transferTx =
        RequestBuilder
            .getCryptoTransferRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
                payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 50,
                timestamp, transactionDuration, false,
                "Test Transfer", sigList, fromAccount.getAccountNum(),
                -amount, toAccount.getAccountNum(), amount);
    // get the transaction body
    ByteString transferBodyBytes = transferTx.getBodyBytes();

    // Payer Account will sign this transaction
    ByteString payerAcctSig = TransactionSigner
        .signBytes(transferBodyBytes.toByteArray(), payerAccountKey);
    // from Account will sign the key
    ByteString fromAccountSig = TransactionSigner
        .signBytes(transferBodyBytes.toByteArray(), fromKey);

    Signature signaturePayeeAcct = Signature.newBuilder().setEd25519(payerAcctSig).build();
    Signature fromAccountObj = Signature.newBuilder().setEd25519(fromAccountSig).build();

//        SignatureList newsigList = SignatureList.newBuilder().addSigs(signaturePayeeAcct).addSigs(fromAccountObj)
//                .build();

    return Transaction.newBuilder().setBodyBytes(transferBodyBytes).build();

  }

  private static Transaction createTransferNInvalidTransactionBodySig(AccountID fromAccount,
      PrivateKey fromKey, AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount,
      long amount) {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    SignatureList sigList = SignatureList.getDefaultInstance();
    Transaction transferTx =
        RequestBuilder
            .getCryptoTransferRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
                payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 50,
                timestamp, transactionDuration, false,
                "Test Transfer", sigList, fromAccount.getAccountNum(),
                -amount, toAccount.getAccountNum(), amount);
    // get the transaction body
    ByteString txBodyBytes = transferTx.getBodyBytes();
    // Payer Account will sign this transaction
    ByteString payerAcctSig = TransactionSigner
        .signBytes(txBodyBytes.toByteArray(), payerAccountKey);
    // from Account will sign the key
    ByteString fromAccountSig = TransactionSigner
        .signBytes(txBodyBytes.toByteArray(), fromKey);

    Signature signaturePayeeAcct = Signature.newBuilder().setEd25519(payerAcctSig).build();
    Signature fromAccountObj = Signature.newBuilder().setEd25519(fromAccountSig).build();

    SignatureList newsigList = SignatureList.newBuilder().addSigs(signaturePayeeAcct)
        .addSigs(fromAccountObj)
        .build();

    return Transaction.newBuilder().setBodyBytes(txBodyBytes).setSigs(newsigList).build();
  }

  public static Transaction createTransferNInvalidAccountsAmounts(AccountID fromAccount,
      PrivateKey fromKey, AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount,
      long amount) {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    SignatureList sigList = SignatureList.getDefaultInstance();
    Transaction transferTx =
        RequestBuilder
            .getCryptoTransferRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
                payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 5000000000000000000L,
                timestamp, transactionDuration, false,
                "Test Transfer", sigList, fromAccount.getAccountNum(),
                -amount, toAccount.getAccountNum(), amount);
    // get the transaction body
    ByteString txBodyBytes = transferTx.getBodyBytes();
    // Payer Account will sign this transaction
    ByteString payerAcctSig = TransactionSigner
        .signBytes(txBodyBytes.toByteArray(), payerAccountKey);
    // from Account will sign the key
    ByteString fromAccountSig = TransactionSigner
        .signBytes(txBodyBytes.toByteArray(), fromKey);

    Signature signaturePayeeAcct = Signature.newBuilder().setEd25519(payerAcctSig).build();
    Signature fromAccountObj = Signature.newBuilder().setEd25519(fromAccountSig).build();

    SignatureList newsigList = SignatureList.newBuilder().addSigs(signaturePayeeAcct)
        .addSigs(fromAccountObj)
        .build();

    return Transaction.newBuilder().setBodyBytes(txBodyBytes).setSigs(newsigList).build();
  }

  public static Transaction createTransferNInvalidAccountsAmountsDifference(AccountID fromAccount,
      PrivateKey fromKey, AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount,
      long amount) throws Exception {
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionDuration = RequestBuilder.getDuration(30);
    long amountReceived = amount + 112;
    SignatureList sigList = SignatureList.getDefaultInstance();
    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 0, timestamp, transactionDuration,
        false,
        "Test Transfer", sigList, fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
        amountReceived);
    // sign the tx
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    List<PrivateKey> payerPrivKeyList = new ArrayList<>();
    payerPrivKeyList.add(payerAccountKey);
    privKeysList.add(payerPrivKeyList);

    List<PrivateKey> fromPrivKeyList = new ArrayList<>();
    fromPrivKeyList.add(fromKey);
    privKeysList.add(fromPrivKeyList);

    Transaction signedTx = TransactionSigner.signTransactionNew(transferTx, privKeysList);

    long transferFee = FeeClient.getTransferFee(signedTx, privKeysList.size());
    transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
        payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
        transactionDuration, false,
        "Test Transfer", sigList, fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
        amountReceived);

    signedTx = TransactionSigner.signTransactionNew(transferTx, privKeysList);
    return signedTx;

  }

  private static Transaction createTransferNInvalidSignature(AccountID fromAccount,
      PrivateKey fromKey, AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount,
      long amount) {
    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);

    SignatureList sigList = SignatureList.getDefaultInstance();
    Transaction transferTx =
        RequestBuilder
            .getCryptoTransferRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
                payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 50,
                timestamp, transactionDuration, false,
                "Test Transfer", sigList, fromAccount.getAccountNum(),
                -amount, toAccount.getAccountNum(), amount);
    // get the transaction body
    ByteString txBodyBytes = transferTx.getBodyBytes();
    // Payer Account will sign this transaction
    ByteString payerAcctSig = TransactionSigner
        .signBytes(txBodyBytes.toByteArray(), payerAccountKey);
    // from Account will sign the key
    ByteString fromAccountSig = TransactionSigner
        .signBytes(txBodyBytes.toByteArray(), fromKey);

    Signature signaturePayeeAcct = Signature.newBuilder().setEd25519(fromAccountSig).build();
    Signature fromAccountObj = Signature.newBuilder().setEd25519(fromAccountSig).build();

    SignatureList newsigList = SignatureList.newBuilder().addSigs(signaturePayeeAcct)
        .addSigs(fromAccountObj)
        .build();

    return Transaction.newBuilder().setBodyBytes(txBodyBytes).setSigs(newsigList).build();
  }

}
