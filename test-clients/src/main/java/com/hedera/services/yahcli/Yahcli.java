package com.hedera.services.yahcli;

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

import com.hedera.services.yahcli.commands.accounts.AccountsCommand;
import com.hedera.services.yahcli.commands.accounts.BalanceCommand;
import com.hedera.services.yahcli.commands.files.SysFilesCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(
		name = "yahcli",
		subcommands = {
				HelpCommand.class,
				AccountsCommand.class,
				SysFilesCommand.class
		},
		description = "Perform operations against well-known entities on a Hedera Services network")
public class Yahcli implements Callable<Integer> {
	public static final long NO_FIXED_FEE = Long.MIN_VALUE;

	@Spec
	CommandSpec spec;

	@Option(names = { "-f", "--fixed-fee" },
			paramLabel = "fee to offer",
			defaultValue = "" + NO_FIXED_FEE)
	Long fixedFee;

	@Option(names = { "-n", "--network" },
			paramLabel = "target network")
	String net;

	@Option(names = { "-p", "--payer" },
			paramLabel = "payer")
	String payer;

	@Option(names = { "-c", "--config" },
			paramLabel = "yahcli config YAML",
			defaultValue = "config.yml")
	String configLoc;

	@Override
	public Integer call() throws Exception {
		throw new ParameterException(spec.commandLine(), "Please specify a subcommand!");
	}

	public static void main(String... args) {
		int rc = new CommandLine(new Yahcli()).execute(args);
		System.exit(rc);
	}

	public String getNet() {
		return net;
	}

	public String getPayer() {
		return payer;
	}

	public String getConfigLoc() {
		return configLoc;
	}

	public CommandSpec getSpec() {
		return spec;
	}

	public Long getFixedFee() {
		return fixedFee;
	}
}
