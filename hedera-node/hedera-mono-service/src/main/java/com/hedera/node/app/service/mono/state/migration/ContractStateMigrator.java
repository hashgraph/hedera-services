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
import com.hedera.node.app.service.mono.state.migration.internal.ContractMigrationValidationCounters;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.interrupt.InterruptableSupplier;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Migrate mono service's contract store (contract slots, i.e. per-contract K/V pairs) to modular service's contract store.
 *
 * Three static methods run the migrator:  Reading the entire contract store from `fromState` and writing the migrated
 * (transformed) slots to `toState`.  Caller is responsible for supplying the state to write to AND for committing it
 * after the transform returns.
 */
public class ContractStateMigrator {
    private static final Logger log = LogManager.getLogger(ContractStateMigrator.class);

    /** Given a `WritableKVState` for the contract store, makes a copy of it - thus flushing it to disk! - and then
     * returns the copy for further writing.
     */
    public interface StateFlusher extends UnaryOperator<WritableKVState<SlotKey, SlotValue>> {}

    public static Status migrateFromContractStoreVirtualMap(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull final WritableKVState<SlotKey, SlotValue> initialToState,
            @NonNull final StateFlusher stateFlusher) {
        return migrateFromContractStoreVirtualMap(
                fromState, initialToState, stateFlusher, new ArrayList<>(), false, DEFAULT_THREAD_COUNT);
    }

    @NonNull
    public static Status migrateFromContractStoreVirtualMap(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull final WritableKVState<SlotKey, SlotValue> initialToState,
            @NonNull final StateFlusher stateFlusher,
            @NonNull final List<String> validationFailures) {
        return migrateFromContractStoreVirtualMap(
                fromState, initialToState, stateFlusher, validationFailures, true, DEFAULT_THREAD_COUNT);
    }

    @NonNull
    public static Status migrateFromContractStoreVirtualMap(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull final WritableKVState<SlotKey, SlotValue> initialToState,
            @NonNull final StateFlusher stateFlusher,
            @NonNull final List<String> validationFailures,
            final boolean doFullValidation,
            final int threadCount) {
        requireNonNull(fromState);
        requireNonNull(initialToState);
        requireNonNull(stateFlusher);
        requireNonNull(validationFailures);

        validationFailures.clear();

        final var migrator = new ContractStateMigrator(fromState, initialToState, stateFlusher)
                .withValidation(doFullValidation)
                .withThreadCount(threadCount);

        final var status = migrator.doit(validationFailures);
        if (status != Status.SUCCESS) {
            final var formattedFailedValidations =
                    validationFailures.stream().collect(Collectors.joining("\n   ***", "   ***", "\n"));
            log.error("{}: transform validations failed:\n{}", LOG_CAPTION, formattedFailedValidations);
            throw new BrokenTransformationException(LOG_CAPTION + ": transformation didn't complete successfully");
        }
        return status;
    }

