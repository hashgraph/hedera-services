// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support.validators.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.hedera.hapi.block.stream.output.*;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.hapi.utils.blocks.StateChangeDeltas;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.BinaryState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BinaryStateChangeParserTest {
    private static final int ID = 9999;
    private static final MapChangeKey KEY =
            MapChangeKey.newBuilder().accountIdKey(AccountID.DEFAULT).build();

    @Test
    void fullBlockInfoUsesTheOriginalFieldOneWithoutReadingABaseline() {
        final var state = mock(BinaryState.class);
        // StateChanges -> state_id=9999 -> singleton_update -> block_info_value (field 1), empty.
        BinaryStateChangeParser.applyStateChanges(state, Bytes.fromHex("1207088f4e22020a00"));
        verify(state).updateSingleton(ID, Bytes.EMPTY);
        verify(state, never()).getSingleton(anyInt());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "120e088f4e22094a0412020802c00101",
                "1212088f4e220d4a0412020802c00101ca010101",
                "1211088f4e220c4a0412020802c00101c80101"
            })
    void singletonGoldenWireFixturesUseMetadataFields24And25(final String wire) {
        final var prior = ThrottleUsageSnapshots.newBuilder()
                .tpsThrottles(List.of(new ThrottleUsageSnapshot(7, null)))
                .gasThrottle(new ThrottleUsageSnapshot(1, null))
                .build();
        final var state = mock(BinaryState.class);
        when(state.getSingleton(ID)).thenReturn(ThrottleUsageSnapshots.PROTOBUF.toBytes(prior));
        // The first fixture has no clears; the others clear tps_throttles via packed/unpacked field 25.
        final var expected = prior.copyBuilder().gasThrottle(new ThrottleUsageSnapshot(2, null));
        if (!wire.endsWith("c00101")) expected.tpsThrottles(List.of());
        BinaryStateChangeParser.applyStateChanges(state, Bytes.fromHex(wire));
        verify(state).getSingleton(ID);
        verify(state).updateSingleton(ID, ThrottleUsageSnapshots.PROTOBUF.toBytes(expected.build()));
    }

    @Test
    void everyMapAndSingletonPayloadReplaysThroughTheBinaryStateApi() {
        for (var kind : MapChangeValue.ValueChoiceOneOfType.values()) {
            if (kind.protoOrdinal() <= 0) continue;
            final var type = StateChangeDeltas.mapType(kind.protoOrdinal());
            final var value = type.wireValue(type.defaultValue());
            final var state = mock(BinaryState.class);
            when(state.getKv(ID, Bytes.EMPTY)).thenReturn(Bytes.EMPTY);
            final var update = MapUpdateChange.newBuilder()
                    .key(KEY)
                    .value(new MapChangeValue(new OneOf<>(kind, value)))
                    .partial(true)
                    .build();
            apply(state, StateChange.newBuilder().stateId(ID).mapUpdate(update).build());
            verify(state).updateKv(ID, Bytes.EMPTY, Bytes.EMPTY);
        }
        for (var kind : SingletonUpdateChange.NewValueOneOfType.values()) {
            if (kind.protoOrdinal() <= 0) continue;
            final var type = StateChangeDeltas.singletonType(kind.protoOrdinal());
            final var state = mock(BinaryState.class);
            when(state.getSingleton(ID)).thenReturn(Bytes.EMPTY);
            final var update =
                    new SingletonUpdateChange(new OneOf<>(kind, type.wireValue(type.defaultValue())), true, List.of());
            apply(
                    state,
                    StateChange.newBuilder().stateId(ID).singletonUpdate(update).build());
            verify(state).updateSingleton(ID, Bytes.EMPTY);
        }
    }

    @Test
    void replacesClearsAndSwitchesOneofUsingTheStoredBaseline() {
        final var old = Account.newBuilder()
                .memo("m".repeat(100))
                .tinybarBalance(5)
                .stakedNodeId(7)
                .build();
        final var next = old.copyBuilder()
                .tinybarBalance(0)
                .stakedAccountId(AccountID.DEFAULT)
                .build();
        final var full = MapUpdateChange.newBuilder()
                .key(KEY)
                .value(MapChangeValue.newBuilder().accountValue(next))
                .build();
        final var partial = StateChangeDeltas.encodeMap(ID, old, full);
        assertThat(partial.partial()).isTrue();
        assertThat(partial.clearedFields()).containsExactly(5, 11);
        final var state = mock(BinaryState.class);
        when(state.getKv(ID, Bytes.EMPTY)).thenReturn(Account.PROTOBUF.toBytes(old));
        apply(state, StateChange.newBuilder().stateId(ID).mapUpdate(partial).build());
        verify(state).updateKv(ID, Bytes.EMPTY, Account.PROTOBUF.toBytes(next));
    }

    @Test
    void fullUpdatesAvoidPriorReadsAndMissingPartialBaselineFails() {
        final var full = MapUpdateChange.newBuilder()
                .key(KEY)
                .value(MapChangeValue.newBuilder().accountValue(Account.DEFAULT))
                .build();
        final var state = mock(BinaryState.class);
        apply(state, StateChange.newBuilder().stateId(ID).mapUpdate(full).build());
        verify(state, never()).getKv(anyInt(), any());
        assertThatThrownBy(() -> apply(
                        state,
                        StateChange.newBuilder()
                                .stateId(ID)
                                .mapUpdate(full.copyBuilder().partial(true))
                                .build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateAndInvalidClearsCannotModifyState() {
        final var state = mock(BinaryState.class);
        final var prior = Account.newBuilder().tinybarBalance(5).build();
        when(state.getKv(ID, Bytes.EMPTY)).thenReturn(Account.PROTOBUF.toBytes(prior));
        for (var clears : List.of(List.of(5, 5), List.of(0), List.of(536870912))) {
            var update = MapUpdateChange.newBuilder()
                    .key(KEY)
                    .value(MapChangeValue.newBuilder().accountValue(Account.DEFAULT))
                    .partial(true)
                    .clearedFields(clears)
                    .build();
            assertThatThrownBy(() -> apply(
                            state,
                            StateChange.newBuilder()
                                    .stateId(ID)
                                    .mapUpdate(update)
                                    .build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(state, never()).updateKv(anyInt(), any(), any());
    }

    private static void apply(BinaryState state, StateChange change) {
        BinaryStateChangeParser.applyStateChanges(
                state,
                StateChanges.PROTOBUF.toBytes(
                        StateChanges.newBuilder().stateChanges(change).build()));
    }
}
