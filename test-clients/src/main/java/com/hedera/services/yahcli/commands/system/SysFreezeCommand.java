package com.hedera.services.yahcli.commands.system;

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

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.FreezeSuite;
import picocli.CommandLine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

@CommandLine.Command(
		name = "freeze",
		subcommands = { picocli.CommandLine.HelpCommand.class },
		description = "Freeze system at given start time")
public class SysFreezeCommand implements Callable<Integer> {

	@CommandLine.ParentCommand
	private Yahcli yahcli;

	@CommandLine.Option(names = { "-s", "--start-time"},
			paramLabel = "Freeze start time",
			defaultValue = "")
	private String freezeStartTimeStr;

	private Instant freezeStartTime;
	@Override
	public Integer call() throws Exception {
		var config = configFrom(yahcli);

		freezeStartTime = ensureFreezeStartTime(freezeStartTimeStr);

		var delegate = new FreezeSuite(config.asSpecConfig(), freezeStartTime);
		delegate.runSuiteSync();

		return 0;
	}

	private Instant ensureFreezeStartTime(String timeStampInStr) {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

		Instant startTime = Instant.from(dtf.parse(timeStampInStr));

		return startTime;

	}

}