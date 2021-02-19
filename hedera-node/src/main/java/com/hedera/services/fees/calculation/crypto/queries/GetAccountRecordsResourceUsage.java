package com.hedera.services.fees.calculation.crypto.queries;

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
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;

public class GetAccountRecordsResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetAccountInfoResourceUsage.class);

	private final AnswerFunctions answerFunctions;
	private final CryptoFeeBuilder usageEstimator;

	public GetAccountRecordsResourceUsage(
			AnswerFunctions answerFunctions,
			CryptoFeeBuilder usageEstimator
	) {
		this.answerFunctions = answerFunctions;
		this.usageEstimator = usageEstimator;
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasCryptoGetAccountRecords();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getCryptoGetAccountRecords().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		var target = fromAccountId(query.getCryptoGetAccountRecords().getAccountID());
		if (!view.accounts().containsKey(target)) {
			/* Given the test in {@code GetAccountRecordsAnswer.checkValidity}, this can only be
			 * missing under the extraordinary circumstance that the desired account expired
			 * during the query answer flow (which will now fail downstream with an appropriate
			 * status code); so just return the default {@code FeeData} here. */
			return FeeData.getDefaultInstance();
		}
		List<TransactionRecord> records = answerFunctions.accountRecords(view, query);
		return usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(records, type);
	}
}
