/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.submission;

import static com.hedera.services.txns.submission.PresolvencyFlaws.WELL_KNOWN_FLAWS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.system.PlatformStatus;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ArgumentConverter;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionPrecheckTest {
    private static final long reqFee = 1234L;

    @Mock private QueryFeeCheck queryFeeCheck;
    @Mock private CurrentPlatformStatus currentPlatformStatus;
    @Mock private SystemPrecheck systemPrecheck;
    @Mock private SyntaxPrecheck syntaxPrecheck;
    @Mock private SemanticPrecheck semanticPrecheck;
    @Mock private SolvencyPrecheck solvencyPrecheck;
    @Mock private StructuralPrecheck structuralPrecheck;

    private TransactionPrecheck subject;

    @BeforeEach
    void setUp() {
        final var stagedPrechecks =
                new StagedPrechecks(
                        syntaxPrecheck,
                        systemPrecheck,
                        semanticPrecheck,
                        solvencyPrecheck,
                        structuralPrecheck);
        subject = new TransactionPrecheck(queryFeeCheck, stagedPrechecks, currentPlatformStatus);
    }

    @Test
    void abortsOnInactivePlatform() {
        given(currentPlatformStatus.get()).willReturn(PlatformStatus.MAINTENANCE);

        final var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());
        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertFailure(PLATFORM_NOT_ACTIVE, topLevelResponse);
        assertFailure(PLATFORM_NOT_ACTIVE, queryPaymentResponse);
    }

    @Test
    void abortsOnStructuralFlaw() {
        givenActivePlatform();
        given(structuralPrecheck.assess(any()))
                .willReturn(WELL_KNOWN_FLAWS.get(TRANSACTION_TOO_MANY_LAYERS));

        final var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());
        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertFailure(TRANSACTION_TOO_MANY_LAYERS, topLevelResponse);
        assertFailure(TRANSACTION_TOO_MANY_LAYERS, queryPaymentResponse);
    }

    @Test
    void abortsOnStructuralFlawWithBadAccessor() {
        givenActivePlatform();
        final Pair<TxnValidityAndFeeReq, SignedTxnAccessor> dummyPair =
                Pair.of(new TxnValidityAndFeeReq(OK), null);
        given(structuralPrecheck.assess(any())).willReturn(dummyPair);

        final var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());
        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertFailure(OK, topLevelResponse);
        assertFailure(OK, queryPaymentResponse);
        verify(syntaxPrecheck, never()).validate(any());
    }

    @ParameterizedTest
    @CsvSource({
        "INVALID_TRANSACTION_ID",
        "TRANSACTION_ID_FIELD_NOT_ALLOWED",
        "DUPLICATE_TRANSACTION",
        "INSUFFICIENT_TX_FEE",
        "PAYER_ACCOUNT_NOT_FOUND",
        "INVALID_NODE_ACCOUNT",
        "MEMO_TOO_LONG",
        "INVALID_ZERO_BYTE_IN_STRING",
        "INVALID_TRANSACTION_DURATION",
        "TRANSACTION_EXPIRED",
        "INVALID_TRANSACTION_START"
    })
    void abortsOnSyntaxError(
            @ConvertWith(ResponseCodeConverter.class) final ResponseCodeEnum syntaxError) {
        givenActivePlatform();
        givenStructuralSoundness();
        given(syntaxPrecheck.validate(any())).willReturn(syntaxError);

        final var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());
        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertFailure(syntaxError, topLevelResponse);
        assertFailure(syntaxError, queryPaymentResponse);
    }

    @Test
    void abortsOnSemanticErrorForTopLevel() {
        givenActivePlatform();
        givenStructuralSoundness();
        givenValidSyntax();
        given(semanticPrecheck.validate(any(), any(), eq(NOT_SUPPORTED))).willReturn(NOT_SUPPORTED);

        final var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());

        assertFailure(NOT_SUPPORTED, topLevelResponse);
    }

    @Test
    void abortsOnSemanticErrorForQueryPayment() {
        givenActivePlatform();
        givenStructuralSoundness();
        givenValidSyntax();
        given(semanticPrecheck.validate(any(), eq(CryptoTransfer), eq(INSUFFICIENT_TX_FEE)))
                .willReturn(INSUFFICIENT_TX_FEE);

        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertFailure(INSUFFICIENT_TX_FEE, queryPaymentResponse);
    }

    @Test
    void presolvencyFlawsCanResolveEvenUnexpectedError() {
        final var response = PresolvencyFlaws.responseForFlawed(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        assertFailure(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, response);
    }

    @Test
    void abortsOnInsolvencyForTopLevel() {
        givenActivePlatform();
        givenStructuralSoundness();
        givenValidSyntax();
        givenValidSemantics();
        given(solvencyPrecheck.assessSansSvcFees(any()))
                .willReturn(new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, reqFee));

        final var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());

        assertFailure(INSUFFICIENT_TX_FEE, reqFee, topLevelResponse);
    }

    @Test
    void abortsOnInsolvencyForQueryPayment() {
        givenActivePlatform();
        givenStructuralSoundness();
        givenValidSyntax();
        givenValidSemantics();
        given(solvencyPrecheck.assessWithSvcFees(any()))
                .willReturn(new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, reqFee));

        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertFailure(INSUFFICIENT_TX_FEE, reqFee, queryPaymentResponse);
    }

    @Test
    void abortsOnFailedSystemChecksForTopLevel() {
        givenActivePlatform();
        givenStructuralSoundness();
        givenValidSyntax();
        givenValidSemantics();
        givenNodeAndNetworkSolvency();
        given(systemPrecheck.screen(any())).willReturn(BUSY);

        final var topLevelResponse = subject.performForTopLevel(Transaction.getDefaultInstance());

        assertFailure(BUSY, reqFee, topLevelResponse);
    }

    @Test
    void doesntPerformSystemChecksForQueryPayments() {
        givenActivePlatform();
        givenStructuralSoundness();
        givenValidSyntax();
        givenValidSemantics();
        givenFullSolvency();
        givenValidQueryPaymentXfers();

        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertSuccess(reqFee, queryPaymentResponse);
    }

    @Test
    void rejectsInvalidQueryPaymentXfers() {
        givenActivePlatform();
        givenStructuralSoundness();
        givenValidSyntax();
        givenValidSemantics();
        givenFullSolvency();
        given(queryFeeCheck.validateQueryPaymentTransfers(any()))
                .willReturn(INSUFFICIENT_PAYER_BALANCE);

        final var queryPaymentResponse =
                subject.performForQueryPayment(Transaction.getDefaultInstance());

        assertFailure(INSUFFICIENT_PAYER_BALANCE, reqFee, queryPaymentResponse);
    }

    private void givenValidQueryPaymentXfers() {
        given(queryFeeCheck.validateQueryPaymentTransfers(any())).willReturn(OK);
    }

    private void givenActivePlatform() {
        given(currentPlatformStatus.get()).willReturn(PlatformStatus.ACTIVE);
    }

    private void givenStructuralSoundness() {
        given(structuralPrecheck.assess(any()))
                .willReturn(
                        Pair.of(
                                new TxnValidityAndFeeReq(OK),
                                SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance())));
    }

    private void givenFullSolvency() {
        given(solvencyPrecheck.assessWithSvcFees(any()))
                .willReturn(new TxnValidityAndFeeReq(OK, reqFee));
    }

    private void givenNodeAndNetworkSolvency() {
        given(solvencyPrecheck.assessSansSvcFees(any()))
                .willReturn(new TxnValidityAndFeeReq(OK, reqFee));
    }

    private void givenValidSyntax() {
        given(syntaxPrecheck.validate(any())).willReturn(OK);
    }

    private void givenValidSemantics() {
        given(semanticPrecheck.validate(any(), any(), any())).willReturn(OK);
    }

    private void assertSuccess(
            final long reqFee, final Pair<TxnValidityAndFeeReq, SignedTxnAccessor> response) {
        assertEquals(OK, response.getLeft().getValidity());
        assertEquals(reqFee, response.getLeft().getRequiredFee());
        assertNotNull(response.getRight());
    }

    private void assertFailure(
            final ResponseCodeEnum abort,
            final Pair<TxnValidityAndFeeReq, SignedTxnAccessor> response) {
        assertFailure(abort, 0L, response);
    }

    private void assertFailure(
            final ResponseCodeEnum abort,
            final long reqFee,
            final Pair<TxnValidityAndFeeReq, SignedTxnAccessor> response) {
        final var req = response.getLeft();
        assertEquals(abort, req.getValidity());
        assertEquals(reqFee, req.getRequiredFee());
        assertNull(response.getRight());
    }

    private static final class ResponseCodeConverter implements ArgumentConverter {
        @Override
        public Object convert(Object arg, ParameterContext parameterContext)
                throws ArgumentConversionException {
            return ResponseCodeEnum.valueOf((String) arg);
        }
    }
}
