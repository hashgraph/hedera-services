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

import com.hedera.services.legacy.regression.BaseClient;
import com.hedera.services.legacy.regression.umbrella.CryptoServiceTest;
import com.hedera.services.legacy.regression.umbrella.TestHelperComplex;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Systematic crypto API integration tests.
 *
 * @author Hua Li Created on 2019-03-20
 */
public class CryptoTests extends BaseClient {

	private static final Logger log = LogManager.getLogger(CryptoTests.class);
	private static String testConfigFilePath = "config/umbrellaTest.properties";
	private static int TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT = 10; // number of accounts allowed in the transfer
	// list

	public CryptoTests(String testConfigFilePath) {
		super(testConfigFilePath);
	}

	/**
	 * Tests all fields of crypto update API.
	 */
	public void cryptoUpdateTests() throws Throwable {
		// create an account
		CryptoServiceTest.receiverSigRequired = false;
		CryptoServiceTest.payerAccounts = accountCreatBatch(2);
		AccountID accID = CryptoServiceTest.payerAccounts[0];
		Assert.assertNotNull(accID);

		// get account content
		AccountID payerID = CryptoServiceTest.payerAccounts[1];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;
		AccountInfo accInfo = getAccountInfo(accID, payerID, nodeID);

		Key key = accInfo.getKey();
		AccountID proxyAccountID = accInfo.getProxyAccountID();
		long sendRecordThreshold = accInfo.getGenerateSendRecordThreshold();
		long recvRecordThreshold = accInfo.getGenerateReceiveRecordThreshold();
		Duration autoRenewPeriod = accInfo.getAutoRenewPeriod();
		Timestamp expirationTime = accInfo.getExpirationTime();
		boolean receiverSigRequired = accInfo.getReceiverSigRequired();

		// create update tx based on content: change all fields
		Key keyMod = genComplexKey("thresholdKey");
		Assert.assertNotEquals(keyMod, key);
		AccountID proxyAccountIDMod = CryptoServiceTest.nodeAccounts[1];
		Assert.assertNotEquals(proxyAccountIDMod, proxyAccountID);
		long sendRecordThresholdMod = sendRecordThreshold * 2;
		long recvRecordThresholdMod = recvRecordThreshold * 2;
		Duration autoRenewPeriodMod =
				Duration.newBuilder().setSeconds(autoRenewPeriod.getSeconds() + 30).build();
		Timestamp expirationTimeMod = Timestamp.newBuilder().setSeconds(expirationTime.getSeconds() * 2)
				.setNanos(expirationTime.getNanos()).build();
		boolean receiverSigRequiredMod = !receiverSigRequired;

		// submit update tx and get acc content
		AccountInfo accInfoMod =
				updateAccount(accID, payerID, nodeID, keyMod, proxyAccountIDMod, sendRecordThresholdMod,
						recvRecordThresholdMod, autoRenewPeriodMod, expirationTimeMod, receiverSigRequiredMod);
		log.info("accInfo before update ==> " + accInfo);
		log.info("accInfo after update ==> " + accInfoMod);

		// verify content
		Assert.assertEquals(keyMod, accInfoMod.getKey());
		Assert.assertEquals(proxyAccountIDMod, accInfoMod.getProxyAccountID());
		Assert.assertEquals(sendRecordThresholdMod, accInfoMod.getGenerateSendRecordThreshold());
		Assert.assertEquals(recvRecordThresholdMod, accInfoMod.getGenerateReceiveRecordThreshold());
		Assert.assertEquals(autoRenewPeriodMod, accInfoMod.getAutoRenewPeriod());
		Assert.assertEquals(expirationTimeMod, accInfoMod.getExpirationTime());
		Assert.assertEquals(receiverSigRequiredMod, accInfoMod.getReceiverSigRequired());
		log.info(LOG_PREFIX + "cryptoUpdateTests: PASSED! :)");
	}

