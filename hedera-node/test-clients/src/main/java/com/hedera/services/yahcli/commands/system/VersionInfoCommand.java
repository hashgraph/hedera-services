/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.services.yahcli.commands.system;

import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

import com.hedera.services.bdd.suites.meta.VersionInfoSpec;
import com.hedera.services.yahcli.Yahcli;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "version",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Get the deployed version of a network")
public final class VersionInfoCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    private Yahcli yahcli;

    @Override
    public Integer call() throws Exception {
        final var config = configFrom(yahcli);

        final var delegate = new VersionInfoSpec(config.asSpecConfig());

        delegate.runSuiteSync();

        return 0;
    }
}
