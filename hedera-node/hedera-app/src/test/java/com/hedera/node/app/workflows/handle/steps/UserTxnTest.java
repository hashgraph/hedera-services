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
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.blocks.impl.PairedStreamBuilder;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.BlockRecordInfo;
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
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
    private BlockRecordManager blockRecordManager;

    @Mock
    private BlockStreamManager blockStreamManager;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableKVState<AccountID, Account> accountState;

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

        final var factory = createUserTxnFactory();
        final var subject = factory.createUserTxn(state, creatorInfo, PLATFORM_TXN, CONSENSUS_NOW, GENESIS_TRANSACTION);

        assertSame(GENESIS_TRANSACTION, subject.type());
        assertSame(CONSENSUS_CREATE_TOPIC, subject.functionality());
        assertSame(CONSENSUS_NOW, subject.consensusNow());
        assertSame(state, subject.state());
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
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(BLOCKS_CONFIG, 1));
        given(txnInfo.payerID()).willReturn(PAYER_ID);
        given(txnInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .transactionID(TransactionID.DEFAULT)
                        .build());
        given(txnInfo.transaction()).willReturn(Transaction.DEFAULT);
        given(txnInfo.signatureMap()).willReturn(SignatureMap.DEFAULT);
        given(preHandleResult.payerKey()).willReturn(AN_ED25519_KEY);
        given(preHandleResult.getVerificationResults()).willReturn(emptyMap());
        given(feeManager.congestionMultiplierFor(any(), eq(CONSENSUS_CREATE_TOPIC), any(ReadableStoreFactory.class)))
                .willReturn(CONGESTION_MULTIPLIER);
        given(serviceScopeLookup.getServiceName(any())).willReturn(ConsensusServiceImpl.NAME);
        given(state.getWritableStates(any())).willReturn(writableStates);
        given(writableStates.<AccountID, Account>get(ACCOUNTS_KEY)).willReturn(accountState);
        given(accountState.getStateKey()).willReturn(ACCOUNTS_KEY);
        given(dispatcher.dispatchComputeFees(any())).willReturn(Fees.FREE);

        final var factory = createUserTxnFactory();
        final var subject = factory.createUserTxn(state, creatorInfo, PLATFORM_TXN, CONSENSUS_NOW, GENESIS_TRANSACTION);

        final var dispatch = factory.createDispatch(subject, ExchangeRateSet.DEFAULT);

        assertSame(PAYER_ID, dispatch.payerId());
        final var result = ((BlockStreamBuilder) subject.baseBuilder())
                .build().blockItems().stream()
                        .filter(BlockItem::hasTransactionResult)
                        .findFirst()
                        .map(BlockItem::transactionResultOrThrow)
                        .orElseThrow();
        assertEquals(CONGESTION_MULTIPLIER, result.congestionPricingMultiplier());
    }

    @Test
    void constructsDispatchAsExpectedWithCongestionMultiplierEqualToOne() {
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(BLOCKS_CONFIG, 1));
        given(txnInfo.payerID()).willReturn(PAYER_ID);
        given(txnInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .transactionID(TransactionID.DEFAULT)
                        .build());
        given(txnInfo.transaction()).willReturn(Transaction.DEFAULT);
        given(txnInfo.signatureMap()).willReturn(SignatureMap.DEFAULT);
        given(preHandleResult.getVerificationResults()).willReturn(emptyMap());
        given(feeManager.congestionMultiplierFor(any(), eq(CONSENSUS_CREATE_TOPIC), any(ReadableStoreFactory.class)))
                .willReturn(1L);
        given(serviceScopeLookup.getServiceName(any())).willReturn(ConsensusServiceImpl.NAME);
        given(state.getWritableStates(any())).willReturn(writableStates);
        given(writableStates.<AccountID, Account>get(ACCOUNTS_KEY)).willReturn(accountState);
        given(accountState.getStateKey()).willReturn(ACCOUNTS_KEY);
        given(dispatcher.dispatchComputeFees(any())).willReturn(Fees.FREE);

        final var factory = createUserTxnFactory();
        final var subject = factory.createUserTxn(state, creatorInfo, PLATFORM_TXN, CONSENSUS_NOW, GENESIS_TRANSACTION);

        final var dispatch = factory.createDispatch(subject, ExchangeRateSet.DEFAULT);

        assertSame(PAYER_ID, dispatch.payerId());
        final var result = ((BlockStreamBuilder) subject.baseBuilder())
                .build().blockItems().stream()
                        .filter(BlockItem::hasTransactionResult)
                        .findFirst()
                        .map(BlockItem::transactionResultOrThrow)
                        .orElseThrow();
        assertEquals(0L, result.congestionPricingMultiplier());
    }

    private UserTxnFactory createUserTxnFactory() {
        return new UserTxnFactory(
                configProvider,
                storeMetricsService,
                kvStateChangeListener,
                boundaryStateChangeListener,
                preHandleWorkflow,
                authorizer,
                networkInfo,
                feeManager,
                dispatchProcessor,
                serviceScopeLookup,
                exchangeRateManager,
                dispatcher,
                networkUtilizationManager,
                blockRecordManager,
                blockStreamManager,
                childDispatchFactory);
    }
}
