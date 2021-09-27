package com.hedera.services.yahcli.commands.system;


import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.UpgradePrepSuite;
import com.swirlds.common.CommonUtils;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "stage",
		subcommands = { picocli.CommandLine.HelpCommand.class },
		description = "Stages artifacts prior to an NMT software upgrade")
public class UpgradePrepCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	private Yahcli yahcli;

	@CommandLine.Option(names = { "-f", "--upgrade-file-num" },
			paramLabel = "Number of the upgrade ZIP file",
			defaultValue = "150")
	private String upgradeFileNum;

	@CommandLine.Option(names = { "-h", "--upgrade-zip-hash" },
			paramLabel = "Hex-encoded SHA-384 hash of the upgrade ZIP")
	private String upgradeFileHash;

	@Override
	public Integer call() throws Exception {
		final var config = configFrom(yahcli);

		final var upgradeFile = "0.0." + upgradeFileNum;
		final var unhexedHash = CommonUtils.unhex(upgradeFileHash);
		final var delegate = new UpgradePrepSuite(config.asSpecConfig(), unhexedHash, upgradeFile);

		delegate.runSuiteSync();
		COMMON_MESSAGES.info("SUCCESS - NMT upgrade staged from " + upgradeFile + " artifacts ZIP");

		return 0;
	}
}
