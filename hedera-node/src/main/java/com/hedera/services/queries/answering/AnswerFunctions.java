package com.hedera.services.queries.answering;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Singleton
public class AnswerFunctions {
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public AnswerFunctions(GlobalDynamicProperties dynamicProperties) {
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * Returns the most recent payer records available for an account in the given {@link StateView}.
	 *
	 * Note that at most {@link GlobalDynamicProperties#maxNumQueryableRecords()} records will be available,
	 * even if the given account has paid for more than this number of transactions in the last 180 seconds.
	 *
	 * @param view the view of the world state to get payer records from
	 * @param op the query with the target payer account
	 * @return the most recent available records for the given payer
	 */
	public List<TransactionRecord> mostRecentRecords(final StateView view, final CryptoGetAccountRecordsQuery op) {
		final var targetId = EntityNum.fromAccountId(op.getAccountID());
		final var targetAccount = view.accounts().get(targetId);
		if (targetAccount == null) {
			return Collections.emptyList();
		}
		final var numAvailable = targetAccount.numRecords();
		final var maxQueryable = dynamicProperties.maxNumQueryableRecords();
		return numAvailable <= maxQueryable
				? allFrom(targetAccount, numAvailable)
				: mostRecentFrom(targetAccount, maxQueryable, numAvailable);
	}

	/**
	 * Returns the record of the requested transaction from the given {@link RecordCache}, if available.
	 *
	 * @param recordCache the cache to get the record from
	 * @param op the query with the target transaction id
	 * @return the transaction record if available
	 */
	public Optional<TransactionRecord> txnRecord(final RecordCache recordCache, final TransactionGetRecordQuery op) {
		final var txnId = op.getTransactionID();
		final var expirableTxnRecord = recordCache.getPriorityRecord(txnId);
		return Optional.ofNullable(expirableTxnRecord).map(ExpirableTxnRecord::asGrpc);
	}

	/* --- Internal helpers --- */
	private List<TransactionRecord> allFrom(final MerkleAccount account, final int n) {
		return mostRecentFrom(account, n, n);
	}

	private List<TransactionRecord> mostRecentFrom(final MerkleAccount account, final int m, final int n) {
		final List<TransactionRecord> ans = new ArrayList<>();
		final Iterator<ExpirableTxnRecord> iter = account.recordIterator();
		for (int i = 0, cutoff = n - m; i < n; i++) {
			final var nextRecord = iter.next();
			if (i >= cutoff) {
				ans.add(nextRecord.asGrpc());
			}
		}
		return ans;
	}
}
