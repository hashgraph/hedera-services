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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

/**
 * Validate the output of opcodes that we have made constant, since they are not relevant to
 * Hashgraph
 *
 * @author Peter
 */
public class StaticOpcodes {
	private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private final Logger log = LogManager.getLogger(StaticOpcodes.class);


	private static final int MAX_RECEIPT_RETRIES = 60;
	public static final String STATIC_OPCODES_BIN = "/testfiles/StaticOpcodes.bin";

	private static final String SCZ_ADD_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"runAdd\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"pure\"," +
			"\"type\":\"function\"}";
	private static final String SCZ_BLOCKHASH_ABI = "{\"constant\":true,\"inputs\":[{\"name\":\"_block\"," +
			"\"type\":\"uint256\"}],\"name\":\"runBlockhash\",\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}]," +
			"\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SCZ_COINBASE_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"runCoinbase\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SCZ_NUMBER_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"runNumber\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SCZ_DIFFICULTY_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"runDifficulty\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SCZ_GASLIMIT_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"runGaslimit\"," +
			"\"outputs\":[{\"name\":\"_resp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";

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
			StaticOpcodes scSs = new StaticOpcodes();
			scSs.demo();
		}

	}

	private void loadGenesisAndNodeAcccounts() throws Exception {
		Map<String, List<AccountKeyListObj>> hederaAccounts = null;
		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

		// Get Genesis Account key Pair
		List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");
		;

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
				.usePlaintext(true)
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
	Methods to run the runAdd method
	 */
	private int getRunAdd(AccountID payerAccount, ContractID contractId) throws Exception {
		int retVal = -1; // Value if nothing was returned
		byte[] dataToGet = encodeRunAdd();
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeRunAddResult(result);
		}
		return retVal;
	}

	public static byte[] encodeRunAdd() {
		CallTransaction.Function function = getRunAddFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getRunAddFunction() {
		String funcJson = SCZ_ADD_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeRunAddResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getRunAddFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Methods to run the runBlockhash method
	 */
	private int getRunBlockhash(AccountID payerAccount, ContractID contractId, int block)
			throws Exception {
		int retVal = -1; // Value if nothing was returned
		byte[] dataToGet = encodeRunBlockhash(block);
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeRunBlockhashResult(result);
		}
		return retVal;
	}

	public static byte[] encodeRunBlockhash(int block) {
		CallTransaction.Function function = getRunBlockhashFunction();
		byte[] encodedFunc = function.encode(block);
		return encodedFunc;
	}

	public static CallTransaction.Function getRunBlockhashFunction() {
		String funcJson = SCZ_BLOCKHASH_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeRunBlockhashResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getRunBlockhashFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Methods to run the runCoinbase method
	 */
	private int getRunCoinbase(AccountID payerAccount, ContractID contractId) throws Exception {
		int retVal = -1;
		byte[] dataToGet = encodeRunCoinbase();
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeRunCoinbaseResult(result);
		}
		return retVal;
	}

	public static byte[] encodeRunCoinbase() {
		CallTransaction.Function function = getRunCoinbaseFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getRunCoinbaseFunction() {
		String funcJson = SCZ_COINBASE_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeRunCoinbaseResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getRunCoinbaseFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Methods to run the runNumber method
	 */
	private int getRunNumber(AccountID payerAccount, ContractID contractId) throws Exception {
		int retVal = -1; // Value if nothing was returned
		byte[] dataToGet = encodeRunNumber();
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeRunNumberResult(result);
		}
		return retVal;
	}

	public static byte[] encodeRunNumber() {
		CallTransaction.Function function = getRunNumberFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getRunNumberFunction() {
		String funcJson = SCZ_NUMBER_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeRunNumberResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getRunNumberFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}

	/*
	Methods to run the runDifficulty method
	 */
	private int getRunDifficulty(AccountID payerAccount, ContractID contractId) throws Exception {
		int retVal = -1; // Value if nothing was returned
		byte[] dataToGet = encodeRunDifficulty();
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeRunDifficultyResult(result);
		}
		return retVal;
	}

	public static byte[] encodeRunDifficulty() {
		CallTransaction.Function function = getRunDifficultyFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getRunDifficultyFunction() {
		String funcJson = SCZ_DIFFICULTY_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeRunDifficultyResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getRunDifficultyFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}


	/*
	Methods to run the runGaslimit method
   */
	private int getRunGaslimit(AccountID payerAccount, ContractID contractId) throws Exception {
		int retVal = -123; // Value if nothing was returned. -1 is expected, so don't us it here.
		byte[] dataToGet = encodeRunGaslimit();
		byte[] result = callContractLocal(payerAccount, contractId, dataToGet);
		if (result != null && result.length > 0) {
			retVal = decodeRunGaslimitResult(result);
		}
		return retVal;
	}

	public static byte[] encodeRunGaslimit() {
		CallTransaction.Function function = getRunGaslimitFunction();
		byte[] encodedFunc = function.encode();
		return encodedFunc;
	}

	public static CallTransaction.Function getRunGaslimitFunction() {
		String funcJson = SCZ_GASLIMIT_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	public static int decodeRunGaslimitResult(byte[] value) {
		int decodedReturnedValue = 0;
		CallTransaction.Function function = getRunGaslimitFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			BigInteger retBi = (BigInteger) retResults[0];
			decodedReturnedValue = retBi.intValue();
		}
		return decodedReturnedValue;
	}


	private TransactionRecord callContract(AccountID payerAccount, ContractID contractToCall,
			byte[] data, ResponseCodeEnum expectedStatus)
			throws Exception {
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
						transactionDuration, 250000, contractToCall, dataBstr, 0,
						accountKeyPairs.get(payerAccount));

		TransactionResponse response = stub.contractCallMethod(callContractRequest);
		System.out.println(
				" callContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
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

	private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data)
			throws Exception {
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

	public void demo() throws Exception {
		loadGenesisAndNodeAcccounts();

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
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
		FileID zeroContractFileId = LargeFileUploadIT
				.uploadFile(crAccount, STATIC_OPCODES_BIN, crAccountKeyPair);
		Assert.assertNotNull(zeroContractFileId);
		Assert.assertNotEquals(0, zeroContractFileId.getFileNum());
		log.info("Contract file uploaded successfully");

		// Create contract
		ContractID zeroContractId = createContract(crAccount, zeroContractFileId);
		Assert.assertNotNull(zeroContractId);
		Assert.assertNotEquals(0, zeroContractId.getContractNum());
		log.info("Contract created successfully: " + zeroContractId);

		// First test an opcode that returns a non-zero value. Return add(2, 4)
		int output = getRunAdd(crAccount, zeroContractId);
		Assert.assertEquals(6, output);
		log.info("Passed test for good opcode 'add'");

		// Blockhash opcode should return zero
		output = getRunBlockhash(crAccount, zeroContractId, 12 /* arbitrary block number */);
		Assert.assertEquals(0, output);
		log.info("Passed test for zero opcode 'blockhash'");

		// Coinbase opcode should return zero
		output = getRunCoinbase(crAccount, zeroContractId);
		Assert.assertEquals(0, output);
		log.info("Passed test for zero opcode 'coinbase'");

		// Number opcode should return zero
		output = getRunNumber(crAccount, zeroContractId);
		Assert.assertEquals(0, output);
		log.info("Passed test for zero opcode 'number'");

		// Difficulty opcode should return zero
		output = getRunDifficulty(crAccount, zeroContractId);
		Assert.assertEquals(0, output);
		log.info("Passed test for zero opcode 'difficulty'");

		// Gaslimit opcode should return negative one
		output = getRunGaslimit(crAccount, zeroContractId);
		Assert.assertEquals(-1, output);
		log.info("Passed test for negative one opcode 'gaslimit'");
	}


}
