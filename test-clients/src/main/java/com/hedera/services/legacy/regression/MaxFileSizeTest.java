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
import java.util.Random;

import com.hedera.services.legacy.client.util.Common;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

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
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;

public class MaxFileSizeTest {
	private final Logger log = LogManager.getLogger(MaxFileSizeTest.class);

	private static final int MAX_RECEIPT_RETRIES = 60;
	private static final long MAX_TX_FEE = FeeClient.getMaxFee();
	private static AccountID nodeAccount;
	private static long node_account_number;
	private static long node_shard_number;
	private static long node_realm_number;
	public static String INITIAL_ACCOUNTS_FILE = "StartUpAccount.txt";
	private AccountID genesisAccount;
	private Map<AccountID, KeyPair> accountKeyPairs = new HashMap<>();
	private static String host;
	private static int port;
	private static long fileDuration;
	public static long DAY_SEC = 24 * 60 * 60; // secs in a day
	private static int FILE_CHUNK_SIZE = 5; //in kilobytes 
	private  static int serverMaxFileSize; //in kilobytes

	public static void main(String args[]) throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		host = properties.getProperty("host");
		port = Integer.parseInt(properties.getProperty("port"));
		node_account_number = Utilities.getDefaultNodeAccount();
		node_shard_number = Long.parseLong(properties.getProperty("NODE_REALM_NUMBER"));
		node_realm_number = Long.parseLong(properties.getProperty("NODE_SHARD_NUMBER"));
		nodeAccount = AccountID.newBuilder().setAccountNum(node_account_number).setRealmNum(node_shard_number)
				.setShardNum(node_realm_number).build();
		fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));

		// Read server configuration
		ServerAppConfigUtility serverConfig = ServerAppConfigUtility.getInstance(
				host, node_account_number);
		serverMaxFileSize = serverConfig.getMaxFileSize();

		int numberOfReps = 1;
