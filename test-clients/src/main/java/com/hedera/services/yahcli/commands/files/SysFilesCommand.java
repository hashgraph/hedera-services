package com.hedera.services.yahcli.commands.files;

import com.hedera.services.yahcli.Yahcli;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(
		name = "sysfiles",
		subcommands = {
				picocli.CommandLine.HelpCommand.class,
				SysFileDownloadCommand.class
		},
		description = "Perform system file operations")
public class SysFilesCommand implements Callable<Integer> {
	@ParentCommand
	Yahcli yahcli;

	@Override
	public Integer call() throws Exception {
		throw new picocli.CommandLine.ParameterException(
				yahcli.getSpec().commandLine(),
				"Please specify an sysfile subcommand!");
	}

	public Yahcli getYahcli() {
		return yahcli;
	}
}
