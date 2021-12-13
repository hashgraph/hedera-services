package com.hedera.services.yahcli.commands.accounts;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.yahcli.suites.BalanceSuite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

@Command(
		name = "send",
		subcommands = { HelpCommand.class },
		description = "Transfers funds from the payer to a target account")
public class SendCommand implements Callable<Integer> {
	@ParentCommand
	AccountsCommand accountsCommand;

	@CommandLine.Option(names = { "-d", "--denomination" },
			paramLabel = "denomination",
			description = "{tinybar|hbar|kilobar}",
			defaultValue = "hbar")
	String denomination;

	@Parameters(
			index = "0",
			paramLabel = "<beneficiary>",
			description = "account to receive the funds")
	String beneficiary;
	@Parameters(
			index = "1",
			paramLabel = "<beneficiary>",
			description = "account to receive the funds")
	Long amount;

	@Override
	public Integer call() throws Exception {
		var config = configFrom(accountsCommand.getYahcli());

		var delegate = new BalanceSuite(config.asSpecConfig(), beneficiary);
		delegate.runSuiteSync();

		return 0;
	}

	private void printTable(final StringBuilder balanceRegister) {
		System.out.println(balanceRegister.toString());
	}
}
