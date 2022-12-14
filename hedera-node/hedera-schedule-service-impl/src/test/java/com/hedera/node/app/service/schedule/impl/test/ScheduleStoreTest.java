package com.hedera.node.app.service.schedule.impl.test;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ScheduleStore;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScheduleStoreTest {
    @Mock States states;
    @Mock State state;
    @Mock ScheduleVirtualValue schedule;
    @Mock JKey adminKey;
    private ScheduleStore subject;

    @BeforeEach
    void setUp() {
        given(states.get("SCHEDULES_BY_ID")).willReturn(state);
        subject = new ScheduleStore(states);
    }

    @Test
    void constructorThrowsIfStatesIsNull() {
        assertThrows(NullPointerException.class, () -> new ScheduleStore(null));
    }

    @Test
    void returnsEmptyIfMissingSchedule(){
        given(state.get(1L)).willReturn(Optional.empty());

        assertEquals(Optional.empty(), subject.get(ScheduleID.newBuilder().setScheduleNum(1L).build()));
    }

    @Test
    void getsScheduleMetaFromFetchedSchedule(){
        given(state.get(1L)).willReturn(Optional.of(schedule));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(schedule.adminKey()).willReturn(Optional.of(adminKey));
        given(schedule.hasExplicitPayer()).willReturn(true);
        given(schedule.payer()).willReturn(EntityId.fromNum(2L));

        final var meta = subject.get(ScheduleID.newBuilder().setScheduleNum(1L).build());

        assertEquals(Optional.of(adminKey), meta.get().adminKey());
        assertEquals(TransactionBody.getDefaultInstance(), meta.get().scheduledTxn());
        assertEquals(Optional.of(EntityId.fromNum(2L).toGrpcAccountId()), meta.get().designatedPayer());
    }

    @Test
    void getsScheduleMetaFromFetchedScheduleNoExplicitPayer(){
        given(state.get(1L)).willReturn(Optional.of(schedule));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(schedule.adminKey()).willReturn(Optional.of(adminKey));
        given(schedule.hasExplicitPayer()).willReturn(false);

        final var meta = subject.get(ScheduleID.newBuilder().setScheduleNum(1L).build());

        assertEquals(Optional.of(adminKey), meta.get().adminKey());
        assertEquals(TransactionBody.getDefaultInstance(), meta.get().scheduledTxn());
        assertEquals(Optional.empty(), meta.get().designatedPayer());
    }
}
