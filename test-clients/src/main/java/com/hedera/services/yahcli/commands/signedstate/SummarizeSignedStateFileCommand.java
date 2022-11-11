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

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import com.swirlds.platform.state.signed.SignedStateFileReader;

@Command(
        name = "summarize",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Summarizes contents of signed state file")
public class SummarizeSignedStateFileCommand implements Callable<Integer> {
  @ParentCommand private SignedStateCommand signedStateCommand;

  @Option(names = {"-f","--file"}, paramLabel = "INPUT-SIGNED-STATE-FILE", description = "Input signed state file")
  Optional<Path> inputFile;

  @Option(names = {"-o", "--out"}, paramLabel = "OUTPUT-SIGNED-STATE-FILE", description = "Output signed state file")
  Optional<Path> outputFile;

  @Option(names = {"-s", "--summarize"}, description = "Provide summary of what was in the input file")
  Optional<Boolean> doSummary;

  @Override
  public Integer call() throws Exception {
    System.out.println("SummarizeSignedStateFile! <TBD>");
    System.out.printf("SummarizeSignedStateFile: input file %s output file %s summarize %s%n",
                      inputFile.isPresent() ? inputFile.get().toString() : "<NONE>",
                      outputFile.isPresent() ? outputFile.get().toString() : "<NONE>",
                      doSummary.isPresent() ? "YES" : "NO");

    // Must have an input file (TODO: use stdin if no input file specified)
    if (inputFile.isEmpty()) return 1;

    var signedPair = SignedStateFileReader.readStateFile(inputFile.get());


    return 0;
  }
}
