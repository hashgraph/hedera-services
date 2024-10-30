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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_CREATE_TOPIC;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.ACCOUNTS_KEY;
import static com.hedera.node.app.workflows.handle.TransactionType.GENESIS_TRANSACTION;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.blocks.impl.PairedStreamBuilder;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.ServiceMetricsFactory;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.dispatch.ChildDispatchFactory;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.state.State;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTxnTest {
    private static final long CONGESTION_MULTIPLIER = 2L;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final ConsensusTransaction PLATFORM_TXN = new TransactionWrapper(EventTransaction.DEFAULT);
    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(1234).build();
    private static final Key AN_ED25519_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();
    private static final Configuration BLOCKS_CONFIG = HederaTestConfigBuilder.create()
            .withValue("blockStream.streamMode", "BLOCKS")
            .getOrCreateConfig();

    @Mock
    private State state;

    @Mock
    private ConsensusEvent event;

    @Mock
    private NodeInfo creatorInfo;

    @Mock
    private PreHandleResult preHandleResult;

    @Mock
    private PreHandleWorkflow preHandleWorkflow;

    @Mock
    private TransactionInfo txnInfo;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private KVStateChangeListener kvStateChangeListener;

    @Mock
    private BoundaryStateChangeListener boundaryStateChangeListener;

    @Mock
    private Authorizer authorizer;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private FeeManager feeManager;

    @Mock
    private DispatchProcessor dispatchProcessor;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    @Mock
    private ServiceScopeLookup serviceScopeLookup;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private ChildDispatchFactory childDispatchFactory;

    @Mock
    private TransactionDispatcher dispatcher;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableKVState<AccountID, Account> accountState;

    private StreamBuilder baseBuilder;

    @BeforeEach
    void setUp() {
        given(preHandleWorkflow.getCurrentPreHandleResult(
                        eq(creatorInfo), eq(PLATFORM_TXN), any(ReadableStoreFactory.class)))
                .willReturn(preHandleResult);
        given(preHandleResult.txInfo()).willReturn(txnInfo);
        given(txnInfo.functionality()).willReturn(CONSENSUS_CREATE_TOPIC);
    }

    @Test
    void usesPairedStreamBuilderWithDefaultConfig() {
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 1));

        final var subject = UserTxn.from(
                state,
                event,
                creatorInfo,
                PLATFORM_TXN,
                CONSENSUS_NOW,
                GENESIS_TRANSACTION,
                configProvider,
                storeMetricsService,
                kvStateChangeListener,
                boundaryStateChangeListener,
                preHandleWorkflow,
                mock(ServiceMetricsFactory.class));

        assertSame(GENESIS_TRANSACTION, subject.type());
        assertSame(CONSENSUS_CREATE_TOPIC, subject.functionality());
        assertSame(CONSENSUS_NOW, subject.consensusNow());
        assertSame(state, subject.state());
        assertSame(event, subject.event());
        assertSame(PLATFORM_TXN, subject.platformTxn());
        assertSame(txnInfo, subject.txnInfo());
        assertSame(preHandleResult, subject.preHandleResult());
        assertSame(creatorInfo, subject.creatorInfo());
        assertNotNull(subject.tokenContextImpl());
        assertNotNull(subject.stack());
        assertNotNull(subject.readableStoreFactory());
        assertNotNull(subject.config());

        assertThat(subject.baseBuilder()).isInstanceOf(PairedStreamBuilder.class);
    }

    @Test
    void constructsDispatchAsExpectedWithCongestionMultiplierGreaterThanOne() {
        baseBuilder = Mockito.mock(StreamBuilder.class, withSettings().extraInterfaces(FeeStreamBuilder.class));
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(BLOCKS_CONFIG, 1));
        given(txnInfo.payerID()).willReturn(PAYER_ID);
        given(txnInfo.txBody()).willReturn(TransactionBody.DEFAULT);
        given(txnInfo.signatureMap()).willReturn(SignatureMap.DEFAULT);
        given(preHandleResult.payerKey()).willReturn(AN_ED25519_KEY);
        given(preHandleResult.getVerificationResults()).willReturn(emptyMap());
        given(feeManager.congestionMultiplierFor(
                        eq(TransactionBody.DEFAULT), eq(CONSENSUS_CREATE_TOPIC), any(ReadableStoreFactory.class)))
                .willReturn(CONGESTION_MULTIPLIER);
        given(serviceScopeLookup.getServiceName(TransactionBody.DEFAULT)).willReturn(ConsensusServiceImpl.NAME);
        given(state.getWritableStates(any())).willReturn(writableStates);
        given(writableStates.<AccountID, Account>get(ACCOUNTS_KEY)).willReturn(accountState);
        given(accountState.getStateKey()).willReturn(ACCOUNTS_KEY);
        given(dispatcher.dispatchComputeFees(any())).willReturn(Fees.FREE);

        final var subject = UserTxn.from(
                state,
                event,
                creatorInfo,
                PLATFORM_TXN,
                CONSENSUS_NOW,
                GENESIS_TRANSACTION,
                configProvider,
                storeMetricsService,
                kvStateChangeListener,
                boundaryStateChangeListener,
                preHandleWorkflow,
                mock(ServiceMetricsFactory.class));

        final var dispatch = subject.newDispatch(
                authorizer,
                networkInfo,
                feeManager,
                dispatchProcessor,
                blockRecordInfo,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                childDispatchFactory,
                dispatcher,
                networkUtilizationManager,
                baseBuilder,
                BLOCKS);

        assertSame(PAYER_ID, dispatch.payerId());
        verify(baseBuilder).congestionMultiplier(CONGESTION_MULTIPLIER);
    }

    @Test
    void constructsDispatchAsExpectedWithCongestionMultiplierEqualToOne() {
        baseBuilder = Mockito.mock(StreamBuilder.class, withSettings().extraInterfaces(FeeStreamBuilder.class));
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(BLOCKS_CONFIG, 1));
        given(txnInfo.payerID()).willReturn(PAYER_ID);
        given(txnInfo.txBody()).willReturn(TransactionBody.DEFAULT);
        given(txnInfo.signatureMap()).willReturn(SignatureMap.DEFAULT);
        given(preHandleResult.getVerificationResults()).willReturn(emptyMap());
        given(feeManager.congestionMultiplierFor(
                        eq(TransactionBody.DEFAULT), eq(CONSENSUS_CREATE_TOPIC), any(ReadableStoreFactory.class)))
                .willReturn(1L);
        given(serviceScopeLookup.getServiceName(TransactionBody.DEFAULT)).willReturn(ConsensusServiceImpl.NAME);
        given(state.getWritableStates(any())).willReturn(writableStates);
        given(writableStates.<AccountID, Account>get(ACCOUNTS_KEY)).willReturn(accountState);
        given(accountState.getStateKey()).willReturn(ACCOUNTS_KEY);
        given(dispatcher.dispatchComputeFees(any())).willReturn(Fees.FREE);

        final var subject = UserTxn.from(
                state,
                event,
                creatorInfo,
                PLATFORM_TXN,
                CONSENSUS_NOW,
                GENESIS_TRANSACTION,
                configProvider,
                storeMetricsService,
                kvStateChangeListener,
                boundaryStateChangeListener,
                preHandleWorkflow,
                mock(ServiceMetricsFactory.class));

        final var dispatch = subject.newDispatch(
                authorizer,
                networkInfo,
                feeManager,
                dispatchProcessor,
                blockRecordInfo,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                childDispatchFactory,
                dispatcher,
                networkUtilizationManager,
                baseBuilder,
                BLOCKS);

        assertSame(PAYER_ID, dispatch.payerId());
        verify(baseBuilder, never()).congestionMultiplier(1);
    }
}
