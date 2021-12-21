package com.hedera.services.legacy.regression.umbrella;

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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.legacy.client.util.KeyExpansion;
import com.hedera.services.legacy.client.util.TransactionSigner;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.CustomProperties;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse.FileContents;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.KeyList.Builder;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import static com.hedera.services.legacy.client.util.KeyExpansion.keyFromBytes;

/**
 * Adds crypto related functions for regression tests.
 */
public class CryptoServiceTest extends TestHelperComplex {

	private static final Logger log = LogManager.getLogger(CryptoServiceTest.class);
	protected static String testConfigFilePath = "src/main/resource/umbrellaTest.properties";
	protected static String[] fileTypes = { "txt", "jpg", "pdf", "bin" };
	protected static int[] fileSizesK = { 1, 2, 3, 4, 5 };
	public static double MAX_REQUESTS_IN_K = 1;
	protected static boolean isRandomSubmission = false;
	protected static boolean isRandomPayer = true;
	protected static int numPayerAccounts = 2;
	protected static AccountID[] payerAccounts = null; // payer accounts, size determined by numCryptoAccounts
	protected static boolean isRandomTransferAccount = true;
	protected static int numTransferAccounts = 2;
	protected static AccountID[] transferAccounts = null;// accounts for transfer's from and to parties, size
	// determined by numTransferAccounts
	public static int K = 1000;
	protected static AccountID[] nodeAccounts = null;
	protected static Set<AccountID> accountsBeingUpdated = new HashSet<>();
	protected static Random rand = new Random();
	protected static Map<AccountID, NodeAddress> nodeID2Ip = new HashMap<>();
	protected static Map<AccountID, FileServiceBlockingStub> nodeID2Stub = new HashMap<>();
	protected static int MAX_TRANSFER_AMOUNT = 100;
	protected int COMPLEX_KEY_SIZE = 3;
	protected int COMPLEX_KEY_THRESHOLD = 2;
	protected static String[] accountKeyTypes = null;
	protected static String[] signatureFormat = null;
	public static String CONFIG_LIST_SEPARATOR = ",\\s*";
	protected static boolean receiverSigRequired = true;
	protected static Set<AccountID> recvSigRequiredAccounts = new HashSet<>();
	protected static boolean getReceipt = true; //flag whether or not to get receipts after a transfer transaction
	protected static boolean useSystemAccountAsPayer = false; //flag whether or not to use system accounts (account
	// number under 100 excluding genesis and node accounts).
	protected static int MAX_BUSY_RETRIES = 10;
	protected static int BUSY_RETRY_MS = 200;

	protected static enum SUPPORTED_KEY_TYPES {
		single, keylist, thresholdKey
	}

	protected static long DEFAULT_INITIAL_ACCOUNT_BALANCE = getUmbrellaProperties().getLong("initialAccountBalance",
			2000_00_000_000L);
	protected static long SMALL_ACCOUNT_BALANCE_FACTOR = getUmbrellaProperties().getLong("smallAccountBalanceFactor",
			2000L);
	public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup
	public static long DAY_SEC = 24 * 60 * 60; // secs in a day
	protected static String[] files = { "1K.txt", "overview-frame.html" };
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	protected String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";

	protected static ManagedChannel channel = null;
	protected static int transactionMaxBytes = Integer
			.parseInt(getApplicationProperties().getProperty("transactionMaxBytes", "8192"));//8192;
	protected static long specialAccountNum = Long
			.parseLong(getApplicationProperties().getProperty("specialAccountNum", "50"));
	protected static long genesisAccountNum = Long
			.parseLong(getApplicationProperties().getProperty("genesisAccountNum", "2"));
	protected long accountDuration;
	protected long fileDuration;
	protected long contractDuration;
	protected PrivateKey genesisPrivateKey;
	protected KeyPair genKeyPair;
	/**
	 * // default file service stub that connects to the default listening node
	 */
	protected static FileServiceBlockingStub stub = null;

	/**
	 * default crypto service stub that connects to the default listening node
	 */
	protected static CryptoServiceGrpc.CryptoServiceBlockingStub cstub = null;

	protected static Duration transactionDuration = Duration.newBuilder().setSeconds(TX_DURATION_SEC)
			.build();
	public static Map<String, List<AccountKeyListObj>> hederaAccounts = null;
	protected static List<AccountKeyListObj> genesisAccountList;
	protected static AccountID genesisAccountID;
	public static int NUM_WACL_KEYS = 1;
	protected static int WAIT_IN_SEC = 1;
	protected static String host = "localhost";
	protected static int port = 50211;

	/**
	 * The account ID of the default listening node
	 */
	public static AccountID defaultListeningNodeAccountID = null;

	/**
	 * By default, all nodes are listening on the same port, i.e. 50211
	 */
	protected static int uniqueListeningPortFlag = 0;

	/**
	 * A value of 1 is for production, where all nodes listen on same default port A value of 0 is for
	 * development, i.e. running locally
	 */
	protected static int productionFlag = 0;

	/**
	 * remember the start up genesis key in the case the genesis key is updated and want to revert
	 * back
	 */
	protected static Key startUpKey = null;

	/**
	 * flag whether change the genesis key
	 */
	protected boolean changeGenesisKey = false;

	/**
	 * maintains the live transaction ids, which are removed when they expire
	 */
	protected TransactionIDCache cache = null;

	protected static long QUERY_PAYMENT_AMOUNT = TestHelper.getCryptoMaxFee();

	/**
	 * Flag for creating a new channel and stub for single use. Destroyed after the use.
	 */
	protected boolean isOneUseChannel = true;

	public CryptoServiceTest(String testConfigFilePath) {
		CryptoServiceTest.testConfigFilePath = testConfigFilePath;
	}

	public CryptoServiceTest() {
	}

	public void setUp() throws Throwable {
		setUp(null, null);
	}

