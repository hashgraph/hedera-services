package com.hedera.services.yahcli.commands.throttles;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "download",
		subcommands = { CommandLine.HelpCommand.class },
		description = "download throttle property on the target network")
public class ThrottleDownloadCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	ThrottlesCommand throttlesCommand;

	@Override
	public Integer call() throws Exception {
		return null;
	}
}