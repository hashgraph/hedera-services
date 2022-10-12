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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SolvencyPrecheckTest {
    private final long insolventExpiry = 1_234_567L;
    private final long payerBalance = 1_234L;
    private final long acceptableRequiredFee = 666L;
    private final long acceptableRequiredFeeSansSvc = 333L;
    private final long unacceptableRequiredFee = 667L;
    private final long unacceptableRequiredFeeSansSvc = 334L;
    private final FeeObject acceptableFees = new FeeObject(111L, 222L, 333L);
    private final FeeObject unacceptableFees = new FeeObject(111L, 223L, 333L);
    private final JKey payerKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();
    private final Timestamp now = MiscUtils.asTimestamp(Instant.ofEpochSecond(1_234_567L));
    private final AccountID payer = IdUtils.asAccount("0.0.1234");
    private final MerkleAccount solventPayerAccount =
            MerkleAccountFactory.newAccount().accountKeys(payerKey).balance(payerBalance).get();
    private final MerkleAccount insolventPayerAccount =
            MerkleAccountFactory.newAccount()
                    .expirationTime(insolventExpiry)
                    .accountKeys(payerKey)
                    .balance(0L)
                    .get();
    private final SignedTxnAccessor accessorCoveringAllFees =
            SignedTxnAccessor.uncheckedFrom(
                    Transaction.newBuilder()
                            .setBodyBytes(
                                    TransactionBody.newBuilder()
                                            .setTransactionID(
                                                    TransactionID.newBuilder()
                                                            .setTransactionValidStart(now)
                                                            .setAccountID(payer))
                                            .setTransactionFee(acceptableRequiredFee)
                                            .build()
                                            .toByteString())
                            .build());
    private final SignedTxnAccessor accessorNotCoveringSvcFee =
            SignedTxnAccessor.uncheckedFrom(
                    Transaction.newBuilder()
                            .setBodyBytes(
                                    TransactionBody.newBuilder()
                                            .setTransactionID(
                                                    TransactionID.newBuilder()
                                                            .setTransactionValidStart(now)
                                                            .setAccountID(payer))
                                            .setTransactionFee(acceptableRequiredFeeSansSvc)
                                            .build()
                                            .toByteString())
                            .build());

    @Mock private OptionValidator validator;
    @Mock private StateView stateView;
    @Mock private FeeExemptions feeExemptions;
    @Mock private FeeCalculator feeCalculator;
    @Mock private PrecheckVerifier precheckVerifier;
    @Mock private AccountStorageAdapter accounts;

    private SolvencyPrecheck subject;

    @BeforeEach
    void setUp() {
        subject =
                new SolvencyPrecheck(
                        feeExemptions,
                        feeCalculator,
                        validator,
                        precheckVerifier,
                        () -> stateView,
                        () -> accounts);
    }

    @Test
    void rejectsUnusablePayer() {
        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertJustValidity(result, PAYER_ACCOUNT_NOT_FOUND);
    }

    @Test
    void preservesRespForPrefixMismatch() throws Exception {
        givenSolventPayer();
        given(precheckVerifier.hasNecessarySignatures(accessorCoveringAllFees))
                .willThrow(KeyPrefixMismatchException.class);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertJustValidity(result, KEY_PREFIX_MISMATCH);
    }

    @Test
    void preservesRespForInvalidAccountId() throws Exception {
        givenSolventPayer();
        given(precheckVerifier.hasNecessarySignatures(accessorCoveringAllFees))
                .willThrow(InvalidAccountIDException.class);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertJustValidity(result, INVALID_ACCOUNT_ID);
    }

    @Test
    void preservesRespForGenericFailure() throws Exception {
        givenSolventPayer();
        given(precheckVerifier.hasNecessarySignatures(accessorCoveringAllFees))
                .willThrow(Exception.class);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertJustValidity(result, INVALID_SIGNATURE);
    }

    @Test
    void preservesRespForMissingSigs() throws Exception {
        givenSolventPayer();
        given(precheckVerifier.hasNecessarySignatures(accessorCoveringAllFees)).willReturn(false);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertJustValidity(result, INVALID_SIGNATURE);
    }

    @Test
    void alwaysOkForVerifiedExemptPayer() {
        givenSolventPayer();
        givenValidSigs();
        given(feeExemptions.hasExemptPayer(accessorCoveringAllFees)).willReturn(true);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertJustValidity(result, OK);
    }

    @Test
    void translatesFeeCalcFailure() {
        givenSolventPayer();
        givenValidSigs();
        given(feeCalculator.estimateFee(accessorCoveringAllFees, payerKey, stateView, now))
                .willThrow(IllegalStateException.class);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertJustValidity(result, FAIL_FEE);
    }

    @Test
    void recognizesUnwillingnessToPayAllFees() {
        givenSolventPayer();
        givenValidSigs();
        given(feeCalculator.estimateFee(accessorCoveringAllFees, payerKey, stateView, now))
                .willReturn(unacceptableFees);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertBothValidityAndReqFee(result, INSUFFICIENT_TX_FEE, unacceptableRequiredFee);
    }

    @Test
    void recognizesUnwillingnessToPayNodeAndNetwork() {
        givenSolventPayer();
        givenValidSigsNonSvc();
        given(feeCalculator.estimateFee(accessorNotCoveringSvcFee, payerKey, stateView, now))
                .willReturn(unacceptableFees);

        // when:
        var result = subject.assessSansSvcFees(accessorNotCoveringSvcFee);

        // then:
        assertBothValidityAndReqFee(result, INSUFFICIENT_TX_FEE, unacceptableRequiredFeeSansSvc);
    }

    @Test
    void refinesInsufficientPayerBalanceToDetachedResponseIfExpired() {
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean()))
                .willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        givenInsolventPayer();
        givenValidSigs();
        givenAcceptableFees();
        given(feeCalculator.estimatedNonFeePayerAdjustments(accessorCoveringAllFees, now))
                .willReturn(+payerBalance);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertBothValidityAndReqFee(
                result, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, acceptableRequiredFee);
    }

    @Test
    void reportsIpbIfNotDetached() {
        givenInsolventPayer();
        givenValidSigs();
        givenAcceptableFees();
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);
        given(feeCalculator.estimatedNonFeePayerAdjustments(accessorCoveringAllFees, now))
                .willReturn(+payerBalance);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertBothValidityAndReqFee(result, INSUFFICIENT_PAYER_BALANCE, acceptableRequiredFee);
    }

    @Test
    void recognizesInTxnAdjustmentsDontCreateSolvency() {
        givenInsolventPayer();
        givenValidSigs();
        givenAcceptableFees();
        given(feeCalculator.estimatedNonFeePayerAdjustments(accessorCoveringAllFees, now))
                .willReturn(+payerBalance);
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertBothValidityAndReqFee(result, INSUFFICIENT_PAYER_BALANCE, acceptableRequiredFee);
    }

    @Test
    void recognizesInTxnAdjustmentsMayCreateInsolvency() {
        givenSolventPayer();
        givenValidSigs();
        givenAcceptableFees();
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);
        given(feeCalculator.estimatedNonFeePayerAdjustments(accessorCoveringAllFees, now))
                .willReturn(-payerBalance);

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertBothValidityAndReqFee(result, INSUFFICIENT_PAYER_BALANCE, acceptableRequiredFee);
    }

    @Test
    void recognizesSolventPayer() {
        givenSolventPayer();
        givenValidSigs();
        givenAcceptableFees();
        givenNoMaterialAdjustment();

        // when:
        var result = subject.assessWithSvcFees(accessorCoveringAllFees);

        // then:
        assertBothValidityAndReqFee(result, OK, acceptableRequiredFee);
    }

    private void givenNoMaterialAdjustment() {
        given(feeCalculator.estimatedNonFeePayerAdjustments(accessorCoveringAllFees, now))
                .willReturn(-1L);
    }

    private void givenAcceptableFees() {
        given(feeCalculator.estimateFee(accessorCoveringAllFees, payerKey, stateView, now))
                .willReturn(acceptableFees);
    }

    private void givenValidSigs() {
        try {
            given(precheckVerifier.hasNecessarySignatures(accessorCoveringAllFees))
                    .willReturn(true);
        } catch (Exception impossible) {
        }
    }

    private void givenValidSigsNonSvc() {
        try {
            given(precheckVerifier.hasNecessarySignatures(accessorNotCoveringSvcFee))
                    .willReturn(true);
        } catch (Exception impossible) {
        }
    }

    private void givenSolventPayer() {
        given(accounts.get(EntityNum.fromAccountId(payer))).willReturn(solventPayerAccount);
    }

    private void givenInsolventPayer() {
        given(accounts.get(EntityNum.fromAccountId(payer))).willReturn(insolventPayerAccount);
    }

    private void assertJustValidity(TxnValidityAndFeeReq result, ResponseCodeEnum expected) {
        assertEquals(expected, result.getValidity());
        assertEquals(0, result.getRequiredFee());
    }

    private void assertBothValidityAndReqFee(
            TxnValidityAndFeeReq result, ResponseCodeEnum expected, long reqFee) {
        assertEquals(expected, result.getValidity());
        assertEquals(reqFee, result.getRequiredFee());
    }
}
