package com.hedera.services.yahcli.commands.throttles;

import com.hedera.services.yahcli.Yahcli;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "throttles",
		subcommands = {
				CommandLine.HelpCommand.class,
				ThrottleUpdateCommand.class
		},
		description = "Alter throttle settings")
public class ThrottlesCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	Yahcli yahcli;

	@Override
	public Integer call() throws Exception {
		throw new CommandLine.ParameterException(
				yahcli.getSpec().commandLine(),
				"Please specify an throttles subcommand!");
	}

	public Yahcli getYahcli() {
		return yahcli;
	}
}
