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

package com.hedera.services.yahcli.commands.contract.subcommands;

import com.hedera.services.yahcli.commands.contract.ContractCommand;
import com.hedera.services.yahcli.commands.contract.utils.ByteArrayAsKey;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder.Contract;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder.Contracts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "dumprawcontracts",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Dumps contract bytecodes in hex (to stdout)")
public class DumpRawContractsCommand implements Callable<Integer> {
    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 2_000;

    @ParentCommand
    private ContractCommand contractCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            description = "Input signed state file")
    Path inputFile;

    @Option(
            names = {"-u", "--unique"},
            description = "Emit each contract only once (not matter how many times it occurs)")
    boolean emitUnique;

    @Option(
            names = {"-p", "--prefix"},
            description = "Prefix for each contract bytecode line (suitable for finding lines via grep")
    String prefix = "";

    @Option(
            names = {"-#", "--with-ids"},
            description = "Output contract ids too")
    boolean withIds;

    @Override
    public Integer call() throws Exception {

        contractCommand.setupLogging();

        try (final var signedState = new SignedStateHolder(inputFile)) {

            final var zeroLengthCount = new int[1];

            var r = getNonTrivialContracts(signedState, zeroLengthCount);

            final var totalContractsRegisteredWithAccounts = r.registeredContractsCount();
            final var totalContractsPresentInFileStore = r.contracts().size();
            int totalUniqueContractsPresentInFileStore = totalContractsPresentInFileStore;

            if (emitUnique) {
                r = uniquifyContracts(r);
                totalUniqueContractsPresentInFileStore = r.contracts().size();
            }

            final var formattedContracts = formatContractLines(r);

            System.out.printf(
                    "%d registered contracts, %d with bytecode (%d are 0-length)%s%n",
                    totalContractsRegisteredWithAccounts,
                    totalContractsPresentInFileStore + zeroLengthCount[0],
                    zeroLengthCount[0],
                    emitUnique ? ", %d unique (by bytecode)".formatted(totalUniqueContractsPresentInFileStore) : "");
            for (final var s : formattedContracts) System.out.println(s);
        }

        return 0;
    }

    /** Format a collection of pairs of a set of contract ids with their associated bytecode */
    @NonNull
    private Collection</*@NonNull*/ String> formatContractLines(@NonNull final Contracts contracts) {
        final Collection</*@NonNull*/ String> formattedContracts = new ArrayList<>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (final var contract : contracts.contracts()) {
            final var s = formatContractLine(contract);
            formattedContracts.add(s);
        }
        return formattedContracts;
    }

    private final HexFormat hexer = HexFormat.of().withUpperCase();

    /** Format a single contract line - may have a prefix, may want any id, may want _all_ ids */
    @NonNull
    private String formatContractLine(@NonNull final Contract contract) {
        final var sb = new StringBuilder(
                contract.bytecode().length * 2 + prefix.length() + 50 /*more than enuf for the rest*/);
        if (!prefix.isEmpty()) {
            sb.append(prefix);
            sb.append('\t');
        }

        sb.append(hexer.formatHex(contract.bytecode()));

        final var ids = contract.ids();
        if (withIds && !ids.isEmpty()) {
            // Output canonical id - we choose the minimum id (so it is deterministic)
            final var sortedIds = ids.stream().sorted().toList();
            sb.append('\t');
            sb.append(sortedIds.get(0));

            // Now output _all_ ids
            sb.append('\t');
            sb.append(String.join(",", sortedIds.stream().map(Object::toString).toList()));
        }

        return sb.toString();
    }

    /**
     * Returns, out of the signed state store, all unique contracts (by their bytecode)
     *
     * <p>Returns the set of all unique contract (by their bytecode), each contract with _all_ of
     * the contract ids associated with it. Also returns the total number of contracts registered in
     * the signed state file. The latter number may be larger than the number of
     * contracts-with-bytecodes returned because some contracts known to accounts are not present in
     * the file store.
     */
    @NonNull
    private Contracts uniquifyContracts(@NonNull final Contracts contracts) {

        // First, create a map where the bytecode is the key (have to wrap the array for that) and
        // the value is a set of all contract ids associated with that bytecode.
        final var contractsByBytecode = new HashMap<ByteArrayAsKey, Set<Integer>>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (var contract : contracts.contracts()) {
            final var bytecode = contract.bytecode();
            final var cids = contract.ids();
            contractsByBytecode.compute(new ByteArrayAsKey(bytecode), (k, v) -> {
                if (null == v) {
                    v = new HashSet<>();
                }
                v.addAll(cids);
                return v;
            });
        }

        // Second, flatten that map into a collection.
        final var uniqueContracts = new ArrayList<Contract>(contractsByBytecode.size());
        for (final var kv : contractsByBytecode.entrySet()) {
            uniqueContracts.add(new Contract(kv.getValue(), kv.getKey().array()));
        }

        return new Contracts(uniqueContracts, contracts.registeredContractsCount());
    }

    /**
     * Returns, out of the signed state store, all contracts
     *
     * <p>Returns both the set of all contract ids with their bytecode, and the total number of
     * contracts registered in the signed state file. The latter number may be larger than the
     * number of contracts-with-bytecodes returned because some contracts known to accounts are not
     * present in the file store.
     */
    @NonNull
    private Contracts getNonTrivialContracts(
            @NonNull final SignedStateHolder signedState, final @NonNull int[] outZeroLengthCount) {

        final var knownContracts = signedState.getContracts();

        // Remove (and report) 0-length contracts (what's that about? probably some user's error?)
        knownContracts.contracts().removeIf(contract -> {
            if (0 == contract.bytecode().length) {
                outZeroLengthCount[0]++;
                System.out.printf(
                        "!!! 0-byte bytecode for %d%n",
                        contract.ids().iterator().next());
                return true;
            }
            return false;
        });

        return knownContracts;
    }
}
