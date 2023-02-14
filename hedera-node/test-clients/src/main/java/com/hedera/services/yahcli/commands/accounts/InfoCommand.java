/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

import com.hedera.services.yahcli.suites.InfoSuite;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "info",
        subcommands = {HelpCommand.class},
        description = "Checks account info")
public class InfoCommand implements Callable<Integer> {
    @ParentCommand AccountsCommand accountsCommand;

    @Parameters(arity = "1..*", paramLabel = "<accounts>", description = "account names or numbers")
    String[] accounts;

    @Override
    public Integer call() throws Exception {
        var config = configFrom(accountsCommand.getYahcli());

        StringBuilder balanceRegister = new StringBuilder();
        String serviceBorder = "---------------------|----------------------|";
        balanceRegister.append(serviceBorder).append("\n");
        balanceRegister.append(String.format("%20s | %20s |%n", "Account Id", "Keys"));
        balanceRegister.append(serviceBorder);

        printTable(balanceRegister);

        var delegate = new InfoSuite(config.asSpecConfig(), accounts);
        delegate.runSuiteSync();

        return 0;
    }

    private void printTable(final StringBuilder balanceRegister) {
        System.out.println(balanceRegister.toString());
    }
}
