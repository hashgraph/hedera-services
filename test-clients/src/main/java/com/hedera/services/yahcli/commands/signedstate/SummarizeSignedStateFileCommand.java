/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import com.hedera.services.utils.EntityNum;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "summarize",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Summarizes contents of signed state file")
public class SummarizeSignedStateFileCommand implements Callable<Integer> {
    @ParentCommand private SignedStateCommand signedStateCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            paramLabel = "INPUT-SIGNED-STATE-FILE",
            description = "Input signed state file")
    Path inputFile;

    @Option(
            names = {"-s", "--summarize"},
            description = "Provide summary of what was in the input file")
    Optional<Boolean> doSummary;

    @Override
    public Integer call() throws Exception {
        System.out.printf(
                "SummarizeSignedStateFile: input file %s summarize %s%n",
                inputFile.toString(), doSummary.isPresent() ? "YES" : "NO");

        var r = getContracts(inputFile);

        int contractsFound = r.right;
        var contractContents = r.left;
        int contractsWithBytecodeFound = contractContents.size();
        int bytesFound =
                contractContents.values().stream().collect(Collectors.summingInt((a) -> a.length));

        System.out.printf(
                "SummarizeSignedStateFile: %d contractIDs found %d contracts found in file store"
                        + " (%d bytes total)",
                contractsFound, contractsWithBytecodeFound, bytesFound);

        return 0;
    }

    private @NotNull ImmutablePair<Map<EntityNum, byte[]>, Integer> getContracts(Path inputFile)
            throws Exception {
        int contractsFound = 0;
        try (var signedState = new SignedStateHolder(inputFile)) {
            var contractIds = signedState.getAllKnownContracts();
            contractsFound = contractIds.size();
            var contractContents = signedState.getAllContractContents(contractIds);
            return ImmutablePair.of(contractContents, contractsFound);
        }
    }
}
