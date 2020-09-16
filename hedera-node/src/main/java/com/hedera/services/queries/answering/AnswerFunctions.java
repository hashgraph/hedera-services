package com.hedera.services.queries.answering;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.legacy.core.jproto.TxnId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

public class AnswerFunctions {
	public static final Logger log = LogManager.getLogger(AnswerFunctions.class);

	public List<TransactionRecord> accountRecords(StateView view, Query query) {
		CryptoGetAccountRecordsQuery op = query.getCryptoGetAccountRecords();
		MerkleEntityId key = MerkleEntityId.fromAccountId(op.getAccountID());
		MerkleAccount account = view.accounts().get(key);
		return ExpirableTxnRecord.allToGrpc(account.recordList());
	}

	public Optional<TransactionRecord> txnRecord(RecordCache recordCache, StateView view, Query query) {
		var txnId = query.getTransactionGetRecord().getTransactionID();
		var record = recordCache.getPriorityRecord(txnId);
		if (record != null) {
			return Optional.of(record);
		} else {
			try {
				AccountID id = txnId.getAccountID();
				MerkleAccount account = view.accounts().get(MerkleEntityId.fromAccountId(id));
				TxnId searchableId = TxnId.fromGrpc(txnId);
				return account.recordList()
						.stream()
						.filter(r -> r.getTxnId().equals(searchableId))
						.findAny()
						.map(ExpirableTxnRecord::asGrpc);
			} catch (Exception ignore) {
				return Optional.empty();
			}
		}
	}
}
