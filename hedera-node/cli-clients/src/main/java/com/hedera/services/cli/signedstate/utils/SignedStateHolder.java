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
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
 * indexes of the virtual maps ("vmap"s) - ~2Gb serialized (2023-04). Then you can traverse the
 * rematerialized hashgraph state.
 *
 * <p>Currently implements only operations needed for looking at contract bytecodes and contract
 * state, but you can grab them in bulk.
 */
public class SignedStateHolder implements AutoCloseableNonThrowing {

    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 100_000;
    static final int ESTIMATED_NUMBER_OF_DELETED_CONTRACTS = 10_000;

    @NonNull
    private final Path swh;

    @NonNull
    private final ReservedSignedState reservedSignedState;

    @NonNull
    private final ServicesState servicesState;

    public SignedStateHolder(@NonNull final Path swhFile) {
        Objects.requireNonNull(swhFile, "swhFile");

        swh = swhFile;
        final var state = dehydrate();
        reservedSignedState = state.getLeft();
        servicesState = state.getRight();
    }

    /** Deserialize the signed state file into an in-memory data structure. */
    @SuppressWarnings("removal")
    @NonNull
    private Pair<ReservedSignedState, ServicesState> dehydrate() {
        try {
            registerConstructables();

            final var config = com.swirlds.common.config.singleton.ConfigurationHolder.getInstance()
                    .get();
            final var metrics = new com.swirlds.common.metrics.noop.NoOpMetrics();
            final var crypto = com.swirlds.common.crypto.CryptographyHolder.get();

            final PlatformContext platformContext = new DefaultPlatformContext(config, metrics, crypto);
            final var rss =
                    SignedStateFileReader.readStateFile(platformContext, swh).reservedSignedState();
            if (null == rss) throw new MissingSignedStateComponent("ReservedSignedState", swh);
            final var swirldsState = rss.get().getSwirldState();
            if (swirldsState instanceof ServicesState ss) {
                return Pair.of(rss, ss);
            } else {
                throw new MissingSignedStateComponent("ServicesState", swh);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            if (obj == null) return false;
            if (obj == this) return true;
            return obj instanceof Contract other
                    && new EqualsBuilder()
                            .append(ids, other.ids)
                            .append(bytecode, other.bytecode)
                            .append(validity, other.validity)
                            .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37)
                    .append(ids)
                    .append(bytecode)
                    .append(validity)
                    .toHashCode();
        }

        @Override
        public String toString() {
            var csvIds = ids.stream().map(Object::toString).collect(Collectors.joining(","));

            return "Contract{ids=(%s), %s, bytecode=%s}".formatted(csvIds, validity, Arrays.toString(bytecode));
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
            @NonNull Collection<Integer> deletedContracts,
            int registeredContractsCount) {}

    /**
     * Convenience method: Given the signed state file's name (the `.swh` file) return all the
     * bytecodes for all the contracts in that state.
     */
    @NonNull
    public Contracts getContracts() {
        final var contractIds = getAllKnownContracts();
        final var deletedContractIds = getAllDeletedContracts();
        final var contractContents = getAllContractContents(contractIds, deletedContractIds);
        return new Contracts(contractContents, deletedContractIds, contractIds.size());
    }

    public record ContractKeyLocal(long contractId, @NonNull UInt256 key) {
        public static ContractKeyLocal from(final ContractKey ckey) {
            Objects.requireNonNull(ckey, "ckey");

            return new ContractKeyLocal(ckey.getContractId(), toUInt256FromPackedIntArray(ckey.getKey()));
        }
    }

    @NonNull
    public static UInt256 toUInt256FromPackedIntArray(@NonNull final int[] packed) {
        Objects.requireNonNull(packed, "packed");

        final var buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(packed);
        return UInt256.fromBytes(Bytes.wrap(buf.array()));
    }

    @NonNull
    public VirtualMapLike<ContractKey, IterableContractValue> getRawContractStorage() {
        return getServicesState().contractStorage();
    }

    public @NonNull ServicesState getServicesState() {
        return servicesState;
    }

    /** Gets all existing accounts */
    public @NonNull AccountStorageAdapter getAccounts() {
        final var accounts = servicesState.accounts();
        assertSignedStateComponentExists(accounts, "accounts");
        return accounts;
    }

    /**
     * Returns the file store from the state
     *
     * <p>The file state contains, among other things, all the contracts' bytecodes.
     */
    public @NonNull VirtualMapLike<VirtualBlobKey, VirtualBlobValue> getFileStore() {
        final var fileStore = servicesState.storage();
        assertSignedStateComponentExists(fileStore, "fileStore");
        return fileStore;
    }

    /**
     * Returns all contracts known via Hedera accounts, by their contract id (lowered to an Integer)
     */
    @NonNull
    public Set</*@NonNull*/ Integer> getAllKnownContracts() {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_CONTRACTS);
        getAccounts().forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract()) ids.add(k.intValue());
        });
        return ids;
    }

    /** Returns the ids of all deleted contracts ("self-destructed") */
    @NonNull
    public Set</*@NonNull*/ Integer> getAllDeletedContracts() {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_DELETED_CONTRACTS);
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
        Objects.requireNonNull(contractIds, "contractIds");
        Objects.requireNonNull(deletedContractIds, "deletedContractIds");

        final var fileStore = getFileStore();
        final var codes = new ArrayList<Contract>();
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

    /** register all applicable classes on classpath before deserializing signed state */
    void registerConstructables() {
        try {
            ConstructableRegistry.getInstance().registerConstructables("*");
        } catch (ConstructableRegistryException e) {
            throw new UncheckedConstructableRegistryException(e);
        }
    }

    public static class UncheckedConstructableRegistryException extends RuntimeException {
        public UncheckedConstructableRegistryException(@NonNull final ConstructableRegistryException e) {
            super(e);
        }
    }

    public static class MissingSignedStateComponent extends NullPointerException {
        public MissingSignedStateComponent(@NonNull final String component, @NonNull final Path swh) {
            super("Expected non-null %s from signed state file %s".formatted(component, swh.toString()));
        }
    }

    private void assertSignedStateComponentExists(
            @Nullable final Object component, @NonNull final String componentName) {
        if (null == component) throw new MissingSignedStateComponent(componentName, swh);
    }
}
