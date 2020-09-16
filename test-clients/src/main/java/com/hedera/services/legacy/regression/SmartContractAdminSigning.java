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
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
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
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Test signing the creation of a smart contract.
 *
 * @author peter
 * 		Adapted 2018-03-04 from SmartContractCRUD.java
 */
public class SmartContractAdminSigning {

	private static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private final static String CONTRACT_MEMO_STRING = "This is a memo string with non-Ascii characters: ȀĊ";
	private final Logger log = LogManager.getLogger(SmartContractAdminSigning.class);


	private static final int MAX_RECEIPT_RETRIES = 60;
	private static final long MAX_TX_FEE = TestHelper.getContractMaxFee();
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
			SmartContractAdminSigning scas = new SmartContractAdminSigning();
			scas.demo();
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

	private ContractID createContractWithKey(AccountID payerAccount, FileID contractFile,
			long durationInSeconds, Key adminKey,
			AccountID adminAccount, ResponseCodeEnum expectedStatus) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();

		Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		List<KeyPair> keyPairList = new ArrayList<>();
		keyPairList.add(accountKeyPairs.get(payerAccount));
		if (adminAccount != null && !adminAccount.equals(payerAccount)) {
			keyPairList.add(accountKeyPairs.get(adminAccount));
		}

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction createContractRequest = TestHelper
				.getCreateContractRequestSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
						nodeAccount.getShardNum(), MAX_TX_FEE, timestamp,
						transactionDuration, true, "", 250000, contractFile, ByteString.EMPTY, 0,
						contractAutoRenew, keyPairList, CONTRACT_MEMO_STRING, adminKey);

		TransactionResponse response = stub.createContract(createContractRequest);
		System.out.println(
				" createContractWithKey Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());
		TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
				createContractBody.getTransactionID());
		channel.shutdown();

		Assert.assertNotNull(contractCreateReceipt);
		Assert.assertEquals(expectedStatus, contractCreateReceipt.getReceipt().getStatus());
		System.out.println("Got expected status: " + expectedStatus);
		System.out.println("");

		if (contractCreateReceipt.getReceipt().getStatus() == ResponseCodeEnum.SUCCESS) {
			TransactionRecord trRecord = getTransactionRecord(payerAccount,
					createContractBody.getTransactionID());
			Assert.assertNotNull(trRecord);
			Assert.assertTrue(trRecord.hasContractCreateResult());
			Assert.assertEquals(trRecord.getContractCreateResult().getContractID(),
					contractCreateReceipt.getReceipt().getContractID());
		}

		return contractCreateReceipt.getReceipt().getContractID();
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

	private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
			throws Exception {
		Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
				nodeAccount, payer,
				accountKeyPairs.get(payer), nodeAccount, transferAmt);
		return transferTx;

	}

	public void demo() throws Exception {
		loadGenesisAndNodeAcccounts();

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		TestHelper.initializeFeeClient(channel, genesisAccount, accountKeyPairs.get(genesisAccount),
				nodeAccount);
		channel.shutdown();

		KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
		Key adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		Key adminContractIDKey = Key.newBuilder().setContractID(
				ContractID.newBuilder().setContractNum(100).build()).build();
		AccountID adminAccount = createAccount(adminKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);

		KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
		AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, TestHelper.getCryptoMaxFee() * 10);

		Assert.assertNotNull(crAccount);
		Assert.assertNotNull(adminAccount);
		Assert.assertNotEquals(0, crAccount.getAccountNum());
		Assert.assertNotEquals(0, adminAccount.getAccountNum());

		log.info("Accounts created successfully " + crAccount.getAccountNum());
		String fileName = "simpleStorage.bin";

		FileID simpleStorageFileId = LargeFileUploadIT
				.uploadFile(crAccount, fileName, crAccountKeyPair);
		if (simpleStorageFileId != null) {
			log.info("Smart Contract file uploaded successfully " + simpleStorageFileId.getFileNum());

			// Properly signed with admin key
			createContractWithKey(crAccount, simpleStorageFileId, contractDuration,
					adminPubKey, adminAccount, ResponseCodeEnum.SUCCESS);

			// No admin key, no sig
			createContractWithKey(crAccount, simpleStorageFileId, contractDuration,
					null, null, ResponseCodeEnum.SUCCESS);

			// Missing sig, should fail
			createContractWithKey(crAccount, simpleStorageFileId, contractDuration,
					adminPubKey, null, ResponseCodeEnum.INVALID_SIGNATURE);

			// Wrong sig, should fail
			createContractWithKey(crAccount, simpleStorageFileId, contractDuration,
					adminPubKey, crAccount, ResponseCodeEnum.INVALID_SIGNATURE);

			// Admin sig but no admin key, should fail
			createContractWithKey(crAccount, simpleStorageFileId, contractDuration,
					null, adminAccount, ResponseCodeEnum.SUCCESS);

			// ContractID admin key, should succeed
			createContractWithKey(crAccount, simpleStorageFileId, contractDuration,
					adminContractIDKey, null, ResponseCodeEnum.SUCCESS);

			// ContractID admin key, should succeed
			createContractWithKey(crAccount, simpleStorageFileId, contractDuration,
					adminContractIDKey, adminAccount, ResponseCodeEnum.OK);
		}
	}
}
