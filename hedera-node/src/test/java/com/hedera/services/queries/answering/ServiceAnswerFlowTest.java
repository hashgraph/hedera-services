package com.hedera.services.queries.answering;

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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetStakers;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.swirlds.common.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;

@RunWith(JUnitPlatform.class)
class ServiceAnswerFlowTest {
	Timestamp at = Timestamp.newBuilder().setSeconds(1_234_567L).build();
	HederaFunctionality function = HederaFunctionality.ConsensusGetTopicInfo;

	TransactionID userTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount("0.0.1002"))
			.setTransactionValidStart(at)
			.build();
	Transaction userTxn = Transaction.newBuilder()
			.setBody(TransactionBody.newBuilder().setTransactionID(userTxnId))
			.build();
	SignedTxnAccessor userAccessor = SignedTxnAccessor.uncheckedFrom(userTxn);

	TransactionID masterTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount("0.0.50"))
			.setTransactionValidStart(at)
			.build();
	Transaction masterTxn = Transaction.newBuilder()
			.setBody(TransactionBody.newBuilder().setTransactionID(masterTxnId))
			.build();
	SignedTxnAccessor masterAccessor = SignedTxnAccessor.uncheckedFrom(masterTxn);

	FeeObject costs = new FeeObject(1L, 2L, 3L);
	FeeObject zeroCosts = new FeeObject(0L, 0L, 0L);

	Platform platform;
	FeeCalculator fees;
	TransactionHandler legacyHandler;
	StateView view;
	Supplier<StateView> stateViews;
	UsagePricesProvider resourceCosts;
	FunctionalityThrottling throttles;

	Query query = Query.getDefaultInstance();
	FeeData usagePrices;
	Response response;
	AnswerService service;

	ServiceAnswerFlow subject;

	@BeforeEach
	private void setup() {
		fees = mock(FeeCalculator.class);
		view = mock(StateView.class);
		platform = mock(Platform.class);
		throttles = mock(FunctionalityThrottling.class);
		legacyHandler = mock(TransactionHandler.class);
		stateViews = () -> view;
		resourceCosts = mock(UsagePricesProvider.class);
		usagePrices = mock(FeeData.class);

		service = mock(AnswerService.class);
		response = mock(Response.class);

		subject = new ServiceAnswerFlow(platform, fees, legacyHandler, stateViews, resourceCosts, throttles);
	}

	@Test
	public void doesntThrottleExemptAccounts() {
		// setup:
		Response wrongResponse = mock(Response.class);

		given(throttles.shouldThrottle(CryptoGetStakers)).willReturn(true);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(legacyHandler.validateQuery(query, false)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(masterAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(false);
		given(service.needsAnswerOnlyCost(query)).willReturn(false);
		// and:
		given(service.responseGiven(query, view, BUSY)).willReturn(wrongResponse);
		given(service.responseGiven(query, view, OK, 0)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(throttles, never()).shouldThrottle(CryptoGetStakers);
	}


	@Test
	public void throttlesIfAppropriate() {
		given(service.canonicalFunction()).willReturn(function);
		given(throttles.shouldThrottle(function)).willReturn(true);
		given(service.responseGiven(query, view, BUSY)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(throttles).shouldThrottle(function);
	}

	@Test
	public void validatesMetaAsExpected() {
		given(legacyHandler.validateQuery(query, false)).willReturn(ACCOUNT_IS_NOT_GENESIS_ACCOUNT);
		given(service.responseGiven(query, view, ACCOUNT_IS_NOT_GENESIS_ACCOUNT)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service).requiresNodePayment(query);
	}

	@Test
	public void validatesSpecificAfterMetaOk() {
		given(legacyHandler.validateQuery(query, false)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(INVALID_ACCOUNT_ID);
		given(service.responseGiven(query, view, INVALID_ACCOUNT_ID)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service).requiresNodePayment(query);
	}

	@Test
	public void figuresResponseWhenNoNodePaymentNoAnswerOnlyCostRequired() {
		given(legacyHandler.validateQuery(query, false)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(userAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(false);
		given(service.needsAnswerOnlyCost(query)).willReturn(false);
		given(service.responseGiven(query, view, OK, 0)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service, times(2)).requiresNodePayment(query);
		verify(service).needsAnswerOnlyCost(query);
		verifyNoInteractions(fees);
	}

	@Test
	public void figuresResponseWhenNoNodePaymentButAnswerOnlyCostRequired() {
		given(legacyHandler.validateQuery(query, false)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(userAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(false);
		given(service.needsAnswerOnlyCost(query)).willReturn(true);
		given(fees.estimatePayment(query, usagePrices, view, at, ANSWER_ONLY)).willReturn(costs);
		given(service.responseGiven(query, view, OK, 6)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service, times(2)).requiresNodePayment(query);
		verify(service).needsAnswerOnlyCost(query);
	}

	@Test
	public void figuresResponseWhenNodePaymentButNoAnswerOnlyCostRequired() throws Exception {
		given(legacyHandler.validateQuery(query, true)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(userAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(true);
		given(service.needsAnswerOnlyCost(query)).willReturn(false);
		given(fees.computePayment(query, usagePrices, view, at)).willReturn(costs);
		given(legacyHandler.validateTransactionPreConsensus(userTxn, true))
				.willReturn(new TxnValidityAndFeeReq(INVALID_ACCOUNT_ID));
		given(service.responseGiven(query, view, INVALID_ACCOUNT_ID, 6)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service, times(2)).requiresNodePayment(query);
		verify(legacyHandler, never()).submitTransaction(platform, userTxn, userTxnId);
	}

	@Test
	public void figuresResponseWhenZeroNodePaymentButNoAnswerOnlyCostRequired() {
		given(legacyHandler.validateQuery(query, true)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(userAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(true);
		given(service.needsAnswerOnlyCost(query)).willReturn(false);
		given(fees.computePayment(query, usagePrices, view, at)).willReturn(zeroCosts);
		given(legacyHandler.validateTransactionPreConsensus(userTxn, true))
				.willReturn(new TxnValidityAndFeeReq(INVALID_ACCOUNT_ID));
		given(service.responseGiven(query, view, INVALID_ACCOUNT_ID, 6)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		verify(service, times(2)).requiresNodePayment(query);
		verify(legacyHandler, never()).validateTransactionPreConsensus(any(), anyBoolean());
	}

	@Test
	public void validatesFullyWhenNodePaymentButNoAnswerOnlyCostRequired() throws Exception {
		given(legacyHandler.validateQuery(query, true)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(userAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(true);
		given(service.needsAnswerOnlyCost(query)).willReturn(false);
		given(fees.computePayment(query, usagePrices, view, at)).willReturn(costs);
		given(legacyHandler.validateTransactionPreConsensus(userTxn, true))
				.willReturn(new TxnValidityAndFeeReq(OK));
		given(legacyHandler.nodePaymentValidity(userTxn, 6)).willReturn(INSUFFICIENT_PAYER_BALANCE);
		given(service.responseGiven(query, view, INSUFFICIENT_PAYER_BALANCE, 6)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service, times(2)).requiresNodePayment(query);
		verify(legacyHandler, never()).submitTransaction(platform, userTxn, userTxnId);
	}

	@Test
	public void submitsWhenAppropriate() throws Exception {
		given(legacyHandler.validateQuery(query, true)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(userAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(true);
		given(service.needsAnswerOnlyCost(query)).willReturn(false);
		given(fees.computePayment(query, usagePrices, view, at)).willReturn(costs);
		given(legacyHandler.validateTransactionPreConsensus(userTxn, true))
				.willReturn(new TxnValidityAndFeeReq(OK));
		given(legacyHandler.nodePaymentValidity(userTxn, 6)).willReturn(OK);
		given(service.responseGiven(query, view, OK, 6)).willReturn(response);
		given(legacyHandler.submitTransaction(platform, userTxn, userTxnId)).willReturn(true);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service, times(2)).requiresNodePayment(query);
	}

	@Test
	public void recoversFromPtce() throws Exception {
		given(legacyHandler.validateQuery(query, true)).willReturn(OK);
		given(service.checkValidity(query, view)).willReturn(OK);
		given(service.extractPaymentFrom(query)).willReturn(Optional.of(userAccessor));
		given(service.canonicalFunction()).willReturn(CryptoGetStakers);
		given(resourceCosts.pricesGiven(CryptoGetStakers, at)).willReturn(usagePrices);
		given(service.requiresNodePayment(query)).willReturn(true);
		given(service.needsAnswerOnlyCost(query)).willReturn(false);
		given(fees.computePayment(query, usagePrices, view, at)).willReturn(costs);
		given(legacyHandler.validateTransactionPreConsensus(userTxn, true))
				.willReturn(new TxnValidityAndFeeReq(OK));
		given(legacyHandler.nodePaymentValidity(userTxn, 6)).willReturn(OK);
		given(legacyHandler.submitTransaction(platform, userTxn, userTxnId)).willReturn(false);
		given(service.responseGiven(query, view, PLATFORM_TRANSACTION_NOT_CREATED, 6)).willReturn(response);

		// when:
		Response actual = subject.satisfyUsing(service, query);

		// then:
		assertEquals(response, actual);
		verify(service, times(2)).requiresNodePayment(query);
		verify(legacyHandler).submitTransaction(platform, userTxn, userTxnId);
	}
}
