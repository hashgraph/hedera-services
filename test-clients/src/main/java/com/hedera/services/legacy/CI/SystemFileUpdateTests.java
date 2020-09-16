package com.hedera.services.legacy.CI;

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
import com.hedera.services.legacy.regression.BaseFeeTests;
import com.hedera.services.legacy.regression.FeeUtility;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.client.test.ClientBaseThread;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Class for testing System File Updates
 * -Negative test for invalid system file
 * -System File update using special account
 * -System File Append test
 * -System File update with not a special account
 *
 * @author Tirupathi Mandala Created on 2019-07-27
 */
public class SystemFileUpdateTests extends BaseFeeTests {

	private static final Logger log = LogManager.getLogger(SystemFileUpdateTests.class);
	private static String testConfigFilePath = "config/umbrellaTest.properties";
	private static AccountID specialAccountID = AccountID.newBuilder().setAccountNum(
			CryptoServiceTest.specialAccountNum).setRealmNum(0).setShardNum(0).build();
	private static Key aKey;
	private static long fileDuration;

	public SystemFileUpdateTests(String testConfigFilePath) {
		super(testConfigFilePath);
	}


	public static void main(String[] args) throws Throwable {
		Properties properties = TestHelper.getApplicationProperties();
		fileDuration = Long.parseLong(properties.getProperty("FILE_DURATION"));

		SystemFileUpdateTests tester = new SystemFileUpdateTests(testConfigFilePath);
		tester.setup(args);
		aKey = tester.setupSpecialAccount(specialAccountID);
		// Fee Schedule File update test cases
		tester.feeScheduleFileUpdateInvalidFileTest();
		tester.feeScheduleFileUpdateTest_NotSpecialAccount();
		tester.feeScheduleFileUpdate_Append_Test();
		//Exchange Rate File update test cases
		tester.exchangeRateUpdateTest_Invalid();
		tester.exchangeRateUpdateTest_NotSpecialAccount();
		tester.exchangeRateUpdateTest();
	}

	public void exchangeRateUpdateTest_Invalid() throws Exception {

		String localPath = "ExchangeRateProto_ONLY_NEXT.txt";
		byte[] fileContents = CommonUtils.readBinaryFileAsResource(localPath, getClass());
		log.info("FeeSchedule File Size: " + fileContents.length);
		ExchangeRateSet beforeUpdateExchangeRate = getCurrentExchangeRateFile();
		TransactionReceipt receipt = updateExchangeRateFile(specialAccountID, ByteString.copyFrom(fileContents),
				ResponseCodeEnum.OK);
		log.info("receipt: " + receipt);
		Assert.assertEquals(ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE, receipt.getStatus());
		ExchangeRateSet afterUpdateExchangeRate = getCurrentExchangeRateFile();
		log.info(" afterUpdateExchangeRate = " + afterUpdateExchangeRate);
		Assert.assertTrue(
				afterUpdateExchangeRate.getCurrentRate().getCentEquiv() == beforeUpdateExchangeRate.getCurrentRate().getCentEquiv());
		Assert.assertTrue(
				afterUpdateExchangeRate.getNextRate().getCentEquiv() == beforeUpdateExchangeRate.getNextRate().getCentEquiv());

	}

	public void exchangeRateUpdateTest_NotSpecialAccount() throws Exception {

		ExchangeRateSet existingExchangeRate = getCurrentExchangeRateFile();
		log.info(" currentExchangeRate = " + existingExchangeRate);
		long expirationTime = existingExchangeRate.getCurrentRate().getExpirationTime().getSeconds() + 100000L;
		TimestampSeconds newExpirationTime = TimestampSeconds.newBuilder().setSeconds(expirationTime).build();
		ExchangeRate toUpdateCurrentRate = ExchangeRate.newBuilder().setCentEquiv(30).setHbarEquiv(1).setExpirationTime(
				newExpirationTime).build();
		ExchangeRate toUpdateNextRate = ExchangeRate.newBuilder().setCentEquiv(35).setHbarEquiv(1).setExpirationTime(
				newExpirationTime).build();
		ExchangeRateSet.Builder builder = existingExchangeRate.toBuilder();
		builder.setCurrentRate(toUpdateCurrentRate);
		builder.setNextRate(toUpdateNextRate);
		ExchangeRateSet newExchangeRateSet = builder.build();
		TransactionReceipt receipt = updateExchangeRateFile(account_1, newExchangeRateSet.toByteString(),
				ResponseCodeEnum.AUTHORIZATION_FAILED);

	}

