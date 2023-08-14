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
import static com.swirlds.platform.testreader.JrsTestReader.findTestResults;
import static com.swirlds.platform.testreader.JrsTestReader.saveTestResults;

import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.testreader.JrsTestResult;
import com.swirlds.platform.util.VirtualTerminal;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import picocli.CommandLine;

@CommandLine.Command(
        name = "scrape",
        mixinStandardHelpOptions = true,
        description = "Scrape test data from gcp buckets and generate a csv containing test data.")
@SubcommandOf(JrsTestReaderCommand.class)
public class JrsTestReaderScapeCommand extends AbstractCommand {

    private JrsTestReaderScapeCommand() {}

    private String bucketPrefix;
    private String testDirectory;
    private int days = 7;
    private Path output = Path.of("testData.csv");
    private int threads = 48;

    @CommandLine.Parameters(description = "The gs bucket to scrape data from", index = "0")
    private void setBucketPrefix(@NonNull final String bucketPrefix) {
        this.bucketPrefix = bucketPrefix;
    }

    @CommandLine.Parameters(description = "The test directory relative to the bucket prefix", index = "1")
    private void setTestDirectory(@NonNull final String testDirectory) {
        this.testDirectory = testDirectory;
    }

    @CommandLine.Option(
            names = {"-d", "--days"},
            description = "Specify the number of days in the past to begin scraping from. Defaults to 7.")
    private void setDays(final int days) {
        this.days = days;
    }

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Specify the path to the output csv file. Defaults to 'testData.csv'.")
    private void setOutput(@NonNull final Path output) {
        this.output = pathMustNotExist(getAbsolutePath(output));
    }

    @CommandLine.Option(
            names = {"-t", "--threads"},
            description = "Specify the number of threads to use. Defaults to 48.")
    private void setThreads(final int threads) {
        this.threads = threads;
    }

    @Override
    public Integer call() {
        final VirtualTerminal terminal =
                new VirtualTerminal().setProgressIndicatorEnabled(true).setThrowOnError(true);

        final Instant now = Instant.now();

        final boolean hasSlash = bucketPrefix.endsWith("/") || testDirectory.startsWith("/");
        final String rootDirectory = bucketPrefix + (hasSlash ? "" : "/") + testDirectory;

        final List<JrsTestResult> results = findTestResults(
                terminal, Executors.newFixedThreadPool(threads), rootDirectory, now, Duration.ofDays(days));

        saveTestResults(results, output);
        return 0;
    }
}
