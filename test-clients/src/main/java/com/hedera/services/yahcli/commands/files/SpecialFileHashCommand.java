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
package com.hedera.services.yahcli.commands.files;

import static com.hedera.services.bdd.spec.HapiApiSpec.SpecStatus.PASSED;
import static com.hedera.services.yahcli.config.ConfigUtils.configFrom;

import com.hedera.services.yahcli.suites.SpecialFileHashSuite;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "hash-check",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Checks the hash of a special file")
public class SpecialFileHashCommand implements Callable<Integer> {
    @ParentCommand private SysFilesCommand sysFilesCommand;

    @Parameters(paramLabel = "<special-file>", description = "{ software-zip, telemetry-zip }")
    private String specialFile;

    @Override
    public Integer call() throws Exception {
        var config = configFrom(sysFilesCommand.getYahcli());

        var delegate = new SpecialFileHashSuite(config.asSpecConfig(), specialFile);
        delegate.runSuiteSync();

        return (delegate.getFinalSpecs().get(0).getStatus() == PASSED) ? 0 : 1;
    }
}
