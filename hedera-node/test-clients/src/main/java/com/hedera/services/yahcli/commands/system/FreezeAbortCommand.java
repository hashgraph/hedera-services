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
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "freeze-abort",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Aborts any scheduled freeze and discards any staged NMT upgrade")
public class FreezeAbortCommand implements Callable<Integer> {
    @CommandLine.ParentCommand private Yahcli yahcli;

    @Override
    public Integer call() throws Exception {
        final var config = configFrom(yahcli);

        final var delegate = new FreezeHelperSuite(config.asSpecConfig(), null, true);

        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
            COMMON_MESSAGES.info("SUCCESS - freeze aborted and/or staged upgrade discarded");
        } else {
            COMMON_MESSAGES.warn(
                    "FAILED - Scheduled freeze is not aborted and/or staged upgrade is not"
                            + " discarded");
            return 1;
        }

        return 0;
    }
}
