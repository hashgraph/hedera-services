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

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "summarize",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Summarizes contents of signed state file")
public class SummarizeSignedStateFileCommand implements Callable<Integer> {
  @ParentCommand private SignedStateCommand signedStateCommand;

  @Override
  public Integer call() throws Exception {
    System.out.println("SummarizeSignedStateFile! <TBD>");
    return 0;
  }
}
