/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.schedule;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleExecutorTest {
    private ScheduleID id = IdUtils.asSchedule("0.0.1234");

    @Mock private ScheduleStore store;
    @Mock private TransactionContext txnCtx;
    @Mock private ScheduleVirtualValue schedule;

    private ScheduleExecutor subject;
    @Mock private TxnAccessor accessor;

    @Mock AccessorFactory factory;

    @BeforeEach
    void setUp() {
        subject = new ScheduleExecutor(factory);
    }

    @Test
    void processesIfCanMarkAsExecuted() throws InvalidProtocolBufferException {
        given(store.preMarkAsExecuted(id)).willReturn(OK);
        given(store.get(id)).willReturn(schedule);
        given(schedule.asSignedTxn()).willReturn(Transaction.getDefaultInstance());
        given(schedule.effectivePayer()).willReturn(new EntityId(0, 0, 4321));

        // when:
        var result = subject.processImmediateExecution(id, store, txnCtx);

        // then:
        verify(txnCtx).trigger(any());
        // and:
        Assertions.assertEquals(OK, result);
        verify(factory)
                .triggeredTxn(
                        Transaction.getDefaultInstance(),
                        new EntityId(0, 0, 4321).toGrpcAccountId(),
                        id,
                        false,
                        false);
    }

    @Test
    void triggerReturnsIfCanMarkAsExecuted() throws InvalidProtocolBufferException {
        given(store.preMarkAsExecuted(id)).willReturn(OK);
        given(store.get(id)).willReturn(schedule);
        given(schedule.asSignedTxn()).willReturn(Transaction.getDefaultInstance());
        given(schedule.effectivePayer()).willReturn(new EntityId(0, 0, 4321));
        given(
                        factory.triggeredTxn(
                                Transaction.getDefaultInstance(),
                                new EntityId(0, 0, 4321).toGrpcAccountId(),
                                id,
                                true,
                                true))
                .willReturn(accessor);

        // when:
        var result = subject.getTriggeredTxnAccessor(id, store, false);

        // then:
        Assertions.assertEquals(OK, result.getLeft());
        // and:
        verify(txnCtx, never()).trigger(any());
        Assertions.assertEquals(accessor, result.getRight());
    }

    @Test
    void nullArgumentsThrow() {
        Assertions.assertThrows(
                RuntimeException.class,
                () -> subject.processImmediateExecution(null, store, txnCtx));
        Assertions.assertThrows(
                RuntimeException.class, () -> subject.processImmediateExecution(id, null, txnCtx));
        Assertions.assertThrows(
                RuntimeException.class, () -> subject.processImmediateExecution(id, store, null));

        Assertions.assertThrows(
                RuntimeException.class, () -> subject.getTriggeredTxnAccessor(null, store, false));
        Assertions.assertThrows(
                RuntimeException.class, () -> subject.getTriggeredTxnAccessor(id, null, false));
    }

    @Test
    void doesntProcessUnlessAbleToPreMarkScheduleExecuted() throws InvalidProtocolBufferException {
        given(store.preMarkAsExecuted(id)).willReturn(SCHEDULE_ALREADY_EXECUTED);

        // when:
        var result = subject.processImmediateExecution(id, store, txnCtx);

        // then:
        verify(txnCtx, never()).trigger(any());
        // and:
        Assertions.assertEquals(SCHEDULE_ALREADY_EXECUTED, result);
    }

    @Test
    void doesntReturnTriggerUnlessAbleToPreMarkScheduleExecuted()
            throws InvalidProtocolBufferException {
        given(store.preMarkAsExecuted(id)).willReturn(SCHEDULE_ALREADY_EXECUTED);

        // when:
        var result = subject.getTriggeredTxnAccessor(id, store, false);

        // then:
        verify(txnCtx, never()).trigger(any());
        // and:
        Assertions.assertEquals(SCHEDULE_ALREADY_EXECUTED, result.getLeft());
        Assertions.assertNull(result.getRight());
    }
}