	/**
	 * Tests all fields of crypto create API.
	 */
	public void cryptoCreateTests() throws Throwable {
		// create accounts
		CryptoServiceTest.receiverSigRequired = false;
		CryptoServiceTest.payerAccounts = accountCreatBatch(3);
		AccountID payerID = CryptoServiceTest.payerAccounts[0];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

		AccountID accID = CryptoServiceTest.payerAccounts[1];
		Assert.assertNotNull(accID);
		AccountID toID = CryptoServiceTest.payerAccounts[2];
		Assert.assertNotNull(toID);

		// get account content
		AccountInfo accInfo = getAccountInfo(accID, payerID, nodeID);
		boolean recvSigRequired = accInfo.getReceiverSigRequired();
		Assert.assertEquals(false, recvSigRequired);
		long balance = accInfo.getBalance();

		// verify if the account fields are respected
		// key field: use a wrong key to sign a transfer with this account as the payer, should fail in
		// postconsensus with invalid signature error code
		Key accKey = TestHelperComplex.acc2ComplexKeyMap.get(accID);
		Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
		TestHelperComplex.acc2ComplexKeyMap.put(accID, payerKey); // temporarily assign payer key to account at
		// question,
		// thus this account has a wrong key
		long amount = 100L;

		TransactionReceipt receipt = transfer(payerID, nodeID, accID, toID, amount);
		Assert.assertEquals(true,
				ResponseCodeEnum.INVALID_SIGNATURE.equals(receipt.getStatus())
						|| ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY
						.equals(receipt.getStatus()));
		TestHelperComplex.acc2ComplexKeyMap.put(accID, accKey); // right the account key

		// sendRecordThreshold and recvRecordThreshold fields:
		// scenario A: a transfer with amount less than the thresholds, then no record is available
		long sendRecordThreshold = balance / 1000;
		long recvRecordThreshold = sendRecordThreshold / 10;
		AccountInfo accInfoMod = updateAccount(accID, payerID, nodeID, null, null, sendRecordThreshold,
				recvRecordThreshold, null, null, null);
		Assert.assertEquals(sendRecordThreshold, accInfoMod.getGenerateSendRecordThreshold());
		Assert.assertEquals(recvRecordThreshold, accInfoMod.getGenerateReceiveRecordThreshold());

		amount = sendRecordThreshold;
		receipt = transfer(payerID, nodeID, accID, toID, amount); // account_2 as sender
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
		amount = recvRecordThreshold;
		receipt = transfer(payerID, nodeID, toID, accID, amount); // account_2 as receiver
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		// get all records of account_2, size should be zero
		List<TransactionRecord> records = getTransactionRecordsByAccountId(accID, payerID, nodeID);
		Assert.assertEquals(0, records.size());

		// scenario B: a transfer with amount no less than thresholds, then records are available
		amount = sendRecordThreshold + 1;
		receipt = transfer(payerID, nodeID, accID, toID, amount); // account_2 as sender
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
		amount = recvRecordThreshold + 1;
		receipt = transfer(payerID, nodeID, toID, accID, amount); // account_2 as receiver
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		// get all records of account_2, size should be zero
		records = getTransactionRecordsByAccountId(accID, payerID, nodeID);
		log.info("@@@ account_2 = " + accID + ", payerID = " + payerID + ", records = " + records);
		Assert.assertEquals(2, records.size());

		// receiverSigRequired field: update account such that this flag is set to true, make a transfer
		// to this account,
		// but don't sign as a receiver, the transfer should fail post consensus with invalid signature
		accInfoMod = updateAccount(accID, payerID, nodeID, null, null, null, null, null, null, true);
		Assert.assertEquals(true, accInfoMod.getReceiverSigRequired());
		amount = recvRecordThreshold + 1;
		receipt = transfer(payerID, nodeID, toID, accID,
				amount); // account_2 as receiver, but not signing
		// as receiver
		Assert.assertEquals(true,
				ResponseCodeEnum.INVALID_SIGNATURE.name().equals(receipt.getStatus().name())
						|| ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name()
						.equals(receipt.getStatus().name()));

		CryptoServiceTest.recvSigRequiredAccounts
				.add(accID); // register the fact that account_2 as receiver and is required
		// to sign
		receipt = transfer(payerID, nodeID, toID, accID,
				amount); // account_2 as receiver, now do signing
		// as receiver
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		// autoRenewPeriod and expirationTime fields: cannot tested until platform API for deletion is
		// available
		CryptoServiceTest.receiverSigRequired = true; // default
		log.info(LOG_PREFIX + "cryptoCreateTests: PASSED! :)");
	}

