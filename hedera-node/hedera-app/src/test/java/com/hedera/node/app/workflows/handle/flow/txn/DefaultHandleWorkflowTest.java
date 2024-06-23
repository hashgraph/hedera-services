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

package com.hedera.node.app.workflows.handle.flow.txn;

import static com.hedera.node.app.workflows.handle.flow.DispatchHandleContextTest.CONSENSUS_NOW;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.ScheduleExpirationHook;
import com.hedera.node.app.workflows.handle.StakingPeriodTimeHook;
import com.hedera.node.app.workflows.handle.flow.dispatch.helpers.DispatchProcessor;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserDispatchComponent;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.state.HederaState;
import javax.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
public class DefaultHandleWorkflowTest {
    @Mock
    private StakingPeriodTimeHook stakingPeriodTimeHook;

    @Mock
    private BlockRecordManager blockRecordManager;

    @Mock
    private DispatchProcessor dispatchProcessor;

    @Mock
    private HollowAccountCompleter hollowAccountFinalization;

    @Mock(strictness = LENIENT)
    private UserTxnComponent userTxn;

    @Mock
    private HederaState state;

    @Mock
    private Provider<UserDispatchComponent.Factory> userDispatchProvider;

    @Mock
    private UserDispatchComponent.Factory userDispatchComponentFactory;

    @Mock
    private UserDispatchComponent userDispatchComponent;

    @Mock
    private StoreMetricsService storeMetricsService;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private DefaultHandleWorkflow subject;

    private ScheduleExpirationHook scheduleExpirationHook = new ScheduleExpirationHook();

    @BeforeEach
    public void setUp() {
        subject = new DefaultHandleWorkflow(
                stakingPeriodTimeHook,
                blockRecordManager,
                dispatchProcessor,
                hollowAccountFinalization,
                scheduleExpirationHook,
                storeMetricsService);

        when(userTxn.consensusNow()).thenReturn(CONSENSUS_NOW);
        when(userTxn.state()).thenReturn(state);
        when(userTxn.userDispatchProvider()).thenReturn(userDispatchProvider);
        when(userTxn.userDispatchProvider().get()).thenReturn(userDispatchComponentFactory);
        lenient().when(userTxn.userDispatchProvider().get().create()).thenReturn(userDispatchComponent);

        when(userTxn.platformTxn()).thenReturn(mock(ConsensusTransactionImpl.class));
        when(userTxn.txnInfo()).thenReturn(mock(TransactionInfo.class));
        when(userTxn.txnInfo().txBody()).thenReturn(mock(TransactionBody.class));
        when(userTxn.txnInfo().payerID()).thenReturn(mock(AccountID.class));
        when(userTxn.preHandleResult()).thenReturn(mock(PreHandleResult.class));
    }

    @Test
    public void testExecute() {
        subject.execute(userTxn);

        verify(stakingPeriodTimeHook).process(any(), any());
        verify(blockRecordManager).advanceConsensusClock(any(), any());
        verify(hollowAccountFinalization)
                .finalizeHollowAccounts(
                        userTxn, userTxn.userDispatchProvider().get().create());
        verify(dispatchProcessor)
                .processDispatch(userTxn.userDispatchProvider().get().create());
    }

    @Test
    public void testExecuteWithStakingPeriodTimeHookException() {
        doThrow(new RuntimeException("Test exception"))
                .when(stakingPeriodTimeHook)
                .process(any(), any());

        subject.execute(userTxn);

        verify(stakingPeriodTimeHook).process(userTxn.stack(), userTxn.tokenContext());
        verify(blockRecordManager).advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
        verify(hollowAccountFinalization)
                .finalizeHollowAccounts(
                        userTxn, userTxn.userDispatchProvider().get().create());
        verify(dispatchProcessor)
                .processDispatch(userTxn.userDispatchProvider().get().create());

        assertTrue(logCaptor.errorLogs().getFirst().contains("Failed to process staking period time hook"));
    }
}