	public void setUp(String host, int port, long nodeAccount, long retryFreq,
			boolean isExponentialBackoff) throws Throwable {
		CryptoServiceTest.host = host;
		CryptoServiceTest.port = port;
		defaultListeningNodeAccountID = AccountID.newBuilder().setAccountNum(nodeAccount).build();
		RETRY_FREQ_MILLIS = retryFreq;
		MAX_RECEIPT_RETRIES = (int) (180000 / RETRY_FREQ_MILLIS) + 1; // total receipt time is 3 minutes
		TestHelper.isExponentialBackoff = isExponentialBackoff;

		getTestConfig();
		readGenesisInfo();
		createStubs();
		cache = TransactionIDCache
				.getInstance(TransactionIDCache.txReceiptTTL, TransactionIDCache.txRecordTTL);
		nodeID2Stub.put(defaultListeningNodeAccountID, stub);
		nodeAccounts = new AccountID[1];
		nodeAccounts[0] = defaultListeningNodeAccountID;
	}

	public void setUp(String host, Long nodeAccount) throws Throwable {
		readAppConfig(host, nodeAccount);
		getTestConfig();
		readGenesisInfo();
		createStubs();
		cache = TransactionIDCache
				.getInstance(TransactionIDCache.txReceiptTTL, TransactionIDCache.txRecordTTL);
		nodeID2Stub.put(defaultListeningNodeAccountID, stub);
		nodeAccounts = new AccountID[1];
		nodeAccounts[0] = defaultListeningNodeAccountID;
	}

	public static CustomProperties getUmbrellaProperties() {
		File checkFile = new File(testConfigFilePath);
		if (checkFile.exists()) {
			return new CustomProperties(testConfigFilePath, false);
		} else {
			String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
			return new CustomProperties(rootPath + "umbrellaTest.properties", false);
		}
	}

	/**
	 * Gets the test config for the tests
	 */
	protected void getTestConfig() {
		CustomProperties testProps = getUmbrellaProperties();
		isRandomPayer = Boolean.parseBoolean(testProps.getString("isRandomPayer", "false"));
		isRandomTransferAccount = Boolean
				.parseBoolean(testProps.getString("isRandomTransferAccount", "false"));
		isRandomSubmission = Boolean.parseBoolean(testProps.getString("isRandomSubmission", "false"));
		fileTypes = testProps.getString("fileTypes", "txt,jpg,pdf,bin").split(CONFIG_LIST_SEPARATOR);
		String[] fileSizesKStr = testProps.getString("fileSizesK", "1,10,100,1000")
				.split(CONFIG_LIST_SEPARATOR);
		fileSizesK = parseIntArray(fileSizesKStr);
		MAX_REQUESTS_IN_K = Double.parseDouble(testProps.getString("maxRequestsInK", "1"));
		MAX_TRANSFER_AMOUNT = testProps.getInt("maxTransferAmount", 100);
		numPayerAccounts = testProps.getInt("numCryptoAccounts", 10);
		numTransferAccounts = testProps.getInt("numTransferAccounts", 2);
		accountKeyTypes = testProps.getString("accountKeyType", "single")
				.split(CONFIG_LIST_SEPARATOR);
		changeGenesisKey = Boolean.parseBoolean(testProps.getString("changeGenesisKey", "false"));
		NUM_WACL_KEYS = testProps.getInt("numWaclKeys", 1);

		getReceipt = Boolean.parseBoolean(testProps.getString("getReceipt", "true"));
		SMALL_ACCOUNT_BALANCE_FACTOR = testProps.getLong("smallAccountBalanceFactor", 100000L);
		useSystemAccountAsPayer = Boolean
				.parseBoolean(testProps.getString("useSystemAccountAsPayer", "false"));
		port = testProps.getInt("port", 50211);
		System.out.println("port:" + port);
		this.MAX_BUSY_RETRIES = testProps.getInt("maxBusyRetry", 10);
		this.BUSY_RETRY_MS = testProps.getInt("busyRetrySleep", 50211);
		Properties applicationProperties = getApplicationProperties();
		specialAccountNum = Long
				.parseLong(applicationProperties.getProperty("specialAccountNum", "55"));
		genesisAccountNum = Long
				.parseLong(applicationProperties.getProperty("genesisAccountNum", "2"));
		accountDuration = Long
				.parseLong(applicationProperties.getProperty("ACCOUNT_DURATION", "7890000"));
		fileDuration = Long
				.parseLong(applicationProperties.getProperty("FILE_DURATION", "7890000"));
		contractDuration = Long
				.parseLong(applicationProperties.getProperty("CONTRACT_DURATION", "7890000"));
	}

	/**
	 * Gets a non-negative random amount for transfers.
	 *
	 * @return random amount for transfers
	 */
	public static long getRandomTransferAmount() {
		long rv = rand.nextInt(MAX_TRANSFER_AMOUNT - 1) + 1;
		return rv;
	}

	/**
	 * Gets transaction records by account ID.
	 *
	 * @param accountID
	 * 		account id to get transaction records for
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @return list of transaction records of the given account Id
	 * @throws Throwable
	 * 		indicates failure while getting transaction record
	 */
	public List<TransactionRecord> getTransactionRecordsByAccountId(AccountID accountID,
			AccountID payerAccount, AccountID nodeAccountID) throws Throwable {
		log.info("Get Tx records by account Id...");
		long fee = getAccountRecordsCostFee(accountID, payerAccount.getAccountNum(), nodeAccountID.getAccountNum());
		Query query = TestHelperComplex
				.getTxRecordByAccountIdComplex(accountID, payerAccount, nodeAccountID, fee,
						ResponseType.ANSWER_ONLY);
		Response transactionRecord = retryLoopQuery(query, "getAccountRecords");
		Assertions.assertEquals(ResponseCodeEnum.OK,
				transactionRecord.getCryptoGetAccountRecords().getHeader()
						.getNodeTransactionPrecheckCode());
		Assertions.assertNotNull(transactionRecord.getCryptoGetAccountRecords());
		Assertions.assertEquals(accountID, transactionRecord.getCryptoGetAccountRecords().getAccountID());
		List<TransactionRecord> recordList = transactionRecord.getCryptoGetAccountRecords()
				.getRecordsList();
		log.info(
				"Tx Records List for account ID " + accountID.getAccountNum() + " :: " + recordList.size());
		log.info("--------------------------------------");
		return recordList;
	}

