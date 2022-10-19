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
package com.hedera.services.yahcli.commands.accounts;

import com.hedera.services.yahcli.Yahcli;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(
        name = "accounts",
        subcommands = {
            HelpCommand.class,
            BalanceCommand.class,
            RekeyCommand.class,
            SendCommand.class,
            CreateCommand.class,
            StakeCommand.class
        },
        description = "Performs account operations")
public class AccountsCommand implements Callable<Integer> {
    @ParentCommand Yahcli yahcli;

    @Override
    public Integer call() throws Exception {
        throw new CommandLine.ParameterException(
                yahcli.getSpec().commandLine(), "Please specify an accounts subcommand!");
    }

    public Yahcli getYahcli() {
        return yahcli;
    }
}
