// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.workflows.handle.stack;

import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_STORAGE;
import static com.hedera.hapi.node.base.HederaFunctionality.ATOMIC_BATCH;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_SIGN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.ReversingBehavior.REVERSIBLE;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.SignedTxCustomizer.NOOP_SIGNED_TX_CUSTOMIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.trace.ContractSlotUsage;
import com.hedera.hapi.block.stream.trace.EvmTraceData;
import com.hedera.hapi.block.stream.trace.SlotRead;
import com.hedera.hapi.block.stream.trace.WrittenSlotKeys;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.ImmediateStateChangeListener;
import com.hedera.node.app.service.contract.impl.records.ContractOperationStreamBuilder;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.workflows.handle.record.TraceDataSizeLimiter;
import com.hedera.node.config.types.StreamMode;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.State;
import com.swirlds.state.spi.KVChangeListener;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ScheduledEvmTraceTest {
    private static final String SERVICE = "ContractService";
    private static final int STORAGE_ID = STATE_ID_STORAGE.protoOrdinal();
    private static final TransactionID TXN_ID = TransactionID.newBuilder()
            .accountID(AccountID.newBuilder().accountNum(1031))
            .transactionValidStart(new Timestamp(1_792_765_247, 561_237_597))
            .build();
    private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_792_765_252, 996_259_408);
    private static final SlotKey POINTER_ONLY = key(1037, 3);

    @ParameterizedTest
    @MethodSource("schedulingModes")
    void indexesScheduledWritesAgainstRootCommit(final StreamMode mode, final HederaFunctionality schedulingFunction) {
        final var fixture = fixture(mode, schedulingFunction);
        final var scheduled = scheduledIn(fixture.root(), mode);
        final var hashedSlot = new SlotKey(
                contract(1035), Bytes.fromHex("4a7203f705e51df4a56139a9b86b4a0970bed7ab7a3446eaffe2289989c1645c"));
        final var writes = Map.of(
                key(1035, 1),
                word(2),
                hashedSlot,
                word(2),
                key(1037, 1),
                word(2),
                key(1037, 2),
                word(102),
                key(1041, 0),
                word(101));
        final var usages = List.of(
                usage(1035, read(word(1), 1), read(hashedSlot.key(), 0)),
                usage(1037, read(Bytes.EMPTY, 1035), read(word(1), 1), read(word(2), 101)),
                usage(1041, read(word(0), 0)));
        trace(scheduled, usages, writes.keySet());
        writes.forEach((key, value) -> storage(scheduled).put(key, new SlotValue(value, Bytes.EMPTY, Bytes.EMPTY)));
        // Storage-list maintenance changes a pointer without changing this slot's logical value.
        storage(scheduled).put(POINTER_ONLY, new SlotValue(word(7), word(1), Bytes.EMPTY));
        scheduled.commitFullStack();
        fixture.root().commitFullStack();

        final var changes = fixture.root().getBaseBuilder(StreamBuilder.class).getStateChanges();
        assertThat(changes).hasSize(6);
        assertThat(changes.stream().filter(c -> c.mapUpdateOrThrow().identical()))
                .singleElement()
                .satisfies(c -> assertThat(c.mapUpdateOrThrow().keyOrThrow().slotKeyKeyOrThrow())
                        .isEqualTo(POINTER_ONLY));
        assertThat(scheduled.getBaseBuilder(StreamBuilder.class).getStateChanges())
                .isEmpty();
        final var items = items(fixture.root());
        assertThat(items.stream().filter(BlockItem::hasStateChanges)).singleElement();
        final var actual = traces(items).getFirst().contractSlotUsages();
        assertThat(actual).hasSize(3);
        for (int i = 0; i < usages.size(); i++) {
            final var expectedUsage = usages.get(i);
            final var actualUsage = actual.get(i);
            assertThat(actualUsage.hasWrittenSlotKeys()).isFalse();
            assertThat(actualUsage.slotReads())
                    .hasSize(expectedUsage.slotReads().size());
            final var writtenKeys = changes.stream()
                    .filter(c -> !c.mapUpdateOrThrow().identical())
                    .map(c -> c.mapUpdateOrThrow().keyOrThrow().slotKeyKeyOrThrow())
                    .filter(k -> k.contractID().equals(expectedUsage.contractId()))
                    .map(SlotKey::key)
                    .toList();
            for (int j = 0; j < expectedUsage.slotReads().size(); j++) {
                final var expectedRead = expectedUsage.slotReads().get(j);
                final var actualRead = actualUsage.slotReads().get(j);
                assertThat(actualRead.readValue()).isEqualTo(expectedRead.readValue());
                if (expectedRead.keyOrThrow().equals(Bytes.EMPTY)) {
                    assertThat(actualRead).isEqualTo(expectedRead);
                } else {
                    assertThat(actualRead.hasIndex()).isTrue();
                    assertThat(writtenKeys.get(actualRead.indexOrThrow())).isEqualTo(expectedRead.keyOrThrow());
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = StreamMode.class,
            names = {"BLOCKS", "BOTH"})
    void indexesDeletedSlots(final StreamMode mode) {
        final var fixture = fixture(mode, SCHEDULE_SIGN);
        final var scheduled = scheduledIn(fixture.root(), mode);
        trace(scheduled, List.of(usage(1037, read(POINTER_ONLY.key(), 7))), Set.of(POINTER_ONLY));
        storage(scheduled).remove(POINTER_ONLY);
        scheduled.commitFullStack();
        fixture.root().commitFullStack();

        assertThat(fixture.root().getBaseBuilder(StreamBuilder.class).getStateChanges())
                .singleElement()
                .satisfies(c -> assertThat(c.mapDeleteOrThrow().keyOrThrow().slotKeyKeyOrThrow())
                        .isEqualTo(POINTER_ONLY));
        final var read = traces(items(fixture.root()))
                .getFirst()
                .contractSlotUsages()
                .getFirst()
                .slotReads()
                .getFirst();
        assertThat(read.indexOrThrow()).isZero();
        assertThat(read.readValue()).isEqualTo(minimal(7));
    }

    @ParameterizedTest
    @EnumSource(
            value = StreamMode.class,
            names = {"BLOCKS", "BOTH"})
    void revertedScheduledWritesCannotReferenceSurvivingWrites(final StreamMode mode) {
        final var fixture = fixture(mode, SCHEDULE_CREATE);
        final var writtenKey = key(1037, 1);
        final var successful = scheduledIn(fixture.root(), mode);
        trace(successful, List.of(usage(1037, read(word(1), 0))), Set.of(writtenKey));
        storage(successful).put(writtenKey, new SlotValue(word(2), Bytes.EMPTY, Bytes.EMPTY));
        successful.commitFullStack();
        final var reverted = scheduledIn(fixture.root(), mode);
        trace(reverted, List.of(usage(1037, read(word(1), 2))), Set.of(writtenKey));
        storage(reverted).put(writtenKey, new SlotValue(word(3), Bytes.EMPTY, Bytes.EMPTY));
        reverted.rollbackFullStack();
        fixture.root().commitFullStack();

        assertThat(reverted.getBaseBuilder(StreamBuilder.class).status()).isEqualTo(REVERTED_SUCCESS);
        final var evmTraces = traces(items(fixture.root()));
        assertThat(evmTraces).hasSize(2);
        assertThat(evmTraces
                        .getFirst()
                        .contractSlotUsages()
                        .getFirst()
                        .slotReads()
                        .getFirst()
                        .hasIndex())
                .isTrue();
        final var revertedRead =
                evmTraces.getLast().contractSlotUsages().getFirst().slotReads().getFirst();
        assertThat(revertedRead.hasKey()).isTrue();
        assertThat(revertedRead.keyOrThrow()).isEqualTo(minimal(1));
        assertThat(revertedRead.readValue()).isEqualTo(minimal(2));
    }

    @ParameterizedTest
    @EnumSource(
            value = StreamMode.class,
            names = {"BLOCKS", "BOTH"})
    void committedAndRevertedPredicatesDoNotLeakIntoNextCommit(final StreamMode mode) {
        final var fixture = fixture(mode, SCHEDULE_CREATE);
        final var previous = scheduledIn(fixture.root(), mode);
        trace(previous, List.of(), Set.of(POINTER_ONLY));
        storage(previous).put(POINTER_ONLY, new SlotValue(word(8), Bytes.EMPTY, Bytes.EMPTY));
        previous.commitFullStack();
        fixture.root().commitFullStack();

        final var reverted = scheduledIn(fixture.root(), mode);
        trace(reverted, List.of(), Set.of(POINTER_ONLY));
        storage(reverted).put(POINTER_ONLY, new SlotValue(word(9), Bytes.EMPTY, Bytes.EMPTY));
        reverted.rollbackFullStack();
        final var current = scheduledIn(fixture.root(), mode);
        trace(current, List.of(), Set.of());
        storage(current).put(POINTER_ONLY, new SlotValue(word(8), word(1), Bytes.EMPTY));
        current.commitFullStack();
        fixture.root().commitFullStack();

        assertThat(fixture.root().getBaseBuilder(StreamBuilder.class).getStateChanges())
                .hasSize(2)
                .last()
                .satisfies(c -> assertThat(c.mapUpdateOrThrow().identical()).isTrue());
    }

    @ParameterizedTest
    @EnumSource(
            value = StreamMode.class,
            names = {"BLOCKS", "BOTH"})
    void independentlyExecutedScheduledEvmUsesItsOwnChanges(final StreamMode mode) {
        final var fixture = fixture(mode, CONTRACT_CALL);
        fixture.root()
                .getBaseBuilder(StreamBuilder.class)
                .transactionID(TXN_ID.copyBuilder().scheduled(true).build());
        trace(fixture.root(), List.of(usage(1037, read(word(1), 0))), Set.of(key(1037, 1)));
        storage(fixture.root()).put(key(1037, 1), new SlotValue(word(2), Bytes.EMPTY, Bytes.EMPTY));
        storage(fixture.root()).put(POINTER_ONLY, new SlotValue(word(7), word(1), Bytes.EMPTY));
        fixture.root().commitFullStack();

        assertThat(fixture.root().getBaseBuilder(StreamBuilder.class).getStateChanges().stream()
                        .filter(c -> c.mapUpdateOrThrow().identical()))
                .hasSize(1);
        assertThat(traces(items(fixture.root()))
                        .getFirst()
                        .contractSlotUsages()
                        .getFirst()
                        .slotReads()
                        .getFirst()
                        .indexOrThrow())
                .isZero();
    }

    @ParameterizedTest
    @MethodSource("explicitWriteModes")
    void explicitWritesAreNotMarkedIdenticalByAnotherDispatch(
            final StreamMode mode, final HederaFunctionality rootFunction) {
        final var fixture = fixture(mode, rootFunction);
        final var implicit = scheduledIn(fixture.root(), mode);
        trace(implicit, List.of(), Set.of());
        implicit.commitFullStack();
        final var explicit = scheduledIn(fixture.root(), mode);
        explicit.getBaseBuilder(ContractOperationStreamBuilder.class)
                .addContractSlotUsages(List.of(new ContractSlotUsage(
                        contract(1037),
                        new WrittenSlotKeys(List.of(minimal(1))),
                        List.of(SlotRead.newBuilder()
                                .index(0)
                                .readValue(minimal(1))
                                .build()))));
        storage(explicit).put(key(1037, 1), new SlotValue(word(2), Bytes.EMPTY, Bytes.EMPTY));
        explicit.commitFullStack();
        fixture.root().commitFullStack();

        assertThat(fixture.root().getBaseBuilder(StreamBuilder.class).getStateChanges())
                .singleElement()
                .satisfies(c -> assertThat(c.mapUpdateOrThrow().identical()).isFalse());
        final var usage =
                traces(items(fixture.root())).getLast().contractSlotUsages().getFirst();
        assertThat(usage.writtenSlotKeysOrThrow().keys()).containsExactly(minimal(1));
        assertThat(usage.slotReads().getFirst().indexOrThrow()).isZero();
    }

    private static Stream<Arguments> schedulingModes() {
        return Stream.of(StreamMode.BLOCKS, StreamMode.BOTH).flatMap(mode -> Stream.of(SCHEDULE_CREATE, SCHEDULE_SIGN)
                .map(function -> Arguments.of(mode, function)));
    }

    private static Stream<Arguments> explicitWriteModes() {
        return Stream.of(StreamMode.BLOCKS, StreamMode.BOTH)
                .flatMap(mode -> Stream.of(SCHEDULE_CREATE, SCHEDULE_SIGN, ATOMIC_BATCH)
                        .map(function -> Arguments.of(mode, function)));
    }

    private record Fixture(SavepointStackImpl root) {}

    private static Fixture fixture(final StreamMode mode, final HederaFunctionality function) {
        final var listener = new ImmediateStateChangeListener();
        final var state = mock(State.class);
        final var kv = new MapWritableKVState<>(
                STORAGE_ID,
                "STORAGE",
                new HashMap<>(Map.of(POINTER_ONLY, new SlotValue(word(7), Bytes.EMPTY, Bytes.EMPTY))));
        kv.registerListener(new KVChangeListener<SlotKey, SlotValue>() {
            @Override
            public void mapUpdateChange(final SlotKey key, final SlotValue value) {
                listener.mapUpdateChange(STORAGE_ID, key, value);
            }

            @Override
            public void mapDeleteChange(final SlotKey key) {
                listener.mapDeleteChange(STORAGE_ID, key);
            }
        });
        final var states = MapWritableStates.builder().state(kv).build();
        when(state.getWritableStates(SERVICE)).thenReturn(states);
        when(state.getReadableStates(SERVICE)).thenReturn(states);
        final var root = SavepointStackImpl.newRootStack(
                state, 3, 50, mock(BoundaryStateChangeListener.class), listener, mode, TraceDataSizeLimiter.NO_LIMIT);
        initialize(root.getBaseBuilder(StreamBuilder.class), function, TXN_ID);
        return new Fixture(root);
    }

    private static SavepointStackImpl scheduledIn(final SavepointStackImpl root, final StreamMode mode) {
        final var child =
                SavepointStackImpl.newChildStack(root, REVERSIBLE, SCHEDULED, NOOP_SIGNED_TX_CUSTOMIZER, mode);
        initialize(
                child.getBaseBuilder(StreamBuilder.class),
                CONTRACT_CALL,
                TXN_ID.copyBuilder().scheduled(true).build());
        return child;
    }

    private static void initialize(
            final StreamBuilder builder, final HederaFunctionality function, final TransactionID id) {
        builder.signedTx(SignedTransaction.DEFAULT)
                .status(SUCCESS)
                .exchangeRate(ExchangeRateSet.DEFAULT)
                .functionality(function)
                .transactionID(id);
    }

    private static void trace(
            final SavepointStackImpl stack, final List<ContractSlotUsage> usages, final Set<SlotKey> changedKeys) {
        final var builder = stack.getBaseBuilder(ContractOperationStreamBuilder.class);
        builder.addContractSlotUsages(usages);
        builder.testForIdenticalKeys(key -> key instanceof SlotKey slotKey && !changedKeys.contains(slotKey));
    }

    private static WritableKVState<SlotKey, SlotValue> storage(final SavepointStackImpl stack) {
        return stack.getWritableStates(SERVICE).get(STORAGE_ID);
    }

    private static List<BlockItem> items(final SavepointStackImpl root) {
        return root.buildHandleOutput(CONSENSUS_TIME, ExchangeRateSet.DEFAULT)
                .blockRecordSourceOrThrow()
                .blockItems();
    }

    private static List<EvmTraceData> traces(final List<BlockItem> items) {
        return items.stream()
                .filter(BlockItem::hasTraceData)
                .map(i -> i.traceDataOrThrow().evmTraceDataOrThrow())
                .toList();
    }

    private static ContractSlotUsage usage(final long contractNum, final SlotRead... reads) {
        return new ContractSlotUsage(contract(contractNum), null, List.of(reads));
    }

    private static SlotRead read(final Bytes key, final long value) {
        return SlotRead.newBuilder().key(key).readValue(minimal(value)).build();
    }

    private static SlotKey key(final long contractNum, final long slot) {
        return new SlotKey(contract(contractNum), word(slot));
    }

    private static ContractID contract(final long number) {
        return ContractID.newBuilder().contractNum(number).build();
    }

    private static Bytes word(final long value) {
        return Bytes.fromHex(String.format("%064x", value));
    }

    private static Bytes minimal(final long value) {
        if (value == 0) {
            return Bytes.EMPTY;
        }
        final var hex = Long.toHexString(value);
        return Bytes.fromHex(hex.length() % 2 == 0 ? hex : "0" + hex);
    }
}
