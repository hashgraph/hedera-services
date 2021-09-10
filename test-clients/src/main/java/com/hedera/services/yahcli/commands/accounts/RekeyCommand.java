package com.hedera.services.yahcli.commands.accounts;

import com.google.common.io.Files;
import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Paths;
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
	String replKeyLoc;

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

	private void backupCurrentKey(ConfigManager configManager, String num) throws IOException {
		var optKeyFile = ConfigUtils.keyFileFor(configManager.keysLoc(), "account" + num);
		if (optKeyFile.isPresent()) {
			final var keyFile = optKeyFile.get();
			final var keyLoc = keyFile.getAbsolutePath();
			final var backupLoc = keyLoc + ".bkup";
			Files.copy(keyFile, java.nio.file.Files.newOutputStream(Paths.get(backupLoc)));
			if (keyLoc.endsWith(".pem")) {
				final var optPassFile = ConfigUtils.passFileFor(keyFile);
				if (optPassFile.isPresent()) {
						
				}
			}
		} else {
			System.out.println("⚠️ No current key for account " + num + ", payer will need special privileges");
		}
	}
}
