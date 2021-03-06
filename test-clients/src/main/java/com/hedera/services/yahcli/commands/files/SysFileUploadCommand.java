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
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.yahcli.commands.files.SysFilesCommand.resolvedDir;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

@CommandLine.Command(
		name = "upload",
		subcommands = { picocli.CommandLine.HelpCommand.class },
		description = "Upload a system file")
public class SysFileUploadCommand implements Callable<Integer> {
	public static AtomicReference<String> activeSrcDir = new AtomicReference<>();

	@CommandLine.ParentCommand
	private SysFilesCommand sysFilesCommand;

	@CommandLine.Option(names = { "-s", "--source-dir" },
			paramLabel = "source directory",
			defaultValue = "{network}/sysfiles/")
	private String srcDir;

	@CommandLine.Option(names = { "--dry-run" },
			description = "only write the serialized form of the system file to disk, do not send a FileUpdate")
	private boolean dryRun;

	@CommandLine.Parameters(
			arity = "1",
			paramLabel = "<sysfile>",
			description = "one of " +
					"{ address-book, node-details, fees, rates, props, permissions, throttles } (or " +
					"{ 101, 102, 111, 112, 121, 122, 123 })")
	private String sysFile;

	@Override
	public Integer call() throws Exception {
		var config = configFrom(sysFilesCommand.getYahcli());
		srcDir = resolvedDir(srcDir, config);
		activeSrcDir.set(srcDir);

		var delegate = new SysFileUploadSuite(srcDir, config.asSpecConfig(), sysFile, dryRun);
		delegate.runSuiteSync();

		return 0;
	}
}
