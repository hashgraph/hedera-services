package com.hedera.services.queries.contract;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GetContractRecordsAnswerTest {
	long fee = 1_234L;
	String node = "0.0.3";
	String payer = "0.0.12345";
	String target = payer;
	StateView view;
	Transaction paymentTxn;
	FCMap<MerkleEntityId, MerkleAccount> accounts;

	OptionValidator optionValidator;
	GetContractRecordsAnswer subject;

	@BeforeEach
	private void setup() throws Throwable {
		view = new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts, null, null);

		optionValidator = mock(OptionValidator.class);

		subject = new GetContractRecordsAnswer(optionValidator);
	}

	@Test
	public void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, target)));
		assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void getsInvalidResponse() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, CONTRACT_DELETED, fee);

		// then:
		assertTrue(response.hasContractGetRecordsResponse());
		ContractGetRecordsResponse opResponse = response.getContractGetRecordsResponse();
		assertEquals(CONTRACT_DELETED, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasContractGetRecordsResponse());
		ContractGetRecordsResponse opResponse = response.getContractGetRecordsResponse();
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	public void getsTheAccountRecords() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasContractGetRecordsResponse());
		ContractGetRecordsResponse opResponse = response.getContractGetRecordsResponse();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
		assertEquals(0, opResponse.getHeader().getCost());
		// and:
		assertEquals(GetContractRecordsAnswer.GUARANTEED_EMPTY_PAYER_RECORDS, opResponse.getRecordsList());
	}

	@Test
	public void usesValidator() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		given(optionValidator.queryableContractStatus(asContract(target), accounts)).willReturn(CONTRACT_DELETED);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(CONTRACT_DELETED, validity);
		// and:
		verify(optionValidator).queryableContractStatus(any(), any());
	}

	@Test
	public void getsExpectedPayment() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee, target);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxn());
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(ContractGetRecords, subject.canonicalFunction());
	}

	@Test
	public void requiresAnswerOnlyPayment() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, target)));
		assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setContractGetRecordsResponse(
				ContractGetRecordsResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		ContractGetRecordsQuery.Builder op = ContractGetRecordsQuery.newBuilder()
				.setHeader(header)
				.setContractID(asContract(idLit));
		return Query.newBuilder().setContractGetRecords(op).build();
	}
}