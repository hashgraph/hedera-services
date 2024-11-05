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

package com.hedera.node.app.service.schedule.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema;
import com.hedera.node.app.spi.store.StoreFactory;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.StateDefinition;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    private StoreFactory storeFactory;
    private WritableScheduleStoreImpl store;
    private Supplier<StoreFactory> cleanupStoreFactory;
    private ScheduleService scheduleService;

    @Test
    void testsSpi() {
        final ScheduleService service = new ScheduleServiceImpl();
        BDDAssertions.assertThat(service).isNotNull();
        BDDAssertions.assertThat(service.getClass()).isEqualTo(ScheduleServiceImpl.class);
        BDDAssertions.assertThat(service.getServiceName()).isEqualTo("ScheduleService");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void registersExpectedSchema() {
        final ScheduleServiceImpl subject = new ScheduleServiceImpl();
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);
        subject.registerSchemas(registry);
        Mockito.verify(registry, times(2)).register(schemaCaptor.capture());

        final var schemas = schemaCaptor.getAllValues();
        assertThat(schemas).hasSize(2);
        assertThat(schemas.getFirst()).isInstanceOf(V0490ScheduleSchema.class);
        assertThat(schemas.get(1)).isInstanceOf(V0570ScheduleSchema.class);

        Set<StateDefinition> statesToCreate = schemas.getFirst().statesToCreate();
        BDDAssertions.assertThat(statesToCreate).isNotNull();
        List<String> statesList =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().toList();
        BDDAssertions.assertThat(statesToCreate.size()).isEqualTo(3);
        BDDAssertions.assertThat(statesList.get(0)).isEqualTo(V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY);
        BDDAssertions.assertThat(statesList.get(1)).isEqualTo(V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY);
        BDDAssertions.assertThat(statesList.get(2)).isEqualTo(V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);

        statesToCreate = schemas.get(1).statesToCreate();
        BDDAssertions.assertThat(statesToCreate).isNotNull();
        statesList =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().toList();
        BDDAssertions.assertThat(statesToCreate.size()).isEqualTo(2);
        BDDAssertions.assertThat(statesList.get(0)).isEqualTo(V0570ScheduleSchema.SCHEDULE_IDS_BY_EXPIRY_SEC_KEY);
        BDDAssertions.assertThat(statesList.get(1)).isEqualTo(V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY);
    }

    @Test
    void testBasicIteration() {
        setUpMocks();
        // Given two schedules within the interval
        final var schedule1 = createMockSchedule(Instant.now().plusSeconds(60));
        final var schedule2 = createMockSchedule(Instant.now().plusSeconds(120));
        when(store.getByExpirationBetween(anyLong(), anyLong())).thenReturn(List.of(schedule1, schedule2));

        final var iterator =
                scheduleService.iterTxnsForInterval(Instant.now(), Instant.now().plusSeconds(180), cleanupStoreFactory);

        // Assert both schedules can be iterated over
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isNotNull();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isNotNull();
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    void testEmptyList() {
        setUpMocks();
        // No schedules within the interval
        when(store.getByExpirationBetween(anyLong(), anyLong())).thenReturn(List.of());

        final var iterator =
                scheduleService.iterTxnsForInterval(Instant.now(), Instant.now().plusSeconds(180), cleanupStoreFactory);

        // Assert that iterator has no elements
        assertThat(iterator.hasNext()).isFalse();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void testRemoveFunctionality() {
        setUpMocks();
        // Given one schedule
        final var schedule = createMockSchedule(Instant.now().plusSeconds(60));
        when(store.getByExpirationBetween(anyLong(), anyLong())).thenReturn(List.of(schedule));

        final var iterator =
                scheduleService.iterTxnsForInterval(Instant.now(), Instant.now().plusSeconds(120), cleanupStoreFactory);

        assertThat(iterator.hasNext()).isTrue();
        final var txn = iterator.next();
        assertThat(txn).isNotNull();

        // Test remove
        iterator.remove();

        // Verify that delete and purge were called on the store
        final InOrder inOrder = inOrder(store);
        inOrder.verify(store).delete(eq(schedule.scheduleId()), any());
        inOrder.verify(store).purge(eq(schedule.scheduleId()));
    }

    @Test
    void testRemoveWithoutNextShouldThrowException() {
        setUpMocks();
        // Given one schedule
        final var schedule = mock(Schedule.class);
        when(store.getByExpirationBetween(anyLong(), anyLong())).thenReturn(List.of(schedule));

        final var iterator =
                scheduleService.iterTxnsForInterval(Instant.now(), Instant.now().plusSeconds(120), cleanupStoreFactory);

        // Attempt to remove without calling next() should throw IllegalStateException
        assertThatThrownBy(iterator::remove).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testNextBeyondEndShouldThrowException() {
        setUpMocks();
        // Given one schedule
        final var schedule = createMockSchedule(Instant.now().plusSeconds(60));
        when(store.getByExpirationBetween(anyLong(), anyLong())).thenReturn(List.of(schedule));

        final var iterator =
                scheduleService.iterTxnsForInterval(Instant.now(), Instant.now().plusSeconds(120), cleanupStoreFactory);

        assertThat(iterator.hasNext()).isTrue();
        iterator.next();

        // No more elements, calling next() should throw NoSuchElementException
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void testFilterExecutedOrDeletedSchedules() {
        setUpMocks();
        // Given three schedules, one executed, one deleted, and one valid
        final var schedule1 = mockExecuted();
        final var schedule2 = mockDeleted();
        final var schedule3 = createMockSchedule(Instant.now().plusSeconds(180)); // Valid

        when(store.getByExpirationBetween(anyLong(), anyLong())).thenReturn(List.of(schedule1, schedule2, schedule3));

        final var iterator =
                scheduleService.iterTxnsForInterval(Instant.now(), Instant.now().plusSeconds(200), cleanupStoreFactory);

        // Only the valid schedule should be iterated over
        assertThat(iterator.hasNext()).isTrue();
        final var txn = iterator.next();
        assertThat(txn).isNotNull();
        assertThat(iterator.hasNext()).isFalse();
    }

    private Schedule createMockSchedule(final Instant expiration) {
        final var schedule = mock(Schedule.class);
        final var createTransaction = mock(TransactionBody.class);
        when(createTransaction.transactionIDOrThrow()).thenReturn(TransactionID.DEFAULT);
        when(schedule.originalCreateTransactionOrThrow()).thenReturn(createTransaction);
        when(schedule.executed()).thenReturn(false);
        when(schedule.deleted()).thenReturn(false);
        when(schedule.calculatedExpirationSecond()).thenReturn(expiration.getEpochSecond());
        when(schedule.signatories()).thenReturn(Collections.emptyList()); // Customize as necessary
        return schedule;
    }

    private Schedule mockDeleted() {
        final var schedule = mock(Schedule.class);
        when(schedule.deleted()).thenReturn(true);
        return schedule;
    }

    private Schedule mockExecuted() {
        final var schedule = mock(Schedule.class);
        when(schedule.executed()).thenReturn(true);
        return schedule;
    }

    private void setUpMocks() {
        storeFactory = mock(StoreFactory.class);
        store = mock(WritableScheduleStoreImpl.class);
        cleanupStoreFactory = () -> storeFactory;
        scheduleService = new ScheduleServiceImpl();
        when(storeFactory.writableStore(WritableScheduleStoreImpl.class)).thenReturn(store);
    }
}
