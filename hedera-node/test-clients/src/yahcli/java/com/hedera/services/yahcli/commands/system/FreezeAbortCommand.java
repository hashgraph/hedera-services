// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.yahcli.commands.system;

import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.FreezeHelperSuite;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "freeze-abort",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Aborts any scheduled freeze and discards any staged NMT upgrade")
public class FreezeAbortCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    private Yahcli yahcli;

    @Override
    public Integer call() throws Exception {
        final var config = ConfigUtils.configFrom(yahcli);

        final var delegate = new FreezeHelperSuite(config.asSpecConfig(), null, true);

        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == HapiSpec.SpecStatus.PASSED) {
            COMMON_MESSAGES.info("SUCCESS - freeze aborted and/or staged upgrade discarded");
        } else {
            COMMON_MESSAGES.warn(
                    "FAILED - Scheduled freeze is not aborted and/or staged upgrade is not" + " discarded");
            return 1;
        }

        return 0;
    }
}
