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
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.HexUtils;
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
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

/**
 * Test that "precompiled opcodes" produce the expected results
 *
 * @author Peter
 */
public class PrecompiledOpcodes {
  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
  private final Logger log = LogManager.getLogger(PrecompiledOpcodes.class);

  private static final int MAX_RECEIPT_RETRIES = 60;
  public static final String PRECOMPILED_OPCODES_BIN = "/testfiles/PrecompiledOpcodes.bin";
  
  private static final String SCZ_ADDMOD_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"runAddmod\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
  private static final String SCZ_MULMOD_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"runMulmod\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
  private static final String SCZ_RIPEMD160_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_value\",\"type\":\"string\"}],\"name\":\"runRipemd160\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"bytes20\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
  private static final String SCZ_SHA256_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_value\",\"type\":\"string\"}],\"name\":\"runSha256\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"bytes32\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
  private static final String SCZ_SHA3_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_value\",\"type\":\"string\"}],\"name\":\"runSha3\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"bytes32\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
  private static final String SCZ_KECCAK256_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_value\",\"type\":\"string\"}],\"name\":\"runKeccak256\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"bytes32\"}],\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";

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

    int numberOfReps = 1;
    if ((args.length) > 0) {
      numberOfReps = Integer.parseInt(args[0]);
    }
    for (int i = 0; i < numberOfReps; i++) {
      PrecompiledOpcodes scSs = new PrecompiledOpcodes();
      scSs.demo();
    }

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

  private ContractID createContract(AccountID payerAccount, FileID contractFile) throws Exception {
    ContractID createdContract = null;
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .usePlaintext()
        .build();

    Duration contractAutoRenew = Duration.newBuilder().setSeconds(contractDuration).build();
    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
        .newBlockingStub(channel);

    Timestamp timestamp = RequestBuilder
        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
    Duration transactionDuration = RequestBuilder.getDuration(30);
    Transaction createContractRequest = TestHelper
        .getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
            payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
            nodeAccount.getShardNum(), 100l, timestamp,
            transactionDuration, true, "", 3_000_000, contractFile, ByteString.EMPTY, 0,
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
  Methods to run the runAddmod method
   */
  private int getRunAddmod(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = -123; // Value if nothing was returned
    byte[] dataToGet = encodeRunAddmod();
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeRunAddmodResult(result);
    }
    return retVal;
  }

  public static byte[] encodeRunAddmod() {
    CallTransaction.Function function = getRunAddmodFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public static CallTransaction.Function getRunAddmodFunction() {
    String funcJson = SCZ_ADDMOD_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static int decodeRunAddmodResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getRunAddmodFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  /*
  Methods to run the runMulmod method
   */
  private int getRunMulmod(AccountID payerAccount, ContractID contractId) throws Exception {
    int retVal = -123; // Value if nothing was returned
    byte[] dataToGet = encodeRunMulmod();
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeRunMulmodResult(result);
    }
    return retVal;
  }

  public static byte[] encodeRunMulmod() {
    CallTransaction.Function function = getRunMulmodFunction();
    byte[] encodedFunc = function.encode();
    return encodedFunc;
  }

  public static CallTransaction.Function getRunMulmodFunction() {
    String funcJson = SCZ_MULMOD_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static int decodeRunMulmodResult(byte[] value) {
    int decodedReturnedValue = 0;
    CallTransaction.Function function = getRunMulmodFunction();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      BigInteger retBi = (BigInteger) retResults[0];
      decodedReturnedValue = retBi.intValue();
    }
    return decodedReturnedValue;
  }

  /*
  Methods to run the runRipemd160 method
   */
  private byte[] getRunRipemd160(AccountID payerAccount, ContractID contractId, String encodeThis)
      throws Exception {
    byte[] retVal = new byte[0];
    byte[] dataToGet = encodeRunRipemd160(encodeThis);
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeRunRipemd160Result(result);
    }
    return retVal;
  }

  public static byte[] encodeRunRipemd160(String encodeThis) {
    CallTransaction.Function function = getRunRipemd160Function();
    byte[] encodedFunc = function.encode(encodeThis);
    return encodedFunc;
  }

  public static CallTransaction.Function getRunRipemd160Function() {
    String funcJson = SCZ_RIPEMD160_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static byte[] decodeRunRipemd160Result(byte[] value) {
    byte[] retBytes = new byte[20];
    CallTransaction.Function function = getRunRipemd160Function();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      System.arraycopy((byte[])retResults[0], 0, retBytes, 0, 20);
    }
    return retBytes;
  }

