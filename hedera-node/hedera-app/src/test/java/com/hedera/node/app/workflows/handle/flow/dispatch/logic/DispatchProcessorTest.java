/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatch.logic;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.spi.authorization.SystemPrivilege.UNNECESSARY;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.creatorErrorReport;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.errorFreeReport;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.payerErrorReport;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.FEES_ONLY;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.USER_TRANSACTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.PlatformStateUpdateFacility;
import com.hedera.node.app.workflows.handle.SystemFileUpdateFacility;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.PlatformState;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchProcessorTest {
    private static final Fees FEES = new Fees(1L, 2L, 3L);
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1_234).build();
    private static final Account PAYER =
            Account.newBuilder().accountId(PAYER_ACCOUNT_ID).build();
    private static final AccountID CREATOR_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(3).build();
    private static final NodeId CREATOR_NODE_ID = new NodeId(0L);
    private static final SignatureVerification PASSED_VERIFICATION =
            new SignatureVerificationImpl(Key.DEFAULT, Bytes.EMPTY, true);
    private static final SignatureVerification FAILED_VERIFICATION =
            new SignatureVerificationImpl(Key.DEFAULT, Bytes.EMPTY, false);
    private static final TransactionBody TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .build();
    private static final TransactionInfo TXN_INFO =
            new TransactionInfo(Transaction.DEFAULT, TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CRYPTO_TRANSFER);
    private static final TransactionInfo CONTRACT_TXN_INFO =
            new TransactionInfo(Transaction.DEFAULT, TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CONTRACT_CALL);

    @Mock
    private Authorizer authorizer;

    @Mock
    private HandleContext context;

    @Mock
    private ErrorReporter errorReporter;

    @Mock
    private RecordFinalizer recordFinalizer;

    @Mock
    private SystemFileUpdateFacility systemFileUpdateFacility;

    @Mock
    private PlatformStateUpdateFacility platformStateUpdateFacility;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    @Mock
    private Dispatch dispatch;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock
    private PlatformState platformState;

    @Mock
    private RecordListBuilder recordListBuilder;

    @Mock
    private FeeAccumulator feeAccumulator;

    private DispatchProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new DispatchProcessor(
                authorizer,
                errorReporter,
                recordFinalizer,
                systemFileUpdateFacility,
                platformStateUpdateFacility,
                exchangeRateManager,
                dispatcher,
                networkUtilizationManager);
        given(dispatch.stack()).willReturn(stack);
        given(dispatch.recordBuilder()).willReturn(recordBuilder);
    }

    @Test
    void creatorErrorAsExpected() {
        given(dispatch.fees()).willReturn(FEES);
        given(dispatch.feeAccumulator()).willReturn(feeAccumulator);
        given(errorReporter.errorReportFor(dispatch))
                .willReturn(creatorErrorReport(CREATOR_ACCOUNT_ID, INVALID_PAYER_SIGNATURE));

        assertThat(subject.processDispatch(dispatch)).isEqualTo(FEES_ONLY);

        verify(feeAccumulator).chargeNetworkFee(CREATOR_ACCOUNT_ID, FEES.networkFee());
        verify(recordBuilder).status(INVALID_PAYER_SIGNATURE);
        assertFinished();
    }

    @Test
    void waivedFeesDoesNotCharge() {
        given(errorReporter.errorReportFor(dispatch)).willReturn(errorFreeReport(CREATOR_ACCOUNT_ID, PAYER));
        given(dispatch.syntheticPayer()).willReturn(PAYER_ACCOUNT_ID);
        given(dispatch.txnInfo()).willReturn(TXN_INFO);
        given(authorizer.hasWaivedFees(PAYER_ACCOUNT_ID, TXN_INFO.functionality(), TXN_BODY))
                .willReturn(true);
        given(recordBuilder.exchangeRate(any())).willReturn(recordBuilder);
        given(dispatch.handleContext()).willReturn(context);
        givenAuthorization();

        assertThat(subject.processDispatch(dispatch)).isEqualTo(USER_TRANSACTION);

        verifyNoInteractions(feeAccumulator);
        verify(dispatcher).dispatchHandle(context);
        verify(recordBuilder).status(SUCCESS);
        assertFinished();
    }

    @Test
    void thrownHandleExceptionRollsBackIfRequested() {
        given(dispatch.fees()).willReturn(FEES);
        given(dispatch.feeAccumulator()).willReturn(feeAccumulator);
        given(errorReporter.errorReportFor(dispatch)).willReturn(errorFreeReport(CREATOR_ACCOUNT_ID, PAYER));
        given(dispatch.syntheticPayer()).willReturn(PAYER_ACCOUNT_ID);
        given(dispatch.txnInfo()).willReturn(TXN_INFO);
        given(dispatch.handleContext()).willReturn(context);
        given(dispatch.recordListBuilder()).willReturn(recordListBuilder);
        givenAuthorization();
        doThrow(new HandleException(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT))
                .when(dispatcher)
                .dispatchHandle(context);

        assertThat(subject.processDispatch(dispatch)).isEqualTo(USER_TRANSACTION);

        verify(dispatcher).dispatchHandle(context);
        verify(recordBuilder).status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        verify(recordListBuilder).revertChildrenOf(recordBuilder);
        verify(feeAccumulator, times(2)).chargeFees(PAYER_ACCOUNT_ID, CREATOR_ACCOUNT_ID, FEES);
        assertFinished();
    }

    @Test
    void consGasExhaustedWaivesServiceFee() {
        given(dispatch.fees()).willReturn(FEES);
        given(dispatch.feeAccumulator()).willReturn(feeAccumulator);
        given(errorReporter.errorReportFor(dispatch)).willReturn(errorFreeReport(CREATOR_ACCOUNT_ID, PAYER));
        given(dispatch.syntheticPayer()).willReturn(PAYER_ACCOUNT_ID);
        given(dispatch.txnInfo()).willReturn(CONTRACT_TXN_INFO);
        given(dispatch.recordListBuilder()).willReturn(recordListBuilder);
        givenAuthorization(CONTRACT_TXN_INFO);
        given(networkUtilizationManager.wasLastTxnGasThrottled()).willReturn(true);

        assertThat(subject.processDispatch(dispatch)).isEqualTo(FEES_ONLY);

        verify(dispatcher, never()).dispatchHandle(context);
        verify(recordBuilder).status(CONSENSUS_GAS_EXHAUSTED);
        verify(recordListBuilder).revertChildrenOf(recordBuilder);
        verify(feeAccumulator).chargeFees(PAYER_ACCOUNT_ID, CREATOR_ACCOUNT_ID, FEES);
        verify(feeAccumulator).chargeFees(PAYER_ACCOUNT_ID, CREATOR_ACCOUNT_ID, FEES.withoutServiceComponent());
        assertFinished();
    }

    @Test
    void failInvalidWaivesServiceFee() {
        given(dispatch.fees()).willReturn(FEES);
        given(dispatch.feeAccumulator()).willReturn(feeAccumulator);
        given(errorReporter.errorReportFor(dispatch)).willReturn(errorFreeReport(CREATOR_ACCOUNT_ID, PAYER));
        given(dispatch.syntheticPayer()).willReturn(PAYER_ACCOUNT_ID);
        given(dispatch.txnInfo()).willReturn(TXN_INFO);
        given(dispatch.recordListBuilder()).willReturn(recordListBuilder);
        given(dispatch.handleContext()).willReturn(context);
        givenAuthorization(TXN_INFO);
        doThrow(new IllegalStateException()).when(dispatcher).dispatchHandle(context);

        assertThat(subject.processDispatch(dispatch)).isEqualTo(FEES_ONLY);

        verify(recordBuilder).status(FAIL_INVALID);
        verify(recordListBuilder).revertChildrenOf(recordBuilder);
        verify(feeAccumulator).chargeFees(PAYER_ACCOUNT_ID, CREATOR_ACCOUNT_ID, FEES);
        verify(feeAccumulator).chargeFees(PAYER_ACCOUNT_ID, CREATOR_ACCOUNT_ID, FEES.withoutServiceComponent());
        assertFinished();
    }

    @Test
    void happyPathContractCallAsExpected() {
        given(dispatch.fees()).willReturn(FEES);
        given(dispatch.feeAccumulator()).willReturn(feeAccumulator);
        given(errorReporter.errorReportFor(dispatch)).willReturn(errorFreeReport(CREATOR_ACCOUNT_ID, PAYER));
        given(dispatch.syntheticPayer()).willReturn(PAYER_ACCOUNT_ID);
        given(dispatch.txnInfo()).willReturn(CONTRACT_TXN_INFO);
        given(dispatch.handleContext()).willReturn(context);
        givenAuthorization(CONTRACT_TXN_INFO);
        givenSystemEffectSuccess(CONTRACT_TXN_INFO);

        assertThat(subject.processDispatch(dispatch)).isEqualTo(USER_TRANSACTION);

        verify(platformStateUpdateFacility).handleTxBody(stack, platformState, CONTRACT_TXN_INFO.txBody());
        verify(recordBuilder, times(2)).status(SUCCESS);
        verify(feeAccumulator).chargeFees(PAYER_ACCOUNT_ID, CREATOR_ACCOUNT_ID, FEES);
        assertFinished();
    }

    @Test
    void unableToAffordServiceFeesChargesAccordingly() {
        given(dispatch.fees()).willReturn(FEES);
        given(dispatch.feeAccumulator()).willReturn(feeAccumulator);
        given(errorReporter.errorReportFor(dispatch))
                .willReturn(payerErrorReport(
                        CREATOR_ACCOUNT_ID,
                        PAYER,
                        INSUFFICIENT_ACCOUNT_BALANCE,
                        ServiceFeeStatus.UNABLE_TO_PAY_SERVICE_FEE,
                        DuplicateStatus.NO_DUPLICATE));
        given(dispatch.syntheticPayer()).willReturn(PAYER_ACCOUNT_ID);
        given(dispatch.txnInfo()).willReturn(TXN_INFO);

        assertThat(subject.processDispatch(dispatch)).isEqualTo(FEES_ONLY);

        verify(feeAccumulator).chargeFees(PAYER_ACCOUNT_ID, CREATOR_ACCOUNT_ID, FEES.withoutServiceComponent());
        verify(recordBuilder).status(INSUFFICIENT_ACCOUNT_BALANCE);
        verifyNoInteractions(dispatcher);
        assertFinished();
    }

    private void givenSystemEffectSuccess(@NonNull final TransactionInfo txnInfo) {
        given(systemFileUpdateFacility.handleTxBody(stack, txnInfo.txBody())).willReturn(SUCCESS);
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);
        given(recordBuilder.exchangeRate(ExchangeRateSet.DEFAULT)).willReturn(recordBuilder);
        given(dispatch.platformState()).willReturn(platformState);
    }

    private void givenAuthorization() {
        givenAuthorization(TXN_INFO);
    }

    private void givenAuthorization(@NonNull final TransactionInfo txnInfo) {
        given(authorizer.isAuthorized(PAYER_ACCOUNT_ID, txnInfo.functionality()))
                .willReturn(true);
        given(authorizer.hasPrivilegedAuthorization(PAYER_ACCOUNT_ID, txnInfo.functionality(), TXN_BODY))
                .willReturn(UNNECESSARY);
    }

    private void assertFinished() {
        verify(recordFinalizer).finalizeRecord(dispatch);
        verify(stack).commitFullStack();
    }
}