	/**
	 * Updates an account's autoRenew duration.
	 *
	 * @param accountID
	 * 		account id of the account to be updated
	 * @param payerAccountID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @return account info after update
	 * @throws Throwable
	 * 		indicates failure while signing transaction with sigMap
	 */
	public AccountInfo updateAccount(AccountID accountID, AccountID payerAccountID,
			AccountID nodeAccountID)
			throws Throwable {
		Duration autoRenew = RequestBuilder
				.getDuration(CustomPropertiesSingleton.getInstance().getUpdateDurationValue());

		Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
		Key accKey = acc2ComplexKeyMap.get(accountID);
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		if (accountID.getAccountNum() != 55) {
			keys.add(accKey);
		}
		Transaction updateTx = TestHelperComplex.updateAccount(accountID, payerAccountID,
				nodeAccountID, autoRenew);
		Transaction signUpdate = TransactionSigner
				.signTransactionComplexWithSigMap(updateTx, keys, pubKey2privKeyMap);

		log.info("\n-----------------------------------\nupdateAccount: request = " + signUpdate);
		TransactionResponse response = retryLoopTransaction(signUpdate, "updateAccount");
		Assertions.assertNotNull(response);
		Assertions.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(signUpdate);
		TransactionID transactionID = body.getTransactionID();
		cache.addTransactionID(transactionID);

		AccountInfo accInfo = null;
		if (getReceipt) {
			TransactionReceipt fastRecord = getTxFastRecord(transactionID);
			Assertions.assertNotNull(fastRecord);

			accInfo = getAccountInfo(accountID, payerAccountID, nodeAccountID);
			Assertions.assertNotNull(accInfo);
			log.info(accInfo);
			Assertions.assertEquals(autoRenew, accInfo.getAutoRenewPeriod());
			log.info("updating successful" + "\n");
		}

		return accInfo;
	}

	/**
	 * Creates an account.
	 *
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @param cacheTxID
	 * 		if transaction id need to be cached
	 * @return the account ID of the newly created account
	 * @throws Throwable
	 * 		indicates failure while creating complex account
	 */
	public AccountID createAccount(AccountID payerAccount, AccountID nodeAccountID, boolean cacheTxID)
			throws Throwable {
		String accountKeyType = getRandomAccountKeyType();
		return createAccountComplex(cstub, payerAccount, nodeAccountID, accountKeyType, cacheTxID);
	}

	private void sendCreateAccountRequest(Transaction createAccountRequest) throws Exception {
		log.info("\n-----------------------------------");
		log.info("createAccount: request = " + createAccountRequest);
		TransactionResponse response = retryLoopTransaction(createAccountRequest, "createAccount");
		log.info("createAccount Response :: " + response.getNodeTransactionPrecheckCodeValue());
		Assertions.assertNotNull(response);
	}

	/**
	 * Creates a crypto account with complex types, i.e. keylist or threshold keys.
	 *
	 * @param stub
	 * 		CryptoServiceBlockingStub
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @param accountKeyType
	 * 		type of complex key
	 * @param cacheTxID
	 * 		if transaction id need to be cached
	 * @return the account ID of the created account
	 * @throws Throwable
	 * 		indicates failure while creating complex account
	 */
	public AccountID createAccountComplex(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			AccountID payerAccount, AccountID nodeAccountID, String accountKeyType,
			boolean cacheTxID) throws Throwable {
		return createAccountComplex(payerAccount, nodeAccountID, accountKeyType, getReceipt,
				cacheTxID);
	}

	/**
	 * Creates a crypto account with complex types, i.e. keylist or threshold keys.
	 *
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @param accountKeyType
	 * 		type of complex key
	 * @param needReceipt
	 * 		if receipt needs to be provided
	 * @param cacheTxID
	 * 		if transaction id need to be cached
	 * @return the account ID of the created account
	 * @throws Throwable
	 * 		indicates failure while creating complex account
	 */
	public AccountID createAccountComplex(AccountID payerAccount, AccountID nodeAccountID,
			String accountKeyType, boolean needReceipt, boolean cacheTxID) throws Throwable {
		long balance = DEFAULT_INITIAL_ACCOUNT_BALANCE;
		if (UmbrellaServiceRunnable.isSmallAccount) {
			balance = balance / SMALL_ACCOUNT_BALANCE_FACTOR;
		}

		return createAccountComplex(payerAccount, nodeAccountID, accountKeyType, balance,
				needReceipt, cacheTxID);
	}

