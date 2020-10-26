package com.hedera.services.queries.contract;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetContractRecordsAnswer implements AnswerService {
	public static final List<TransactionRecord> GUARANTEED_EMPTY_PAYER_RECORDS = Collections.emptyList();

	private final OptionValidator optionValidator;

	public GetContractRecordsAnswer(OptionValidator optionValidator) {
		this.optionValidator = optionValidator;
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getContractGetRecords().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return typicallyRequiresNodePayment(query.getContractGetRecords().getHeader().getResponseType());
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		ContractGetRecordsQuery op = query.getContractGetRecords();
		ContractGetRecordsResponse.Builder response = ContractGetRecordsResponse.newBuilder();

		ResponseType type = op.getHeader().getResponseType();
		if (validity != OK) {
			response.setHeader(header(validity, type, cost));
		} else {
			if (type == COST_ANSWER) {
				response.setContractID(op.getContractID());
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				response.setHeader(answerOnlyHeader(OK));
				response.setContractID(op.getContractID());
				response.addAllRecords(GUARANTEED_EMPTY_PAYER_RECORDS);
			}
		}

		return Response.newBuilder()
				.setContractGetRecordsResponse(response)
				.build();
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		var id = query.getContractGetRecords().getContractID();

		return optionValidator.queryableContractStatus(id, view.accounts());
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return ContractGetRecords;
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getContractGetRecordsResponse().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		var paymentTxn = query.getContractGetRecords().getHeader().getPayment();
		return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
	}
}
