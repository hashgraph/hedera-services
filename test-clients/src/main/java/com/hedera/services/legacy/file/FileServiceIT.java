package com.hedera.services.legacy.file;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomProperties;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import com.hedera.services.legacy.regression.Utilities;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse.FileContents;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.client.util.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for file APIs.
 *
 * @author hua
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore
public class FileServiceIT {

	private static final long MAX_TX_FEE = TestHelper.getFileMaxFee();
	private static final int MAX_BUSY_RETRIES = 25;
	private static final int BUSY_RETRY_MS = 200;
	protected static final Logger log = LogManager.getLogger(FileServiceIT.class);
	protected static long DEFAULT_INITIAL_ACCOUNT_BALANCE = 100000000000000L;
	public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup
	public static long DAY_SEC = 24 * 60 * 60; // secs in a day
	protected static String[] files = { "1K.txt", "overview-frame.html" };
	protected static String UPLOAD_PATH = "testfiles/";
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	protected String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";


	protected static ManagedChannel channel = null;
	protected static Map<Long, List<PrivateKey>> acc2keyMap = new LinkedHashMap<>();
	protected static Map<AccountID, List<KeyPair>> account2keyMap = new HashMap<>();
	protected static FileServiceBlockingStub stub = null; // default file service stub that connects to the default
	// listening node
	protected static CryptoServiceGrpc.CryptoServiceBlockingStub cstub = null; // default crypto service stub that
	// connects to the default listening node
	protected static ByteString fileData = null;
	protected static TransactionID txId = null;
	protected static FileID fid = null;
	protected static Duration transactionDuration = Duration.newBuilder().setSeconds(TX_DURATION_SEC).build();
	public static Map<String, List<AccountKeyListObj>> hederaAccounts = null;
	protected static List<AccountKeyListObj> genesisAccountList;
	protected static List<PrivateKey> genesisPrivateKeyList = new ArrayList<>();
	protected static AccountID genesisAccountID;
	private static KeyPairObj genesisKeyPair;
	private List<PrivateKey> waclPrivKeyList;
	private List<PrivateKey> newWaclPrivKeyList;
	public static int NUM_WACL_KEYS = 5;
	protected static int WAIT_IN_SEC = 5;
	protected static AccountID senderId;
	protected static AccountID recvId;
	protected static String localPath;
	protected static String host = "localhost";
	protected boolean hostOverridden = false;
	protected static int port = 50211;
	public static AccountID defaultListeningNodeAccountID = null; // The account ID of the default listening node
	protected boolean nodeAccountOverridden = false;
	protected static int uniqueListeningPortFlag = 0; // By default, all nodes are listening on the same port, i.e.
	// 50211
	protected static long payerSeq = -1;
	protected static long recvSeq = -1;
	// A value of 1 is for production, where all nodes listen on same default port
	// A value of 0 is for development, i.e. running locally
	protected static int productionFlag = 0;
	protected static long fileDuration;

	@Before
	public void setUp() throws Exception {
		init();
		String filePath = files[0];
		localPath = UPLOAD_PATH + filePath;
		byte[] bytes = CommonUtils.readBinaryFileAsResource(localPath, getClass());
		fileData = ByteString.copyFrom(bytes);
	}

	protected void init() throws URISyntaxException, IOException {
		init(null, null);
	}

	protected void init(String overrideHost, AccountID overrideNodeAccountID)
			throws URISyntaxException, IOException {
		log.info("Starting File Service Test...");
		readAppConfig();
		if (overrideHost != null) {
			host = overrideHost;
			hostOverridden = true;
		}
		if (overrideNodeAccountID != null) {
			defaultListeningNodeAccountID = overrideNodeAccountID;
			nodeAccountOverridden = true;
		}
		readGenesisInfo();
		createStubs();
	}

