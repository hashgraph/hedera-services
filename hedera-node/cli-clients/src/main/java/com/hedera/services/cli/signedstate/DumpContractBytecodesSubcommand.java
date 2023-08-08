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

package com.hedera.services.cli.signedstate;

import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpStateCommand.Uniqify;
import com.hedera.services.cli.signedstate.DumpStateCommand.WithIds;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.signedstate.SignedStateHolder.Contract;
import com.hedera.services.cli.signedstate.SignedStateHolder.Contracts;
import com.hedera.services.cli.signedstate.SignedStateHolder.Validity;
import com.hedera.services.cli.utils.ByteArrayAsKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

/** Dump all the contract bytecodes in a signed state file in textual format, (optionally) deduped, and (optionally)
 * with their contract ids.  Output is deterministic for a signed state - sorted by contract id, etc. - so that
 * comparisons can be made between two signed states that are similar (e.g., mono-service vs modularized service when
 * the same events are run through them).
 */
@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpContractBytecodesSubcommand {

    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 500_000;

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path bytecodePath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Uniqify uniqify,
            @NonNull final WithIds withIds,
            @NonNull final Verbosity verbosity) {
        new DumpContractBytecodesSubcommand(state, bytecodePath, emitSummary, uniqify, withIds, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path bytecodePath;

    @NonNull
    final EmitSummary emitSummary;

    @NonNull
    final Uniqify uniqify;

    @NonNull
    final WithIds withIds;

    @NonNull
    final Verbosity verbosity;

    DumpContractBytecodesSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path bytecodePath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Uniqify uniqify,
            @NonNull final WithIds withIds,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.bytecodePath = bytecodePath;
        this.emitSummary = emitSummary;
        this.uniqify = uniqify;
        this.withIds = withIds;
        this.verbosity = verbosity;
    }

    void doit() {

        var r = getNonTrivialContracts();
        var contractsWithBytecode = r.getLeft();
        var zeroLengthContracts = r.getRight();

        final var totalContractsRegisteredWithAccounts = contractsWithBytecode.registeredContractsCount();
        final var totalContractsPresentInFileStore =
                contractsWithBytecode.contracts().size();
        int totalUniqueContractsPresentInFileStore = totalContractsPresentInFileStore;

        if (uniqify == DumpStateCommand.Uniqify.YES) {
            r = uniqifyContracts(contractsWithBytecode, zeroLengthContracts);
            contractsWithBytecode = r.getLeft();
            zeroLengthContracts = r.getRight();
            totalUniqueContractsPresentInFileStore =
                    contractsWithBytecode.contracts().size();
        }

        final var sb = new StringBuilder(estimateReportSize(contractsWithBytecode));
        if (emitSummary == DumpStateCommand.EmitSummary.YES) {
            sb.append("%d registered contracts, %d with bytecode (%d are zero-length)%s, %d deleted contracts%n"
                    .formatted(
                            totalContractsRegisteredWithAccounts,
                            totalContractsPresentInFileStore + zeroLengthContracts.size(),
                            zeroLengthContracts.size(),
                            uniqify == DumpStateCommand.Uniqify.YES
                                    ? ", %d unique (by bytecode)".formatted(totalUniqueContractsPresentInFileStore)
                                    : "",
                            contractsWithBytecode.deletedContracts().size()));
        }
        appendFormattedContractLines(sb, contractsWithBytecode);

        if (verbosity == Verbosity.VERBOSE) System.out.printf("=== Have %d byte report%n", sb.length());

        writeReportToFile(sb.toString());
    }

    int estimateReportSize(@NonNull Contracts contracts) {
        int totalBytecodeSize = contracts.contracts().stream()
                .map(Contract::bytecode)
                .mapToInt(bc -> bc.length)
                .sum();
        // Make a swag based on how many contracts there are plus bytecode size - each line has not just the bytecode
        // but the list of contract ids, so the estimated size of the file accounts for the bytecodes (as hex) and the
        // contract ids (as decimal)
        int reportSizeEstimate = contracts.registeredContractsCount() * 20 + totalBytecodeSize * 2;
        if (verbosity == Verbosity.VERBOSE) System.out.printf("=== Estimating %d byte report%n", reportSizeEstimate);
        return reportSizeEstimate;
    }

    void writeReportToFile(@NonNull String report) {
        try (final PrintWriter out = new PrintWriter(bytecodePath.toFile(), StandardCharsets.UTF_8)) {
            out.print(report);
            out.flush();
        } catch (final FileNotFoundException ex) {
            System.err.printf("*** Cannot create '%s' for writing%n", bytecodePath);
        } catch (final IOException ex) {
            System.err.printf("*** Exception when trying to write '%s'%n", bytecodePath);
            throw new UncheckedIOException(ex); // This is a CLI program: Java will print the stack trace properly
        }
    }

    /** Returns all _unique_ contracts (by their bytecode) from the signed state.
     *
     * Returns the set of all unique contracts (by their bytecode), each contract bytecode with _all_ of the
     * contract ids that have that bytecode. Also returns the total number of contracts registered in the signed
     * state.  The latter number may be larger than the number of contracts-with-bytecodes because some contracts
     * known to accounts are not present in the file store.  Deleted contracts are _omitted_.
     */
    @NonNull
    Pair<Contracts, List<Integer>> uniqifyContracts(
            @NonNull final Contracts contracts, @NonNull final List<Integer> zeroLengthContracts) {
        // First create a map where the bytecode is the key (have to wrap the byte[] for that) and the value is
        // the set of all contract ids that have that bytecode
        final var contractsByBytecode = new HashMap<ByteArrayAsKey, TreeSet<Integer>>(ESTIMATED_NUMBER_OF_CONTRACTS);
        for (var contract : contracts.contracts()) {
            if (contract.validity() == Validity.DELETED) continue;
            final var bytecode = contract.bytecode();
            final var cids = contract.ids();
            contractsByBytecode.compute(new ByteArrayAsKey(bytecode), (k, v) -> {
                if (v == null) v = new TreeSet<>();
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

    /** Returns all contracts with bytecodes from the signed state, plus the ids of contracts with 0-length bytecodes.
     *
     * Returns both the set of all contract ids with their bytecode, and the total number of contracts registered
     * in the signed state file.  The latter number may be larger than the number of contracts-with-bytecodes
     * returned because some contracts known to accounts are not present in the file store.
     */
    @NonNull
    Pair<Contracts, List<Integer>> getNonTrivialContracts() {
        final var knownContracts = state.getContracts();
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

    /** Format a collection of pairs of a set of contract ids with their associated bytecode */
    void appendFormattedContractLines(@NonNull final StringBuilder sb, @NonNull final Contracts contracts) {
        contracts.contracts().stream()
                .sorted(Comparator.comparingInt(Contract::canonicalId))
                .forEach(contract -> appendFormattedContractLine(sb, contract));
    }

    private final HexFormat hexer = HexFormat.of().withUpperCase();

    /** Format a single contract line - may want any id, may want _all_ ids */
    void appendFormattedContractLine(@NonNull final StringBuilder sb, @NonNull final Contract contract) {
        sb.append(hexer.formatHex(contract.bytecode()));

        if (withIds == DumpStateCommand.WithIds.YES && !contract.ids().isEmpty()) {
            sb.append('\t');
            sb.append(contract.canonicalId());
            sb.append('\t');
            sb.append(contract.ids().stream().map(Object::toString).collect(Collectors.joining(",")));
            sb.append('\n');
        }
    }
}
