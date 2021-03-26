package com.hedera.services.yahcli.commands.throttles;

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
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "throttles",
		subcommands = {
				CommandLine.HelpCommand.class,
				ThrottleUpdateCommand.class
		},
		description = "Alter throttle settings")
public class ThrottlesCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	Yahcli yahcli;

	@Override
	public Integer call() throws Exception {
		throw new CommandLine.ParameterException(
				yahcli.getSpec().commandLine(),
				"Please specify an throttles subcommand!");
	}

	public Yahcli getYahcli() {
		return yahcli;
	}
}
