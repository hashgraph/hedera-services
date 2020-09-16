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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.builder.TransactionSigner;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Update account with keys and transfer with updated keys tests.
 *
 * @author Tirupathi Mandala
 */
public class CryptoUpdateKeysOrderTests extends BaseClient {

	private static final Logger log = LogManager.getLogger(CryptoUpdateKeysOrderTests.class);
	private static String testConfigFilePath = "config/umbrellaTest.properties";

	public CryptoUpdateKeysOrderTests(String testConfigFilePath) {
		super(testConfigFilePath);
	}


	/**
	 * Test crypto update with key order and transfer.
	 */
	public void cryptoUpdate_KeyOrder_TransferTests() throws Throwable {
		// create accounts
		accountCreatBatch(3);
		AccountID payerID = payerAccounts[0];
		AccountID nodeID = nodeAccounts[0];

		AccountID toID = payerAccounts[1];
		Assert.assertNotNull(toID);
		AccountID accID = payerAccounts[2];
		Assert.assertNotNull(accID);

		// get account content
		AccountInfo accInfo = getAccountInfo(accID, payerID, nodeID);

		Key oldKey = accInfo.getKey();
		AccountID proxyAccountID = accInfo.getProxyAccountID();
		long sendRecordThreshold = accInfo.getGenerateSendRecordThreshold();
		long recvRecordThreshold = accInfo.getGenerateReceiveRecordThreshold();
		Duration autoRenewPeriod = accInfo.getAutoRenewPeriod();
		Timestamp expirationTime = accInfo.getExpirationTime();
		boolean receiverSigRequired = accInfo.getReceiverSigRequired();
		// create update tx based on content: change all fields
		Key newKey = genComplexKey("thresholdKey");
		Assert.assertNotEquals(newKey, oldKey);
		AccountID proxyAccountIDMod = nodeAccounts[1];
		Assert.assertNotEquals(proxyAccountIDMod, proxyAccountID);
		long sendRecordThresholdMod = sendRecordThreshold * 2;
		long recvRecordThresholdMod = recvRecordThreshold * 2;
		Duration autoRenewPeriodMod = Duration.newBuilder().setSeconds(autoRenewPeriod.getSeconds() * 2)
				.build();
		Timestamp expirationTimeMod = Timestamp.newBuilder().setSeconds(expirationTime.getSeconds() * 2)
				.setNanos(expirationTime.getNanos()).build();
		boolean receiverSigRequiredMod = !receiverSigRequired;

		// submit update tx and get acc content
		AccountInfo accInfoMod = updateAccount(accID, payerID, nodeID, newKey, proxyAccountIDMod,
				sendRecordThresholdMod, recvRecordThresholdMod,
				autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);
		log.info("accInfo before update : " + accInfo);
		log.info("accInfo after update : " + accInfoMod);

		// verify content
		Assert.assertEquals(newKey, accInfoMod.getKey());
		Assert.assertEquals(proxyAccountIDMod, accInfoMod.getProxyAccountID());
		Assert.assertEquals(sendRecordThresholdMod, accInfoMod.getGenerateSendRecordThreshold());
		Assert.assertEquals(recvRecordThresholdMod, accInfoMod.getGenerateReceiveRecordThreshold());
		Assert.assertEquals(autoRenewPeriodMod, accInfoMod.getAutoRenewPeriod());
		Assert.assertEquals(expirationTimeMod, accInfoMod.getExpirationTime());

		// transfer with one from and one to accounts, verify the balance change
		long fromBal1 = getAccountBalance(accID, payerID, nodeID);
		long toBal1 = getAccountBalance(toID, payerID, nodeID);
		long amount = 100L;

		Transaction paymentTx = getUnSignedTransferTx(payerID, nodeID, accID, toID, amount,
				"Transaction with Updated signMap");
		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key fromKey = acc2ComplexKeyMap.get(accID);
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(fromKey);
		Key toKey = acc2ComplexKeyMap.get(toID);
		keys.add(toKey);
		Transaction transferTxSigned = TransactionSigner
				.signTransactionComplex(paymentTx, keys, pubKey2privKeyMap);
		TransactionReceipt receipt = transfer(transferTxSigned);

		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
		long fromBal2 = getAccountBalance(accID, payerID, nodeID);
		long toBal2 = getAccountBalance(toID, payerID, nodeID);
		Assert.assertEquals(fromBal2, fromBal1 - amount);
		Assert.assertEquals(toBal2, toBal1 + amount);

		log.info(LOG_PREFIX + "cryptoTransfer after Key Order Update Tests: PASSED! :)");
	}