	/**
	 * Creates a crypto account with complex types, i.e. keylist or threshold keys.
	 *
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @param accountKeyType
	 * 		type of complex key
	 * @param initBalance
	 * 		initial balance for the new account
	 * @param needReceipt
	 * 		if receipt needs to be provided
	 * @param cacheTxID
	 * 		if transaction id need to be cached
	 * @return the account ID of the created account
	 * @throws Throwable
	 * 		indicates failure while creating complex account
	 */
	public AccountID createAccountComplex(AccountID payerAccount, AccountID nodeAccountID,
			String accountKeyType, long initBalance, boolean needReceipt, boolean cacheTxID)
			throws Throwable {
		Key key = genComplexKey(accountKeyType);
		Transaction createAccountRequest = TestHelperComplex.createAccountComplex(payerAccount,
				nodeAccountID, key, initBalance, receiverSigRequired, accountDuration);

		sendCreateAccountRequest(createAccountRequest);

		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(createAccountRequest);
		TransactionID transactionID = body.getTransactionID();
		if (cacheTxID) {
			cache.addTransactionID(transactionID);
		}

		// get transaction receipt
		log.info("preparing to getTransactionReceipts....");

		AccountID accountID = null;
		if (needReceipt) {
			Query query = Query.newBuilder().setTransactionGetReceipt(
					RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
					.build();
			Response transactionReceipts = fetchReceipts(query, cstub);
			if (!ResponseCodeEnum.SUCCESS.name()
					.equals(transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name())) {
				throw new Exception(
						"Create account failed! The receipt retrieved receipt=" + transactionReceipts);
			}
			accountID = transactionReceipts.getTransactionGetReceipt().getReceipt()
					.getAccountID();
			acc2ComplexKeyMap.put(accountID, key);
			if (receiverSigRequired) {
				recvSigRequiredAccounts.add(accountID);
			}
			log.info("Account created: account num :: " + accountID.getAccountNum());

			// get account info
			CommonUtils.nap(WAIT_IN_SEC);
			AccountInfo accInfo = getAccountInfo(accountID);
			log.info("Created account info = " + accInfo);
			Assertions.assertEquals(body.getCryptoCreateAccount().getInitialBalance(),
					accInfo.getBalance());
		}

		return accountID;
	}

	/**
	 * Update complex key registry by reading the genesis information.
	 *
	 * @throws Exception
	 * 		indicates failure while reading genesis information
	 */
	protected void readGenesisInfo() throws Exception {
		// Get Genesis Account key Pair
		final var keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);
		genesisAccountList = keyFromFile.get("START_ACCOUNT");
		genesisAccountID = genesisAccountList.get(0).getAccountId();
		final var genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
		final var pubKeyHex = genesisKeyPair.getPublicKeyAbyteStr();
		genesisPrivateKey = genesisKeyPair.getPrivateKey();
		pubKey2privKeyMap.put(pubKeyHex, genesisPrivateKey);
		startUpKey = Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(keyFromBytes(com.swirlds.common.CommonUtils.unhex(pubKeyHex))))
				.build();
		acc2ComplexKeyMap.put(genesisAccountID, startUpKey);
	}

	/**
	 * Reads default application properties.
	 *
	 * @param hostOverWrite
	 * 		used to overwrite default host
	 * @param nodeAccountOverWrite
	 * 		used to overwrite default listening node account ID
	 */
	protected void readAppConfig(String hostOverWrite, Long nodeAccountOverWrite) {
		CustomProperties properties = TestHelperComplex.getApplicationPropertiesNew();
		if (hostOverWrite == null) {
			host = properties.getString("host", "localhost");
		} else {
			host = hostOverWrite;
		}

		port = properties.getInt("port", 50211);
		if (nodeAccountOverWrite == null) {
			String nodeAccIDStr = properties
					.getString("defaultListeningNodeAccount", DEFAULT_NODE_ACCOUNT_ID_STR);
			defaultListeningNodeAccountID = extractAccountID(nodeAccIDStr);
		} else {
			defaultListeningNodeAccountID = AccountID.newBuilder().setAccountNum(nodeAccountOverWrite)
					.build();
		}

		uniqueListeningPortFlag = properties.getInt("uniqueListeningPortFlag", 0);
		productionFlag = properties.getInt("productionFlag", 0);
	}

	protected void createStubs() throws URISyntaxException, IOException {
		channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		stub = FileServiceGrpc.newBlockingStub(channel);
		cstub = CryptoServiceGrpc.newBlockingStub(channel);
	}

	/**
	 * Parse a string array to an int array.
	 *
	 * @param arrayOfStr
	 * 		string array which need to be parsed
	 * @return an array of int that is parsed from string array
	 */
	private int[] parseIntArray(String[] arrayOfStr) {
		int[] rv = new int[arrayOfStr.length];
		for (int i = 0; i < rv.length; i++) {
			rv[i] = Integer.parseInt(arrayOfStr[i]);
		}

		return rv;
	}


	/**
	 * Get the transaction receipt given TransactionID.
	 *
	 * @param txId
	 * 		given transaction's TransactionID
	 * @return the transaction receipt
	 * @throws Throwable
	 * 		indicates failure while getting transaction receipt
	 */
	public TransactionReceipt getTxReceipt(TransactionID txId) throws Throwable {
		Query query = Query.newBuilder()
				.setTransactionGetReceipt(
						RequestBuilder.getTransactionGetReceiptQuery(txId, ResponseType.ANSWER_ONLY))
				.build();
		Response transactionReceipts = fetchReceipts(query, cstub);
		TransactionReceipt rv = transactionReceipts.getTransactionGetReceipt().getReceipt();
		return rv;
	}

	/**
	 * Gets transaction fast record, retry if necessary.
	 *
	 * @param txId
	 * 		transaction id of transaction
	 * @return transaction receipt for the transaction
	 * @throws Throwable
	 * 		indicates failure while getting transaction receipt
	 */
	public TransactionReceipt getTxFastRecord(TransactionID txId) throws Throwable {
		return TestHelperComplex.getTxReceipt(txId, cstub, log, host);
	}


	/**
	 * Gets the random account key type.
	 *
	 * @return random account key type
	 */
	protected String getRandomAccountKeyType() {
		int index = rand.nextInt(accountKeyTypes.length);
		return accountKeyTypes[index];
	}

	/**
	 * Gets account info using genesis as payer and default listening node as receiving node.
	 *
	 * @param accountID
	 * 		the account to get info for
	 * @return account info of the given account
	 * @throws Throwable
	 * 		indicates failure while getting account info of given account
	 */
	public AccountInfo getAccountInfo(AccountID accountID) throws Throwable {
		return getAccountInfo(accountID, genesisAccountID.getAccountNum(),
				defaultListeningNodeAccountID.getAccountNum());
	}

	/**
	 * Gets the account info for a given account ID.
	 *
	 * @param accountID
	 * 		the account to get info for
	 * @param fromAccountNum
	 * 		payer and sender account id for query payment transaction
	 * @param toAccountNum
	 * 		receiver account id for query payment transaction
	 * @return account info of the given account
	 * @throws Throwable
	 * 		indicates failure while getting account info of given account
	 */
	public AccountInfo getAccountInfo(AccountID accountID, long fromAccountNum, long toAccountNum)
			throws Throwable {

		long getAccountInfoFee = getGetAccountInfoFee(accountID, fromAccountNum, toAccountNum);

		AccountID payerAccountID = AccountID.newBuilder().setAccountNum(fromAccountNum).setRealmNum(0)
				.setShardNum(0).build();
		AccountID toID = AccountID.newBuilder().setAccountNum(toAccountNum).setRealmNum(0).setShardNum(0)
				.build();
		Transaction paymentTxSigned = getQueryPaymentSignedWithFee(payerAccountID, toID,
				"getCryptoGetAccountInfo", getAccountInfoFee);

		Query cryptoGetInfoQuery = RequestBuilder
				.getCryptoGetInfoQuery(accountID, paymentTxSigned, ResponseType.ANSWER_ONLY);
		log.info(
				"\n-----------------------------------\ngetAccountInfo: request = " + cryptoGetInfoQuery);
		Response getInfoResponse = retryLoopQuery(cryptoGetInfoQuery, "getAccountInfo");
		log.info("Pre Check Response of getAccountInfo:: "
				+ getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
		Assertions.assertNotNull(getInfoResponse);
		Assertions.assertNotNull(getInfoResponse.getCryptoGetInfo());
		log.info("getInfoResponse :: " + getInfoResponse.getCryptoGetInfo());

		AccountInfo accInfo = getInfoResponse.getCryptoGetInfo().getAccountInfo();
		return accInfo;
	}

	private long getGetAccountInfoFee(AccountID accountID, long fromAccountNum, long toAccountNum) throws Exception {
		Transaction paymentTxSignedCost = getPaymentSigned(fromAccountNum, toAccountNum,
				"getCryptoGetAccountInfoCost");
		Query cryptoGetInfoQueryCost = RequestBuilder
				.getCryptoGetInfoQuery(accountID, paymentTxSignedCost, ResponseType.COST_ANSWER);
		Response getInfoResponseCost = retryLoopQuery(cryptoGetInfoQueryCost, "getAccountInfo");
		return getInfoResponseCost.getCryptoGetInfo().getHeader().getCost();
	}

	private long getAccountRecordsCostFee(AccountID accountID, long fromAccountNum, long toAccountNum) throws Exception {
		Transaction paymentTxSignedCost = getPaymentSigned(fromAccountNum, toAccountNum,
				"getAccountRecordsCost");
		Query cryptoGetInfoQueryCost = RequestBuilder.getAccountRecordsQuery(accountID, paymentTxSignedCost,
				ResponseType.COST_ANSWER);
		Response getAccountRecordsCost = retryLoopQuery(cryptoGetInfoQueryCost, "getAccountRecords");
		return getAccountRecordsCost.getCryptoGetAccountRecords().getHeader().getCost();
	}

	/**
	 * Generates a complex key up to 2 levels.
	 *
	 * @param accountKeyType
	 * 		complex key type
	 * @return complex key generated
	 */
	protected Key genComplexKey(String accountKeyType) {
		Key key = null;
		if (accountKeyType.equals(SUPPORTED_KEY_TYPES.thresholdKey.name())) {
			key = KeyExpansion
					.genThresholdKeyInstance(COMPLEX_KEY_SIZE, COMPLEX_KEY_THRESHOLD, pubKey2privKeyMap);
		} else if (accountKeyType.equals(SUPPORTED_KEY_TYPES.keylist.name())) {
			key = KeyExpansion.genKeyListInstance(COMPLEX_KEY_SIZE, pubKey2privKeyMap);
		} else {
			key = KeyExpansion.genSingleEd25519Key(pubKey2privKeyMap);
		}

		return key;
	}

	/**
	 * Extract account ID from memo string, e.g. "0.0.5".
	 *
	 * @param memo
	 * 		memo from which account ID to be extracted from
	 * @return extracted account id from the memo
	 */
	protected AccountID extractAccountID(String memo) {
		AccountID rv = null;
		String[] parts = memo.split("\\.");
		rv = AccountID.newBuilder().setShardNum(Long.parseLong(parts[0]))
				.setRealmNum(Long.parseLong(parts[1]))
				.setAccountNum(Long.parseLong(parts[2])).build();
		return rv;
	}

	/**
	 * Get file content give the file ID
	 *
	 * @param fid
	 * 		file id to get content from
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account id
	 * @return byte string of the contents of the given file ID
	 * @throws Throwable
	 * 		indicates failure while getting file content
	 */
	public ByteString getFileContent(FileID fid, AccountID payerID, AccountID nodeID)
			throws Throwable {
		FileServiceBlockingStub stub = null;
		long fileGetContentCost = getFileGetContentCost(fid, payerID, nodeID, stub);
		Transaction paymentTxSigned = getQueryPaymentSignedWithFee(payerID, nodeID, "FileGetContent",
				fileGetContentCost);
		Query fileGetContentQuery = RequestBuilder.getFileGetContentBuilder(paymentTxSigned, fid,
				ResponseType.ANSWER_ONLY);
		log.info("\n-----------------------------------");
		log.info("FileGetContent: query = " + fileGetContentQuery);

		Response fileContentResp = retryLoopQuery(fileGetContentQuery, "getFileContent");

		FileContents fileContent = fileContentResp.getFileGetContents().getFileContents();
		ByteString actualFileData = fileContent.getContents();
		log.info("FileGetContent: content = " + fileContent);
		log.info("FileGetContent: file size = " + actualFileData.size());
		return actualFileData;
	}

	private long getFileGetContentCost(FileID fid, AccountID payerID, AccountID nodeID,
			FileServiceBlockingStub stub) throws Exception {
		Transaction paymentTxSignedCost = getQueryPaymentSigned(payerID, nodeID, "FileGetContent");
		Query fileGetContentQueryCost = RequestBuilder.getFileGetContentBuilder(paymentTxSignedCost, fid,
				ResponseType.COST_ANSWER);
		Response getFileContentResponseCost = retryLoopQuery(fileGetContentQueryCost, "getFileContent");
		return getFileContentResponseCost.getFileGetContents().getHeader().getCost();
	}


	/**
	 * Gets a FileServiceBlockingStub instance for connecting to a given node. Creates it if none exists.
	 *
	 * @param nodeID
	 * 		the node to connect to as default listening account id
	 * @return FileServiceBlockingStub, channel for file service
	 * @throws Exception
	 * 		indicates failure while getting FileServiceBlockingStub
	 */
	public synchronized FileServiceBlockingStub getStub(AccountID nodeID)
			throws Exception {
		FileServiceBlockingStub rv = null;
		if (nodeID2Stub.containsKey(nodeID)) {
			rv = nodeID2Stub.get(nodeID);
		} else {
			rv = newStub(nodeID);
		}

		return rv;
	}

	protected FileServiceBlockingStub newStub(AccountID nodeID) throws Exception {
		FileServiceBlockingStub rv;
		int p = -1;
		NodeAddress nodeAddr = nodeID2Ip.get(nodeID);
		String host = new String(nodeAddr.getIpAddress().toByteArray(), "UTF-8");
		if (productionFlag != 0) { // for production, all nodes listen on same default port
			// gen new stub with default port
			p = port;
		} else { // for development
			if (uniqueListeningPortFlag != 1) { // single default stub
				reConnectChannel(); // Does nothing if the channel is READY.
				return stub;
			} else {
				// gen new stub with diff port
				int pDelta = nodeAddr.getPortno() % 1000;
				p = port + pDelta;
			}
		}

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, p).usePlaintext().build();
		rv = FileServiceGrpc.newBlockingStub(channel);
		nodeID2Stub.put(nodeID, rv);

		log.info("created stub for nodeID=" + nodeID);
		return rv;
	}

	/**
	 * Gets balance of an account.
	 *
	 * @param accountID
	 * 		the account to get balance for
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account id
	 * @return the balance of given account
	 * @throws Throwable
	 * 		indicates failure while getting account balance
	 */
	public long getAccountBalance(AccountID accountID, AccountID payerID, AccountID nodeID)
			throws Throwable {
		return getAccountBalance(cstub, accountID, payerID, nodeID);
	}

	/**
	 * Gets account info for given account.
	 *
	 * @param accountID
	 * 		the account to get info for
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account id
	 * @return account info for the given account
	 * @throws Throwable
	 * 		indicates failure while getting account info
	 */
	public AccountInfo getAccountInfo(AccountID accountID, AccountID payerID, AccountID nodeID)
			throws Throwable {
		return getAccountInfo(accountID, payerID.getAccountNum(), nodeID.getAccountNum());
	}

	/**
	 * Makes a transfer.
	 *
	 * @param payerAccountID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @param fromAccountID
	 * 		sender account id for the transfer
	 * @param toAccountID
	 * 		receiver account id of the transfer
	 * @param amount
	 * 		amount to be transferred
	 * @return transaction receipt for the transfer transaction
	 * @throws Throwable
	 * 		indicates failure caused while doing the transfer transaction
	 */
	public TransactionReceipt transfer(AccountID payerAccountID, AccountID nodeAccountID,
			AccountID fromAccountID,
			AccountID toAccountID, long amount) throws Throwable {
		Transaction transferTxSigned = getSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID,
				toAccountID,
				amount, "Transfer");

		return transfer(transferTxSigned);
	}

	/**
	 * Generates a complex key up to defined depth.
	 *
	 * @param accountKeyType
	 * 		complex key type
	 * @param currentLevel
	 * 		the current depth of the key tree
	 * @param targetLevel
	 * 		the target depth of the key tree
	 * @return the key constructed
	 * @throws Exception
	 * 		indicates failure caused if target level is less than 2 for keyList and thresholdKey
	 */
	protected Key genComplexKeyRecursive(String accountKeyType, int currentLevel, int targetLevel)
			throws Exception {
		Key key = null;
		if (accountKeyType.equals(SUPPORTED_KEY_TYPES.thresholdKey.name())
				|| (accountKeyType.equals(SUPPORTED_KEY_TYPES.keylist.name()))) {
			if (targetLevel < 2) {
				throw new Exception(
						"Error: targetLevel should be at least 2 for keyList and thresholdKey! A base key has a depth" +
								" " +
								"of 1 whereas keyList and thresholdKey starts at depth of 2.");
			}
			Builder keyList = KeyList.newBuilder();
			for (int i = 0; i < COMPLEX_KEY_SIZE; i++) {
				Key item = null;
				// check if level reached, if not call parent at the next level
				if (currentLevel >= (targetLevel - 1)) {
					item = KeyExpansion.genSingleEd25519Key(pubKey2privKeyMap);
				} else {
					String keyType = getRandomAccountKeyType();
					item = genComplexKeyRecursive(keyType, (currentLevel + 1), targetLevel);
				}
				keyList.addKeys(item);
			}
			if (accountKeyType.equals(SUPPORTED_KEY_TYPES.thresholdKey.name())) {
				key = Key.newBuilder()
						.setThresholdKey(
								ThresholdKey.newBuilder().setKeys(keyList).setThreshold(COMPLEX_KEY_THRESHOLD))
						.build();
			} else {
				key = Key.newBuilder().setKeyList(keyList).build();
			}
		} else {
			key = KeyExpansion.genSingleEd25519Key(pubKey2privKeyMap);
		}

		return key;
	}

	public static void main(String[] args) throws Throwable {
		testGenComplexKeyRecursive("keylist", 5);
		testGenComplexKeyRecursive("thresholdKey", 5);
	}

	public static void testGenComplexKeyRecursive(String type, int depth) throws Exception {
		CryptoServiceTest tester = new CryptoServiceTest();
		tester.getTestConfig();
		Key key = tester.genComplexKeyRecursive(type, 1, depth);
		byte[] message = "testGenComplexKeyRecursive".getBytes();
		Signature sig = KeyExpansion.sign(key, message, pubKey2privKeyMap, depth);
		log.info(
				"\n\n******======> type=" + type + ", depth=" + depth + "\nkey=" + key + "\n\nsig=" + sig);
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
	 * 		memo in the payment transaction
	 * @return the signed query payment transaction
	 * @throws Exception
	 * 		indicates failure while getting query payment signed transaction
	 */
	protected static Transaction getPaymentSigned(long payerSeq, long toSeq, String memo)
			throws Exception {
		AccountID payerAccountID = AccountID.newBuilder().setAccountNum(payerSeq).setRealmNum(0)
				.setShardNum(0).build();
		AccountID toID = AccountID.newBuilder().setAccountNum(toSeq).setRealmNum(0).setShardNum(0)
				.build();
		return getQueryPaymentSigned(payerAccountID, toID, memo);
	}

	/**
	 * Fetches the receipts, wait if necessary.
	 *
	 * @param query
	 * 		query to get the receipt for
	 * @param cstub2
	 * 		CryptoServiceBlockingStub, blocking-style stub that supports unary and streaming output calls on the crypto
	 * 		service
	 * @return response for the query
	 * @throws Exception
	 * 		indicates failure while fetching receipts
	 */
	protected static Response fetchReceipts(Query query, CryptoServiceBlockingStub cstub2)
			throws Exception {
		return TestHelperComplex.fetchReceipts(query, cstub2, log, host);
	}

	/**
	 * Creates a signed payment transaction for query.
	 *
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account id
	 * @param memo
	 * 		memo for the transaction
	 * @return signed query payment transaction
	 * @throws Exception
	 * 		indicates failure while getting signed transfer transaction
	 */
	public static Transaction getQueryPaymentSigned(AccountID payerID, AccountID nodeID, String memo)
			throws Exception {
		Transaction paymentTxSigned = getSignedTransferTx(payerID, nodeID, payerID, nodeID,
				TestHelper.getCryptoMaxFee(), memo);
		return paymentTxSigned;
	}

	/**
	 * Creates a signed payment transaction with query payment fee.
	 *
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account id
	 * @param memo
	 * 		memo for the transaction
	 * @param queryFee
	 * 		fees to be paid for the query
	 * @return query payment signed transaction with fee
	 * @throws Exception
	 * 		indicates failure while getting signed transfer transaction
	 */
	public static Transaction getQueryPaymentSignedWithFee(AccountID payerID, AccountID nodeID,
			String memo, long queryFee)
			throws Exception {
		Transaction paymentTxSigned = getSignedTransferTx(payerID, nodeID, payerID, nodeID,
				queryFee, memo);
		return paymentTxSigned;
	}

	/**
	 * Creates a signed transfer transaction
	 *
	 * @param payerAccountID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @param fromAccountID
	 * 		sender account id for transfer transaction
	 * @param toAccountID
	 * 		receiver account id  of transfer transaction
	 * @param amount
	 * 		amount to be transferred
	 * @param memo
	 * 		memo for the transaction
	 * @return signed transfer transaction
	 * @throws Exception
	 * 		indicates failure while getting signed transaction with complex sigMap
	 */
	protected static Transaction getSignedTransferTx(AccountID payerAccountID,
			AccountID nodeAccountID,
			AccountID fromAccountID, AccountID toAccountID, long amount, String memo) throws Exception {

		Transaction paymentTx = getUnSignedTransferTx(payerAccountID, nodeAccountID, fromAccountID,
				toAccountID, amount, memo);
		Key payerKey = acc2ComplexKeyMap.get(payerAccountID);
		Key fromKey = acc2ComplexKeyMap.get(fromAccountID);
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(fromKey);
		if (recvSigRequiredAccounts.contains(toAccountID)) {
			Key toKey = acc2ComplexKeyMap.get(toAccountID);
			keys.add(toKey);
		}
		Transaction paymentTxSigned = TransactionSigner
				.signTransactionComplexWithSigMap(paymentTx, keys, pubKey2privKeyMap);
		return paymentTxSigned;
	}

	/**
	 * Gets a random node account if isRandomSubmission is true, otherwise gets the default receiving
	 * node account specified in test config.
	 *
	 * @return a a random node account ID
	 */
	public static AccountID getRandomNodeAccount() {
		AccountID rv = defaultListeningNodeAccountID;
		if (isRandomSubmission && nodeAccounts != null && nodeAccounts.length > 0) {
			int index = rand.nextInt(nodeAccounts.length);
			rv = nodeAccounts[index];
		}

		return rv;
	}

	/**
	 * Gets a random payer account.
	 *
	 * @return the selected account ID or null if none available
	 */
	public static AccountID getRandomPayerAccount() {
		if (payerAccounts == null || payerAccounts.length == 0) {
			return null;
		}
		int index = 0;
		if (isRandomPayer) {
			index = rand.nextInt(payerAccounts.length);
		}
		return payerAccounts[index];
	}

	/**
	 * Checks the transaction size for given transaction.
	 *
	 * @param txSigned
	 * 		given signed transaction
	 * @throws Exception
	 * 		indicates the max size of transaction is exceeded.
	 */
	protected void checkTxSize(Transaction txSigned) throws Exception {
		int requestSize = txSigned.getSerializedSize();
		if (requestSize > transactionMaxBytes) {
			String msg =
					"transactionMaxBytes (" + transactionMaxBytes + ") exceeded! requestSize=" + requestSize
							+ ", txShortInfo=" + com.hedera.services.legacy.proto.utils.CommonUtils
							.toReadableTransactionID(txSigned);
			log.warn(msg);
			throw new Exception(msg);
		} else {
			log.debug("request serialized size=" + requestSize);
		}
	}

	/**
	 * Makes a transfer.
	 *
	 * @param transferTxSigned
	 * 		signed transfer transaction
	 * @return transaction receipt for the transfer transaction
	 * @throws Throwable
	 * 		indicates failure while extracting transaction body
	 */
	public TransactionReceipt transfer(Transaction transferTxSigned) throws Throwable {
		log.info("\n-----------------------------------\ntransfer: request = "
				+ TxnUtils.toReadableString(transferTxSigned));
		TransactionResponse response = retryLoopTransaction(transferTxSigned, "transfer");
		log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCodeValue());
		Assertions.assertNotNull(response);
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(transferTxSigned);
		TransactionID txId = body.getTransactionID();
		cache.addTransactionID(txId);
		TransactionReceipt receipt = null;
		if (CryptoServiceTest.getReceipt) {
			receipt = getTxReceipt(txId);
		}
		return receipt;
	}

	/**
	 * Gets a random account for the from or to account of a transfer.
	 *
	 * @return the selected account ID or null if none available
	 */
	public static AccountID getRandomTransferAccount() {
		if (transferAccounts == null || transferAccounts.length == 0) {
			return null;
		}

		int index = 0;
		if (isRandomTransferAccount) {
			index = rand.nextInt(transferAccounts.length);
		}
		return transferAccounts[index];
	}

	/**
	 * Creates an unsigned transfer transaction
	 *
	 * @param payerAccountID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccountID
	 * 		node account id, default listening account id
	 * @param fromAccountID
	 * 		sender account id for the transfer
	 * @param toAccountID
	 * 		receiver account id of the transfer
	 * @param amount
	 * 		amount to be transferred
	 * @param memo
	 * 		memo for the transfer transaction
	 * @return unsigned transfer transaction
	 */
	public static Transaction getUnSignedTransferTx(AccountID payerAccountID,
			AccountID nodeAccountID,
			AccountID fromAccountID, AccountID toAccountID, long amount, String memo) {

		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccountID.getAccountNum(),
				payerAccountID.getRealmNum(), payerAccountID.getShardNum(), nodeAccountID.getAccountNum(),
				nodeAccountID.getRealmNum(), nodeAccountID.getShardNum(), TestHelper.getCryptoMaxFee(), timestamp,
				transactionDuration, true,
				memo, fromAccountID.getAccountNum(), -amount, toAccountID.getAccountNum(),
				amount);
		return transferTx;
	}

	/**
	 * Creates a new channel and stub for file service.
	 *
	 * @param createdChannels
	 * 		to store generated channel
	 * @return FileServiceBlockingStub created new channel for file service
	 */
	public static FileServiceBlockingStub createFileServiceStub(ManagedChannel[] createdChannels) {
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext()
				.build();
		FileServiceBlockingStub rv = FileServiceGrpc.newBlockingStub(channel);
		createdChannels[0] = channel;

		return rv;
	}

	/**
	 * Get balance for provided Account with stub
	 *
	 * @param stub
	 * 		CryptoServiceBlockingStub, blocking-style stub that supports unary and streaming output calls on the crypto
	 * 		service
	 * @param accountID
	 * 		account id to get balance
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account id
	 * @return account balance of given account id
	 * @throws Throwable
	 * 		indicates failure while getting signed payment transaction
	 */
	public static long getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			AccountID accountID, AccountID payerID, AccountID nodeID)
			throws Throwable {
		Transaction paymentTxSigned = getPaymentSigned(payerID.getAccountNum(), nodeID.getAccountNum(),
				"getAccountBalance");
		Query cryptoGetBalanceQuery = RequestBuilder
				.getCryptoGetBalanceQuery(accountID, paymentTxSigned,
						ResponseType.ANSWER_ONLY);
		log.info("\n-----------------------------------\ngetAccountBalance: request = "
				+ cryptoGetBalanceQuery);
		CryptoServiceTest cryptoServiceTest = new CryptoServiceTest();
		CryptoServiceTest.cstub = stub;
		Response getBalanceResponse = cryptoServiceTest
				.retryLoopQuery(cryptoGetBalanceQuery, "cryptoGetBalance");
		log.info("Pre Check Response of getAccountBalance:: "
				+ getBalanceResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode()
				.name());
		log.info("Get Balance Fee: " + getBalanceResponse.getCryptogetAccountBalance().getHeader()
				.getCost());
		Assertions.assertNotNull(getBalanceResponse);
		Assertions.assertNotNull(getBalanceResponse.getCryptoGetInfo());
		Assertions.assertEquals(ResponseCodeEnum.OK,
				getBalanceResponse.getCryptogetAccountBalance().getHeader()
						.getNodeTransactionPrecheckCode());

		long balance = getBalanceResponse.getCryptogetAccountBalance().getBalance();
		log.info("getAccountBalance :: account=" + accountID.getAccountNum() + ", balance=" + balance);
		return balance;
	}

	protected void reConnectChannel() throws Exception {
		long stTime = System.nanoTime();
		if (channel == null || channel.getState(false) != ConnectivityState.READY) {
			createStubs();
			long endTime = System.nanoTime() - stTime;
			log.error("Reconnect took NS " + endTime);
		}
	}

	private TransactionResponse retryLoopTransaction(Transaction transaction, String apiName)
			throws InterruptedException {
		TransactionResponse response = null;
		for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

			if (i > 0) {
				log.info("retrying api call " + apiName);
			}

			try {
				switch (apiName) {
					case "createAccount":
						response = cstub.createAccount(transaction);
						log.info(response);
						break;
					case "updateAccount":
						response = cstub.updateAccount(transaction);
						break;
					case "transfer":
						response = cstub.cryptoTransfer(transaction);
						break;
					case "cryptoDelete":
						response = cstub.cryptoDelete(transaction);
						break;
					case "addClaim":
						response = cstub.addLiveHash(transaction);
						break;
					case "deleteClaim":
						response = cstub.deleteLiveHash(transaction);
						break;
					default:
						throw new IllegalArgumentException();
				}
			} catch (StatusRuntimeException ex) {
				log.error("Platform exception ...", ex);
				Status status = ex.getStatus();
				String errorMsg = status.getDescription();
				if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
					try {
						reConnectChannel();
					} catch (Exception e) {
						log.error("Reconnect channel failed..");
						break;
					}
				}
				Thread.sleep(BUSY_RETRY_MS);
				continue;
			}

			if (!ResponseCodeEnum.BUSY.equals(response.getNodeTransactionPrecheckCode())) {
				break;
			}
			Thread.sleep(BUSY_RETRY_MS);
		}
		return response;
	}

	private Response retryLoopQuery(Query query, String apiName)
			throws InterruptedException {
		Response response = null;
		for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

			if (i > 0) {
				log.info("retrying api call " + apiName);
			}
			ResponseCodeEnum precheckCode;
			try {
				switch (apiName) {
					case "getReceipt":
						response = cstub.getTransactionReceipts(query);
						precheckCode = response.getTransactionGetReceipt()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					case "getAccountInfo":
						response = cstub.getAccountInfo(query);
						precheckCode = response.getCryptoGetInfo()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					case "getAccountRecords":
						response = cstub.getAccountRecords(query);
						precheckCode = response.getCryptoGetAccountRecords()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					case "getTxRecordByTxID":
						response = cstub.getTxRecordByTxID(query);
						precheckCode = response.getTransactionGetRecord()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					case "cryptoGetBalance":
						response = cstub.cryptoGetBalance(query);
						precheckCode = response.getCryptogetAccountBalance()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					case "getFastTransactionRecord":
						response = cstub.getFastTransactionRecord(query);
						precheckCode = response.getTransactionGetFastRecord()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					case "getClaim":
						response = cstub.getLiveHash(query);
						precheckCode = response.getCryptoGetLiveHash()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					case "getFileContent":
						response = stub.getFileContent(query);
						precheckCode = response.getFileGetContents()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					default:
						throw new IllegalArgumentException();
				}
			} catch (StatusRuntimeException ex) {
				log.error("Platform exception ...", ex);
				Status status = ex.getStatus();
				String errorMsg = status.getDescription();
				if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
					try {
						reConnectChannel();
					} catch (Exception e) {
						log.error("Reconnect channel failed..");
						break;
					}
				}
				Thread.sleep(BUSY_RETRY_MS);
				continue;
			}

			if (!ResponseCodeEnum.BUSY.equals(precheckCode)) {
				break;
			}
			Thread.sleep(BUSY_RETRY_MS);
		}
		return response;
	}
}
