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
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hedera.services.legacy.regression.ServerAppConfigUtility;
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
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

public class BigArrayPerformance extends Thread {
	private final static Logger log = LogManager.getLogger(BigArrayPerformance.class);
	private static final int MAX_RECEIPT_RETRIES = 120;
	private static final int MAX_BUSY_RETRIES = 25;
	private static final int BUSY_RETRY_MS = 200;
	private static final int BATCH_SIZE = 5;

	private static final String BA_SETSIZEINKB_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_howManyKB\"," +
			"\"type\":\"uint256\"}],\"name\":\"setSizeInKB\",\"outputs\":[],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	private static final String BA_CHANGEARRAY_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"_value\"," +
			"\"type\":\"uint256\"}],\"name\":\"changeArray\",\"outputs\":[],\"payable\":false," +
			"\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";

	private static long nodeAccountNum;
	private static AccountID nodeAccount;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private static final String BIG_ARRAY_BIN = "testfiles/BigArray.bin";
	private static AccountID genesisAccount;
	private static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
	private static String grpcHost;
	private static int grpcPort;
	private static long contractDuration;

	private static int numberOfThreads = 1;

	private static int sizeInKb;
	private static int numberOfIterations;

	private ManagedChannel channelShared;
	private CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub;
	private SmartContractServiceGrpc.SmartContractServiceBlockingStub sCServiceStub;
	KeyPair crAccountKeyPair;
	AccountID crAccount;
	private static long gasToOffer;

	static ContractID zeroContractId; //shared by all threads

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

	public BigArrayPerformance(String grpcHost, int grpcPort, int index) throws Exception {

		channelShared = ManagedChannelBuilder.forAddress(grpcHost, grpcPort)
				.usePlaintext(true)
				.build();
		cryptoStub = CryptoServiceGrpc.newBlockingStub(channelShared);
		sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channelShared);
		loadGenesisAndNodeAcccounts();

		TestHelper.initializeFeeClient(channelShared, genesisAccount, accountKeyPairs.get(genesisAccount),
				nodeAccount);

		crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
		crAccount = createAccount(crAccountKeyPair, genesisAccount,
				TestHelper.getCryptoMaxFee() * 5L);
		Assert.assertNotNull(crAccount);
		Assert.assertNotEquals(0, crAccount.getAccountNum());
		System.out.println("Account created successfully: " + crAccount);