//	    if ((args.length) > 0) {
//	      numberOfReps = Integer.parseInt(args[0]);
//	    }
		for (int i = 0; i < numberOfReps; i++) {
			MaxFileSizeTest mFsz = new MaxFileSizeTest();
			mFsz.demo();
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

	private Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt) throws Exception {
		Transaction transferTx = TestHelper.createTransferSigMap(payer, accountKeyPairs.get(payer), nodeAccount, payer,
				accountKeyPairs.get(payer), nodeAccount, transferAmt);
		return transferTx;

	}

	private AccountID createAccount(KeyPair keyPair, AccountID payerAccount, long initialBalance) throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Transaction transaction = TestHelper.createAccountWithSigMap(payerAccount, nodeAccount, keyPair, initialBalance,
				accountKeyPairs.get(payerAccount));
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create  account :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId = TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		accountKeyPairs.put(newlyCreateAccountId, keyPair);
		channel.shutdown();
		return newlyCreateAccountId;
	}

	private TransactionGetReceiptResponse getReceipt(TransactionID transactionId, ResponseCodeEnum expectedStatus)
			throws Exception {
		Query query = Query.newBuilder().setTransactionGetReceipt(
				RequestBuilder.getTransactionGetReceiptQuery(transactionId, ResponseType.ANSWER_ONLY)).build();
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Response transactionReceipts = stub.getTransactionReceipts(query);
		int attempts = 1;
		while (attempts <= MAX_RECEIPT_RETRIES && transactionReceipts.getTransactionGetReceipt().getReceipt()
				.getStatus().equals(ResponseCodeEnum.UNKNOWN)) {
			Thread.sleep(1000);
			transactionReceipts = stub.getTransactionReceipts(query);
			log.info("waiting to getTransactionReceipts as not Unknown..."
					+ transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
			attempts++;
		}
		channel.shutdown();
		Assert.assertEquals(expectedStatus, transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus());
		return transactionReceipts.getTransactionGetReceipt();

	}

	private TransactionRecord getTransactionRecord(AccountID payerAccount, TransactionID transactionId)
			throws Exception {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
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

	public void updateContractWithKey(AccountID payerAccount, ContractID contractToUpdate, Duration autoRenewPeriod,
			Timestamp expirationTime, String contractMemo, AccountID adminAccount, ResponseCodeEnum expectedStatus)
			throws Exception {

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		List<Key> keyList = new ArrayList<>();
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		KeyPair payerKeyPair = accountKeyPairs.get(payerAccount);
		keyList.add(Common.PrivateKeyToKey(payerKeyPair.getPrivate()));
		Common.addKeyMap(payerKeyPair, pubKey2privKeyMap);
		if (adminAccount != null && !adminAccount.equals(payerAccount)) {
			KeyPair adminKeyPair = accountKeyPairs.get(adminAccount);
			keyList.add(Common.PrivateKeyToKey(adminKeyPair.getPrivate()));
			Common.addKeyMap(adminKeyPair, pubKey2privKeyMap);
		}

		Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction updateContractRequest = RequestBuilder.getContractUpdateRequest(payerAccount, nodeAccount,
				MAX_TX_FEE, timestamp, transactionDuration, true, "", contractToUpdate, autoRenewPeriod, null, null,
				expirationTime,
				SignatureList.newBuilder()
						.addSigs(Signature.newBuilder().setEd25519(ByteString.copyFrom("testsignature".getBytes())))
						.build(),
				contractMemo);
		updateContractRequest = TransactionSigner.signTransactionComplexWithSigMap(updateContractRequest, keyList,
				pubKey2privKeyMap);

		TransactionResponse response = stub.updateContract(updateContractRequest);
		System.out
				.println(" update contract Pre Check Response :: " + response.getNodeTransactionPrecheckCode().name());
		Thread.sleep(1000);
		TransactionBody updateContractBody = TransactionBody.parseFrom(updateContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(updateContractBody.getTransactionID(),
				expectedStatus);
		Assert.assertNotNull(contractUpdateReceipt);
		TransactionRecord trRecord = getTransactionRecord(payerAccount, updateContractBody.getTransactionID());
		Assert.assertNotNull(trRecord);
		channel.shutdown();

	}

	public void demo() throws Exception {
		loadGenesisAndNodeAcccounts();

		KeyPair adminKeyPair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) adminKeyPair.getPublic()).getAbyte();
		Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();

		KeyPair crAccountKeyPair = new KeyPairGenerator().generateKeyPair();
		AccountID crAccount = createAccount(crAccountKeyPair, genesisAccount, FeeClient.getMaxFee() * 10);
		log.info("Account created successfully " + crAccount.getAccountNum());
		List<Key> waclPubKeyList = new ArrayList<Key>();
		List<PrivateKey> waclPrivKeyList = new ArrayList<PrivateKey>();
		int sentFileBytes = 0;
		if (crAccount != null) {
			genWacl(1,waclPubKeyList,waclPrivKeyList);
			Assert.assertNotEquals(0, crAccount.getAccountNum());
			Random rd = new Random(); 
			byte[] fileContent = new byte[1024 * FILE_CHUNK_SIZE];
			rd.nextBytes(fileContent);
			FileID fileId = createFile(crAccount,waclPubKeyList,waclPrivKeyList,fileContent);
			Assert.assertNotNull(fileId);
			Assert.assertNotEquals(0,fileId.getFileNum());
			sentFileBytes = FILE_CHUNK_SIZE;
			while(sentFileBytes <= serverMaxFileSize) {
				log.info("File Current size is " + sentFileBytes);
				int appendFileSize = FILE_CHUNK_SIZE;
				ResponseCodeEnum expectedStatus = ResponseCodeEnum.SUCCESS;
				if(sentFileBytes + appendFileSize >serverMaxFileSize) {
					//first check if  exact max was reached 
					if(sentFileBytes==serverMaxFileSize) {
						log.info("About to reach max file size limit");
						appendFileSize = serverMaxFileSize -sentFileBytes +1; //exceed allowed size by 1 byte
						expectedStatus = ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
					}
					else {
						appendFileSize = serverMaxFileSize -sentFileBytes;
					}
				}
				byte[] appendContent = new byte[1024 * appendFileSize];
				appendFile(crAccount,fileId,waclPubKeyList,waclPrivKeyList,appendContent,expectedStatus);
				sentFileBytes +=appendFileSize;
				
			}
			
			
		}
	}

	public void genWacl(int numKeys, List<Key> waclPubKeyList, List<PrivateKey> waclPrivKeyList) {
		for (int i = 0; i < numKeys; i++) {
			KeyPair pair = new KeyPairGenerator().generateKeyPair();
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			waclPubKeyList.add(waclKey);
			waclPrivKeyList.add(pair.getPrivate());
		}
	}

	public static void populateKeyListAndMapFromWacl(List<Key> waclPubKeyList, List<PrivateKey> waclPrivKeyList,
			List<Key> keyList, Map<String, PrivateKey> pubKey2privKeyMap) {
		for (int i = 0; i < waclPubKeyList.size(); i++) {
			byte[] pubKey = waclPubKeyList.get(i).getEd25519().toByteArray();
			String tempPublicKeyHex = HexUtils.bytes2Hex(pubKey);
			Key key = waclPubKeyList.get(i);
			keyList.add(key);
			pubKey2privKeyMap.put(tempPublicKeyHex, waclPrivKeyList.get(i));
		}
	}

	public static void populateKeyListAndMapFromKeyPair(List<KeyPair> keyPairList, List<Key> keyList,
			Map<String, PrivateKey> pubKey2privKeyMap) {
		for (KeyPair keyPair : keyPairList) {
			byte[] pubKey = ((EdDSAPublicKey) keyPair.getPublic()).getAbyte();
			String tempPublicKeyHex = HexUtils.bytes2Hex(pubKey);
			Key key = KeyExpansion.genEd25519Key(keyPair.getPublic());
			keyList.add(key);
			pubKey2privKeyMap.put(tempPublicKeyHex, keyPair.getPrivate());
		}
	}

	public FileID createFile(AccountID payerAccount, List<Key> waclPubKeyList, List<PrivateKey> waclPrivKeyList,
			byte[] fileContent) throws Exception {
		FileID fileIdToReturn = FileID.getDefaultInstance();
		Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));

		Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp, fileDuration);
		Duration transactionDuration = RequestBuilder.getDuration(120);
		ByteString fileData = ByteString.copyFrom(fileContent);
		SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();
		Transaction fileCreateRequest = RequestBuilder.getFileCreateBuilder(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), this.node_account_number, 0l, 0l, MAX_TX_FEE,
				timestamp, transactionDuration, true, "FileCreate", signatures, fileData, fileExp, waclPubKeyList);
		
		List<Key> keyList = new ArrayList<>();
        Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
        
        //add payer keys 
        List<KeyPair> payerKeyPair = Collections.singletonList(accountKeyPairs.get(payerAccount));
        populateKeyListAndMapFromKeyPair(payerKeyPair,keyList, pubKey2privKeyMap);
        //add wacl keys
        populateKeyListAndMapFromWacl(waclPubKeyList,waclPrivKeyList,keyList, pubKey2privKeyMap);
        Transaction signedFileCreate =TransactionSigner.signTransactionComplexWithSigMap(fileCreateRequest, keyList,
                pubKey2privKeyMap);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
		FileServiceGrpc.FileServiceBlockingStub stub = FileServiceGrpc
				.newBlockingStub(channel);
		
		TransactionResponse response = stub.createFile(signedFileCreate);
		System.out
				.println(" createFile Pre Check Response :: " + response.getNodeTransactionPrecheckCode().name());
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		TransactionBody fileCreateBody = TransactionBody.parseFrom(signedFileCreate.getBodyBytes());
		TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(fileCreateBody.getTransactionID(),
				ResponseCodeEnum.SUCCESS);
		fileIdToReturn = contractUpdateReceipt.getReceipt().getFileID();
		channel.shutdown();
		return fileIdToReturn;
	}
	
	public void appendFile(AccountID payerAccount, FileID fileToAppend, List<Key> waclPubKeyList, List<PrivateKey> waclPrivKeyList,
			byte[] fileContent, ResponseCodeEnum expectedResponse) throws Exception {
		FileID fileIdToReturn = FileID.getDefaultInstance();
		Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));

		Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp, fileDuration);
		Duration transactionDuration = RequestBuilder.getDuration(120);
		ByteString fileData = ByteString.copyFrom(fileContent);
		SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();
		Transaction fileAppendRequest = RequestBuilder.getFileAppendBuilder(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), this.node_account_number, 0l, 0l, MAX_TX_FEE,
				timestamp, transactionDuration, true, "fileAppend", signatures, fileData, fileToAppend);
		
		List<Key> keyList = new ArrayList<>();
        Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
        
        //add payer keys 
        List<KeyPair> payerKeyPair = Collections.singletonList(accountKeyPairs.get(payerAccount));
        populateKeyListAndMapFromKeyPair(payerKeyPair,keyList, pubKey2privKeyMap);
        //add wacl keys
        populateKeyListAndMapFromWacl(waclPubKeyList,waclPrivKeyList,keyList, pubKey2privKeyMap);
        Transaction signedFileAppend =TransactionSigner.signTransactionComplexWithSigMap(fileAppendRequest, keyList,
                pubKey2privKeyMap);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
		FileServiceGrpc.FileServiceBlockingStub stub = FileServiceGrpc
				.newBlockingStub(channel);
		
		TransactionResponse response = stub.appendContent(signedFileAppend);
		log.info(" appendContent Pre Check Response :: " + response.getNodeTransactionPrecheckCode().name());
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		TransactionBody fileAppendBody = TransactionBody.parseFrom(signedFileAppend.getBodyBytes());
		TransactionGetReceiptResponse contractUpdateReceipt = getReceipt(fileAppendBody.getTransactionID(),
				expectedResponse);
		
		channel.shutdown();
		
	}
}
