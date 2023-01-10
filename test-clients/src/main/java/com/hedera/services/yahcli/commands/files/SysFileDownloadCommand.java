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
package com.hedera.services.yahcli.commands.files;

import static com.hedera.services.bdd.spec.HapiSpec.SpecStatus.PASSED;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.services.yahcli.suites.SysFileDownloadSuite;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "download",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Downloads system files")
public class SysFileDownloadCommand implements Callable<Integer> {
    @ParentCommand private SysFilesCommand sysFilesCommand;

    @CommandLine.Option(
            names = {"-d", "--dest-dir"},
            paramLabel = "destination directory",
            defaultValue = "{network}/sysfiles/")
    private String destDir;

    @Parameters(
            arity = "1..*",
            paramLabel = "<sysfiles>",
            description =
                    "one or more from "
                            + "{ address-book, node-details, fees, rates, props, "
                            + "permissions, throttles, software-zip, telemetry-zip } (or "
                            + "{ 101, 102, 111, 112, 121, 122, 123, 150, 159 })---or 'all'")
    private String[] sysFiles;

    @Override
    public Integer call() throws Exception {
        var config = configFrom(sysFilesCommand.getYahcli());
        destDir = SysFilesCommand.resolvedDir(destDir, config);

        var delegate = new SysFileDownloadSuite(destDir, config.asSpecConfig(), sysFiles);
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == PASSED) {
            COMMON_MESSAGES.info("SUCCESS - downloaded all requested system files");
        } else {
            COMMON_MESSAGES.warn("FAILED downloading requested system files");
            return 1;
        }

        return 0;
    }
}
