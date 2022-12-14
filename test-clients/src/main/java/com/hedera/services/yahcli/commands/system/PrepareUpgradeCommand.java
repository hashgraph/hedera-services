/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import com.hedera.services.yahcli.suites.UpgradeHelperSuite;
import com.swirlds.common.utility.CommonUtils;
import java.util.concurrent.Callable;
import picocli.CommandLine;

@CommandLine.Command(
        name = "prepare-upgrade",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Stages artifacts prior to an NMT software upgrade")
public class PrepareUpgradeCommand implements Callable<Integer> {
    @CommandLine.ParentCommand private Yahcli yahcli;

    @CommandLine.Option(
            names = {"-f", "--upgrade-file-num"},
            paramLabel = "Number of the upgrade ZIP file",
            defaultValue = "150")
    private String upgradeFileNum;

    @CommandLine.Option(
            names = {"-h", "--upgrade-zip-hash"},
            paramLabel = "Hex-encoded SHA-384 hash of the upgrade ZIP")
    private String upgradeFileHash;

    @Override
    public Integer call() throws Exception {
        final var config = configFrom(yahcli);

        final var upgradeFile = "0.0." + upgradeFileNum;
        final var unhexedHash = CommonUtils.unhex(upgradeFileHash);
        final var delegate =
                new UpgradeHelperSuite(config.asSpecConfig(), unhexedHash, upgradeFile);

        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
            COMMON_MESSAGES.info(
                    "SUCCESS - NMT upgrade staged from " + upgradeFile + " artifacts ZIP");
        } else {
            COMMON_MESSAGES.warn("FAILED - NMT software upgrade is not in staged ");
            return 1;
        }

        return 0;
    }
}
