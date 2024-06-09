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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fixtures.AppTestBase;
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
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.throttle.ThrottleServiceManager;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionScenarioBuilder;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.txn.UserTransactionComponent;
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
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private TransactionChecker transactionChecker;

    @Mock
    private InitTrigger initTrigger;

    @Mock
    private SoftwareVersion softwareVersion;

    @Mock
    private Provider<UserTransactionComponent.Factory> userTxnProvider;

    private HandleWorkflow workflow;

    @BeforeEach
    void setup() throws PreCheckException {

        setupStandardStates();

        final var config = new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), CONFIG_VERSION);
        when(configProvider.getConfiguration()).thenReturn(config);

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

        final var hederaVersion =
                new HederaSoftwareVersion(selfNodeInfo.hapiVersion(), selfNodeInfo.appVersion(), (int) CONFIG_VERSION);
        when(event.getSoftwareVersion()).thenReturn(hederaVersion);

        when(platformTxn.getConsensusTimestamp()).thenReturn(CONSENSUS_NOW);
        when(platformTxn.getMetadata()).thenReturn(OK_RESULT);
        lenient().when(blockRecordManager.consTimeOfLastHandledTxn()).thenReturn(CONSENSUS_NOW.minusSeconds(1));

        when(serviceLookup.getServiceName(any())).thenReturn(TokenService.NAME);

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
                blockRecordManager,
                cacheWarmer,
                handleWorkflowMetrics,
                throttleServiceManager,
                userTxnProvider);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testContructorWithInvalidArguments() {
        assertThatThrownBy(() -> new HandleWorkflow(
                        null,
                        blockRecordManager,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager,
                        userTxnProvider))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo, null, cacheWarmer, handleWorkflowMetrics, throttleServiceManager, userTxnProvider))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        blockRecordManager,
                        null,
                        handleWorkflowMetrics,
                        throttleServiceManager,
                        userTxnProvider))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo, blockRecordManager, cacheWarmer, null, throttleServiceManager, userTxnProvider))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo, blockRecordManager, cacheWarmer, handleWorkflowMetrics, null, userTxnProvider))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleWorkflow(
                        networkInfo,
                        blockRecordManager,
                        cacheWarmer,
                        handleWorkflowMetrics,
                        throttleServiceManager,
                        null))
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
        verify(blockRecordManager, never()).startUserTransaction(any(), any(), any());
        verify(blockRecordManager, never()).endUserTransaction(any(), any());
        verify(throttleServiceManager).updateAllMetrics();
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
    }
}
