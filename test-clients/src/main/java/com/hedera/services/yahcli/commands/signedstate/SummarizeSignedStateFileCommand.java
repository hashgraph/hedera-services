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

import static com.hedera.services.yahcli.commands.signedstate.SignedStateHolder.getContracts;

import com.hedera.services.yahcli.commands.signedstate.SignedStateHolder.Contract;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "summarize",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Summarizes contents of signed state file (to stdout)")
public class SummarizeSignedStateFileCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            description = "Input signed state file")
    Path inputFile;

    @Override
    public Integer call() throws Exception {
        final var knownContracts = getContracts(inputFile);

        final int contractsWithBytecodeFound = knownContracts.contracts().size();
        final var bytesFound =
                knownContracts.contracts().stream()
                        .map(Contract::bytecode)
                        .mapToInt(Array::getLength)
                        .sum();

        System.out.printf(
                "SummarizeSignedStateFile: %d contractIDs found %d contracts found in file store"
                        + " (%d bytes total)",
                knownContracts.registeredContractsCount(), contractsWithBytecodeFound, bytesFound);

        return 0;
    }
}
