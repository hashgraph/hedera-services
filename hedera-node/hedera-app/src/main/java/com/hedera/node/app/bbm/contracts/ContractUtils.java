/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.bbm.contracts;

import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey.Type;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class ContractUtils {

    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 100_000;
    static final int ESTIMATED_NUMBER_OF_DELETED_CONTRACTS = 10_000;

    /**
     * Return all the bytecodes for all the contracts in this state.
     */
    @NonNull
    public static Contracts getContracts(
            VirtualMapLike<VirtualBlobKey, VirtualBlobValue> files, AccountStorageAdapter accountAdapter) {
        final var contractIds = getAllKnownContracts(accountAdapter);
        final var deletedContractIds = getAllDeletedContracts(accountAdapter);
        final var contractContents = getAllContractContents(files, contractIds, deletedContractIds);
        return new Contracts(contractContents, deletedContractIds, contractIds.size());
    }

    /**
     * Returns all contracts known via Hedera accounts, by their contract id (lowered to an Integer)
     */
    @NonNull
    private static Set</*@NonNull*/ Integer> getAllKnownContracts(AccountStorageAdapter accounts) {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_CONTRACTS);
        accounts.forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract()) {
                ids.add(k.intValue());
            }
        });
        return ids;
    }

    /** Returns the ids of all deleted contracts ("self-destructed") */
    @NonNull
    private static Set</*@NonNull*/ Integer> getAllDeletedContracts(AccountStorageAdapter accounts) {
        final var ids = new HashSet<Integer>(ESTIMATED_NUMBER_OF_DELETED_CONTRACTS);
        accounts.forEach((k, v) -> {
            if (null != k && null != v && v.isSmartContract() && v.isDeleted()) {
                ids.add(k.intValue());
            }
        });
        return ids;
    }

    /** Returns the bytecodes for all the requested contracts */
    @NonNull
    private static Collection</*@NonNull*/ Contract> getAllContractContents(
            @NonNull final VirtualMapLike<VirtualBlobKey, VirtualBlobValue> fileStore,
            @NonNull final Collection</*@NonNull*/ Integer> contractIds,
            @NonNull final Collection</*@NonNull*/ Integer> deletedContractIds) {
        Objects.requireNonNull(contractIds);
        Objects.requireNonNull(deletedContractIds);

        final var codes = new ArrayList<Contract>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (final var cid : contractIds) {
            final var vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, cid);
            if (fileStore.containsKey(vbk)) {
                final var blob = fileStore.get(vbk);
                if (null != blob) {
                    final var c = new Contract(
                            new TreeSet<>(),
                            blob.getData(),
                            deletedContractIds.contains(cid) ? Validity.DELETED : Validity.ACTIVE);
                    c.ids().add(cid);
                    codes.add(c);
                }
            }
        }
        return codes;
    }
}
