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

package com.hedera.services.cli.signedstate;

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.Level;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * A subcommand of the {@link PlatformCli}, for dealing with signed state files
 */
@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
@CommandLine.Command(
        name = "signed-state",
        mixinStandardHelpOptions = true,
        description = "Operations on signed-state files.")
@SubcommandOf(PlatformCli.class)
public final class SignedStateCommand extends AbstractCommand {

    @ParentCommand
    PlatformCli parent;

    enum Verbosity {
        SILENT,
        VERBOSE
    }

    @Option(
            names = {"-f", "--file"},
            required = true,
            arity = "1",
            description = "Input signed state file")
    Path inputFile;

    @Option(
            names = {"-c", "--config"},
            description = "A path to where a configuration file can be found. If not provided then defaults are used.")
    private void setConfigurationPath(@NonNull final List<Path> configurationPaths) {
        Objects.requireNonNull(configurationPaths, "configurationPaths");

        configurationPaths.forEach(this::pathMustExist);
        this.configurationPaths = configurationPaths;
    }

    List<Path> configurationPaths = List.of();

    @Option(
            names = {"-v", "--verbose"},
            arity = "0..1",
            defaultValue = "false",
            description = "Verbosity of command")
    private void setVerbosity(final boolean doVerbose) {
        verbosity = doVerbose ? Verbosity.VERBOSE : Verbosity.SILENT;
    }

    Verbosity verbosity;

    static class LogLevelConverter implements CommandLine.ITypeConverter<Level> {

        @Override
        public Level convert(String value) throws Exception {
            return new org.apache.logging.log4j.core.config.plugins.convert.TypeConverters.LevelConverter()
                    .convert(value);
        }
    }

    // We want to open the signed state file only once but run a bunch of dumps against it
    // (because it takes a long time to open the signed state file).  So we can specify
    // more than one of these subcommands on the single command line.  But we don't get
    // any hook at the beginning of the entire set of subcommands or after either.  So
    // that we have to track for ourselves, and each subcommand is responsible for opening
    // _and closing_ the signed state file appropriatel

    SignedStateHolder signedState;

    @NonNull
    SignedStateHolder openSignedState() {
        if (signedState == null) {
            if (verbosity == Verbosity.VERBOSE) System.out.printf("=== opening signed state file '%s'%n", inputFile);

            signedState = new SignedStateHolder(inputFile, configurationPaths);

            if (verbosity == Verbosity.VERBOSE) System.out.printf("=== signed state file '%s' opened%n", inputFile);
        } else {
            System.out.printf("*** signed state file '%s' already opened%n", inputFile);
        }
        return signedState;
    }

    void closeSignedState() {
        if (signedState != null) {
            if (verbosity == Verbosity.VERBOSE) System.out.printf("=== closing signed state file '%s'%n", inputFile);

            signedState.close();

            if (verbosity == Verbosity.VERBOSE) System.out.printf("=== signed state file '%s' closed%n", inputFile);
        } else {
            System.out.printf("*** signed state file '%s' already closed%n", inputFile);
        }
        signedState = null;
    }

    private SignedStateCommand() {}
}
