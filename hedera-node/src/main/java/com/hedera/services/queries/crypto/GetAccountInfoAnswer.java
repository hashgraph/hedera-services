package com.hedera.services.queries.crypto;

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
import com.hedera.services.queries.AnswerService;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;

import java.util.Optional;

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static com.hedera.services.legacy.core.MapKey.getMapKey;

public class GetAccountInfoAnswer implements AnswerService {
	private final OptionValidator optionValidator;

	public GetAccountInfoAnswer(OptionValidator optionValidator) {
		this.optionValidator = optionValidator;
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		AccountID id = query.getCryptoGetInfo().getAccountID();

		return optionValidator.queryableAccountStatus(id, view.accounts());
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		CryptoGetInfoQuery op = query.getCryptoGetInfo();
		CryptoGetInfoResponse.Builder response = CryptoGetInfoResponse.newBuilder();

		ResponseType type = op.getHeader().getResponseType();
		if (validity != OK) {
			response.setHeader(header(validity, type, cost));
		} else {
			if (type == COST_ANSWER) {
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				AccountID id = op.getAccountID();
				HederaAccount account = view.accounts().get(getMapKey(id));
				String solidityAddress = asSolidityAddressHex(id);
				CryptoGetInfoResponse.AccountInfo.Builder info = CryptoGetInfoResponse.AccountInfo.newBuilder()
						.setKey(asKeyUnchecked(account.getAccountKeys()))
						.setExpirationTime(Timestamp.newBuilder().setSeconds(account.getExpirationTime()))
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(account.getAutoRenewPeriod()))
						.setProxyAccountID(asAccount(account.getProxyAccount()))
						.setAccountID(op.getAccountID())
						.setBalance(account.getBalance())
						.setContractAccountID(solidityAddress)
						.setReceiverSigRequired(account.isReceiverSigRequired())
						.setGenerateSendRecordThreshold(account.getSenderThreshold())
						.setGenerateReceiveRecordThreshold(account.getReceiverThreshold());
				response.setHeader(answerOnlyHeader(OK));
				response.setAccountInfo(info);
			}
		}
		return Response.newBuilder()
				.setCryptoGetInfo(response)
				.build();
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getCryptoGetInfo().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return typicallyRequiresNodePayment(query.getCryptoGetInfo().getHeader().getResponseType());
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		Transaction paymentTxn = query.getCryptoGetInfo().getHeader().getPayment();
		return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return CryptoGetInfo;
	}
}
