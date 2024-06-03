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

import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_EQUALITY_KEY;
import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_EXPIRY_SEC_KEY;
import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_ID_KEY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.StateDefinition;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import com.swirlds.state.spi.WritableStates;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class InitialModServiceScheduleSchemaTest {

    private static final SemanticVersion VERSION_123 = new SemanticVersion(1, 2, 3, "pre", "build");

    private InitialModServiceScheduleSchema subject;

    @BeforeEach
    void setUp() {
        subject = new InitialModServiceScheduleSchema(VERSION_123);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void constructorNullArgThrows() {
        Assertions.assertThatThrownBy(() -> new InitialModServiceScheduleSchema(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorHappyPath() {
        // Instance created in setup
        Assertions.assertThat(subject.getVersion()).isEqualTo(VERSION_123);
    }

    @Test
    void statesToCreateIsCorrect() {
        var sortedResult = subject.statesToCreate().stream()
                .sorted(Comparator.comparing(StateDefinition::stateKey))
                .toList();

        final var stateDef1 = sortedResult.getFirst();
        Assertions.assertThat(stateDef1.stateKey()).isEqualTo(SCHEDULES_BY_EQUALITY_KEY);
        Assertions.assertThat(stateDef1.keyCodec()).isEqualTo(ProtoBytes.PROTOBUF);
        Assertions.assertThat(stateDef1.valueCodec()).isEqualTo(ScheduleList.PROTOBUF);
        final var stateDef2 = sortedResult.get(1);
        Assertions.assertThat(stateDef2.stateKey()).isEqualTo(SCHEDULES_BY_EXPIRY_SEC_KEY);
        Assertions.assertThat(stateDef2.keyCodec()).isEqualTo(ProtoLong.PROTOBUF);
        Assertions.assertThat(stateDef2.valueCodec()).isEqualTo(ScheduleList.PROTOBUF);
        final var stateDef3 = sortedResult.get(2);
        Assertions.assertThat(stateDef3.stateKey()).isEqualTo(SCHEDULES_BY_ID_KEY);
        Assertions.assertThat(stateDef3.keyCodec()).isEqualTo(ScheduleID.PROTOBUF);
        Assertions.assertThat(stateDef3.valueCodec()).isEqualTo(Schedule.PROTOBUF);
    }

    @Test
    void migrateWithoutFromStateDoesNothing() {
        // ensure the 'from' state is null
        subject.setFs(null);

        final var ctx = mock(MigrationContext.class);
        Assertions.assertThatNoException().isThrownBy(() -> subject.migrate(ctx));
        verifyNoInteractions(ctx);
    }

    @Test
    void migrateWithEmptyFromStateMakesNoChanges() {
        // set up the empty 'from' state
        subject.setFs(new MerkleScheduledTransactions());

        // set up the 'to' state
        final var writableStates = newEmptySchedulesWritableStates();
        final var ctx = newMockCtx(writableStates);

        subject.migrate(ctx);

        verifyEmptyById(writableStates);
        verifyEmptyByExpiry(writableStates);
        verifyEmptyByEquality(writableStates);
    }

    @Test
    void migrateScheduleById() {
        // set up the 'byId' portion of the 'from' state
        final var expectedScheduleId = 5;
        final var byId = new MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue>();
        byId.put(
                new EntityNumVirtualKey(expectedScheduleId),
                ScheduleVirtualValue.from(new byte[0], Instant.now().getEpochSecond()));

        // mock the other pieces of the 'from' state
        final var state = mock(MerkleScheduledTransactionsState.class);
        final var byEquality = mock(MerkleMap.class);
        final var byExpiry = mock(MerkleMap.class);
        subject.setFs(new MerkleScheduledTransactions(List.of(state, byId, byExpiry, byEquality)));

        // set up the 'to' state
        final var writableStates = newEmptySchedulesWritableStates();
        final var ctx = newMockCtx(writableStates);

        subject.migrate(ctx);

        verifyNonEmptyById(writableStates);
        final long actualKey = writableStates
                .<ScheduleID, Schedule>get(SCHEDULES_BY_ID_KEY)
                .keys()
                .next()
                .scheduleNum();
        assertThat(actualKey).isEqualTo(expectedScheduleId);
        verifyEmptyByExpiry(writableStates);
        verifyEmptyByEquality(writableStates);
    }

    @Test
    void migrateScheduleByExpiry() {
        final var currentTime = Instant.now().getEpochSecond();

        // set up the 'byExpiry' portion of the 'from' state
        final var byExpiry = new MerkleMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue>();
        final var scheduleValue = new ScheduleSecondVirtualValue();
        scheduleValue.add(RichInstant.fromJava(Instant.now()), new PartialLongListFixture(List.of(3L)));
        byExpiry.put(new SecondSinceEpocVirtualKey(currentTime), scheduleValue);

        // mock the other pieces of the 'from' state
        final var state = mock(MerkleScheduledTransactionsState.class);
        final var byEquality = mock(MerkleMap.class);
        final var byId = mock(MerkleMap.class);
        subject.setFs(new MerkleScheduledTransactions(List.of(state, byId, byExpiry, byEquality)));

        // set up the 'to' state
        final var writableStates = newEmptySchedulesWritableStates();
        final var ctx = newMockCtx(writableStates);

        subject.migrate(ctx);

        verifyNonEmptyByExpiry(writableStates);
        assertThat(writableStates.get(SCHEDULES_BY_EXPIRY_SEC_KEY).get(new ProtoLong(currentTime)))
                .isNotNull();
        verifyEmptyById(writableStates);
        verifyEmptyByEquality(writableStates);
    }

    @Test
    void migrateScheduleByEquality() {
        final var currentTime = Instant.now().getEpochSecond();

        // Set up the 'byId' portion of the 'from' state (needed for the 'byEquality' piece)
        final var byId = new MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue>();
        byId.put(new EntityNumVirtualKey(6), ScheduleVirtualValue.from(new byte[0], currentTime));

        // set up the 'byEquality' portion of the 'from' state
        final var byEquality = new MerkleMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue>();
        byEquality.put(new ScheduleEqualityVirtualKey(), new ScheduleEqualityVirtualValue(Map.of("ignoredKey", 6L)));

        // mock the other pieces of the 'from' state
        final var state = mock(MerkleScheduledTransactionsState.class);
        final var byExpiry = mock(MerkleMap.class);
        subject.setFs(new MerkleScheduledTransactions(List.of(state, byId, byExpiry, byEquality)));

        // set up the 'to' state
        final var writableStates = newEmptySchedulesWritableStates();
        final var ctx = newMockCtx(writableStates);

        subject.migrate(ctx);

        verifyNonEmptyById(writableStates);
        verifyNonEmptyByEquality(writableStates);
        verifyEmptyByExpiry(writableStates);
    }

    @Test
    void statesToRemoveIsEmpty() {
        Assertions.assertThat(subject.statesToRemove()).isEmpty();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void restartNullArgThrows() {
        Assertions.assertThatThrownBy(() -> subject.restart(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void restartHappyPath() {
        Assertions.assertThatNoException().isThrownBy(() -> subject.restart(mock(MigrationContext.class)));
    }

    private WritableStates newEmptySchedulesWritableStates() {
        final var writableStates = MapWritableStates.builder()
                .state(new MapWritableKVState<>(SCHEDULES_BY_ID_KEY))
                .state(new MapWritableKVState<>(SCHEDULES_BY_EQUALITY_KEY))
                .state(new MapWritableKVState<>(SCHEDULES_BY_EXPIRY_SEC_KEY))
                .build();
        verifyEmptyById(writableStates);
        verifyEmptyByExpiry(writableStates);
        verifyEmptyByEquality(writableStates);

        return writableStates;
    }

    private MigrationContext newMockCtx(final WritableStates ws) {
        final var ctx = mock(MigrationContext.class);
        BDDMockito.given(ctx.newStates()).willReturn(ws);
        return ctx;
    }

    private void verifyEmptyById(final WritableStates actual) {
        verifyEmptyScheduleState(SCHEDULES_BY_ID_KEY, actual);
    }

    private void verifyEmptyByExpiry(final WritableStates actual) {
        verifyEmptyScheduleState(SCHEDULES_BY_EXPIRY_SEC_KEY, actual);
    }

    private void verifyEmptyByEquality(final WritableStates actual) {
        verifyEmptyScheduleState(SCHEDULES_BY_EQUALITY_KEY, actual);
    }

    private void verifyEmptyScheduleState(final String scheduleStateKey, final WritableStates actual) {
        assertThat(actual.get(scheduleStateKey).size()).isZero();
    }

    private void verifyNonEmptyById(final WritableStates actual) {
        verifyNonEmptyScheduleState(SCHEDULES_BY_ID_KEY, actual);
    }

    private void verifyNonEmptyByExpiry(final WritableStates actual) {
        verifyNonEmptyScheduleState(SCHEDULES_BY_EXPIRY_SEC_KEY, actual);
    }

    private void verifyNonEmptyByEquality(final WritableStates actual) {
        verifyNonEmptyScheduleState(SCHEDULES_BY_EQUALITY_KEY, actual);
    }

    private void verifyNonEmptyScheduleState(final String scheduleStateKey, final WritableStates actual) {
        // Note: we're not worried so much about a correct entity migration; just that the entity migration happened
        final var scheduleKey = actual.get(scheduleStateKey).keys().next();
        assertThat(scheduleKey).isNotNull();
        final var scheduleVal = actual.get(scheduleStateKey).get(scheduleKey);
        assertThat(scheduleVal).isNotNull();
        assertThat(actual.get(scheduleStateKey).size()).isEqualTo(1);
    }
}
