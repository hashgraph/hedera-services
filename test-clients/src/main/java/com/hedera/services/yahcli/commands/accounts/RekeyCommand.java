package com.hedera.services.yahcli.commands.accounts;

import com.hedera.services.yahcli.config.ConfigManager;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "rekey",
		subcommands = { CommandLine.HelpCommand.class },
		description = "Replaces the key on an account")
public class RekeyCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	AccountsCommand accountsCommand;

	@CommandLine.Option(names = { "-k", "--replacement-key" },
			paramLabel = "path to new key file")
	String newKeyLoc;

	@CommandLine.Option(names = { "-g", "--gen-new-key" },
			paramLabel = "path to new key file",
			defaultValue = "false",
			description = "new key should be auto-generated")
	Boolean genNewKey;

	@CommandLine.Parameters(
			arity = "1",
			paramLabel = "<account>",
			description = "number of account to rekey")
	String account;

	@Override
	public Integer call() throws Exception {
		var config = ConfigManager.from(accountsCommand.getYahcli());
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config);

//		var delegate = new RekeySuite(config.asSpecConfig(), account, rekeyPemPath, rekeyPemPass);
//		delegate.runSuiteSync();

		return 0;
	}
}
