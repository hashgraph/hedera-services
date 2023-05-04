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

package com.hedera.services.cli.signedstate.utils;

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
import java.util.Set;
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
 * indexes of the virtual maps ("vmap"s) - ~1Gb serialized (2022-11). Then you can traverse the
 * rematerialized hashgraph state.
 *
 * <p>Currently implements only operations needed for looking at contract bytecodes and contract
 * state, but you can grab them in bulk.
 */
public class SignedStateHolder implements AutoCloseableNonThrowing {

    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 100_000;

    @NonNull
    private final Path swh;

    private final ReservedSignedState reservedSignedState;

    @NonNull
    private ServicesState platformState;

    public SignedStateHolder(@NonNull final Path swhFile) throws Exception {
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

    public enum Validity {
        ACTIVE,
        DELETED
    }

    /**
     * A contract - some bytecode associated with its contract id(s)
     *
     * @param ids - direct from the signed state file there's one contract id for each bytecode, but
     *     there are duplicates which can be coalesced and then there's a set of ids for the single
     *     contract
     * @param bytecode - bytecode of the contract
     * @param validity - whether the contract is valid or not - aka active or deleted
     */
    public record Contract(
            @NonNull Set</*@NonNull*/ Integer> ids, @NonNull byte[] bytecode, @NonNull Validity validity) {

        @Override
        public boolean equals(final Object obj) {
            return obj instanceof Contract other
                    && ids.equals(other.ids)
                    && Arrays.equals(bytecode, other.bytecode)
                    && validity.equals(other.validity);
        }

        @Override
        public int hashCode() {
            return ids.hashCode() * 31 + Arrays.hashCode(bytecode) + validity.hashCode();
        }

        @Override
        public String toString() {

            var csvIds = new StringBuilder(100);
            for (var id : ids()) {
                csvIds.append(id); // hides a `toString` which is why `String::join` isn't enough
                csvIds.append(',');
            }
            csvIds.setLength(csvIds.length() - 1);

            return "Contract{ids=(%s), %s, bytecode=%s}"
                    .formatted(csvIds.toString(), validity, Arrays.toString(bytecode));
        }
    }

    /**
     * All contracts extracted from a signed state file
     *
     * @param contracts - collection of all contracts (with bytecode)
     * @param deletedContracts - collection of ids of deleted contracts
     * @param registeredContractsCount - total #contracts known to the _accounts_ in the signed
     *     state file (not all actually have bytecodes in the file store, and of those, some have
     *     0-length bytecode files)
     */
    public record Contracts(
            @NonNull Collection</*@NonNull*/ Contract> contracts,
            Collection<Integer> deletedContracts,
            int registeredContractsCount) {}

    /**
     * Convenience method: Given the signed state file's name (the `.swh` file) return all the
     * bytecodes for all the contracts in that state.
     */
    @NonNull
    public Contracts getContracts() throws Exception {
        final var contractIds = getAllKnownContracts();
        final var deletedContractIds = getAllDeletedContracts();
        final var contractContents = getAllContractContents(contractIds, deletedContractIds);
        return new Contracts(contractContents, deletedContractIds, contractIds.size());
    }

    public record ContractKeyLocal(long contractId, UInt256 key) {
        public static ContractKeyLocal from(final ContractKey ckey) {
            return new ContractKeyLocal(ckey.getContractId(), toUInt256FromPackedIntArray(ckey.getKey()));
        }
    }

    @NonNull
    public static UInt256 toUInt256FromPackedIntArray(final int[] packed) {
        final var buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(packed);
        return UInt256.fromBytes(Bytes.wrap(buf.array()));
    }

    @NonNull
    public VirtualMapLike<ContractKey, IterableContractValue> getRawContractStorage() {
        return getPlatformState().contractStorage();
    }

    public @NonNull ServicesState getPlatformState() {
        return platformState;
    }

    /** Gets all existing accounts */
    public @NonNull AccountStorageAdapter getAccounts() {
        final var accounts = platformState.accounts();
        assertSignedStateComponentExists(accounts, "accounts");
        return accounts;
    }

    /**
     * Returns the file store from the state
     *
     * <p>The file state contains, among other things, all the contracts' bytecodes.
     */
    public @NonNull VirtualMapLike<VirtualBlobKey, VirtualBlobValue> getFileStore() {
        final var fileStore = platformState.storage();
        assertSignedStateComponentExists(fileStore, "fileStore");
        return fileStore;
    }

    /**
     * Returns all contracts known via Hedera accounts, by their contract id (lowered to an Integer)
     */
    @NonNull
    public Set</*@NonNull*/ Integer> getAllKnownContracts() {
        var ids = new HashSet<Integer>();
        getAccounts().forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract()) ids.add(k.intValue());
        });
        return ids;
    }

    /** Returns the ids of all deleted contracts ("self-destructed") */
    @NonNull
    public Set</*@NonNull*/ Integer> getAllDeletedContracts() {
        var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_CONTRACTS);
        getAccounts().forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract() && v.isDeleted()) ids.add(k.intValue());
        });
        return ids;
    }

    /** Returns the bytecodes for all the requested contracts */
    @NonNull
    public Collection</*@NonNull*/ Contract> getAllContractContents(
            @NonNull final Collection</*@NonNull*/ Integer> contractIds,
            @NonNull final Collection</*@NonNull*/ Integer> deletedContractIds) {

        final var fileStore = getFileStore();
        var codes = new ArrayList<Contract>();
        for (var cid : contractIds) {
            final var vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, cid);
            if (fileStore.containsKey(vbk)) {
                final var blob = fileStore.get(vbk);
                if (null != blob) {
                    final var c = new Contract(
                            Set.of(cid),
                            blob.getData(),
                            deletedContractIds.contains(cid) ? Validity.DELETED : Validity.ACTIVE);
                    codes.add(c);
                }
            }
        }
        return codes;
    }

    public static class MissingSignedStateComponent extends NullPointerException {
        public MissingSignedStateComponent(@NonNull final String component, @NonNull final Path swh) {
            super("Expected non-null %s from signed state file %s".formatted(component, swh.toString()));
        }
    }

    private void assertSignedStateComponentExists(final Object component, @NonNull final String componentName) {
        if (null == component) throw new MissingSignedStateComponent(componentName, swh);
    }
}
