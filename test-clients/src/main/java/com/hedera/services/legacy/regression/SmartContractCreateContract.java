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
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
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
import java.util.Arrays;
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

/**
 * Test one smart contract creating and using another
 *
 * @author Peter
 */
public class SmartContractCreateContract extends LegacySmartContractTest {
  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private final Logger log = LogManager.getLogger(SmartContractCreateContract.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  public static final String CREATE_TRIVIAL_BIN = "src/main/resource/CreateTrivial.bin";
  private static final int CREATED_TRIVIAL_CONTRACT_RETURNS = 7;
  private static final String SC_CT_CREATE_ABI = "{\"constant\":false,\"inputs\":[],\"name\":\"create\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
  private static final String SC_CT_GETINDIRECT_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getIndirect\",\"outputs\":[{\"name\":\"value\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
  private static final String SC_CT_GETADDRESS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getAddress\",\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

  private static AccountID nodeAccount;
  private static long node_account_number;
  private static long node_shard_number;
  private static long node_realm_number;
  public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
  private AccountID genesisAccount;
  private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
  private static String host;
  private static int port;
  private static long localCallGas;
  private static long contractDuration;

  public static void main(String args[]) throws Exception {

    int numberOfReps = 1;
    if ((args.length) > 0) {
      numberOfReps = Integer.parseInt(args[0]);
    }
    for (int i = 0; i < numberOfReps; i++) {
      SmartContractCreateContract scSs = new SmartContractCreateContract();
      scSs.demo(host, nodeAccount);
    }

  }

  private void setUp() {
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

  private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
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
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), 100l, timestamp,
            transactionDuration, true, "", 250000, contractFile, ByteString.EMPTY, 0,
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

  /*
  Functions for CreateTrivial.bin: create, getIndirect, getAddress
   */
  public static byte[] encodeCreateTrivialCreate() {
    CallTransaction.Function function = getCreateTrivialCreateFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public static CallTransaction.Function getCreateTrivialCreateFunction() {
    String funcJson = SC_CT_CREATE_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static byte[] encodeCreateTrivialGetIndirect() {
    CallTransaction.Function function = getCreateTrivialGetIndirectFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public static CallTransaction.Function getCreateTrivialGetIndirectFunction() {
    String funcJson = SC_CT_GETINDIRECT_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static int  decodeCreateTrivialGetResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getCreateTrivialGetIndirectFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  public static byte[] encodeCreateTrivialGetAddress() {
    CallTransaction.Function function = getCreateTrivialGetAddressFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public static CallTransaction.Function getCreateTrivialGetAddressFunction() {
    String funcJson = SC_CT_GETADDRESS_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static ContractID  decodeCreateTrivialGetAddress (byte[] value) {
    byte[] retVal = new byte[0];
    CallTransaction.Function function = getCreateTrivialGetAddressFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      retVal = (byte[]) retResults[0];

      long realm = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 4, 12));
      long accountNum = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 12, 20));
      ContractID contractID =  ContractID.newBuilder().setContractNum(accountNum).setRealmNum(realm)
          .setShardNum(0).build();
      return contractID;
   }
    return null;
  }

  private TransactionRecord callContract(AccountID payerAccount, ContractID contractToCall,
      byte[] data, ResponseCodeEnum expectedStatus)
      throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
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
    System.out.println(
        " createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
            .name());
    Thread.sleep(1000);
    TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
    TransactionGetReceiptResponse contractCallReceipt = getReceipt(
        callContractBody.getTransactionID());
    Assert.assertEquals(expectedStatus, contractCallReceipt.getReceipt().getStatus());

    TransactionRecord txRecord = getTransactionRecord(payerAccount,
        callContractBody.getTransactionID());
    Assert.assertTrue(txRecord.hasContractCallResult());

    String errMsg = txRecord.getContractCallResult().getErrorMessage();
    if (!StringUtils.isEmpty(errMsg)) {
      log.info("@@@ Contract Call resulted in error: " + errMsg);
      }

    channel.shutdown();
    return txRecord;
  }

  private TransactionRecord getTransactionRecord(AccountID payerAccount,
      TransactionID transactionId) throws Exception {
    AccountID createdAccount = null;
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
        ResponseType.COST_ANSWER);
    fee = callResp.getContractCallLocal().getHeader().getCost() + localCallGas;
    callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
        ResponseType.ANSWER_ONLY);
    System.out.println("callContractLocal response = " + callResp);
    ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
        .getContractCallResult();

