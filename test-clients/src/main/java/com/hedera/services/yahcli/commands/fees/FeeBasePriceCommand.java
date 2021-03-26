package com.hedera.services.yahcli.commands.fees;

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

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.suites.CostOfEveryThingSuite;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "list-base-prices",
		subcommands = { CommandLine.HelpCommand.class },
		description = "List base prices for all operations")
public class FeeBasePriceCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	FeesCommand feesCommand;

	@CommandLine.Parameters(
			arity = "1..*",
			paramLabel = "<services>",
			description = "services ('crypto', 'consensus', 'token', 'file', 'contract', 'scheduled')  \n" +
					"or 'all' to get fees for all basic operations ")
	String[] services;

	@Override
	public Integer call() throws Exception {
		var config = ConfigManager.from(feesCommand.getYahcli());
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config);

		StringBuilder feeTableSB = new StringBuilder();
		String serviceBorder = "-------------------------------|-----------------|\n";
		feeTableSB.append(serviceBorder);
		feeTableSB.append(String.format("%30s |  \t\t |\n", "Transaction and Query Fees"));
		feeTableSB.append(serviceBorder);

		var delegate = new CostOfEveryThingSuite(config.asSpecConfig(), feeTableSB, serviceBorder, services);
		delegate.runSuiteSync();

		printTable(feeTableSB);

		return 0;
	}

	private void printTable(final StringBuilder feeTableSB) {
		System.out.println(feeTableSB.toString());
	}
}
