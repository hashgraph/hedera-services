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

package com.swirlds.platform.cli;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;

import com.swirlds.cli.commands.DevCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.testreader.JrsTestReader;
import com.swirlds.platform.util.VirtualTerminal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import picocli.CommandLine;

@CommandLine.Command(
        name = "jtr",
        mixinStandardHelpOptions = true,
        description = "Read JRS test results and create a report.")
@SubcommandOf(DevCommand.class)
public class JrsTestReaderCommand extends AbstractCommand {

    private JrsTestReaderCommand() {}

    @Override
    public Integer call() {

        final VirtualTerminal terminal =
                new VirtualTerminal().setProgressIndicatorEnabled(true).setThrowOnError(true);

        // "gs://swirlds-circleci-jrs-results/swirlds-automation/develop"
        // "gs://swirlds-circleci-jrs-results/cody-littley"
        // "gs://swirlds-circleci-jrs-results/swirlds-automation/develop"
        final String root = "gs://swirlds-circleci-jrs-results/swirlds-automation/develop";

        JrsTestReader.generateTestReport(
                terminal,
                Executors.newFixedThreadPool(48),
                root,
                Duration.ofDays(7),
                Path.of("/Users/codylittley/ws/hedera-services/platform-sdk/swirlds-platform-core/"
                        + "src/main/java/com/swirlds/platform/testreader/testNotes.csv"),
                getAbsolutePath(Path.of("~/Desktop/report.html")));

        return 0;
    }
}