		if (index == 0) {
			// Upload contract file
			FileID zeroContractFileId = LargeFileUploadIT
					.uploadFile(crAccount, BIG_ARRAY_BIN, new ArrayList<>(
							List.of(crAccountKeyPair.getPrivate())), grpcHost, nodeAccount);
			Assert.assertNotNull(zeroContractFileId);
			Assert.assertNotEquals(0, zeroContractFileId.getFileNum());
			System.out.println("Contract file uploaded successfully");

			// Create contract
			zeroContractId = createContract(crAccount, zeroContractFileId, null);
			Assert.assertNotNull(zeroContractId);
			Assert.assertNotEquals(0, zeroContractId.getContractNum());
			System.out.println("Contract created successfully: " + zeroContractId);

		}
	}


	public static void main(String args[]) throws Exception {
		Properties properties = getApplicationProperties();
		contractDuration = Long.parseLong(properties.getProperty("CONTRACT_DURATION"));
		grpcPort = Integer.parseInt(properties.getProperty("port"));

		if (args.length < 4) {
			System.out.println("Must provide at least four arguments to this application.");
			System.out.println("0: host");
			System.out.println("1: node number");
			System.out.println("2: number of iterations");
			System.out.println("3: storage in KB");
			System.out.println("4: (optional) thread amount");
			return;
		}

		System.out.println("args[0], host, is " + args[0]);
		System.out.println("args[1], node account, is " + args[1]);
		System.out.println("args[2], number of transfers, is " + args[2]);
		System.out.println("args[3], storage in KB, is " + args[3]);


		grpcHost = args[0];
		System.out.println("Got Grpc host as " + grpcHost);

		nodeAccountNum = Long.parseLong(args[1]);
		System.out.println("Got Node Account number as " + nodeAccountNum);
		nodeAccount = RequestBuilder
				.getAccountIdBuild(nodeAccountNum, 0l, 0l);

		numberOfIterations = Integer.parseInt(args[2]);
		System.out.println("Got number of iterations as " + numberOfIterations);

		sizeInKb = Integer.parseInt(args[3]);
		System.out.println("Got size in KB as " + sizeInKb);


		if ((args.length) > 4) {
			numberOfThreads = Integer.parseInt(args[4]);
			System.out.println("args[4], thread amount is " + args[4]);
		}

		ServerAppConfigUtility appConfig = ServerAppConfigUtility.getInstance(grpcHost, nodeAccountNum);
		gasToOffer = appConfig.getMaxGasLimit() - 1;

		BigArrayPerformance[] threadClients = new BigArrayPerformance[numberOfThreads];
		for (int k = 0; k < numberOfThreads; k++) {
			threadClients[k] = new BigArrayPerformance(grpcHost, grpcPort, k);
			threadClients[k].setName("thread" + k + ":");
		}

		for (int k = 0; k < numberOfThreads; k++) {
			threadClients[k].start();
		}
		for (int k = 0; k < numberOfThreads; k++) {
			threadClients[k].join();
		}

	}

	@Override
	public void run() {
		try {
			demo();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
		} catch (InvalidNodeTransactionPrecheckCode e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void demo()
			throws Exception {

		// Initialize storage size
		List<TransactionID> txnIdList = new ArrayList<>();
		TransactionID txnId = doSetSizeInKB(zeroContractId, crAccount, sizeInKb);
		txnIdList.add(txnId);
		clearTransactions(crAccount, txnIdList);
		System.out.println(getName() + "Contract storage size set");

		// Loop of store calls
		long start = System.nanoTime();
		for (int i = 0; i < numberOfIterations; i++) {
			txnId = doChangeArray(zeroContractId, crAccount, ThreadLocalRandom.current().nextInt(1000));
			txnIdList.add(txnId);
			if (txnIdList.size() >= BATCH_SIZE) {
				log.info(getName() + "Sent call " + i);
				clearTransactions(crAccount, txnIdList);
			}
		}
		if (txnIdList.size() > 0) {
			clearTransactions(crAccount, txnIdList);
		}

		long end = System.nanoTime();
		long elapsedMillis = (end - start) / 1_000_000;
		log.info(getName() + "Making " + numberOfIterations + " transfers took " +
				elapsedMillis / 1000.0 + " seconds");
		double tps = (numberOfIterations * 100000 / elapsedMillis) / 100.0;
		log.info(getName() + "About " + tps + " TPS for " + sizeInKb + " KB of storage");

		channelShared.shutdown();
	}


	private void clearTransactions(AccountID payingAccount, List<TransactionID> transactions)
			throws Exception {
		for (TransactionID id : transactions) {
			readReceiptAndRecord(payingAccount, id);
		}
		transactions.clear();
	}

	private static Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
			throws Exception {
		Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
				nodeAccount, payer,
				accountKeyPairs.get(payer), nodeAccount, transferAmt);
		return transferTx;
	}


	private AccountID createAccount(KeyPair keyPair, AccountID payerAccount,
			long initialBalance) throws Exception {

		Transaction transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
						accountKeyPairs.get(payerAccount));
		TransactionResponse response = cryptoStub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		System.out.println(
				"Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode()
						.name());
		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId = TestHelper
				.getTxReceipt(body.getTransactionID(), cryptoStub).getAccountID();
		accountKeyPairs.put(newlyCreateAccountId, keyPair);
		return newlyCreateAccountId;
	}

	private TransactionGetReceiptResponse getReceipt(TransactionID transactionId)
			throws Exception {
		TransactionGetReceiptResponse receiptToReturn = null;
		Query query = Query.newBuilder()
				.setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
						transactionId, ResponseType.ANSWER_ONLY)).build();

		Response transactionReceipts = cryptoStub.getTransactionReceipts(query);
		int attempts = 1;
		while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt()
				.getReceipt()
				.getStatus().equals(ResponseCodeEnum.UNKNOWN)) {
			Thread.sleep(1000);
			transactionReceipts = cryptoStub.getTransactionReceipts(query);
			System.out.println("waiting to getTransactionReceipts as not Unknown..." +
					transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
			attempts++;
		}
		Assert.assertEquals(ResponseCodeEnum.SUCCESS,
				transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());

		return transactionReceipts.getTransactionGetReceipt();

	}

	private ContractID createContract(AccountID payerAccount, FileID contractFile,
			byte[] constructorData) throws Exception {
		ContractID createdContract = null;
		ByteString dataToPass = ByteString.EMPTY;
		if (constructorData != null) {
			dataToPass = ByteString.copyFrom(constructorData);
		}

		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		;
		Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
		Transaction createContractRequest = TestHelper
				.getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
						nodeAccount.getShardNum(), TestHelper.getContractMaxFee(), timestamp,
						transactionDuration, true, "", 1250000, contractFile, dataToPass, 0,
						Duration.newBuilder().setSeconds(contractDuration).build(), accountKeyPairs.get(payerAccount),
						"",
						null);

		TransactionResponse response = sCServiceStub.createContract(createContractRequest);
		System.out.println(
				" createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());
		TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
				createContractBody.getTransactionID());
		if (contractCreateReceipt != null) {
			createdContract = contractCreateReceipt.getReceipt().getContractID();
		}

		return createdContract;
	}


	private TransactionID callContract(AccountID payerAccount, ContractID contractToCall, byte[] data)
			throws Exception {
		ContractID createdContract = null;

		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		;
		Duration transactionDuration = RequestBuilder.getDuration(TestHelper.TX_DURATION);
		//payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee,
		// timestamp, txDuration, gas, contractId, functionData, value, signatures
		ByteString dataBstr = ByteString.EMPTY;
		if (data != null) {
			dataBstr = ByteString.copyFrom(data);
		}
		Transaction callContractRequest = TestHelper
				.getContractCallRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccountNum, 0l, 0l,
						TestHelper.getContractMaxFee(), timestamp,
						transactionDuration, gasToOffer, contractToCall, dataBstr, 0,
						accountKeyPairs.get(payerAccount));

		TransactionResponse response = null;
		for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
			response = sCServiceStub.contractCallMethod(callContractRequest);
			System.out.println(" call contract  Pre Check Response :: " +
					response.getNodeTransactionPrecheckCode().name());
			if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
				break;
			}
			Assert.assertEquals(ResponseCodeEnum.BUSY, response.getNodeTransactionPrecheckCode());
			Thread.sleep(BUSY_RETRY_MS);
		}
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());

		TransactionID txId = TransactionBody.parseFrom(callContractRequest.getBodyBytes())
				.getTransactionID();
		return txId;
	}

	private void readReceiptAndRecord(AccountID payerAccount, TransactionID txId)
			throws Exception {
		TransactionGetReceiptResponse contractCallReceipt = getReceipt(txId);
		if (contractCallReceipt != null && contractCallReceipt.getReceipt().getStatus().name()
				.equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
			//Thread.sleep(6000);
			TransactionRecord trRecord = getTransactionRecord(payerAccount, txId);
			if (trRecord != null && trRecord.hasContractCallResult()) {
				ContractFunctionResult callResults = trRecord.getContractCallResult();
				String errMsg = callResults.getErrorMessage();
				if (!StringUtils.isEmpty(errMsg)) {
					System.out.println("@@@ Contract Call resulted in error: " + errMsg);
				}
			}
		}
	}

	private TransactionRecord getTransactionRecord(AccountID payer,
			TransactionID transactionId) throws Exception {
		AccountID createdAccount = null;
		long fee = FeeClient.getCostForGettingTxRecord();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc
				.newBlockingStub(channelShared);
		Transaction paymentTx = createQueryHeaderTransfer(payer, fee);
		Query getRecordQuery = RequestBuilder
				.getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.COST_ANSWER);
		Response recordResp = stub.getTxRecordByTxID(getRecordQuery);

		fee = recordResp.getTransactionGetRecord().getHeader().getCost();
		paymentTx = createQueryHeaderTransfer(payer, fee);
		getRecordQuery = RequestBuilder
				.getTransactionGetRecordQuery(transactionId, paymentTx, ResponseType.ANSWER_ONLY);
		recordResp = stub.getTxRecordByTxID(getRecordQuery);

		TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();

		return txRecord;
	}

	/*
	Methods to run setSizeInKB method
	 */
	private TransactionID doSetSizeInKB(ContractID contractId, AccountID payerAccount, int sizeInKB)
			throws Exception {
		byte[] dataToSet = encodeSetSizeInKB(sizeInKB);
		//set value to simple storage smart contract
		return callContract(payerAccount, contractId, dataToSet);
	}

	private static byte[] encodeSetSizeInKB(int sizeInKB) {
		String retVal = "";
		CallTransaction.Function function = getSetSizeInKBFunction();
		byte[] encodedFunc = function.encode(sizeInKB);

		return encodedFunc;
	}

	private static CallTransaction.Function getSetSizeInKBFunction() {
		String funcJson = BA_SETSIZEINKB_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	/*
	Methods to run changeArray method
	 */
	private TransactionID doChangeArray(ContractID contractId, AccountID payerAccount, int newValue)
			throws Exception {
		byte[] dataToSet = encodeChangeArray(newValue);
		//set value to simple storage smart contract
		return callContract(payerAccount, contractId, dataToSet);
	}

	private static byte[] encodeChangeArray(int newValue) {
		String retVal = "";
		CallTransaction.Function function = getChangeArrayFunction();
		byte[] encodedFunc = function.encode(newValue);

		return encodedFunc;
	}

	private static CallTransaction.Function getChangeArrayFunction() {
		String funcJson = BA_CHANGEARRAY_ABI.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function;
	}

	private static Properties getApplicationProperties() {
		Properties prop = new Properties();
		InputStream input = null;
		try {
			String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
			input = new FileInputStream(rootPath + "application.properties");
			prop.load(input);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return prop;
	}
}
