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
import com.hederahashgraph.api.proto.java.AccountAmount;
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
import com.hederahashgraph.api.proto.java.TransferList;
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
import java.util.concurrent.ThreadLocalRandom;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

/**
 * Testing transaction records transfer list for payable smartcontract
 *
 * @author Constantin
 */
public class SmartContractPayReceivableAmount {
	 private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	  private final Logger log = LogManager.getLogger(SmartContractPayReceivable.class);


	  private static final int MAX_RECEIPT_RETRIES = 60;
	  private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	  private static final String SC_GET_BALANCE = "{\"constant\":true,\"inputs\":[],\"name\":\"getBalance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static final String SC_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
	  private static final String SC_GET_BALANCE_OF = "{\"constant\":true,\"inputs\":[{\"name\":\"accToCheck\",\"type\":\"address\"}],\"name\":\"getBalanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	  private static final String SC_SEND_FUNDS = "{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
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

	    int numberOfReps = 1;
	    if ((args.length) > 0) {
	      numberOfReps = Integer.parseInt(args[0]);
	    }
	    for (int i = 0; i < numberOfReps; i++) {
	      SmartContractPayReceivableAmount scSs = new SmartContractPayReceivableAmount();
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
	      long durationInSeconds) throws Exception {
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


	  private byte[] encodeSet(int valueToAdd) {
	    String retVal = "";
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
	        log.info("Gas used : " + callResults.getGasUsed());
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

	  private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data,
	      long value) throws Exception {
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
	            transactionDuration, 25000, contractToCall, dataBstr, value,
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
	        log.info("Gas used : " + callResults.getGasUsed());
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
	  
	  
	  private TransactionRecord callContractReturnRecord(AccountID payerAccount, ContractID contractToCall, byte[] data,
		      long value) throws Exception {
		    TransactionRecord trRecord = null;
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
		            transactionDuration, 250000, contractToCall, dataBstr, value,
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
		       trRecord = getTransactionRecord(payerAccount,
		    		  callContractBody.getTransactionID());

		    }
		    channel.shutdown();

		    return trRecord;
		  }


	  private CallTransaction.Function getGetValueFunction() {
	    String funcJson = SC_GET_ABI.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] encodeGetValue() {
	    String retVal = "";
	    CallTransaction.Function function = getGetValueFunction();
	    byte[] encodedFunc = function.encode();
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
	    fee = callResp.getContractCallLocal().getHeader().getCost();
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


	  private AccountInfo getCryptoGetAccountInfo(AccountID payerAccount,
		      AccountID accountID) throws Exception {
		    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
		        .usePlaintext(true)
		        .build();
		    CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);

		    long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetInfo);

		    Response respToReturn = executeGetAcctInfoQuery(payerAccount, accountID, stub, fee,
		        ResponseType.COST_ANSWER);

		    fee = respToReturn.getCryptoGetInfo().getHeader().getCost();
		    respToReturn = executeGetAcctInfoQuery(payerAccount, accountID, stub, fee,
		        ResponseType.ANSWER_ONLY);
		    AccountInfo accInfToReturn = null;
		    accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();
		    channel.shutdown();

		    return accInfToReturn;
		  }


	  private Response executeGetAcctInfoQuery(AccountID payerAccount, AccountID accountID,
		      CryptoServiceGrpc.CryptoServiceBlockingStub stub,
		      long fee, ResponseType responseType) throws Exception {
		    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		    Query cryptoGetInfoQuery = RequestBuilder
		        .getCryptoGetInfoQuery(accountID, paymentTx, responseType);

		    Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
		    return respToReturn;
		  }

	  private GetBySolidityIDResponse getBySolidityID(AccountID payerAccount,
	      String solidityId) throws Exception {
	    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
	        .usePlaintext(true)
	        .build();
	    SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
	        .newBlockingStub(channel);
	    long fee = FeeClient.getFeegetBySolidityID();
	    Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
	    Query getBySolidityIdQuery = RequestBuilder.getBySolidityIDQuery(
	        solidityId, paymentTx, ResponseType.ANSWER_ONLY);

	    Response respToReturn = stub.getBySolidityID(getBySolidityIdQuery);
	    GetBySolidityIDResponse bySolidityReturn = null;
	    bySolidityReturn = respToReturn.getGetBySolidityID();
	    channel.shutdown();

	    return bySolidityReturn;
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
	    
	    KeyPair receiverAccountKeyPair = new KeyPairGenerator().generateKeyPair();
	    AccountID receiverAccount = createAccount(receiverAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
	    
	    log.info("Account created successfully");
	    String fileName = "PayReceivableAmount.bin";
	    if (crAccount != null) {
          AccountInfo recInfo = getCryptoGetAccountInfo(crAccount, receiverAccount);
          String recAccountSolidityAddress = recInfo.getContractAccountID();
	      FileID simpleStorageFileId = LargeFileUploadIT
	          .uploadFile(crAccount, fileName, crAccountKeyPair);
	      if (simpleStorageFileId != null) {
	        log.info("Smart Contract file uploaded successfully");
	        ContractID payTestContractId = createContract(crAccount, simpleStorageFileId,
							contractDuration);
	        Assert.assertNotNull(payTestContractId);
	        log.info("Contract created successfully");
	        for (int i = 0; i < 10; i++) {
		        int currValueToDeposit = ThreadLocalRandom.current().nextInt(100, 10000 + 1);
		        int receiverValue = currValueToDeposit/2;
		        int remainToContract = currValueToDeposit - receiverValue;
		        AccountID contractAccountId = AccountID.newBuilder().setAccountNum(payTestContractId.getContractNum())
		        		.setRealmNum(payTestContractId.getRealmNum()).setShardNum(payTestContractId.getShardNum()).build();
		        List<AccountAmount> expectedTransfers = new ArrayList<>(3);
		        AccountAmount receiverTransfer = AccountAmount.newBuilder().setAccountID(receiverAccount).setAmount(receiverValue).build();
		        expectedTransfers.add(receiverTransfer);
		        AccountAmount contractTransfer = AccountAmount.newBuilder().setAccountID(contractAccountId).setAmount(remainToContract).build();
		        expectedTransfers.add(contractTransfer);
		        
		        TransactionRecord  sfTrRecord = sendFunds(crAccount, payTestContractId, recAccountSolidityAddress, currValueToDeposit, receiverValue);
		        Assert.assertNotNull(sfTrRecord);
		        assert(sfTrRecord.getReceipt().getStatus().equals(ResponseCodeEnum.SUCCESS));
		        TransferList trList = sfTrRecord.getTransferList();
		        Assert.assertNotNull(trList);
		        Assert.assertNotNull(trList.getAccountAmountsList());
		        assert(trList.getAccountAmountsList().containsAll(expectedTransfers));
		        long amountSum =  sumAmountsInTransferList(trList.getAccountAmountsList());
		        Assert.assertEquals(0, amountSum);
	        }
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


	  private byte[] encodeDeposit(int valueToDeposit) {
	    String retVal = "";
	    CallTransaction.Function function = getDepositFunction();
	    byte[] encodedFunc = function.encode(valueToDeposit);

	    return encodedFunc;
	  }

	  private CallTransaction.Function getDepositFunction() {
	    String funcJson = SC_DEPOSIT.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }


	  private CallTransaction.Function getGetBalanceFunction() {
	    String funcJson = SC_GET_BALANCE.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] encodeGetBalance() {
	    String retVal = "";
	    CallTransaction.Function function = getGetBalanceFunction();
	    byte[] encodedFunc = function.encode();
	    return encodedFunc;
	  }

	  private int decodeGetBalanceResult(byte[] value) {
	    int decodedReturnedValue = 0;
	    CallTransaction.Function function = getGetBalanceFunction();
	    Object[] retResults = function.decodeResult(value);
	    if (retResults != null && retResults.length > 0) {
	      BigInteger retBi = (BigInteger) retResults[0];
	      decodedReturnedValue = retBi.intValue();
	    }
	    return decodedReturnedValue;
	  }


	  private void depositToContract(AccountID payerAccount, ContractID contractId, int valueToDeposit)
	      throws Exception {
	    byte[] dataToSet = encodeDeposit(valueToDeposit);
	    //set value to simple storage smart contract
	    byte[] retData = callContract(payerAccount, contractId, dataToSet, valueToDeposit);
	  }

	  private void transferToContract(AccountID payerAccount, ContractID contractId, int valueToDeposit)
	      throws Exception {
	    byte[] dataToSet = {};
	    //set value to simple storage smart contract
	    byte[] retData = callContract(payerAccount, contractId, dataToSet, valueToDeposit);
	  }

	  private int getBalanceFromContract(AccountID payerAccount, ContractID contractId)
	      throws Exception {
	    int retVal = 0;
	    byte[] getBalanceEncodedFunction = encodeGetBalance();
	    byte[] result = callContractLocal(payerAccount, contractId, getBalanceEncodedFunction);
	    if (result != null && result.length > 0) {
	      retVal = decodeGetBalanceResult(result);
	    }
	    return retVal;
	  }

	  private CallTransaction.Function getGetBalanceOfFunction() {
	    String funcJson = SC_GET_BALANCE_OF.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] encodeGetBalanceOf(String address) {
	    String retVal = "";
	    CallTransaction.Function function = getGetBalanceOfFunction();
	    byte[] encodedFunc = function.encode(address);
	    return encodedFunc;
	  }

	  private CallTransaction.Function getSendFundsFunction() {
	    String funcJson = SC_SEND_FUNDS.replaceAll("'", "\"");
	    CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
	    return function;
	  }

	  private byte[] encodeSendFunds(String address, int amount) {
	    String retVal = "";
	    CallTransaction.Function function = getSendFundsFunction();
	    byte[] encodedFunc = function.encode(address, amount);
	    return encodedFunc;
	  }

	  private TransactionRecord sendFunds(AccountID payerAccount, ContractID contractId, String receiverAccount,
	      int valueToSend, int receiverAmount) throws Exception {
	    byte[] dataToSet = encodeSendFunds(receiverAccount, receiverAmount);
	    //set value to simple storage smart contract
	    return callContractReturnRecord(payerAccount, contractId, dataToSet,valueToSend);
	  }

	  private int decodeGetBalanceOfResult(byte[] value) {
	    int decodedReturnedValue = 0;
	    CallTransaction.Function function = getGetBalanceFunction();
	    Object[] retResults = function.decodeResult(value);
	    if (retResults != null && retResults.length > 0) {
	      BigInteger retBi = (BigInteger) retResults[0];
	      decodedReturnedValue = retBi.intValue();
	    }
	    return decodedReturnedValue;
	  }

	  private int getBalanceOf(AccountID payerAccount, ContractID contractId, String accountAddressEth)
	      throws Exception {
	    int retVal = 0;
	    byte[] getBalanceEncodedFunction = encodeGetBalanceOf(accountAddressEth);
	    byte[] result = callContractLocal(payerAccount, contractId, getBalanceEncodedFunction);
	    if (result != null && result.length > 0) {
	      retVal = decodeGetBalanceOfResult(result);
	    }
	    return retVal;
	  }
	  
	  public long sumAmountsInTransferList(List<AccountAmount> transferList) {
		  long sumToReturn = 0L;
		  for(AccountAmount currAccAmount :transferList) {
			  sumToReturn +=  currAccAmount.getAmount();
		  }
		  return sumToReturn;
	  }
}
