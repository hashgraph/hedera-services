/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ApprovalSwitchHelper;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.function.Predicate;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ClassicTransfersCallTest extends HtsCallTestBase {
    private static final TupleType INT64_ENCODER = TupleType.parse(ReturnTypes.INT_64);

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private Predicate<Key> signatureTest;

    @Mock
    private ApprovalSwitchHelper approvalSwitchHelper;

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private CryptoTransferRecordBuilder recordBuilder;

    private ClassicTransfersCall subject;

    @Test
    void doesNotRetryWithInitialSuccess() {
        givenRetryingSubject();
    }

    @Test
    void throwsOnUnrecognizedSelector() {
        given(attempt.selector()).willReturn(Erc20TransfersCall.ERC_20_TRANSFER.selector());
        given(attempt.enhancement()).willReturn(mockEnhancement());
        final var e = assertThrows(
                IllegalArgumentException.class, () -> ClassicTransfersCall.from(attempt, EIP_1014_ADDRESS, true));
        assertTrue(e.getMessage().endsWith("is not a classic transfer"));
    }

    @Test
    void transferHappyPathCompletesWithSuccessResponseCode() {
        givenRetryingSubject();
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(CryptoTransferRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SUCCESS);

        givenRetryingSubject();

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(asBytesResult(INT64_ENCODER.encodeElements((long) SUCCESS.protoOrdinal())), result.getOutput());
    }

    @Test
    void retryingTransferHappyPathCompletesWithSuccessResponseCode() {
        givenRetryingSubject();
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(CryptoTransferRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(INVALID_SIGNATURE).willReturn(SUCCESS);
        given(systemContractOperations.activeSignatureTestWith(verificationStrategy))
                .willReturn(signatureTest);
        given(approvalSwitchHelper.switchToApprovalsAsNeededIn(
                        CryptoTransferTransactionBody.DEFAULT, signatureTest, nativeOperations))
                .willReturn(CryptoTransferTransactionBody.DEFAULT);

        givenRetryingSubject();

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(asBytesResult(INT64_ENCODER.encodeElements((long) SUCCESS.protoOrdinal())), result.getOutput());
    }

    @Test
    void unsupportedV2transferCompletesWithNotSupportedResponseCode() {
        givenV2SubjectWithV2Disabled();

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(INT64_ENCODER.encodeElements((long) NOT_SUPPORTED.protoOrdinal())), result.getOutput());
    }

    @Test
    void supportedV2transferCompletesWithNominalResponseCode() {
        givenV2SubjectWithV2Enabled();
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(CryptoTransferRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SPENDER_DOES_NOT_HAVE_ALLOWANCE);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(INT64_ENCODER.encodeElements((long) SPENDER_DOES_NOT_HAVE_ALLOWANCE.protoOrdinal())),
                result.getOutput());
    }

    private void givenRetryingSubject() {
        subject = new ClassicTransfersCall(
                mockEnhancement(),
                ClassicTransfersCall.CRYPTO_TRANSFER.selector(),
                A_NEW_ACCOUNT_ID,
                TransactionBody.newBuilder()
                        .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                        .build(),
                DEFAULT_CONFIG,
                approvalSwitchHelper,
                verificationStrategy);
    }

    private void givenV2SubjectWithV2Enabled() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("contracts.precompile.atomicCryptoTransfer.enabled", "true")
                .getOrCreateConfig();
        subject = new ClassicTransfersCall(
                mockEnhancement(),
                ClassicTransfersCall.CRYPTO_TRANSFER_V2.selector(),
                A_NEW_ACCOUNT_ID,
                TransactionBody.DEFAULT,
                config,
                null,
                verificationStrategy);
    }

    private void givenV2SubjectWithV2Disabled() {
        subject = new ClassicTransfersCall(
                mockEnhancement(),
                ClassicTransfersCall.CRYPTO_TRANSFER_V2.selector(),
                A_NEW_ACCOUNT_ID,
                TransactionBody.DEFAULT,
                DEFAULT_CONFIG,
                null,
                verificationStrategy);
    }
}
