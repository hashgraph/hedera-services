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

import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;

import java.util.ArrayList;
import java.util.List;

/**
 * Extending TestHelper to support complex key structure.
 */
public class TestHelperComplex extends TestHelper {

	public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup

	/**
	 * Creates an account with complex keys with max tx fee.
	 *
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccount
	 * 		node account id, default listening account
	 * @param key
	 * 		key for the account to be created
	 * @param initialBalance
	 * 		initial balance on account to be created
	 * @param receiverSigRequired
	 * 		if receiver signature is required
	 * @param accountDuration
	 * 		auto renew period for the account
	 * @return Transaction for creating account
	 */
	public static Transaction createAccountComplex(AccountID payerAccount, AccountID nodeAccount,
			Key key, long initialBalance, boolean receiverSigRequired, long accountDuration) {
		long transactionFee = 100000000L;
		Duration duration = RequestBuilder.getDuration(accountDuration);
		return createAccountComplex(payerAccount, nodeAccount, key,
				initialBalance, transactionFee, receiverSigRequired, duration);
	}

	/**
	 * Creates an account with complex keys.
	 *
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccount
	 * 		node account id, default listening account
	 * @param key
	 * 		key for the account to be created
	 * @param initialBalance
	 * 		initial balance on account
	 * @param transactionFee
	 * 		transaction fees for creating account
	 * @param receiverSigRequired
	 * 		if receiver signature is required
	 * @param autoRenewPeriod
	 * 		auto renew period for the account
	 * @return Transaction for creating account
	 */
	public static Transaction createAccountComplex(AccountID payerAccount, AccountID nodeAccount,
			Key key, long initialBalance, long transactionFee, boolean receiverSigRequired,
			Duration autoRenewPeriod) {
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);

		boolean generateRecord = true;
		String memo = "Create Account Test";
		long sendRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
		long receiveRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;

		Transaction createAccountRequest = RequestBuilder
				.getCreateAccountBuilder(payerAccount.getAccountNum(),
						payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
						nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee, timestamp,
						transactionDuration,
						generateRecord, memo, key, initialBalance, sendRecordThreshold, receiveRecordThreshold,
						receiverSigRequired, autoRenewPeriod);
		List<Key> keys = new ArrayList<Key>();
		Key payerKey = acc2ComplexKeyMap.get(payerAccount);
		keys.add(payerKey);
		if (receiverSigRequired) {
			keys.add(key);
		}

		Transaction transaction = null;
		try {
			transaction = TransactionSigner
					.signTransactionComplexWithSigMap(createAccountRequest, keys, pubKey2privKeyMap);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return transaction;
	}

	/**
	 * Gets records by account ID.
	 *
	 * @param accountID
	 * 		given account ID
	 * @param payerAccount
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeAccount
	 * 		node account id, default listening account
	 * @param getTxRecordFee
	 * 		fees to get transaction record
	 * @param responsetype
	 * 		response type for the query
	 * @return Query for getting transaction record
	 * @throws Exception
	 * 		indicates failure while getting transaction record
	 */
	public static Query getTxRecordByAccountIdComplex(AccountID accountID, AccountID payerAccount,
			AccountID nodeAccount, long getTxRecordFee, ResponseType responsetype) throws Exception {
		Transaction transferTransaction = CryptoServiceTest
				.getSignedTransferTx(payerAccount, nodeAccount, payerAccount,
						nodeAccount, getTxRecordFee, "getTxRecordByAccountId");
		return RequestBuilder.getAccountRecordsQuery(accountID, transferTransaction, responsetype);
	}

	public static Transaction updateAccount(AccountID accountID, AccountID payerAccount,
			AccountID nodeAccount, Duration autoRenew) {

		Timestamp startTime = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION);

		long nodeAccountNum = nodeAccount.getAccountNum();
		long payerAccountNum = payerAccount.getAccountNum();
		return RequestBuilder
				.getAccountUpdateRequest(accountID, payerAccountNum, 0l, 0l, nodeAccountNum, 0l, 0l,
						TestHelper.getCryptoMaxFee(),
						startTime, transactionDuration, true, "Update Account", autoRenew);

	}
}
