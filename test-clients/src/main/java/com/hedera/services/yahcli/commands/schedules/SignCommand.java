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

import com.hedera.services.yahcli.suites.ScheduleSuite;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PASSED;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

@Command(
        name = "sign",
        subcommands = {HelpCommand.class},
        description = "Sign a transaction with schedule id")
public class SignCommand implements Callable<Integer> {

    @ParentCommand ScheduleCommand scheduleCommand;

    @CommandLine.Option(
            names = {"--scheduleId"},
            paramLabel = "<scheduleId>",
            description = "schedule Id to sign")
    String scheduleId;

    @Override
    public Integer call() throws Exception {
        var config = configFrom(scheduleCommand.getYahcli());

        final var effectiveScheduleId = scheduleId != null ? scheduleId : "";
        var delegate = new ScheduleSuite(config.asSpecConfig(), effectiveScheduleId);
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
            COMMON_MESSAGES.info(
                    "SUCCESS - " + "scheduleId " + effectiveScheduleId + " " + " signed");
        } else {
            COMMON_MESSAGES.info("FAILED - " + "could not sign scheduleId " + effectiveScheduleId);
            return 1;
        }

        return 0;
    }
}
