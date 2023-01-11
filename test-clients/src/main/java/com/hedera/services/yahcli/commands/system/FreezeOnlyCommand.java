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

import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PASSED;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.suites.FreezeHelperSuite;
import com.hedera.services.yahcli.suites.Utils;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "freeze",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Schedules a freeze for network maintenance (no NMT upgrade)")
public class FreezeOnlyCommand implements Callable<Integer> {
    @CommandLine.ParentCommand private Yahcli yahcli;

    @CommandLine.Option(
            names = {"-s", "--start-time"},
            paramLabel = "Freeze start time in UTC (yyyy-MM-dd.HH:mm:ss)")
    private String startTime;

    @Override
    public Integer call() throws Exception {

        final var config = configFrom(yahcli);

        final var freezeStartTime = Utils.parseFormattedInstant(startTime);
        final var delegate = new FreezeHelperSuite(config.asSpecConfig(), freezeStartTime, false);

        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
            COMMON_MESSAGES.info("SUCCESS - " + "freeze scheduled for " + startTime);
        } else {
            COMMON_MESSAGES.warn("FAILED - freeze is not scheduled for " + startTime);
            return 1;
        }

        return 0;
    }
}
