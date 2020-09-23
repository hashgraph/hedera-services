package com.hedera.services.bdd.suites.crypto;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE_COUNT_MISMATCHING_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

public class CryptoCornerCasesSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoCornerCasesSuite.class);

	public static void main(String... args) {
		new CryptoCornerCasesSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
		);
	}
	private List<HapiApiSpec> negativeTests() {
		return List.of(
				invalidNodeAccount(),
				invalidTransactionBody(),
				invalidTransactionPayerAccountNotFound(),
				invalidTransactionMemoTooLong(),
				invalidTransactionDuration(),
				invalidTransactionStartTime()

//				invalidSigsCountMismatchingKey()
		);
	}


	private static Transaction removeTransactionBody(Transaction txn) {
		return txn.toBuilder().setBodyBytes(Transaction.getDefaultInstance().getBodyBytes()).build();
	}

	public static HapiApiSpec invalidTransactionBody() {
		return defaultHapiSpec("InvalidTransactionBody")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::removeTransactionBody)
								.hasPrecheckFrom(INVALID_TRANSACTION_BODY)
				);
	}


	private static Transaction replaceTxnNodeAccount(Transaction txn) {
		AccountID badNodeAccount = AccountID.newBuilder().setAccountNum(2000).setRealmNum(0).setShardNum(0).build();
		return TxnUtils.replaceTxnNodeAccount(txn, badNodeAccount);
	}

	public static HapiApiSpec invalidNodeAccount() {
		return defaultHapiSpec("InvalidNodeAccount")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnNodeAccount)
								.hasPrecheckFrom(INVALID_NODE_ACCOUNT)
				);
	}

	private static Transaction replaceTxnDuration(Transaction txn) {
		return TxnUtils.replaceTxnDuration(txn, -1L);
	}

	public static HapiApiSpec invalidTransactionDuration() {
		return defaultHapiSpec("InvalidTransactionDuration")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnDuration)
								.hasPrecheckFrom(INVALID_TRANSACTION_DURATION)
				);
	}

	private static Transaction replaceTxnMemo(Transaction txn) {
		String newMemo = RandomStringUtils.randomAlphanumeric(120);
		return TxnUtils.replaceTxnMemo(txn, newMemo);
	}

	public static HapiApiSpec invalidTransactionMemoTooLong() {
		return defaultHapiSpec("InvalidTransactionMemoTooLong")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnMemo)
								.hasPrecheckFrom(MEMO_TOO_LONG)
				);
	}


	private static Transaction replaceTxnPayerAccount(Transaction txn) {
		AccountID badPayerAccount = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(999999).build();
		return TxnUtils.replaceTxnPayerAccount(txn, badPayerAccount);
	}

	public static HapiApiSpec invalidTransactionPayerAccountNotFound() {
		return defaultHapiSpec("InvalidTransactionDuration")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnPayerAccount)
								.hasPrecheckFrom(PAYER_ACCOUNT_NOT_FOUND)
				);
	}

	private static Transaction replaceTxnStartTtime(Transaction txn) {
		long newStartTimeSecs = Instant.now(Clock.systemUTC()).getEpochSecond() + 100L;
		return TxnUtils.replaceTxnStartTime(txn, newStartTimeSecs, 0);
	}

	public static HapiApiSpec invalidTransactionStartTime() {
		return defaultHapiSpec("InvalidTransactionStartTime")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::replaceTxnStartTtime)
								.hasPrecheckFrom(INVALID_TRANSACTION_START)
				);
	}


	// These two scenarios still don't work.

	public static HapiApiSpec invalidSigsCountMismatchingKey() {
		return defaultHapiSpec("invalidSigsCountMismatchingKey")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::removeSigPairFromTransaction)
								.hasPrecheckFrom(INVALID_SIGNATURE_COUNT_MISMATCHING_KEY)
				);
	}

	public static HapiApiSpec invalidKeyPrefixMismatch() {
		return defaultHapiSpec("invalidKeyPrefixMismatch")
				.given(
				).when(
				).then(
						cryptoCreate("newPayee").balance(10000L)
								.scrambleTxnBody(CryptoCornerCasesSuite::removeSigPairFromTransaction)
								.hasPrecheckFrom(KEY_PREFIX_MISMATCH)
				);
	}


	public static Transaction removeSigPairFromTransaction(Transaction transaction) {
		SignatureMap sigMap =transaction.getSigMap();
		List<SignaturePair> sigPairList = sigMap.getSigPairList();

		List<SignaturePair> newSigPairList = new ArrayList<>();
		for(int i=1;i<sigPairList.size();i++) {
			newSigPairList.add(sigPairList.get(i));
		}
		SignatureMap newSigMap = sigMap.toBuilder().addAllSigPair(newSigPairList).build();
		return transaction.toBuilder().setSigMap(newSigMap).build();
	}

	private static Transaction replaceTxnSigs(Transaction txn) {
		Transaction newTxn = Transaction.getDefaultInstance();
		try {

			TransactionBody txnBody = TransactionBody.parseFrom(txn.getBodyBytes().toByteArray());
			TransactionBody.Builder tbb = TransactionBody.newBuilder(txnBody);
			TransactionBody newTxnBody = tbb.build();
			Transaction.Builder tb = Transaction.newBuilder()
					.setBodyBytes(txnBody.toByteString())
					.setSigs(txn.getSigs())
					;
			newTxn = tb.build();
		} catch (Exception e) {

		}

		return newTxn;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
