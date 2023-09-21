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
import static com.swirlds.platform.cli.utils.JtrUtils.scrapeTestData;
import static com.swirlds.platform.testreader.JrsTestReportGenerator.generateReport;

import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.cli.utils.JtrUtils;
import com.swirlds.platform.testreader.JrsReportData;
import com.swirlds.platform.testreader.JrsTestIdentifier;
import com.swirlds.platform.testreader.JrsTestMetadata;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import picocli.CommandLine;

@CommandLine.Command(
        name = "report",
        mixinStandardHelpOptions = true,
        description = "Scrape test data from gcp buckets and generate an HTML report. "
                + "Equivalent to running 'scrape' followed by 'render'.")
@SubcommandOf(JrsTestReaderCommand.class)
public class JrsTestReaderReportCommand extends AbstractCommand {

    private String bucketPrefix;
    private String bucketPrefixReplacement;
    private String testDirectory;
    private int days = 7;
    private Path metadataFile;
    private Path output = Path.of("report.html");
    private int threads = 48;

    @CommandLine.Parameters(description = "The gs bucket to scrape data from", index = "0")
    private void setBucketPrefix(@NonNull final String bucketPrefix) {
        this.bucketPrefix = bucketPrefix;
    }

    @CommandLine.Parameters(
            description = "The replacement for bucket prefix in order to convert bucket URLs to web links.",
            index = "1")
    private void setBucketPrefixReplacement(@NonNull final String bucketPrefix) {
        this.bucketPrefixReplacement = bucketPrefix;
    }

    @CommandLine.Parameters(description = "The test directory relative to the bucket prefix", index = "2")
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
            description = "Specify the path to the output html file. Defaults to 'report.html'.")
    private void setOutput(@NonNull final Path output) {
        this.output = getAbsolutePath(output);
    }

    @CommandLine.Option(
            names = {"-m", "--metadata"},
            description = "Specify the path to the test metadata csv file.")
    private void setMetadataFile(@NonNull final Path metadataFile) {
        this.metadataFile = getAbsolutePath(metadataFile);
    }

    @CommandLine.Option(
            names = {"-t", "--threads"},
            description = "Specify the number of threads to use. Defaults to 48.")
    private void setThreads(final int threads) {
        this.threads = threads;
    }

    private JrsTestReaderReportCommand() {}

    @Override
    public Integer call() {
        if (Files.exists(output)) {
            try {
                Files.delete(output);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        final JrsReportData data = scrapeTestData(bucketPrefix, testDirectory, days, threads);
        final Map<JrsTestIdentifier, JrsTestMetadata> metadata = JtrUtils.getTestMetadata(metadataFile);

        generateReport(data, metadata, Instant.now(), bucketPrefix, bucketPrefixReplacement, output);

        return 0;
    }
}
