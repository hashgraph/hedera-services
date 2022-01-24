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
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
public class AnswerFunctions {
	@Inject
	public AnswerFunctions() {
		// Default Constructor
	}

	public List<TransactionRecord> accountRecords(final StateView view, final CryptoGetAccountRecordsQuery op) {
		final var key = EntityNum.fromAccountId(op.getAccountID());
		final var account = view.accounts().get(key);
		return ExpirableTxnRecord.allToGrpc(account.recordList());
	}

	public Optional<TransactionRecord> txnRecord(
			final RecordCache recordCache,
			final StateView view,
			final TransactionGetRecordQuery query
	) {
		final var txnId = query.getTransactionID();
		final var expirableTxnRecord = recordCache.getPriorityRecord(txnId);
		if (expirableTxnRecord != null) {
			return Optional.of(expirableTxnRecord.asGrpc());
		} else {
			try {
				final var id = txnId.getAccountID();
				final var account = view.accounts().get(EntityNum.fromAccountId(id));
				final var searchableId = TxnId.fromGrpc(txnId);
				return account.recordList()
						.stream()
						.filter(r -> r.getTxnId().equals(searchableId))
						.findAny()
						.map(ExpirableTxnRecord::asGrpc);
			} catch (final Exception ignore) {
				return Optional.empty();
			}
		}
	}
}
