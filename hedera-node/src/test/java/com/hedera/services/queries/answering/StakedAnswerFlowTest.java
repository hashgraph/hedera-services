/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.queries.answering;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import com.hedera.services.utils.accessors.SignedTxnAccessor;
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
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakedAnswerFlowTest {
    private static final long queryCost = 666L;
    private static final FeeObject detailCost = new FeeObject(111L, 222L, 333L);
    private static final Instant instant = Instant.ofEpochSecond(1_234_567L);
    private static final Timestamp now = MiscUtils.asTimestamp(instant);
    private static final AccountID node = IdUtils.asAccount("0.0.3");
    private static final AccountID payer = IdUtils.asAccount("0.0.1234");
    private static final AccountID superuser = IdUtils.asAccount("0.0.50");
    private static final Query query = Query.getDefaultInstance();
    private static final Response response = Response.getDefaultInstance();
    private static final SignedTxnAccessor paymentAccessor = accessorWith(payer);
    private static final SignedTxnAccessor superuserPaymentAccessor = accessorWith(superuser);

    private static final AccountNumbers accountNumbers = new MockAccountNumbers();

    @Mock private FeeData usagePrices;
    @Mock private StateView stateView;
    @Mock private FeeCalculator fees;
    @Mock private AnswerService service;
    @Mock private QueryFeeCheck queryFeeCheck;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private QueryHeaderValidity queryHeaderValidity;
    @Mock private TransactionPrecheck transactionPrecheck;
    @Mock private HapiOpPermissions hapiOpPermissions;
    @Mock private FunctionalityThrottling throttles;
    @Mock private PlatformSubmissionManager submissionManager;

    private StakedAnswerFlow subject;

    @BeforeEach
    void setUp() {
        subject =
                new StakedAnswerFlow(
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

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
    }

    @Test
    void rejectsNetworkGetExecutionTimeQueriesWithNoPayment() {
        setupServiceResponse(NOT_SUPPORTED);
        givenValidHeader();
        given(service.canonicalFunction()).willReturn(NetworkGetExecutionTime);

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
    }

    @Test
    void rejectsUnprivilegedNetworkGetExecutionTimeQueriesWithPayment() {
        setupServiceResponse(NOT_SUPPORTED);
        givenValidHeader();
        givenPaymentIsRequired();
        givenExtractablePayment();
        givenValidExtraction();
        given(hapiOpPermissions.permissibilityOf(NetworkGetExecutionTime, payer))
                .willReturn(NOT_SUPPORTED);
        given(service.canonicalFunction()).willReturn(NetworkGetExecutionTime);

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
    }

    @Test
    void allowsPrivilegedNetworkGetExecutionTimeQueriesWithPayment() {
        setupCostAwareSuccessServiceResponse();
        givenValidHeader();
        givenExtractableSuperuserPayment();
        givenValidSuperuserExtraction();
        givenPaymentIsRequired();
        given(service.canonicalFunction()).willReturn(NetworkGetExecutionTime);
        given(
                        transactionPrecheck.performForQueryPayment(
                                superuserPaymentAccessor.getSignedTxnWrapper()))
                .willReturn(Pair.of(new TxnValidityAndFeeReq(OK), superuserPaymentAccessor));
        given(hapiOpPermissions.permissibilityOf(NetworkGetExecutionTime, superuser))
                .willReturn(OK);
        givenHappyService();
        given(resourceCosts.defaultPricesGiven(NetworkGetExecutionTime, now))
                .willReturn(usagePrices);
        givenComputableCost();

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
        verify(submissionManager, never()).trySubmission(superuserPaymentAccessor);
    }

    @Test
    void immediatelyRejectsMissingPayment() {
        setupServiceResponse(INSUFFICIENT_TX_FEE);
        givenValidHeader();
        givenPaymentIsRequired();

        final var actual = subject.satisfyUsing(service, query);

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

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
    }

    @Test
    void doesntChargeSuperusers() {
        setupCostAwareSuccessServiceResponse();
        givenValidHeader();
        givenExtractableSuperuserPayment();
        givenValidSuperuserExtraction();
        givenPaymentIsRequired();
        given(
                        transactionPrecheck.performForQueryPayment(
                                superuserPaymentAccessor.getSignedTxnWrapper()))
                .willReturn(Pair.of(new TxnValidityAndFeeReq(OK), superuserPaymentAccessor));
        givenAvailFunction();
        givenSuperuserPermission();
        givenHappyService();
        givenAvailableResourcePrices();
        givenComputableCost();

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
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
        given(hapiOpPermissions.permissibilityOf(ConsensusGetTopicInfo, payer))
                .willReturn(NOT_SUPPORTED);

        final var actual = subject.satisfyUsing(service, query);

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
        given(throttles.shouldThrottleQuery(eq(ConsensusGetTopicInfo), any())).willReturn(true);

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
    }

    @Test
    void throttlesIfBusyEvenWhenNoPaymentNeeded() {
        setupServiceResponse(BUSY);
        givenValidHeader();
        givenNoExtractablePayment();
        givenAvailFunction();
        given(throttles.shouldThrottleQuery(eq(ConsensusGetTopicInfo), any())).willReturn(true);

        final var actual = subject.satisfyUsing(service, query);

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

        final var actual = subject.satisfyUsing(service, query);

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

        final var actual = subject.satisfyUsing(service, query);

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
        given(submissionManager.trySubmission(paymentAccessor))
                .willReturn(PLATFORM_TRANSACTION_NOT_CREATED);

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
    }

    @Test
    void returnsCostToCostAnswer() {
        setupCostAwareSuccessServiceResponse();
        final var mockedStatic = mockStatic(Instant.class);
        mockedStatic.when(Instant::now).thenReturn(instant);
        givenValidHeader();
        givenAvailFunction();
        givenCapacity();
        givenHappyService();
        givenAvailableResourcePrices();
        givenEstimableCost();
        givenCostEstimateIsRequired();

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);

        mockedStatic.close();
    }

    @Test
    void returnsCostToCostAnswerEvenIfBadPayment() {
        setupCostAwareSuccessServiceResponse();
        final var mockedStatic = mockStatic(Instant.class);
        mockedStatic.when(Instant::now).thenReturn(instant);
        givenValidHeader();
        givenExtractablePayment();
        givenAvailFunction();
        givenCapacity();
        givenHappyService();
        givenAvailableResourcePrices();
        givenEstimableCost();
        givenCostEstimateIsRequired();

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
        verify(transactionPrecheck, never()).performForQueryPayment(any());

        mockedStatic.close();
    }

    @Test
    void doesNotThrottleIfSuperuser() {
        setupCostAwareSuccessServiceResponse();
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

        final var actual = subject.satisfyUsing(service, query);

        assertEquals(response, actual);
    }

    private void givenSuccessfulSubmission() {
        given(submissionManager.trySubmission(paymentAccessor)).willReturn(OK);
    }

    private void givenValidPayment() {
        given(queryFeeCheck.nodePaymentValidity(Collections.emptyList(), queryCost, node))
                .willReturn(OK);
    }

    private void givenEstimableCost() {
        given(fees.estimatePayment(eq(query), eq(usagePrices), eq(stateView), eq(now), any()))
                .willReturn(detailCost);
    }

    private void givenComputableCost() {
        given(fees.computePayment(eq(query), eq(usagePrices), eq(stateView), eq(now), any()))
                .willReturn(detailCost);
    }

    private void givenAvailableResourcePrices() {
        given(resourceCosts.defaultPricesGiven(ConsensusGetTopicInfo, now)).willReturn(usagePrices);
    }

    private void givenHappyService() {
        given(service.checkValidity(query, stateView)).willReturn(OK);
    }

    private void givenCapacity() {
        given(throttles.shouldThrottleQuery(eq(ConsensusGetTopicInfo), any())).willReturn(false);
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
        given(
                        transactionPrecheck.performForQueryPayment(
                                superuserPaymentAccessor.getSignedTxnWrapper()))
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

    private void setupServiceResponse(final ResponseCodeEnum expected) {
        given(service.responseGiven(query, stateView, expected)).willReturn(response);
    }

    private void setupCostAwareFailedServiceResponse(final ResponseCodeEnum expected) {
        given(service.responseGiven(query, stateView, expected, queryCost)).willReturn(response);
    }

    private void setupCostAwareSuccessServiceResponse() {
        given(service.responseGiven(query, stateView, OK, queryCost, Collections.emptyMap()))
                .willReturn(response);
    }

    private static final SignedTxnAccessor accessorWith(final AccountID txnPayer) {
        return SignedTxnAccessor.uncheckedFrom(
                Transaction.newBuilder()
                        .setBodyBytes(
                                TransactionBody.newBuilder()
                                        .setNodeAccountID(node)
                                        .setTransactionID(
                                                TransactionID.newBuilder()
                                                        .setTransactionValidStart(now)
                                                        .setAccountID(txnPayer))
                                        .build()
                                        .toByteString())
                        .build());
    }
}
