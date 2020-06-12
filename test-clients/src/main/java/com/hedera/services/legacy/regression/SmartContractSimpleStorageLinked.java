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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.ContractID;
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
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
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
import net.i2p.crypto.eddsa.KeyPairGenerator;

public class SmartContractSimpleStorageLinked {

	  private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	  private final Logger log = LogManager.getLogger(SmartContractSimpleStorage.class);


	  private static final int MAX_RECEIPT_RETRIES = 60;
	  private static final String CONSTRUCTOR_ABI = "{\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"_levels\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"}";
	  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"level\",\"type\":\"uint256\"}],\"name\":\"get\",\"outputs\":[{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint256\",\"name\":\"level\",\"type\":\"uint256\"},{\"internalType\":\"uint256\",\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
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
//	    if ((args.length) > 0) {
//	      numberOfReps = Integer.parseInt(args[0]);
//	    }
	    for (int i = 0; i < numberOfReps; i++) {
	      SmartContractSimpleStorageLinked scSs = new SmartContractSimpleStorageLinked();
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
	    while (attempts <= MAX_RECEIPT_RETRIES && !transactionReceipts.getTransactionGetReceipt()
	        .getReceipt()
	        .getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
	      Thread.sleep(1000);
	      transactionReceipts = stub.getTransactionReceipts(query);
	      System.out.println("waiting to getTransactionReceipts as Success..." +
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
	      long durationInSeconds,byte[] contructorArgs) throws Exception {
	    ContractID createdContract = null;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
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
	            transactionDuration, true, "", 1600000, contractFile, ByteString.copyFrom(contructorArgs), 0,
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


	  private byte[] encodeSet(int valueToAdd,int level) {
	    String retVal = "";
	    CallTransaction.Function function = getSetFunction();
	    byte[] encodedFunc = function.encode(level,valueToAdd);

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
	    ContractID createdContract = null;
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Timestamp timestamp = RequestBuilder
	        .getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
	    Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
	    //payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId, functionData, value, signatures
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


	  private CallTransaction.Function getGetValueFunction() {
	    String funcJson = SC_GET_ABI.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] encodeGetValue(int level) {
	    String retVal = "";
	    CallTransaction.Function function = getGetValueFunction();
	    byte[] encodedFunc = function.encode(level);
	    return encodedFunc;
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


	  private int getValueFromContract(AccountID payerAccount, ContractID contractId,int level) throws Exception {
	    int retVal = 0;
	    byte[] getValueEncodedFunction = encodeGetValue(level);
	    byte[] result = callContractLocal(payerAccount, contractId, getValueEncodedFunction);
	    if (result != null && result.length > 0) {
	      retVal = decodeGetValueResult(result);
	    }
	    return retVal;
	  }


	  private void setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet,int level)
	      throws Exception {
	    byte[] dataToSet = encodeSet(valuetoSet,level);
	    //set value to simple storage smart contract
	    byte[] retData = callContract(payerAccount, contractId, dataToSet);
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




	  /**
	   * Get Tx records by contract ID.
	   *
	   * @return list of Tx records
	   */
	  public List<TransactionRecord> getTxRecordByContractID(AccountID payerID, ContractID contractId)
	      throws Exception {
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();

	    SmartContractServiceGrpc.SmartContractServiceBlockingStub scstub = SmartContractServiceGrpc
	        .newBlockingStub(channel);

	    Transaction paymentTxSigned = createQueryHeaderTransfer(payerID, TestHelper.getCryptoMaxFee());
	    Query query = RequestBuilder
	        .getContractRecordsQuery(contractId, paymentTxSigned, ResponseType.ANSWER_ONLY);
	    Response transactionRecord = scstub.getTxRecordByContractID(query);
	    channel.shutdown();

	    Assert.assertNotNull(transactionRecord);
	    ContractGetRecordsResponse response = transactionRecord.getContractGetRecordsResponse();
	    Assert.assertNotNull(response);
	    List<TransactionRecord> records = response.getRecordsList();
	    return records;
	  }

	  public void demo() throws Exception {
	    loadGenesisAndNodeAcccounts();

	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
	        nodeAccount);
	    channel.shutdown();

	    KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
	    AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
	    log.info("Account created successfully");
	    String fileName = "simpleStorageLinked.bin";
	    if (crAccount != null) {
	      int totalLevels = 5;
	      
	      FileID simpleStorageFileId = LargeFileUploadIT
	          .uploadFile(crAccount, fileName, crAccountKeyPair);
	      if (simpleStorageFileId != null) {
	        log.info("Smart Contract file uploaded successfully");
	        byte[] encodedConstructor =  getEncodedConstructor(totalLevels);
	        ContractID sampleStorageContractId = createContract(crAccount, simpleStorageFileId,
	            contractDuration,encodedConstructor);
	        Assert.assertNotNull(sampleStorageContractId);
	        log.info("Contract created successfully");

	        int iterations = 10;
	        for (int i = 0; i < iterations; i++) {
	          int level = ThreadLocalRandom.current().nextInt(1, totalLevels + 1); 
	          int currValueToSet = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);
	          setValueToContract(crAccount, sampleStorageContractId, currValueToSet,level);
	          //Thread.sleep(10000);
	          int actualStoredValue = getValueFromContract(crAccount, sampleStorageContractId,level);
	           Assert.assertEquals(currValueToSet, actualStoredValue);
	          log.info("Contract get/set iteration " + i + " completed successfully==>");
	        }
	        // Check fetch of records by ContractID
	        List<TransactionRecord> recordsByContractID = getTxRecordByContractID(crAccount,
	            sampleStorageContractId);
	        Assert.assertEquals(iterations + 1, recordsByContractID.size());
	        Assert.assertTrue("First record should be contract create",
	            recordsByContractID.get(0).hasContractCreateResult());
	        Assert.assertTrue("Second record should be contract call",
	            recordsByContractID.get(1).hasContractCallResult());
          }

          // Marker message for regression report
          log.info("Regression summary: This run is successful.");
        }
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
	  
	  private  CallTransaction.Function getConstructorFunction() {
		    String funcJson = CONSTRUCTOR_ABI.replaceAll("'", "\"");
		    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		    return function;
		  }

		  private  byte[] getEncodedConstructor(int levels) {
		    String retStr = "";
		    CallTransaction.Function func = getConstructorFunction();
		    byte[] encodedFunc = func.encodeArguments(levels);

		    return encodedFunc;
		  }
}
