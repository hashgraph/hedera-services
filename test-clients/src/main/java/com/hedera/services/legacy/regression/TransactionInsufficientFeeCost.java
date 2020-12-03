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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Tests that transaction calls resulting in INSUFFICIENT_TX_FEE also return the needed fee in the
 * cost field
 *
 * @author Peter
 */
public class TransactionInsufficientFeeCost {

  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private static long INITIAL_FEE_OFFERED = 0;
  private final static String CONTRACT_MEMO_STRING_1 = "This is a memo string with only Ascii characters";
  private final static String CONTRACT_MEMO_STRING_3 = "Yet another memo.";
  private final Logger log = LogManager.getLogger(TransactionInsufficientFeeCost.class);


  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final long MAX_TX_FEE = TestHelper.getContractMaxFee();
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private List<PrivateKey> waclPrivKeyList;
  protected static ByteString dummyFileData = null;
  private static String host;
  private static int port;
  private static Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);


  public static void main(String args[]) throws Exception {

    Properties properties = TestHelper.getApplicationProperties();
    host = properties.getProperty("host");
    port = Integer.parseInt(properties.getProperty("port"));
    node_account_number = Utilities.getDefaultNodeAccount();
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();
    dummyFileData = ByteString.copyFrom("abcd".getBytes());

    TransactionInsufficientFeeCost tifc = new TransactionInsufficientFeeCost();
    tifc.demo();
  }


  private void loadGenesisAndNodeAcccounts() throws Exception {
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");

    // get Private Key
    KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
    PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
    KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

    // get the Account Object
    genesisAccount = genesisAccountList.get(0).getAccountId();
    accountKeyPairs.put(genesisAccount, genesisKeyPair);
  }

  private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
      throws Exception {
    Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
        nodeAccount, payer,
        accountKeyPairs.get(payer), nodeAccount, transferAmt);
    return transferTx;

  }

  private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    // First time through get INSUFFICIENT_TX_FEE and the cost
    long feeOffered = INITIAL_FEE_OFFERED;
    Transaction transaction = TestHelper.createAccount(
        payerAccount, nodeAccount, keyPair, initialBalance, feeOffered,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    Transaction signTransaction = TransactionSigner.signTransaction(transaction,
        Collections.singletonList(accountKeyPairs.get(payerAccount).getPrivate()));
    TransactionResponse response = stub.createAccount(signTransaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First create account attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    transaction = TestHelper.createAccount(
        payerAccount, nodeAccount, keyPair, initialBalance, feeOffered,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
        TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
    signTransaction = TransactionSigner.signTransaction(transaction,
        Collections.singletonList(accountKeyPairs.get(payerAccount).getPrivate()));
    response = stub.createAccount(signTransaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second create account attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    channel.shutdown();
    return newlyCreateAccountId;
  }

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId,
      ResponseCodeEnum expectedStatus) throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Response transactionReceipts = stub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
        .getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN)) {
      Thread.sleep(1000);
      transactionReceipts = stub.getTransactionReceipts(query);
      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      attempts++;
    }
    channel.shutdown();
    Assert.assertEquals(expectedStatus,
        transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());
    return transactionReceipts.getTransactionGetReceipt();
  }

  private ContractID createContractWithKey(AccountID payerAccount, FileID contractFile,
      Key adminKey,
      AccountID adminAccount) throws Exception {
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();

    Duration contractAutoRenew = Duration.newBuilder()
        .setSeconds(CustomPropertiesSingleton.getInstance().getContractDuration()).build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(payerAccount).getPrivate());
    keyList.add(accountKeyPairs.get(adminAccount).getPrivate());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long feeOffered = INITIAL_FEE_OFFERED;

    // First time through get INSUFFICIENT_TX_FEE and the cost
    Transaction transaction = RequestBuilder
        .getCreateContractRequest(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp,
            transactionDuration, true, "", 250000L, contractFile, ByteString.EMPTY, 0L,
            contractAutoRenew, CONTRACT_MEMO_STRING_1, adminKey);
    transaction = TransactionSigner.signTransaction(transaction, keyList);
    TransactionResponse response = stub.createContract(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First create contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transaction = RequestBuilder
        .getCreateContractRequest(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp,
            transactionDuration, true, "", 250000L, contractFile, ByteString.EMPTY, 0L,
            contractAutoRenew, "", adminKey);
    transaction = TransactionSigner.signTransaction(transaction, keyList);
    response = stub.createContract(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second create contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody createContractBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
    		createContractBody.getTransactionID(), ResponseCodeEnum.SUCCESS);
    if (contractCreateReceipt != null) {
      createdContract = contractCreateReceipt.getReceipt().getContractID();
    }
    TransactionRecord trRecord = getTransactionRecord(payerAccount,
        createContractBody.getTransactionID());
    Assert.assertNotNull(trRecord);
    Assert.assertTrue(trRecord.hasContractCreateResult());
    Assert.assertEquals(trRecord.getContractCreateResult().getContractID(),
        contractCreateReceipt.getReceipt().getContractID());

    channel.shutdown();
    return createdContract;
  }

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    long fee = FeeClient.getCostForGettingTxRecord();
    Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.COST_ANSWER);
    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.ANSWER_ONLY);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
    System.out.println("tx record = " + txRecord);
    channel.shutdown();
    return txRecord;
  }


  private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
    Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
    return recordResp;
  }

  public void updateContractWithKey(AccountID payerAccount, ContractID contractToUpdate,
      Duration autoRenewPeriod, Timestamp expirationTime, String contractMemo,
      AccountID adminAccount, ResponseCodeEnum expectedStatus) throws Exception {

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(payerAccount).getPrivate());
    keyList.add(accountKeyPairs.get(adminAccount).getPrivate());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long feeOffered = INITIAL_FEE_OFFERED;

    // First time through get INSUFFICIENT_TX_FEE and the cost
    Transaction updateContractRequest = RequestBuilder
        .getContractUpdateRequest(payerAccount, nodeAccount, feeOffered, timestamp,
            transactionDuration, true, "", contractToUpdate, autoRenewPeriod, null, null,
            expirationTime, contractMemo);
    updateContractRequest = TransactionSigner.signTransaction(updateContractRequest, keyList);
    TransactionResponse response = stub.updateContract(updateContractRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First update contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    updateContractRequest = RequestBuilder
        .getContractUpdateRequest(payerAccount, nodeAccount, feeOffered, timestamp,
            transactionDuration, true, "", contractToUpdate, autoRenewPeriod, null, null,
            expirationTime, contractMemo);
    updateContractRequest = TransactionSigner.signTransaction(updateContractRequest, keyList);
    response = stub.updateContract(updateContractRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second update contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody updateContractBody = TransactionBody.parseFrom(updateContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(
    		updateContractBody.getTransactionID(), expectedStatus);
    Assert.assertNotNull(contractUpdateReceipt);
    TransactionRecord trRecord = getTransactionRecord(payerAccount,
        updateContractBody.getTransactionID());
    Assert.assertNotNull(trRecord);
    channel.shutdown();
  }

  private ResponseCodeEnum deleteContract(AccountID payerAccount, ContractID contractId,
      AccountID adminAccount, ResponseCodeEnum expectedStatus) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(payerAccount).getPrivate());
    keyList.add(accountKeyPairs.get(adminAccount).getPrivate());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long feeOffered = INITIAL_FEE_OFFERED;

    // First time through get INSUFFICIENT_TX_FEE and the cost
    Transaction deleteContractRequest = RequestBuilder
        .getDeleteContractRequest(payerAccount, nodeAccount, feeOffered, timestamp,
            transactionDuration, contractId, null, null, true, "");
    deleteContractRequest = TransactionSigner.signTransaction(deleteContractRequest, keyList);
    TransactionResponse response = stub.deleteContract(deleteContractRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First delete contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();


    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    deleteContractRequest = RequestBuilder
        .getDeleteContractRequest(payerAccount, nodeAccount, feeOffered, timestamp,
            transactionDuration, contractId, null, null, true, "");
    deleteContractRequest = TransactionSigner.signTransaction(deleteContractRequest, keyList);
    response = stub.deleteContract(deleteContractRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second delete contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody deleteContractBody = TransactionBody.parseFrom(deleteContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractDeleteReceipt = getReceipt(
        deleteContractBody.getTransactionID(), expectedStatus);

    channel.shutdown();
    return contractDeleteReceipt.getReceipt().getStatus();
  }

  private void setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet)
      throws Exception {
    byte[] dataToSet = encodeSet(valuetoSet);
    //set value to simple storage smart contract
    byte[] retData = callContract(payerAccount, contractId, dataToSet);
  }
  private byte[] encodeSet(int valueToAdd) {
    CallTransaction.Function function = getSetFunction();
    byte[] encodedFunc = function.encode(valueToAdd);

    return encodedFunc;
  }

  private CallTransaction.Function getSetFunction() {
    String funcJson = SC_SET_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }

    List<PrivateKey> keyList = Collections.singletonList(
        accountKeyPairs.get(payerAccount).getPrivate());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long feeOffered = INITIAL_FEE_OFFERED;

    // First time through get INSUFFICIENT_TX_FEE and the cost
    Transaction callContractRequest = RequestBuilder
        .getContractCallRequest(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, 0);
    callContractRequest = TransactionSigner.signTransaction(callContractRequest, keyList);
    TransactionResponse response = stub.contractCallMethod(callContractRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First call contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    callContractRequest = RequestBuilder
        .getContractCallRequest(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, 0);
    callContractRequest = TransactionSigner.signTransaction(callContractRequest, keyList);
    response = stub.contractCallMethod(callContractRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second call contract attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID(), ResponseCodeEnum.SUCCESS);
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      TransactionRecord trRecord = getTransactionRecord(payerAccount,
          callContractBody.getTransactionID());
      if (trRecord != null && trRecord.hasContractCallResult()) {
        ContractFunctionResult callResults = trRecord.getContractCallResult();
        String errMsg = callResults.getErrorMessage();
        if (StringUtils.isEmpty(errMsg)) {
          if (!callResults.getContractCallResult().isEmpty()) {
            dataToReturn = callResults.getContractCallResult().toByteArray();
          }
        } else {
          log.info("@@@ Contract Call resulted in error: " + errMsg);
        }
      }
    }
    channel.shutdown();
    return dataToReturn;
  }

  public void crytpoTransfer(AccountID fromAccount, AccountID toAccount, long amount)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(fromAccount).getPrivate());
    keyList.add(accountKeyPairs.get(fromAccount).getPrivate());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long feeOffered = INITIAL_FEE_OFFERED;

    // First time through get INSUFFICIENT_TX_FEE and the cost
    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(
        fromAccount.getAccountNum(), fromAccount.getRealmNum(), fromAccount.getShardNum(),
        nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
        feeOffered, timestamp, transactionDuration,
        true, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    transferTx = TransactionSigner.signTransaction(transferTx, keyList);
    TransactionResponse response = stub.cryptoTransfer(transferTx);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First transfer attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transferTx = RequestBuilder.getCryptoTransferRequest(
        fromAccount.getAccountNum(), fromAccount.getRealmNum(), fromAccount.getShardNum(),
        nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
        feeOffered, timestamp, transactionDuration,
        true, "Test Transfer", fromAccount.getAccountNum(), -amount,
        toAccount.getAccountNum(), amount);
    // sign the tx
    transferTx = TransactionSigner.signTransaction(transferTx, keyList);
    response = stub.cryptoTransfer(transferTx);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second transfer attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody cryptoTransferBody = TransactionBody.parseFrom(transferTx.getBodyBytes());
    TransactionGetReceiptResponse contractDeleteReceipt = getReceipt(
        cryptoTransferBody.getTransactionID(), ResponseCodeEnum.SUCCESS);

    channel.shutdown();
  }

  public void crytpoUpdate(AccountID accountID) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Duration accountAutoRenew = Duration.newBuilder()
        .setSeconds(CustomPropertiesSingleton.getInstance().getContractDuration() + 2).build();

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(accountID).getPrivate());
    keyList.add(accountKeyPairs.get(accountID).getPrivate());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long feeOffered = INITIAL_FEE_OFFERED;

    // First time through get INSUFFICIENT_TX_FEE and the cost
    Transaction cryptoUpdateRequest = RequestBuilder.getAccountUpdateRequest(accountID,
        accountID.getAccountNum(), accountID.getRealmNum(), accountID.getShardNum(),
        nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
        feeOffered, timestamp,
        transactionDuration, true, "Update Account", accountAutoRenew);
    cryptoUpdateRequest = TransactionSigner.signTransaction(cryptoUpdateRequest, keyList);
    TransactionResponse response = stub.updateAccount(cryptoUpdateRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First crypto update attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    cryptoUpdateRequest = RequestBuilder.getAccountUpdateRequest(accountID,
        accountID.getAccountNum(), accountID.getRealmNum(), accountID.getShardNum(),
        nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
        feeOffered, timestamp,
        transactionDuration, true, "Update Account", accountAutoRenew);
    cryptoUpdateRequest = TransactionSigner.signTransaction(cryptoUpdateRequest, keyList);
    response = stub.updateAccount(cryptoUpdateRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second crypto update attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody cryptoUpdateBody = TransactionBody.parseFrom(cryptoUpdateRequest.getBodyBytes());
    TransactionGetReceiptResponse contractDeleteReceipt = getReceipt(
        cryptoUpdateBody.getTransactionID(), ResponseCodeEnum.SUCCESS);

    channel.shutdown();
  }

  public void cryptoDelete(AccountID accountID) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(accountID).getPrivate());
    keyList.add(accountKeyPairs.get(accountID).getPrivate());
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    long feeOffered = INITIAL_FEE_OFFERED;

    // First time through get INSUFFICIENT_TX_FEE and the cost
    TransactionID transactionID = TransactionID.newBuilder().setAccountID(accountID)
        .setTransactionValidStart(timestamp).build();
    CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(accountID).setTransferAccountID(accountID)
        .build();
    TransactionBody transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeAccount)
        .setTransactionFee(feeOffered)
        .setTransactionValidDuration(transactionDuration)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    byte[] bodyBytesArr = transactionBody.toByteArray();
    ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
    Transaction cryptoDeleteRequest = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    cryptoDeleteRequest = TransactionSigner.signTransaction(cryptoDeleteRequest, keyList);
    TransactionResponse response = stub.updateAccount(cryptoDeleteRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First crypto delete attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    transactionID = TransactionID.newBuilder().setAccountID(accountID)
        .setTransactionValidStart(timestamp).build();
    cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
        .newBuilder().setDeleteAccountID(accountID).setTransferAccountID(nodeAccount)
        .build();
    transactionBody = TransactionBody.newBuilder()
        .setTransactionID(transactionID)
        .setNodeAccountID(nodeAccount)
        .setTransactionFee(feeOffered)
        .setTransactionValidDuration(transactionDuration)
        .setMemo("Crypto Delete")
        .setCryptoDelete(cryptoDeleteTransactionBody)
        .build();
    bodyBytesArr = transactionBody.toByteArray();
    bodyBytes = ByteString.copyFrom(bodyBytesArr);
    cryptoDeleteRequest = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
    cryptoDeleteRequest = TransactionSigner.signTransaction(cryptoDeleteRequest, keyList);
    response = stub.updateAccount(cryptoDeleteRequest);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second crypto delete attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody cryptoDeleteBody = TransactionBody.parseFrom(cryptoDeleteRequest.getBodyBytes());
    TransactionGetReceiptResponse contractDeleteReceipt = getReceipt(
        cryptoDeleteBody.getTransactionID(), ResponseCodeEnum.SUCCESS);

    channel.shutdown();
  }

  public FileID fileCreate(AccountID payerAccount, ByteString fileData) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    FileServiceGrpc.FileServiceBlockingStub stub = FileServiceGrpc.newBlockingStub(channel);
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp,
        CustomPropertiesSingleton.getInstance().getContractDuration());

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(payerAccount).getPrivate());
    long feeOffered = INITIAL_FEE_OFFERED;

    List<Key> waclPubKeyList = new ArrayList<>();
    waclPrivKeyList = new ArrayList<>();
    TestHelper.genWacl(1, waclPubKeyList, waclPrivKeyList);

    // First time through get INSUFFICIENT_TX_FEE and the cost
    Transaction FileCreateRequest = RequestBuilder
        .getFileCreateBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, transactionDuration, true, "FileCreate",
            fileData, fileExp, waclPubKeyList);
    Transaction filesignedByPayer = TransactionSigner.signTransaction(FileCreateRequest, keyList);
    // append wacl sigs
    Transaction filesigned = TransactionSigner.signTransaction(filesignedByPayer, waclPrivKeyList, true);
    TransactionResponse response = stub.createFile(filesigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First file create attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    FileCreateRequest = RequestBuilder
        .getFileCreateBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, transactionDuration, true, "FileCreate",
            fileData, fileExp, waclPubKeyList);
    filesignedByPayer = TransactionSigner.signTransaction(FileCreateRequest, keyList);
    // append wacl sigs
    filesigned = TransactionSigner.signTransaction(filesignedByPayer, waclPrivKeyList, true);
    response = stub.createFile(filesigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second file create attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody fileCreateBody = TransactionBody.parseFrom(filesigned.getBodyBytes());
    TransactionGetReceiptResponse fileCreateReceipt = getReceipt(
        fileCreateBody.getTransactionID(), ResponseCodeEnum.SUCCESS);

    channel.shutdown();
    return fileCreateReceipt.getReceipt().getFileID();
  }

  public void fileAppend(AccountID payerAccount, ByteString fileData, FileID fid) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    FileServiceGrpc.FileServiceBlockingStub stub = FileServiceGrpc.newBlockingStub(channel);
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(payerAccount).getPrivate());
    long feeOffered = INITIAL_FEE_OFFERED;

    Transaction fileAppendRequest = RequestBuilder
        .getFileAppendBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, transactionDuration, true, "FileAppend",
            fileData, fid);
    Transaction txSignedByPayer = TransactionSigner.signTransaction(fileAppendRequest, keyList);
    Transaction txSigned = TransactionSigner.signTransaction(txSignedByPayer, waclPrivKeyList, true);
    TransactionResponse response = stub.appendContent(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE, response.getNodeTransactionPrecheckCode());
    log.info("First file append attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    fileAppendRequest = RequestBuilder
        .getFileAppendBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, transactionDuration, true, "FileAppend",
            fileData, fid);
    txSignedByPayer = TransactionSigner.signTransaction(fileAppendRequest, keyList);
    txSigned = TransactionSigner.signTransaction(txSignedByPayer, waclPrivKeyList, true);
    response = stub.appendContent(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second file append attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody fileAppendBody = TransactionBody.parseFrom(txSigned.getBodyBytes());
    TransactionGetReceiptResponse fileCreateReceipt = getReceipt(
        fileAppendBody.getTransactionID(), ResponseCodeEnum.SUCCESS);

    channel.shutdown();
  }

  public void fileUpdate(AccountID payerAccount, ByteString fileData, FileID fid) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    FileServiceGrpc.FileServiceBlockingStub stub = FileServiceGrpc.newBlockingStub(channel);
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp,
        CustomPropertiesSingleton.getInstance().getContractDuration());

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(payerAccount).getPrivate());
    long feeOffered = INITIAL_FEE_OFFERED;

    Transaction fileAppendRequest = RequestBuilder
        .getFileUpdateBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, fileExp, transactionDuration, true, "FileAppend",
            fileData, fid);
    Transaction txSignedByPayer = TransactionSigner.signTransaction(fileAppendRequest, keyList);
    Transaction txSigned = TransactionSigner.signTransaction(txSignedByPayer, waclPrivKeyList, true);
    TransactionResponse response = stub.appendContent(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE,
        response.getNodeTransactionPrecheckCode());
    log.info("First file update attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    fileAppendRequest = RequestBuilder
        .getFileUpdateBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, fileExp, transactionDuration, true, "FileAppend",
            fileData, fid);
    txSignedByPayer = TransactionSigner.signTransaction(fileAppendRequest, keyList);
    txSigned = TransactionSigner.signTransaction(txSignedByPayer, waclPrivKeyList, true);
    response = stub.appendContent(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second file update attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody fileUpdateBody = TransactionBody.parseFrom(txSigned.getBodyBytes());
    TransactionGetReceiptResponse fileCreateReceipt = getReceipt(
        fileUpdateBody.getTransactionID(), ResponseCodeEnum.SUCCESS);

    channel.shutdown();
  }

  public void fileDelete(AccountID payerAccount, FileID fid) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    FileServiceGrpc.FileServiceBlockingStub stub = FileServiceGrpc.newBlockingStub(channel);
    Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();

    List<PrivateKey> keyList = new ArrayList<>();
    keyList.add(accountKeyPairs.get(payerAccount).getPrivate());
    long feeOffered = INITIAL_FEE_OFFERED;

    Transaction fileDeleteRequest = RequestBuilder
        .getFileDeleteBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, transactionDuration, true, "FileDelete", fid);
    Transaction txSignedByPayer = TransactionSigner.signTransaction(fileDeleteRequest, keyList);
    Transaction txSigned = TransactionSigner.signTransaction(txSignedByPayer, waclPrivKeyList, true);
    TransactionResponse response = stub.appendContent(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_TX_FEE,
        response.getNodeTransactionPrecheckCode());
    log.info("First file delete attempt resulted in " + response.getNodeTransactionPrecheckCode());

    // Take the fee from the response
    feeOffered = response.getCost();

    // Second time through should work
    timestamp = TestHelper.getDefaultCurrentTimestampUTC();
    fileDeleteRequest = RequestBuilder
        .getFileDeleteBuilder(
            payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
            nodeAccount.getAccountNum(), nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
            feeOffered, timestamp, transactionDuration, true, "FileDelete", fid);
    txSignedByPayer = TransactionSigner.signTransaction(fileDeleteRequest, keyList);
    txSigned = TransactionSigner.signTransaction(txSignedByPayer, waclPrivKeyList, true);
    response = stub.appendContent(txSigned);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info("Second file delete attempt resulted in " + response.getNodeTransactionPrecheckCode());

    TransactionBody fileDeleteBody = TransactionBody.parseFrom(txSigned.getBodyBytes());
    TransactionGetReceiptResponse fileCreateReceipt = getReceipt(
        fileDeleteBody.getTransactionID(), ResponseCodeEnum.SUCCESS);

    channel.shutdown();
  }

  public void demo() throws Exception {
    loadGenesisAndNodeAcccounts();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
    byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
    Key adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    AccountID adminAccount = createAccount(adminKeyPair, genesisAccount,
        TestHelper.getCryptoMaxFee() * 5);

    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount,
            TestHelper.getCryptoMaxFee() * 5);
    log.info("Account created successfully " + crAccount.getAccountNum());

    String fileName = "contract/bytecodes/simpleStorage.bin";
    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(crAccount, fileName, crAccountKeyPair);
    Assert.assertNotNull(simpleStorageFileId);
    log.info("Smart Contract file uploaded successfully " + simpleStorageFileId.getFileNum());

    // Smart contract transactions
    ContractID sampleStorageContractId = createContractWithKey(crAccount, simpleStorageFileId,
        adminPubKey, adminAccount);
    Assert.assertNotNull(sampleStorageContractId);
    Assert.assertNotEquals(0L, sampleStorageContractId.getContractNum());
    log.info("Contract created successfully");

    setValueToContract(crAccount, sampleStorageContractId, 7);
    log.info("Contract called successfully");

    // Update only memo
    updateContractWithKey(crAccount, sampleStorageContractId, null, null, CONTRACT_MEMO_STRING_3,
        adminAccount, ResponseCodeEnum.SUCCESS);
    log.info("Contract updated successfully");

    deleteContract(crAccount, sampleStorageContractId, adminAccount, ResponseCodeEnum.SUCCESS);
    log.info("Contract deleted successfully");

    // File transactions
    FileID fileId = fileCreate(crAccount, dummyFileData);
    log.info("File create executed successfully: " + fileId);

    fileAppend(crAccount, dummyFileData, fileId);
    log.info("File append executed successfully");

    fileUpdate(crAccount, dummyFileData, fileId);
    log.info("File update executed successfully");

    fileDelete(crAccount, fileId);
    log.info("File delete executed successfully");

    // Crypto account transactions
    crytpoTransfer(genesisAccount, crAccount, 10);
    log.info("Crypto transfer executed successfully");

    crytpoUpdate(crAccount);
    log.info("Crypto update executed successfully");

    cryptoDelete(crAccount);
    log.info("Crypto delete executed successfully");
  }
}