	/**
	 * Test crypto update with key order and transfer.
	 */
	public void cryptoUpdate_different_KeyOrder_TransferTests() throws Throwable {
		// create accounts
		accountCreatBatch(3);
		AccountID payerID = payerAccounts[0];
		AccountID nodeID = nodeAccounts[0];

		AccountID toID = payerAccounts[1];
		Assert.assertNotNull(toID);
		AccountID accID = payerAccounts[2];
		Assert.assertNotNull(accID);

		// get account content
		AccountInfo accInfo = getAccountInfo(accID, payerID, nodeID);

		Key oldKey = accInfo.getKey();
		AccountID proxyAccountID = accInfo.getProxyAccountID();
		long sendRecordThreshold = accInfo.getGenerateSendRecordThreshold();
		long recvRecordThreshold = accInfo.getGenerateReceiveRecordThreshold();
		Duration autoRenewPeriod = accInfo.getAutoRenewPeriod();
		Timestamp expirationTime = accInfo.getExpirationTime();
		boolean receiverSigRequired = accInfo.getReceiverSigRequired();
		// create update tx based on content: change all fields
		Key newKey = genComplexKey("thresholdKey");
		Assert.assertNotEquals(newKey, oldKey);
		AccountID proxyAccountIDMod = nodeAccounts[1];
		Assert.assertNotEquals(proxyAccountIDMod, proxyAccountID);
		long sendRecordThresholdMod = sendRecordThreshold * 2;
		long recvRecordThresholdMod = recvRecordThreshold * 2;
		Duration autoRenewPeriodMod = Duration.newBuilder().setSeconds(autoRenewPeriod.getSeconds() * 2)
				.build();
		Timestamp expirationTimeMod = Timestamp.newBuilder().setSeconds(expirationTime.getSeconds() * 2)
				.setNanos(expirationTime.getNanos()).build();
		boolean receiverSigRequiredMod = !receiverSigRequired;

		// submit update tx and get acc content
		AccountInfo accInfoMod = updateAccount(accID, payerID, nodeID, newKey, proxyAccountIDMod,
				sendRecordThresholdMod, recvRecordThresholdMod,
				autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);
		log.info("accInfo before update : " + accInfo);
		log.info("accInfo after update : " + accInfoMod);

		// verify content
		Assert.assertEquals(newKey, accInfoMod.getKey());
		Assert.assertEquals(proxyAccountIDMod, accInfoMod.getProxyAccountID());
		Assert.assertEquals(sendRecordThresholdMod, accInfoMod.getGenerateSendRecordThreshold());
		Assert.assertEquals(recvRecordThresholdMod, accInfoMod.getGenerateReceiveRecordThreshold());
		Assert.assertEquals(autoRenewPeriodMod, accInfoMod.getAutoRenewPeriod());
		Assert.assertEquals(expirationTimeMod, accInfoMod.getExpirationTime());

		// transfer with one from and one to accounts, verify the balance change
		long fromBal1 = getAccountBalance(accID, payerID, nodeID);
		long toBal1 = getAccountBalance(toID, payerID, nodeID);
		long amount = 100L;

		Transaction paymentTx = getUnSignedTransferTx(payerID, nodeID, accID, toID, amount,
				"Transaction with Updated signMap");
		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key fromKey = acc2ComplexKeyMap.get(accID);
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(newKey);
		keys.add(oldKey);
		Key toKey = acc2ComplexKeyMap.get(toID);
		keys.add(toKey);
		Transaction transferTxSigned = TransactionSigner
				.signTransactionComplex(paymentTx, keys, pubKey2privKeyMap);
		TransactionReceipt receipt = transfer(transferTxSigned);

		Assert.assertEquals(ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name(),
				receipt.getStatus().name());
		long fromBal2 = getAccountBalance(accID, payerID, nodeID);
		long toBal2 = getAccountBalance(toID, payerID, nodeID);
		Assert.assertNotEquals(fromBal2, fromBal1 - amount);
		Assert.assertNotEquals(toBal2, toBal1 + amount);

		log.info(LOG_PREFIX + "cryptoTransfer after Key changed Order Update Tests: PASSED! :)");
	}

	public static void main(String[] args) throws Throwable {
		CryptoUpdateKeysOrderTests tester = new CryptoUpdateKeysOrderTests(testConfigFilePath);
		tester.init(args);
		tester.cryptoUpdate_KeyOrder_TransferTests();
		tester.cryptoUpdate_different_KeyOrder_TransferTests();
	}
}