  /*
  Methods to run the runSha256 method
   */
  private byte[] getrunSha256(AccountID payerAccount, ContractID contractId, String encodeThis)
      throws Exception {
    byte[] retVal = new byte[0];
    byte[] dataToGet = encoderunSha256(encodeThis);
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decoderunSha256Result(result);
    }
    return retVal;
  }

  public static byte[] encoderunSha256(String encodeThis) {
    CallTransaction.Function function = getrunSha256Function();
    byte[] encodedFunc = function.encode(encodeThis);
    return encodedFunc;
  }

  public static CallTransaction.Function getrunSha256Function() {
    String funcJson = SCZ_SHA256_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static byte[] decoderunSha256Result(byte[] value) {
    byte[] retBytes = new byte[32];
    CallTransaction.Function function = getrunSha256Function();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      retBytes = (byte[]) retResults[0];
    }
    return retBytes;
  }

  /*
  Methods to run the runKeccak256 method
   */
  private byte[] getRunKeccak256(AccountID payerAccount, ContractID contractId, String encodeThis)
      throws Exception {
    byte[] retVal = new byte[0];
    byte[] dataToGet = encodeRunKeccak256(encodeThis);
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeRunKeccak256Result(result);
    }
    return retVal;
  }

  public static byte[] encodeRunKeccak256(String encodeThis) {
    CallTransaction.Function function = getRunKeccak256Function();
    byte[] encodedFunc = function.encode(encodeThis);
    return encodedFunc;
  }

  public static CallTransaction.Function getRunKeccak256Function() {
    String funcJson = SCZ_KECCAK256_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static byte[] decodeRunKeccak256Result(byte[] value) {
    byte[] retBytes = new byte[32];
    CallTransaction.Function function = getRunKeccak256Function();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      retBytes = (byte[]) retResults[0];
    }
    return retBytes;
  }

  /*
  Methods to run the runSha3 method
   */
  private byte[] getRunSha3(AccountID payerAccount, ContractID contractId, String encodeThis)
      throws Exception {
    byte[] retVal = new byte[0];
    byte[] dataToGet = encodeRunSha3(encodeThis);
    byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
    if (result != null && result.length > 0) {
      retVal = decodeRunSha3Result(result);
    }
    return retVal;
  }

  public static byte[] encodeRunSha3(String encodeThis) {
    CallTransaction.Function function = getRunSha3Function();
    byte[] encodedFunc = function.encode(encodeThis);
    return encodedFunc;
  }

  public static CallTransaction.Function getRunSha3Function() {
    String funcJson = SCZ_SHA3_ABI.replaceAll("'", "\"");
    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
    return function;
  }

  public static byte[] decodeRunSha3Result(byte[] value) {
    byte[] retBytes = new byte[32];
    CallTransaction.Function function = getRunSha3Function();
    Object[] retResults = function.decodeResult(value);
    if (retResults != null && retResults.length > 0) {
      retBytes = (byte[]) retResults[0];
    }
    return retBytes;
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
    Assert.assertEquals(ResponseCodeEnum.OK,
        callResp.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());
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

    public void demo() throws Exception {
      loadGenesisAndNodeAcccounts();

      ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
          .usePlaintext()
          .build();
      TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
          nodeAccount);
      channel.shutdown();

      KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
      AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount,
              TestHelper.getCryptoMaxFee() * 10L);
      Assert.assertNotNull(crAccount);
      Assert.assertNotEquals(0, crAccount.getAccountNum());
      log.info("Account created successfully: " + crAccount);

      // Upload contract file
      FileID newContractFileId = LargeFileUploadIT
          .uploadFile(crAccount, PRECOMPILED_OPCODES_BIN, crAccountKeyPair);
      Assert.assertNotNull(newContractFileId);
      Assert.assertNotEquals(0, newContractFileId.getFileNum());
      log.info("Contract file uploaded successfully");

      // Create contract
      ContractID newContractId = createContract(crAccount, newContractFileId);
      Assert.assertNotNull(newContractId);
      Assert.assertNotEquals(0, newContractId.getContractNum());
      log.info("Contract created successfully: " + newContractId);

      // Return addmod(8, 9, 10)
      int output = getRunAddmod(crAccount, newContractId);
      Assert.assertEquals(7, output);
      log.info("Passed test for opcode 'addmod'");

      // Return mulmod(8, 9, 10)
      output = getRunMulmod(crAccount, newContractId);
      Assert.assertEquals(2, output);
      log.info("Passed test for opcode 'mulmod'");

      byte[] hash;
      hash = getRunRipemd160(crAccount, newContractId, "abc");
      log.info("ripemd160 of string 'abc' is " + HexUtils.bytes2Hex(hash));
      Assert.assertEquals("8eb208f7e05d987a9b044a8e98c6b087f15a0bfc", HexUtils.bytes2Hex(hash));
      log.info("Passed test for ripemd160 hash of string 'abc'.");

      hash = getrunSha256(crAccount, newContractId, "abc");
      log.info("sha256 of string 'abc' is " + HexUtils.bytes2Hex(hash));
      Assert.assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
          HexUtils.bytes2Hex(hash));
      log.info("Passed test for sha256 hash of string 'abc'.");

      hash = getRunKeccak256(crAccount, newContractId, "abc");
      log.info("keccak256 of string 'abc' is " + HexUtils.bytes2Hex(hash));
      Assert.assertEquals("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45",
          HexUtils.bytes2Hex(hash));
      log.info("Passed test for keccak256 hash of string 'abc'.");

      hash = getRunSha3(crAccount, newContractId, "abc");
      log.info("sha3 of string 'abc' is " + HexUtils.bytes2Hex(hash));
      Assert.assertEquals("4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45",
          HexUtils.bytes2Hex(hash));
      log.info("Passed test for sha3 hash of string 'abc'.  Identical to keccak256.");

    }
}
