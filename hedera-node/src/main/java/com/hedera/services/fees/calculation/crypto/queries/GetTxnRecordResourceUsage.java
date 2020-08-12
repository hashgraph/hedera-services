package com.hedera.services.fees.calculation.crypto.queries;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Optional;

public class GetTxnRecordResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetTxnRecordResourceUsage.class);

	private static final TransactionRecord MISSING_RECORD_STANDIN = TransactionRecord.getDefaultInstance();

	private final RecordCache recordCache;
	private final AnswerFunctions answerFunctions;
	private final CryptoFeeBuilder usageEstimator;

	public GetTxnRecordResourceUsage(
			RecordCache recordCache,
			AnswerFunctions answerFunctions,
			CryptoFeeBuilder usageEstimator
	) {
		this.recordCache = recordCache;
		this.usageEstimator = usageEstimator;
		this.answerFunctions = answerFunctions;
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasTransactionGetRecord();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getTransactionGetRecord().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		try {
			var record = answerFunctions.txnRecord(recordCache, view, query).orElse(MISSING_RECORD_STANDIN);
			return usageEstimator.getTransactionRecordQueryFeeMatrices(record, type);
		} catch (Exception illegal) {
			log.warn("Usage estimation unexpectedly failed for {}!", query, illegal);
			throw new IllegalArgumentException();
		}
	}
}
