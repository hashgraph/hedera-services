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

package com.hedera.services.yahcli.commands.contract.utils;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.Comparator.naturalOrder;
import static java.util.Map.Entry.comparingByKey;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.Type;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Navigates a signed state "file" and returns information from it
 *
 * <p>A "signed state" is actually a directory tree, at the top level of which is the serialized
 * merkle tree of the hashgraph state. That is in a file named `SignedState.swh` and file is called
 * the "signed state file". But the whole directory tree must be present.
 *
 * <p>This uses a `SignedStateFileReader` to suck that entire merkle tree into memory, plus the
 * indexes of the virtual maps ("vmap"s) - ~1.8Gb serialized (2023-03). Then you can traverse the
 * rematerialized hashgraph state.
 *
 * <p>Currently implements only operations needed for looking at contract bytecodes and contract
 * state, but you can grab them in bulk.
 */
public class SignedStateHolder implements AutoCloseableNonThrowing {

    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 2_000;

    @NonNull
    private final Path swh;

    private final ReservedSignedState reservedSignedState;

    private final ServicesState platformState;

    public SignedStateHolder(@NonNull final Path swhFile) {
        swh = swhFile;
        final var state = dehydrate();
        reservedSignedState = state.getLeft();
        platformState = state.getRight();
    }

    /** Deserialize the signed state file into an in-memory data structure. */
    @SuppressWarnings("java:S112") // "Generic exceptions should never be thrown" - LCM of fatal exceptions: don't care
    @NonNull
    private Pair<ReservedSignedState, ServicesState> dehydrate() {
        try {
            // register all applicable classes on classpath before deserializing signed state
            ConstructableRegistry.getInstance().registerConstructables("*");

            final PlatformContext platformContext = new DefaultPlatformContext(
                    ConfigurationHolder.getInstance().get(), new NoOpMetrics(), CryptographyHolder.get());

            final var rss =
                    SignedStateFileReader.readStateFile(platformContext, swh).reservedSignedState();
            final var ps = (ServicesState) (rss.get().getSwirldState());

            assertSignedStateComponentExists(ps, "platform state (Swirlds)");
            return Pair.of(rss, ps);
        } catch (ConstructableRegistryException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        reservedSignedState.close();
    }

    /**
     * A contract - some bytecode associated with its contract id(s)
     *
     * @param ids - direct from the signed state file there's one contract id for each bytecode, but
     *     there are duplicates which can be coalesced and then there's a set of ids for the single
     *     contract
     * @param bytecode - bytecode of the contract
     */
    public record Contract(@NonNull Set</*@NonNull*/ Integer> ids, @NonNull byte[] bytecode) {

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof Contract other && ids.equals(other.ids) && Arrays.equals(bytecode, other.bytecode);
        }

        @Override
        public int hashCode() {
            return ids.hashCode() * 31 + Arrays.hashCode(bytecode);
        }

        @Override
        public String toString() {

            var csvIds = new StringBuilder(100);
            for (var id : ids()) {
                csvIds.append(id); // hides a `toString` which is why `String::join` isn't enough
                csvIds.append(',');
            }
            csvIds.setLength(csvIds.length() - 1);

            return "Contract{ids=(%s), bytecode=%s}".formatted(csvIds.toString(), Arrays.toString(bytecode));
        }
    }

    /**
     * All contracts extracted from a signed state file
     *
     * @param contracts - dictionary of contract bytecodes indexed by their contract id (as a Long)
     * @param registeredContractsCount - total #contracts known to the _accounts_ in the signed
     *     state file (not all actually have bytecodes in the file store, and of those, some have
     *     0-length bytecode files)
     */
    public record Contracts(@NonNull Collection</*@NonNull*/ Contract> contracts, int registeredContractsCount) {}

    /**
     * Convenience method: Given the signed state file's name (the `.swh` file) return all the
     * bytecodes for all the contracts in that state.
     */
    @NonNull
    public static Contracts getContracts(@NonNull final Path inputFile) {
        try (final var ssh = new SignedStateHolder(inputFile)) {
            return ssh.getContracts();
        }
    }

    @NonNull
    public Contracts getContracts() {
        final var contractIds = getAllKnownContracts();
        final var contractContents = getAllContractContents(contractIds);
        return new Contracts(contractContents, contractIds.size());
    }

