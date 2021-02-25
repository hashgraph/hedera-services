package com.hedera.services.yahcli.commands.accounts;

import com.hedera.services.yahcli.Yahcli;
import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "accounts",
		subcommands = {
				HelpCommand.class,
				BalanceCommand.class
		},
		description = "Perform account operations")
public class AccountsCommand implements Callable<Integer> {
	@ParentCommand
	Yahcli yahcli;

	@Override
	public Integer call() throws Exception {
		throw new CommandLine.ParameterException(
				yahcli.getSpec().commandLine(),
				"Please specify an accounts subcommand!");
	}

	public Yahcli getYahcli() {
		return yahcli;
	}
}
