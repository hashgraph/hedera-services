package com.hedera.services.legacy.core;

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
import com.google.protobuf.TextFormat;
import com.hedera.services.legacy.client.util.KeyExpansion;
import com.hedera.services.legacy.client.util.TransactionSigner;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc.CryptoServiceBlockingStub;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public class TestHelper {
	private static final Logger log = LogManager.getLogger(TestHelper.class);

	public static long DEFAULT_SEND_RECV_RECORD_THRESHOLD = 5000000000000000000L;
	public static long DEFAULT_WIND_SEC = -30; // seconds to wind back the UTC clock
	public static long TX_DURATION = 180;
	private static volatile long lastNano = 0;
	protected static Map<AccountID, Key> acc2ComplexKeyMap = new LinkedHashMap<>();
	protected static Map<ContractID, Key> contract2ComplexKeyMap = new LinkedHashMap<>();
	protected static Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
	protected static int MAX_RECEIPT_RETRIES = 108000;
	protected static long RETRY_FREQ_MILLIS = 50;
	protected static long MAX_RETRY_FREQ_MILLIS = 200;
	protected static boolean isExponentialBackoff = false;
	private static Properties properties = TestHelper.getApplicationProperties();
	public static String fileName = TestHelper.getStartUpFile();

	public static Map<String, List<AccountKeyListObj>> getKeyFromFile(String strPath) throws IOException {
		log.info("Startup File Path: " + strPath);
		try {
			new File(strPath);
		} catch (Throwable t) {
		}
		Path path;
		try {
			int index = strPath.lastIndexOf("\\");
			if (index <= 0) {
				index = strPath.lastIndexOf("/");
			}
			String localStartUpFile = strPath.substring(index + 1);
			path = Paths.get(TestHelper.class.getClassLoader().getResource(localStartUpFile).toURI());
		} catch (Exception e) {
			log.info("Error while loading startup file " + e.getMessage());
			path = Paths.get(strPath);
		}
		log.info("Loading Startup File: " + path);
		StringBuilder data = new StringBuilder();
		Stream<String> lines = Files.lines(path);
		lines.forEach(data::append);
		lines.close();
		byte[] accountKeyPairHolderBytes = CommonUtils.base64decode(String.valueOf(data));
		return (Map<String, List<AccountKeyListObj>>) CommonUtils
				.convertFromBytes(accountKeyPairHolderBytes);
	}

	// Read exchange rate from the application.properties file
	public static void initializeFeeClient(Channel channel, AccountID payerAccount,
			KeyPair payerKeyPair, AccountID nodeAccount) {
		int centEquivalent;
		int hbarEquivalent;
		Properties properties = TestHelper.getApplicationProperties();
		try {
			centEquivalent = Integer.parseInt(properties.getProperty("currentCentEquivalent"));
		} catch (NumberFormatException nfe) {
			centEquivalent = 12;
		}
		try {
			hbarEquivalent = Integer.parseInt(properties.getProperty("currentHbarEquivalent"));
		} catch (NumberFormatException nfe) {
			hbarEquivalent = 1;
		}

		FileServiceGrpc.FileServiceBlockingStub fStub = FileServiceGrpc.newBlockingStub(channel);
		FileID feeFileId = FileID.newBuilder()
				.setFileNum(111L)
				.setRealmNum(0L).setShardNum(0L).build();
		byte[] feeScheduleBytes = new byte[0];
		try {
			Response response = getFileContentInitial(fStub, feeFileId, payerAccount,
					payerKeyPair, nodeAccount);
			feeScheduleBytes = response.getFileGetContents().getFileContents().getContents()
					.toByteArray();
			if (feeScheduleBytes.length < 1) {
				log.error("Empty data found while reading Fee file from fcfs, status is "
						+ response.getFileGetContents().getHeader().getNodeTransactionPrecheckCode());
			}
		} catch (Exception e) {
			log.error("Exception while reading Fee file from fcfs :: " + e);
		}

		FeeClient.initialize(hbarEquivalent, centEquivalent, feeScheduleBytes);
	}

	public static Transaction createAccountWithFee(AccountID payerAccount, AccountID nodeAccount,
			KeyPair pair, long initialBalance, List<PrivateKey> privKey) {
		Transaction transaction = TestHelper
				.createAccount(payerAccount, nodeAccount, pair, initialBalance, TestHelper.getCryptoMaxFee(),
						DEFAULT_SEND_RECV_RECORD_THRESHOLD, DEFAULT_SEND_RECV_RECORD_THRESHOLD);
		return TransactionSigner.signTransaction(transaction, privKey);
	}

	public static Transaction createAccountWithSigMap(AccountID payerAccount, AccountID nodeAccount,
			KeyPair pair, long initialBalance, KeyPair payerKeyPair) throws Exception {
		Transaction transaction = TestHelper
				.createAccount(payerAccount, nodeAccount, pair, initialBalance, TestHelper.getCryptoMaxFee(),
						DEFAULT_SEND_RECV_RECORD_THRESHOLD, DEFAULT_SEND_RECV_RECORD_THRESHOLD);
		List<Key> keyList = Collections.singletonList(KeyExpansion.keyFromPrivateKey(payerKeyPair.getPrivate()));
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		KeyExpansion.addKeyMap(pair, pubKey2privKeyMap);
		KeyExpansion.addKeyMap(payerKeyPair, pubKey2privKeyMap);
		Transaction signTransaction = TransactionSigner
				.signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);
		long createAccountFee = FeeClient.getCreateAccountFee(signTransaction, 1);
		System.out.println("createAccountFee ===> " + createAccountFee);
		return signTransaction;
	}

	public static Transaction createAccount(AccountID payerAccount, AccountID nodeAccount,
			KeyPair pair, long initialBalance, long transactionFee, long defaultSendRecordThreshold,
			long defaultRecvRecordThreshold) {
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		List<Key> keyList = Collections.singletonList(key);

		boolean generateRecord = true;
		String memo = "Create Account Test";
		boolean receiverSigRequired = false;
		CustomPropertiesSingleton propertyWrapper = CustomPropertiesSingleton.getInstance();
		Duration autoRenewPeriod = RequestBuilder.getDuration(propertyWrapper.getAccountDuration());

		return RequestBuilder
				.getCreateAccountBuilder(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccount.getAccountNum(),
						nodeAccount.getRealmNum(), nodeAccount.getShardNum(),
						transactionFee, timestamp, transactionDuration, generateRecord,
						memo, keyList, initialBalance, defaultSendRecordThreshold,
						defaultRecvRecordThreshold, receiverSigRequired, autoRenewPeriod);


	}

	public static TransactionReceipt getTxReceipt(TransactionID transactionID,
			CryptoServiceBlockingStub stub, Logger log, String host)
			throws InvalidNodeTransactionPrecheckCode {
		Query query = Query.newBuilder().setTransactionGetReceipt(
						RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
				.build();

		TransactionReceipt rv;
		Response transactionReceipts = fetchReceipts(query, stub, log, host);
		rv = transactionReceipts.getTransactionGetReceipt().getReceipt();
		return rv;
	}

	public static TransactionReceipt getTxReceipt(TransactionID transactionID,
			CryptoServiceGrpc.CryptoServiceBlockingStub stub)
			throws InvalidNodeTransactionPrecheckCode {
		log.info("getTxReceipt--transactionID : " + TextFormat.shortDebugString(transactionID));
		return getTxReceipt(transactionID, stub, null, "localhost");
	}

	public static Response getCryptoGetBalance(CryptoServiceBlockingStub stub,
			AccountID accountID, AccountID payerAccount,
			KeyPair payerAccountKey, AccountID nodeAccount) throws Exception {

		long costForQuery = FeeClient.getFeeByID(HederaFunctionality.CryptoGetAccountBalance) + 50000;
		System.out.println(costForQuery + " :: is the cost for query");
		Response response = executeGetBalanceQuery(stub, accountID, payerAccount, payerAccountKey,
				nodeAccount, costForQuery, ResponseType.COST_ANSWER);

		if (response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode()
				== ResponseCodeEnum.OK) {
			long getAcctFee = response.getCryptogetAccountBalance().getHeader().getCost();
			response = executeGetBalanceQuery(stub, accountID, payerAccount, payerAccountKey,
					nodeAccount, getAcctFee, ResponseType.ANSWER_ONLY);
		}
		return response;
	}

	private static Response executeGetBalanceQuery(CryptoServiceBlockingStub stub,
			AccountID accountID, AccountID payerAccount, KeyPair payerAccountKey,
			AccountID nodeAccount, long costForQuery, ResponseType responseType) throws Exception {
		Transaction transferTransaction = createTransferSigMap(payerAccount, payerAccountKey,
				nodeAccount, payerAccount, payerAccountKey, nodeAccount, costForQuery);
		Query getBalanceQuery = RequestBuilder
				.getCryptoGetBalanceQuery(accountID, transferTransaction, responseType);
		return stub.cryptoGetBalance(getBalanceQuery);
	}

	public static Properties getApplicationProperties() {
		Properties prop = new Properties();
		InputStream input;
		try {
			String fileName = "src/main/resource/application.properties";
			File checkFile = new File(fileName);
			if (!checkFile.exists()) {
				String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
				input = new FileInputStream(rootPath + "application.properties");
				log.info("In IF rootPath: " + rootPath);
			} else {
				log.info("In Else fileName: " + fileName);
				input = new FileInputStream(fileName);
			}
			prop.load(input);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return prop;
	}

	public static String getStartUpFile() {
		if (properties == null || properties.size() <= 0) {
			properties = TestHelper.getApplicationProperties();
		}
		log.info("properties: " + properties);
		return properties.getProperty("startUpFile");
	}

	/**
	 * Gets the application properties as a more friendly CustomProperties object.
	 *
	 * @return custom application properties
	 */
	public static CustomProperties getApplicationPropertiesNew() {
		String fileName = "src/main/resource/application.properties";
		File checkFile = new File(fileName);
		if (checkFile.exists()) {
			return new CustomProperties(fileName, false);
		} else {
			String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
			return new CustomProperties(rootPath + "application.properties", false);
		}
	}

	// For getting the initial files, exchange rate and fee schedule.
	public static Response getFileContentInitial(FileServiceBlockingStub fileStub,
			FileID fileID, AccountID payerAccount,
			KeyPair payerAccountKey, AccountID nodeAccount) throws Exception {
		long getContentFee = TestHelper.getFileMaxFee();
		Transaction transferTransaction = createTransferSigMapInitial(payerAccount, payerAccountKey,
				nodeAccount, payerAccount, payerAccountKey, nodeAccount, getContentFee);
		Query fileContentQuery = RequestBuilder
				.getFileContentQuery(fileID, transferTransaction, ResponseType.COST_ANSWER);
		Response response = fileStub.getFileContent(fileContentQuery);
		getContentFee = response.getFileGetContents().getHeader().getCost();
		transferTransaction = createTransferSigMapInitial(payerAccount, payerAccountKey,
				nodeAccount, payerAccount, payerAccountKey, nodeAccount, getContentFee);
		fileContentQuery = RequestBuilder
				.getFileContentQuery(fileID, transferTransaction, ResponseType.ANSWER_ONLY);
		return fileStub.getFileContent(fileContentQuery);
	}

	public static Transaction createTransferSigMap(AccountID fromAccount, KeyPair fromKeyPair,
			AccountID toAccount, AccountID payerAccount, KeyPair payerKeyPair,
			AccountID nodeAccount, long amount) throws Exception {
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

		Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
				nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 0, timestamp, transactionDuration,
				false, "Test Transfer", fromAccount.getAccountNum(), -amount,
				toAccount.getAccountNum(), amount);
		// sign the tx
		List<Key> keyList = new ArrayList<>();
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		keyList.add(KeyExpansion.keyFromPrivateKey(payerKeyPair.getPrivate()));
		KeyExpansion.addKeyMap(payerKeyPair, pubKey2privKeyMap);
		if (!payerKeyPair.equals(fromKeyPair)) {
			keyList.add(KeyExpansion.keyFromPrivateKey(fromKeyPair.getPrivate()));
			KeyExpansion.addKeyMap(fromKeyPair, pubKey2privKeyMap);
		}

		Transaction signedTx = TransactionSigner.signTransactionComplexWithSigMap(
				transferTx, keyList, pubKey2privKeyMap);

		long transferFee = TestHelper.getCryptoMaxFee();
		transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
				nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
				transactionDuration, false,
				"Test Transfer", fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
				amount);

		signedTx = TransactionSigner.signTransactionComplexWithSigMap(
				transferTx, keyList, pubKey2privKeyMap);
		return signedTx;
	}

	//  For getting the initial files, exchange rate and fee schedule. Needs to offer a large
	// transaction fee, because the exchange rate is not yet known.
	public static Transaction createTransferSigMapInitial(AccountID fromAccount, KeyPair fromKeyPair,
			AccountID toAccount, AccountID payerAccount, KeyPair payerKeyPair,
			AccountID nodeAccount, long amount) throws Exception {
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

		Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
				nodeAccount.getRealmNum(), nodeAccount.getShardNum(), FeeClient.getMaxFee(), timestamp,
				transactionDuration, false, "Test Transfer", fromAccount.getAccountNum(), -amount,
				toAccount.getAccountNum(), amount);
		// sign the tx
		List<Key> keyList = new ArrayList<>();
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		keyList.add(KeyExpansion.keyFromPrivateKey(payerKeyPair.getPrivate()));
		KeyExpansion.addKeyMap(payerKeyPair, pubKey2privKeyMap);
		if (!payerKeyPair.equals(fromKeyPair)) {
			keyList.add(KeyExpansion.keyFromPrivateKey(fromKeyPair.getPrivate()));
			KeyExpansion.addKeyMap(fromKeyPair, pubKey2privKeyMap);
		}

		Transaction signedTx = TransactionSigner.signTransactionComplexWithSigMap(
				transferTx, keyList, pubKey2privKeyMap);
		return signedTx;
	}


	/**
	 * Fetches the receipts, wait if necessary.
	 *
	 * @param query
	 * 		query for which receipts to be fetched
	 * @param cstub
	 * 		CryptoServiceBlockingStub blocking-style stub that supports unary and streaming output calls on the service
	 * @param log
	 * 		logger to add logs
	 * @param host
	 * 		host to build address
	 * @return the getTransactionReceipt response
	 * @throws InvalidNodeTransactionPrecheckCode
	 * 		indicates there is a failure while querying transaction receipt if pre-check code is not OK or BUSY
	 */
	public static Response fetchReceipts(final Query query, CryptoServiceBlockingStub cstub,
			final Logger log, String host)
			throws InvalidNodeTransactionPrecheckCode {
		long start = System.currentTimeMillis();
		if (log != null) {
			log.debug("GetTxReceipt: query=" + query);
		}

		Response transactionReceipts = cstub.getTransactionReceipts(query);
		Assertions.assertNotNull(transactionReceipts);
		ResponseCodeEnum precheckCode = transactionReceipts.getTransactionGetReceipt().getHeader()
				.getNodeTransactionPrecheckCode();
		if (!precheckCode.equals(ResponseCodeEnum.OK) && !precheckCode.equals(ResponseCodeEnum.BUSY)) {
			throw new InvalidNodeTransactionPrecheckCode("Invalid node transaction precheck code " +
					precheckCode.name() +
					" from getTransactionReceipts");
		}

		int cnt = 0;
		String status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
		while (cnt < MAX_RECEIPT_RETRIES &&
				(status.equals(ResponseCodeEnum.UNKNOWN.name()) || precheckCode == ResponseCodeEnum.BUSY)) {
			long napMillis = RETRY_FREQ_MILLIS;
			if (isExponentialBackoff) {
				napMillis = getExpWaitTimeMillis(cnt, MAX_RETRY_FREQ_MILLIS);
			}
			CommonUtils.napMillis(napMillis);
			cnt++;
			if (log != null) {
				log.info("Retrying getTransactionReceipts call");
			}
			try {
				transactionReceipts = cstub.getTransactionReceipts(query);
				status = transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name();
				precheckCode = transactionReceipts.getTransactionGetReceipt().getHeader()
						.getNodeTransactionPrecheckCode();
			} catch (StatusRuntimeException e) {
				if (log != null) {
					log.warn("getTransactionReceipts: RPC failed!", e);
				}
				String errorMsg = e.getStatus().getDescription();
				if (e.getStatus().equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg
						.contains("max_age")) {
					Channel channel = cstub.getChannel();
					if (channel == null) {
						Properties properties = TestHelper.getApplicationProperties();
						int port = Integer.parseInt(properties.getProperty("port"));
						channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
						cstub = CryptoServiceGrpc.newBlockingStub(channel);
					}
				}
				status = ResponseCodeEnum.UNKNOWN.name();
			}
		}

		long elapse = System.currentTimeMillis() - start;
		long secs = elapse / 1000;
		long milliSec = elapse % 1000;
		String msg =
				"GetTxReceipt: took = " + secs + " second " + milliSec + " millisec; retries = " + cnt
						+ "; receipt=" + transactionReceipts;
		if (!status.equals(ResponseCodeEnum.SUCCESS.name())) {
			if (log != null) {
				log.warn(msg);
			}
		} else {
			if (log != null) {
				log.info(msg);
			}
		}
		return transactionReceipts;
	}

	/**
	 * Exponential wait time in millis capped by a max wait time.
	 *
	 * @param retries
	 * 		num of retries to be performed
	 * @param maxWaitMillis
	 * 		beyond which, the wait time will be this value
	 * @return the wait time in millis
	 */
	private static long getExpWaitTimeMillis(int retries, long maxWaitMillis) {
		double rv = 0;
		rv = (Math.pow(2, retries) * RETRY_FREQ_MILLIS);

		if (rv > maxWaitMillis) {
			rv = maxWaitMillis;
		}

		long waitMillis = (long) rv;

		return waitMillis;
	}

	/**
	 * Gets the current UTC timestamp with default winding back seconds.
	 *
	 * @return current UTC timestamp with default winding back seconds
	 */
	public synchronized static Timestamp getDefaultCurrentTimestampUTC() {
		Timestamp rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
		if (rv.getNanos() == lastNano) {
			try {
				Thread.sleep(0, 1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
		}

		lastNano = rv.getNanos();

		return rv;
	}

	public static Transaction getContractCallRequestSigMap(Long payerAccountNum, Long payerRealmNum,
			Long payerShardNum,
			Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
			long transactionFee, Timestamp timestamp,
			Duration txDuration, long gas, ContractID contractId,
			ByteString functionData, long value,
			KeyPair payerKeyPair) throws Exception {

		List<Key> keyList = Collections.singletonList(KeyExpansion.keyFromPrivateKey(payerKeyPair.getPrivate()));
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		KeyExpansion.addKeyMap(payerKeyPair, pubKey2privKeyMap);

		Transaction transaction = RequestBuilder
				.getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, gas, contractId, functionData, value);

		transaction = TransactionSigner.signTransactionComplexWithSigMap(
				transaction, keyList, pubKey2privKeyMap);

		transactionFee = FeeClient.getCostContractCallFee(transaction, keyList.size());

		transaction = RequestBuilder
				.getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, gas, contractId, functionData, value);

		return TransactionSigner.signTransactionComplexWithSigMap(
				transaction, keyList, pubKey2privKeyMap);

	}

	public static Transaction getCreateContractRequestSigMap(Long payerAccountNum, Long payerRealmNum,
			Long payerShardNum,
			Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
			long transactionFee, Timestamp timestamp, Duration txDuration,
			boolean generateRecord, String txMemo, long gas, FileID fileId,
			ByteString constructorParameters, long initialBalance,
			Duration autoRenewalPeriod, KeyPair payerKeyPair, String contractMemo,
			Key adminPubKey) throws Exception {

		List<Key> keyList = Collections.singletonList(KeyExpansion.keyFromPrivateKey(payerKeyPair.getPrivate()));
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		KeyExpansion.addKeyMap(payerKeyPair, pubKey2privKeyMap);

		Transaction transaction = RequestBuilder
				.getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
						autoRenewalPeriod, contractMemo, adminPubKey);

		transaction = TransactionSigner.signTransactionComplexWithSigMap(
				transaction, keyList, pubKey2privKeyMap);

		transactionFee = FeeClient.getContractCreateFee(transaction, keyList.size());

		transaction = RequestBuilder
				.getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
						autoRenewalPeriod, contractMemo, adminPubKey);

		transaction = TransactionSigner.signTransactionComplexWithSigMap(
				transaction, keyList, pubKey2privKeyMap);
		return transaction;
	}

	public static long getFileMaxFee() {
		return CryptoServiceTest.getUmbrellaProperties().getLong("fileMaxFee", 800_000_000L);
	}

	public static long getContractMaxFee() {
		return CryptoServiceTest.getUmbrellaProperties().getLong("contractMaxFee", 60_00_000_000L);
	}

	public static long getCryptoMaxFee() {
		return CryptoServiceTest.getUmbrellaProperties().getLong("cryptoMaxFee", 5_00_000_000L);
	}
}