	/**
	 * Tests fields of crypto transfer API.
	 */
	public void cryptoTransferTests() throws Throwable {
		// create accounts
		CryptoServiceTest.receiverSigRequired = false;
		CryptoServiceTest.payerAccounts = accountCreatBatch(4);
		AccountID payerID = CryptoServiceTest.payerAccounts[0];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

		AccountID fromID = CryptoServiceTest.payerAccounts[1];
		Assert.assertNotNull(fromID);
		AccountID toID = CryptoServiceTest.payerAccounts[2];
		Assert.assertNotNull(toID);
		AccountID accID = CryptoServiceTest.payerAccounts[3];
		Assert.assertNotNull(accID);

		// positive scenario transfer with one from and one to accounts, verify the balance change
		long fromBal1 = getAccountBalance(fromID, payerID, nodeID);
		long toBal1 = getAccountBalance(toID, payerID, nodeID);
		long amount = 100L;
		TransactionReceipt receipt = transfer(payerID, nodeID, fromID, toID, amount);
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
		long fromBal2 = getAccountBalance(fromID, payerID, nodeID);
		long toBal2 = getAccountBalance(toID, payerID, nodeID);
		Assert.assertEquals(fromBal2, fromBal1 - amount);
		Assert.assertEquals(toBal2, toBal1 + amount);

		// negative scenario transfer with two from and two to accounts, where accounts are repeated
		AccountID[] accs = { fromID, toID, fromID, toID };
		long[] amts = { -amount - 2, amount + 1, -amount + 2, amount - 1 }; // note the 4 amounts below
		// are all different but sums
		// up to zero
		TransactionResponse response = transferWrapperNoReceipt(payerID, nodeID, accs, amts);
		Assert.assertEquals(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS.name(),
				response.getNodeTransactionPrecheckCode().name());

		// negative scenario transfer with one from and one to accounts, amounts do not sum to zero
		AccountID[] accs1 = { fromID, toID };
		long[] amts1 = { -amount, amount + 1 }; // note the 4 amounts below are all different but sums up
		// to zero
		receipt = transferWrapper(payerID, nodeID, accs1, amts1);
		Assert.assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS.name(),
				receipt.getStatus().name());

		// negative scenario transfer with two from and two to accounts, amounts do not sum to zero,
		// where accounts are repeated
		AccountID[] accs2 = { fromID, toID, fromID, toID };
		long[] amts2 = { -amount - 2, amount + 1, -amount + 2, amount - 1 - 1 }; // note the 4 amounts
		// below are all
		// different AND does NOT
		// sum up to zero
		response = transferWrapperNoReceipt(payerID, nodeID, accs2, amts2);
		Assert.assertEquals(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS.name(),
				response.getNodeTransactionPrecheckCode().name());

		// positive scenario transfer with one from and one to accounts, amounts are all zero, i.e. they
		// sum to zero
		AccountID[] accs3 = { fromID, toID };
		long[] amts3 = { 0, 0 };
		receipt = transferWrapper(payerID, nodeID, accs3, amts3);
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		// positive, one sender and two different receivers, all amounts sum to zero
		long fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		long toBalPre = getAccountBalance(toID, payerID, nodeID);
		long accBalPre = getAccountBalance(accID, payerID, nodeID);

		AccountID[] accs4 = { fromID, toID, accID };
		long[] amts4 = { -amount * 2, amount, amount };
		receipt = transferWrapper(payerID, nodeID, accs4, amts4);
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		long fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		long toBalPost = getAccountBalance(toID, payerID, nodeID);
		long accBalPost = getAccountBalance(accID, payerID, nodeID);

		Assert.assertEquals(fromBalPost, fromBalPre + amts4[0]);
		Assert.assertEquals(toBalPost, toBalPre + amts4[1]);
		Assert.assertEquals(accBalPost, accBalPre + amts4[2]);

		// positive, two different senders and one receiver, all amounts sum to zero
		fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		toBalPre = getAccountBalance(toID, payerID, nodeID);
		accBalPre = getAccountBalance(accID, payerID, nodeID);

		AccountID[] accs5 = { fromID, toID, accID };
		long[] amts5 = { -amount, amount * 2, -amount };
		receipt = transferWrapper(payerID, nodeID, accs5, amts5);
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		toBalPost = getAccountBalance(toID, payerID, nodeID);
		accBalPost = getAccountBalance(accID, payerID, nodeID);

		Assert.assertEquals(fromBalPost, fromBalPre + amts5[0]);
		Assert.assertEquals(toBalPost, toBalPre + amts5[1]);
		Assert.assertEquals(accBalPost, accBalPre + amts5[2]);

		// negative, two different senders and one receiver, the second sender overdrafts, all amounts
		// sum to zero
		// first sender should not be debited, and the receiver should not be credited
		fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		toBalPre = getAccountBalance(toID, payerID, nodeID);
		accBalPre = getAccountBalance(accID, payerID, nodeID);

		AccountID[] accs6 = { fromID, toID, accID };
		long[] amts6 = { -amount, amount + accBalPre + 1, -accBalPre - 1 };
		receipt = transferWrapper(payerID, nodeID, accs6, amts6);
		Assert.assertEquals(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE.name(),
				receipt.getStatus().name());

		fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		toBalPost = getAccountBalance(toID, payerID, nodeID);
		accBalPost = getAccountBalance(accID, payerID, nodeID);

