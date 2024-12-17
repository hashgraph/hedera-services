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

package com.hedera.node.app.statedumpers.contracts;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.state.merkle.StateMetadata;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class ContractBytecodesDumpUtils {
    private static final int ESTIMATED_NUMBER_OF_CONTRACTS = 2_000_000;

    private ContractBytecodesDumpUtils() {
        // Utility class
    }

    public static void dumpModContractBytecodes(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<ContractID>, OnDiskValue<Bytecode>> contracts,
            final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts,
            final StateMetadata<AccountID, Account> stateMetadata,
            @NonNull final DumpCheckpoint checkpoint) {
        final var dumpableAccounts = gatherModContracts(contracts, accounts, stateMetadata);
        final var sb = generateReport(dumpableAccounts);
        try (@NonNull final var writer = new Writer(path)) {
            writer.writeln(sb.toString());
            System.out.printf(
                    "=== contract bytecodes report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    public static Contracts gatherModContracts(
            VirtualMap<OnDiskKey<ContractID>, OnDiskValue<Bytecode>> contracts,
            final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts,
            final StateMetadata<AccountID, Account> stateMetadata) {
        final var contractsToReturn = new ConcurrentLinkedQueue<BBMContract>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();

        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    contracts,
                    p -> {
                        processed.incrementAndGet();
                        contractsToReturn.add(fromMod(p.left(), p.right(), accounts, stateMetadata));
                    },
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of contracts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final var contractArr = contractsToReturn.toArray(new BBMContract[0]);
        final var deletedContracts = contractsToReturn.stream()
                .filter(c -> c.validity() == Validity.DELETED)
                .map(BBMContract::canonicalId)
                .toList();
        System.out.printf("=== %d contracts iterated over (%d saved)%n", processed.get(), contractArr.length);
        return new Contracts(List.of(contractArr), deletedContracts, contractArr.length - deletedContracts.size());
    }

    public static BBMContract fromMod(
            OnDiskKey<ContractID> id,
            OnDiskValue<Bytecode> bytecode,
            final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts,
            final StateMetadata<AccountID, Account> stateMetadata) {
        final var isDeleted = accounts.get(new OnDiskKey<>(
                        stateMetadata.onDiskKeyClassId(),
                        stateMetadata.stateDefinition().keyCodec(),
                        AccountID.newBuilder()
                                .accountNum(id.getKey().contractNum())
                                .build()))
                .getValue()
                .deleted();

        final var c = new BBMContract(
                new TreeSet<>(),
                bytecode.getValue().code().toByteArray(),
                isDeleted ? Validity.DELETED : Validity.ACTIVE);
        if (id.getKey().contractNum() != null) {
            c.ids().add(id.getKey().contractNum().intValue());
        }
        return c;
    }

    public static StringBuilder generateReport(Contracts knownContracts) {
        if (knownContracts.contracts().isEmpty()) {
            return new StringBuilder();
        }

        var r = getNonTrivialContracts(knownContracts);
        var contractsWithBytecode = r.getLeft();
        var zeroLengthContracts = r.getRight();

        final var totalContractsRegisteredWithAccounts = contractsWithBytecode.registeredContractsCount();
        final var totalContractsPresentInFileStore =
                contractsWithBytecode.contracts().size();
        int totalUniqueContractsPresentInFileStore;

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
        return sb;
    }

    /** Returns all contracts with bytecodes from the signed state, plus the ids of contracts with 0-length bytecodes.
     *
     * Returns both the set of all contract ids with their bytecode, and the total number of contracts registered
     * in the signed state file.  The latter number may be larger than the number of contracts-with-bytecodes
     * returned because some contracts known to accounts are not present in the file store.
     */
    @NonNull
    private static Pair<Contracts, List<Integer>> getNonTrivialContracts(Contracts contracts) {
        final var zeroLengthContracts = new ArrayList<Integer>(10000);
        final var knownContracts = new ArrayList<>(contracts.contracts());
        knownContracts.removeIf(contract -> {
            if (0 == contract.bytecode().length) {
                zeroLengthContracts.addAll(contract.ids());
                return true;
            }
            return false;
        });
        return Pair.of(
                new Contracts(knownContracts, contracts.deletedContracts(), contracts.registeredContractsCount()),
                zeroLengthContracts);
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
        final var uniqueContracts = new ArrayList<BBMContract>(contractsByBytecode.size());
        for (final var kv : contractsByBytecode.entrySet()) {
            uniqueContracts.add(new BBMContract(kv.getValue(), kv.getKey().array(), Validity.ACTIVE));
        }

        return Pair.of(
                new Contracts(uniqueContracts, contracts.deletedContracts(), contracts.registeredContractsCount()),
                zeroLengthContracts);
    }

    private static int estimateReportSize(@NonNull Contracts contracts) {
        int totalBytecodeSize = contracts.contracts().stream()
                .map(BBMContract::bytecode)
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
                .sorted(Comparator.comparingInt(BBMContract::canonicalId))
                .forEach(contract -> appendFormattedContractLine(sb, contract));
    }

    private static final HexFormat hexer = HexFormat.of().withUpperCase();

    /** Format a single contract line - may want any id, may want _all_ ids */
    private static void appendFormattedContractLine(
            @NonNull final StringBuilder sb, @NonNull final BBMContract contract) {
        sb.append(hexer.formatHex(contract.bytecode()));
        sb.append('\t');
        sb.append(contract.canonicalId());
        sb.append('\t');
        sb.append(contract.ids().stream().map(Object::toString).collect(Collectors.joining(",")));
        sb.append('\n');
    }
}
