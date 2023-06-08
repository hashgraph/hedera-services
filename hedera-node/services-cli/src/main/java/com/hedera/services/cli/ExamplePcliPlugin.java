/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.cli;

import com.hedera.services.cli.signedstate.SummarizeSignedStateFileCommand;
import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@CommandLine.Command(
        name = "example",
        mixinStandardHelpOptions = true,
        description = "Example Pcli Plugin for Services",
        subcommands = {SummarizeSignedStateFileCommand.class})
@SubcommandOf(PlatformCli.class)
public final class ExamplePcliPlugin extends AbstractCommand {

    @Spec
    CommandSpec spec;

    private ExamplePcliPlugin() {}

    @Override
    public Integer call() {
        throw new ParameterException(spec.commandLine(), "Please specify a subcommand!");
    }
}
