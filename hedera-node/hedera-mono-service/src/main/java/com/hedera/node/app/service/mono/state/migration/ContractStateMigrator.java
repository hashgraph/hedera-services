/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.utils.MiscUtils.withLoggedDuration;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Migrate mono service's contract store (contract slots, i.e. per-contract K/V pairs) to modular service's contract store.
 */
public class ContractStateMigrator {
    private static final Logger log = LogManager.getLogger(ContractStateMigrator.class);

    /**
     * The actual migration routine, called by the thing that migrates all the stores
     */
    public static void migrateFromContractStoreVirtualMap(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull WritableKVState<SlotKey, SlotValue> toState) {
        requireNonNull(fromState);
        requireNonNull(toState);

        final var validationsFailed = new ArrayList<String>();
        final var migrator = new ContractStateMigrator(fromState, toState);
        migrator.doit(validationsFailed);
        if (!validationsFailed.isEmpty()) {
            final var formattedFailedValidations =
                    validationsFailed.stream().collect(Collectors.joining("\n   ***", "   ***", "\n"));
            log.error("{}: transform validations failed:\n{}", LOG_CAPTION, formattedFailedValidations);
            throw new BrokenTransformationException(LOG_CAPTION + ": transformation didn't complete successfully");
        }
    }

    static final int THREAD_COUNT = 8; // TODO: is there a configuration option good for this?
    static final int MAXIMUM_SLOTS_IN_FLIGHT = 1_000_000; // This holds both mainnet and testnet (at this time, 2023-11)
    static final int DISTINCT_CONTRACTS_ESTIMATE = 10_000;
    static final String LOG_CAPTION = "contract-store mono-to-modular migration";

    static final ContractSlotLocal SENTINEL = new ContractSlotLocal(0L, new int[8], new byte[32], null, null);

    final VirtualMapLike<ContractKey, IterableContractValue> fromState;
    final WritableKVState<SlotKey, SlotValue> toState;

    final Counters counters = Counters.create();

    ContractStateMigrator(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull WritableKVState<SlotKey, SlotValue> toState) {
        requireNonNull(fromState);
        requireNonNull(toState);
        this.fromState = fromState;
        this.toState = toState;
    }

    /**
     * Do the transform from mono-service's contract store to modular-service's contract store, and do some
     * post-transforms sanity checking.
     */
    void doit(@NonNull final List<String> validationsFailed) {

        final var completedProcesses = new NonAtomicReference<EnumSet<CompletedProcesses>>();

        withLoggedDuration(() -> completedProcesses.set(transformStore()), log, LOG_CAPTION + ": complete transform");

        requireNonNull(completedProcesses.get(), "must have valid completed processes set at this point");

        // All that follows is validation that all processing completed and sanity checks pass

        if (completedProcesses.get().size()
                != EnumSet.allOf(CompletedProcesses.class).size()) {
            if (!completedProcesses.get().contains(CompletedProcesses.SOURCING))
                validationsFailed.add("Sourcing process didn't finish");
            if (!completedProcesses.get().contains(CompletedProcesses.SINKING))
                validationsFailed.add("Sinking process didn't finish");
        }

        final var fromSize = fromState.size();
        if (fromSize != counters.slotsSourced().get()
                || fromSize != counters.slotsSunk().get()
                || fromSize != toState.size()) {
            validationsFailed.add(
                    "counters of slots processed don't match: %d source size, %d #slots sourced, %d slots sunk, %d final destination size"
                            .formatted(
                                    fromSize,
                                    counters.slotsSourced().get(),
                                    counters.slotsSunk().get(),
                                    toState.size()));
        }

        final var nContracts = counters.contracts().get().size();
        if (nContracts != counters.nNullPrevs().get()
                || nContracts != counters.nNullNexts().get()) {
            validationsFailed.add(
                    "distinct contracts doesn't match #null prev links and/or #null next links: %d contract, %d null prevs, %d null nexts"
                            .formatted(
                                    nContracts,
                                    counters.nNullPrevs().get(),
                                    counters.nNullNexts().get()));
        }
    }

    /**
     * The intermediate representation of a contract slot (K/V pair, plus prev/next linked list references.
     */
    @SuppressWarnings("java:S6218") // should overload equals/hashcode  - but will never test for equality or hash this
    record ContractSlotLocal(long contractId, @NonNull int[] key, @NonNull byte[] value, int[] prev, int[] next) {
        ContractSlotLocal {
            requireNonNull(key);
            requireNonNull(value);
            validateArgument(key.length == 8, "wrong length key");
            validateArgument(value.length == 32, "wrong length value");
            if (prev != null) validateArgument(prev.length == 8, "wrong length prev link");
            if (next != null) validateArgument(next.length == 8, "wrong length next link");
        }

        ContractSlotLocal(@NonNull final ContractKey k, @NonNull final IterableContractValue v) {
            this(k.getContractId(), k.getKey(), v.getValue(), v.getExplicitPrevKey(), v.getExplicitNextKey());
        }
    }

    /**
     * Various counters used to perform final validations on the completed transform.
     *
     * The counters used by the sink process need not actually be atomic at this time, as the sink process is single
     * threaded.  But IMO it is more consistent and thus easier to read to treat _all_ of the counters the same.
     */
    record Counters(
            @NonNull AtomicInteger slotsSourced,
            @NonNull AtomicInteger slotsSunk,
            @NonNull AtomicReference<KeySetView<Long, Boolean>> contracts,
            @NonNull AtomicInteger nNullPrevs,
            @NonNull AtomicInteger nNullNexts) {
        @NonNull
        static Counters create() {
            return new Counters(
                    new AtomicInteger(),
                    new AtomicInteger(),
                    new AtomicReference<>(ConcurrentHashMap.newKeySet(DISTINCT_CONTRACTS_ESTIMATE)),
                    new AtomicInteger(),
                    new AtomicInteger());
        }

        void sourceOne() {
            slotsSourced.incrementAndGet();
        }

        void sinkOne() {
            slotsSunk.incrementAndGet();
        }

        void addContract(long cid) {
            contracts.get().add(cid);
        }

        void addNullPrev() {
            nNullPrevs.incrementAndGet();
        }

        void addNullNext() {
            nNullNexts.incrementAndGet();
        }
    }

