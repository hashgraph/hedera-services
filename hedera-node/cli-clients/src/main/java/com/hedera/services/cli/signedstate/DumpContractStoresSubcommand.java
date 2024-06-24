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

package com.hedera.services.cli.signedstate;

import static java.util.Comparator.naturalOrder;
import static java.util.Map.Entry.comparingByKey;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpStateCommand.WithMigration;
import com.hedera.services.cli.signedstate.DumpStateCommand.WithSlots;
import com.hedera.services.cli.signedstate.DumpStateCommand.WithValidation;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.units.bigints.UInt256;

@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpContractStoresSubcommand {
    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path storePath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final WithSlots withSlots,
            @NonNull final WithMigration withMigration,
            @NonNull final WithValidation withValidation,
            @NonNull final Verbosity verbosity) {
        new DumpContractStoresSubcommand(
                        state, storePath, emitSummary, withSlots, withMigration, withValidation, verbosity)
                .doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path storePath;

    @NonNull
    final EmitSummary emitSummary;

    @NonNull
    final WithSlots withSlots;

    @NonNull
    final WithMigration withMigration;

    @NonNull
    final WithValidation withValidation;

    @NonNull
    final Verbosity verbosity;

    DumpContractStoresSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path storePath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final WithSlots withSlots,
            @NonNull final WithMigration withMigration,
            @NonNull final WithValidation withValidation,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.storePath = storePath;
        this.emitSummary = emitSummary;
        this.withSlots = withSlots;
        this.withMigration = withMigration;
        this.withValidation = withValidation;
        this.verbosity = verbosity;
    }

    record ContractKeyLocal(long contractId, UInt256 key) {}

    @SuppressWarnings(
            "java:S3864") // "Remove Stream.peek - should be used with caution" - conflicts with an IntelliJ inspection
    // that says to put _in_ a `Stream.peek`; anyway, in this case I _want_ it, it makes sense, I
    // _am in fact_ being properly cautious, thank you Sonar
    void doit() {

        // First grab all slot pairs from all contracts from the signed state
        final var contractState = new ConcurrentHashMap<Long, ConcurrentLinkedQueue<Pair<UInt256, UInt256>>>(5000);

        // We only handle mono-service state, but we can choose to handle it "native" _or_ migrate it to
        // modular-service's
        // representation first.
        final Predicate<BiConsumer<ContractKeyLocal, UInt256>> contractStoreIterator = withMigration == WithMigration.NO
                ? this::iterateThroughContractStorage
                : this::iterateThroughMigratedContractStorage;

        final var traversalOk = contractStoreIterator.test((ckey, value) -> {
            contractState.computeIfAbsent(ckey.contractId(), k -> new ConcurrentLinkedQueue<>());
            contractState.get(ckey.contractId()).add(Pair.of(ckey.key(), value));
        });

        if (traversalOk) {

            final var nDistinctContractIds = contractState.size();

            final var nContractStateValues = contractState.values().stream()
                    .mapToInt(ConcurrentLinkedQueue::size)
                    .sum();

            // I can't seriously be intending to cons up the _entire_ store of all contracts as a single string, can I?
            // Well, Toto, this isn't the 1990s anymore ...

            long reportSizeEstimate = (nDistinctContractIds * 20L)
                    + (nContractStateValues * 2 /*K/V*/ * (32 /*bytes*/ * 2 /*hexits/byte*/ + 3 /*whitespace+slop*/));
            final var sb = new StringBuilder((int) reportSizeEstimate);

            if (verbosity == Verbosity.VERBOSE)
                System.out.printf(
                        "=== %d contract stores found, %d k/v pairs%n", nDistinctContractIds, nContractStateValues);

            if (emitSummary == EmitSummary.YES)
                sb.append(
                        "*** %d contract stores, %d k/v pairs%n".formatted(nDistinctContractIds, nContractStateValues));

            // This list is generated in contract id order so that it is deterministic; also all the slot#/value pairs
            // are sorted by slot# for the same reason
            final var contractStates = contractState.entrySet().stream()
                    .map(entry -> Pair.of(entry.getKey(), new ArrayList<>(entry.getValue())))
                    .peek(entry -> entry.getRight().sort(naturalOrder()))
                    .sorted(comparingByKey())
                    .toList();

            if (emitSummary == EmitSummary.YES) {

                final var validationSummary = withValidation == WithValidation.NO ? "" : "validated ";
                final var migrationSummary =
                        withMigration == WithMigration.NO ? "" : "(with %smigration)".formatted(validationSummary);

                sb.append("%s%n%d contractKeys found, %d total slots %s%n"
                        .formatted("=".repeat(80), nDistinctContractIds, nContractStateValues, migrationSummary));
                appendContractStoreSummary(sb, contractStates);
            }

            if (withSlots == WithSlots.YES)
                for (final var aContractState : contractStates) appendSerializedContractStore(sb, aContractState);

            if (verbosity == Verbosity.VERBOSE)
                System.out.printf("=== Contract store report is %d bytes%n", sb.length());

            writeReportToFile(sb.toString());
        } else {
            System.err.printf("*** Traversal of the entire contract store did not complete (interrupted) ***%n");
        }
    }

    /** Iterating through contract storage to get all slot/value pairs is done indirectly: You pass a visitor to the
     * virtual merkle tree and it does the traversal on multiple threads.  (So the visitor needs to be able to handle
     * multiple concurrent calls.)
     */
    boolean iterateThroughContractStorage(BiConsumer<ContractKeyLocal, UInt256> visitor) {
        return false;
    }

    /** Iterate through a _migrated_ contract store that is in modular-service's representation */
    boolean iterateThroughMigratedContractStorage(BiConsumer<ContractKeyLocal, UInt256> visitor) {
        final var contractStorageStore = getMigratedContractStore();

        final var nSlotsSeen = new AtomicLong();

        if (contractStorageStore == null) return false;
        contractStorageStore.keys().forEachRemaining(key -> {
            // (Not sure how many temporary _copies_ of a byte arrays are made here ... best not to ask ...)
            final var contractKeyLocal = new ContractKeyLocal(
                    key.contractID().contractNumOrThrow(),
                    uint256FromByteArray(key.key().toByteArray()));
            final var slotValue = contractStorageStore.get(key);
            assert (slotValue != null);
            final var value = uint256FromByteArray(slotValue.value().toByteArray());
            nSlotsSeen.incrementAndGet();
            visitor.accept(contractKeyLocal, value);
        });

        return true;
    }

    /** First migrates the contract store from the mono-service representation to the modular-service representations,
     *  and then returns all contracts with bytecodes from the migrated contract store, plus the ids of contracts with
     *  0-length bytecodes.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    WritableKVState<SlotKey, SlotValue> getMigratedContractStore() {
        return null;
    }

    // Produce a report, one line per contract, summarizing the #slot pairs and the min/max slot#
    void appendContractStoreSummary(
            @NonNull final StringBuilder sb,
            @NonNull final List<Pair<Long, ArrayList<Pair<UInt256, UInt256>>>> contractStates) {

        sb.append("contractId  #slots    min       max    oob\n");
        //         ----------: ------ --------- --------- ------
        for (final var contractState : contractStates) {
            final var contractId = contractState.getKey();
            final var slots = contractState.getValue();

            record Acc(long min, long max, long outOfBounds) {}
            final var slotSummary = slots.stream()
                    .reduce(
                            new Acc(Long.MAX_VALUE, Long.MIN_VALUE, 0L),
                            (r, e) -> {
                                final var slot = e.getKey();
                                final var itFits = slot.fitsLong();
                                if (itFits) {
                                    final var slotL = slot.toLong();
                                    return new Acc(Long.min(r.min(), slotL), Long.max(r.max(), slotL), r.outOfBounds());
                                } else return new Acc(r.min(), r.max(), r.outOfBounds + 1L);
                            },
                            (r1, r2) -> new Acc(
                                    Long.min(r1.min(), r2.min()),
                                    Long.max(r1.max(), r2.max()),
                                    r1.outOfBounds() + r2.outOfBounds()));
            sb.append("%10d; %6d %9d %9d %6d%n"
                    .formatted(
                            contractId, slots.size(), slotSummary.min(), slotSummary.max(), slotSummary.outOfBounds())
                    .replace("-9223372036854775808", "      N/A") // fixup case where state had _no_
                    .replace("9223372036854775807", "      N/A")); // slot#s that fit in a long
        }
    }

    // Format a single contract's slot/value pairs on a single line
    void appendSerializedContractStore(
            @NonNull final StringBuilder sb,
            @NonNull final Pair<Long, ArrayList<Pair<UInt256, UInt256>>> contractState) {

        // First two fields are the contract id and the number of slot pairs it has
        sb.append(contractState.getKey());
        sb.append(" #");
        sb.append(contractState.getValue().size());

        // Now we emit the slots in increasing order by slot#. Format is "@slot# slotVal" for each one. But we do
        // an optimization: If two slots are directly sequential we omit the slot# of the second.  This is because
        // Solidity tends to allocate a dense group of slots first (for scalars and fixed size arrays and such) and
        // then the rest of the slots (for variable size arrays and maps) have pretty random slot#s.
        var nextSlot = 0L;
        for (final var slotPair : contractState.getValue()) {
            final var slot = slotPair.getKey();
            if (slot.fitsLong()) {
                final var slotL = slot.toLong();
                if (nextSlot != slotL) {
                    sb.append(" @");
                    sb.append(slotL);
                    nextSlot = slotL;
                } else nextSlot++;
            } else {
                sb.append(" @");
                sb.append(slot.toQuantityHexString().substring(2)); // strip off hex prefix
            }
            sb.append(" ");
            sb.append(slotPair.getValue().toQuantityHexString().substring(2)); // strip off hex prefix
        }
    }

    void writeReportToFile(@NonNull String report) {
        try (final PrintWriter out = new PrintWriter(storePath.toFile(), StandardCharsets.UTF_8)) {
            out.print(report);
            out.flush();
        } catch (final FileNotFoundException ex) {
            System.err.printf("*** Cannot create '%s' for writing%n", storePath);
        } catch (final IOException ex) {
            System.err.printf("*** Exception when trying to write '%s'%n", storePath);
            throw new UncheckedIOException(ex); // This is a CLI program: Java will print the stack trace properly
        }
    }

    @NonNull
    static UInt256 toUint256FromPackedIntArray(@NonNull final int[] packed) {
        final var buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(packed);
        return uint256FromByteArray(buf.array());
    }

    @NonNull
    static UInt256 uint256FromByteArray(@NonNull final byte[] bytes) {
        return UInt256.fromBytes(org.apache.tuweni.bytes.Bytes.wrap(bytes));
    }
}