    channel.shutdown();
    return functionResults.toByteArray();
  }

  private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
      SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
      ResponseType resposeType)
      throws Exception {
    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
    Query contractCallLocal = RequestBuilder
        .getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000, paymentTx,
            resposeType);

    Response callResp = stub.contractCallLocalMethod(contractCallLocal);
    return callResp;
  }

  private ContractID getAddressFromContract(AccountID payerAccount, ContractID contractId) throws Exception {
    ContractID retVal = null;
    byte[] dataToGet = encodeCreateTrivialGetAddress();
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeCreateTrivialGetAddress(result);
    }
    return retVal;
  }

  private int getIndirectFromContract(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = 0;
    byte[] dataToGet = encodeCreateTrivialGetIndirect();
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeCreateTrivialGetResult(result);
    }
    return retVal;
  }

  private TransactionRecord callContractCreate(AccountID payerAccount, ContractID contractId,
      ResponseCodeEnum expectedStatus)
      throws Exception {
    byte[] dataToSet = encodeCreateTrivialCreate();
    //set value to simple storage smart contract
    TransactionRecord txRec = callContract(payerAccount, contractId, dataToSet,
        ResponseCodeEnum.SUCCESS);
    return txRec;
  }

  public void demo(String grpcHost, AccountID nodeAccountID) throws Exception {
    log.info("-------------- STARTING SmartContractCreateContract Regression");
    setUp();
    host = grpcHost;
    nodeAccount = nodeAccountID;
    loadGenesisAndNodeAcccounts();

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();
    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
            SmartContractCreateContract.nodeAccount);
    channel.shutdown();

    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
    Assert.assertNotNull(crAccount);
    Assert.assertNotEquals(0, crAccount.getAccountNum());
    log.info("Account created successfully");

    FileID simpleStorageFileId = LargeFileUploadIT
        .uploadFile(crAccount, CREATE_TRIVIAL_BIN, new ArrayList<>(
                List.of(crAccountKeyPair.getPrivate())), host, nodeAccount);
    Assert.assertNotNull(simpleStorageFileId);
    Assert.assertNotEquals(0, simpleStorageFileId.getFileNum());
    log.info("Smart Contract file uploaded successfully");

    ContractID creatingContractId = createContract(crAccount, simpleStorageFileId,
        contractDuration);
    Assert.assertNotNull(creatingContractId);
    Assert.assertNotEquals(0, creatingContractId.getContractNum());
    log.info("Contract created successfully: " + creatingContractId);

    // Execute call to create second contract
    callContractCreate(crAccount, creatingContractId, ResponseCodeEnum.SUCCESS);
    log.info("Second contract created successfully");

    // Test that the first contract calling the second gets the correct return value
    int indirectValue = getIndirectFromContract(crAccount, creatingContractId);
    Assert.assertEquals(CREATED_TRIVIAL_CONTRACT_RETURNS, indirectValue);
    log.info("Indirect call succeeded.");

    // Get the second contract's address and info
    ContractID createdContractId = getAddressFromContract(crAccount, creatingContractId);
    log.info("Created contract ID is " + createdContractId);
    ContractInfo innerInfo = getContractInfo(crAccount, createdContractId);

    Assert.assertTrue(innerInfo.hasContractID());
    Assert.assertTrue(innerInfo.hasAccountID());
    Assert.assertTrue(innerInfo.hasExpirationTime());

    // Marker message for regression report
    log.info("Regression summary: This run is successful.");
    // Marker message for regression report
    log.info("-------------- RESULTS OF SmartContractCreateContract ----------------------");
    log.info("SmartContractCreateContract Regression summary: This run is successful.");
  }

  private ContractInfo getContractInfo(AccountID payerAccount,
      ContractID contractId) throws Exception {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
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