	protected void readGenesisInfo() throws IOException {
		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

		// Get Genesis Account key Pair
		genesisAccountList = keyFromFile.get("START_ACCOUNT");
		genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
		getGenesisPrivateKeyList().add(genesisKeyPair.getPrivateKey());
		genesisAccountID = genesisAccountList.get(0).getAccountId();
		payerSeq = genesisAccountID.getAccountNum();
		recvId = defaultListeningNodeAccountID;
		recvSeq = defaultListeningNodeAccountID.getAccountNum();
		acc2keyMap.put(genesisAccountID.getAccountNum(), getGenesisPrivateKeyList());
	}

	protected void createStubs() throws URISyntaxException, IOException {
		if (channel != null) {
			channel.shutdownNow();
			try {
				channel.awaitTermination(10, TimeUnit.SECONDS);
			} catch (Exception e) {
			}
		}
		channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		stub = FileServiceGrpc.newBlockingStub(channel);
		cstub = CryptoServiceGrpc.newBlockingStub(channel);
	}

	protected void readAppConfig() {
		CustomProperties properties = TestHelper.getApplicationPropertiesNew();
		host = properties.getString("host", "localhost");
		port = properties.getInt("port", 50211);
		String nodeAccIDStr = properties
				.getString("defaultListeningNodeAccount", DEFAULT_NODE_ACCOUNT_ID_STR);
		defaultListeningNodeAccountID = extractAccountID(nodeAccIDStr);

		uniqueListeningPortFlag = properties.getInt("uniqueListeningPortFlag", 0);
		productionFlag = properties.getInt("productionFlag", 0);
		fileDuration = properties.getLong("FILE_DURATION", DAY_SEC * 30);
	}

	@Test
	public void test01InitAccounts() throws Exception {
		senderId = createAccount(acc2keyMap, genesisAccountID);
		payerSeq = senderId.getAccountNum();
	}

	@Test
	public void test01Transfer() {
		long transferAmt = 100l;
		Transaction transferTxSigned = getPaymentSigned(payerSeq, recvSeq, "Transfer", transferAmt);
		log.info("\n-----------------------------------");
		log.info("Transfer: request = " + transferTxSigned);
		TransactionResponse response = cstub.cryptoTransfer(transferTxSigned);
		log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(response);
	}

	@Test
	public void test02CreateFile() throws Exception {
		log.info("@@@ upload file at: " + localPath + "; file size in byte = " + fileData.size());
		log.info("host : " + host);
		log.info("node number : " + defaultListeningNodeAccountID.getAccountNum());
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp, fileDuration);
		List<Key> waclPubKeyList = new ArrayList<>();
		waclPrivKeyList = new ArrayList<>();
		genWacl(NUM_WACL_KEYS, waclPubKeyList, waclPrivKeyList);

		// fetching private key of payer account
		List<PrivateKey> privKeys = getPayerPrivateKey(payerSeq);

