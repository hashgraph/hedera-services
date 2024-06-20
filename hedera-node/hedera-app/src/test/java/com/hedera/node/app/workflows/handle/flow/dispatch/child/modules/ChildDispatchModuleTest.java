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

package com.hedera.node.app.workflows.handle.flow.dispatch.child.modules;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_UPDATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fees.ResourcePriceCalculator;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordBuilders;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.TriggeredFinalizeContext;
import com.hedera.node.app.workflows.handle.flow.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.logic.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.logic.DispatchProcessor;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import javax.inject.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildDispatchModuleTest {
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1_234).build();
    private static final TransactionBody TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .build();
    private static final TransactionInfo CRYPTO_TRANSFER_TXN_INFO =
            new TransactionInfo(Transaction.DEFAULT, TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CRYPTO_TRANSFER);
    private static final TransactionInfo CRYPTO_UPDATE_TXN_INFO =
            new TransactionInfo(Transaction.DEFAULT, TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CRYPTO_UPDATE);
    private static final TransactionInfo CRYPTO_CREATE_TXN_INFO =
            new TransactionInfo(Transaction.DEFAULT, TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CRYPTO_CREATE);
    private static final Fees FEES = new Fees(1L, 2L, 3L);
    private static final Instant CHILD_CONS_NOW = Instant.ofEpochSecond(1_234_567L);
    private static final PreHandleResult PRE_HANDLE_RESULT = new PreHandleResult(
            AccountID.DEFAULT,
            Key.DEFAULT,
            SO_FAR_SO_GOOD,
            SUCCESS,
            CRYPTO_TRANSFER_TXN_INFO,
            Set.of(Key.DEFAULT),
            Collections.emptySet(),
            Set.of(Account.DEFAULT),
            Collections.emptyMap(),
            null,
            1L);

    @Mock
    private FeeContext feeContext;

    @Mock
    private TransactionInfo transactionInfo;

    @Mock
    private ServiceApiFactory serviceApiFactory;

    @Mock
    private TokenServiceApi tokenServiceApi;

    @Mock
    private WritableStates writableStates;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private Configuration configuration;

    @Mock
    private Authorizer authorizer;

    @Mock
    private BlockRecordManager blockRecordManager;

    @Mock
    private ResourcePriceCalculator resourcePriceCalculator;

    @Mock
    private FeeManager feeManager;

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private AccountID syntheticPayer;

    @Mock
    private KeyVerifier verifier;

    @Mock
    private Key payerkey;

    @Mock
    private FeeAccumulator feeAccumulator;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private WritableEntityIdStore entityIdStore;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private RecordCache recordCache;

    @Mock
    private WritableStoreFactory writableStoreFactory;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock
    private Provider<ChildDispatchComponent.Factory> childDispatchFactory;

    @Mock
    private ChildDispatchFactory childDispatchLogic;

    @Mock
    private ChildDispatchComponent dispatch;

    @Mock
    private DispatchProcessor dispatchProcessor;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    @Mock
    private ServiceScopeLookup serviceScopeLookup;

    @Mock
    private RecordBuilders recordBuilders;

    @Test
    void childHandleContextConstructedWithRecordBuilderConsTime() {
        given(recordBuilders.getOrCreate(SingleTransactionRecordBuilderImpl.class)).willReturn(recordBuilder);
        given(recordBuilder.consensusNow()).willReturn(CHILD_CONS_NOW);
        final var childContext = ChildDispatchModule.provideDispatchHandleContext(
                transactionInfo,
                configuration,
                authorizer,
                blockRecordManager,
                resourcePriceCalculator,
                feeManager,
                readableStoreFactory,
                syntheticPayer,
                verifier,
                payerkey,
                exchangeRateManager,
                stack,
                entityIdStore,
                dispatcher,
                recordCache,
                writableStoreFactory,
                serviceApiFactory,
                networkInfo,
                recordBuilders,
                childDispatchFactory,
                childDispatchLogic,
                dispatch,
                dispatchProcessor,
                networkUtilizationManager);
        assertThat(childContext).isInstanceOf(DispatchHandleContext.class);
        assertThat(childContext.consensusNow()).isSameAs(CHILD_CONS_NOW);
    }

    @Test
    void providesRequiredKeys() {
        assertThat(ChildDispatchModule.provideRequiredKeys(PRE_HANDLE_RESULT))
                .isSameAs(PRE_HANDLE_RESULT.getRequiredKeys());
    }

    @Test
    void providesRequiredHollowAccounts() {
        assertThat(ChildDispatchModule.provideHollowAccounts(PRE_HANDLE_RESULT))
                .isSameAs(PRE_HANDLE_RESULT.getHollowAccounts());
    }

    @Test
    void providesTriggeredFinalizeContext() {
        given(recordBuilder.consensusNow()).willReturn(CHILD_CONS_NOW);
        final var finalizeContext = ChildDispatchModule.provideFinalizeContext(
                readableStoreFactory, recordBuilder, stack, configuration, storeMetricsService);
        assertThat(finalizeContext).isInstanceOf(TriggeredFinalizeContext.class);
        assertThat(finalizeContext.consensusTime()).isSameAs(CHILD_CONS_NOW);
    }

    @Test
    void providesServiceScopedWritableStoreFactory() {
        given(serviceScopeLookup.getServiceName(TXN_BODY)).willReturn(UtilService.NAME);
        final var writableStoreFactory = ChildDispatchModule.provideWritableStoreFactory(
                stack, CRYPTO_TRANSFER_TXN_INFO, configuration, serviceScopeLookup, storeMetricsService);
        assertThat(writableStoreFactory.getServiceName()).isEqualTo(UtilService.NAME);
    }

    @Test
    void providesWritableEntityIdStore() {
        given(stack.getWritableStates(EntityIdService.NAME)).willReturn(writableStates);
        final var writableEntityIdStore =
                ChildDispatchModule.provideWritableEntityIdStore(stack, configuration, storeMetricsService);
        assertThat(writableEntityIdStore).isNotNull();
    }

    @Test
    void usesDefaultPayerKeyForFeeCalculations() {
        assertThat(ChildDispatchModule.providePayerKey()).isSameAs(Key.DEFAULT);
    }

    @Test
    void providesServiceApiFactory() {
        assertThat(ChildDispatchModule.provideServiceApiFactory(stack, configuration, storeMetricsService))
                .isNotNull();
    }

    @Test
    void providesFeeAccumulatorImpl() {
        given(serviceApiFactory.getApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        assertThat(ChildDispatchModule.provideFeeAccumulator(recordBuilder, serviceApiFactory))
                .isInstanceOf(FeeAccumulator.class);
    }

    @Test
    void providesReadableStoreFactory() {
        assertThat(ChildDispatchModule.provideReadableStoreFactory(stack)).isNotNull();
    }

    @Test
    void scheduledFeesIncludeOnlyServiceComponent() {
        given(dispatcher.dispatchComputeFees(feeContext)).willReturn(FEES);
        assertThat(ChildDispatchModule.provideFees(
                        feeContext,
                        HandleContext.TransactionCategory.SCHEDULED,
                        dispatcher,
                        CRYPTO_TRANSFER,
                        CRYPTO_TRANSFER_TXN_INFO))
                .isEqualTo(FEES.onlyServiceComponent());
    }

    @Test
    void precedingFeesAreZeroForContract() {
        assertThat(ChildDispatchModule.provideFees(
                        feeContext,
                        HandleContext.TransactionCategory.PRECEDING,
                        dispatcher,
                        CONTRACT_CALL,
                        CRYPTO_TRANSFER_TXN_INFO))
                .isSameAs(Fees.FREE);
    }

    @Test
    void precedingFeesAreZeroForCryptoUpdate() {
        assertThat(ChildDispatchModule.provideFees(
                        feeContext,
                        HandleContext.TransactionCategory.PRECEDING,
                        dispatcher,
                        CRYPTO_TRANSFER,
                        CRYPTO_UPDATE_TXN_INFO))
                .isSameAs(Fees.FREE);
    }

    @Test
    void precedingFeesAreNonZeroForAutoCreation() {
        given(dispatcher.dispatchComputeFees(feeContext)).willReturn(FEES);
        assertThat(ChildDispatchModule.provideFees(
                        feeContext,
                        HandleContext.TransactionCategory.PRECEDING,
                        dispatcher,
                        CRYPTO_TRANSFER,
                        CRYPTO_CREATE_TXN_INFO))
                .isSameAs(FEES);
    }

    @Test
    void childFeesAreZero() {
        assertThat(ChildDispatchModule.provideFees(
                        feeContext,
                        HandleContext.TransactionCategory.CHILD,
                        dispatcher,
                        CONTRACT_CALL,
                        CRYPTO_CREATE_TXN_INFO))
                .isSameAs(Fees.FREE);
    }

    @Test
    void cannotDispatchUserTransaction() {
        assertThatThrownBy(() -> ChildDispatchModule.provideFees(
                        feeContext,
                        HandleContext.TransactionCategory.USER,
                        dispatcher,
                        CONTRACT_CALL,
                        CRYPTO_CREATE_TXN_INFO))
                .isInstanceOf(IllegalStateException.class);
    }
}
