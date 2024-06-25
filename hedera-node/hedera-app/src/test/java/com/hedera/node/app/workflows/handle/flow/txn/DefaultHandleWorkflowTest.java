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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.ScheduleExpirationHook;
import com.hedera.node.app.workflows.handle.StakingPeriodTimeHook;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.helpers.DispatchProcessor;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
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

    @Mock
    private HederaState state;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock(strictness = LENIENT)
    private SavepointStackImpl savepointStack;

    @Mock
    private WritableStates states;

    @Mock
    private WritableKVState schedulesById;

    @Mock
    private PreHandleResult preHandleResult;

    @Mock
    private UserRecordInitializer userRecordInitializer;

    @Mock
    private Authorizer authorizer;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private FeeManager feeManager;

    @Mock
    private RecordCache recordCache;

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
                storeMetricsService,
                userRecordInitializer,
                authorizer,
                networkInfo,
                feeManager,
                recordCache,
                serviceScopeLookup,
                exchangeRateManager,
                childDispatchFactory,
                dispatcher,
                networkUtilizationManager);
        // TODO
        lenient().when(state.getWritableStates(anyString())).thenReturn(states);
        //        when(userTxn.stack()).thenReturn(savepointStack);
        when(savepointStack.getWritableStates(anyString())).thenReturn(states);
        // TODO

        //        when(userTxn.platformTxn()).thenReturn(mock(ConsensusTransactionImpl.class));
        //        when(userTxn.txnInfo()).thenReturn(mock(TransactionInfo.class));
        //        when(userTxn.txnInfo().txBody()).thenReturn(mock(TransactionBody.class));
        //        when(userTxn.txnInfo().payerID()).thenReturn(mock(AccountID.class));
        //        when(userTxn.preHandleResult()).thenReturn(mock(PreHandleResult.class));
        when(states.get(any())).thenReturn(schedulesById);
    }

    @Test
    public void testExecute() {
        //        subject.execute(userTxn);

        verify(stakingPeriodTimeHook).process(any(), any());
        verify(blockRecordManager).advanceConsensusClock(any(), any());
        // TODO
    }

    @Test
    public void testExecuteWithStakingPeriodTimeHookException() {
        doThrow(new RuntimeException("Test exception"))
                .when(stakingPeriodTimeHook)
                .process(any(), any());

        //        subject.execute(userTxn);

        //        verify(stakingPeriodTimeHook).process(userTxn.stack(), userTxn.tokenContext());
        //        verify(blockRecordManager).advanceConsensusClock(userTxn.consensusNow(), userTxn.state());
        // TODO
        // TODO

        assertTrue(logCaptor.errorLogs().getFirst().contains("Failed to process staking period time hook"));
    }
}
