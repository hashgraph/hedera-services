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
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder;
import com.hedera.services.yahcli.commands.contract.utils.SignedStateHolder.DumpOperation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "dumprawcontractstate",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Dumps contract state in hex (to stdout)")
public class DumpRawContractStateCommand implements Callable<Integer> {

    @ParentCommand
    private ContractCommand contractCommand;

    @Option(
            names = {"-f", "--file"},
            arity = "1",
            description = "Input signed state file")
    Path inputFile;

    @Option(
            names = {"-o", "--output"},
            arity = "1",
            description = "Output file for contracts' state")
    Path outputFile;

    @Option(
            names = {"-p", "--prefix"},
            description = "Prefix for each contract bytecode line (suitable for finding lines via grep")
    String prefix = "";

    @Option(names = {"-s", "--summary"})
    boolean doSummary;

    @Option(names = {"-c", "--contents"})
    boolean doDumpContents;

    @Override
    public Integer call() throws Exception {

        contractCommand.setupLogging();

        try (final var signedState = new SignedStateHolder(inputFile)) {

            if (doSummary) {
                final var summary = signedState.dumpContractStorage(DumpOperation.SUMMARIZE);
                System.out.println(prefixLines(prefix, summary));
            }

            if (doDumpContents) {
                final var contents = signedState.dumpContractStorage(DumpOperation.CONTENTS);
                if (null != outputFile)
                    FileUtils.writeStringToFile(new File(outputFile.toUri()), contents, StandardCharsets.UTF_8);
                else {
                    System.out.println(prefixLines(prefix, contents));
                }
            }
        }

        return 0;
    }

    private static String prefixLines(@NonNull final String prefix, @NonNull final String multiLineString) {
        if (prefix.isEmpty()) return multiLineString;
        return multiLineString.lines().collect(Collectors.joining("\n" + prefix, prefix, ""));
    }

    @Override
    public String toString() {
        return "DumpRawContractStateCommand{}";
    }
}
