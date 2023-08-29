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
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.system.NodeId;
import com.swirlds.logging.LogMarker;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

@CommandLine.Command(
        name = "format",
        mixinStandardHelpOptions = true,
        description = "Generate an html formatted version of swirlds log files.")
@SubcommandOf(SwirldsLogCommand.class)
public class FormatSwirldsLogCommand extends AbstractCommand {
    private static final Logger logger = LogManager.getLogger(FormatSwirldsLogCommand.class);

    /**
     * The path to the root directory where the input log files are contained.
     */
    private Path inputDirectory = FileUtils.getAbsolutePath();

    /**
     * The path where the output file will be saved.
     */
    private Path outputPath;

    @CommandLine.Option(
            names = {"-i", "--input"},
            description = "The path to the root directory where the input log files are contained."
                    + "Defaults to the current working directory")
    private void setInputDirectory(@NonNull final Path inputDirectory) {
        if (!Files.isDirectory(inputDirectory)) {
            throw new IllegalArgumentException(
                    "Input directory '%s' does not exist, or is not a directory".formatted(inputDirectory));
        }

        this.inputDirectory = Objects.requireNonNull(inputDirectory);
    }

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Specify the destination for the formatted log file. Defaults the the input directory.")
    private void setOutputPath(@NonNull final Path outputPath) {
        Objects.requireNonNull(outputPath);
        this.outputPath = outputPath.toAbsolutePath().normalize();
    }

    /**
     * Find all the swirlds.log files in the input directory.
     *
     * @return a map of node id to the path of the swirlds.log file for that node
     * @throws IOException if an IO error occurs
     */
    @NonNull
    private Map<NodeId, Path> findLogFiles() throws IOException {
        final Map<NodeId, Path> logFilesByNode = new HashMap<>();

        try (final Stream<Path> stream = Files.walk(inputDirectory)) {
            stream.forEach(path -> {
                if (path.getFileName().toString().equals("swirlds.log")) {
                    final String parentDirectoryName =
                            path.getParent().getFileName().toString();

                    final Matcher logLineMatcher =
                            Pattern.compile("node0*(\\d+)").matcher(parentDirectoryName);

                    if (!logLineMatcher.matches()) {
                        throw new IllegalArgumentException(
                                "swirlds.log parent directory name doesn't match expected format: "
                                        + parentDirectoryName);
                    }

                    logFilesByNode.put(new NodeId(Integer.parseInt(logLineMatcher.group(1))), path);

                    logger.info(LogMarker.CLI.getMarker(), "Found log file: {}", path);
                }
            });
        }

        return logFilesByNode;
    }

    @Override
    public Integer call() throws Exception {
        final Map<NodeId, Path> logFilesByNode = findLogFiles();
        final Map<NodeId, List<String>> logLinesByNode = new HashMap<>();

        for (final Map.Entry<NodeId, Path> entry : logFilesByNode.entrySet()) {
            final List<String> logLines;
            try (final BufferedReader reader =
                    new BufferedReader(new FileReader(entry.getValue().toFile()))) {
                logLines = reader.lines().toList();
            }

            logLinesByNode.put(entry.getKey(), logLines);
        }

        final String htmlPage = HtmlGenerator.generateHtmlPage(logLinesByNode);

        if (outputPath == null) {
            outputPath = inputDirectory.resolve("swirlds-logs.html");
        }

        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toString(), false))) {

            writer.write(htmlPage);
        } catch (final IOException e) {
            logger.info(LogMarker.CLI.getMarker(), "Failed to write formatted log file to {}", outputPath);
        }

        return 0;
    }
}
