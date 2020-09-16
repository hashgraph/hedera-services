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
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
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
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

/**
 * Test that contract may not transfer to a deleted account
 *
 * @author Peter
 */
public class DeletedReceiver {

	private static final int VALUE_TO_DEPOSIT = 10_000;
	private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private final Logger log = LogManager.getLogger(DeletedReceiver.class);

	private static final int MAX_RECEIPT_RETRIES = 60;
	private static final String SC_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\"," +
			"\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";
	private static final String SC_SEND_FUNDS = "{\"constant\":false,\"inputs\":[{\"name\":\"receiver\"," +
			"\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\",\"outputs\":[]," +
			"\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final long MAX_TX_FEE = TestHelper.getContractMaxFee();
	private static AccountID nodeAccount;
	private static long node_account_number;
	private static long node_shard_number;
	private static long node_realm_number;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private AccountID genesisAccount;
	private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
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

		DeletedReceiver scSs = new DeletedReceiver();
		scSs.demo();

	}


	private void loadGenesisAndNodeAcccounts() throws Exception {
		Map<String, List<AccountKeyListObj>> hederaAccounts = null;
		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

		// Get Genesis Account key Pair
		List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");
		;

		// get Private Key
		PrivateKey genesisPrivateKey = null;

		genesisPrivateKey = genesisAccountList.get(0).getKeyPairList().get(0).getPrivateKey();

		// get the Account Object
		genesisAccount = genesisAccountList.get(0).getAccountId();
		List<PrivateKey> genesisKeyList = new ArrayList<PrivateKey>(1);
		genesisKeyList.add(genesisPrivateKey);
		accountKeys.put(genesisAccount, genesisKeyList);

	}

	private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
			throws Exception {
		return TestHelper.createTransfer(payer, accountKeys.get(payer).get(0),
				nodeAccount, payer,
				accountKeys.get(payer).get(0), nodeAccount, transferAmt);

	}

	private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance)
			throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Transaction transaction = TestHelper
				.createAccountWithFee(payerAccount, nodeAccount, keyPair, initialBalance,
						accountKeys.get(payerAccount));
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		System.out.println(
				"Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId = TestHelper
				.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		accountKeys.put(newlyCreateAccountId, Collections.singletonList(keyPair.getPrivate()));
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
		while (attempts <= MAX_RECEIPT_RETRIES &&
				transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
						.equals(ResponseCodeEnum.UNKNOWN)) {
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
			long durationInSeconds, KeyPair adminKeyPair) throws Exception {
		ContractID createdContract = null;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();

		byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
		Key adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();

		List<PrivateKey> keyList = new ArrayList<>(2);
		keyList.add(accountKeys.get(payerAccount).get(0));
		keyList.add(adminKeyPair.getPrivate());

		Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction createContractRequest = TestHelper
				.getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
						nodeAccount.getShardNum(), MAX_TX_FEE, timestamp,
						transactionDuration, true, "", 250000, contractFile, ByteString.EMPTY, 0,
						contractAutoRenew, keyList, "", adminPubKey);

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
			long value, ResponseCodeEnum expectedStatus) throws Exception {
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
		//payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee,
		// timestamp, txDuration, gas, contractId, functionData, value, signatures
		ByteString dataBstr = ByteString.EMPTY;
		if (data != null) {
			dataBstr = ByteString.copyFrom(data);
		}
		Transaction callContractRequest = TestHelper
				.getContractCallRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), node_account_number, 0l, 0l, MAX_TX_FEE, timestamp,
						transactionDuration, 25000, contractToCall, dataBstr, value,
						accountKeys.get(payerAccount));

		TransactionResponse response = stub.contractCallMethod(callContractRequest);
		System.out.println(
				" createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		Thread.sleep(500);
		TransactionBody callContractBody = TransactionBody.parseFrom(callContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCallReceipt = getReceipt(
				callContractBody.getTransactionID());
		Assert.assertEquals(expectedStatus, contractCallReceipt.getReceipt().getStatus());

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

		channel.shutdown();

		return dataToReturn;
	}

	public void updateContract(AccountID payerAccount, ContractID contractToUpdate,
			Duration autoRenewPeriod, Timestamp expirationTime) throws Exception {

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction updateContractRequest = RequestBuilder
				.getContractUpdateRequest(payerAccount, nodeAccount, MAX_TX_FEE, timestamp,
						transactionDuration, true, "", contractToUpdate, autoRenewPeriod, null, null,
						expirationTime, SignatureList.newBuilder().addSigs(Signature.newBuilder()
								.setEd25519(ByteString.copyFrom("testsignature".getBytes()))).build(), "");

		updateContractRequest = TransactionSigner
				.signTransaction(updateContractRequest, accountKeys.get(payerAccount));
		TransactionResponse response = stub.updateContract(updateContractRequest);
		System.out.println(
				" update contract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());
		Thread.sleep(1000);
		TransactionBody updateContractBody = TransactionBody.parseFrom(updateContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(
				updateContractBody.getTransactionID());
		Assert.assertNotNull(contractUpdateReceipt);
		channel.shutdown();

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

	private void deleteAccount(AccountID accountID, AccountID payerAccount, AccountID nodeID)
			throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoServiceGrpc.CryptoServiceBlockingStub cstub = CryptoServiceGrpc.newBlockingStub(channel);

		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionValidDuration = RequestBuilder.getDuration(100);
		TransactionID transactionID = TransactionID.newBuilder().setAccountID(payerAccount)
				.setTransactionValidStart(timestamp).build();
		CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
				.newBuilder().setDeleteAccountID(accountID).setTransferAccountID(payerAccount)
				.build();
		TransactionBody transactionBody = TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setNodeAccountID(nodeID)
				.setTransactionFee(TestHelper.getCryptoMaxFee())
				.setTransactionValidDuration(transactionValidDuration)
				.setMemo("Crypto Delete")
				.setCryptoDelete(cryptoDeleteTransactionBody)
				.build();
		byte[] bodyBytesArr = transactionBody.toByteArray();
		ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
		Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();

		List<PrivateKey> keys = new ArrayList<>(2);
		keys.addAll(accountKeys.get(payerAccount));
		keys.addAll(accountKeys.get(accountID));
		Transaction signTx = TransactionSigner
				.signTransaction(tx, keys);

		TransactionResponse response = cstub.cryptoDelete(signTx);
		log.info("cryptoDelete Response :: " + response.getNodeTransactionPrecheckCodeValue());

		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account delete :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signTx);

		TransactionBody deleteContractBody = TransactionBody.parseFrom(signTx.getBodyBytes());
		TransactionGetReceiptResponse cryptoDeleteReceipt = getReceipt(
				deleteContractBody.getTransactionID());
		Assert.assertEquals(ResponseCodeEnum.SUCCESS, cryptoDeleteReceipt.getReceipt().getStatus());

		channel.shutdown();
	}

	public void demo() throws Exception {
		loadGenesisAndNodeAcccounts();

		KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
		AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
		Assert.assertNotEquals(0, crAccount.getAccountNum());
		KeyPair receiverKeyPair = new KeyPairGenerator().generateKeyPair();
		AccountID receiverAccount = createAccount(receiverKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
		Assert.assertNotEquals(0, receiverAccount.getAccountNum());
		log.info("Accounts created successfully");

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		TestHelper.initializeFeeClient(channel, crAccount, crAccountKeyPair, nodeAccount);
		channel.shutdown();


		String fileName = "PayTest.bin";

		FileID simpleStorageFileId = LargeFileUploadIT
				.uploadFile(crAccount, fileName, crAccountKeyPair);
		Assert.assertNotEquals(0, simpleStorageFileId.getFileNum());
		log.info("Smart Contract file uploaded successfully");

		KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
		ContractID payTestContractId = createContract(crAccount, simpleStorageFileId, contractDuration,
				adminKeyPair);
		Assert.assertNotNull(payTestContractId);
		Assert.assertNotEquals(0, payTestContractId.getContractNum());
		log.info("Contract created successfully");

		depositToContract(crAccount, payTestContractId, VALUE_TO_DEPOSIT);
		log.info("Deposited to contract");

		AccountInfo sendAccInfo = getCryptoGetAccountInfo(crAccount, receiverAccount);
		String receiverSolidityAcc = sendAccInfo.getContractAccountID();
		int sendFundsAmount = VALUE_TO_DEPOSIT / 3;
		sendFunds(crAccount, payTestContractId, receiverSolidityAcc, sendFundsAmount,
				ResponseCodeEnum.SUCCESS);
		log.info("Contract paid receiver");

		deleteAccount(receiverAccount, crAccount, nodeAccount);
		log.info("Receiver account deleted");

		sendFunds(crAccount, payTestContractId, receiverSolidityAcc, sendFundsAmount,
				ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS);
		log.info("Contract paid to deleted receiver was rejected");
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

	private void depositToContract(AccountID payerAccount, ContractID contractId, int valueToDeposit)
			throws Exception {
		byte[] dataToSet = encodeDeposit(valueToDeposit);
		//set value to simple storage smart contract
		byte[] retData = callContract(payerAccount, contractId, dataToSet, valueToDeposit,
				ResponseCodeEnum.SUCCESS);
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

	private void sendFunds(AccountID payerAccount, ContractID contractId, String receiverAccount,
			int valueToSend, ResponseCodeEnum expectedStatus) throws Exception {
		byte[] dataToSet = encodeSendFunds(receiverAccount, valueToSend);
		//set value to simple storage smart contract
		byte[] retData = callContract(payerAccount, contractId, dataToSet, 0, expectedStatus);
	}
}
