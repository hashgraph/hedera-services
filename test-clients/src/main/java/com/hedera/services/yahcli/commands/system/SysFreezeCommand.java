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
import com.hedera.services.yahcli.suites.Utils;
import picocli.CommandLine;

import java.time.Instant;
import java.util.concurrent.Callable;

import static com.hedera.services.bdd.spec.HapiApiSpec.SpecStatus.PASSED;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@CommandLine.Command(
		name = "freeze",
		subcommands = { picocli.CommandLine.HelpCommand.class },
		description = "Manages the network's maintenance freezes")
public class SysFreezeCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	private Yahcli yahcli;

	@CommandLine.Option(names = { "-s", "--start-time" },
			paramLabel = "Freeze start time in UTC (yyyy-MM-dd.HH:mm:ss)")
	private String startTime;

	@CommandLine.Option(names = { "-u", "--trigger-staged-upgrade" },
			description = "Freeze should trigger a staged NMT upgrade",
			defaultValue = "false")
	private boolean triggerNmtUpgrade;

	@CommandLine.Option(names = { "-a", "--abort" },
			description = "Abort the scheduled freeze and/or staged NMT upgrade",
			defaultValue = "false")
	private boolean abortFreeze;

	@Override
	public Integer call() throws Exception {
		assertSensibleArgLine();

		final var config = configFrom(yahcli);

		final var freezeStartTime = abortFreeze ? Instant.EPOCH : Utils.parseFormattedInstant(startTime);
		final var delegate = new FreezeSuite(config.asSpecConfig(), freezeStartTime, abortFreeze, triggerNmtUpgrade);

		delegate.runSuiteSync();

		if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
			COMMON_MESSAGES.info("SUCCESS - " + desc());
		}

		return 0;
	}

	private String desc() {
		if (abortFreeze) {
			return "freeze aborted";
		} else {
			return "freeze scheduled for " + startTime
					+ (triggerNmtUpgrade ? " w/ prepared NMT upgrade" : "");
		}
	}

	private void assertSensibleArgLine() {
		if (startTime == null && !abortFreeze) {
			throw new CommandLine.ParameterException(
					yahcli.getSpec().commandLine(),
					"Freeze start time can only be omitted with an explicit abort flag");
		}
		if (startTime != null && abortFreeze) {
			throw new CommandLine.ParameterException(
					yahcli.getSpec().commandLine(),
					"Freeze start time cannot be given with an explicit abort flag");
		}
	}

}