    /** A reasonable guess as to the number of threads to devote to the source tree traversal.
     *
     *  a) Don't wish to use a configuration option because I want to be able to use this class in test code or a CLI
     *  tool that doesn't necessarily have the services (or platform) configuration system (easily) available.
     *  b) And though `availableProcessors()` may in fact give different results during the _same_ JVM invocation: IDC.
     */
    static final int DEFAULT_THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() * 3 / 2);

    static final int MAXIMUM_SLOTS_IN_FLIGHT = 1_000_000; // This holds both mainnet and testnet (at this time, 2023-11)
    static final int EVM_WORD_WIDTH_IN_BYTES = 32;
    static final int EVM_WORD_WIDTH_IN_INTS = 8;
    static final String LOG_CAPTION = "contract-store mono-to-modular migration";

    private static final int COMMIT_STATE_EVERY_N_INSERTS =
            10_000; // VirtualMapConfig.preferredFlushQueueSize() but no access here

    final VirtualMapLike<ContractKey, IterableContractValue> fromState;
    WritableKVState<SlotKey, SlotValue> toState;
    final StateFlusher stateFlusher;

    boolean doFullValidation;
    int threadCount;
    final ContractMigrationValidationCounters counters;
    int nInsertionsDone = 0;

    ContractStateMigrator(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull final WritableKVState<SlotKey, SlotValue> initialToState,
            @NonNull final StateFlusher stateFlusher) {
        requireNonNull(fromState);
        requireNonNull(initialToState);
        requireNonNull(stateFlusher);

        this.fromState = fromState;
        this.toState = initialToState;
        this.stateFlusher = stateFlusher;
        this.threadCount = DEFAULT_THREAD_COUNT;
        this.doFullValidation = true;
        this.counters = ContractMigrationValidationCounters.create();
    }

    @NonNull
    ContractStateMigrator withValidation(final boolean doFullValidation) {
        this.doFullValidation = doFullValidation;
        return this;
    }

    @NonNull
    ContractStateMigrator withThreadCount(final int threadCount) {
        this.threadCount = threadCount;
        return this;
    }

    public enum Status {
        SUCCESS,
        VALIDATION_ERRORS,
        INCOMPLETE_TRAVERSAL
    }

    /**
     * Do the transform from mono-service's contract store to modular-service's contract store, and do some
     * post-transforms sanity checking.
     */
    @NonNull
    Status doit(@NonNull final List<String> validationsFailed) {

        final var completedProcesses = new NonAtomicReference<EnumSet<CompletedProcesses>>();

        withLoggedDuration(
                () -> completedProcesses.set(transformStore(doFullValidation)),
                log,
                LOG_CAPTION + ": complete transform");

        requireNonNull(completedProcesses.get(), "must have valid completed processes set at this point");

        return validateTransform(completedProcesses.get(), validationsFailed, doFullValidation);
    }

    /**
     * The intermediate representation of a contract slot (K/V pair, plus prev/next linked list references).
     */
    @SuppressWarnings("java:S6218") // should overload equals/hashcode  - but will never test for equality or hash this
    private record ContractSlotLocal(
            long contractId, @NonNull int[] key, @NonNull byte[] value, @Nullable int[] prev, @Nullable int[] next) {

        public static final ContractSlotLocal SENTINEL = new ContractSlotLocal(
                0L, new int[EVM_WORD_WIDTH_IN_INTS], new byte[EVM_WORD_WIDTH_IN_BYTES], null, null);

        private ContractSlotLocal {
            requireNonNull(key);
            requireNonNull(value);
            validateArgument(key.length == EVM_WORD_WIDTH_IN_INTS, "wrong length key");
            validateArgument(value.length == EVM_WORD_WIDTH_IN_BYTES, "wrong length value");
            if (prev != null) validateArgument(prev.length == EVM_WORD_WIDTH_IN_INTS, "wrong length prev link");
            if (next != null) validateArgument(next.length == EVM_WORD_WIDTH_IN_INTS, "wrong length next link");
        }

        public ContractSlotLocal(@NonNull final ContractKey k, @NonNull final IterableContractValue v) {
            this(k.getContractId(), k.getKey(), v.getValue(), v.getExplicitPrevKey(), v.getExplicitNextKey());
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
    EnumSet<CompletedProcesses> transformStore(final boolean doFullValidation) {

        final var slotQueue = new ArrayBlockingQueue<ContractSlotLocal>(MAXIMUM_SLOTS_IN_FLIGHT);

        // Sinking and sourcing happen concurrently.  (Though sourcing is multithreaded, sinking is all on one thread.)
        // Consider: For debugging, don't create (and start) `processSlotQueue` until after sourcing is complete. Just
        // accumulate everything in the fromStore in the queue before sinking anything.

        CompletableFuture<Void> processSlotQueue =
                CompletableFuture.runAsync(() -> iterateOverAllQueuedSlots(slotQueue::take, doFullValidation));

        final var completedTasks = EnumSet.noneOf(CompletedProcesses.class);

        boolean didCompleteSourcing = iterateOverAllCurrentData(slotQueue::put, doFullValidation);

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
    void iterateOverAllQueuedSlots(
            @NonNull final InterruptableSupplier<ContractSlotLocal> slotSource, final boolean doFullValidation) {
        requireNonNull(slotSource);

        while (true) {
            final ContractSlotLocal slot;
            try {
                slot = slotSource.get();
            } catch (InterruptedException e) {
                throw new CompletionException(e);
            }
            if (slot == ContractSlotLocal.SENTINEL) break;

            // The transform from the mono-service slot representation to the modular-service slot representation
            // is here:

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

            commitToStateIfNeeded();

            // Some counts to provide some validation
            if (doFullValidation) {
                counters.sinkOne();
                counters.addContract(key.contractNumber());
                if (value.previousKey() == Bytes.EMPTY) counters.addMissingPrev();
                else counters.xorAnotherLink(value.previousKey().toByteArray());
                if (value.nextKey() == Bytes.EMPTY) counters.addMissingNext();
                else counters.xorAnotherLink(value.nextKey().toByteArray());
            }
        }

        commitToStateNow();
    }

    // State needs to be committed every so many inserts so that it can get flushed to disk. We can't do it on a
    // `WritableKVState` so we defer to the caller.
    void commitToStateIfNeeded() {
        if (++nInsertionsDone % COMMIT_STATE_EVERY_N_INSERTS == 0) commitToStateNow();
    }

    void commitToStateNow() {
        toState = stateFlusher.apply(toState);
    }

    /**
     * Iterate over the incoming mono-service state, pushing slots (key + value) into the queue.  This is "sourcing"
     * the slots.
     *
     * This iteration operates with multiple threads simultaneously.
     */
    boolean iterateOverAllCurrentData(
            @NonNull final InterruptableConsumer<ContractSlotLocal> slotSink, final boolean doFullValidation) {
        boolean didRunToCompletion = true;
        try {
            fromState.extractVirtualMapData(
                    getStaticThreadManager(),
                    entry -> {
                        final var contractKey = entry.left();
                        final var iterableContractValue = entry.right();
                        slotSink.accept(new ContractSlotLocal(contractKey, iterableContractValue));

                        // Some counts to provide some validation
                        if (doFullValidation) {
                            counters.sourceOne();
                        }
                    },
                    this.threadCount);
            slotSink.accept(ContractSlotLocal.SENTINEL);
        } catch (final InterruptedException ex) {
            currentThread().interrupt();
            didRunToCompletion = false;
        }
        return didRunToCompletion;
    }

    /** If processing did not complete, report that.  Otherwise, perform (optionally) some simple consistency checks. */
    @NonNull
    Status validateTransform(
            @NonNull final EnumSet<CompletedProcesses> completedProcesses,
            @NonNull final List<String> validationsFailed,
            final boolean doFullValidation) {
        requireNonNull(completedProcesses);

        if (completedProcesses.size() != EnumSet.allOf(CompletedProcesses.class).size()) {
            if (!completedProcesses.contains(CompletedProcesses.SOURCING))
                validationsFailed.add("Sourcing process didn't finish");
            if (!completedProcesses.contains(CompletedProcesses.SINKING))
                validationsFailed.add("Sinking process didn't finish");
            return Status.INCOMPLETE_TRAVERSAL;
        }

        if (!doFullValidation) return Status.SUCCESS;

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

        final var nContracts = counters.contracts().size();
        if (nContracts != counters.nMissingPrevs().get()
                || nContracts != counters.nMissingNexts().get()) {
            validationsFailed.add(
                    "distinct contracts doesn't match #null prev links and/or #null next links: %d contract, %d null prevs, %d null nexts"
                            .formatted(
                                    nContracts,
                                    counters.nMissingPrevs().get(),
                                    counters.nMissingNexts().get()));
        }

        if (!counters.runningXorOfLinksIsZero()) {
            validationsFailed.add("prev/next links (over all contracts) aren't properly paired");
        }

        return validationsFailed.isEmpty() ? Status.VALIDATION_ERRORS : Status.SUCCESS;
    }

    /** Convert int[] to byte[] and then to Bytes. If argument is null or 0-length then return `Bytes.EMPTY`. */
    @NonNull
    static Bytes bytesFromInts(@Nullable final int[] ints) {
        if (ints == null) return Bytes.EMPTY;
        if (ints.length == 0) return Bytes.EMPTY;

        // N.B.: `ByteBuffer.allocate` creates the right `byte[]`.  `asIntBuffer` will share it, so no extra copy
        // there.  Finally, `Bytes.wrap` will wrap it - and end up owning it once `buf` goes out of scope - so no extra
        // copy there either.
        final ByteBuffer buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(ints);
        return Bytes.wrap(buf.array());
    }

    /** Validate an argument by throwing `IllegalArgumentException` if the test fails */
    public static void validateArgument(final boolean b, @NonNull final String msg) {
        if (!b) throw new IllegalArgumentException(msg);
    }

    static class BrokenTransformationException extends RuntimeException {

        public BrokenTransformationException(@NonNull final String message) {
            super(message);
        }
    }
}
