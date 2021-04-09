package com.hedera.services.yahcli.commands.files;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.yahcli.suites.SysFileUploadSuite;
import picocli.CommandLine;

import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.commands.files.SysFilesCommand.configFrom;
import static com.hedera.services.yahcli.commands.files.SysFilesCommand.resolvedDir;

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
		var config = configFrom(sysFilesCommand.getYahcli());
		srcDir = resolvedDir(srcDir, config);

		var delegate = new SysFileUploadSuite(srcDir, config.asSpecConfig(), sysFile);
		delegate.runSuiteSync();

		return 0;
	}

}
