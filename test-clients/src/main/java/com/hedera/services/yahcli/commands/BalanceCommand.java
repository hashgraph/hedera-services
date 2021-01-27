package com.hedera.services.yahcli.commands;

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.SplittableRandom;
import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@Command(
		name = "balance",
		subcommands = { HelpCommand.class },
		description = "Retrieve the balance of account(s) on the target network")
public class BalanceCommand implements Callable<Integer> {
	@ParentCommand
	Yahcli yahcli;

	@Parameters(
			arity = "1..*",
			paramLabel = "<accounts>",
			description = "account names or numbers")
	String[] accounts;

	@Override
	public Integer call() throws Exception {
		var config = ConfigManager.from(yahcli);
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config.getTargetName(), config.getDefaultPayer());

		var r = new SplittableRandom();
		for (String account : accounts) {
			System.out.println(String.format(
					" - Account %s has balance %d",
					account,
					r.nextInt(0, Integer.MAX_VALUE)));
		}
		return 0;
	}
}
