/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.cli.utility;

import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine;

/**
 * A picocli class that contains parameter definitions.
 */
public class ParameterizedClass {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    /**
     * Get the picocli command line spec.
     */
    public CommandLine.Model.CommandSpec getSpec() {
        return spec;
    }

    /**
     * Build an exception caused by invalid parameters.
     *
     * @param message
     * 		the message on the exception
     */
    protected CommandLine.ParameterException buildParameterException(final String message) {
        throw new CommandLine.ParameterException(spec.commandLine(), message);
    }

    /**
     * Ensure that a path from the command line exists and that this process has read access to it.
     *
     * @param path
     * 		a path from the command line
     * @return the path if it passes validation
     */
    protected Path pathMustExist(final Path path) {
        if (!Files.exists(path)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Path " + path + " does not exist");
        }
        if (!Files.isReadable(path)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "No read permission for " + path);
        }
        return path;
    }

    /**
     * Ensure that a path from the command line does not exist.
     *
     * @param path
     * 		a path from the command line
     * @return the path if it passes validation
     */
    protected Path pathMustNotExist(final Path path) {
        if (Files.exists(path)) {
            throw new CommandLine.ParameterException(spec.commandLine(), "Path " + path + " already exists");
        }
        return path;
    }
}
