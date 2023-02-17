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

package com.hedera.services.yahcli.commands.schedules;

import com.hedera.services.yahcli.Yahcli;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;

@CommandLine.Command(
        name = "schedule",
        subcommands = {HelpCommand.class, SignCommand.class},
        description = "Performs scheduler operations")
public class ScheduleCommand implements Callable<Integer> {
    @ParentCommand
    Yahcli yahcli;

    @Override
    public Integer call() throws CommandLine.ParameterException {
        throw new CommandLine.ParameterException(
                yahcli.getSpec().commandLine(), "Please specify a schedule subcommand!");
    }

    public Yahcli getYahcli() {
        return yahcli;
    }
}