    public record ContractKeyLocal(long contractId, UInt256 key) {
        public static ContractKeyLocal from(ContractKey ckey) {
            return new ContractKeyLocal(ckey.getContractId(), toUInt256FromPackedIntArray(ckey.getKey()));
        }
    }

    @NonNull
    public static UInt256 toUInt256FromPackedIntArray(final int[] packed) {
        final var buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(packed);
        return UInt256.fromBytes(Bytes.wrap(buf.array()));
    }

    public enum DumpOperation {
        SUMMARIZE,
        CONTENTS
    }

    @NonNull
    public static String dumpContractStorage(@NonNull DumpOperation operation, @NonNull final Path inputFile) {
        try (final var ssh = new SignedStateHolder(inputFile)) {
            return ssh.dumpContractStorage(operation);
        }
    }

    @SuppressWarnings("java:S3864") // `Stream.peek` should be used with caution - yes, and I've been careful
    //                                 Plus, IntelliJ suggested replacing a `map` with this `peek`: Two "inspections"
    //                                 disagreeing with each other: nice.
    @NonNull
    public String dumpContractStorage(@NonNull DumpOperation operation) {
        final var contractKeys = new ConcurrentLinkedQueue<ContractKeyLocal>();
        final var contractState = new ConcurrentHashMap<Long, ConcurrentLinkedQueue<Pair<UInt256, UInt256>>>(5000);
        final var traversalOk = iterateThroughContractStorage((ckey, iter) -> {
            final var contractId = ckey.getContractId();
            final var key = toUInt256FromPackedIntArray(ckey.getKey());

            contractKeys.add(new ContractKeyLocal(contractId, key));

            contractState.computeIfAbsent(contractId, k -> new ConcurrentLinkedQueue<>());
            contractState.get(contractId).add(Pair.of(key, iter.asUInt256()));
        });

        if (traversalOk) {
            final var nDistinctContractIds = contractKeys.stream()
                    .map(ContractKeyLocal::contractId)
                    .distinct()
                    .count();
            final var nContractStateValues = contractState.values().stream()
                    .mapToInt(ConcurrentLinkedQueue::size)
                    .sum();

            System.out.println("****** %d contract stores found, %d k/v pairs"
                    .formatted(nDistinctContractIds, nContractStateValues));

            final var contractStates = contractState.entrySet().stream()
                    .map(entry -> Pair.of(entry.getKey(), new ArrayList<>(entry.getValue())))
                    .peek(entry -> entry.getRight().sort(naturalOrder()))
                    .sorted(comparingByKey())
                    .toList();

            return switch (operation) {
                case SUMMARIZE -> {
                    final var sb = new StringBuilder(300_000);
                    sb.append(
                            "%s%n%d contractKeys found, %d distinct; %d contract state entries totalling %d values%n-----%n"
                                    .formatted(
                                            "=".repeat(80),
                                            contractKeys.size(),
                                            nDistinctContractIds,
                                            contractState.size(),
                                            nContractStateValues));

                    sb.append(getContractStoreSlotSummary(contractStates));
                    yield sb.toString();
                }
                case CONTENTS -> {
                    // I can't seriously be intending to cons up the _entire_ store of all contracts as a single
                    // string, can I? (Currently 29MB.) Well, this isn't the 1990s ...
                    final var sb = new StringBuilder(50_000_000);
                    for (final var aContractState : contractStates) {
                        sb.append(serializeContractStoreToText(aContractState));
                        sb.append("\n");
                    }
                    yield sb.toString();
                }
            };
        } else return "*** traversal didn't complete (interrupted) ***";
    }

    @NonNull
    public static String serializeContractStoreToText(
            final Pair<Long, ArrayList<Pair<UInt256, UInt256>>> contractState) {
        final var sb = new StringBuilder(contractState.getValue().size() * 100 /*???*/);
        sb.append(contractState.getKey());
        var nextSlot = 0L;
        for (final var slotPair : contractState.getValue()) {
            final var slot = slotPair.getKey();
            if (slot.fitsLong()) {
                final var slotL = slot.toLong();
                if (nextSlot != slotL) {
                    sb.append(" @");
                    sb.append(slotL);
                    nextSlot = slotL;
                } else {
                    nextSlot++;
                }
            } else {
                sb.append(" @");
                sb.append(slot.toQuantityHexString().substring(2)); // strip off hex prefix
            }
            sb.append(" ");
            sb.append(slotPair.getValue().toQuantityHexString().substring(2)); // strip off hex prefix
        }
        return sb.toString();
    }

