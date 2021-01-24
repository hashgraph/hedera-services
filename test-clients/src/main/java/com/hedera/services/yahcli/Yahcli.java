package com.hedera.services.yahcli;

import com.hedera.services.yahcli.commands.BalanceCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(
		name = "yahcli",
		subcommands = {
				HelpCommand.class,
				BalanceCommand.class
		},
		description = "Perform operations against well-known entities on a Hedera Services network ")
public class Yahcli implements Callable<Integer> {
	@Spec
	CommandSpec spec;

	@Option(names = { "-n", "--network" },
			paramLabel = "target network")
	String net;

	@Option(names = { "-p", "--payer" },
			paramLabel = "payer")
	String payer;

	@Option(names = { "-c", "--config" },
			paramLabel = "yahcli YAML",
			defaultValue = "config.yml")
	String configLoc;

	@Override
	public Integer call() throws Exception {
		throw new ParameterException(spec.commandLine(), "Please specify a subcommand!");
	}

	public static void main(String... args) {
		int rc = new CommandLine(new Yahcli()).execute(args);
		System.exit(rc);
	}

	public String getNet() {
		return net;
	}

	public String getPayer() {
		return payer;
	}

	public String getConfigLoc() {
		return configLoc;
	}

	public CommandSpec getSpec() {
		return spec;
	}
}
