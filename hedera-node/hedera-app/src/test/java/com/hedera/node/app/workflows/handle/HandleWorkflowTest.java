/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static java.lang.Boolean.FALSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fixtures.AppTestBase;
import com.hedera.node.app.fixtures.workflows.handle.record.SingleTransactionRecordConditions;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionScenarioBuilder;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.app.workflows.prehandle.FakeSignatureVerificationFuture;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleResult.Status;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleWorkflowTest extends AppTestBase {

    private static final Instant CONSENSUS_NOW = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant TX_CONSENSUS_NOW = CONSENSUS_NOW.minusNanos(1000 - 3);

    private static final long CONFIG_VERSION = 11L;

    private static final PreHandleResult OK_RESULT = createPreHandleResult(Status.SO_FAR_SO_GOOD, ResponseCodeEnum.OK);

    private static final PreHandleResult PRE_HANDLE_FAILURE_RESULT =
            createPreHandleResult(Status.PRE_HANDLE_FAILURE, ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID);

    private static final PreHandleResult DUE_DILIGENCE_RESULT = PreHandleResult.nodeDueDiligenceFailure(
            NODE_1.nodeAccountID(),
            ResponseCodeEnum.INVALID_TRANSACTION,
            new TransactionScenarioBuilder().txInfo(),
            1L);

    private static final ExchangeRateSet EXCHANGE_RATE_SET =
            ExchangeRateSet.newBuilder().build();

    private static final Fees DEFAULT_FEES = new Fees(1L, 20L, 300L);

    private static PreHandleResult createPreHandleResult(@NonNull Status status, @NonNull ResponseCodeEnum code) {
        final var key = ALICE.account().keyOrThrow();
        return new PreHandleResult(
                ALICE.accountID(),
                key,
                status,
                code,
                new TransactionScenarioBuilder().txInfo(),
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of(key, FakeSignatureVerificationFuture.goodFuture(key)),
                null,
                CONFIG_VERSION);
    }

    @Mock(strictness = LENIENT)
    private NetworkInfo networkInfo;

    @Mock(strictness = LENIENT)
    private ThrottleServiceManager throttleServiceManager;

    @Mock(strictness = LENIENT)
    private PreHandleWorkflow preHandleWorkflow;

    @Mock(strictness = LENIENT)
    private TransactionDispatcher dispatcher;

    @Mock
    private BlockRecordManager blockRecordManager;

    @Mock
    private TransactionChecker checker;

    @Mock(strictness = LENIENT)
    private ServiceScopeLookup serviceLookup;

    @Mock(strictness = LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = LENIENT)
    private Round round;

    @Mock(strictness = LENIENT)
    private ConsensusEvent event;

    @Mock(strictness = LENIENT)
    private SwirldTransaction platformTxn;

    @Mock(strictness = LENIENT)
    private HederaRecordCache recordCache;

    @Mock
    private GenesisRecordsConsensusHook genesisRecordsTimeHook;

    @Mock
    private ScheduleExpirationHook scheduleExpirationHook;

    @Mock
    private StakingPeriodTimeHook stakingPeriodTimeHook;

    @Mock
    private FeeManager feeManager;

    @Mock(strictness = LENIENT)
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private ParentRecordFinalizer finalizer;

    @Mock
    private ChildRecordFinalizer childRecordFinalizer;

    @Mock(strictness = LENIENT)
    private SystemFileUpdateFacility systemFileUpdateFacility;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    @Mock
    private SynchronizedThrottleAccumulator synchronizedThrottleAccumulator;

    @Mock
    private PlatformStateUpdateFacility platformStateUpdateFacility;

    @Mock(strictness = LENIENT)
    private SolvencyPreCheck solvencyPreCheck;

    @Mock(strictness = LENIENT)
    private Authorizer authorizer;

    @Mock
    private PlatformState platformState;

    @Mock
    private CacheWarmer cacheWarmer;

    @Mock
    private HandleWorkflowMetrics handleWorkflowMetrics;

    private HandleWorkflow workflow;

    @BeforeEach
    void setup() throws PreCheckException {
        setupStandardStates();

        accountsState.put(
                ALICE.accountID(),
                ALICE.account()
                        .copyBuilder()
                        .tinybarBalance(DEFAULT_FEES.totalFee())
                        .build());
        accountsState.put(
                nodeSelfAccountId,
                nodeSelfAccount
                        .copyBuilder()
                        .tinybarBalance(DEFAULT_FEES.totalFee())
                        .build());
        accountsState.commit();

        when(round.iterator()).thenReturn(List.of(event).iterator());
        when(event.consensusTransactionIterator())
                .thenReturn(List.<ConsensusTransaction>of(platformTxn).iterator());
        when(event.getCreatorId()).thenReturn(nodeSelfId);
        when(platformTxn.getConsensusTimestamp()).thenReturn(CONSENSUS_NOW);
        when(platformTxn.getMetadata()).thenReturn(OK_RESULT);
        lenient().when(blockRecordManager.consTimeOfLastHandledTxn()).thenReturn(CONSENSUS_NOW.minusSeconds(1));

        when(serviceLookup.getServiceName(any())).thenReturn(TokenService.NAME);

        final var config = new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), CONFIG_VERSION);
        when(configProvider.getConfiguration()).thenReturn(config);

        when(solvencyPreCheck.getPayerAccount(any(), eq(ALICE.accountID()))).thenReturn(ALICE.account());

        doAnswer(invocation -> {
                    final var context = invocation.getArgument(0, HandleContext.class);
                    context.writableStore(WritableAccountStore.class)
                            .putAlias(Bytes.wrap(ALICE_ALIAS), ALICE.accountID());
                    return null;
                })
                .when(dispatcher)
                .dispatchHandle(any());

        when(dispatcher.dispatchComputeFees(any())).thenReturn(DEFAULT_FEES);
        when(networkInfo.nodeInfo(nodeSelfId.id())).thenReturn(selfNodeInfo);
        when(exchangeRateManager.exchangeRates()).thenReturn(EXCHANGE_RATE_SET);
        when(recordCache.hasDuplicate(any(), eq(nodeSelfId.id())))
                .thenReturn(HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE);
        when(authorizer.isAuthorized(eq(ALICE.accountID()), any())).thenReturn(true);
        when(authorizer.hasPrivilegedAuthorization(eq(ALICE.accountID()), any(), any()))
                .thenReturn(SystemPrivilege.UNNECESSARY);
        when(systemFileUpdateFacility.handleTxBody(any(), any())).thenReturn(SUCCESS);

        workflow = new HandleWorkflow(
                networkInfo,
                preHandleWorkflow,
                dispatcher,
                blockRecordManager,
                checker,
                serviceLookup,
                configProvider,
                recordCache,
                genesisRecordsTimeHook,
                stakingPeriodTimeHook,
                feeManager,
                exchangeRateManager,
                childRecordFinalizer,
                finalizer,
                systemFileUpdateFacility,
                platformStateUpdateFacility,
                solvencyPreCheck,
                authorizer,
                networkUtilizationManager,
                synchronizedThrottleAccumulator,
                scheduleExpirationHook,
                cacheWarmer,
                handleWorkflowMetrics,
                throttleServiceManager);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testContructorWithInvalidArguments() {
        assertThatThrownBy(() -> new HandleWorkflow(
                        null,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        null,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        null,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        null,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        null,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        null,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        null,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        null,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        null,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        null,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        null,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        null,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        null,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        null,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        null,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        null,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        null,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        null,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        null,
                        scheduleExpirationHook,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        null,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        null,
                        handleWorkflowMetrics,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        preHandleWorkflow,
                        dispatcher,
                        blockRecordManager,
                        checker,
                        serviceLookup,
                        configProvider,
                        recordCache,
                        genesisRecordsTimeHook,
                        stakingPeriodTimeHook,
                        feeManager,
                        exchangeRateManager,
                        childRecordFinalizer,
                        finalizer,
                        systemFileUpdateFacility,
                        platformStateUpdateFacility,
                        solvencyPreCheck,
                        authorizer,
                        networkUtilizationManager,
                        synchronizedThrottleAccumulator,
                        scheduleExpirationHook,
                        cacheWarmer,
                        null,
                        throttleServiceManager))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("System transaction is skipped")
    void testPlatformTxnIsSkipped() {
        // given
        when(platformTxn.isSystem()).thenReturn(true);

        // when
        workflow.handleRound(state, platformState, round);

        // then
        assertThat(accountsState.isModified()).isFalse();
        assertThat(aliasesState.isModified()).isFalse();
        verify(blockRecordManager, never()).advanceConsensusClock(any(), any());
        verify(blockRecordManager, never()).startUserTransaction(any(), any());
        verify(blockRecordManager, never()).endUserTransaction(any(), any());
    }

    @Test
    @DisplayName("Successful execution of simple case")
    void testHappyPath() {
        given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                .willAnswer(invocation -> invocation.getArgument(4));

        // when
        workflow.handleRound(state, platformState, round);

        // then
        verify(cacheWarmer).warm(any(), any());
        verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
        final var alice = aliasesState.get(new ProtoBytes(Bytes.wrap(ALICE_ALIAS)));
        assertThat(alice).isEqualTo(ALICE.account().accountId());
        // TODO: Check that record was created
        verify(systemFileUpdateFacility).handleTxBody(any(), any());
        verify(platformStateUpdateFacility).handleTxBody(any(), any(), any());
        verify(handleWorkflowMetrics)
                .updateTransactionDuration(eq(HederaFunctionality.CRYPTO_TRANSFER), intThat(i -> i > 0));
    }

    @Nested
    @DisplayName("Tests for cases when preHandle needs to be run")
    final class FullPreHandleRunTest {

        @BeforeEach
        void setup() {
            when(preHandleWorkflow.preHandleTransaction(any(), any(), any(), eq(platformTxn), any()))
                    .thenReturn(OK_RESULT);
        }

        @Test
        @DisplayName("Run preHandle, if it was not executed before (platformTxn.metadata is null)")
        void testPreHandleNotExecuted() {
            // given
            when(platformTxn.getMetadata()).thenReturn(null);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            verify(preHandleWorkflow).preHandleTransaction(any(), any(), any(), eq(platformTxn), eq(null));
        }

        @Test
        @DisplayName("Run preHandle, if previous execution resulted in Status.PRE_HANDLE_FAILURE")
        void testPreHandleFailure() {
            // given
            when(platformTxn.getMetadata()).thenReturn(PRE_HANDLE_FAILURE_RESULT);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            verify(preHandleWorkflow)
                    .preHandleTransaction(any(), any(), any(), eq(platformTxn), eq(PRE_HANDLE_FAILURE_RESULT));
        }

        @Test
        @DisplayName("Run preHandle, if previous execution resulted in Status.UNKNOWN_FAILURE")
        void testUnknownFailure() {
            final var unknownFailure = PreHandleResult.unknownFailure();
            // given
            when(platformTxn.getMetadata()).thenReturn(unknownFailure);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            verify(preHandleWorkflow).preHandleTransaction(any(), any(), any(), eq(platformTxn), eq(unknownFailure));
        }

        @Test
        @DisplayName("Run preHandle, if configuration changed")
        void testConfigurationChanged() {
            // given
            final var key = ALICE.account().keyOrThrow();
            final var preHandleResult = new PreHandleResult(
                    ALICE.accountID(),
                    key,
                    Status.SO_FAR_SO_GOOD,
                    ResponseCodeEnum.OK,
                    new TransactionScenarioBuilder().txInfo(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    Map.of(key, FakeSignatureVerificationFuture.goodFuture(key)),
                    null,
                    CONFIG_VERSION - 1L);
            when(platformTxn.getMetadata()).thenReturn(preHandleResult);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(preHandleWorkflow).preHandleTransaction(any(), any(), any(), eq(platformTxn), eq(preHandleResult));
        }

        @Test
        @DisplayName("Handle transaction successfully, if running preHandle caused no issues")
        void testPreHandleSuccess() {
            // given
            when(platformTxn.getMetadata()).thenReturn(null);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var alice = aliasesState.get(new ProtoBytes(Bytes.wrap(ALICE_ALIAS)));
            assertThat(alice).isEqualTo(ALICE.account().accountId());
            // TODO: Check that record was created
        }

        @Test
        @DisplayName("Create penalty payment, if running preHandle causes a due diligence error")
        void testPreHandleCausesDueDilligenceError() {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            when(platformTxn.getMetadata()).thenReturn(null);
            when(preHandleWorkflow.preHandleTransaction(any(), any(), any(), eq(platformTxn), eq(null)))
                    .thenReturn(DUE_DILIGENCE_RESULT);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            assertThat(aliasesState.isModified()).isFalse();
            // TODO: Verify that we created a penalty payment (https://github.com/hashgraph/hedera-services/issues/6811)
        }

        @Test
        @DisplayName("Charge user, but do not change state otherwise, if running preHandle causes PRE_HANDLE_FAILURE")
        void testPreHandleCausesPreHandleFailure() {
            // given
            when(platformTxn.getMetadata()).thenReturn(null);
            when(preHandleWorkflow.preHandleTransaction(any(), any(), any(), eq(platformTxn), eq(null)))
                    .thenReturn(DUE_DILIGENCE_RESULT);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            assertThat(aliasesState.isModified()).isFalse();
            // TODO: Check that record was created
        }

        @Test
        @DisplayName("Update receipt, but charge no one, if running preHandle causes Status.UNKNOWN_FAILURE")
        void testPreHandleCausesUnknownFailure() {
            when(platformTxn.getMetadata()).thenReturn(null);
            when(preHandleWorkflow.preHandleTransaction(any(), any(), any(), eq(platformTxn), eq(null)))
                    .thenReturn(PreHandleResult.unknownFailure());

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            assertThat(accountsState.isModified()).isFalse();
            assertThat(aliasesState.isModified()).isFalse();
            // TODO: Check receipt
        }
    }

    @Test
    @DisplayName("Create penalty payment, if  previous preHandle resulted in a due diligence error")
    void testPreHandleWithDueDiligenceFailure() {
        // given
        when(platformTxn.getMetadata()).thenReturn(DUE_DILIGENCE_RESULT);
        // when
        workflow.handleRound(state, platformState, round);

        // then
        verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
        assertThat(aliasesState.isModified()).isFalse();
        // TODO: Verify that we created a penalty payment (https://github.com/hashgraph/hedera-services/issues/6811)
    }

    @Nested
    @DisplayName("Tests for cases when preHandle ran successfully")
    final class AddMissingSignaturesTest {
        @Test
        @DisplayName("Add failing verification result, if a key was handled in preHandle")
        void testRequiredExistingKeyWithFailingSignature() throws PreCheckException {
            // given
            final var alicesKey = ALICE.account().keyOrThrow();
            final var bobsKey = BOB.account().keyOrThrow();
            final var verificationResults = Map.<Key, SignatureVerificationFuture>of(
                    alicesKey, FakeSignatureVerificationFuture.goodFuture(alicesKey),
                    bobsKey, FakeSignatureVerificationFuture.badFuture(bobsKey));
            final var preHandleResult = new PreHandleResult(
                    ALICE.accountID(),
                    alicesKey,
                    Status.SO_FAR_SO_GOOD,
                    ResponseCodeEnum.OK,
                    new TransactionScenarioBuilder().txInfo(),
                    Set.of(bobsKey),
                    Set.of(),
                    Set.of(),
                    verificationResults,
                    null,
                    CONFIG_VERSION);
            when(platformTxn.getMetadata()).thenReturn(preHandleResult);
            doAnswer(invocation -> {
                        final var context = invocation.getArgument(0, PreHandleContext.class);
                        context.requireKey(bobsKey);
                        return null;
                    })
                    .when(dispatcher)
                    .dispatchPreHandle(any());

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(dispatcher, never()).dispatchHandle(any());
        }

        @Test
        @DisplayName("Trigger failing verification, if new key was found")
        void testRequiredNewKeyWithFailingSignature() throws PreCheckException {
            // given
            final var bobsKey = BOB.account().keyOrThrow();
            doAnswer(invocation -> {
                        final var context = invocation.getArgument(0, PreHandleContext.class);
                        context.requireKey(bobsKey);
                        return null;
                    })
                    .when(dispatcher)
                    .dispatchPreHandle(any());

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(dispatcher, never()).dispatchHandle(any());
        }

        @Test
        @DisplayName("Add passing verification result, if a key was handled in preHandle")
        void testOptionalExistingKeyWithPassingSignature() throws PreCheckException {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            final var alicesKey = ALICE.account().keyOrThrow();
            final var bobsKey = BOB.account().keyOrThrow();
            final var verificationResults = Map.<Key, SignatureVerificationFuture>of(
                    alicesKey, FakeSignatureVerificationFuture.goodFuture(alicesKey),
                    bobsKey, FakeSignatureVerificationFuture.goodFuture(bobsKey));
            final var preHandleResult = new PreHandleResult(
                    ALICE.accountID(),
                    alicesKey,
                    Status.SO_FAR_SO_GOOD,
                    ResponseCodeEnum.OK,
                    new TransactionScenarioBuilder().txInfo(),
                    Set.of(),
                    Set.of(bobsKey),
                    Set.of(),
                    verificationResults,
                    null,
                    CONFIG_VERSION);
            when(platformTxn.getMetadata()).thenReturn(preHandleResult);
            doReturn(ALICE.account()).when(solvencyPreCheck).getPayerAccount(any(), eq(ALICE.accountID()));
            doAnswer(invocation -> {
                        final var context = invocation.getArgument(0, PreHandleContext.class);
                        context.optionalKey(bobsKey);
                        return null;
                    })
                    .when(dispatcher)
                    .dispatchPreHandle(any());

            // when
            workflow.handleRound(state, platformState, round);

            // then
            final var argCapture = ArgumentCaptor.forClass(HandleContext.class);
            verify(dispatcher).dispatchHandle(argCapture.capture());
            final var alicesVerification = argCapture.getValue().verificationFor(alicesKey);
            assertThat(alicesVerification).isNotNull();
            assertThat(alicesVerification.key()).isEqualTo(alicesKey);
            assertThat(alicesVerification.evmAlias()).isNull();
            assertThat(alicesVerification.passed()).isTrue();
            final var bobsVerification = argCapture.getValue().verificationFor(bobsKey);
            assertThat(bobsVerification).isNotNull();
            assertThat(bobsVerification.key()).isEqualTo(bobsKey);
            assertThat(bobsVerification.evmAlias()).isNull();
            assertThat(bobsVerification.passed()).isTrue();
        }

        @Test
        void testComplexCase() {
            // given
            final var alicesKey = ALICE.account().keyOrThrow();
            final var bobsKey = BOB.account().keyOrThrow();
            final var carolsKey = CAROL.account().keyOrThrow();
            final var preHandleVerificationResults = Map.<Key, SignatureVerificationFuture>of(
                    alicesKey, FakeSignatureVerificationFuture.goodFuture(alicesKey),
                    bobsKey, FakeSignatureVerificationFuture.goodFuture(bobsKey),
                    carolsKey, FakeSignatureVerificationFuture.goodFuture(carolsKey));
            final var preHandleResult = new PreHandleResult(
                    ALICE.accountID(),
                    alicesKey,
                    Status.SO_FAR_SO_GOOD,
                    ResponseCodeEnum.OK,
                    new TransactionScenarioBuilder().txInfo(),
                    Set.of(),
                    Set.of(),
                    Set.of(),
                    preHandleVerificationResults,
                    null,
                    CONFIG_VERSION);
            when(platformTxn.getMetadata()).thenReturn(preHandleResult);
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // when
            workflow.handleRound(state, platformState, round);

            // then
            final var argCapture = ArgumentCaptor.forClass(HandleContext.class);
            verify(dispatcher).dispatchHandle(argCapture.capture());
            final var alicesVerification = argCapture.getValue().verificationFor(alicesKey);
            assertThat(alicesVerification).isNotNull();
            assertThat(alicesVerification.key()).isEqualTo(alicesKey);
            assertThat(alicesVerification.evmAlias()).isNull();
            assertThat(alicesVerification.passed()).isTrue();
            final var bobsVerification = argCapture.getValue().verificationFor(bobsKey);
            assertThat(bobsVerification).isNotNull();
            assertThat(bobsVerification.key()).isEqualTo(bobsKey);
            assertThat(bobsVerification.evmAlias()).isNull();
            assertThat(bobsVerification.passed()).isTrue();
            final var carolsVerification = argCapture.getValue().verificationFor(carolsKey);
            assertThat(carolsVerification).isNotNull();
            assertThat(carolsVerification.key()).isEqualTo(carolsKey);
            assertThat(carolsVerification.evmAlias()).isNull();
            assertThat(carolsVerification.passed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Tests for general validations before handle is called")
    final class ValidationTest {

        @Test
        @DisplayName("Reject transaction, if it is a duplicate from another node")
        void testDuplicateFromOtherNode() {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            when(recordCache.hasDuplicate(OK_RESULT.txInfo().txBody().transactionID(), selfNodeInfo.nodeId()))
                    .thenReturn(DuplicateCheckResult.OTHER_NODE);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isLessThan(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance())
                    .isEqualTo(DEFAULT_FEES.totalFee() + DEFAULT_FEES.nodeFee());
        }

        @Test
        @DisplayName("Reject transaction, if it is a duplicate from same node")
        void testDuplicateFromSameNode() {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            when(recordCache.hasDuplicate(OK_RESULT.txInfo().txBody().transactionID(), selfNodeInfo.nodeId()))
                    .thenReturn(DuplicateCheckResult.SAME_NODE);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isEqualTo(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance()).isLessThan(DEFAULT_FEES.totalFee());
        }

        @ParameterizedTest
        @EnumSource(names = {"INVALID_TRANSACTION_DURATION", "TRANSACTION_EXPIRED", "INVALID_TRANSACTION_START"})
        @DisplayName("Reject transaction, if it does not fit in the time box")
        void testExpiredTransactionFails(final ResponseCodeEnum responseCode) throws PreCheckException {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            doThrow(new PreCheckException(responseCode))
                    .when(checker)
                    .checkTimeBox(
                            OK_RESULT.txInfo().txBody(),
                            TX_CONSENSUS_NOW,
                            TransactionChecker.RequireMinValidLifetimeBuffer.NO);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var block = getRecordFromStream();
            assertThat(block).has(SingleTransactionRecordConditions.status(responseCode));
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isEqualTo(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance())
                    .isEqualTo(DEFAULT_FEES.totalFee() - DEFAULT_FEES.networkFee());
        }

        @ParameterizedTest
        @EnumSource(names = {"INVALID_ACCOUNT_ID", "ACCOUNT_DELETED"})
        @DisplayName("Reject transaction, if the payer account is not valid")
        void testInvalidPayerAccountFails(final ResponseCodeEnum responseCode) throws PreCheckException {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willReturn(createPreHandleResult(Status.NODE_DUE_DILIGENCE_FAILURE, responseCode));
            final var numInvocations = new AtomicLong();
            // given
            doAnswer(invocation -> {
                        if (numInvocations.incrementAndGet() == 1L) {
                            return ALICE.account();
                        } else {
                            throw new PreCheckException(responseCode);
                        }
                    })
                    .when(solvencyPreCheck)
                    .getPayerAccount(any(), eq(ALICE.accountID()));

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var block = getRecordFromStream();
            assertThat(block).has(SingleTransactionRecordConditions.status(responseCode));
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isEqualTo(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance()).isLessThan(DEFAULT_FEES.totalFee());
        }

        @ParameterizedTest
        @EnumSource(
                names = {
                    "INSUFFICIENT_TX_FEE",
                    "INVALID_TRANSACTION_BODY",
                    "INSUFFICIENT_ACCOUNT_BALANCE",
                    "ACCOUNT_EXPIRED_AND_PENDING_REMOVAL"
                })
        @DisplayName("Reject transaction, if the payer cannot pay the fees")
        void testInsolventPayerAccountFails(final ResponseCodeEnum responseCode) throws PreCheckException {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            doThrow(new PreCheckException(responseCode))
                    .when(solvencyPreCheck)
                    .checkSolvency(eq(OK_RESULT.txInfo()), any(), eq(DEFAULT_FEES), eq(FALSE));

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var block = getRecordFromStream();
            assertThat(block).has(SingleTransactionRecordConditions.status(responseCode));
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isEqualTo(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance()).isLessThan(DEFAULT_FEES.totalFee());
        }

        @Test
        @DisplayName("Reject transaction, if the payer is not authorized")
        void testNonAuthorizedAccountFails() {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            when(authorizer.isAuthorized(ALICE.accountID(), OK_RESULT.txInfo().functionality()))
                    .thenReturn(false);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var block = getRecordFromStream();
            assertThat(block).has(SingleTransactionRecordConditions.status(UNAUTHORIZED));
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isLessThan(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance())
                    .isEqualTo(DEFAULT_FEES.totalFee() + DEFAULT_FEES.nodeFee());
        }

        @Test
        @DisplayName("Reject transaction, if the transaction is privileged and the payer is not authorized")
        void testNonAuthorizedAccountFailsForPrivilegedTxn() {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            when(authorizer.hasPrivilegedAuthorization(
                            ALICE.accountID(),
                            OK_RESULT.txInfo().functionality(),
                            OK_RESULT.txInfo().txBody()))
                    .thenReturn(SystemPrivilege.UNAUTHORIZED);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var block = getRecordFromStream();
            assertThat(block).has(SingleTransactionRecordConditions.status(AUTHORIZATION_FAILED));
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isLessThan(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance())
                    .isEqualTo(DEFAULT_FEES.totalFee() + DEFAULT_FEES.nodeFee());
        }

        @Test
        @DisplayName("Reject transaction, if the transaction is impermissible")
        void testImpermissibleTransactionFails() {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            when(authorizer.hasPrivilegedAuthorization(
                            ALICE.accountID(),
                            OK_RESULT.txInfo().functionality(),
                            OK_RESULT.txInfo().txBody()))
                    .thenReturn(SystemPrivilege.IMPERMISSIBLE);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var block = getRecordFromStream();
            assertThat(block).has(SingleTransactionRecordConditions.status(ENTITY_NOT_ALLOWED_TO_DELETE));
            assertThat(accountsState.get(ALICE.accountID()).tinybarBalance()).isLessThan(DEFAULT_FEES.totalFee());
            assertThat(accountsState.get(nodeSelfAccountId).tinybarBalance())
                    .isEqualTo(DEFAULT_FEES.totalFee() + DEFAULT_FEES.nodeFee());
        }

        @ParameterizedTest
        @EnumSource(names = {"UNNECESSARY", "AUTHORIZED"})
        @DisplayName("Accept transaction, if the transaction is not privileged or the payer is authorized")
        void testAuthorizedAccountFailsForPrivilegedTxn(final SystemPrivilege privilege) {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));

            // given
            when(authorizer.hasPrivilegedAuthorization(
                            ALICE.accountID(),
                            OK_RESULT.txInfo().functionality(),
                            OK_RESULT.txInfo().txBody()))
                    .thenReturn(privilege);

            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            final var block = getRecordFromStream();
            assertThat(block).has(SingleTransactionRecordConditions.status(SUCCESS));
        }
    }

    @Nested
    @DisplayName("Tests for special cases during transaction dispatching")
    final class DispatchTest {
        @Test
        @DisplayName("Charge user, but do not change state otherwise, if transaction causes a HandleException")
        void testHandleException() {
            // when
            doThrow(new HandleException(ResponseCodeEnum.INVALID_SIGNATURE))
                    .when(dispatcher)
                    .dispatchHandle(any());
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            assertThat(aliasesState.isModified()).isFalse();
            // TODO: Check that record was created
        }

        @Test
        @DisplayName("Update receipt, but charge no one, if transaction causes an unexepected exception")
        void testUnknownFailure() {
            // when
            doThrow(new ArrayIndexOutOfBoundsException()).when(dispatcher).dispatchHandle(any());
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            assertThat(accountsState.isModified()).isFalse();
            assertThat(aliasesState.isModified()).isFalse();
            // TODO: Check receipt
        }
    }

    @Nested
    @DisplayName("Tests for checking the interaction with the record manager")
    final class RecordManagerInteractionTest {

        // TODO: Add more tests to make sure we produce the right input for the recordManger (once it is implemented)
        // https://github.com/hashgraph/hedera-services/issues/6746

        @Test
        void testSimpleRun() {
            given(preHandleWorkflow.preHandleTransaction(any(), any(), any(), any(), any()))
                    .willAnswer(invocation -> invocation.getArgument(4));
            // when
            workflow.handleRound(state, platformState, round);

            // then
            verify(blockRecordManager).advanceConsensusClock(notNull(), notNull());
            verify(blockRecordManager).startUserTransaction(TX_CONSENSUS_NOW, state);
            verify(blockRecordManager).endUserTransaction(any(), eq(state));
            verify(blockRecordManager).endRound(state);
        }
    }

    @Test
    void testConsensusTimeHooksCalled() {
        workflow.handleRound(state, platformState, round);
        verify(blockRecordManager).consTimeOfLastHandledTxn();
        verify(genesisRecordsTimeHook).process(notNull());
        verify(stakingPeriodTimeHook).process(notNull(), notNull());
    }

    private SingleTransactionRecord getRecordFromStream() {
        final var argument = ArgumentCaptor.forClass(Stream.class);
        verify(blockRecordManager).endUserTransaction(argument.capture(), eq(state));
        final var blockStream = argument.getValue().toList();
        assertThat(blockStream).isNotEmpty();
        return (SingleTransactionRecord) blockStream.get(0);
    }
}
