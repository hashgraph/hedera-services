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

import com.hedera.services.legacy.core.KeyPairObj;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.KeyPairGenerator;

/**
 * Test a different ways to pass contract as a reference to another contract
 *
 * @author Constantin
 */
public class ContractToContract {

	private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private final Logger log = LogManager.getLogger(ContractToContract.class);

	private static final int MAX_RECEIPT_RETRIES = 60;
	private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}],\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_SET_ADDRESS_CASE_1 = "{\"constant\":false,\"inputs\":[{\"name\":\"_myStorage\",\"type\":\"address\"}],\"name\":\"setAddressCase1\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_SET_STORAGE_CASE_2 = "{\"constant\":false,\"inputs\":[{\"name\":\"_myStorage\",\"type\":\"address\"}],\"name\":\"setStorageCase2\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"};";
	private static final String SC_SET_STORAGE_CASE_3 = "{\"constant\":false,\"inputs\":[{\"name\":\"_myStorage\",\"type\":\"address\"}],\"name\":\"setStorageCase3\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String SC_CT_GETADDRESS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getAddress\",\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";

	private static AccountID nodeAccount;
	private static long node_account_number;
	private static long node_shard_number;
	private static long node_realm_number;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private AccountID genesisAccount;
	private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
	private KeyPair genesisKeyPair;
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
		nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number).setRealmNum(node_shard_number)
				.setShardNum(node_realm_number).build();

		int numberOfReps = 1;
		for (int i = 0; i < numberOfReps; i++) {
			ContractToContract scSs = new ContractToContract();
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
		genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);

		// get the Account Object
		genesisAccount = genesisAccountList.get(0).getAccountId();
		List<PrivateKey> genesisKeyList = new ArrayList<PrivateKey>(1);
		genesisKeyList.add(genesisPrivateKey);
		accountKeys.put(genesisAccount, genesisKeyList);

	}

	private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt) throws Exception {
		return TestHelper.createTransfer(payer, accountKeys.get(payer).get(0), nodeAccount, payer,
				accountKeys.get(payer).get(0), nodeAccount, transferAmt);
	}

	private AccountID createAccount(AccountID payerAccount, long initialBalance) throws Exception {

		KeyPair keyGenerated = new KeyPairGenerator().generateKeyPair();
		return createAccount(keyGenerated, payerAccount, initialBalance);
	}

	private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Transaction transaction = TestHelper.createAccountWithFee(payerAccount, nodeAccount, keyPair, initialBalance,
				accountKeys.get(payerAccount));
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		System.out.println(
				"Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode().name());

		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId = TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		accountKeys.put(newlyCreateAccountId, Collections.singletonList(keyPair.getPrivate()));
		channel.shutdown();
		return newlyCreateAccountId;
	}

	private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
		TransactionGetReceiptResponse receiptToReturn = null;
		Query query = Query.newBuilder().setTransactionGetReceipt(
				RequestBuilder.getTransactionGetReceiptQuery(transactionId, ResponseType.ANSWER_ONLY)).build();
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Response transactionReceipts = stub.getTransactionReceipts(query);
		int attempts = 1;
		while (attempts <= MAX_RECEIPT_RETRIES && !transactionReceipts.getTransactionGetReceipt().getReceipt()
				.getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
			Thread.sleep(1000);
			transactionReceipts = stub.getTransactionReceipts(query);
			System.out.println("waiting to getTransactionReceipts as Success..."
					+ transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
			attempts++;
		}
		if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().equals(ResponseCodeEnum.SUCCESS)) {
			receiptToReturn = transactionReceipts.getTransactionGetReceipt();
		}
		channel.shutdown();
		return transactionReceipts.getTransactionGetReceipt();

	}

	private ContractID createContract(AccountID payerAccount, FileID contractFile, long durationInSeconds)
			throws Exception {
		ContractID createdContract = null;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

		Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction createContractRequest = TestHelper.getCreateContractRequest(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
				nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 100l, timestamp, transactionDuration, true, "",
				650000, contractFile, ByteString.EMPTY, 0, contractAutoRenew, accountKeys.get(payerAccount), "");

		TransactionResponse response = stub.createContract(createContractRequest);
		System.out.println(" createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode().name());

		TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCreateReceipt = getReceipt(createContractBody.getTransactionID());
		if (contractCreateReceipt != null) {
			createdContract = contractCreateReceipt.getReceipt().getContractID();
		}
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

	private byte[] callContract(AccountID payerAccount, ContractID contractToCall, byte[] data) throws Exception {
		byte[] dataToReturn = null;
		ContractID createdContract = null;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		// payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum,
		// nodeShardNum, transactionFee, timestamp, txDuration, gas, contractId,
		// functionData, value, signatures
		ByteString dataBstr = ByteString.EMPTY;
		if (data != null) {
			dataBstr = ByteString.copyFrom(data);
		}
		Transaction callContractRequest = TestHelper.getContractCallRequest(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), node_account_number, 0l, 0l, 100l, timestamp,
				transactionDuration, 250000, contractToCall, dataBstr, 0, accountKeys.get(payerAccount));

		TransactionResponse response = stub.contractCallMethod(callContractRequest);
		System.out.println(" createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode().name());
		Thread.sleep(1000);
		TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCallReceipt = getReceipt(callContractBody.getTransactionID());
		if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
				.equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
			TransactionRecord trRecord = getTransactionRecord(payerAccount, callContractBody.getTransactionID());
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

	private TransactionRecord getTransactionRecord(AccountID payerAccount, TransactionID transactionId)
			throws Exception {
		AccountID createdAccount = null;
		int port = 50211;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		long fee = FeeClient.getCostForGettingTxRecord();
		Response recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee, ResponseType.COST_ANSWER);
		fee = recordResp.getTransactionGetRecord().getHeader().getCost();
		recordResp = executeQueryForTxRecord(payerAccount, transactionId, stub, fee, ResponseType.ANSWER_ONLY);
		TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
		System.out.println("tx record = " + txRecord);
		channel.shutdown();
		return txRecord;
	}

	private Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
			CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType) throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		Query getRecordQuery = RequestBuilder.getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
		Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
		return recordResp;
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

	private byte[] callContractLocal(AccountID payerAccount, ContractID contractToCall, byte[] data) throws Exception {
		byte[] dataToReturn = null;
		AccountID createdAccount = null;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
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
		callResp = executeContractCall(payerAccount, contractToCall, stub, callData, fee, ResponseType.ANSWER_ONLY);
		System.out.println("callContractLocal response = " + callResp);
		ByteString functionResults = callResp.getContractCallLocal().getFunctionResult().getContractCallResult();

		channel.shutdown();
		return functionResults.toByteArray();
	}

	private Response executeContractCall(AccountID payerAccount, ContractID contractToCall,
			SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, ByteString callData, long fee,
			ResponseType resposeType) throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		Query contractCallLocal = RequestBuilder.getContractCallLocalQuery(contractToCall, 250000L, callData, 0L, 5000,
				paymentTx, resposeType);

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

	private void setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet) throws Exception {
		byte[] dataToSet = encodeSet(valuetoSet);
		// set value to simple storage smart contract
		byte[] retData = callContract(payerAccount, contractId, dataToSet);
	}

	public void updateContract(AccountID payerAccount, ContractID contractToUpdate, Duration autoRenewPeriod,
			Timestamp expirationTime) throws Exception {

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction updateContractRequest = RequestBuilder.getContractUpdateRequest(payerAccount, nodeAccount, 100L,
				timestamp, transactionDuration, true, "", contractToUpdate, autoRenewPeriod,
				null, null, expirationTime, "");

		updateContractRequest = TransactionSigner.signTransaction(updateContractRequest, accountKeys.get(payerAccount));
		TransactionResponse response = stub.updateContract(updateContractRequest);
		System.out
				.println(" update contract Pre Check Response :: " + response.getNodeTransactionPrecheckCode().name());
		Thread.sleep(1000);
		TransactionBody updateContractBody = TransactionBody.parseFrom(updateContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(updateContractBody.getTransactionID());
		Assert.assertNotNull(contractUpdateReceipt);
		channel.shutdown();

	}

	private String getContractByteCode(AccountID payerAccount, ContractID contractId) throws Exception {
		String byteCode = "";
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetBytecode);
		Response respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee,
				ResponseType.COST_ANSWER);

		fee = respToReturn.getContractGetBytecodeResponse().getHeader().getCost();
		respToReturn = executeContractByteCodeQuery(payerAccount, contractId, stub, fee, ResponseType.ANSWER_ONLY);
		ByteString contractByteCode = null;
		contractByteCode = respToReturn.getContractGetBytecodeResponse().getBytecode();
		if (contractByteCode != null && !contractByteCode.isEmpty()) {
			byteCode = ByteUtil.toHexString(contractByteCode.toByteArray());
		}
		channel.shutdown();

		return byteCode;
	}

	private Response executeContractByteCodeQuery(AccountID payerAccount, ContractID contractId,
			SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee, ResponseType responseType)
			throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		Query getContractBytecodeQuery = RequestBuilder.getContractGetBytecodeQuery(contractId, paymentTx,
				responseType);
		Response respToReturn = stub.contractGetBytecode(getContractBytecodeQuery);
		return respToReturn;
	}

	private AccountInfo getCryptoGetAccountInfo(AccountID accountID) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
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

	private Response executeGetAcctInfoQuery(AccountID accountID, CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			long fee, ResponseType responseType) throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(accountID, fee);
		Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(accountID, paymentTx, responseType);

		Response respToReturn = stub.getAccountInfo(cryptoGetInfoQuery);
		return respToReturn;
	}

	private GetBySolidityIDResponse getBySolidityID(AccountID payerAccount, String solidityId) throws Exception {
		int port = 50211;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);
		long fee = FeeClient.getFeegetBySolidityID();
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		Query getBySolidityIdQuery = RequestBuilder.getBySolidityIDQuery(solidityId, paymentTx,
				ResponseType.ANSWER_ONLY);

		Response respToReturn = stub.getBySolidityID(getBySolidityIdQuery);
		GetBySolidityIDResponse bySolidityReturn = null;
		bySolidityReturn = respToReturn.getGetBySolidityID();
		channel.shutdown();

		return bySolidityReturn;
	}

	public void demo() throws Exception {
		loadGenesisAndNodeAcccounts();

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext()
				.build();
		TestHelper.initializeFeeClient(channel, genesisAccount, genesisKeyPair, nodeAccount);
		channel.shutdown();

		KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
		AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, 100000000000000L);
		log.info("Account created successfully");
		String fileName = "InsideStorage.bin";
		if (crAccount != null) {

			FileID insideStorageFileId = LargeFileUploadIT.uploadFile(crAccount, fileName, crAccountKeyPair);
			assert (insideStorageFileId != null && insideStorageFileId.getFileNum() > 0);
			fileName = "StorageUser.bin";
			FileID storageUserFileId = LargeFileUploadIT.uploadFile(crAccount, fileName, crAccountKeyPair);
			assert (storageUserFileId != null && storageUserFileId.getFileNum() > 0);

			testSetAddressCase1(crAccount, insideStorageFileId, storageUserFileId);
			testSetStorageCase2(crAccount, insideStorageFileId, storageUserFileId);
			testSetStorageCase3(crAccount, insideStorageFileId, storageUserFileId);
		}

	}

	private void testSetAddressCase1(AccountID payerAccount, FileID insideStorageFileId, FileID storageUserFileId)
			throws Exception {
		ContractID sampleStorageContractId = createContract(payerAccount, insideStorageFileId, contractDuration);
		assert (sampleStorageContractId != null && sampleStorageContractId.getContractNum() > 0);
		log.info("Contract created successfully");
		ContractID storageUserContractId = createContract(payerAccount, storageUserFileId, contractDuration);
		assert (storageUserContractId != null && storageUserContractId.getContractNum() > 0);
		log.info("Contract created successfully");

		ContractInfo sampleStorageContractInfo = getContractInfo(payerAccount, sampleStorageContractId);
		String sampleStorageSolidityID = sampleStorageContractInfo.getContractAccountID();
		assert (StringUtils.isNotEmpty(sampleStorageSolidityID));
		setAddressCase1(payerAccount, storageUserContractId, sampleStorageSolidityID);
		ContractID returnedContract = getAddressFromContract(payerAccount,storageUserContractId);
		assert(sampleStorageContractId.equals(returnedContract));
		testGetSet(payerAccount, storageUserContractId, 7);

	}

	
	private void testSetStorageCase2(AccountID payerAccount, FileID insideStorageFileId, FileID storageUserFileId)
			throws Exception {
		ContractID sampleStorageContractId = createContract(payerAccount, insideStorageFileId, contractDuration);
		assert (sampleStorageContractId != null && sampleStorageContractId.getContractNum() > 0);
		log.info("Contract created successfully");
		ContractID storageUserContractId = createContract(payerAccount, storageUserFileId, contractDuration);
		assert (storageUserContractId != null && storageUserContractId.getContractNum() > 0);
		log.info("Contract created successfully");

		ContractInfo sampleStorageContractInfo = getContractInfo(payerAccount, sampleStorageContractId);
		String sampleStorageSolidityID = sampleStorageContractInfo.getContractAccountID();
		assert (StringUtils.isNotEmpty(sampleStorageSolidityID));
		setStorageCase2(payerAccount, storageUserContractId, sampleStorageSolidityID);
		ContractID returnedContract = getAddressFromContract(payerAccount,storageUserContractId);
		assert(sampleStorageContractId.equals(returnedContract));
		testGetSet(payerAccount, storageUserContractId, 5);
	}
	
	private void testSetStorageCase3(AccountID payerAccount, FileID insideStorageFileId, FileID storageUserFileId)
			throws Exception {
		ContractID sampleStorageContractId = createContract(payerAccount, insideStorageFileId, contractDuration);
		assert (sampleStorageContractId != null && sampleStorageContractId.getContractNum() > 0);
		log.info("Contract created successfully");
		ContractID storageUserContractId = createContract(payerAccount, storageUserFileId, contractDuration);
		assert (storageUserContractId != null && storageUserContractId.getContractNum() > 0);
		log.info("Contract created successfully");

		ContractInfo sampleStorageContractInfo = getContractInfo(payerAccount, sampleStorageContractId);
		String sampleStorageSolidityID = sampleStorageContractInfo.getContractAccountID();
		assert (StringUtils.isNotEmpty(sampleStorageSolidityID));
		setStorageCase3(payerAccount, storageUserContractId, sampleStorageSolidityID);
		ContractID returnedContract = getAddressFromContract(payerAccount,storageUserContractId);
		assert(sampleStorageContractId.equals(returnedContract));
		testGetSet(payerAccount, storageUserContractId, 5);
	}
	
	private void testGetSet(AccountID payerAccount, ContractID contractToTest, int numberOfTests) throws Exception {
		for (int i = 0; i < numberOfTests; i++) {
			int currValueToSet = ThreadLocalRandom.current().nextInt(1, 1000000 + 1);
			setValueToContract(payerAccount, contractToTest, currValueToSet);
			int actualStoredValue = getValueFromContract(payerAccount, contractToTest);
			Assert.assertEquals(currValueToSet, actualStoredValue);
			log.info("Contract get/set iteration " + i + " completed successfully==>");
		}
	}

	private ContractInfo getContractInfo(AccountID payerAccount, ContractID contractId) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);
		long fee = FeeClient.getFeeByID(HederaFunctionality.ContractGetInfo);

		Response respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee, ResponseType.COST_ANSWER);

		fee = respToReturn.getContractGetInfo().getHeader().getCost();
		respToReturn = executeGetContractInfo(payerAccount, contractId, stub, fee, ResponseType.ANSWER_ONLY);
		ContractInfo contractInfToReturn = null;
		contractInfToReturn = respToReturn.getContractGetInfo().getContractInfo();
		channel.shutdown();

		return contractInfToReturn;
	}

	private Response executeGetContractInfo(AccountID payerAccount, ContractID contractId,
			SmartContractServiceGrpc.SmartContractServiceBlockingStub stub, long fee, ResponseType responseType)
			throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);

		Query getContractInfoQuery = RequestBuilder.getContractGetInfoQuery(contractId, paymentTx, responseType);

		Response respToReturn = stub.getContractInfo(getContractInfoQuery);
		return respToReturn;
	}

	private CallTransaction.Function getSetAdderssCase1Function() {
		String funcJson = SC_SET_ADDRESS_CASE_1.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	private byte[] encodeSetAdderssCase1(String address) {
		String retVal = "";
		CallTransaction.Function function = getSetAdderssCase1Function();
		byte[] encodedFunc = function.encode(address);
		return encodedFunc;
	}

	private void setAddressCase1(AccountID payerAccount, ContractID contractId, String addressToSet) throws Exception {
		byte[] dataToSet = encodeSetAdderssCase1(addressToSet);
		// set value to simple storage smart contract
		byte[] retData = callContract(payerAccount, contractId, dataToSet);
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

	public static ContractID decodeCreateTrivialGetAddress(byte[] value) {
		byte[] retVal = new byte[0];
		CallTransaction.Function function = getCreateTrivialGetAddressFunction();
		Object[] retResults = function.decodeResult(value);
		if (retResults != null && retResults.length > 0) {
			retVal = (byte[]) retResults[0];

			long realm = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 4, 12));
			long accountNum = ByteUtil.byteArrayToLong(Arrays.copyOfRange(retVal, 12, 20));
			ContractID contractID = ContractID.newBuilder().setContractNum(accountNum).setRealmNum(realm).setShardNum(0)
					.build();
			return contractID;
		}
		return null;
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
	
	private CallTransaction.Function getSetStorageCase2Function() {
		String funcJson = SC_SET_STORAGE_CASE_2.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	private byte[] encodeSetStorageCase2(String address) {
		String retVal = "";
		CallTransaction.Function function = getSetStorageCase2Function();
		byte[] encodedFunc = function.encode(address);
		return encodedFunc;
	}

	private void setStorageCase2(AccountID payerAccount, ContractID contractId, String addressToSet) throws Exception {
		byte[] dataToSet = encodeSetStorageCase2(addressToSet);
		// set value to simple storage smart contract
		byte[] retData = callContract(payerAccount, contractId, dataToSet);
	}
	
	private CallTransaction.Function getSetStorageCase3Function() {
		String funcJson = SC_SET_STORAGE_CASE_3.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	private byte[] encodeSetStorageCase3(String address) {
		String retVal = "";
		CallTransaction.Function function = getSetStorageCase3Function();
		byte[] encodedFunc = function.encode(address);
		return encodedFunc;
	}

	private void setStorageCase3(AccountID payerAccount, ContractID contractId, String addressToSet) throws Exception {
		byte[] dataToSet = encodeSetStorageCase3(addressToSet);
		// set value to simple storage smart contract
		byte[] retData = callContract(payerAccount, contractId, dataToSet);
	}
}
