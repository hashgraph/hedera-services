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

package com.hedera.node.app.service.schedule.impl.schemas;

import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_COUNTS_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_ORDERS_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.hapi.node.state.schedule.ScheduledCounts;
import com.hedera.hapi.node.state.schedule.ScheduledOrder;
import com.hedera.node.app.service.schedule.impl.ScheduleStoreUtility;
import com.hedera.node.app.service.schedule.impl.ScheduleTestBase;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapReadableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class V0570ScheduleSchemaTest extends ScheduleTestBase {
    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private V0570ScheduleSchema subject;

    @Mock
    private MigrationContext migrationContext;

    private final Map<ProtoLong, ScheduleList> schedulesByExpirySec = new HashMap<>();
    private MapReadableKVState<ProtoLong, ScheduleList> readableSchedulesByExpirySec;
    private MapWritableKVState<TimestampSeconds, ScheduledCounts> writableScheduleCounts;
    private MapWritableKVState<ScheduledOrder, ScheduleID> writableScheduleOrders;

    private final Map<ProtoBytes, ScheduleList> schedulesByEquality = new HashMap<>();
    private final Map<ProtoBytes, ScheduleID> scheduleByEquality = new HashMap<>();
    private MapReadableKVState<ProtoBytes, ScheduleList> readableSchedulesByEquality;
    private MapWritableKVState<ProtoBytes, ScheduleID> writableScheduleIdByEquality;

    private MapWritableStates writableStates = null;
    private MapReadableStates readableStates = null;

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
        subject = new V0570ScheduleSchema();
    }

    @Test
    void constructorHappyPath() {
        assertThat(subject.getVersion())
                .isEqualTo(
                        SemanticVersion.newBuilder().major(0).minor(57).patch(0).build());
    }

    @Test
    void testStatesToRemove() {
        Set<String> statesToRemove = subject.statesToRemove();
        assertNotNull(statesToRemove);
        assertEquals(2, statesToRemove.size());
        assertTrue(statesToRemove.containsAll(Set.of(SCHEDULES_BY_EXPIRY_SEC_KEY, SCHEDULES_BY_EQUALITY_KEY)));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void restartNullArgThrows() {
        Assertions.assertThatThrownBy(() -> subject.restart(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void restartHappyPath() {
        Assertions.assertThatNoException().isThrownBy(() -> subject.restart(migrationContext));
    }

    @Test
    void migrateAsExpected() {
        setupMigrationContext();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Migrated 2 schedules from SCHEDULES_BY_EQUALITY_KEY");
        assertThat(writableScheduleIdByEquality.size()).isEqualTo(2L);
    }

    private void setupMigrationContext() {
        final var scheduler1 =
                otherScheduleInState.copyBuilder().memo("otherMemo").build();
        schedulesByExpirySec.put(
                new ProtoLong(scheduler1.calculatedExpirationSecond()),
                ScheduleList.newBuilder()
                        .schedules(List.of(scheduler1, otherScheduleInState))
                        .build());
        readableSchedulesByExpirySec = new MapReadableKVState<>(SCHEDULES_BY_EXPIRY_SEC_KEY, schedulesByExpirySec);
        writableScheduleCounts = new MapWritableKVState<>(SCHEDULED_COUNTS_KEY, new HashMap<>());
        writableScheduleOrders = new MapWritableKVState<>(SCHEDULED_ORDERS_KEY, new HashMap<>());

        final ProtoBytes protoHash1 = new ProtoBytes(ScheduleStoreUtility.calculateBytesHash(scheduler1));
        final ProtoBytes protoHash2 = new ProtoBytes(ScheduleStoreUtility.calculateBytesHash(otherScheduleInState));
        schedulesByEquality.put(
                protoHash1,
                ScheduleList.newBuilder().schedules(List.of(scheduler1)).build());
        schedulesByEquality.put(
                protoHash2,
                ScheduleList.newBuilder()
                        .schedules(List.of(otherScheduleInState))
                        .build());
        readableSchedulesByEquality = new MapReadableKVState<>(SCHEDULES_BY_EQUALITY_KEY, schedulesByEquality);
        writableScheduleIdByEquality = new MapWritableKVState<>(SCHEDULE_ID_BY_EQUALITY_KEY, scheduleByEquality);

        writableStates = MapWritableStates.builder()
                .state(writableScheduleIdByEquality)
                .state(writableScheduleCounts)
                .state(writableScheduleOrders)
                .build();
        readableStates = MapReadableStates.builder()
                .state(readableSchedulesByExpirySec)
                .state(readableSchedulesByEquality)
                .build();
        given(migrationContext.newStates()).willReturn(writableStates);
        given(migrationContext.previousStates()).willReturn(readableStates);
    }
}
