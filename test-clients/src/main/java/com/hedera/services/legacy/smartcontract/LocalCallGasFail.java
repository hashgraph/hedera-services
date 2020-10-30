package com.hedera.services.legacy.smartcontract;

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
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
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
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc.SmartContractServiceBlockingStub;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

/**
 * Fail on insufficient gas for local call
 *
 * @author Peter
 */
public class LocalCallGasFail {

  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private final Logger log = LogManager.getLogger(LocalCallGasFail.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String host;
  private static int port;
  private static long contractDuration;

  public static void main(String[] args) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
    host = properties.getProperty("host");
    port = Integer.parseInt(properties.getProperty("port"));
    node_account_number = Utilities.getDefaultNodeAccount();
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

    LocalCallGasFail scSs = new LocalCallGasFail();
    scSs.demo();
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
      throws Exception{
     return TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
        nodeAccount, payer,
        accountKeyPairs.get(payer), nodeAccount, transferAmt);
  }

  private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
            accountKeyPairs.get(payerAccount));
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    log.info(
        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    channel.shutdown();
    return newlyCreateAccountId;
  }

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) {
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
    while (attempts <= MAX_RECEIPT_RETRIES && !transactionReceipts.getTransactionGetReceipt()
        .getReceipt()
        .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        ;
      }
      transactionReceipts = stub.getTransactionReceipts(query);
      log.info("waiting to getTransactionReceipts as Success..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      attempts++;
    }
    if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
        .equals(ResponseCodeEnum.SUCCESS)) {
      receiptToReturn = transactionReceipts.getTransactionGetReceipt();
    }
    channel.shutdown();
    return transactionReceipts.getTransactionGetReceipt();

  }

  private ContractID createContract(AccountID payerAccount, FileID contractFile,
      long durationInSeconds) throws Exception {
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();

    Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), 100l, timestamp,
            transactionDuration, true, "", 1600000, contractFile, ByteString.EMPTY, 0,
            contractAutoRenew, accountKeyPairs.get(payerAccount), "", null);

    TransactionResponse response = stub.createContract(createContractRequest);
    log.info(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
    		createContractBody.getTransactionID());
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


  private byte[] encodeSet(int valueToAdd) {
    CallTransaction.Function function = getSetFunction();
    return function.encode(valueToAdd);
  }

  private CallTransaction.Function getSetFunction() {
    String funcJson = SC_SET_ABI.replaceAll("'", "\"");
    return CallTransaction.Function.fromJsonInterface(funcJson);
  }

  private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    ByteString dataBstr = ByteString.EMPTY;
    if (data != null) {
      dataBstr = ByteString.copyFrom(data);
    }
    Transaction callContractRequest = TestHelper
        .getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), node_account_number, 0l, 0l, 100l, timestamp,
            transactionDuration, 250000, contractToCall, dataBstr, 0,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = stub.contractCallMethod(callContractRequest);
    log.info(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(1000);
    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID());
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

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    int port = 50211;
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
    log.info("tx record = " + txRecord);
    channel.shutdown();
    return txRecord;
  }


  private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getRecordQuery = RequestBuilder
        .getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
    return stub.getTxRecordByTxID(getRecordQuery);
  }


  private CallTransaction.Function getGetValueFunction() {
    String funcJson = SC_GET_ABI.replaceAll("'", "\"");
    return CallTransaction.Function.fromJsonInterface(funcJson);
  }

  private byte[] encodeGetValue() {
    CallTransaction.Function function = getGetValueFunction();
    return function.encode();
  }

  private int decodeGetValueResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getGetValueFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }


  private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    ByteString callData = ByteString.EMPTY;
    int callDataSize = 0;
    if (data != null) {
      callData = ByteString.copyFrom(data);
      callDataSize = callData.size();
    }
    long fee = FeeClient.getCostContractCallLocalFee(callDataSize);
    Response callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.COST_ANSWER, 250000);
    fee = callResp.getContractCallLocal().getHeader().getCost();
    callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.ANSWER_ONLY, 100);
    log.info("callContractLocal response = " + callResp);
    Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_GAS,
        callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());
    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    channel.shutdown();
    return functionResults.toByteArray();
  }

  private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
      SmartContractServiceBlockingStub stub, ByteString callData, long fee,
      ResponseType resposeType, long gas) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, gas, callData, 0L, 5000, paymentTx,
            resposeType);

    return stub.contractCallLocalMethod(contractCallLocal);
  }


  private int getValueFromContract(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = 0;
    byte[] getValueEncodedFunction = encodeGetValue();
    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
    if (result != null && result.length > 0) {
      retVal = decodeGetValueResult(result);
    }
    return retVal;
  }


  private void setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet)
      throws Exception {
    byte[] dataToSet = encodeSet(valuetoSet);
    //set value to simple storage smart contract
    callContract(payerAccount, contractId, dataToSet);
  }

  public void demo() throws Exception {
    loadGenesisAndNodeAcccounts();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 5);
    log.info("Account created successfully");
    String fileName = "simpleStorage.bin";

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(crAccount, fileName, crAccountKeyPair);
    Assert.assertNotNull(simpleStorageFileId);
    Assert.assertNotEquals(0, simpleStorageFileId.getFileNum());
    log.info("Smart Contract file uploaded successfully");
    ContractID sampleStorageContractId = createContract(crAccount, simpleStorageFileId,
        contractDuration);
    Assert.assertNotNull(sampleStorageContractId);
    Assert.assertNotEquals(0, sampleStorageContractId.getContractNum());
    log.info("Contract created successfully");

    int iterations = 2;
    for (int i = 0; i < iterations; i++) {
      int currValueToSet = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);
      setValueToContract(crAccount, sampleStorageContractId, currValueToSet);
      //Thread.sleep(10000);
      int valueRetrieved = getValueFromContract(crAccount, sampleStorageContractId);
       Assert.assertEquals(0, valueRetrieved);
      log.info("Contract get/set iteration " + i + " failed on insufficient gas for local call ==>");
    }
  }
}
