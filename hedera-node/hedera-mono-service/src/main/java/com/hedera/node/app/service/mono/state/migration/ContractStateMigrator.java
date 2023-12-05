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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Migrate mono service's contract storage (contract slots, i.e. per-contract K/V pairs) to modular service's contract
 * storage.
 *
 * Three static methods run the migrator:  Reading the entire contract storage from `fromState` and writing the migrated
 * (transformed) slots to `toState`.  Caller is responsible for supplying the state to write.  This class will commit
 * all items added to the `toState`.
 */
public class ContractStateMigrator {
    private static final Logger log = LogManager.getLogger(ContractStateMigrator.class);

    /** Return status from the contract storage migrator.  The migration methods can also return a list of specific
     * migration validation consistency failures (strings).
     */
    public enum Status {
        SUCCESS,
        VALIDATION_ERRORS,
        INCOMPLETE_TRAVERSAL
    }

    /** Given a `WritableKVState` for the contract storage, makes a copy of it - thus flushing it to disk! - and then
     * returns the copy for further writing.
     *
     * Supplied by the caller because only it knows how to get (or hold) the underlying datastore for a `WritableKVState`.
     * (There's an example over at `DumpContractStoresSubcommand`.)
     */
    public interface StateFlusher extends UnaryOperator<WritableKVState<SlotKey, SlotValue>> {}

    public static Status migrateFromContractStorageVirtualMap(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull final WritableKVState<SlotKey, SlotValue> initialToState,
            @NonNull final StateFlusher stateFlusher) {
        return migrateFromContractStorageVirtualMap(
                fromState, initialToState, stateFlusher, new ArrayList<>(), false, DEFAULT_THREAD_COUNT);
    }

    @NonNull
    public static Status migrateFromContractStorageVirtualMap(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull final WritableKVState<SlotKey, SlotValue> initialToState,
            @NonNull final StateFlusher stateFlusher,
            @NonNull final List<String> validationFailures) {
        return migrateFromContractStorageVirtualMap(
                fromState, initialToState, stateFlusher, validationFailures, true, DEFAULT_THREAD_COUNT);
    }

    @NonNull
    public static Status migrateFromContractStorageVirtualMap(
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
    static final int DEFAULT_THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() * 2 / 3);

    /** Slots are read from `fromState` concurrently with their being transformed and put into `toState`, with a queue
     *  between the source and the sink.  This is the maximum size of that queue.
     *
     *  (Current value of 1M holds both mainnet and testnet state as of 2023-11.)
     */
    static final int MAXIMUM_SLOTS_IN_FLIGHT = 1_000_000;

    static final int EVM_WORD_WIDTH_IN_BYTES = 32;
    static final int EVM_WORD_WIDTH_IN_INTS = 8;
    static final String LOG_CAPTION = "contract-storage mono-to-modular migration";

    /** Need to commit and flush at intervals; should be VirtualMapConfig.preferredFlushQueueSize() but no access here */
    private static final int COMMIT_STATE_EVERY_N_INSERTS = 10_000;

    /** Timeout to prevent a thread hanging in case of some design flaw - any guess for timeout is good, it's safety only */
    private static final Duration MIGRATION_QUEUE_POLL_TIMEOUT = Duration.of(100, ChronoUnit.MILLIS);

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

    /** Perform (optional) validation that `toState` contains the same entities as `fromState`.  Not a direct
     * comparision, but a rough safety check.  (Default: do not do this validation.)
     */
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

