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

import static com.hedera.node.app.bbm.contracts.ContractUtils.ESTIMATED_NUMBER_OF_CONTRACTS;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.bbm.DumpCheckpoint;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.virtual.ContractValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class ContractBytecodesDumpUtils {

    private ContractBytecodesDumpUtils() {
        // Utility class
    }

    public static void dumpModContractBytecodes(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<ContractID>, OnDiskValue<ContractValue>> contracts,
            @NonNull final DumpCheckpoint checkpoint) {}

    public static void dumpMonoContractBytecodes(
            @NonNull final Path path,
            @NonNull final VirtualMap<EntityNumVirtualKey, OnDiskAccount> accounts,
            @NonNull final VirtualMapLike<VirtualBlobKey, VirtualBlobValue> files,
            @NonNull final DumpCheckpoint checkpoint) {

        final var accountAdapter = AccountStorageAdapter.fromOnDisk(VirtualMapLike.from(accounts));

        final var knownContracts = ContractUtils.getContracts(files, accountAdapter);

        var r = getNonTrivialContracts(knownContracts);
        var contractsWithBytecode = r.getLeft();
        var zeroLengthContracts = r.getRight();

        final var totalContractsRegisteredWithAccounts = contractsWithBytecode.registeredContractsCount();
        final var totalContractsPresentInFileStore =
                contractsWithBytecode.contracts().size();
        int totalUniqueContractsPresentInFileStore = totalContractsPresentInFileStore;

        r = uniqifyContracts(contractsWithBytecode, zeroLengthContracts);
        contractsWithBytecode = r.getLeft();
        zeroLengthContracts = r.getRight();
        totalUniqueContractsPresentInFileStore =
                contractsWithBytecode.contracts().size();

        // emitSummary
        final var sb = new StringBuilder(estimateReportSize(contractsWithBytecode));
        sb.append("%d registered contracts, %d with bytecode (%d are zero-length)%s, %d deleted contracts%n"
                .formatted(
                        totalContractsRegisteredWithAccounts,
                        totalContractsPresentInFileStore + zeroLengthContracts.size(),
                        zeroLengthContracts.size(),
                        ", %d unique (by bytecode)".formatted(totalUniqueContractsPresentInFileStore),
                        contractsWithBytecode.deletedContracts().size()));

        appendFormattedContractLines(sb, contractsWithBytecode);

        try (@NonNull final var writer = new Writer(path)) {
            writer.writeln(sb.toString());
            System.out.printf(
                    "=== mono contract bytecodes report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    /** Returns all contracts with bytecodes from the signed state, plus the ids of contracts with 0-length bytecodes.
     *
     * Returns both the set of all contract ids with their bytecode, and the total number of contracts registered
     * in the signed state file.  The latter number may be larger than the number of contracts-with-bytecodes
     * returned because some contracts known to accounts are not present in the file store.
     */
    @NonNull
    private static Pair<Contracts, List<Integer>> getNonTrivialContracts(Contracts knownContracts) {
        final var zeroLengthContracts = new ArrayList<Integer>(10000);
        knownContracts.contracts().removeIf(contract -> {
            if (0 == contract.bytecode().length) {
                zeroLengthContracts.addAll(contract.ids());
                return true;
            }
            return false;
        });
        return Pair.of(knownContracts, zeroLengthContracts);
    }

    /** Returns all _unique_ contracts (by their bytecode) from the signed state.
     *
     * Returns the set of all unique contracts (by their bytecode), each contract bytecode with _all_ of the
     * contract ids that have that bytecode. Also returns the total number of contracts registered in the signed
     * state.  The latter number may be larger than the number of contracts-with-bytecodes because some contracts
     * known to accounts are not present in the file store.  Deleted contracts are _omitted_.
     */
    @NonNull
    private static Pair<Contracts, List<Integer>> uniqifyContracts(
            @NonNull final Contracts contracts, @NonNull final List<Integer> zeroLengthContracts) {
        // First create a map where the bytecode is the key (have to wrap the byte[] for that) and the value is
        // the set of all contract ids that have that bytecode
        final var contractsByBytecode = new HashMap<ByteArrayAsKey, TreeSet<Integer>>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (var contract : contracts.contracts()) {
            if (contract.validity() == Validity.DELETED) {
                continue;
            }
            final var bytecode = contract.bytecode();
            final var cids = contract.ids();
            contractsByBytecode.compute(new ByteArrayAsKey(bytecode), (k, v) -> {
                if (v == null) {
                    v = new TreeSet<>();
                }
                v.addAll(cids);
                return v;
            });
        }

        // Second, flatten that map into a collection
        final var uniqueContracts = new ArrayList<Contract>(contractsByBytecode.size());
        for (final var kv : contractsByBytecode.entrySet()) {
            uniqueContracts.add(new Contract(kv.getValue(), kv.getKey().array(), Validity.ACTIVE));
        }

        return Pair.of(
                new Contracts(uniqueContracts, contracts.deletedContracts(), contracts.registeredContractsCount()),
                zeroLengthContracts);
    }

    private static int estimateReportSize(@NonNull Contracts contracts) {
        int totalBytecodeSize = contracts.contracts().stream()
                .map(Contract::bytecode)
                .mapToInt(bc -> bc.length)
                .sum();
        // Make a swag based on how many contracts there are plus bytecode size - each line has not just the bytecode
        // but the list of contract ids, so the estimated size of the file accounts for the bytecodes (as hex) and the
        // contract ids (as decimal)
        return contracts.registeredContractsCount() * 20 + totalBytecodeSize * 2;
    }

    /** Format a collection of pairs of a set of contract ids with their associated bytecode */
    private static void appendFormattedContractLines(
            @NonNull final StringBuilder sb, @NonNull final Contracts contracts) {
        contracts.contracts().stream()
                .sorted(Comparator.comparingInt(Contract::canonicalId))
                .forEach(contract -> appendFormattedContractLine(sb, contract));
    }

    private static final HexFormat hexer = HexFormat.of().withUpperCase();

    /** Format a single contract line - may want any id, may want _all_ ids */
    private static void appendFormattedContractLine(@NonNull final StringBuilder sb, @NonNull final Contract contract) {
        sb.append(hexer.formatHex(contract.bytecode()));
        sb.append('\t');
        sb.append(contract.canonicalId());
        sb.append('\t');
        sb.append(contract.ids().stream().map(Object::toString).collect(Collectors.joining(",")));
        sb.append('\n');
    }
}
