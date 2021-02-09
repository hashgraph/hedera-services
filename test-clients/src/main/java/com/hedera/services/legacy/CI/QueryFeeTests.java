package com.hedera.services.legacy.CI;

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
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.regression.BaseFeeTests;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;

import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Test Client for Query fee tests
 *
 * @author Tirupathi Mandala Created on 2019-06-28
 */
public class QueryFeeTests extends BaseFeeTests {

	private static final Logger log = LogManager.getLogger(QueryFeeTests.class);
	private static String testConfigFilePath = "config/umbrellaTest.properties";

	public QueryFeeTests(String testConfigFilePath) {
		super(testConfigFilePath);
	}

	public static void main(String[] args) throws Throwable {
		QueryFeeTests tester = new QueryFeeTests(testConfigFilePath);
		tester.setup(args);

		tester.cryptoGetAccountBalanceFeeTest();
		tester.cryptoGetBalanceFeeTest_multiSig();
		tester.cryptoGetAccountInfoFeeTest();
		tester.cryptoGetAccountInfoFeeTest_multiSig();
		tester.cryptoGetFileFeeTest();
		tester.cryptoGetFileFeeTest_MultiSig();
		tester.getTxRecordByTxIdFeeTest(1, 10);
		tester.getTxRecordByTxIdFeeTest(10, 10);
		tester.cryptoGetFileContentFeeTest(1, 10, 30);
		tester.cryptoGetFileContentFeeTest(10, 10, 30);
		tester.cryptoGetTransactionRecordsByAccountId(1);
		tester.cryptoGetTransactionRecordsByAccountId(5);

		log.info("------------ Test Results --------------");
		testResults.stream().forEach(a -> log.info(a));
	}