    /** Indicates that the source or sink process processed all slots */
    enum CompletedProcesses {
        SOURCING,
        SINKING
    }

    /**
     *  Operates the source and sink processes to transform the mono-state ("from") into the modular-state ("to").
     *  */
    @NonNull
    EnumSet<CompletedProcesses> transformStore() {

        final var slotQueue = new ArrayBlockingQueue<ContractSlotLocal>(MAXIMUM_SLOTS_IN_FLIGHT);

        // Sinking and sourcing happen concurrently.  (Though sourcing is multithreaded, sinking is all on one thread.)
        // Consider: For debugging, don't create (and start) `processSlotQueue` until after sourcing is complete. Just
        // accumulate everything in the fromStore in the queue before sinking anything.

        CompletableFuture<Void> processSlotQueue =
                CompletableFuture.runAsync(() -> iterateOverQueuedSlots((slotQueue)));

        final var completedTasks = EnumSet.noneOf(CompletedProcesses.class);

        boolean didCompleteSourcing = iterateOverCurrentData(slotQueue::put);
        try {
            slotQueue.put(SENTINEL);
        } catch (InterruptedException ex) { // TODO: This InterruptedException can be ignored, I think; correct?
            log.error(LOG_CAPTION + ": interrupt when sourcing slots", ex);
            didCompleteSourcing = false;
        }
        if (didCompleteSourcing) completedTasks.add(CompletedProcesses.SOURCING);

        boolean didCompleteSinking = true;
        try {
            processSlotQueue.join();
        } catch (CompletionException cex) {
            final var ex = cex.getCause();
            log.error(LOG_CAPTION + ": interrupt when sinking slots", ex);
            didCompleteSinking = false;
        }
        if (didCompleteSinking) completedTasks.add(CompletedProcesses.SINKING);

        return completedTasks;
    }

    /**
     * Pull slots off the queue and add each one immediately to the modular-service state.  This is "sinking" the slots.
     *
     * This is single-threaded.  (But does operate concurrently with sourcing.)
     */
    @SuppressWarnings("java:S2142") // must rethrow InterruptedException - Sonar doesn't understand wrapping it to throw
    void iterateOverQueuedSlots(@NonNull final ArrayBlockingQueue<ContractSlotLocal> slotQueue) {
        requireNonNull(slotQueue);

        // TODO: Consider passing this an InterruptableSupplier, not the queue directly.  See how
        // `iterateOverCurrentData` takes a InterruptableConsumer.

        while (true) {
            final ContractSlotLocal slot;
            try {
                slot = slotQueue.take();
            } catch (InterruptedException e) {
                throw new CompletionException(e);
            }
            if (slot == SENTINEL) break;

            final var key = SlotKey.newBuilder()
                    .contractNumber(slot.contractId())
                    .key(bytesFromInts(slot.key()))
                    .build();
            final var value = SlotValue.newBuilder()
                    .value(Bytes.wrap(slot.value()))
                    .previousKey(bytesFromInts(slot.prev()))
                    .nextKey(bytesFromInts((slot.next())))
                    .build();
            toState.put(key, value);

            // Some counts to provide some validation
            counters.sinkOne();
            counters.addContract(slot.contractId());
            if (slot.prev() == null) counters.addNullPrev();
            if (slot.next() == null) counters.addNullNext();
        }
    }

    /**
     * Iterate over the incoming mono-service state, pushing slots (key + value) into the queue.  This is "sourcing"
     * the slots.
     *
     * This iteration operates with multiple threads simultaneously.
     */
    boolean iterateOverCurrentData(@NonNull final InterruptableConsumer<ContractSlotLocal> slotSink) {
        boolean didRunToCompletion = true;
        try {
            fromState.extractVirtualMapData(
                    getStaticThreadManager(),
                    entry -> {
                        final var contractKey = entry.left();
                        final var iterableContractValue = entry.right();
                        slotSink.accept(new ContractSlotLocal(contractKey, iterableContractValue));

                        // Some counts to provide some validation
                        counters.sourceOne();
                    },
                    THREAD_COUNT);
        } catch (final InterruptedException ex) {
            currentThread().interrupt();
            didRunToCompletion = false;
        }
        return didRunToCompletion;
    }

    //    @NonNull
    //    static WritableKVState<SlotKey, SlotValue> getNewContractStore() {
    //        return new InMemoryWritableKVState<SlotKey, SlotValue>();
    //    }

    /** Convert int[] to byte[] and then to Bytes. If argument is null then return value is null. */
    static Bytes bytesFromInts(final int[] ints) {
        if (ints == null) return null;

        // N.B.: `ByteBuffer.allocate` creates the right `byte[]`.  `asIntBuffer` will share it, so no extra copy
        // there.  Finally, `Bytes.wrap` will wrap it so no extra copy there either.
        final ByteBuffer buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(ints);
        return Bytes.wrap(buf.array());
    }

    /** Validate an argument by throwing `IllegalArgumentException` if the test fails */
    static void validateArgument(final boolean b, @NonNull final String msg) {
        if (!b) throw new IllegalArgumentException(msg);
    }

    static class BrokenTransformationException extends RuntimeException {

        public BrokenTransformationException(String message) {
            super(message);
        }
    }
}