		Assert.assertEquals(fromBalPost, fromBalPre);
		Assert.assertEquals(toBalPost, toBalPre);
		Assert.assertEquals(accBalPost, accBalPre);

		// This test case is related to issue #830 and it should fail due to accounts are repeated in
		// transfer list.
		// Case A: transfer is NOT rejected prior to processing any entry. // in fact, this succeeds,
		// but end up with negative balance
		// transfer list:
		// a1, -50
		// a2, +50
		// a1, -51
		// a2, +51
		// The first two entries will be processed successfully with FCM updates on the account
		// balances. However, the third entry will cause insufficient balance to be thrown and stopping
		// processing the rest of the transfer list.
		fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		toBalPre = getAccountBalance(toID, payerID, nodeID);

		AccountID[] accs7 = { fromID, toID, fromID, toID };
		long[] amts7 = { -50, 50, -(fromBalPre - 50) - 1, fromBalPre - 50 + 1 };
		response = transferWrapperNoReceipt(payerID, nodeID, accs7, amts7);
		Assert.assertEquals(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS.name(),
				response.getNodeTransactionPrecheckCode().name());

		fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		toBalPost = getAccountBalance(toID, payerID, nodeID);

		Assert.assertEquals(fromBalPost, fromBalPre);
		Assert.assertEquals(toBalPost, toBalPre);

		// This test case is related to issue #830 and it should fail due to accounts are repeated in
		// transfer list.
		// Case B: transfer should succeed but are rejected // in fact, this transfer does succeed
		// transfer list:
		// a1, -50
		// a2, +50
		// a1, -51
		// a2, +51
		// a1, +10
		// a2, -10
		// This transfer should succeed with a balance of 9 for a1 and 291 for a2. Instead, the
		// following will happen. The first two entries will be processed successfully with FCM updates
		// on the account balances. However, the third entry will cause insufficient balance to be
		// thrown and stopping processing the rest of the transfer list.
		fromID = accID;
		fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		toBalPre = getAccountBalance(toID, payerID, nodeID);

		AccountID[] accs8 = { fromID, toID, fromID, toID, fromID, toID };
		long[] amts8 = { -50, 50, -(fromBalPre - 50) - 1, fromBalPre - 50 + 1, 10, -10 };

		response = transferWrapperNoReceipt(payerID, nodeID, accs8, amts8);
		Assert.assertEquals(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS.name(),
				response.getNodeTransactionPrecheckCode().name());

		fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		toBalPost = getAccountBalance(toID, payerID, nodeID);

		Assert.assertEquals(fromBalPost, fromBalPre);
		Assert.assertEquals(toBalPost, toBalPre);