	public void cryptoGetAccountBalanceFeeTest() throws Throwable {
		String memo = TestHelperComplex.getStringMemo(1);
		long payerAccountBalance_before = getAccountBalance(account_3, queryPayerId, nodeID);
		long balanceQueryCost = FeeClient.getBalanceQueryFee();
		Transaction paymentTxSigned = CryptoServiceTest.getQueryPaymentSignedWithFee(account_3, nodeID, memo, balanceQueryCost);

		Query cryptoGetBalanceQuery = RequestBuilder.getCryptoGetBalanceQuery(payerID, paymentTxSigned,
				ResponseType.COST_ANSWER);

		Response getBalanceResponse = CryptoServiceTest.cstub.cryptoGetBalance(cryptoGetBalanceQuery);
		// getBalance Fee + Transfer Fee

		log.info("Pre Check Response of getAccountBalance:: "
				+ getBalanceResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
		long balanceQueryFee = getTransactionFee(paymentTxSigned);
		// 83350 Transaction Fee

		log.info("paymentTxSigned=" + paymentTxSigned.getSigMap().getSigPairCount());
		String result = "From Transaction Crypto GetBalance Sig=1, memo=1 :" + balanceQueryFee;
		testResults.add(result);
		log.info(result);
		String result1 = "From Record Crypto GetBalance Sig=1, memo=1 :"
				+ getBalanceResponse.getTransactionGetRecord().getTransactionRecord().getTransactionFee();
		testResults.add(result1);
		String result2 = "From Header Crypto GetBalance Sig=1, memo=1 :"
				+ getBalanceResponse.getCryptogetAccountBalance().getHeader().getCost();
		testResults.add(result2);
		Thread.sleep(NAP + 300);
		long payerAccountBalance_after = getAccountBalance(account_3, queryPayerId, nodeID);
		// Diff: -250083350 = (250000000 + 83350)
		log.info("payerAccountBalance_before=" + payerAccountBalance_before);
		log.info("payerAccountBalance_after=" + payerAccountBalance_after);
		log.info("Balance Diff: " + (payerAccountBalance_after - payerAccountBalance_before));
		long feeVariance = (balanceQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = QUERY_GET_ACCOUNT_BALANCE_SIG_1 + feeVariance;
		long minTransactionFee = QUERY_GET_ACCOUNT_BALANCE_SIG_1 - feeVariance;
		if (paymentTxSigned.getSigMap().getSigPairCount() == 1) {
			Assert.assertTrue(maxTransactionFee >= balanceQueryFee);
			Assert.assertTrue(minTransactionFee <= balanceQueryFee);
		}

	}

	public void cryptoGetBalanceFeeTest_multiSig() throws Throwable {
		long durationSeconds = 90 * 24 * 60 * 60; // 1 Month (30 Days)
		AccountID newAccountID = getMultiSigAccount(10, 10, durationSeconds);
		if (newAccountID == null) {
			return;
		}
		String memo = TestHelperComplex.getStringMemo(10);
		long balanceQueryCost = FeeClient.getBalanceQueryFee();
		Transaction paymentTxSigned = CryptoServiceTest.getQueryPaymentSignedWithFee(newAccountID, nodeID, memo, balanceQueryCost);

		Query cryptoGetBalanceQuery = RequestBuilder.getCryptoGetBalanceQuery(newAccountID, paymentTxSigned,
				ResponseType.COST_ANSWER);
		Response getBalanceResponse = CryptoServiceTest.cstub.cryptoGetBalance(cryptoGetBalanceQuery);
		log.info("Pre Check Response of getAccountBalance:: "
				+ getBalanceResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
		long balanceQueryFee = getTransactionFee(paymentTxSigned);
		log.info("paymentTxSigned=" + paymentTxSigned.getSigMap().getSigPairCount());
		String result = "Crypto GetBalance Sig=10, memo=10 :" + balanceQueryFee;
		testResults.add(result);
		String result2 = "From Header Crypto GetBalance Sig=10, memo=10 :"
				+ getBalanceResponse.getCryptogetAccountBalance().getHeader().getCost();
		testResults.add(result2);

		log.info(result);

		long feeVariance = (balanceQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = QUERY_GET_ACCOUNT_BALANCE_SIG_10 + feeVariance;
		long minTransactionFee = QUERY_GET_ACCOUNT_BALANCE_SIG_10 - feeVariance;
		if (paymentTxSigned.getSigMap().getSigPairCount() != 0) {
			Assert.assertTrue(maxTransactionFee >= balanceQueryFee);
			Assert.assertTrue(minTransactionFee <= balanceQueryFee);
		}
	}

	public void cryptoGetAccountInfoFeeTest() throws Throwable {
		String memo = TestHelperComplex.getStringMemo(1);
		long balanceQueryCost = TestHelper.getCryptoMaxFee();
		Transaction paymentTxSigned = CryptoServiceTest.getQueryPaymentSignedWithFee(payerID, nodeID, memo, balanceQueryCost);
		Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(payerID, paymentTxSigned,
				ResponseType.ANSWER_ONLY);
		log.info("\n-----------------------------------\ngetAccountInfo: request = " + cryptoGetInfoQuery);
		Response getInfoResponse = CryptoServiceTest.cstub.getAccountInfo(cryptoGetInfoQuery);
		log.info("Pre Check Response of getAccountInfo:: "
				+ getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(getInfoResponse);
		Assert.assertNotNull(getInfoResponse.getCryptoGetInfo());
		log.info("getInfoResponse :: " + getInfoResponse.getCryptoGetInfo());

		AccountInfo accInfo = getInfoResponse.getCryptoGetInfo().getAccountInfo();
		log.info("accInfo : " + accInfo);
		long accInfoQueryFee = getInfoResponse.getCryptoGetInfo().getHeader().getCost();
		log.info("paymentTxSigned=" + paymentTxSigned.getSigMap().getSigPairCount());
		String result = "Crypto GetInfo Sig=1, memo=1 :" + getTransactionFee(paymentTxSigned);
		testResults.add(result);
		String result2 = "From Header Crypto GetInfo Sig=1, memo=1 :"
				+ getInfoResponse.getCryptoGetInfo().getHeader().getCost();
		testResults.add(result2);

		log.info(result);

		long feeVariance = (accInfoQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = QUERY_GET_ACCOUNT_INFO_SIG_1 + feeVariance;
		long minTransactionFee = QUERY_GET_ACCOUNT_INFO_SIG_1 - feeVariance;
		if (paymentTxSigned.getSigMap().getSigPairCount() != 0) {
			Assert.assertTrue(maxTransactionFee >= accInfoQueryFee);
			Assert.assertTrue(minTransactionFee <= accInfoQueryFee);
		}

	}

	public void cryptoGetAccountInfoFeeTest_multiSig() throws Throwable {

		long durationSeconds = CustomPropertiesSingleton.getInstance().getAccountDuration();
		AccountID newAccountID = getMultiSigAccount(10, 10, durationSeconds);
		if (newAccountID == null) {
			return;
		}
		String memo = TestHelperComplex.getStringMemo(10);
		long balanceQueryCost = TestHelper.getCryptoMaxFee();
		Transaction paymentTxSigned = CryptoServiceTest.getQueryPaymentSignedWithFee(payerID, nodeID, memo, balanceQueryCost);
		Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(newAccountID, paymentTxSigned,
				ResponseType.ANSWER_ONLY);
		log.info("\n-----------------------------------\ngetAccountInfo: request = " + cryptoGetInfoQuery);
		Response getInfoResponse = CryptoServiceTest.cstub.getAccountInfo(cryptoGetInfoQuery);
		log.info("Pre Check Response of getAccountInfo:: "
				+ getInfoResponse.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(getInfoResponse);
		Assert.assertNotNull(getInfoResponse.getCryptoGetInfo());
		log.info("getInfoResponse :: " + getInfoResponse.getCryptoGetInfo());

		AccountInfo accInfo = getInfoResponse.getCryptoGetInfo().getAccountInfo();
		log.info("accInfo : " + accInfo);
		long balanceQueryFee = getInfoResponse.getCryptoGetInfo().getHeader().getCost();
		log.info("paymentTxSigned=" + paymentTxSigned.getSigMap().getSigPairCount());
		String result = "Crypto GetInfo Sig=10, memo=10 :" + getTransactionFee(paymentTxSigned);
		testResults.add(result);
		String result2 = "From Header Crypto GetInfo Sig=10, memo=10 :"
				+ getInfoResponse.getCryptoGetInfo().getHeader().getCost();
		testResults.add(result2);

		log.info(result);

		long feeVariance = (balanceQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = QUERY_GET_ACCOUNT_INFO_SIG_10 + feeVariance;
		long minTransactionFee = QUERY_GET_ACCOUNT_INFO_SIG_10 - feeVariance;
		if (paymentTxSigned.getSigMap().getSigPairCount() != 0) {
			Assert.assertTrue(maxTransactionFee >= balanceQueryFee);
			Assert.assertTrue(minTransactionFee <= balanceQueryFee);
		}

	}

	public void cryptoGetFileFeeTest() throws Throwable {

		List<Key> payerKeyList = new ArrayList<>();
		payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));

		byte[] fileContents = new byte[4];
		random.nextBytes(fileContents);
		ByteString fileData = ByteString.copyFrom(fileContents);
		List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
		long durationSeconds = CustomPropertiesSingleton.getInstance().getFileDurtion();
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
		String memo = TestHelperComplex.getStringMemo(1);
		Transaction fileCreateRequest = fit.createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
		TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
		log.info("FileCreate Response :: " + response);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
		Thread.sleep(NAP);

		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(fileCreateRequest);
		TransactionID txId = body.getTransactionID();
		// get the file ID
		TransactionReceipt receipt = getTxReceipt(txId);
		if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
			throw new Exception("Create file failed! The receipt retrieved receipt=" + receipt);
		}
		FileID fid = receipt.getFileID();
		log.info("GetTxReceipt: file ID = " + fid);
		AccountID newAccountID = getMultiSigAccount(1, 10, durationSeconds);
		long queryCost = TestHelper.getCryptoMaxFee();
		Transaction paymentTxSigned = CryptoServiceTest.getQueryPaymentSignedWithFee(newAccountID, nodeID, memo, queryCost);
		Query fileGetInfoQuery = RequestBuilder.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
		log.info("\n-----------------------------------");
		log.info("fileGetInfoQuery: query = " + fileGetInfoQuery);

		Response fileInfoResp = CryptoServiceTest.stub.getFileInfo(fileGetInfoQuery);

		ResponseCodeEnum code = fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode();
		if (code != ResponseCodeEnum.OK) {
			throw new Exception("Precheck error geting file info! Precheck code = " + code.name()
					+ "\nfileGetInfoQuery=" + fileGetInfoQuery);
		}
		FileInfo fileInfo = fileInfoResp.getFileGetInfo().getFileInfo();
		log.info("fileGetInfoQuery: Sign Count = " + paymentTxSigned.getSigMap().getSigPairCount());
		long fileInfoQueryFee = getTransactionFee(paymentTxSigned);
		String result = "File Get Info Sig=1, memo=1 :" + getTransactionFee(paymentTxSigned);
		testResults.add(result);
		log.info(result);
		String result2 = "From Header File Get Info Sig=1, memo=1 :"
				+ fileInfoResp.getFileGetInfo().getHeader().getCost();
		testResults.add(result2);

		long feeVariance = (fileInfoQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = QUERY_GET_FILE_INFO_SIG_1 + feeVariance;
		long minTransactionFee = QUERY_GET_FILE_INFO_SIG_1 - feeVariance;
		if (fileInfoQueryFee != 0) {
			Assert.assertTrue(maxTransactionFee > fileInfoQueryFee);
			Assert.assertTrue(minTransactionFee < fileInfoQueryFee);
		}
	}

	public void cryptoGetFileFeeTest_MultiSig() throws Throwable {

		List<Key> payerKeyList = new ArrayList<>();
		payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));

		byte[] fileContents = new byte[64];
		random.nextBytes(fileContents);
		ByteString fileData = ByteString.copyFrom(fileContents);
		List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
		long durationSeconds = 90 * 24 * 60 * 60; // 1 Day
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
		String memo = TestHelperComplex.getStringMemo(10);
		Transaction fileCreateRequest = fit.createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
		TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
		log.info("FileCreate Response :: " + response);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
		Thread.sleep(NAP);

		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(fileCreateRequest);
		TransactionID txId = body.getTransactionID();
		// get the file ID
		TransactionReceipt receipt = getTxReceipt(txId);
		if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
			throw new Exception("Create file failed! The receipt retrieved receipt=" + receipt);
		}
		FileID fid = receipt.getFileID();
		log.info("GetTxReceipt: file ID = " + fid);
		AccountID newAccountID = getMultiSigAccount(10, 10, durationSeconds);
		long queryCost = TestHelper.getCryptoMaxFee();
		Transaction paymentTxSigned = CryptoServiceTest.getQueryPaymentSignedWithFee(newAccountID, nodeID, memo, queryCost);
		Query fileGetInfoQuery = RequestBuilder.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.COST_ANSWER);
		log.info("\n-----------------------------------");
		log.info("fileGetInfoQuery: query = " + fileGetInfoQuery);

		Response fileInfoResp = CryptoServiceTest.stub.getFileInfo(fileGetInfoQuery);

		ResponseCodeEnum code = fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode();
		if (code != ResponseCodeEnum.OK) {
			throw new Exception("Precheck error geting file info! Precheck code = " + code.name()
					+ "\nfileGetInfoQuery=" + fileGetInfoQuery);
		}
		FileInfo fileInfo = fileInfoResp.getFileGetInfo().getFileInfo();
		log.info("fileGetInfoQuery: info = " + fileInfo);
		long fileInfoQueryFee = getTransactionFee(paymentTxSigned);
		String result = "File Get Info Sig=10, memo=10 :" + fileInfoQueryFee;
		testResults.add(result);
		log.info(result);
		String result2 = "From Header File Get Info Sig=10, memo=10 :"
				+ fileInfoResp.getFileGetInfo().getHeader().getCost();
		testResults.add(result2);
		long feeVariance = (fileInfoQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = QUERY_GET_FILE_INFO_SIG_10 + feeVariance;
		long minTransactionFee = QUERY_GET_FILE_INFO_SIG_10 - feeVariance;
		if (fileInfoQueryFee != 0) {
			Assert.assertTrue(maxTransactionFee > fileInfoQueryFee);
			Assert.assertTrue(minTransactionFee < fileInfoQueryFee);
		}

	}

	public void getTxRecordByTxIdFeeTest(int keyCount, int memoSize) throws Exception {
		String memo = TestHelperComplex.getStringMemo(memoSize);
		long durationSeconds = CustomPropertiesSingleton.getInstance().getAccountDuration();
		Transaction newAccountTransaction = getMultiSigAccountTransaction(keyCount, memoSize, durationSeconds);
		AccountID newAccountID = getAccountID(newAccountTransaction);
		Assert.assertNotNull(newAccountTransaction);
		// get tx record of payer account by txId
		log.info("Get Tx record by Tx Id...");
		long queryFeeForTxRecord = FeeClient.getCostForGettingTxRecord();
		TransactionBody body = TransactionBody.parseFrom(newAccountTransaction.getBodyBytes());
		Transaction transferTransaction = CryptoServiceTest.getSignedTransferTx(newAccountID, nodeID, newAccountID, nodeID,
				queryFeeForTxRecord, memo);

		Query query = RequestBuilder.getTransactionGetRecordQuery(body.getTransactionID(), transferTransaction,
				ResponseType.COST_ANSWER);

		Response transactionRecord = CryptoServiceTest.cstub.getTxRecordByTxID(query);
		Assert.assertNotNull(transactionRecord);
		Assert.assertEquals(ResponseCodeEnum.OK,
				transactionRecord.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
		log.info("transactionRecord=" + transactionRecord);
		long transactionRecordQueryFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
		String result = "Get TxRecordByTxId Sig=" + keyCount + ", memo=" + memoSize + " :"
				+ getTransactionFee(transferTransaction);
		testResults.add(result);
		String result1 = "From Record Get TxRecordByTxId Sig=" + keyCount + ", memo=" + memoSize + " :"
				+ transactionRecord.getTransactionGetRecord().getTransactionRecord().getTransactionFee();
		testResults.add(result1);
		String result2 = "From Header Get TxRecordByTxId Sig=" + keyCount + ", memo=" + memoSize + " :"
				+ transactionRecord.getTransactionGetRecord().getHeader().getCost();
		testResults.add(result2);

		log.info(result);
		long feeVariance = (transactionRecordQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = 0;
		long minTransactionFee = 0;
		if (keyCount == 1) {
			maxTransactionFee = QUERY_GET_TX_RECORD_BY_TX_ID_SIG_1 + feeVariance;
			minTransactionFee = QUERY_GET_TX_RECORD_BY_TX_ID_SIG_1 - feeVariance;
		} else if (keyCount == 10) {
			maxTransactionFee = QUERY_GET_TX_RECORD_BY_TX_ID_SIG_10 + feeVariance;
			minTransactionFee = QUERY_GET_TX_RECORD_BY_TX_ID_SIG_10 - feeVariance;
		} else {
			return;
		}
		if (transferTransaction.getSigMap().getSigPairCount() != 0) {
			Assert.assertTrue(maxTransactionFee >= transactionRecordQueryFee);
			Assert.assertTrue(minTransactionFee <= transactionRecordQueryFee);
		}
	}

	public void cryptoGetFileContentFeeTest(int keyCount, int memoSize, int durationDays) throws Throwable {

		List<Key> payerKeyList = new ArrayList<>();
		payerKeyList.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));

		byte[] fileContents = new byte[4];
		random.nextBytes(fileContents);
		ByteString fileData = ByteString.copyFrom(fileContents);
		List<Key> waclPubKeyList = fit.genWaclComplex(1, "single");
		long durationSeconds = durationDays * 24 * 60 * 60; // 1 Day
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(durationSeconds);
		String memo = TestHelperComplex.getStringMemo(1);
		Transaction fileCreateRequest = fit.createFile(payerID, nodeID, fileData, waclPubKeyList, fileExp, memo);
		TransactionResponse response = CryptoServiceTest.stub.createFile(fileCreateRequest);
		log.info("FileCreate Response :: " + response);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
		Thread.sleep(NAP);

		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(fileCreateRequest);
		TransactionID txId = body.getTransactionID();
		// get the file ID
		TransactionReceipt receipt = getTxReceipt(txId);
		if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
			throw new Exception("Create file failed! The receipt retrieved receipt=" + receipt);
		}
		FileID fid = receipt.getFileID();
		log.info("GetTxReceipt: file ID = " + fid);
		AccountID newAccountID = getMultiSigAccount(keyCount, memoSize, durationSeconds);
		Assert.assertNotNull(newAccountID);
		long payerAccountBalance_before = getAccountBalance(newAccountID, queryPayerId, nodeID);
		long queryCost = FeeClient.getFileContentQueryFee(fileContents.length);
		Transaction paymentTxSigned = CryptoServiceTest.getQueryPaymentSignedWithFee(newAccountID, nodeID, memo, queryCost);

		Query fileGetContentQuery = RequestBuilder.getFileGetContentBuilder(paymentTxSigned, fid,
				ResponseType.COST_ANSWER);
		log.info("\n-----------------------------------");
		log.info("fileGetInfoQuery: query = " + fileGetContentQuery);

		Response fileContentResp = CryptoServiceTest.stub.getFileContent(fileGetContentQuery);

		ResponseCodeEnum code = fileContentResp.getFileGetContents().getHeader().getNodeTransactionPrecheckCode();
		if (code != ResponseCodeEnum.OK) {
			throw new Exception("Precheck error geting file info! Precheck code = " + code.name()
					+ "\nfileGetContentQuery=" + fileGetContentQuery);
		}
		FileGetContentsResponse.FileContents fileContent = fileContentResp.getFileGetContents().getFileContents();
		long fileContentQueryFee = getTransactionFee(paymentTxSigned);
		String result = "File Get Content Sig=" + keyCount + ", memo=" + memoSize + " :" + fileContentQueryFee;
		testResults.add(result);
		accountKeys.put(queryPayerId, CryptoServiceTest.getAccountPrivateKeys(queryPayerId));
		String result1 = "From Record File Get Content Sig=" + keyCount + ", memo=" + memoSize + " :"
				+ getTransactionFeeFromRecord(paymentTxSigned, queryPayerId, "getFileContent");
		testResults.add(result1);
		log.info(result);
		String result2 = "From Header File Get Content Sig=" + keyCount + ", memo=" + memoSize + " :"
				+ fileContentResp.getFileGetContents().getHeader().getCost();
		testResults.add(result2);
		long feeVariance = (fileContentQueryFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = 0;
		long minTransactionFee = 0;
		if (keyCount == 1) {
			maxTransactionFee = QUERY_GET_FILE_CONTENT_SIG_1 + feeVariance;
			minTransactionFee = QUERY_GET_FILE_CONTENT_SIG_1 - feeVariance;
		} else if (keyCount == 10) {
			maxTransactionFee = QUERY_GET_FILE_CONTENT_SIG_10 + feeVariance;
			minTransactionFee = QUERY_GET_FILE_CONTENT_SIG_10 - feeVariance;
		} else {
			return;
		}
		if (paymentTxSigned.getSigMap().getSigPairCount() != 0) {
			Assert.assertTrue(maxTransactionFee > fileContentQueryFee);
			Assert.assertTrue(minTransactionFee < fileContentQueryFee);
		}
		long payerAccountBalance_after = getAccountBalance(newAccountID, queryPayerId, nodeID);
		log.info("payerAccountBalance_before=" + payerAccountBalance_before);
		log.info("payerAccountBalance_after=" + payerAccountBalance_after);
		log.info("Account Balance diff: " + (payerAccountBalance_before - payerAccountBalance_after));
	}

	public void cryptoGetTransactionRecordsByAccountId(int records) throws Throwable {
		Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(account_3);
		long durationSeconds = 30 * 24 * 60 * 60; // 1 Month (30 Days)
		CryptoServiceTest.accountKeyTypes = new String[] { "single" };
		COMPLEX_KEY_SIZE = 1;
		Key key = genComplexKey("single");

		// Create Account for Account Records (with threshold 100)
		Transaction createAccountRequest = TestHelperComplex.createAccountWithThreshold(account_3, payerKey, nodeID,
				key, 1000000000L, TestHelper.getCryptoMaxFee(), false, 10, durationSeconds, 100l, 10000l);
		Thread.sleep(NAP);
		TransactionResponse response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(createAccountRequest);
		Thread.sleep(NAP);
		TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), CryptoServiceTest.cstub);
		AccountID accountForRecords = txReceipt1.getAccountID();
		TestHelperComplex.acc2ComplexKeyMap.put(accountForRecords, key);
		for (int i = 0; i < records; i++) {
			createAccountRequest = TestHelperComplex.createAccount(accountForRecords, key, nodeID, key, 10000L,
					10000000L, false, 10, durationSeconds);
			Thread.sleep(NAP);
			response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
			log.info("The response is ::: " + response.getNodeTransactionPrecheckCode());
			body = com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(createAccountRequest);
			TransactionID transactionID = body.getTransactionID();
			if (transactionID == null || !body.hasTransactionID()) {
				log.info("Transaction is null");
				return;
			}
			Thread.sleep(NAP);
			Thread.sleep(NAP);
			Thread.sleep(NAP);
		}

		// We get fee for getting the Account Records for accountForRecords
		/*
		 * Step 1 :- Get the balance of accountForRecords Step 2 :- Get the fee for
		 * getting Account Records for accountForRecords by querying with response type
		 * as 'COST_ANSWER' Step 3 :- Now, use transfer transaction to pay the fee , use
		 * different account to pay for transfer transaction. Step 4 :- Get the balance
		 * of accountForRecords
		 */

		String memo = TestHelperComplex.getStringMemo(1);

		// Step 1 : get the balance of account_3
		long payerAccountBalance_before = getAccountBalance(accountForRecords, queryPayerId, nodeID);

		// Step 2 : Get the fee for getting account records
		long acctRecordQueryCost = 0;
		Transaction paymentTxSigned = CryptoServiceTest.getSignedTransferTx(account_2, nodeID, accountForRecords, nodeID,
				acctRecordQueryCost, memo);
		Query accountRecordCostQry = RequestBuilder.getAccountRecordsQuery(accountForRecords, paymentTxSigned,
				ResponseType.COST_ANSWER);

		Response accountRecordCostQryRes = CryptoServiceTest.cstub.getAccountRecords(accountRecordCostQry);
		log.info("Pre Check Response of accountRecordCostQry:: " + accountRecordCostQryRes.getCryptoGetAccountRecords()
				.getHeader().getNodeTransactionPrecheckCode().name());
		// get the fee from header
		acctRecordQueryCost = accountRecordCostQryRes.getCryptoGetAccountRecords().getHeader().getCost();
		log.info("Cost for getting the Account Records :: " + acctRecordQueryCost);
		String result = "Fee for " + (2 * records) + " Records query Crypto GetAccountRecord Sig=1, memo=1 :"
				+ acctRecordQueryCost;
		testResults.add(result);

		// Step 3: Actual query to get the Account Records
		// Now prepare the actual transfer transaction to pay the fee for query. The
		// payer for this transfer transaction is different account
		paymentTxSigned = CryptoServiceTest.getSignedTransferTx(account_2, nodeID, accountForRecords, nodeID, acctRecordQueryCost, memo);

		Query accountRecordQry = RequestBuilder.getAccountRecordsQuery(accountForRecords, paymentTxSigned,
				ResponseType.ANSWER_ONLY);
		Response accountRecordQryRes = CryptoServiceTest.cstub.getAccountRecords(accountRecordQry);

		log.info("No of Account Records :: " + accountRecordQryRes.getCryptoGetAccountRecords().getRecordsCount());

		log.info("payerAccountBalance_before=" + payerAccountBalance_before);
		Thread.sleep(NAP);
		Thread.sleep(NAP);
		Thread.sleep(NAP);
		Thread.sleep(NAP);
		long payerAccountBalance_after = getAccountBalance(accountForRecords, queryPayerId, nodeID);
		log.info("payerAccountBalance_before=" + payerAccountBalance_before);
		log.info("payerAccountBalance_after=" + payerAccountBalance_after);
		log.info("Balance Diff: " + (payerAccountBalance_before - payerAccountBalance_after));

		long feeVariance = (acctRecordQueryCost * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = QUERY_GET_ACCOUNT_RECORD_BY_ACCTID_ID_10 + feeVariance;
		long minTransactionFee = QUERY_GET_ACCOUNT_RECORD_BY_ACCTID_ID_10 - feeVariance;
		if (paymentTxSigned.getSigMap().getSigPairCount() == 1) {
			Assert.assertTrue(maxTransactionFee >= acctRecordQueryCost);
			Assert.assertTrue(minTransactionFee <= acctRecordQueryCost);
		}

	}

}
