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

import com.swirlds.cli.commands.SwirldsLogCommand;
import com.swirlds.cli.logging.HtmlGenerator;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import picocli.CommandLine;

@CommandLine.Command(
        name = "format",
        mixinStandardHelpOptions = true,
        description = "Generate an html formatted version of swirlds log files.")
@SubcommandOf(SwirldsLogCommand.class)
public class FormatSwirldsLogCommand extends AbstractCommand {
    private Path targetPath;
    private Path outputPath;

    @CommandLine.Parameters(description = "The location of the log files to format", index = "0")
    private void setInputPath(@NonNull final Path keyFilePath) {
        Objects.requireNonNull(keyFilePath);
        // TODO support multiple log files
        this.targetPath = pathMustExist(keyFilePath.toAbsolutePath().normalize());
    }

    @CommandLine.Option(
            names = {"-o", "--output-path"},
            description =
                    "Specify the destination directory for the formatted log files. Defaults to directory of the input log files")
    private void setOutputPath(@NonNull final Path destinationDirectory) {
        Objects.requireNonNull(destinationDirectory);
        this.outputPath = destinationDirectory.toAbsolutePath().normalize();
    }

    @Override
    public Integer call() throws Exception {
        if (outputPath == null) {
            outputPath = targetPath.getParent();
        }

        final List<String> logLines;
        try (final BufferedReader reader = new BufferedReader(new FileReader(targetPath.toFile()))) {
            logLines = reader.lines().toList();
        }

        final String htmlPage = HtmlGenerator.generateHtmlPage(logLines);

        // TODO define output filename dynamically
        try (final BufferedWriter writer = new BufferedWriter(
                new FileWriter(outputPath.resolve("logs.html").toString(), false))) {
            writer.write(htmlPage);
        } catch (final IOException e) {
            // TODO use logger
            System.out.println("Failed to write formatted log file to " + outputPath);
        }

        return 0;
    }
}
