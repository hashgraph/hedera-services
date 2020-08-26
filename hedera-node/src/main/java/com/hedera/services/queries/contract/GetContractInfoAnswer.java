package com.hedera.services.queries.contract;

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
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;

import static com.hedera.services.utils.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetContractInfoAnswer implements AnswerService {
	public static final String CONTRACT_INFO_CTX_KEY = GetContractInfoAnswer.class.getSimpleName() + "_contractInfo";

	private final OptionValidator validator;

	public GetContractInfoAnswer(OptionValidator validator) {
		this.validator = validator;
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getContractGetInfo().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return typicallyRequiresNodePayment(query.getContractGetInfo().getHeader().getResponseType());
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		throw new AssertionError("Not implemented");
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		var id = query.getContractGetInfo().getContractID();

		return validator.queryableContractStatus(id, view.contracts());
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return ContractGetInfo;
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		var paymentTxn = query.getContractGetInfo().getHeader().getPayment();
		return Optional.ofNullable(uncheckedFrom(paymentTxn));
	}
}
