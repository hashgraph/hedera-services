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

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "update",
		subcommands = { CommandLine.HelpCommand.class },
		description = "update throttle property on the target network")
public class ThrottleUpdateCommand implements Callable<Integer> {
	@CommandLine.ParentCommand
	ThrottlesCommand throttlesCommand;

	@Override
	public Integer call() throws Exception {
		return null;
	}
}
