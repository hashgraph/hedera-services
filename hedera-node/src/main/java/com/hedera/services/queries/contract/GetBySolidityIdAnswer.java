package com.hedera.services.queries.contract;

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
import com.hedera.services.queries.AbstractAnswer;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetBySolidityIdAnswer extends AbstractAnswer {
	private static final Logger log = LogManager.getLogger(GetBySolidityIdAnswer.class);

	public GetBySolidityIdAnswer() {
		super(GetBySolidityID,
				query -> null,
				query -> query.getGetBySolidityID().getHeader().getResponseType(),
				response -> response.getGetBySolidityID().getHeader().getNodeTransactionPrecheckCode(),
				(query, view) -> NOT_SUPPORTED);
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		GetBySolidityIDQuery op = query.getGetBySolidityID();
		ResponseType type = op.getHeader().getResponseType();

		GetBySolidityIDResponse.Builder response = GetBySolidityIDResponse.newBuilder();
		if (type == COST_ANSWER) {
			response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
		} else {
			response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
		}
		return Response.newBuilder()
				.setGetBySolidityID(response)
				.build();
	}
}
