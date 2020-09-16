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
import com.google.protobuf.TextFormat;
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
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
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.ThreadLocalRandom;

public class V3V4Before {

	private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private final static String MEMO_STRING_BASE = "A random number is: ";
	private final Logger log = LogManager.getLogger(V3V4Before.class);
	public final String TRANSFER_FILE_NAME = "v3v4.txt";


	private static final int MAX_RECEIPT_RETRIES = 60;
	private static final long MAX_TX_FEE = TestHelper.getContractMaxFee();
	private static final String SC_GET_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"get\"," +
			"\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\"," +
			"\"type\":\"function\"}";
	private static final String SC_SET_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"x\",\"type\":\"uint256\"}]," +
			"\"name\":\"set\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\"," +
			"\"type\":\"function\"}";
	private static AccountID nodeAccount;
	private static long node_account_number;
	private static long node_shard_number;
	private static long node_realm_number;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private AccountID genesisAccount;
	private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
	private static String host;
	private static int port;


	public static void main(String args[]) throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		host = properties.getProperty("host");
		port = Integer.parseInt(properties.getProperty("port"));
		node_account_number = Utilities.getDefaultNodeAccount();
		node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
		node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
		nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number)
				.setRealmNum(node_shard_number).setShardNum(node_realm_number).build();

		V3V4Before vvb = new V3V4Before();
		vvb.demo();

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

	private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt) {
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

	private TransactionGetReceiptResponse getReceipt(TransactionID transactionId,
			ResponseCodeEnum expectedStatus) throws Exception {
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
			Thread.sleep(1000);
			transactionReceipts = stub.getTransactionReceipts(query);
			System.out.println("waiting to getTransactionReceipts as not Unknown..." +
					transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
			attempts++;
		}
		channel.shutdown();
		Assert.assertEquals(expectedStatus,
				transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());
		return transactionReceipts.getTransactionGetReceipt();

	}

	private ContractID createContractWithKeyAndMemo(AccountID payerAccount, FileID contractFile,
			Key adminKey,
			AccountID adminAccount, String memo) throws Exception {
		ContractID createdContract = null;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();

		Duration contractAutoRenew = Duration.newBuilder().setSeconds(DAY_SEC * 30).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		List<PrivateKey> keyList;
		if (adminAccount == null) {
			keyList = accountKeys.get(payerAccount);
		} else {
			keyList = new ArrayList<>(accountKeys.get(payerAccount));
			keyList.addAll(accountKeys.get(adminAccount));
		}

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction createContractRequest = TestHelper
				.getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
						nodeAccount.getShardNum(), MAX_TX_FEE, timestamp,
						transactionDuration, true, "", 250000, contractFile, ByteString.EMPTY, 0,
						contractAutoRenew, keyList, memo, adminKey);

		TransactionResponse response = stub.createContract(createContractRequest);
		System.out.println(
				" createContractWithKeyAndMemo Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());
		TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
				createContractBody.getTransactionID(), ResponseCodeEnum.SUCCESS);
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
		System.out.println("tx record retrieved");
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

	public void demo() throws Exception {
		loadGenesisAndNodeAcccounts();

		KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
		Key adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		AccountID adminAccount = createAccount(adminKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);

		KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
		AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);
		Assert.assertNotEquals(0, crAccount.getAccountNum());
		log.info("Account created successfully " + crAccount.getAccountNum());
		String fileName = "simpleStorage.bin";

		FileID simpleStorageFileId = LargeFileUploadIT
				.uploadFile(crAccount, fileName, crAccountKeyPair);
		Assert.assertNotEquals(0, simpleStorageFileId.getFileNum());
		log.info("Smart Contract file uploaded successfully " + simpleStorageFileId.getFileNum());

		String memo;
		Path transferPath = FileSystems.getDefault().getPath(TRANSFER_FILE_NAME);
		try (BufferedWriter writer = Files.newBufferedWriter(transferPath)) {

			memo = MEMO_STRING_BASE + ThreadLocalRandom.current().nextInt(1024);
			createAndWriteContract(crAccount, simpleStorageFileId, writer,
					adminPubKey, adminAccount, memo);
			log.info("Contract with both created successfully");

			memo = MEMO_STRING_BASE + ThreadLocalRandom.current().nextInt(1024);
			createAndWriteContract(crAccount, simpleStorageFileId, writer,
					null, null, memo);
			log.info("Contract with only memo created successfully");

			createAndWriteContract(crAccount, simpleStorageFileId, writer,
					adminPubKey, adminAccount, null);
			log.info("Contract with only key created successfully");

			createAndWriteContract(crAccount, simpleStorageFileId, writer,
					null, null, null);
			log.info("Contract with neither created successfully");
		} catch (IOException e) {
			System.out.println(e);
			Assert.fail("Exception writing contract list");
		}
	}

	private void createAndWriteContract(AccountID crAccount, FileID simpleStorageFileId,
			BufferedWriter writer, Key adminPubKey, AccountID adminAccount, String memo)
			throws Exception {
		ContractID thisContractId;
		thisContractId = createContractWithKeyAndMemo(crAccount, simpleStorageFileId,
				adminPubKey, adminAccount, memo);
		Assert.assertNotNull(thisContractId);
		Assert.assertNotEquals(0L, thisContractId.getContractNum());

		ContractInfo contractInfo = getContractInfo(crAccount, thisContractId);
		writer.write(contractInfo.getContractID().getContractNum() + "\n");
		writer.write(contractInfo.getMemo() + "\n");
		String keyString = TextFormat.shortDebugString(contractInfo.getAdminKey());
		writer.write(keyString + "\n");
		writer.write("------------\n"); // just for humans
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
