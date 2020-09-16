package com.hedera.services.queries.meta;

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
import com.hedera.services.queries.AnswerService;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetFastRecordResponse;

import java.util.Optional;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetFastTxnRecordAnswer implements AnswerService {
	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		TransactionGetFastRecordQuery op = query.getTransactionGetFastRecord();
		ResponseType type = op.getHeader().getResponseType();

		TransactionGetFastRecordResponse.Builder response = TransactionGetFastRecordResponse.newBuilder();
		if (type == COST_ANSWER) {
			response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
		} else {
			response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
		}
		return Response.newBuilder()
				.setTransactionGetFastRecord(response)
				.build();
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getTransactionGetFastRecord().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		return NOT_SUPPORTED;
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return TransactionGetRecord;
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		return Optional.empty();
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return false;
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return false;
	}
}
