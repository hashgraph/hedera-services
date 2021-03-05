package com.hedera.services.yahcli.commands.files;

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.suites.SysFileUploadSuite;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "upload",
		subcommands = { CommandLine.HelpCommand.class },
		description = "Upload a system file")
public class SysFileUploadCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	SysFilesCommand sysFilesCommand;

	@CommandLine.Option(names = { "-s", "--source-dir" },
			paramLabel = "source directory",
			defaultValue = "{network}/sysfiles/")
	String srcDir;

	@CommandLine.Parameters(
			arity = "1",
			paramLabel = "<sysfile>",
			description = "system file name (one of \n" +
					" Full names ['addressBook.json', 'nodeDetails.json', 'feeSchedules.json', 'exchangeRates.json'," +
					" 'application.properties', 'api-permission.properties'] \n" +
					" Short handles ['book', 'details', 'fees', 'rates', 'props', 'permissions'] \n" +
					" File numbers ['101', '102'', '111', '112', '121', '122'])")
	String sysFile;

	@Override
	public Integer call() throws Exception {
		var config = ConfigManager.from(sysFilesCommand.getYahcli());
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config);

		if (srcDir.startsWith("{network}")) {
			srcDir = config.getTargetName() + File.separator + "sysfiles";
		}
		if (srcDir.endsWith(File.separator)) {
			srcDir = srcDir.substring(0, srcDir.length() - 1);
		}

		var delegate = new SysFileUploadSuite(srcDir, config.asSpecConfig(), sysFile);
		delegate.runSuiteSync();

		return 0;
	}
}