	public void exchangeRateUpdateTest() throws Exception {
		ExchangeRateSet existingExchangeRate = getCurrentExchangeRateFile();
		log.info(" currentExchangeRate = " + existingExchangeRate);
		long expirationTime = existingExchangeRate.getCurrentRate().getExpirationTime().getSeconds() + 100000L;
		TimestampSeconds newExpirationTime = TimestampSeconds.newBuilder().setSeconds(expirationTime).build();
		ExchangeRate toUpdateCurrentRate = ExchangeRate.newBuilder().setCentEquiv(30).setHbarEquiv(1).setExpirationTime(
				newExpirationTime).build();
		ExchangeRate toUpdateNextRate = ExchangeRate.newBuilder().setCentEquiv(35).setHbarEquiv(1).setExpirationTime(
				newExpirationTime).build();
		ExchangeRateSet.Builder builder = existingExchangeRate.toBuilder();
		builder.setCurrentRate(toUpdateCurrentRate);
		builder.setNextRate(toUpdateNextRate);
		ExchangeRateSet newExchangeRateSet = builder.build();
		log.info("newExchangeRateSet Before update " + newExchangeRateSet);
		TransactionReceipt receipt = updateExchangeRateFile(specialAccountID, newExchangeRateSet.toByteString(),
				ResponseCodeEnum.OK);
		log.info("receipt: " + receipt);
		Assert.assertEquals(ResponseCodeEnum.SUCCESS, receipt.getStatus());
		ExchangeRateSet afterUpdateExchangeRate = getCurrentExchangeRateFile();
		log.info(" afterUpdateExchangeRate = " + afterUpdateExchangeRate);
		Assert.assertTrue(afterUpdateExchangeRate.getCurrentRate().getCentEquiv() == 30);
		Assert.assertTrue(afterUpdateExchangeRate.getNextRate().getCentEquiv() == 35);

	}

	private ExchangeRateSet getCurrentExchangeRateFile() throws Exception {
		log.info("Get exchange rate file..");
		Response response1 = TestHelper.getFileContent(CryptoServiceTest.stub,
				FileID.newBuilder().setFileNum(FeeUtility.EXCHANGE_RATE_FILE_ACCOUNT_NUM).build(),
				specialAccountID, queryPayerKeyPair, nodeID);
		Assert.assertTrue(response1.hasFileGetContents());
		Assert.assertNotNull(response1.getFileGetContents().getFileContents());
		Assert.assertNotNull(response1.getFileGetContents().getFileContents().getContents());
		byte[] fileData = response1.getFileGetContents().getFileContents().getContents().toByteArray();
		ExchangeRateSet exchangeRateSet = ExchangeRateSet.parseFrom(fileData);
		Assert.assertTrue(exchangeRateSet.getCurrentRate().getHbarEquiv() >= 0);
		Assert.assertTrue(exchangeRateSet.getCurrentRate().getCentEquiv() >= 0);
		Assert.assertTrue(exchangeRateSet.getNextRate().getHbarEquiv() >= 0);
		Assert.assertTrue(exchangeRateSet.getNextRate().getCentEquiv() >= 0);
		Assert.assertTrue(exchangeRateSet.getCurrentRate().hasExpirationTime());
		Assert.assertTrue(exchangeRateSet.getNextRate().hasExpirationTime());
		log.info("Asserted current exchange rate file..");
		return exchangeRateSet;
	}