		log.info(LOG_PREFIX + "cryptoTransferTests: PASSED! :)");
	}

	/**
	 * Tests crypto transfer API with more than 10 account amounts.
	 *
	 * @param accountAmountListCount
	 * 		the size of the transfer list, including both from and to
	 * 		accounts.
	 */
	public void cryptoTransferTestsWithVariableAccountAmounts(int accountAmountListCount)
			throws Throwable {
		// create accounts
		int toAccountCount = accountAmountListCount - 1;
		CryptoServiceTest.payerAccounts = accountCreatBatch(toAccountCount + 2);
		AccountID payerID = CryptoServiceTest.payerAccounts[0];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

		AccountID fromID = CryptoServiceTest.payerAccounts[1];
		Assert.assertNotNull(fromID);

		AccountID[] toIDs = new AccountID[toAccountCount];

		System.arraycopy(CryptoServiceTest.payerAccounts, 2, toIDs, 0, toAccountCount);
		Arrays.stream(toIDs).forEach(a -> Assert.assertNotNull(a));

		// positive scenario transfer with one from and one to accounts, verify the balance change
		long amount = 100L;
		TransactionReceipt receipt = null;
		long fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		long toAccBal[] = new long[toIDs.length];
		for (int i = 0; i < toAccBal.length; i++) {
			toAccBal[i] = getAccountBalance(toIDs[i], payerID, nodeID);
		}
		// Transfer list First account is From Account and remaining to Account List
		AccountID[] transferList = new AccountID[toIDs.length + 1];
		long[] amountsList = new long[toIDs.length + 1];
		transferList[0] = fromID;
		amountsList[0] = -amount * toAccountCount;
		for (int i = 0; i < toIDs.length; i++) {
			transferList[i + 1] = toIDs[i];
			amountsList[i + 1] = amount;
		}
		receipt = transferWrapper(payerID, nodeID, transferList, amountsList);
		if (accountAmountListCount <= TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT) {
			Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

			long toAccBalPost[] = new long[toIDs.length];
			for (int i = 0; i < toAccBalPost.length; i++) {
				toAccBalPost[i] = getAccountBalance(toIDs[i], payerID, nodeID);
			}

			long fromBalPost = getAccountBalance(fromID, payerID, nodeID);
			Assert.assertEquals(fromBalPost, fromBalPre + amountsList[0]);
			for (int i = 0; i < toAccBal.length; i++) {
				Assert.assertEquals(toAccBalPost[i], toAccBal[i] + amountsList[i + 1]);
			}

			log.info(LOG_PREFIX + "cryptoTransferTestsWithVariableAccountAmounts: PASSED! :)");
		} else {
			Assert.assertEquals(ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED.name(),
					receipt.getStatus().name());
			log.info("receipt status = " + receipt.getStatus().name());
			log.info(LOG_PREFIX + "cryptoTransferTestsWithVariableAccountAmounts: PASSED! :)");
		}
	}


	/**
	 * A wrapper for transfer with a list of accouts and amounts.
	 */
	private TransactionReceipt transferWrapper(AccountID payerID, AccountID nodeID, AccountID[] accs,
			long[] amts) throws Throwable {
		Transaction transferTxModSigned = createSignedTransferTx(payerID, nodeID, accs, amts);
		TransactionReceipt receipt = transfer(transferTxModSigned);
		return receipt;
	}

	private TransactionReceipt transferWrapper(AccountID payerID, AccountID nodeID, AccountID[] accs,
			long[] amts, ResponseCodeEnum preCode) throws Throwable {
		Transaction transferTxModSigned = createSignedTransferTx(payerID, nodeID, accs, amts);
		TransactionReceipt receipt = transfer(transferTxModSigned, preCode);
		return receipt;
	}

	/**
	 * Creating a transaction for a transfer with a list of accouts and amounts.
	 */
	private Transaction createSignedTransferTx(AccountID payerID, AccountID nodeID, AccountID[] accs,
			long[] amts) throws Throwable {
		Assert.assertEquals(accs.length, amts.length);
		List<AccountAmount> accountAmountsMod = new ArrayList<>();
		for (int i = 0; i < accs.length; i++) {
			AccountAmount aa1 =
					AccountAmount.newBuilder().setAccountID(accs[i]).setAmount(amts[i]).build();
			accountAmountsMod.add(aa1);
		}

		Transaction transferTx =
				CryptoServiceTest.getUnSignedTransferTx(payerID, nodeID, accs[0], accs[1], 100L, "Transfer");
		com.hederahashgraph.api.proto.java.TransactionBody.Builder txBodyBuilder =
				CommonUtils.extractTransactionBody(transferTx).toBuilder();
		Builder transferListBuilder =
				com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody.newBuilder();

		TransferList transferListMod =
				TransferList.newBuilder().addAllAccountAmounts(accountAmountsMod).build();
		transferListBuilder.setTransfers(transferListMod);
		txBodyBuilder.setCryptoTransfer(transferListBuilder);
		Transaction transferTxModUnsigned =
				transferTx.toBuilder().setBodyBytes(txBodyBuilder.build().toByteString()).build();
		Transaction transferTxModSigned = CryptoServiceTest.getSignedTransferTx(transferTxModUnsigned);
		return transferTxModSigned;
	}

	/**
	 * A wrapper for transfer with a list of accouts and amounts.
	 */
	private TransactionResponse transferWrapperNoReceipt(AccountID payerID, AccountID nodeID,
			AccountID[] accs, long[] amts) throws Throwable {
		Transaction transferTxModSigned = createSignedTransferTx(payerID, nodeID, accs, amts);
		TransactionResponse response = transferOnly(transferTxModSigned);
		return response;
	}

	/**
	 * Tests all fields of crypto create API.
	 */
	public void cryptoCreateRecordCheckTests() throws Throwable {
		// create accounts
		CryptoServiceTest.receiverSigRequired = false;
		CryptoServiceTest.payerAccounts = accountCreatBatch(3);
		AccountID payerID = CryptoServiceTest.payerAccounts[0];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

		AccountID accID = CryptoServiceTest.payerAccounts[1];
		Assert.assertNotNull(accID);
		AccountID toID = CryptoServiceTest.payerAccounts[2];
		Assert.assertNotNull(toID);

		// get account content
		AccountInfo accInfo = getAccountInfo(accID, payerID, nodeID);
		boolean recvSigRequired = accInfo.getReceiverSigRequired();
		Assert.assertEquals(false, recvSigRequired);
		long balance = accInfo.getBalance();

		// verify if the account fields are respected
		// key field: use a wrong key to sign a transfer with this account as the payer, should fail in
		// postconsensus with invalid signature error code
		Key accKey = TestHelperComplex.acc2ComplexKeyMap.get(accID);
		Key payerKey = TestHelperComplex.acc2ComplexKeyMap.get(payerID);
		TestHelperComplex.acc2ComplexKeyMap.put(accID, payerKey); // temporarily assign payer key to account at
		// question,
		// thus this account has a wrong key
		long amount = 100L;

		Transaction[] txHolder = new Transaction[1];
		TransactionReceipt receipt = transfer(payerID, nodeID, accID, toID, amount, txHolder);
		Assert.assertEquals(true,
				ResponseCodeEnum.INVALID_SIGNATURE.name().equals(receipt.getStatus().name())
						|| ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name()
						.equals(receipt.getStatus().name()));
		TestHelperComplex.acc2ComplexKeyMap.put(accID, accKey); // right the account key

		// get record and check its fields
		checkRecord(txHolder[0], payerID, nodeID);

		// sendRecordThreshold and recvRecordThreshold fields:
		// scenario A: a transfer with amount less than the thresholds, then no record is available
		long sendRecordThreshold = balance / 1000;
		long recvRecordThreshold = sendRecordThreshold / 10;
		AccountInfo accInfoMod = updateAccount(accID, payerID, nodeID, null, null, sendRecordThreshold,
				recvRecordThreshold, null, null, null);
		Assert.assertEquals(sendRecordThreshold, accInfoMod.getGenerateSendRecordThreshold());
		Assert.assertEquals(recvRecordThreshold, accInfoMod.getGenerateReceiveRecordThreshold());

		amount = sendRecordThreshold;
		receipt = transfer(payerID, nodeID, accID, toID, amount, txHolder); // accID as sender
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		// get record and check its fields
		checkRecord(txHolder[0], payerID, nodeID);

		amount = recvRecordThreshold;
		receipt = transfer(payerID, nodeID, toID, accID, amount, txHolder); // accID as receiver
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		// get record and check its fields
		checkRecord(txHolder[0], payerID, nodeID);

		// get all records of accID, size should be zero
		List<TransactionRecord> records = getTransactionRecordsByAccountId(accID, payerID, nodeID);
		Assert.assertEquals(0, records.size());

		// receiverSigRequired field: update account such that this flag is set to true, make a transfer
		// to this account,
		// but don't sign as a receiver, the transfer should fail post consensus with invalid signature
		accInfoMod = updateAccount(accID, payerID, nodeID, null, null, null, null, null, null, true);
		Assert.assertEquals(true, accInfoMod.getReceiverSigRequired());
		amount = recvRecordThreshold + 1;
		receipt = transfer(payerID, nodeID, toID, accID, amount,
				txHolder); // accID as receiver, but not signing
		// as receiver
		Assert.assertEquals(true,
				ResponseCodeEnum.INVALID_SIGNATURE.name().equals(receipt.getStatus().name())
						|| ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY.name()
						.equals(receipt.getStatus().name()));

		// get record and check its fields
		checkRecord(txHolder[0], payerID, nodeID);

		CryptoServiceTest.recvSigRequiredAccounts.add(accID); // register the fact that accID as receiver and is
		// required
		// to sign
		receipt = transfer(payerID, nodeID, toID, accID, amount,
				txHolder); // accID as receiver, now do signing
		// as receiver
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		// get record and check its fields
		checkRecord(txHolder[0], payerID, nodeID);

		// autoRenewPeriod and expirationTime fields: cannot tested until platform API for deletion is
		// available
		CryptoServiceTest.receiverSigRequired = true; // default
		log.info(LOG_PREFIX + "cryptoCreateRecordCheckTests: PASSED! :)");
	}

	/**
	 * Checks if record fields are instantiated.
	 */
	private void checkRecord(Transaction transaction, AccountID payerID, AccountID nodeID)
			throws Exception {
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(transaction);
		TransactionRecord record = getTransactionRecord(body.getTransactionID(), payerID, nodeID);
		com.hedera.services.legacy.core.CommonUtils.checkRecord(record, body);
	}

	/**
	 * Initialize the client.
	 *
	 * @param args
	 * 		command line arguments, if supplied, the host should be the first argument
	 */
	public void init(String[] args) throws Throwable {
		super.init(args);
		CryptoServiceTest.getReceipt = true;
	}

	/**
	 * Tests threshold record of crypto create API.
	 */
	public void cryptoCreateThresholdRecordCheckTests() throws Throwable {
		CryptoServiceTest.receiverSigRequired = false;
		CryptoServiceTest.payerAccounts = accountCreatBatch(2);
		AccountID expAccount = CryptoServiceTest.payerAccounts[0];
		AccountID payer = CryptoServiceTest.payerAccounts[1];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

		//update expAccount sender threshold to known amount
		long sendThd = 100000000;
		AccountInfo accInfoMod = updateAccount(expAccount, payer, nodeID, null, null, sendThd, null,
				null, null, true);
		log.info("payer account info = " + accInfoMod);
		Assert.assertEquals(sendThd, accInfoMod.getGenerateSendRecordThreshold());

		//create a new account with init balance = threshold
		//check threshold record (using get account record), it should exist by checking account id created,
		// and only two record under the expAccount account
		//i.e. one for the new account creation, the other for the threshold record.
		String accountKeyType = getRandomAccountKeyType();
		AccountID newAccountId = createAccountComplex(expAccount, nodeID, accountKeyType, sendThd, true,
				true);
		List<TransactionRecord> records = getTransactionRecordsByAccountId(expAccount, payer, nodeID);
		log.info("balance = threshold, records=" + records);
		Assert.assertEquals(1, records.size());
		Assert.assertEquals(newAccountId, records.get(0).getReceipt().getAccountID());
		// transfer list, normally 5 items, 2 more items for threshold record
		Assert.assertEquals(5, records.get(0).getTransferList().getAccountAmountsList().size());

		//create a new account with init balance = threshold + 1,
		//check threshold record, it should exist, and there should be 4 records under the expAccount account
		newAccountId = createAccountComplex(expAccount, nodeID, accountKeyType, sendThd + 1, true,
				true);
		records = getTransactionRecordsByAccountId(expAccount, payer, nodeID);
		log.info("balance > threshold, records=" + records);
		Assert.assertEquals(2, records.size());
		Assert.assertEquals(newAccountId, records.get(1).getReceipt().getAccountID());
		Assert.assertEquals(5, records.get(1).getTransferList().getAccountAmountsList().size());

		//create a new account with init balance = threshold - 1,
		//no threshold record nor any 3-min record will be generated
		newAccountId = createAccountComplex(expAccount, nodeID, accountKeyType, sendThd - 1, true,
				true);
		records = getTransactionRecordsByAccountId(expAccount, payer, nodeID);
		log.info("balance < threshold, records=" + records);
		Assert.assertEquals(2, records.size());
		log.info(":) >>>> cryptoCreateThresholdRecordCheckTests success!");
	}

	/**
	 * Tests threshold record fee charge of crypto create API.
	 */
	public void cryptoCreateThresholdRecordFeeTests() throws Throwable {
		CryptoServiceTest.receiverSigRequired = false;
		CryptoServiceTest.payerAccounts = accountCreatBatch(2);
		AccountID expAccount = CryptoServiceTest.payerAccounts[0];
		AccountID payer = CryptoServiceTest.payerAccounts[1];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

		//update expAccount sender threshold to known amount
		long sendThd = 100000000; // tx fee is about 33220363
		AccountInfo accInfoMod = updateAccount(expAccount, payer, nodeID, null, null, sendThd, null,
				null, null, true);
		log.info("payer account info = " + accInfoMod);
		Assert.assertEquals(sendThd, accInfoMod.getGenerateSendRecordThreshold());

		//create a new account with init balance = threshold - 1,
		//no record will be generated
		String accountKeyType = getRandomAccountKeyType();
		createAccountComplex(expAccount, nodeID, accountKeyType, sendThd - 1, true,
				true);
		List<TransactionRecord> records = getTransactionRecordsByAccountId(expAccount, payer, nodeID);
		log.info("balance < threshold, records=" + records);

		Assert.assertEquals(0, records.size());
		log.info(":) >>>> cryptoCreateThresholdRecordFeeTests success!");
	}

	/**
	 * Tests crypto transfer with control account.
	 */
	public void cryptoTransferWithControlAccountTests() throws Throwable {
		// create accounts
		CryptoServiceTest.receiverSigRequired = false;
		CryptoServiceTest.payerAccounts = accountCreatBatch(4);
		AccountID payerID = CryptoServiceTest.payerAccounts[0];
		AccountID nodeID = CryptoServiceTest.defaultListeningNodeAccountID;

		AccountID fromID = CryptoServiceTest.payerAccounts[1];
		Assert.assertNotNull(fromID);
		AccountID toID = CryptoServiceTest.payerAccounts[2];
		Assert.assertNotNull(toID);

		// positive: control account exists and transfer amount is non-zero except for control account
		AccountID controlAccount = CryptoServiceTest.payerAccounts[3];
		Assert.assertNotNull(controlAccount);
		long fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		long toBalPre = getAccountBalance(toID, payerID, nodeID);
		long accBalPre = getAccountBalance(controlAccount, payerID, nodeID);

		AccountID[] accs4 = { fromID, toID, controlAccount };
		int amount = 100;
		long[] amts4 = { -amount, amount, 0 };
		TransactionReceipt receipt = transferWrapper(payerID, nodeID, accs4, amts4);
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		long fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		long toBalPost = getAccountBalance(toID, payerID, nodeID);
		long accBalPost = getAccountBalance(controlAccount, payerID, nodeID);

		Assert.assertEquals(fromBalPost, fromBalPre + amts4[0]);
		Assert.assertEquals(toBalPost, toBalPre + amts4[1]);
		Assert.assertEquals(accBalPost, accBalPre + amts4[2]);
		List<TransactionRecord> records = getTransactionRecordsByAccountId(controlAccount, payerID, nodeID);
		Assert.assertEquals(0, records.size());
		log.info(LOG_PREFIX + "transfer positive test: control account exists: PASSED! :)");

		// Positive: control account exists and transfer list with amounts all set to zero
		long[] zeroAmts = { 0, 0, 0 };
		fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		toBalPre = getAccountBalance(toID, payerID, nodeID);
		accBalPre = getAccountBalance(controlAccount, payerID, nodeID);

		receipt = transferWrapper(payerID, nodeID, accs4, zeroAmts, ResponseCodeEnum.OK);
		Assert.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

		fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		toBalPost = getAccountBalance(toID, payerID, nodeID);
		accBalPost = getAccountBalance(controlAccount, payerID, nodeID);

		// balance not changed
		Assert.assertEquals(fromBalPost, fromBalPre);
		Assert.assertEquals(toBalPost, toBalPre);
		Assert.assertEquals(accBalPost, accBalPre);
		records = getTransactionRecordsByAccountId(controlAccount, payerID, nodeID);
		Assert.assertEquals(0, records.size());
		log.info(LOG_PREFIX + "transfer postive test: transfer list with amounts all set to zero: PASSED! :)");

		// Negative: control account does NOT exist
		controlAccount = AccountID.newBuilder().setAccountNum(
				(CryptoServiceTest.payerAccounts[3].getAccountNum() + 1)).build();
		AccountID[] accs5 = { fromID, toID, controlAccount };
		fromBalPre = getAccountBalance(fromID, payerID, nodeID);
		toBalPre = getAccountBalance(toID, payerID, nodeID);

		receipt = transferWrapper(payerID, nodeID, accs5, amts4, ResponseCodeEnum.OK);
		Assert.assertEquals(ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST.name(), receipt.getStatus().name());

		fromBalPost = getAccountBalance(fromID, payerID, nodeID);
		toBalPost = getAccountBalance(toID, payerID, nodeID);

		// balance not changed
		Assert.assertEquals(fromBalPost, fromBalPre);
		Assert.assertEquals(toBalPost, toBalPre);
		log.info(LOG_PREFIX + "transfer negative test: control account does NoT exist: PASSED! :)");

		log.info(LOG_PREFIX + "cryptoTransferWithControlAccountTests: PASSED! :)");
	}

	/**
	 * Makes a transfer.
	 */
	public TransactionReceipt transfer(Transaction transferTxSigned, ResponseCodeEnum preCode) throws Throwable {
		log.info("\n-----------------------------------\ntransfer: request = "
				+ com.hedera.services.legacy.proto.utils.CommonUtils.toReadableString(transferTxSigned));
		TransactionResponse response = CryptoServiceTest.cstub.cryptoTransfer(transferTxSigned);
		log.info("Transfer Response :: " + response.getNodeTransactionPrecheckCodeValue());
		Assert.assertNotNull(response);
		Assert.assertEquals(preCode, response.getNodeTransactionPrecheckCode());
		if (preCode != ResponseCodeEnum.OK) {
			return null;
		}

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

	public static void main(String[] args) throws Throwable {
		CryptoTests tester = new CryptoTests(testConfigFilePath);
		tester.init(args);
		tester.cryptoCreateTests();
		tester.cryptoUpdateTests();
		tester.cryptoTransferTests();
		tester.cryptoTransferWithControlAccountTests();
		tester.cryptoTransferTestsWithVariableAccountAmounts(2);
		tester.cryptoTransferTestsWithVariableAccountAmounts(TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT - 1);
		tester.cryptoTransferTestsWithVariableAccountAmounts(TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT);
		tester.cryptoTransferTestsWithVariableAccountAmounts(TRANSFER_ACCOUNTS_LIST_SIZE_LIMIT + 1);
		tester.cryptoCreateRecordCheckTests();
		tester.cryptoCreateThresholdRecordCheckTests();
		tester.cryptoCreateThresholdRecordFeeTests();
	}
}
