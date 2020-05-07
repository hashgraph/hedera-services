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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
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
 * Creates an account with multiple keys and then this account does transactions with
 * multiple signatures
 *
 * @author Achal
 */
public class MultiSigCreationTransfer {


  private static final Logger log = LogManager.getLogger(MultiSigCreationTransfer.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static ManagedChannel channel;
  private static long accountDuration;

  public MultiSigCreationTransfer(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    MultiSigCreationTransfer.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public MultiSigCreationTransfer() {
  }

  protected static Properties getApplicationProperties() {
    Properties prop = new Properties();
    InputStream input;
    try {
      String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
      input = new FileInputStream(rootPath + "application.properties");
      prop.load(input);
    } catch (IOException e) {
      log.error(e);
    }
    return prop;
  }

  public static void main(String args[])
      throws Exception {
    Properties properties = getApplicationProperties();
    accountDuration = Long.parseLong(properties.getProperty("ACCOUNT_DURATION"));
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    MultiSigCreationTransfer multipleCryptoTransfers = new MultiSigCreationTransfer(port, host);
    multipleCryptoTransfers.demo();
  }

  public static void demo()
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

    // create 1st multi SIgnature account with payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    KeyPair thirdPair = new KeyPairGenerator().generateKeyPair();
    List<KeyPair> multiSig = new ArrayList<KeyPair>();
    multiSig.add(0, firstPair);
    multiSig.add(1, secondPair);
    multiSig.add(2, thirdPair);

    Transaction transaction = TestHelper
        .createAccountmultiSig(payerAccount, defaultNodeAccount, multiSig, 10000000000l,
            genesisPrivateKey, accountDuration);
//        Transaction signTransaction = TransactionSigner.signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
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
    System.out.println(response.getAllFields());

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // create second multi sig account with genesis as payer

    KeyPair fourthPair = new KeyPairGenerator().generateKeyPair();
    KeyPair fifthPair = new KeyPairGenerator().generateKeyPair();
    KeyPair sixthPair = new KeyPairGenerator().generateKeyPair();
    List<KeyPair> multiSig2 = new ArrayList<>();
    multiSig2.add(fourthPair);
    multiSig2.add(fifthPair);
    multiSig2.add(sixthPair);

    transaction = TestHelper
        .createAccountmultiSig(payerAccount, defaultNodeAccount, multiSig2, 100000000000l,
            genesisPrivateKey, accountDuration);
    //signTransaction = TransactionSigner.signTransaction(transaction, Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Pre Check Response of Create second multiSig account :: " + response
        .getNodeTransactionPrecheckCode().name());

    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId2 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId2);
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");
    System.out.println(response.getAllFields());

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // create 3rd account by payer as genesis
    KeyPair seventh = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, seventh, 1000000000l,
            Collections.singletonList(genesisPrivateKey));
    response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create Third account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId4 = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId4);
    Assert
        .assertTrue(newlyCreateAccountId4.getAccountNum() > newlyCreateAccountId2.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId4.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // transfers from multiSig account

    // transfer between 1st to 2nd account by using payer account as 3rd account

    List<PrivateKey> privatekeysList = new ArrayList<PrivateKey>();
    privatekeysList.add(0, firstPair.getPrivate());
    privatekeysList.add(1, secondPair.getPrivate());
    privatekeysList.add(2, thirdPair.getPrivate());
    Transaction transfer1 = createTransferAccount(newlyCreateAccountId1, privatekeysList,
        newlyCreateAccountId2, newlyCreateAccountId4,
        seventh.getPrivate(), defaultNodeAccount, 100l);

    log.info("Transferring 1000 coin from 1st account to 2nd account....request=" + transfer1);

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

    // transfer between 2nd to 1st multisig accounts by using payer account as 3rd account

    List<PrivateKey> privatekeysList1 = new ArrayList<PrivateKey>();
    privatekeysList1.add(0, fourthPair.getPrivate());
    privatekeysList1.add(1, fifthPair.getPrivate());
    privatekeysList1.add(2, sixthPair.getPrivate());
    transfer1 = createTransferAccount(newlyCreateAccountId2, privatekeysList1,
        newlyCreateAccountId1, newlyCreateAccountId4,
        seventh.getPrivate(), defaultNodeAccount, 100l);

    log.info("Transferring 1000 coin from 2nd account to 1st account....");
    transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info("-----------------------------------------");

    //multi Sig is the payer: newlyCreateAccountId2
    AccountID payerAcc = newlyCreateAccountId2;
    List<PrivateKey> payerPrivKeyList = new ArrayList<>();
    payerPrivKeyList.add(0, fourthPair.getPrivate());
    payerPrivKeyList.add(1, fifthPair.getPrivate());
    payerPrivKeyList.add(2, sixthPair.getPrivate());

    // from account: newlyCreateAccountId1
    AccountID fromAcc = newlyCreateAccountId1;
    List<PrivateKey> fromPrivKeyList = new ArrayList<>();
    fromPrivKeyList.add(0, firstPair.getPrivate());
    fromPrivKeyList.add(1, secondPair.getPrivate());
    fromPrivKeyList.add(2, thirdPair.getPrivate());

    transfer1 = createTransferAccountMultiSigPayee(fromAcc, fromPrivKeyList,
        newlyCreateAccountId4, payerAcc,
        payerPrivKeyList, defaultNodeAccount, 100l);

    log.info("Transferring 1000 coin from MultiSig Account1. MultiSIgAccount 2 is the payer");
    transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Thread.sleep(1000);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    txReceipt = TestHelper.getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info("-----------------------------------------");
    log.info("Multi Sig Test Ended Successfully");

  }

  public static Transaction createTransferAccount(AccountID fromAccount, List<PrivateKey> fromKey,
      AccountID toAccount,
      AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount,
      long amount) throws Exception {
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

    // sign the tx
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    List<PrivateKey> payerPrivKeyList = new ArrayList<>();
    payerPrivKeyList.add(payerAccountKey);
    privKeysList.add(payerPrivKeyList);
    privKeysList.add(fromKey);

    Transaction signedTx = TransactionSigner.signTransactionNew(transferTx, privKeysList);

    long transactionFee = FeeClient.getTransferFee(signedTx,privKeysList.size());
    transferTx =
        RequestBuilder
            .getCryptoTransferRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
                payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee,
                timestamp, transactionDuration, false,
                "Test Transfer", sigList, fromAccount.getAccountNum(),
                -amount, toAccount.getAccountNum(), amount);

    signedTx = TransactionSigner.signTransactionNew(transferTx, privKeysList);
    return signedTx;
  }

  public static Transaction createTransferAccountMultiSigPayee(AccountID fromAccount,
      List<PrivateKey> fromPrivKeyList, AccountID toAccount,
      AccountID payerAccount, List<PrivateKey> payerPrivKeyList, AccountID nodeAccount,
      long amount) throws Exception {
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

    // sign the tx
    List<List<PrivateKey>> privKeysList = new ArrayList<>();
    privKeysList.add(payerPrivKeyList);
    privKeysList.add(fromPrivKeyList);

    Transaction signedTx = TransactionSigner.signTransactionNew(transferTx, privKeysList);

    long transactionFee = FeeClient.getTransferFee(signedTx,privKeysList.size()) * 2;

    transferTx =
        RequestBuilder
            .getCryptoTransferRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
                payerAccount.getShardNum(), nodeAccount.getAccountNum(),
                nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee,
                timestamp, transactionDuration, false,
                "Test Transfer", sigList, fromAccount.getAccountNum(),
                -amount, toAccount.getAccountNum(), amount);

    signedTx = TransactionSigner.signTransactionNew(transferTx, privKeysList);

    return signedTx;
  }

}