    /**
     * Do the transform from mono-service's contract storage to modular-service's contract storage, and do some
     * post-transforms sanity checking.
     */
    @NonNull
    Status doit(@NonNull final List<String> validationsFailed) {

        final var completedProcesses = new NonAtomicReference<EnumSet<CompletedProcesses>>();

        withLoggedDuration(
                () -> completedProcesses.set(transformStorage(doFullValidation)),
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
    EnumSet<CompletedProcesses> transformStorage(final boolean doFullValidation) {

        final var slotQueue = new ArrayBlockingQueue<ContractSlotLocal>(MAXIMUM_SLOTS_IN_FLIGHT);

        // Sinking and sourcing happen concurrently.  (Though sourcing is multithreaded, sinking is all on one thread.)
        // Consider: For debugging, don't create (and start) `processSlotQueue` until after sourcing is complete. Just
        // accumulate everything in the `fromState` in the queue before sinking anything.

        CompletableFuture<Void> processSlotQueue = CompletableFuture.runAsync(() -> iterateOverAllQueuedSlots(
                () -> slotQueue.poll(MIGRATION_QUEUE_POLL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                fromState.size(),
                doFullValidation));

        final var completedTasks = EnumSet.noneOf(CompletedProcesses.class);

        boolean didCompleteSourcing = iterateOverAllCurrentData(slotQueue::put, doFullValidation);
        if (didCompleteSourcing) completedTasks.add(CompletedProcesses.SOURCING);

        boolean didCompleteSinking = true;
        try {
            processSlotQueue.join();
        } catch (BrokenTransformationException bex) {
            log.error("%s: interrupt when sinking slots: %s".formatted(LOG_CAPTION, bex.getMessage()), bex.getCause());
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
            @NonNull final InterruptableSupplier<ContractSlotLocal> slotSource,
            final long expectedSlotCount,
            final boolean doFullValidation) {
        requireNonNull(slotSource);

        // Lambda to get the next item off the concurrent queue between source and sink.  Handles end-of-stream
        // (finding the sentinel element) by returning an empty `Optional`.  Transforms an `InterruptedException`
        // (which is _not_ a `RuntimeException`) into the more easily handled exception defined in this class.
        final Supplier<Optional<ContractSlotLocal>> getter = () -> {
            try {
                final var slot = slotSource.get();
                if (slot == ContractSlotLocal.SENTINEL) return Optional.empty();
                return Optional.of(slot);
            } catch (final InterruptedException ex) {
                throw new BrokenTransformationException(
                        LOG_CAPTION + ": timeout reading contract slots from queue", ex);
            }
        };

        // We know precisely how many slots we have to process.  And they're followed, in the queue, by a sentinel
        // element.  But this loop checks for too _few_ and too _many_ slots in the queue, for safety.  (The check for
        // too few is by either finding the sentinel too soon or hitting a timeout waiting for an element from the
        // queue.)
        for (int i = 0; i < expectedSlotCount; i++) {
            final var oslot = getter.get();
            if (oslot.isEmpty()) {
                final var msg =
                        "%s: not enough contract slots read from queue (%d expected, %d read), SENTINEL found too soon"
                                .formatted(LOG_CAPTION, expectedSlotCount, i);
                throw new BrokenTransformationException(msg);
            }

            // The transform from the mono-service slot representation to the modular-service slot representation
            // is here:

            final var slot = oslot.get();
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

        // Read final sentinel value
        if (getter.get().isPresent()) {
            final var msg = "%s: too many contract slots read (%d expected), SENTINEL not yet found"
                    .formatted(LOG_CAPTION, expectedSlotCount);
            throw new BrokenTransformationException(msg);
        }
    }

    /** State needs to be flushed to disk every so many inserts (for performance reasons w.r.t. the underlying
     * datasource).  `WritableKVState.commit()` doesn't do it.  IN fact, only the _caller_ knows how to do it.
     */
    void commitToStateIfNeeded() {
        if (++nInsertionsDone % COMMIT_STATE_EVERY_N_INSERTS == 0) commitToStateNow();
    }

    /** Force a flush of the underlying datastore. */
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

        // Validate that both the source and sink finished processing slots
        if (completedProcesses.size() != EnumSet.allOf(CompletedProcesses.class).size()) {
            if (!completedProcesses.contains(CompletedProcesses.SOURCING))
                validationsFailed.add("Sourcing process didn't finish");
            if (!completedProcesses.contains(CompletedProcesses.SINKING))
                validationsFailed.add("Sinking process didn't finish");
            return Status.INCOMPLETE_TRAVERSAL;
        }

        if (!doFullValidation) return Status.SUCCESS;

        // Make sure everything agrees on the number of slots migrated
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

        // These next two checks are for the consistency of the doubly-linked lists linking each contract's slots.  In
        // these doubly-linked lists the _end_ links are missing (empty) and each link appears _twice_.

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

    /** Exception to indicate the migration failed. */
    static class BrokenTransformationException extends CompletionException {

        public BrokenTransformationException(@NonNull final String message) {
            super(message);
        }

        public BrokenTransformationException(@NonNull final String message, @NonNull final Throwable t) {
            super(message, t);
        }
    }
}
