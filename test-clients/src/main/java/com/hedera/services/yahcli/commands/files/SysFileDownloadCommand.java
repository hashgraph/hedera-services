package com.hedera.services.yahcli.commands.files;

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.SysFileDownloadSuite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@Command(
		name = "download",
		subcommands = { CommandLine.HelpCommand.class },
		description = "Download a system file")
public class SysFileDownloadCommand implements Callable<Integer> {
	@ParentCommand
	SysFilesCommand sysFilesCommand;

	@CommandLine.Option(names = { "-d", "--dest-dir" },
			paramLabel = "destination directory",
			defaultValue = "{network}/sysfiles/")
	String destDir;

	@Parameters(
			arity = "1..*",
			paramLabel = "<sysfiles>",
			description = "system file names ('book', 'details', 'rates', 'fees', 'props', 'permissions') or numbers")
	String[] sysFiles;

	@Override
	public Integer call() throws Exception {
		var config = ConfigManager.from(sysFilesCommand.getYahcli());
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config);

		if (destDir.startsWith("{network}")) {
			destDir = config.getTargetName() + File.separator + "sysfiles";
		}
		ConfigUtils.ensureDir(destDir);
		if (destDir.endsWith(File.separator)) {
			destDir = destDir.substring(0, destDir.length() - 1);
		}

		var delegate = new SysFileDownloadSuite(destDir, config.asSpecConfig(), sysFiles);
		delegate.runSuiteSync();

		return 0;
	}
}
