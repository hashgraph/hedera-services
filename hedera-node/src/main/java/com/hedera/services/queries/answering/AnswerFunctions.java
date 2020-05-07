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

import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.legacy.core.jproto.JTransactionID;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

import static com.hedera.services.legacy.core.MapKey.getMapKey;
import static com.hedera.services.legacy.core.jproto.JTransactionRecord.convert;

public class AnswerFunctions {
	public static final Logger log = LogManager.getLogger(AnswerFunctions.class);

	public List<TransactionRecord> accountRecords(StateView view, Query query) {
		CryptoGetAccountRecordsQuery op = query.getCryptoGetAccountRecords();
		MapKey key = getMapKey(op.getAccountID());
		HederaAccount account = view.accounts().get(key);
		return convert(account.recordList());
	}

	public Optional<TransactionRecord> txnRecord(RecordCache recordCache, StateView view, Query query) {
		TransactionID txnId = query.getTransactionGetRecord().getTransactionID();
		if (recordCache.isRecordPresent(txnId)) {
			return Optional.of(recordCache.getRecord(txnId));
		} else {
			try {
				AccountID id = txnId.getAccountID();
				HederaAccount account = view.accounts().get(getMapKey(id));
				JTransactionID searchableId = JTransactionID.convert(txnId);
				return account.recordList()
						.stream()
						.filter(r -> r.getTransactionID().equals(searchableId))
						.findAny()
						.map(JTransactionRecord::convert);
			} catch (Exception ignore) {
				log.warn(ignore.getMessage());
				return Optional.empty();
			}
		}
	}
}
