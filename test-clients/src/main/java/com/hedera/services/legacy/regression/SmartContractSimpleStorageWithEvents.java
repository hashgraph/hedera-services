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
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
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
import java.util.concurrent.ThreadLocalRandom;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.ethereum.solidity.Abi;
import org.ethereum.solidity.Abi.Event;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

/**
 * Test that smart contract call can emit events
 *
 * @author Constantin
 */
public class SmartContractSimpleStorageWithEvents {

	private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private final Logger log = LogManager.getLogger(SmartContractSimpleStorageWithEvents.class);


	private static final int MAX_RECEIPT_RETRIES = 60;
	private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}]," +
			"\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	private static final String SC_ABI = "[{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}]," +
			"\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\"," +
			"\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}," +
			"{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"_from\",\"type\":\"address\"}," +
			"{\"indexed\":false,\"name\":\"_value\",\"type\":\"uint256\"}],\"name\":\"Stored\",\"type\":\"event\"}]";
	private static AccountID nodeAccount;
	private static long node_account_number;
	private static long node_shard_number;
	private static long node_realm_number;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private AccountID genesisAccount;
	private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
	private static Event requestEvent = null;
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
		for (int i = 0; i < numberOfReps; i++) {
			SmartContractSimpleStorageWithEvents scSs = new SmartContractSimpleStorageWithEvents();
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
						transactionDuration, true, "", 1500000, contractFile, ByteString.EMPTY, 0,
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
		//payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee,
		// timestamp, txDuration, gas, contractId, functionData, value, signatures
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
		fee = callResp.getContractCallLocal().getHeader().getCost() + localCallGas;
		callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee,
				ResponseType.ANSWER_ONLY);

		System.out.println("callContractLocal response = " + callResp);

		ByteString functionResults = callResp.getContractCallLocal().getFunctionResult()
				.getContractCallResult();

		channel.shutdown();
		return functionResults.toByteArray();
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


	private TransactionRecord setValueToContract(AccountID payerAccount, ContractID contractId,
			int valuetoSet) throws Exception {
		byte[] dataToSet = encodeSet(valuetoSet);
		//set value to simple storage smart contract
		return callContractAndGetRecord(payerAccount, contractId, dataToSet);
	}

	private String getContractByteCode(AccountID payerAccount,
			ContractID contractId) throws Exception {
		String byteCode = "";
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, TestHelper.getCryptoMaxFee());

		Query getContractBytecodeQuery = RequestBuilder
				.getContractGetBytecodeQuery(contractId, paymentTx, ResponseType.ANSWER_ONLY);

		Response respToReturn = stub.contractGetBytecode(getContractBytecodeQuery);
		ByteString contractByteCode = null;
		contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
		if (contractByteCode != null && !contractByteCode.isEmpty()) {
			byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
		}
		channel.shutdown();

		return byteCode;
	}

	private AccountInfo getCryptoGetAccountInfo(
			AccountID accountID) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		long nodeFee = FeeClient.getCostForGettingAccountInfo();
		Transaction paymentTx = createQueryHeaderTransfer(accountID, nodeFee);
		Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(
				accountID, paymentTx, ResponseType.ANSWER_ONLY);

		Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
		AccountInfo accInfToReturn = null;
		accInfToReturn = respToReturn.getCryptoGetInfo().getAccountInfo();
		channel.shutdown();

		return accInfToReturn;
	}

	private GetBySolidityIDResponse getBySolidityID(AccountID payerAccount,
			String solidityId) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, TestHelper.getCryptoMaxFee());
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
		log.info("Account created successfully");
		String fileName = "SimpleStorageWithEvents.bin";
		if (crAccount != null) {

			FileID simpleStorageFileId = LargeFileUploadIT
					.uploadFile(crAccount, fileName, crAccountKeyPair);
			if (simpleStorageFileId != null) {
				log.info("Smart Contract file uploaded successfully");
				ContractID sampleStorageContractId = createContract(crAccount, simpleStorageFileId,
						contractDuration);
				Assert.assertNotNull(sampleStorageContractId);
				log.info("Contract created successfully");

				for (int i = 0; i < 10; i++) {
					int currValueToSet = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);
					TransactionRecord setRecord = setValueToContract(crAccount, sampleStorageContractId,
							currValueToSet);

					validateSetRecord(sampleStorageContractId, currValueToSet, setRecord);
					int actualStoredValue = getValueFromContract(crAccount, sampleStorageContractId);
					Assert.assertEquals(currValueToSet, actualStoredValue);
					log.info("Contract get/set iteration " + i + " completed successfully==>");
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
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, FeeClient.getCostForGettingAccountInfo());

		Query getContractInfoQuery = RequestBuilder
				.getContractGetInfoQuery(contractId, paymentTx, ResponseType.ANSWER_ONLY);

		Response respToReturn = stub.getContractInfo(getContractInfoQuery);
		ContractInfo contractInfToReturn = null;
		contractInfToReturn = respToReturn.getContractGetInfo().getContractInfo();
		channel.shutdown();

		return contractInfToReturn;
	}

	private TransactionRecord callContractAndGetRecord(AccountID payerAccount,
			ContractID contractToCall, byte[] data) throws Exception {
		byte[] dataToReturn = null;
		ContractID createdContract = null;
		TransactionRecord trRecord = TransactionRecord.newBuilder().build();
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(-1 * TestHelper.DEFAULT_WIND_SEC));
		Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
		//payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee,
		// timestamp, txDuration, gas, contractId, functionData, value, signatures
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

	private boolean validateSetRecord(ContractID contractCalled, int valuePassed,
			TransactionRecord setRecord) {
		boolean retValue = false;
		if (setRecord.hasContractCallResult()) {
			ContractFunctionResult setResults = setRecord.getContractCallResult();
			List<ContractLoginfo> logs = setResults.getLogInfoList();
			for (ContractLoginfo currLog : logs) {
				ContractID logContractId = currLog.getContractID();
				assert (logContractId.equals(contractCalled));
				ByteString logdata = currLog.getData();
				byte[] dataArr = { };
				if (logdata != null) {
					dataArr = logdata.toByteArray();
				}
				List<ByteString> topicsBstr = currLog.getTopicList();
				int topicSize = 0;
				if (topicsBstr != null) {
					topicSize = topicsBstr.size();
				}
				byte[][] topicsArr = new byte[topicSize][];
				for (int topicIndex = 0; topicIndex < topicsBstr.size(); topicIndex++) {
					topicsArr[topicIndex] = topicsBstr.get(topicIndex).toByteArray();
				}

				Event storedEvnt = getStoredEvent();
				List<?> eventData = storedEvnt.decode(dataArr, topicsArr);
				BigInteger valueFromEvent = (BigInteger) eventData.get(1);
				byte[] senderAddress = (byte[]) eventData.get(0);
				String senderAddrInStrFormat = ByteUtil.toHexString(senderAddress);
				assert (valueFromEvent.intValue() == valuePassed);
				retValue = true;
			}
		}
		return retValue;
	}


	private static Event getStoredEvent() {
		if (requestEvent == null) {
			Abi abi = Abi.fromJson(SC_ABI);
			Predicate<Event> searchEventPredicate = sep -> {
				return sep.name.equals("Stored");
			};
			requestEvent = abi.findEvent(searchEventPredicate);

		}
		return requestEvent;
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
}