	private TransactionReceipt updateExchangeRateFile(AccountID accountID, ByteString exchangeRateSet,
			ResponseCodeEnum precheckCode)
			throws Exception {
		log.info("Update current exchange rate file..");

		FileID fid = FileID.newBuilder().setFileNum(FeeUtility.EXCHANGE_RATE_FILE_ACCOUNT_NUM).setRealmNum(
				0).setShardNum(0).build();
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(fileDuration + 30);

		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(accountID.getAccountNum(),
				accountID.getRealmNum(), accountID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, fileExp,
				CryptoServiceTest.transactionDuration, true,
				"FileUpdate",
				CryptoServiceTest.signatures, exchangeRateSet, fid);

		List<Key> keys = new ArrayList<Key>();
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(accountID));
		// FeeSchedule file key is same as SpecialAccount key i.e. genesis  key
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(accountID));
		Transaction txSigned = TransactionSigner
				.signTransactionComplex(FileUpdateRequest, keys, TestHelperComplex.pubKey2privKeyMap);

		TransactionResponse response = CryptoServiceTest.stub.updateFile(txSigned);
		Assert.assertEquals(precheckCode, response.getNodeTransactionPrecheckCode());
		if (ResponseCodeEnum.OK.equals(response.getNodeTransactionPrecheckCode())) {
			TransactionBody body = TransactionBody.parseFrom(FileUpdateRequest.getBodyBytes());
			TransactionReceipt txReceipt = TestHelper.getTxReceipt(body.getTransactionID(), CryptoServiceTest.cstub);
			return txReceipt;
		} else {
			log.info("PreCheck response=" + response.getNodeTransactionPrecheckCode());
			return null;
		}
	}


	public void feeScheduleFileUpdateInvalidFileTest() throws Throwable {

		String localPath = "feeSchedule_ONLY_CURRENT.txt";
		byte[] fileContents = CommonUtils.readBinaryFileAsResource(localPath, getClass());
		log.info("FeeSchedule File Size: " + fileContents.length);

		String memo = "File Update";
		ByteString fileData = ByteString.copyFrom(fileContents);
		FileID fid = FileID.newBuilder().setFileNum(FeeUtility.FEE_FILE_ACCOUNT_NUM).setRealmNum(0).setShardNum(
				0).build();
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(fileDuration + 30);
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(specialAccountID.getAccountNum(),
				specialAccountID.getRealmNum(), specialAccountID.getShardNum(), nodeID.getAccountNum(),
				nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, fileExp,
				CryptoServiceTest.transactionDuration, true,
				"FileUpdate",
				CryptoServiceTest.signatures, fileData, fid);

		List<Key> keys = new ArrayList<Key>();
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(specialAccountID));
		// FeeSchedule file key is same as SpecialAccount key i.e. genesis  key
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(specialAccountID));
		Transaction txSigned = TransactionSigner
				.signTransactionComplex(FileUpdateRequest, keys, TestHelperComplex.pubKey2privKeyMap);

		TransactionResponse response = CryptoServiceTest.stub.updateFile(txSigned);
		Thread.sleep(NAP);
		log.info("response=" + response);
		TransactionBody body = TransactionBody.parseFrom(FileUpdateRequest.getBodyBytes());
		TransactionID txId = body.getTransactionID();
		cache.addTransactionID(txId);
		TransactionReceipt receipt = getTxReceipt(txId);
		log.info("receipt =" + receipt);
		Assert.assertEquals("SUCCESS", receipt.getStatus().name());
		//check expected cryptoCreate fee after FeeSchedule file update
		long expectedCryptoFee = 8335410;
		long cryptoCreateFee = cryptoCreateAccountFee();
		long feeVariance = (cryptoCreateFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = expectedCryptoFee + feeVariance;
		long minTransactionFee = expectedCryptoFee - feeVariance;
		if (cryptoCreateFee != 0) {
			Assert.assertTrue(maxTransactionFee > cryptoCreateFee);
			Assert.assertTrue(minTransactionFee < cryptoCreateFee);
		}
	}

	public void feeScheduleFileUpdate_Append_Test() throws Throwable {

		String localPath = "feeSchedule_CHANGED.txt";
		byte[] fileContents = CommonUtils.readBinaryFileAsResource(localPath, getClass());
		int arrSize = fileContents.length;
		log.info("FeeSchedule File Size: " + arrSize);
		byte[] fileContentsPart1 = Arrays.copyOfRange(fileContents, 0, (arrSize + 1) / 2);
		byte[] fileContentsPart2 = Arrays.copyOfRange(fileContents, (arrSize + 1) / 2, arrSize);

		ByteString fileData = ByteString.copyFrom(fileContentsPart1);
		FileID fid = FileID.newBuilder().setFileNum(FeeUtility.FEE_FILE_ACCOUNT_NUM).setRealmNum(0).setShardNum(
				0).build();
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(fileDuration + 30);
		List<Key> waclPubKeyList = new ArrayList<>();
		waclPubKeyList.add(aKey);
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(specialAccountID.getAccountNum(),
				specialAccountID.getRealmNum(), specialAccountID.getShardNum(), nodeID.getAccountNum(),
				nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, fileExp,
				CryptoServiceTest.transactionDuration, true,
				"FileUpdate",
				CryptoServiceTest.signatures, fileData, fid);

		List<Key> keys = new ArrayList<Key>();
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(specialAccountID));
		// FeeSchedule file key is same as SpecialAccount key i.e. genesis  key
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(specialAccountID));
		Transaction txSigned = TransactionSigner
				.signTransactionComplex(FileUpdateRequest, keys, TestHelperComplex.pubKey2privKeyMap);

		TransactionResponse response = CryptoServiceTest.stub.updateFile(txSigned);
		Thread.sleep(NAP);
		TransactionBody body = TransactionBody.parseFrom(FileUpdateRequest.getBodyBytes());
		TransactionID txId = body.getTransactionID();
		cache.addTransactionID(txId);
		TransactionReceipt receipt = getTxReceipt(txId);
		log.info("Update receipt =" + receipt);
		Assert.assertEquals("SUCCESS", receipt.getStatus().name());
		Thread.sleep(NAP);
		Timestamp timestamp1 = TestHelperComplex.getDefaultCurrentTimestampUTC();
		ByteString fileDataPart2 = ByteString.copyFrom(fileContentsPart2);
		Transaction fileAppendRequest = RequestBuilder.getFileAppendBuilder(specialAccountID.getAccountNum(),
				specialAccountID.getRealmNum(), specialAccountID.getShardNum(), nodeID.getAccountNum(),
				nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp1, CryptoServiceTest.transactionDuration,
				true,
				"FileAppend",
				CryptoServiceTest.signatures, fileDataPart2, fid);
		Transaction txSignedAppend = TransactionSigner
				.signTransactionComplex(fileAppendRequest, keys, TestHelperComplex.pubKey2privKeyMap);
		TransactionResponse appendResponse = CryptoServiceTest.stub.appendContent(txSignedAppend);
		Thread.sleep(NAP);
		body = TransactionBody.parseFrom(fileAppendRequest.getBodyBytes());
		TransactionID appendTxId = body.getTransactionID();
		//   cache.addTransactionID(appendTxId);
		TransactionReceipt appendReceipt = getTxReceipt(appendTxId);
		log.info("Append receipt =" + appendReceipt);
		Assert.assertEquals("SUCCESS", appendReceipt.getStatus().name());

		//check expected cryptoCreate fee after FeeSchedule file update
		long expectedCryptoFee = 82480366;
		long cryptoCreateFee = cryptoCreateAccountFee();
		long feeVariance = (cryptoCreateFee * FEE_VARIANCE_PERCENT) / 100;
		long maxTransactionFee = expectedCryptoFee + feeVariance;
		long minTransactionFee = expectedCryptoFee - feeVariance;
		if (cryptoCreateFee != 0) {
			Assert.assertTrue(maxTransactionFee > cryptoCreateFee);
			Assert.assertTrue(minTransactionFee < cryptoCreateFee);
		}
	}

	public void feeScheduleFileUpdateTest_NotSpecialAccount() throws Throwable {

		String localPath = "feeSchedule.txt";
		byte[] fileContents = CommonUtils.readBinaryFileAsResource(localPath, getClass());
		log.info("FeeSchedule File Size: " + fileContents.length);
		int arrSize = fileContents.length;
		log.info("FeeSchedule File Size: " + arrSize);
		byte[] fileContentsPart1 = Arrays.copyOfRange(fileContents, 0, (arrSize + 1) / 2);
		String memo = "File Update";
		ByteString fileData = ByteString.copyFrom(fileContentsPart1);
		FileID fid = FileID.newBuilder().setFileNum(FeeUtility.FEE_FILE_ACCOUNT_NUM).setRealmNum(0).setShardNum(
				0).build();
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(fileDuration + 30);
		List<Key> waclPubKeyList = new ArrayList<>();
		waclPubKeyList.add(aKey);
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(payerID.getAccountNum(),
				payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, fileExp,
				CryptoServiceTest.transactionDuration, true,
				"FileUpdate",
				CryptoServiceTest.signatures, fileData, fid);

		List<Key> keys = new ArrayList<Key>();
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(payerID));
		keys.add(TestHelperComplex.acc2ComplexKeyMap.get(specialAccountID)); // Fee Schedule File wacl key
		Transaction txSigned = TransactionSigner
				.signTransactionComplex(FileUpdateRequest, keys, TestHelperComplex.pubKey2privKeyMap);
		TransactionResponse response = CryptoServiceTest.stub.updateFile(txSigned);
		Thread.sleep(NAP);
		Assert.assertEquals(ResponseCodeEnum.AUTHORIZATION_FAILED, response.getNodeTransactionPrecheckCode());
	}

	private Key setupSpecialAccount(AccountID specialAccountID) throws Throwable {
		TestHelperComplex.acc2ComplexKeyMap.put(specialAccountID,
				TestHelperComplex.acc2ComplexKeyMap.get(CryptoServiceTest.genesisAccountID));

		//Transferring 100000000 to special account 50
		Transaction transfer1 = CryptoServiceTest.getSignedTransferTx(CryptoServiceTest.genesisAccountID, nodeID,
				CryptoServiceTest.genesisAccountID,
				specialAccountID,
				1000000000000000L, "Transfer to acc 50");
		TransactionResponse transferRes = CryptoServiceTest.cstub.cryptoTransfer(transfer1);
		Thread.sleep(NAP);
		KeyPairObj genesisKeyPair = CryptoServiceTest.genesisAccountList.get(0).getKeyPairList().get(0);
		String pubKeyHex = genesisKeyPair.getPublicKeyAbyteStr();

		//Add genesis key to maps
		Key aNewkey = Key.newBuilder().setEd25519(ByteString.copyFrom(ClientBaseThread.hexToBytes(pubKeyHex)))
				.build();
		if (KeyExpansion.USE_HEX_ENCODED_KEY) {
			pubKeyHex = aNewkey.getEd25519().toStringUtf8();
		} else {
			byte[] pubKeyBytes = aNewkey.getEd25519().toByteArray();
			pubKeyHex = Common.bytes2Hex(pubKeyBytes);
		}
		TestHelperComplex.pubKey2privKeyMap.put(pubKeyHex, genesisKeyPair.getPrivateKey());
		//Add key of account55
		TestHelperComplex.acc2ComplexKeyMap.put(specialAccountID,
				Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(aNewkey)).build());
		//END of Add genesis key to maps
		long balance = getAccountBalance(specialAccountID, payerID, nodeID);
		log.info("Special Account Balance = " + balance);
		return aNewkey;
	}

	public long cryptoCreateAccountFee() throws Throwable {
		Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
		long durationSeconds = 30 * 24 * 60 * 60; //1 Month (30 Days)
		Key key = genComplexKey("single");
		Transaction createAccountRequest = TestHelperComplex
				.createAccount(payerID, payerKey, nodeID, key, 1000L, getCryptoMaxFee(),
						false, 1, durationSeconds);
		TransactionResponse response = CryptoServiceTest.cstub.createAccount(createAccountRequest);
		Thread.sleep(NAP);
		long transactionFee = getTransactionFee(createAccountRequest);
		log.info("Crypto Create Account Fee :" + transactionFee);
		return transactionFee;
	}
}
