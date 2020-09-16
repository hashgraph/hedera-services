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
import com.hedera.services.legacy.client.util.Common;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
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
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.LargeFileUploadIT;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Check records for various smart contract creation failures.
 *
 * @author Hua Li
 * 		Created on 2019-06-10
 * 		Based on SmartContractNegativeCreates.java
 */
public class RecordTestsContractCreate {
	public static final long INITIAL_PAYER_BALANCE = TestHelper.getCryptoMaxFee() * 5;
	public static final long GAS_COST = 1L;
	private static long contractDuration = CustomPropertiesSingleton.getInstance().getContractDuration();
	private final static long CONTRACT_CREATE_FAIL_GAS = 1L;
	private final static long CONTRACT_CREATE_SUCCESS_GAS = 250000L;
	private final static Logger log = LogManager.getLogger(RecordTestsContractCreate.class);

	private static final int MAX_RECEIPT_RETRIES = 60;
	private static AccountID nodeAccount;
	private static long node_account_number;
	private static long node_shard_number;
	private static long node_realm_number;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	private AccountID genesisAccount;
	private static Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
	private static String host;
	private static int port;
	private Transaction lastTx;

	public static void main(String args[]) throws Exception {
		Properties properties = TestHelper.getApplicationProperties();
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
			RecordTestsContractCreate scFf = new RecordTestsContractCreate();
			scFf.demo();
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
				.getReceipt()
				.getStatus().name().equalsIgnoreCase(ResponseCodeEnum.UNKNOWN.name())) {
			Thread.sleep(1000);
			transactionReceipts = stub.getTransactionReceipts(query);
			System.out.println("waiting to getTransactionReceipts as not Unknown..." +
					transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name() +
					"  (" + attempts + ")");
			attempts++;
		}
		if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
				.equals(ResponseCodeEnum.SUCCESS)) {
			receiptToReturn = transactionReceipts.getTransactionGetReceipt();
		}
		channel.shutdown();
		return transactionReceipts.getTransactionGetReceipt();

	}

	// Differs from TestHelper version in that you can force it not to look up the fee.
	public static Transaction testGetCreateContractRequest(Long payerAccountNum, Long payerRealmNum,
			Long payerShardNum,
			Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
			long transactionFee, Timestamp timestamp, Duration txDuration,
			boolean generateRecord, String txMemo, long gas, FileID fileId,
			ByteString constructorParameters, long initialBalance,
			Duration autoRenewalPeriod, List<KeyPair> keyPairs, String contractMemo,
			Key adminKey, boolean forceTransactionFee) throws Exception {
		Transaction transaction;

		List<Key> keyList = new ArrayList<>();
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		for (KeyPair pair : keyPairs) {
			keyList.add(Common.PrivateKeyToKey(pair.getPrivate()));
			Common.addKeyMap(pair, pubKey2privKeyMap);
		}

		if (!forceTransactionFee) {
			transaction = RequestBuilder
					.getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
							nodeRealmNum, nodeShardNum, transactionFee, timestamp,
							txDuration, generateRecord, txMemo, gas, fileId, constructorParameters,
							initialBalance,
							autoRenewalPeriod, SignatureList.newBuilder()
									.addSigs(Signature.newBuilder()
											.setEd25519(ByteString.copyFrom("testsignature".getBytes())))
									.build(), contractMemo, adminKey);

			transaction = TransactionSigner.signTransactionComplexWithSigMap(
					transaction, keyList, pubKey2privKeyMap);

			transactionFee = FeeClient.getContractCreateFee(transaction, keyList.size());
		}

		transaction = RequestBuilder
				.getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
						autoRenewalPeriod, SignatureList.newBuilder()
								.addSigs(Signature.newBuilder()
										.setEd25519(ByteString.copyFrom("testsignature".getBytes())))
								.build(), contractMemo, adminKey);

		transaction = TransactionSigner.signTransactionComplexWithSigMap(
				transaction, keyList, pubKey2privKeyMap);
		return transaction;
	}

	private ContractID createContractWithOptions(AccountID payerAccount, FileID contractFile,
			AccountID useNodeAccount, long autoRenewInSeconds, long gas, long balance,
			ResponseCodeEnum expectedPrecheck, ResponseCodeEnum expectedStatus,
			long transactionFee, boolean forceTransactionFee, ByteString constructorParams,
			KeyPair adminKeyPair)
			throws Exception {
		ContractID createdContract = null;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();

		Duration contractAutoRenew = Duration.newBuilder().setSeconds(autoRenewInSeconds).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Key adminPubKey = null;
		List<KeyPair> keyPairList = new ArrayList<>();
		keyPairList.add(accountKeyPairs.get(payerAccount));
		if (adminKeyPair != null) {
			byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
			adminPubKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			if (!adminKeyPair.equals(accountKeyPairs.get(payerAccount))) {
				keyPairList.add(adminKeyPair);
			}
		}

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction createContractRequest = testGetCreateContractRequest(
				payerAccount.getAccountNum(), payerAccount.getRealmNum(), payerAccount.getShardNum(),
				useNodeAccount.getAccountNum(), useNodeAccount.getRealmNum(), useNodeAccount.getShardNum(),
				transactionFee, timestamp,
				transactionDuration, true, "", gas, contractFile, constructorParams, balance,
				contractAutoRenew, keyPairList, "", adminPubKey, forceTransactionFee);

		TransactionResponse response = stub.createContract(createContractRequest);
		System.out.println(
				" createContractWithOptions Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());
		Assert.assertEquals("Unexpected precheck code", expectedPrecheck,
				response.getNodeTransactionPrecheckCode());

		lastTx = createContractRequest;
		TransactionBody createContractBody = TransactionBody.parseFrom(createContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
				createContractBody.getTransactionID());
		if (contractCreateReceipt != null) {
			createdContract = contractCreateReceipt.getReceipt().getContractID();
		}
		ResponseCodeEnum statusCode = contractCreateReceipt.getReceipt().getStatus();
		Assert.assertEquals("Unexpected status code ", expectedStatus, statusCode);
		channel.shutdown();

		return createdContract;
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
		AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, INITIAL_PAYER_BALANCE);
		log.info("Account created successfully");
		String fileName = "simpleStorage.bin";
		Assert.assertNotNull(crAccount);

		FileID simpleStorageFileId = LargeFileUploadIT
				.uploadFile(crAccount, fileName, crAccountKeyPair);
		Assert.assertNotNull("Storage file id is null.", simpleStorageFileId);
		log.info("Smart Contract file uploaded successfully");
		ContractID sampleStorageContractId;

		sampleStorageContractId = createContractWithOptions(crAccount, simpleStorageFileId,
				nodeAccount, contractDuration, CONTRACT_CREATE_SUCCESS_GAS, 0L,
				ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 100L, false, null, crAccountKeyPair);
		Assert.assertNotEquals(0L, sampleStorageContractId.getContractNum());
		log.info("Creator key as admin key succeeds");
		checkRecord(lastTx, crAccount, nodeAccount);

		sampleStorageContractId = createContractWithOptions(crAccount, simpleStorageFileId,
				nodeAccount, contractDuration, CONTRACT_CREATE_FAIL_GAS, 0L,
				ResponseCodeEnum.OK, ResponseCodeEnum.INSUFFICIENT_GAS, 100L, false, null, null);
		Assert.assertEquals(0L, sampleStorageContractId.getContractNum());
		log.info("Contract create failed due to lack of gas");
		checkRecord(lastTx, crAccount, nodeAccount);

		sampleStorageContractId = createContractWithOptions(crAccount, simpleStorageFileId,
				nodeAccount, contractDuration, CONTRACT_CREATE_SUCCESS_GAS, 90_000_000L,
				ResponseCodeEnum.OK, ResponseCodeEnum.CONTRACT_REVERT_EXECUTED, 100L, false, null, null);
		Assert.assertEquals(0L, sampleStorageContractId.getContractNum());
		log.info("Contract create failed with positive initial balance (CONTRACT_REVERT_EXECUTED)");
		checkRecord(lastTx, crAccount, nodeAccount);

		sampleStorageContractId = createContractWithOptions(crAccount, simpleStorageFileId,
				nodeAccount, contractDuration, CONTRACT_CREATE_SUCCESS_GAS, 0L,
				ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 100L, false,
				ByteString.copyFromUtf8("bad params"), null);
		Assert.assertNotEquals(0L, sampleStorageContractId.getContractNum());
		log.info("Unused constructor params are ignored");
		checkRecord(lastTx, crAccount, nodeAccount);

		sampleStorageContractId = createContractWithOptions(crAccount, simpleStorageFileId,
				nodeAccount, contractDuration, CONTRACT_CREATE_SUCCESS_GAS, 0L,
				ResponseCodeEnum.OK, ResponseCodeEnum.SUCCESS, 100L, false, null, null);
		Assert.assertNotEquals(0L, sampleStorageContractId.getContractNum());
		log.info("Contract create succeeded");
		checkRecord(lastTx, crAccount, nodeAccount);
	}

	private static TransactionRecord getTransactionRecord(AccountID payerAccount,
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

	private static Response executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
			CryptoServiceGrpc.CryptoServiceBlockingStub stub, long fee, ResponseType responseType)
			throws Exception {
		Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
		Query getRecordQuery = RequestBuilder
				.getTransactionGetRecordQuery(transactionId, paymentTx, responseType);
		Response recordResp = stub.getTxRecordByTxID(getRecordQuery);
		return recordResp;
	}

	private static Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
			throws Exception {
		Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer),
				nodeAccount, payer, accountKeyPairs.get(payer), nodeAccount, transferAmt);
		return transferTx;
	}

	/**
	 * Checks if record fields are instantiated.
	 *
	 * @param transaction
	 * @param payerID
	 * @param nodeID
	 * @throws Exception
	 */
	public static void checkRecord(Transaction transaction, AccountID payerID, AccountID nodeID) throws Exception {
		CommonUtils.nap(2);
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(transaction);
		TransactionRecord record = getTransactionRecord(payerID, body.getTransactionID());
		CommonUtils.checkRecord(record, body);
	}
}
