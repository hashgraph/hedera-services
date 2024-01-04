/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.cli.commands.LogCommand;
import com.swirlds.cli.logging.LogProcessor;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import picocli.CommandLine;

@CommandLine.Command(
        name = "format",
        mixinStandardHelpOptions = true,
        description = "Generate an html formatted version of swirlds log files.")
@SubcommandOf(LogCommand.class)
public class FormatSwirldsLogCommand extends AbstractCommand {

    /**
     * The path to the root directory where the input log files are contained.
     */
    private Path inputDirectory;

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

    @Override
    public Integer call() throws Exception {
        LogProcessor.processLogs(inputDirectory, outputPath);

        return 0;
    }
}
