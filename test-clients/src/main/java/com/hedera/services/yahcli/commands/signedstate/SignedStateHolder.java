/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.signedstate;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.Type;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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
 * <p>Currently implements only operations needed for looking at contracts: - {@link
 * #getAllKnownContracts()} looks in all the accounts to get all the contract ids present, - {@link
 * #getAllContractContents(Collection)} returns a map, indexed by contract id, of the contract
 * bytecodes.
 */
public class SignedStateHolder {

    @NonNull private final Path swh;
    @NonNull private ServicesState platformState;

    public SignedStateHolder(@NonNull final Path swhFile) throws Exception {
        swh = swhFile;
        platformState = dehydrate();
    }

    /** Deserialize the signed state file into an in-memory data structure. */
    private ServicesState dehydrate() throws Exception {
        // register all applicable classes on classpath before deserializing signed state
        ConstructableRegistry.getInstance().registerConstructables("*");
        platformState =
                (ServicesState)
                        (SignedStateFileReader.readStateFile(swh).signedState().getSwirldState());
        assertSignedStateComponentExists(platformState, "platform state (Swirlds)");
        return platformState;
    }

    /**
     * A contract - some bytecode associated with its contract id(s)
     *
     * @param ids - direct from the signed state file there's one contract id for each bytecode, but
     *     there are duplicates which can be coalesced and then there's a set of ids for the single
     *     contract
     * @param bytecode - bytecode of the contractd
     */
    public record Contract(@NonNull Set</*@NonNull*/ Integer> ids, @NonNull byte[] bytecode) {

        @Override
        public boolean equals(Object o) {
            return o instanceof Contract other
                    && ids.equals(other.ids)
                    && Arrays.equals(bytecode, other.bytecode);
        }

        @Override
        public int hashCode() {
            return ids.hashCode() * 31 + Arrays.hashCode(bytecode);
        }

        @Override
        public String toString() {

            var csvIds = new StringBuilder();
            for (var id : ids()) {
                csvIds.append(id); // hides a `toString` which is why `String::join` isn't enough
                csvIds.append(',');
            }
            csvIds.setLength(csvIds.length() - 1);

            return String.format(
                    "Contract{ids=(%s), bytecode=%s}",
                    csvIds.toString(), Arrays.toString(bytecode));
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
    public record Contracts(
            @NonNull Collection</*@NonNull*/ Contract> contracts, int registeredContractsCount) {}

    /**
     * Convenience method: Given the signed state file's name (the `.swh` file) return all the
     * bytecodes for all the contracts in that state.
     */
    public static @NonNull Contracts getContracts(@NonNull final Path inputFile) throws Exception {
        final var signedState = new SignedStateHolder(inputFile);
        final var contractIds = signedState.getAllKnownContracts();
        final var contractContents = signedState.getAllContractContents(contractIds);
        return new Contracts(contractContents, contractIds.size());
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
    public @NonNull VirtualMap<VirtualBlobKey, VirtualBlobValue> getFileStore() {
        final var fileStore = platformState.storage();
        assertSignedStateComponentExists(fileStore, "fileStore");
        return fileStore;
    }

    /**
     * Returns all contracts known via Hedera accounts, by their contract id (lowered to an Integer)
     */
    public @NonNull Set</*@NonNull*/ Integer> getAllKnownContracts() {
        var ids = new HashSet<Integer>();
        getAccounts()
                .forEach(
                        (k, v) -> {
                            if (null != k && null != v && v.isSmartContract())
                                ids.add(k.intValue());
                        });
        return ids;
    }

    /** Returns the bytecodes for all the requested contracts */
    public @NonNull Collection</*@NonNull*/ Contract> getAllContractContents(
            @NonNull final Collection</*@NonNull*/ Integer> contractIds) {

        final var fileStore = getFileStore();
        var codes = new ArrayList<Contract>();
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

    public static class MissingSignedStateComponent extends NullPointerException {
        public MissingSignedStateComponent(
                @NonNull final String component, @NonNull final Path swh) {
            super(
                    String.format(
                            "Expected non-null %s from signed state file %s",
                            component, swh.toString()));
        }
    }

    private void assertSignedStateComponentExists(
            final Object component, @NonNull final String componentName) {
        if (null == component) throw new MissingSignedStateComponent(componentName, swh);
    }
}
