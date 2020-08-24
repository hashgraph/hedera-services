package com.hedera.services.queries.file;

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
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class GetFileInfoAnswer implements AnswerService {
	private static final Logger log = LogManager.getLogger(GetFileInfoAnswer.class);

	private final OptionValidator validator;

	public GetFileInfoAnswer(OptionValidator validator) {
		this.validator = validator;
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getFileGetInfo().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return typicallyRequiresNodePayment(query.getFileGetInfo().getHeader().getResponseType());
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		var op = query.getFileGetInfo();
		FileGetInfoResponse.Builder response = FileGetInfoResponse.newBuilder();

		ResponseType type = op.getHeader().getResponseType();
		if (validity != OK) {
			log.info("FileGetInfo not successful for: " + query.getFileGetInfo().toString());
			log.info("validity: " + validity.toString());
			response.setHeader(header(validity, type, cost));
		} else {
			if (type == COST_ANSWER) {
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				var info = view.infoFor(op.getFileID());
				/* Include cost here to satisfy legacy regression tests. */
				response.setHeader(answerOnlyHeader(OK, cost));
				response.setFileInfo(info.get());
			}
		}
		return Response.newBuilder()
				.setFileGetInfo(response)
				.build();
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		var id = query.getFileGetInfo().getFileID();

		return validator.queryableFileStatus(id, view);
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return FileGetInfo;
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		Transaction paymentTxn = query.getFileGetInfo().getHeader().getPayment();
		return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
	}
}