    @NonNull
    public static String getContractStoreSlotSummary(
            final List<Pair<Long, ArrayList<Pair<UInt256, UInt256>>>> contractStates) {

        final var contractIds = new ArrayList<Long>(5000);

        final var sb = new StringBuilder(200_000);

        sb.append("contractId  #slots    min       max    oob\n");
        //         ----------: ------ --------- --------- ------
        for (final var state : contractStates) {
            final var contractId = state.getKey();
            final var slots = state.getValue();
            contractIds.add(contractId);

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
                                } else {
                                    return new Acc(r.min(), r.max(), r.outOfBounds + 1L);
                                }
                            },
                            (r1, r2) -> new Acc(
                                    Long.min(r1.min(), r2.min()),
                                    Long.max(r1.max(), r2.max()),
                                    r1.outOfBounds() + r2.outOfBounds()));
            sb.append("%10d: %6d %9d %9d %6d%n"
                    .formatted(
                            contractId, slots.size(), slotSummary.min(), slotSummary.max(), slotSummary.outOfBounds())
                    .replace("-9223372036854775808", "      N/A") //  state had _no_ slot#s that fit
                    .replace("9223372036854775807", "      N/A")); // in a long - clean that up
        }

        return sb.toString();
    }

    public boolean iterateThroughContractStorage(BiConsumer<ContractKey, IterableContractValue> visitor) {

        final int THREAD_COUNT = 8;
        final var contractStorageVMap = getRawContractStorage();

        boolean ranToCompletion = true;
        try {
            contractStorageVMap.extractVirtualMapData(
                    getStaticThreadManager(),
                    entry -> {
                        final var contractKey = entry.getKey();
                        final var iterableContractValue = entry.getValue();
                        visitor.accept(contractKey, iterableContractValue);
                    },
                    THREAD_COUNT);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            ranToCompletion = false;
        }

        contractStorageVMap.release();
        return ranToCompletion;
    }

    @NonNull
    public VirtualMapLike<ContractKey, IterableContractValue> getRawContractStorage() {
        return getPlatformState().contractStorage();
    }

    @NonNull
    public ServicesState getPlatformState() {
        return platformState;
    }

    /** Gets all existing accounts */
    @NonNull
    public AccountStorageAdapter getAccounts() {
        final var accounts = platformState.accounts();
        assertSignedStateComponentExists(accounts, "accounts");
        return accounts;
    }

    /**
     * Returns the file store from the state
     *
     * <p>The file state contains, among other things, all the contracts' bytecodes.
     */
    @NonNull
    public VirtualMapLike<VirtualBlobKey, VirtualBlobValue> getFileStore() {
        final var fileStore = platformState.storage();
        assertSignedStateComponentExists(fileStore, "fileStore");
        return fileStore;
    }

    /**
     * Returns all contracts known via Hedera accounts, by their contract id (lowered to an Integer)
     */
    @NonNull
    public Set</*@NonNull*/ Integer> getAllKnownContracts() {
        var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_CONTRACTS);
        getAccounts().forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract()) ids.add(k.intValue());
        });
        return ids;
    }

    /** Returns the bytecodes for all the requested contracts */
    @NonNull
    public Collection</*@NonNull*/ Contract> getAllContractContents(
            @NonNull final Collection</*@NonNull*/ Integer> contractIds) {

        final var fileStore = getFileStore();
        var codes = new ArrayList<Contract>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (var cid : contractIds) {
            final var vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, cid);
            if (fileStore.containsKey(vbk)) {
                final var blob = fileStore.get(vbk);
                if (null != blob) {
                    final var c = new Contract(Set.of(cid), blob.getData());
                    codes.add(c);
                }
            }
        }
        return codes;
    }

    public static class MissingSignedStateComponentException extends NullPointerException {
        public MissingSignedStateComponentException(@NonNull final String component, @NonNull final Path swh) {
            super("Expected non-null %s from signed state file %s"
                    .formatted(component, swh.toAbsolutePath().toString()));
        }
    }

    private void assertSignedStateComponentExists(final Object component, @NonNull final String componentName) {
        if (null == component) throw new MissingSignedStateComponentException(componentName, swh);
    }
}