		long nodeAccountNumber;
		if (nodeAccountOverridden) {
			nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
		} else {
			nodeAccountNumber = Utilities.getDefaultNodeAccount();
		}
		Transaction FileCreateRequest = RequestBuilder
				.getFileCreateBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
						timestamp, transactionDuration, true, "FileCreate", fileData, fileExp,
						waclPubKeyList);
		TransactionBody body = TransactionBody.parseFrom(FileCreateRequest.getBodyBytes());
		txId = body.getTransactionID();
		Transaction filesignedByPayer = TransactionSigner.signTransaction(FileCreateRequest, privKeys);

		// append wacl sigs
		Transaction filesigned = TransactionSigner.signTransaction(filesignedByPayer, waclPrivKeyList, true);
		log.info("\n-----------------------------------");
		log.info("FileCreate: request = " + filesigned);

		TransactionResponse response = null;
		for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
			response = stub.createFile(filesigned);
			log.info("FileCreate Response :: " + response.getNodeTransactionPrecheckCode().name());
			if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
				break;
			}
			Assert.assertEquals(ResponseCodeEnum.BUSY, response.getNodeTransactionPrecheckCode());
			Thread.sleep(BUSY_RETRY_MS);
		}
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
	}

	/**
	 * Generates wacl keys and add them to public key and private key lists.
	 *
	 * @param numKeys
	 * 		number of keys to generate
	 * @param waclPubKeyList
	 * 		list for storing generated public keys
	 * @param waclPrivKeyList
	 * 		list for storing generated private keys
	 */
	public void genWacl(int numKeys, List<Key> waclPubKeyList, List<PrivateKey> waclPrivKeyList) {
		for (int i = 0; i < numKeys; i++) {
			KeyPair pair = new KeyPairGenerator().generateKeyPair();
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			waclPubKeyList.add(waclKey);
			waclPrivKeyList.add(pair.getPrivate());
		}
	}

	/**
	 * Gets the payer private keys for signing
	 *
	 * @param payerSeqNum
	 * 		payer account number
	 * @return list of payer private keys
	 */
	protected static List<PrivateKey> getPayerPrivateKey(long payerSeqNum) {
		AccountID accountID = RequestBuilder.getAccountIdBuild(payerSeqNum, 0l, 0l);
		List<PrivateKey> privKey = getAccountPrivateKeys(accountID);

		return privKey;
	}

	@Test
	public void test03GetTxReceipt() throws Exception {
		Query query = Query.newBuilder()
				.setTransactionGetReceipt(
						RequestBuilder.getTransactionGetReceiptQuery(txId, ResponseType.ANSWER_ONLY))
				.build();
		log.info("\n-----------------------------------");
		Response transactionReceipts = fetchReceipts(query, cstub);
		fid = transactionReceipts.getTransactionGetReceipt().getReceipt().getFileID();
		log.info("GetTxReceipt: file ID = " + fid);
		Assert.assertNotNull(fid);
		Assert.assertNotEquals(0, fid.getFileNum());
	}

	@Test
	public void test04GetTxRecord() {
		long feeForTxRecordCost = FeeClient.getCostForGettingTxRecord();
		Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetRecordCost",
				feeForTxRecordCost);
		Query query = RequestBuilder
				.getTransactionGetRecordQuery(txId, paymentTxSigned, ResponseType.COST_ANSWER);

		log.info("\n-----------------------------------");
		log.info("FileGetRecordCost: query = " + query);

		CommonUtils.nap(3);
		Response recordResp = cstub.getTxRecordByTxID(query);
		long feeForTxRecord = recordResp.getTransactionGetRecord().getHeader().getCost();
		paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetRecord", feeForTxRecord);
		query = RequestBuilder
				.getTransactionGetRecordQuery(txId, paymentTxSigned, ResponseType.ANSWER_ONLY);

		log.info("FileGetRecord: query = " + query);
		recordResp = cstub.getTxRecordByTxID(query);
		TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
		log.info("FileGetRecord: tx record = " + txRecord);
		FileID actualFid = txRecord.getReceipt().getFileID();
		System.out.println(actualFid);
		System.out.println(fid + ":: is the fid");
		Assert.assertEquals(fid, actualFid);
	}

	@Test
	public void test05GetFileInfo() {
		long feeForFileInfoCost = TestHelper.getFileMaxFee();
		Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "fileGetInfoQueryCost",
				feeForFileInfoCost);
		Query query = RequestBuilder
				.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
		log.info("\n-----------------------------------");
		log.info("fileGetInfoQuery: query = " + query);
		Response fileInfoResp = stub.getFileInfo(query);
		Assert.assertEquals(ResponseCodeEnum.OK,
				fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());

		long feeForFileInfo = fileInfoResp.getFileGetInfo().getHeader().getCost();
		paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "fileGetInfoQuery", feeForFileInfo);
		query = RequestBuilder.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
		fileInfoResp = stub.getFileInfo(query);
		Assert.assertEquals(ResponseCodeEnum.OK,
				fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());

		FileInfo fileInfo = fileInfoResp.getFileGetInfo().getFileInfo();
		log.info("fileGetInfoQuery: info = " + fileInfo);
		FileID actualFid = fileInfo.getFileID();
		log.info("File Info deleted  response: " + fileInfo.getDeleted());
		Assert.assertEquals(fid, actualFid);
	}

	@Test
	public void test06GetFileContent() {
		long fee = FeeClient.getFeeByID(HederaFunctionality.FileGetContents);
		Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetContentCost", fee);
		Query query = RequestBuilder
				.getFileGetContentBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
		log.info("\n-----------------------------------");
		log.info("FileGetContentCost: query = " + query);

		Response fileContentResp = stub.getFileContent(query);

		fee = fileContentResp.getFileGetContents().getHeader().getCost();
		paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "FileGetContent", fee);
		query = RequestBuilder.getFileGetContentBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
		fileContentResp = stub.getFileContent(query);
		FileContents fileContent = fileContentResp.getFileGetContents().getFileContents();
		ByteString actualFileData = fileContent.getContents();
		log.info("FileGetContent: content = " + fileContent + "; file size = " + actualFileData.size());
		Assert.assertEquals(fileData, actualFileData);
	}

	@Test
	public void test07AppendFile() throws Exception {
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();

		long nodeAccountNumber;
		if (nodeAccountOverridden) {
			nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
		} else {
			nodeAccountNumber = Utilities.getDefaultNodeAccount();
		}
		Transaction fileAppendRequest = RequestBuilder
				.getFileAppendBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
						timestamp, transactionDuration, true, "FileAppend", fileData, fid);

		TransactionBody body = TransactionBody.parseFrom(fileAppendRequest.getBodyBytes());
		txId = body.getTransactionID();
		List<PrivateKey> privKey = getPayerPrivateKey(payerSeq);
		Transaction txSignedByPayer = TransactionSigner.signTransaction(fileAppendRequest, privKey);
		Transaction txSigned = TransactionSigner.signTransaction(txSignedByPayer, waclPrivKeyList, true);
		log.info("\n-----------------------------------");
		log.info("FileAppend: request = " + txSigned);

		TransactionResponse response = null;
		for (int i = 0; i < MAX_BUSY_RETRIES + 1; i++) {
			response = stub.appendContent(txSigned);
			log.info("FileAppend: Response = " + response.getNodeTransactionPrecheckCode().name());
			if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
				break;
			}
			Assert.assertEquals(ResponseCodeEnum.BUSY, response.getNodeTransactionPrecheckCode());
			Thread.sleep(BUSY_RETRY_MS);
		}
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void test08UpdateFile() {
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC * 10);
		List<Key> newWaclPubKeyList = new ArrayList<>();
		newWaclPrivKeyList = new ArrayList<>();
		genWacl(NUM_WACL_KEYS, newWaclPubKeyList, newWaclPrivKeyList);
		KeyList wacl = KeyList.newBuilder().addAllKeys(newWaclPubKeyList).build();

		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		long nodeAccountNumber;
		if (nodeAccountOverridden) {
			nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
		} else {
			nodeAccountNumber = Utilities.getDefaultNodeAccount();
		}
		Transaction FileUpdateRequest = RequestBuilder
				.getFileUpdateBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
						timestamp, fileExp, transactionDuration, true, "FileUpdate", fileData, fid,
						wacl);

		List<PrivateKey> privKey = getPayerPrivateKey(payerSeq);
		Transaction txSignedByPayer = TransactionSigner
				.signTransaction(FileUpdateRequest, privKey); // sign with payer keys
		Transaction txSignedByCreationWacl = TransactionSigner.signTransaction(txSignedByPayer,
				waclPrivKeyList, true); // sign with creation wacl keys
		Transaction txSigned = TransactionSigner.signTransaction(txSignedByCreationWacl,
				newWaclPrivKeyList, true); // sign with new wacl keys

		log.info("\n-----------------------------------");
		log.info(
				"FileUpdate: input data = " + fileData + "\nexpirationTime = " + fileExp + "\nWACL keys = "
						+ newWaclPubKeyList);
		log.info("FileUpdate: request = " + txSigned);

		TransactionResponse response = stub.updateFile(txSigned);
		log.info("FileUpdate with data, exp, and wacl respectively, Response :: "
				+ response.getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void test09DeleteFile() {
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		long nodeAccountNumber;
		if (nodeAccountOverridden) {
			nodeAccountNumber = defaultListeningNodeAccountID.getAccountNum();
		} else {
			nodeAccountNumber = Utilities.getDefaultNodeAccount();
		}
		Transaction FileDeleteRequest = RequestBuilder
				.getFileDeleteBuilder(payerSeq, 0l, 0l, nodeAccountNumber, 0l, 0l, MAX_TX_FEE,
						timestamp, transactionDuration, true, "FileDelete", fid);
		List<PrivateKey> privKey = getPayerPrivateKey(payerSeq);
		Transaction txSignedByPayer = TransactionSigner.signTransaction(FileDeleteRequest, privKey);
		Transaction txSigned = TransactionSigner.signTransaction(txSignedByPayer, newWaclPrivKeyList, true);
		log.info("\n-----------------------------------");
		log.info("FileDelete: request = " + txSigned);

		TransactionResponse response = stub.deleteFile(txSigned);
		log.info("FileDelete Response :: " + response.getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
	}

	@Test
	public void test10GetFileInfoAfterDelete() {
		long feeForFileInfoCost = FeeClient.getFeeByID(HederaFunctionality.FileGetInfo);
		Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "fileGetInfoQueryCost",
				feeForFileInfoCost);
		Query query = RequestBuilder
				.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
		log.info("\n-----------------------------------");
		log.info("fileGetInfoQuery: query = " + query);
		Response fileInfoResp = stub.getFileInfo(query);

		log.info(fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());
		Assert.assertEquals(ResponseCodeEnum.OK,
				fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());
	}

	@After
	public void cleanUp() {
		log.info("Finished File Service Test. Goodbye!");
		channel.shutdown();
	}

	/**
	 * Gets the account info for a given account id.
	 *
	 * @param accountID
	 * 		account id to get the info for
	 * @param fromAccountNum
	 * 		payer account number for query payment transaction
	 * @param toAccountNum
	 * 		receiver node account number of query payment transaction
	 * @return queried account info for given account
	 */
	public AccountInfo getAccountInfo(AccountID accountID, long fromAccountNum, long toAccountNum) {
		// get the cost for getting account info
		long queryCostAcctInfo = FeeClient.getCostForGettingAccountInfo();
		Transaction paymentTxSigned = getPaymentSigned(fromAccountNum, toAccountNum,
				"getCostCryptoGetAccountInfo", queryCostAcctInfo);
		Query cryptoCostGetInfoQuery = RequestBuilder
				.getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.COST_ANSWER);
		Response getCostInfoResponse = cstub.getAccountInfo(cryptoCostGetInfoQuery);
		long queryGetAcctInfoFee = getCostInfoResponse.getCryptoGetInfo().getHeader().getCost();

		paymentTxSigned = getPaymentSigned(fromAccountNum, toAccountNum, "getCryptoGetAccountInfo",
				queryGetAcctInfoFee);
		Query cryptoGetInfoQuery = RequestBuilder
				.getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
		Response getInfoResponse = cstub.getAccountInfo(cryptoGetInfoQuery);
		log.info("Pre Check Response of getAccountInfo:: "
				+ getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(getInfoResponse);
		Assert.assertNotNull(getInfoResponse.getCryptoGetInfo());
		log.info("getInfoResponse :: " + getInfoResponse.getCryptoGetInfo());

		AccountInfo accInfo = getInfoResponse.getCryptoGetInfo().getAccountInfo();
		return accInfo;
	}

	/**
	 * Gets account info for an account ID given the payer account id and node account id
	 *
	 * @param accountID
	 * 		the account id to get info for
	 * @param payerID
	 * 		payer account ID for query payment transaction
	 * @param nodeID
	 * 		receiver node account ID of query payment transaction
	 * @return account info queried for the given account id
	 */
	public AccountInfo getAccountInfo(AccountID accountID, AccountID payerID, AccountID nodeID) {
		return getAccountInfo(accountID, payerID.getAccountNum(), nodeID.getAccountNum());
	}

	/**
	 * Gets account info using genesis as payer and default listening node as receiving node.
	 *
	 * @param accountID
	 * 		the account id to get info for
	 * @return account info queried of the given account
	 */
	public AccountInfo getAccountInfo(AccountID accountID) {
		return getAccountInfo(accountID, genesisAccountID.getAccountNum(),
				defaultListeningNodeAccountID.getAccountNum());
	}

	/**
	 * Fetches the receipts, wait if necessary.
	 *
	 * @param query
	 * 		query to get receipt for
	 * @param cstub2
	 * 		CryptoServiceBlockingStub blocking-style stub that supports unary and streaming output calls on the crypto
	 * 		service
	 * @return the getTransactionReceipt response
	 * @throws InvalidNodeTransactionPrecheckCode
	 * 		indicates there is a failure while querying transaction receipt if pre-check code is not OK or BUSY
	 */
	protected static Response fetchReceipts(Query query,
			CryptoServiceBlockingStub cstub2) throws InvalidNodeTransactionPrecheckCode {
		return TestHelper.fetchReceipts(query, cstub2, log, host);
	}

	/**
	 * Creates a crypto account.
	 *
	 * @param account2keyMap
	 * 		maps from account id to corresponding key pair
	 * @param payerAccount
	 * 		payer account id
	 * @return account id of the created crypto account
	 * @throws InvalidProtocolBufferException
	 * 		indicates that the transaction body for creating account could not be parsed
	 * @throws InvalidNodeTransactionPrecheckCode
	 * 		indicates there is a failure while querying transaction receipt if pre-check code is not OK or BUSY
	 */
	public AccountID createAccount(Map<Long, List<PrivateKey>> account2keyMap, AccountID payerAccount)
			throws InvalidProtocolBufferException, InvalidNodeTransactionPrecheckCode {
		KeyPair pair = new KeyPairGenerator().generateKeyPair();
		Transaction createAccountRequest = TestHelper
				.createAccountWithFee(payerAccount, defaultListeningNodeAccountID, pair,
						DEFAULT_INITIAL_ACCOUNT_BALANCE, getGenesisPrivateKeyList());

		log.info("\n-----------------------------------");
		log.info("createAccount: request = " + createAccountRequest);
		TransactionResponse response = cstub.createAccount(createAccountRequest);
		log.info("createAccount Response :: " + response.getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(response);

		// get transaction receipt
		log.info("preparing to getTransactionReceipts....");
		TransactionBody body = TransactionBody.parseFrom(createAccountRequest.getBodyBytes());
		TransactionID transactionID = body.getTransactionID();
		Query query = Query.newBuilder().setTransactionGetReceipt(
				RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
				.build();
		Response transactionReceipts = fetchReceipts(query, cstub);
		AccountID accountID = transactionReceipts.getTransactionGetReceipt().getReceipt()
				.getAccountID();
		List<PrivateKey> pKeys = new ArrayList<>();
		pKeys.add(pair.getPrivate());
		account2keyMap.put(accountID.getAccountNum(), pKeys);
		log.info("Account created: account num :: " + accountID.getAccountNum());

		// get account info
		CommonUtils.nap(WAIT_IN_SEC);
		AccountInfo accInfo = getAccountInfo(accountID);
		log.info("Created account info = " + accInfo);

		Assert.assertEquals(body.getCryptoCreateAccount().getInitialBalance(),
				accInfo.getBalance());

		return accountID;
	}

	/**
	 * Gets account info for given account id
	 *
	 * @param stub
	 * 		CryptoServiceBlockingStub blocking-style stub that supports unary and streaming output calls on the crypto
	 * 		service
	 * @param accountID
	 * 		account for which account info needs to be queried
	 * @return response of the getAccountInfo query
	 */
	private static Response getCryptoGetAccountInfo(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			AccountID accountID) {
		// first get the cost for getting AccountInfo
		long queryCostGetInfo = FeeClient.getCostForGettingAccountInfo();
		Transaction paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "getCostCryptoGetAccountInfo",
				queryCostGetInfo);
		Query cryptoGetCostInfoQuery = RequestBuilder
				.getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.COST_ANSWER);
		Response acctInfo = stub.getAccountInfo(cryptoGetCostInfoQuery);

		long transactionFee = acctInfo.getCryptoGetInfo().getHeader().getCost();
		paymentTxSigned = getPaymentSigned(payerSeq, recvSeq, "getCryptoGetAccountInfo",
				transactionFee);
		Query cryptoGetInfoQuery = RequestBuilder
				.getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
		return stub.getAccountInfo(cryptoGetInfoQuery);
	}

	/**
	 * Gets the account balance for given account id
	 *
	 * @param stub
	 * 		CryptoServiceBlockingStub blocking-style stub that supports unary and streaming output calls on the crypto
	 * 		service
	 * @param accountID
	 * 		account for which accountBalance need to be queried
	 * @return queried balance of the given account
	 */
	protected static long getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			AccountID accountID) {
		Response response = getCryptoGetAccountInfo(stub, accountID);
		long balance = response.getCryptoGetInfo().getAccountInfo().getBalance();
		return balance;
	}

	public static void main(String[] args) throws Exception {
		FileServiceIT tester = new FileServiceIT();
		tester.setUp();
		tester.test01InitAccounts();
		CommonUtils.nap(WAIT_IN_SEC);
		tester.test02CreateFile();
		CommonUtils.nap(WAIT_IN_SEC);
		tester.test03GetTxReceipt();
		tester.test04GetTxRecord();
		tester.test05GetFileInfo();
		tester.test06GetFileContent();
		tester.test07AppendFile();
		CommonUtils.nap(WAIT_IN_SEC);
		tester.test05GetFileInfo();
		tester.test08UpdateFile();
		CommonUtils.nap(WAIT_IN_SEC);
		tester.test05GetFileInfo();
		tester.test09DeleteFile();
		CommonUtils.nap(WAIT_IN_SEC);
		CommonUtils.nap(WAIT_IN_SEC);
		tester.test10GetFileInfoAfterDelete();
	}

	public List<PrivateKey> getGenesisPrivateKeyList() {
		return genesisPrivateKeyList;
	}

	/**
	 * Extract account ID from memo string, e.g. "0.0.5".
	 *
	 * @param memo
	 * 		given memo string from which accountID need to be extracted
	 * @return account ID extracted from memo
	 */
	protected AccountID extractAccountID(String memo) {
		AccountID rv = null;
		String[] parts = memo.split("\\.");
		rv = AccountID.newBuilder().setShardNum(Long.parseLong(parts[0]))
				.setRealmNum(Long.parseLong(parts[1])).setAccountNum(Long.parseLong(parts[2])).build();
		return rv;
	}

	/**
	 * Creates a signed transfer tx, used both for a CryptoTransfer transaction or a Query payment.
	 *
	 * @param payerID
	 * 		payer account ID, as the payer of the tx and the from account for the transfer
	 * @param nodeID
	 * 		node account ID, as the node account that should process the tx
	 * @param fromID
	 * 		sender account id
	 * @param toID
	 * 		to account for the transfer.
	 * @param memo
	 * 		memo for the transaction
	 * @param transferAmount
	 * 		amount to be transferred
	 * @return the signed payment transaction
	 */
	protected static Transaction getPaymentSigned(AccountID payerID, AccountID nodeID,
			AccountID fromID, AccountID toID, String memo, long transferAmount) {
		List<PrivateKey> payerPrivKeys = getPayerPrivateKey(payerID.getAccountNum());
		List<PrivateKey> fromPrivKeys = getPayerPrivateKey(fromID.getAccountNum());
		Transaction paymentTxSigned = getSignedTransferTx(payerID, nodeID, fromID, toID,
				transferAmount, payerPrivKeys, fromPrivKeys, memo);
		return paymentTxSigned;
	}

	/**
	 * Creates a signed Query payment tx using default listening node as the processing node and payer
	 * as the from account.
	 *
	 * @param payerSeq
	 * 		payer account number, as the payer and the from account
	 * @param toSeq
	 * 		node account number, as the node account and the to account
	 * @param memo
	 * 		memo for the transaction
	 * @param transferFeeAmt
	 * 		fee amount to be transferred for payment transaction
	 * @return the signed payment transaction
	 */
	protected static Transaction getPaymentSigned(long payerSeq, long toSeq, String memo,
			long transferFeeAmt) {
		AccountID payerAccountID = AccountID.newBuilder().setAccountNum(payerSeq).setRealmNum(0)
				.setShardNum(0).build();
		AccountID toID = AccountID.newBuilder().setAccountNum(toSeq).setRealmNum(0).setShardNum(0)
				.build();
		return getPaymentSigned(payerAccountID, defaultListeningNodeAccountID, payerAccountID, toID,
				memo, transferFeeAmt);
	}

	/**
	 * Creates a signed transfer tx.
	 *
	 * @param payerAccountID
	 * 		payer account id, as the payer of the tx
	 * @param nodeAccountID
	 * 		node account id, as the node account that should process the tx
	 * @param fromAccountID
	 * 		sender account id
	 * @param toAccountID
	 * 		receiver account id
	 * @param amount
	 * 		amount to be transferred
	 * @param payerPrivKeys
	 * 		list of payer private key
	 * @param fromPrivKeys
	 * 		list of sender private key
	 * @param memo
	 * 		memo of transaction
	 * @return signed transfer transaction
	 */
	protected static Transaction getSignedTransferTx(AccountID payerAccountID,
			AccountID nodeAccountID,
			AccountID fromAccountID, AccountID toAccountID, long amount, List<PrivateKey> payerPrivKeys,
			List<PrivateKey> fromPrivKeys, String memo) {
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Transaction paymentTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
				payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
				nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), TestHelper.getCryptoMaxFee(), timestamp,
				transactionDuration, true,
				memo, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
				amount);
		List<PrivateKey> privKeysList = new ArrayList<>();
		privKeysList.addAll(payerPrivKeys);
		privKeysList.addAll(fromPrivKeys);
		Transaction paymentTxSigned = TransactionSigner.signTransaction(paymentTx, privKeysList);

		long transferFee = TestHelper.getCryptoMaxFee();

		paymentTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
				payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
				nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), transferFee, timestamp,
				transactionDuration, true,
				memo, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
				amount);
		paymentTxSigned = TransactionSigner.signTransaction(paymentTx, privKeysList);

		return paymentTxSigned;
	}

	/**
	 * Gets the account key pairs.
	 *
	 * @param accountID
	 * 		given account id to get key pairs for
	 * @return key pairs of the given account id
	 */
	protected static List<KeyPair> getAccountKeyPairs(AccountID accountID) {
		List<KeyPair> keypairs;
		keypairs = account2keyMap.get(accountID);
		return keypairs;
	}

	/**
	 * Gets the account private keys.
	 *
	 * @param accountID
	 * 		given account id to get private keys for
	 * @return private keys of the given account
	 */
	public static List<PrivateKey> getAccountPrivateKeys(AccountID accountID) {
		List<PrivateKey> rv = new ArrayList<>();
		long seqNum = accountID.getAccountNum();
		if (acc2keyMap.containsKey(seqNum)) {
			rv = acc2keyMap.get(seqNum);
		} else {
			List<KeyPair> keypairs = getAccountKeyPairs(accountID);
			for (int i = 0; i < keypairs.size(); i++) {
				rv.add(keypairs.get(i).getPrivate());
			}
		}
		return rv;
	}
}
