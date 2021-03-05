package com.hedera.services.yahcli.commands.fees;

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.suites.CostOfEveryThingSuite;
import picocli.CommandLine;

import java.util.HashMap;
import java.util.Map;
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
