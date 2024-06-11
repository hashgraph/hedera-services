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

package com.hedera.node.app.workflows.handle.flow.dispatch.txn.logic;

import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.*;

import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.ScheduleExpirationHook;
import com.hedera.node.app.workflows.handle.flow.txn.UserTransactionComponent;
import com.hedera.node.app.workflows.handle.flow.txn.logic.SchedulePurger;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulePurgerTest {
    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock(strictness = LENIENT)
    private UserTransactionComponent userTxnContext;

    @Mock
    private WritableScheduleStore writableScheduleStore;

    @Mock
    private SavepointStackImpl savepointStack;

    @Mock(strictness = LENIENT)
    private TokenContext tokenContext;

    private Configuration configuration = HederaTestConfigBuilder.createConfig();
    private ScheduleExpirationHook scheduleExpirationHook = new ScheduleExpirationHook();

    @InjectMocks
    private SchedulePurger subject;

    @BeforeEach
    void setUp() {
        subject = new SchedulePurger(scheduleExpirationHook, storeMetricsService);
        when(userTxnContext.stack()).thenReturn(savepointStack);
        when(userTxnContext.tokenContext()).thenReturn(tokenContext);
        when(tokenContext.configuration()).thenReturn(configuration);
    }

    @Test
    void expireSchedulesWhenLastHandledTxnTimeIsEpoch() {
        when(userTxnContext.lastHandledConsensusTime()).thenReturn(Instant.EPOCH);

        try (MockedConstruction<WritableStoreFactory> mocked =
                Mockito.mockConstruction(WritableStoreFactory.class, (mock, context) -> {
                    when(mock.getStore(WritableScheduleStore.class)).thenReturn(writableScheduleStore);
                })) {

            subject.expireSchedules(userTxnContext);

            verifyNoInteractions(writableScheduleStore);
            verifyNoInteractions(storeMetricsService);
        }
    }

    @Test
    void expireSchedulesWhenCurrentConsensusTimeGreaterThanLastHandledTime() {
        Instant lastHandledTime = Instant.ofEpochSecond(1000);
        Instant consensusNow = Instant.ofEpochSecond(2000);

        when(userTxnContext.lastHandledConsensusTime()).thenReturn(lastHandledTime);
        when(userTxnContext.consensusNow()).thenReturn(consensusNow);
        try (MockedConstruction<WritableStoreFactory> mocked =
                Mockito.mockConstruction(WritableStoreFactory.class, (mock, context) -> {
                    when(mock.getStore(WritableScheduleStore.class)).thenReturn(writableScheduleStore);
                })) {

            subject.expireSchedules(userTxnContext);

            verify(writableScheduleStore).purgeExpiredSchedulesBetween(1000, 1999);
            verify(userTxnContext.stack()).commitFullStack();
        }
    }

    @Test
    void expireSchedulesWhenCurrentConsensusTimeNotGreaterThanLastHandledTime() {
        Instant lastHandledTime = Instant.ofEpochSecond(2000);
        Instant consensusNow = Instant.ofEpochSecond(1000);

        when(userTxnContext.lastHandledConsensusTime()).thenReturn(lastHandledTime);
        when(userTxnContext.consensusNow()).thenReturn(consensusNow);

        try (MockedConstruction<WritableStoreFactory> mocked =
                Mockito.mockConstruction(WritableStoreFactory.class, (mock, context) -> {
                    when(mock.getStore(WritableScheduleStore.class)).thenReturn(writableScheduleStore);
                })) {

            subject.expireSchedules(userTxnContext);

            verifyNoInteractions(writableScheduleStore);
            verifyNoInteractions(storeMetricsService);
        }
    }
}
