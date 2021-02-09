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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;

import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
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
 * Tests CryptoDelete and then after delete attempts a transaction which is rejected
 *
 * @author Achal
 */
public class CryptoDeleteTest {

  private static final Logger log = LogManager.getLogger(CryptoDeleteTest.class);

  public static String fileName = TestHelper.getStartUpFile();
  private static ManagedChannel channel;
  private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
  private static long accountDuration;
  public CryptoDeleteTest(int port, String host) {
    // connecting to the grpc server on the port
    channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoDeleteTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
  }

  public static void main(String args[]) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    accountDuration = Long.parseLong(properties.getProperty("ACCOUNT_DURATION"));
    String host = properties.getProperty("host");
    int port = Integer.parseInt(properties.getProperty("port"));
    CryptoDeleteTest cryptoDeleteTest = new CryptoDeleteTest(port, host);
    cryptoDeleteTest.demo();
  }

  public void demo() throws Exception {


    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

    List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
    AccountID payerAccount = genesisAccount.get(0).getAccountId();

    AccountID defaultNodeAccount = com.hederahashgraph.builder.RequestBuilder.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

    TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

    // create 1st account by payer as genesis
    KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
    Transaction transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, firstPair, 1000000000000000l,
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
    Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getHbarEquiv() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getCentEquiv() > 0);
    Assert.assertTrue(
        txReceipt1.getExchangeRate().getCurrentRate().getExpirationTime().getSeconds() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getHbarEquiv() > 0);
    Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getCentEquiv() > 0);
    Assert.assertTrue(
        txReceipt1.getExchangeRate().getNextRate().getExpirationTime().getSeconds() > 0);
    log.info("--------------------------------------");
    
    
    // Create Transfer Account ..
    long deleteAcctBalance = 1000000000000000l;
    transaction = TestHelper
            .createAccountWithFee(payerAccount, defaultNodeAccount, firstPair, deleteAcctBalance,
                Collections.singletonList(genesisPrivateKey));
        response = stub.createAccount(transaction);
        Assert.assertNotNull(response);
        Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
        log.info(
            "Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
                .name());

        body = CommonUtils.extractTransactionBody(transaction);
        txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
        AccountID transferAccountID = txReceipt1.getAccountID();

    // Crypto Delete Request

    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Duration transactionValidDuration = com.hederahashgraph.builder.RequestBuilder.getDuration(100);
    TransactionID transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
        .setTransactionValidStart(timestamp).build();
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(newlyCreateAccountId1).setTransferAccountID(transferAccountID)
        .build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(AccountID.newBuilder().setAccountNum(3l).build())
        .setTransactionFee(100000000l)
        .setTransactionValidDuration(transactionValidDuration)
        .setGenerateRecord(false)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    Transaction signedTransaction = com.hederahashgraph.builder.TransactionSigner
        .signTransaction(tx, Collections.singletonList(genesisPrivateKey));
    List<PrivateKey> list = new ArrayList<>();
    list.add(firstPair.getPrivate());
    Transaction signedTransaction1 = TransactionSigner.signTransaction(signedTransaction, list, true);

    TransactionResponse response1 = stub.cryptoDelete(signedTransaction1);
    log.info(response1.getNodeTransactionPrecheckCode());
    Assert.assertEquals(response1.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
    TransactionReceipt txReceipt = null;
    txReceipt = TestHelper.getTxReceipt(transactionID, stub);
    log.info(txReceipt);

    Query query = TestHelper.getTxRecordByTxId(transactionBody.getTransactionID(), payerAccount,
        genKeyPair, defaultNodeAccount, TestHelper.getCryptoMaxFee(), ResponseType.ANSWER_ONLY);

    Response transactionRecord = stub.getTxRecordByTxID(query);
    Assert.assertNotNull(transactionRecord);
    Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
    TransactionRecord transactionRecordResponse = transactionRecord.getTransactionGetRecord()
        .getTransactionRecord();

    Assert.assertEquals(transactionBody.getTransactionID(),
        transactionRecordResponse.getTransactionID());
    log.info(transactionRecordResponse);
    
    for(AccountAmount accountAmount : transactionRecordResponse.getTransferList().getAccountAmountsList()) {
		if(accountAmount.getAccountID().equals(newlyCreateAccountId1)) {
			Assert.assertEquals(-deleteAcctBalance, accountAmount.getAmount());
		}else if(accountAmount.getAccountID().equals(transferAccountID)) {
			Assert.assertEquals(deleteAcctBalance, accountAmount.getAmount());
		}
	}

    // Get account Info of 1st Account
    Response accountInfoResponse = TestHelper
        .getCryptoGetAccountInfo(stub, newlyCreateAccountId1, payerAccount,
            genKeyPair, defaultNodeAccount);
    log.info(accountInfoResponse);
    Assert.assertEquals(ResponseCodeEnum.ACCOUNT_DELETED,
        accountInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());

    // try to transfer to an account after the delete account

    // create 2nd account by payer as genesis
    KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
    transaction = TestHelper
        .createAccountWithFee(payerAccount, defaultNodeAccount, secondPair, 1000000l,
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
    Assert
        .assertTrue(newlyCreateAccountId2.getAccountNum() > newlyCreateAccountId1.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");

    log.info("--------------------------------------");

    log.info("Get Tx record by Tx Id...");

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
    body = CommonUtils.extractTransactionBody(transaction);
    AccountID newlyCreateAccountId3 =
        TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
    Assert.assertNotNull(newlyCreateAccountId3);
    Assert
        .assertTrue(newlyCreateAccountId3.getAccountNum() > newlyCreateAccountId2.getAccountNum());
    log.info("Account ID " + newlyCreateAccountId3.getAccountNum() + " created successfully.");
    log.info("--------------------------------------");

    log.info("Get Tx record by Tx Id...");

    // first account as payer which is deleted so we should get insufficient payer balance
    Transaction transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
        newlyCreateAccountId2, newlyCreateAccountId1,
        firstPair, defaultNodeAccount, 1000l);

    log.info("Transferring 1000 coin from 1st account to 2nd account....");
    TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE,
        transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());

    // transfer 2
    transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
        newlyCreateAccountId2, payerAccount,
        genKeyPair, defaultNodeAccount, 1000l);

    log.info("Transferring 1000 coin from 1st account to 2nd account....");
    transferRes = stub.cryptoTransfer(transfer1);
    Assert.assertNotNull(transferRes);
    Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());

    log.info(
        "Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
    TransactionBody transferBody = CommonUtils.extractTransactionBody(transfer1);
    txReceipt = TestHelper
        .getTxReceipt(transferBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info(txReceipt);
    Assert.assertEquals(ResponseCodeEnum.ACCOUNT_DELETED, txReceipt.getStatus());
    log.info("-----------------------------------------");

    // check for the update request

    Duration autoRenew = com.hederahashgraph.builder.RequestBuilder.getDuration(accountDuration + 30);
    Transaction updateaccount1 = TestHelper.updateAccount(newlyCreateAccountId1, payerAccount,
        genesisPrivateKey, defaultNodeAccount, autoRenew);
    List<PrivateKey> privateKeyList = new ArrayList<>();
    privateKeyList.add(genesisPrivateKey);
    privateKeyList.add(firstPair.getPrivate());
    Transaction signUpdate = com.hederahashgraph.builder.TransactionSigner.signTransaction(updateaccount1, privateKeyList);
    log.info("updateAccount request=" + signUpdate);
    response = stub.updateAccount(signUpdate);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
    TransactionBody updateAccountBody = CommonUtils.extractTransactionBody(updateaccount1);
    txReceipt = TestHelper.getTxReceipt(updateAccountBody.getTransactionID(), stub);
    Assert.assertNotNull(txReceipt);
    log.info(txReceipt);
    Assert.assertEquals(ResponseCodeEnum.ACCOUNT_DELETED, txReceipt.getStatus());

    //get records shoul give the pre check value as Account_Deleted
    getTransactionRecordsByDeletedAccountId(genKeyPair, payerAccount, defaultNodeAccount,
        newlyCreateAccountId1);
  }

  private void getTransactionRecordsByDeletedAccountId(KeyPair genesisKeyPair,
      AccountID payerAccount, AccountID defaultNodeAccount, AccountID accountID) throws Exception {
    log.info("Get Tx records by account Id...");
    long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetAccountRecords);
    Query query = TestHelper
        .getTxRecordByAccountId(accountID, payerAccount, genesisKeyPair, defaultNodeAccount,
            fee, ResponseType.COST_ANSWER);
    Response transactionRecord = stub.getAccountRecords(query);
    log.info(transactionRecord);
    Assert.assertEquals(ResponseCodeEnum.ACCOUNT_DELETED,
        transactionRecord.getCryptoGetAccountRecords().getHeader()
            .getNodeTransactionPrecheckCode());

  }

}
