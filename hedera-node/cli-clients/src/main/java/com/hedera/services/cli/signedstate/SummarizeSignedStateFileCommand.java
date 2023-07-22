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

import com.hedera.services.cli.signedstate.SignedStateHolder.Contract;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "summarize",
        mixinStandardHelpOptions = true,
        description = "Summarizes contents of signed state file (to stdout)")
@SubcommandOf(SignedStateCommand.class)
public class SummarizeSignedStateFileCommand extends AbstractCommand {

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            description = "Input signed state file")
    Path inputFile;

    @CommandLine.Option(
            names = {"-c", "--config"},
            description = "A path to where a configuration file can be found. If not provided then defaults are used.")
    private void setConfigurationPath(@NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        configurationPaths.forEach(this::pathMustExist);
        this.configurationPaths = configurationPaths;
    }

    private List<Path> configurationPaths = List.of();

    @Override
    public Integer call() {

        try (final var signedState = new SignedStateHolder(inputFile, configurationPaths)) {

            final var contractsInfo = signedState.getContracts();
            final var nContractsWithBytecodeFound = contractsInfo.contracts().size();
            final var nDeletedContracts = contractsInfo.deletedContracts().size();
            final var bytesFound = contractsInfo.contracts().stream()
                    .map(Contract::bytecode)
                    .mapToInt(Array::getLength)
                    .sum();

            System.out.printf(
                    "signed-state summarize: %d contractIds found (%d deleted), %d contracts with bytecode, %d bytes total%n",
                    contractsInfo.registeredContractsCount(),
                    nDeletedContracts,
                    nContractsWithBytecodeFound,
                    bytesFound);
        }
        return 0;
    }
}
