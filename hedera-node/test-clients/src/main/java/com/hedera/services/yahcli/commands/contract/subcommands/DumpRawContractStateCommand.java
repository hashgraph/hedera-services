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

import static com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder.getContracts;

import com.hedera.services.yahcli.commands.contract.ContractCommand;
import com.hedera.services.yahcli.commands.contract.utils.ByteArrayAsKey;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder.Contract;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder.Contracts;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder.DumpOperation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Callable;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "dumprawcontractstate",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Dumps contract state in hex (to stdout)")
public class DumpRawContractStateCommand implements Callable<Integer> {
    static final int ESTIMATED_NUMBER_OF_CONTRACTS = 2_000;

    @ParentCommand
    private ContractCommand contractCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            description = "Input signed state file")
    Path inputFile;

    @Option(
            names = {"-p", "--prefix"},
            description = "Prefix for each contract bytecode line (suitable for finding lines via grep")
    String prefix = "";

    @Option(
            names = {"-#", "--with-ids"},
            description = "Output contract ids too")
    boolean withIds;

    @Option(
            names = {"--suppress-logs"},
            description = "Suppress all logging")
    boolean suppressAllLogging;

    @Override
    public Integer call() throws Exception {

        // Refactor so the SignedStateHolder is dehydrated only once!

        if (suppressAllLogging) setRootLogLevel(Level.ERROR);

        final var zeroLengthCount = new int[1];

        final var signedState = new SignedStateHolder(inputFile);
        final var summary = signedState.dumpContractStorage(DumpOperation.SUMMARIZE);
        System.out.println(summary);
        final var contents = signedState.dumpContractStorage(DumpOperation.CONTENTS);
        System.out.println("%sfull contents %d characters".formatted(prefix, contents.length()));
        FileUtils.writeStringToFile(
                new File("./contracts-contents.txt"), contents, StandardCharsets.UTF_8); // TODO filename

        return 0;
    }

    @Override
    public String toString() {
        return "DumpRawContractStateCommand{" + "hexer=" + hexer + '}';
    }

    private final HexFormat hexer = HexFormat.of().withUpperCase();

    @NonNull
    protected byte[] getState(final int cid) {
        return new byte[0];
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
    private Contracts getNonTrivialContracts(@NonNull final Path inputFile, final @NonNull int[] outZeroLengthCount)
            throws Exception {

        final var knownContracts = getContracts(inputFile);

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

    // BTW, yahcli log configuration file is at `test-clients/src/main/resources/log4j2.xml`
    private void setRootLogLevel(final Level level) {
        final var logger = LogManager.getRootLogger();
        Configurator.setAllLevels(logger.getName(), level);
    }
}
