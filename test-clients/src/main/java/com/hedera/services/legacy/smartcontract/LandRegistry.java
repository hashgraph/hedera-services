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
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

// Arguments.  All are optional.
// 1: Number of properties to sell.  Default 4.
// 2-4: Shard, realm, and seq for contract to re-use.  Default is to create a new contract.
//      2-4 are used to run a pre-existing contract.

/**
 * Test a contract with non-fungible tokens
 *
 * @author Peter
 */
public class LandRegistry {

  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private static final int BATCH_SIZE = 250;
  private final Logger log = LogManager.getLogger(LandRegistry.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  private static final String LAND_REGISTRY_BIN = "testfiles/LandRegistry.bin";
  private static final String SC_CONTRACTBALANCE_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getContractBalance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String SC_BUYPROPERTY_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"buyer\",\"type\":\"address\"},{\"name\":\"propertyID\",\"type\":\"uint24\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"buyProperty\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
  private static final String SC_TRANSFER_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"ammount\",\"type\":\"uint256\"}],\"name\":\"transferFundsToQueen\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
  private static final String SC_HIGHESTSOLD_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"highestSold\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  
  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private PrivateKey genesisPrivateKey = null;

  private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String host;
  private static int port;
  private static long localCallGas;
  private static long contractDuration;

  public static void main(String args[]) throws Exception {
    Properties properties = TestHelper.getApplicationProperties();
    contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
    host = properties.getProperty("host");
    port = Integer.parseInt(properties.getProperty("port"));
    node_account_number = Utilities.getDefaultNodeAccount();
    node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
    node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
    nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
        .setRealmNum(node_shard_number).setShardNum(node_realm_number).build();
    localCallGas = Long.parseLong(properties.getProperty("LOCAL_CALL_GAS"));

    int numberOfSales = 10000;
    if ((args.length) > 0) {
      numberOfSales = Integer.parseInt(args[0]);
    }

    if (args.length > 1 && args.length < 4) {
      System.out.println("Specify sales, shard, realm, contractNumber");
      System.exit(1);
    }

    ContractID passedContractID = null;
    if (args.length > 1) {
      int shard = Integer.parseInt(args[1]);
      int realm = Integer.parseInt(args[2]);
      int number = Integer.parseInt(args[3]);
      passedContractID = ContractID.newBuilder()
          .setShardNum(shard).setRealmNum(realm).setContractNum(number).build();

    }

    LandRegistry scSs = new LandRegistry();
    scSs.demo(numberOfSales, passedContractID);
  }


  private void loadGenesisAndNodeAcccounts() throws Exception {
    Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

    // Get Genesis Account key Pair
    List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");

    // get Private Key
    genesisPrivateKey = genesisAccountList.get(0).getKeyPairList().get(0).getPrivateKey();
    KeyPairObj genKeyPairObj = genesisAccountList.get(0).getKeyPairList().get(0);
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

  private AccountID createAccount(AccountID payerAccount, long initialBalance) throws Exception {

    KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
    return createAccount(keyGenerated, payerAccount, initialBalance);
  }

  private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Transaction transaction = TestHelper
        .createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
            accountKeyPairs.get(payerAccount));
    TransactionResponse response = stub.createAccount(transaction);
    Assert.assertNotNull(response);
    Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
    System.out.println(
        "Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
            .name());

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    channel.shutdown();
    return newlyCreateAccountId;
  }

  private long batchCreateAccounts(int quantity, KeyPair keyPair, AccountID payerAccount,
      long initialBalance) throws Exception {
    long start = System.currentTimeMillis();
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    Transaction transaction = null;
    for (int i = 0; i < quantity; i++) {
      transaction = TestHelper
          .createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
              accountKeyPairs.get(payerAccount));
      TransactionResponse response = stub.createAccount(transaction);
      Assert.assertNotNull(response);
      Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
      log.info("Created account " + (i + 1) + " of " + quantity);
    }

    TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
    AccountID newlyCreateAccountId = TestHelper
        .getTxReceipt(body.getTransactionID(), stub).getAccountID();
    accountKeyPairs.put(newlyCreateAccountId, keyPair);
    channel.shutdown();
    long end = System.currentTimeMillis();
    log.info(quantity + " accounts created in " + (end - start) + " msec.");
    log.info("Highest created account is " + newlyCreateAccountId);
    return newlyCreateAccountId.getAccountNum();
  }

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
    TransactionGetReceiptResponse receiptToReturn = null;
    Query query = Query.newBuilder()
        .setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
            transactionId, ResponseType.ANSWER_ONLY)).build();
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    Response transactionReceipts = stub.getTransactionReceipts(query);
    int attempts = 1;
    while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
        .getReceipt().getStatus().equals(ResponseCodeEnum.UNKNOWN)) {
      System.out.println("waiting to getTransactionReceipts as not Unknown..." +
          transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
      Thread.sleep(500);
      transactionReceipts = stub.getTransactionReceipts(query);
      attempts++;
    }
    System.out.println("Final getTransactionReceipts status: " +
        transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
    channel.shutdown();
    return transactionReceipts.getTransactionGetReceipt();

  }

  private ContractID createContract(AccountID payerAccount, FileID contractFile,
      long durationInSeconds) throws Exception {
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
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
            transactionDuration, true, "", 2500000, contractFile, ByteString.EMPTY, 0,
            contractAutoRenew, accountKeyPairs.get(payerAccount), "", null);

    TransactionResponse response = stub.createContract(createContractRequest);
    System.out.println(
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

  private TransactionID doBuyProperty(AccountID payerAccount, ContractID contractId, String buyer,
      int propertyId, int amount) throws Exception {
    byte[] dataToGet = encodeBuyProperty(buyer, propertyId, amount);
    TransactionID result = callContractOnly(payerAccount, contractId, dataToGet, amount);
    return result;
  }

  public static byte[] encodeBuyProperty(String buyer, int propertyId, int amount) {
    CallTransaction.Function function = getBuyPropertyFunction();
    byte[] encodedFunc = function.encode(buyer, propertyId, amount);
    return encodedFunc;
  }

  public static CallTransaction.Function getBuyPropertyFunction() {
    String funcJson = SC_BUYPROPERTY_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static String decodeBuyPropertyResult(byte[] value) {
    String decodedReturnedValue = null;
    CallTransaction.Function function = getBuyPropertyFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      decodedReturnedValue = (String) retResults[0];
    }
    return decodedReturnedValue;
  }

  /*
Methods to run the transferFundsToQueen method
 */
  private void doTransfer(AccountID payerAccount, ContractID contractId, int amount,
      ResponseCodeEnum expectedStatus) throws Exception {
    byte[] dataToGet = encodeTransfer(amount);
    byte[] result = callContract(payerAccount, contractId, dataToGet, 0, expectedStatus);
  }

  public static byte[] encodeTransfer( int amount) {
    CallTransaction.Function function = getTransferFunction();
    byte[] encodedFunc = function.encode(amount);
    return encodedFunc;
  }

  public static CallTransaction.Function getTransferFunction() {
    String funcJson = SC_TRANSFER_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  /*
Methods to run the contractBalance method
 */
  private int getContractBalance(AccountID payerAccount, ContractID contractId)
      throws Exception {
    int retVal = -1; // Value if nothing was returned
    byte[] dataToGet = encodeContractBalance();
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeContractBalanceResult(result);
    }
    return retVal;
  }

  public static byte[] encodeContractBalance() {
    CallTransaction.Function function = getContractBalanceFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public static CallTransaction.Function getContractBalanceFunction() {
    String funcJson = SC_CONTRACTBALANCE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static int decodeContractBalanceResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getContractBalanceFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  /*
Methods to run the highestSold method
 */
  private int getHighestSold(AccountID payerAccount, ContractID contractId)
      throws Exception {
    int retVal = -1; // Value if nothing was returned
    byte[] dataToGet = encodeHighestSold();
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeHighestSoldResult(result);
    }
    return retVal;
  }

  public static byte[] encodeHighestSold() {
    CallTransaction.Function function = getHighestSoldFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public static CallTransaction.Function getHighestSoldFunction() {
    String funcJson = SC_HIGHESTSOLD_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static int decodeHighestSoldResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getHighestSoldFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data,
      int value, ResponseCodeEnum expectedStatus) throws Exception {
    byte[] dataToReturn = null;
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
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
            transactionDuration, 250000, contractToCall, dataBstr, value,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = stub.contractCallMethod(callContractRequest);
    System.out.println(
        " callContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(500);
    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID());
    Assert.assertEquals(expectedStatus, contractCallReceipt.getReceipt().getStatus());
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

  private TransactionID callContractOnly(AccountID payerAccount, ContractID contractToCall,
      byte[] data,
      int value) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
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
            transactionDuration, 250000, contractToCall, dataBstr, value,
            accountKeyPairs.get(payerAccount));

    TransactionResponse response = stub.contractCallMethod(callContractRequest);
    System.out.println(
        " callContractOnly Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionID txId = callContractBody.getTransactionID();

    channel.shutdown();
    return txId;
  }

  private String getReceiptAndRecord(AccountID payerAccount, TransactionID txId) throws Exception{
    byte[] dataToReturn = null;
    String retVal = null;

    TransactionGetReceiptResponse contractCallReceipt = getReceipt(txId);
    Assert.assertEquals(ResponseCodeEnum.SUCCESS, contractCallReceipt.getReceipt().getStatus());
    if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
        .equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
      TransactionRecord trRecord = getTransactionRecord(payerAccount, txId);
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

    if (dataToReturn != null && dataToReturn.length > 0) {
      retVal = decodeBuyPropertyResult(dataToReturn);
    }
    return retVal;
  }

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
    int port = 50211;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
    long fee = FeeClient.getCostForGettingTxRecord();
    Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.COST_ANSWER);
    fee = recordResp.getTransactionGetRecord().getHeader().getCost();
    recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee,
        ResponseType.ANSWER_ONLY);
    TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
//    System.out.println("tx record = " + txRecord);
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


  private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data)
      throws Exception {
    byte[] dataToReturn = null;
    AccountID createdAccount = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
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
        ResponseType.COST_ANSWER);
    fee = callResp.getContractCallLocal().getHeader().getCost() + localCallGas;
    callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.ANSWER_ONLY);
    Assert.assertEquals(ResponseCodeEnum.OK,
        callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());
    System.out.println("callContractLocal response = " + callResp);
    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    channel.shutdown();
    return functionResults.toByteArray();
  }

  private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
      ResponseType responseType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            responseType);

    Response callResp = stub.contractCallLocalMethod(contractCallLocal);
    return callResp;
  }

  private String getContractByteCode(AccountID payerAccount,
      ContractID contractId) throws Exception {
    String byteCode = "";
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();

    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetBytecode);
    Response respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
        ResponseType.COST_ANSWER);

    fee = respToReturn.getContractGetBytecodeResponse().getHeader().getCost();
    respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
        ResponseType.ANSWER_ONLY);
    ByteString contractByteCode = null;
    contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
    if (contractByteCode != null && !contractByteCode.isEmpty()) {
      byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
    }
    channel.shutdown();

    return byteCode;
  }

  private Response executeContractByteCodeQuery(AccountID payerAccount, ContractID contractId,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee,
      ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getContractBytecodeQuery = RequestBuilder
        .getContractGetBytecodeQuery(contractId, paymentTx, responseType);
    Response respToReturn = stub.contractGetBytecode(getContractBytecodeQuery);
    return respToReturn;
  }

  private AccountInfo getCryptoGetAccountInfo(
      AccountID accountID) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

    long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetInfo);

    Response respToReturn = executeGetAcctInfoQuery(accountID, stub, fee, ResponseType.COST_ANSWER);

    fee = respToReturn.getCryptoGetInfo().getHeader().getCost();
    respToReturn = executeGetAcctInfoQuery(accountID, stub, fee, ResponseType.ANSWER_ONLY);
    AccountInfo accInfToReturn = null;
    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();
    channel.shutdown();

    return accInfToReturn;
  }


  private Response executeGetAcctInfoQuery(AccountID accountID,
      CryptoServiceGrpc.CryptoServiceBlockingStub stub,
      long fee, ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(accountID, fee);
    Query cryptoGetInfoQuery = RequestBuilder
        .getCryptoGetInfoQuery(accountID, paymentTx, responseType);

    Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
    return respToReturn;
  }

  private GetBySolidityIDResponse getBySolidityID(AccountID payerAccount,
      String solidityId) throws Exception {
    int port = 50211;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    long fee = FeeClient.getFeegetBySolidityID();
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query getBySolidityIdQuery = RequestBuilder
        .getBySolidityIDQuery(solidityId, paymentTx, ResponseType.ANSWER_ONLY);

    Response respToReturn = stub.getBySolidityID(getBySolidityIdQuery);
    GetBySolidityIDResponse bySolidityReturn = null;
    bySolidityReturn = respToReturn.getGetBySolidityID();
    channel.shutdown();

    return bySolidityReturn;
  }

  public void demo(int numberOfSales, ContractID passedContractID) throws Exception {
    loadGenesisAndNodeAcccounts();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
        nodeAccount);
    channel.shutdown();

    ContractID registryContractId;
    // Use passed contract ID or create a new contract;
    if (passedContractID != null) {
      registryContractId = passedContractID;
    } else {

      KeyPair fakeKeyPair = new KeyPair(null, genesisPrivateKey);
      FileID simpleStorageFileId = LargeFileUploadIT
          .uploadFile(genesisAccount, LAND_REGISTRY_BIN, fakeKeyPair);
      Assert.assertNotNull(simpleStorageFileId);

      log.info("Smart Contract file uploaded successfully");
      registryContractId = createContract(genesisAccount, simpleStorageFileId,
          contractDuration);
      Assert.assertNotNull(registryContractId);
      log.info("Contract created successfully");
    }

    int[] prices = {25000000,50000000,100000000};
    KeyPair buyerKeyPair = new KeyPairGenerator().generateKeyPair();
    int firstSale = getHighestSold(genesisAccount, registryContractId) + 1;
    log.info("Next sale value is " + firstSale);

    int property = -1;
    for (int base = 0; base < numberOfSales; base += BATCH_SIZE) {
      int limit = Math.min(base + BATCH_SIZE, numberOfSales);
      int size = limit - base;
      log.info("Batch from " + (base + 1) + " to " + limit + " of " + numberOfSales +
          ", Batch size is " + size);
      long maxAccountSeq = batchCreateAccounts(size, buyerKeyPair, genesisAccount,
          100000000000L);
      log.info("Min account calculated to be " + (maxAccountSeq - size + 1));
      log.info("Max account calculated to be " + maxAccountSeq);

      long buyerSeq = maxAccountSeq - (long) size;
      long start = System.currentTimeMillis();
      List<TransactionID> txIdList = new ArrayList<>();

      // Do contract calls for sales but do not get receipts yet.
      for (property = firstSale + base; property < firstSale + limit; property++) {
        buyerSeq += 1L;
        AccountID buyerAccount = AccountID.newBuilder()
            .setShardNum(0L).setRealmNum(0L).setAccountNum(buyerSeq).build();
        String buyerSolidityAddress = CommonUtils.calculateSolidityAddress(
            0, 0, buyerAccount.getAccountNum());
        accountKeyPairs.put(buyerAccount, buyerKeyPair);
        TransactionID txId = doBuyProperty(buyerAccount, registryContractId, buyerSolidityAddress,
            property, prices[property % 3]);
        txIdList.add(txId);
        log.info("Sale number " + property +  " to buyer seq " + buyerSeq + " requested.");
      }

      // Now read the receipts and records to ensure all calls have completed.
      for (TransactionID txId : txIdList) {
        String returned = getReceiptAndRecord(genesisAccount, txId);
        log.info("doBuyProperty returned " + returned);
      }
      long end = System.currentTimeMillis();
      log.info(size + " properties sold in " + (end - start) + " msec.");
    }
    log.info("Last property sold was " + (property - 1) + ". The contract has ID " +
        registryContractId);

    int lastSale = getHighestSold(genesisAccount, registryContractId);
    log.info("The contract reports that the highest property sold is number " + lastSale);

  }

  private ContractInfo getContractInfo(AccountID payerAccount,
      ContractID contractId) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext(true)
        .build();

    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);
    long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetInfo);

    Response respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee,
        ResponseType.COST_ANSWER);

    fee = respToReturn.getContractGetInfo().getHeader().getCost();
    respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee,
        ResponseType.ANSWER_ONLY);
    ContractInfo contractInfToReturn = null;
    contractInfToReturn = respToReturn.getContractGetInfo().getContractInfo();
    channel.shutdown();

    return contractInfToReturn;
  }

  private Response executeGetContractInfo(AccountID payerAccount, ContractID contractId,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee,
      ResponseType responseType) throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);

    Query getContractInfoQuery = RequestBuilder
        .getContractGetInfoQuery(contractId, paymentTx, responseType);

    Response respToReturn = stub.getContractInfo(getContractInfoQuery);
    return respToReturn;
  }
}
