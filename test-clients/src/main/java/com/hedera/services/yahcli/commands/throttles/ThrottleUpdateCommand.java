package com.hedera.services.yahcli.commands.throttles;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "update",
		subcommands = { CommandLine.HelpCommand.class },
		description = "update throttle property on the target network")
public class ThrottleUpdateCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	ThrottlesCommand throttlesCommand;

	@Override
	public Integer call() throws Exception {
		return null;
	}
}
