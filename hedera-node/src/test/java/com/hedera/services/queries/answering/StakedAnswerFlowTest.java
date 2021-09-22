package com.hedera.services.queries.answering;

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

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.txns.submission.TransactionPrecheck;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StakedAnswerFlowTest {
	private final long queryCost = 666L;
	private final FeeObject detailCost = new FeeObject(111L, 222L, 333L);
	private final Timestamp now = MiscUtils.asTimestamp(Instant.ofEpochSecond(1_234_567L));
	private final AccountID node = IdUtils.asAccount("0.0.3");
	private final AccountID payer = IdUtils.asAccount("0.0.1234");
	private final AccountID superuser = IdUtils.asAccount("0.0.50");
	private final Query query = Query.getDefaultInstance();
	private final Response response = Response.getDefaultInstance();
	private final SignedTxnAccessor paymentAccessor = SignedTxnAccessor.uncheckedFrom(Transaction.newBuilder()
			.setBodyBytes(TransactionBody.newBuilder()
					.setNodeAccountID(node)
					.setTransactionID(TransactionID.newBuilder()
							.setTransactionValidStart(now)
							.setAccountID(payer))
					.build().toByteString())
			.build());
	private final SignedTxnAccessor superuserPaymentAccessor = SignedTxnAccessor.uncheckedFrom(Transaction.newBuilder()
			.setBodyBytes(TransactionBody.newBuilder()
					.setNodeAccountID(node)
					.setTransactionID(TransactionID.newBuilder()
							.setTransactionValidStart(now)
							.setAccountID(superuser))
					.build().toByteString())
			.build());
	private final AccountNumbers accountNumbers = new MockAccountNumbers();

	@Mock
	private FeeData usagePrices;
	@Mock
	private StateView stateView;
	@Mock
	private FeeCalculator fees;
	@Mock
	private AnswerService service;
	@Mock
	private QueryFeeCheck queryFeeCheck;
	@Mock
	private UsagePricesProvider resourceCosts;
	@Mock
	private QueryHeaderValidity queryHeaderValidity;
	@Mock
	private TransactionPrecheck transactionPrecheck;
	@Mock
	private HapiOpPermissions hapiOpPermissions;
	@Mock
	private FunctionalityThrottling throttles;
	@Mock
	private PlatformSubmissionManager submissionManager;

	private StakedAnswerFlow subject;

	@BeforeEach
	void setUp() {
		subject = new StakedAnswerFlow(
				fees,
				accountNumbers,
				() -> stateView,
				resourceCosts,
				throttles,
				submissionManager,
				queryHeaderValidity,
				transactionPrecheck,
				hapiOpPermissions,
				queryFeeCheck);
	}

	@Test
	void immediatelyRejectsQueryHeaderProblem() {
		setupServiceResponse(NOT_SUPPORTED);

		given(queryHeaderValidity.checkHeader(query)).willReturn(NOT_SUPPORTED);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void immediatelyRejectsMissingPayment() {
		setupServiceResponse(INSUFFICIENT_TX_FEE);

		givenValidHeader();
		givenPaymentIsRequired();

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void immediatelyRejectsBadPayment() {
		setupServiceResponse(INSUFFICIENT_PAYER_BALANCE);

		givenValidHeader();
		givenExtractablePayment();
		givenPaymentIsRequired();
		given(transactionPrecheck.performForQueryPayment(paymentAccessor.getSignedTxnWrapper()))
				.willReturn(Pair.of(new TxnValidityAndFeeReq(INSUFFICIENT_PAYER_BALANCE), null));

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void doesntChargeSuperusers() {
		setupCostAwareSuccessServiceResponse();

		givenValidHeader();
		givenExtractableSuperuserPayment();
		givenValidSuperuserExtraction();
		givenPaymentIsRequired();
		given(transactionPrecheck.performForQueryPayment(superuserPaymentAccessor.getSignedTxnWrapper()))
				.willReturn(Pair.of(new TxnValidityAndFeeReq(OK), superuserPaymentAccessor));
		givenAvailFunction();
		givenSuperuserPermission();
		givenHappyService();
		givenAvailableResourcePrices();
		givenComputableCost();

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		// and:
		verify(submissionManager, never()).trySubmission(superuserPaymentAccessor);
	}

	@Test
	void rejectsIfNotPermissioned() {
		setupServiceResponse(NOT_SUPPORTED);

		givenValidHeader();
		givenExtractablePayment();
		givenValidExtraction();
		givenPaymentIsRequired();
		givenAvailFunction();
		given(hapiOpPermissions.permissibilityOf(ConsensusGetTopicInfo, payer)).willReturn(NOT_SUPPORTED);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void throttlesIfBusy() {
		setupServiceResponse(BUSY);

		givenValidHeader();
		givenExtractablePayment();
		givenValidExtraction();
		givenPaymentIsRequired();
		givenAvailFunction();
		givenPermission();
		given(throttles.shouldThrottleQuery(ConsensusGetTopicInfo)).willReturn(true);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void throttlesIfBusyEvenWhenNoPaymentNeeded() {
		setupServiceResponse(BUSY);

		givenValidHeader();
		givenNoExtractablePayment();
		givenAvailFunction();
		given(throttles.shouldThrottleQuery(ConsensusGetTopicInfo)).willReturn(true);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void abortsIfBadServiceStatus() {
		setupServiceResponse(INVALID_TOPIC_ID);

		givenValidHeader();
		givenExtractablePayment();
		givenValidExtraction();
		givenPaymentIsRequired();
		givenAvailFunction();
		givenPermission();
		givenCapacity();
		given(service.checkValidity(query, stateView)).willReturn(INVALID_TOPIC_ID);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void abortsIfNodePaymentInsufficient() {
		setupCostAwareFailedServiceResponse(INVALID_RECEIVING_NODE_ACCOUNT);

		givenValidHeader();
		givenExtractablePayment();
		givenValidExtraction();
		givenPaymentIsRequired();
		givenAvailFunction();
		givenPermission();
		givenCapacity();
		givenHappyService();
		givenAvailableResourcePrices();
		givenComputableCost();
		given(queryFeeCheck.nodePaymentValidity(Collections.emptyList(), queryCost, node))
				.willReturn(INVALID_RECEIVING_NODE_ACCOUNT);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void abortsIfNodePaymentSubmissionFails() {
		setupCostAwareFailedServiceResponse(PLATFORM_TRANSACTION_NOT_CREATED);

		givenValidHeader();
		givenExtractablePayment();
		givenValidExtraction();
		givenPaymentIsRequired();
		givenAvailFunction();
		givenPermission();
		givenCapacity();
		givenHappyService();
		givenAvailableResourcePrices();
		givenComputableCost();
		givenValidPayment();
		given(submissionManager.trySubmission(paymentAccessor)).willReturn(PLATFORM_TRANSACTION_NOT_CREATED);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void returnsCostToCostAnswer() {
		setupCostAwareSuccessServiceResponse();
		subject.setNow(() -> Instant.ofEpochSecond(now.getSeconds()));

		givenValidHeader();
		givenAvailFunction();
		givenCapacity();
		givenHappyService();
		givenAvailableResourcePrices();
		givenEstimableCost();
		givenCostEstimateIsRequired();

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	@Test
	void returnsCostToCostAnswerEvenIfBadPayment() {
		setupCostAwareSuccessServiceResponse();
		subject.setNow(() -> Instant.ofEpochSecond(now.getSeconds()));

		givenValidHeader();
		givenExtractablePayment();
		givenAvailFunction();
		givenCapacity();
		givenHappyService();
		givenAvailableResourcePrices();
		givenEstimableCost();
		givenCostEstimateIsRequired();

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		// and:
		verify(transactionPrecheck, never()).performForQueryPayment(any());
	}

	@Test
	void doesnThrottleIfSuperuser() {
		setupCostAwareSuccessServiceResponse();
		subject.setIsThrottleExempt(num -> num == payer.getAccountNum());

		givenValidHeader();
		givenExtractablePayment();
		givenValidExtraction();
		givenPaymentIsRequired();
		givenAvailFunction();
		givenPermission();
		givenHappyService();
		givenAvailableResourcePrices();
		givenComputableCost();
		givenValidPayment();
		givenSuccessfulSubmission();

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
	}

	private void givenSuccessfulSubmission() {
		given(submissionManager.trySubmission(paymentAccessor)).willReturn(OK);
	}

	private void givenValidPayment() {
		given(queryFeeCheck.nodePaymentValidity(Collections.emptyList(), queryCost, node)).willReturn(OK);
	}

	private void givenEstimableCost() {
		given(fees.estimatePayment(eq(query), eq(usagePrices), eq(stateView), eq(now), any())).willReturn(detailCost);
	}

	private void givenComputableCost() {
		given(fees.computePayment(eq(query), eq(usagePrices), eq(stateView), eq(now), any())).willReturn(detailCost);
	}

	private void givenAvailableResourcePrices() {
		given(resourceCosts.defaultPricesGiven(ConsensusGetTopicInfo, now)).willReturn(usagePrices);
	}

	private void givenHappyService() {
		given(service.checkValidity(query, stateView)).willReturn(OK);
	}

	private void givenCapacity() {
		given(throttles.shouldThrottleQuery(ConsensusGetTopicInfo)).willReturn(false);
	}

	private void givenPermission() {
		given(hapiOpPermissions.permissibilityOf(ConsensusGetTopicInfo, payer)).willReturn(OK);
	}

	private void givenSuperuserPermission() {
		given(hapiOpPermissions.permissibilityOf(ConsensusGetTopicInfo, superuser)).willReturn(OK);
	}

	private void givenCostEstimateIsRequired() {
		given(service.needsAnswerOnlyCost(query)).willReturn(true);
	}

	private void givenPaymentIsRequired() {
		given(service.requiresNodePayment(query)).willReturn(true);
	}

	private void givenAvailFunction() {
		given(service.canonicalFunction()).willReturn(ConsensusGetTopicInfo);
	}

	private void givenValidExtraction() {
		given(transactionPrecheck.performForQueryPayment(paymentAccessor.getSignedTxnWrapper()))
				.willReturn(Pair.of(new TxnValidityAndFeeReq(OK), paymentAccessor));
	}

	private void givenValidSuperuserExtraction() {
		given(transactionPrecheck.performForQueryPayment(superuserPaymentAccessor.getSignedTxnWrapper()))
				.willReturn(Pair.of(new TxnValidityAndFeeReq(OK), superuserPaymentAccessor));
	}

	private void givenExtractablePayment() {
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(paymentAccessor));
	}

	private void givenExtractableSuperuserPayment() {
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(superuserPaymentAccessor));
	}

	private void givenNoExtractablePayment() {
		given(service.extractPaymentFrom(query)).willReturn(Optional.empty());
	}

	private void givenValidHeader() {
		given(queryHeaderValidity.checkHeader(query)).willReturn(OK);
	}

	private void setupServiceResponse(ResponseCodeEnum expected) {
		given(service.responseGiven(query, stateView, expected)).willReturn(response);
	}

	private void setupCostAwareFailedServiceResponse(ResponseCodeEnum expected) {
		given(service.responseGiven(query, stateView, expected, queryCost)).willReturn(response);
	}

	private void setupCostAwareSuccessServiceResponse() {
		given(service.responseGiven(query, stateView, OK, queryCost, Collections.emptyMap())).willReturn(response);
	}
}
