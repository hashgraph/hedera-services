package com.hedera.services.yahcli;

import com.hedera.services.yahcli.commands.accounts.AccountsCommand;
import com.hedera.services.yahcli.commands.accounts.BalanceCommand;
import com.hedera.services.yahcli.commands.fees.FeesCommand;
import com.hedera.services.yahcli.commands.files.SysFilesCommand;
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
				AccountsCommand.class,
				SysFilesCommand.class,
				FeesCommand.class
		},
		description = "Perform operations against well-known entities on a Hedera Services network")
public class Yahcli implements Callable<Integer> {
	public static final long NO_FIXED_FEE = Long.MIN_VALUE;

	@Spec
	CommandSpec spec;

	@Option(names = { "-f", "--fixed-fee" },
			paramLabel = "fee to offer",
			defaultValue = "" + NO_FIXED_FEE)
	Long fixedFee;

	@Option(names = { "-n", "--network" },
			paramLabel = "target network")
	String net;

	@Option(names = { "-p", "--payer" },
			paramLabel = "payer")
	String payer;

	@Option(names = { "-c", "--config" },
			paramLabel = "yahcli config YAML",
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

	public Long getFixedFee() {
		return fixedFee;
	}
}
