package com.hedera.services.yahcli.commands.fees;

import com.hedera.services.yahcli.Yahcli;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "fees",
		subcommands = {
				picocli.CommandLine.HelpCommand.class,
				FeeBasePriceCommand.class
		},
		description = "Perform system fee operations")
public class FeesCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	Yahcli yahcli;

	@Override
	public Integer call() throws Exception {
		throw new picocli.CommandLine.ParameterException(
				yahcli.getSpec().commandLine(),
				"Please specify a fee subcommand!");
	}

	public Yahcli getYahcli() {
		return yahcli;
	}
}
