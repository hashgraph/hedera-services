/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.cli;

import static com.swirlds.cli.utility.CommandBuilder.buildCommandLine;
import static com.swirlds.cli.utility.CommandBuilder.whitelistCliPackage;

import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.PlatformCliLogo;
import com.swirlds.cli.utility.PlatformCliPreParser;
import com.swirlds.common.formatting.TextEffect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * The Swirlds Platform CLI.
 */
@Command(
        name = "pcli",
        version = "0.34.0",
        mixinStandardHelpOptions = true,
        description = "Miscellaneous platform utilities.")
public class PlatformCli extends AbstractCommand {

    /**
     * Set the paths where jar files should be loaded from.
     */
    @CommandLine.Option(
            names = {"-L", "--load", "--cp"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "A path where additional java libs should be loaded from. "
                    + "Can be a path to a jar file or a path to a directory containing jar files.")
    private void setLoadPath(final List<Path> loadPath) {
        throw buildParameterException(
                "The load path parameter is expected to be parsed prior to the JVM being started. "
                        + "This argument is included here for documentation purposes only.");
    }

    @CommandLine.Option(
            names = {"-J", "--jvm"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "An argument that will be passed to the JVM, e.g. '-Xmx10g'")
    private void setJvmArgs(final List<String> jvmArgs) {
        throw buildParameterException("The jvm args parameter is expected to be parsed prior to the JVM being started. "
                + "This argument is included here for documentation purposes only.");
    }

    @CommandLine.Option(
            names = {"-C", "--cli"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "A package prefix where CLI commands/subcommands can be found. "
                    + "Commands annotated with '@SubcommandOf' in these packages are automatically "
                    + "integrated into pcli.")
    private void setCliPackagePrefixes(final List<String> cliPackagePrefixes) {
        throw buildParameterException("The cli parameter is expected to be parsed using a different pathway. "
                + "This argument is included here for documentation purposes only.");
    }

    @CommandLine.Option(
            names = {"-D", "--debug"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "Pause the JVM at startup, and wait until a debugger "
                    + "is attached to port 8888 before continuing.")
    private void setDebug(final boolean debug) {
        throw buildParameterException("The debug parameter is expected to be parsed prior to the JVM being started. "
                + "This argument is included here for documentation purposes only.");
    }

    @CommandLine.Option(
            names = {"-M", "--memory"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "Set the amount of memory to allocate to the JVM, in gigabytes. "
                    + "'-M 16' is equivalent to '-J -Xmx16g'.")
    private void setJvmArgs(final int memory) {
        throw buildParameterException("The memory parameter is expected to be parsed prior to the JVM being started. "
                + "This argument is included here for documentation purposes only.");
    }

    /**
     * Set the log4j path.
     */
    @CommandLine.Option(
            names = {"--log4j"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "The path where the log4j configuration file can be found.")
    private void setLog4jPath(final Path log4jPath) {
        throw buildParameterException("The log4j path parameter is expected to be parsed manually. "
                + "This argument is included here for documentation purposes only.");
    }

    @CommandLine.Option(
            names = {"--no-color"},
            description = "Disable color output. This option is designed for "
                    + "boring people living boring lives, who want their console "
                    + "output to be just as boring as they are.")
    private void setColorDisabled(final boolean colorDisabled) {
        throw buildParameterException("The setColorDisabled parameter is expected to be parsed manually. "
                + "This argument is included here for documentation purposes only.");
    }

    /**
     * Set the paths where jar files should be loaded from.
     */
    @CommandLine.Option(
            names = {"-I", "--ignore-jars"},
            scope = CommandLine.ScopeType.INHERIT,
            description = "If running in a platform development environment, "
                    + "if this flag is present then ignore jar files in the standard platform sdk/data/lib directory. "
                    + "This can be useful if running on a machine with a platform build environment, but where you "
                    + "don't want to load the jars from that build environment. If not running in a platform "
                    + "development environment, this flag has no effect")
    private void setIgnoreJars(final boolean ignoreJars) {
        throw buildParameterException(
                "The ignore jars parameter is expected to be parsed prior to the JVM being started. "
                        + "This argument is included here for documentation purposes only.");
    }

    /**
     * Before we do the main parsing pass, we first need to extract a small selection of parameters.
     *
     * @param args
     * 		the program arguments
     * @return the arguments that weren't handled by pre-parsing
     */
    private static PlatformCliPreParser preParse(final String[] args) {
        final CommandLine commandLine = new CommandLine(PlatformCliPreParser.class);
        commandLine.setUnmatchedArgumentsAllowed(true);
        commandLine.execute(args);
        final List<String> unmatchedArgs = commandLine.getUnmatchedArguments();
        final PlatformCliPreParser parser = commandLine.getCommand();
        parser.setUnparsedArgs(unmatchedArgs.toArray(new String[0]));

        return parser;
    }

    /**
     * Start log4j on a background thread. Log4j takes a long time to load, and finding
     * subcommands by walking the class graph takes a long time, so it's good
     * if we can do both at the same time.
     *
     * @param log4jPath
     * 		the path to the log4j configuration if it exists, or null if it doesn't
     * @return a latch that counts down when log4j has been started
     */
    private static CountDownLatch startLog4j(final Path log4jPath) {
        final CountDownLatch log4jLoadedLatch = new CountDownLatch(1);

        boolean log4jConfigProvided = false;
        if (log4jPath != null) {
            if (Files.exists(log4jPath)) {
                log4jConfigProvided = true;
                new Thread(() -> {
                            final LoggerContext context = (LoggerContext) LogManager.getContext(false);
                            context.setConfigLocation(log4jPath.toUri());
                            log4jLoadedLatch.countDown();
                        })
                        .start();
            } else {
                System.err.println("File " + log4jPath + " does not exist.");
            }
        }

        if (!log4jConfigProvided) {
            log4jLoadedLatch.countDown();
        }

        return log4jLoadedLatch;
    }

    /**
     * Main entrypoint for the platform CLI.
     *
     * @param args
     * 		program arguments
     */
    public static void main(final String[] args) throws InterruptedException {
        final PlatformCliPreParser preParser = preParse(args);

        TextEffect.setTextEffectsEnabled(!preParser.isColorDisabled());

        // Will lack actual color if color has been disabled
        System.out.println(PlatformCliLogo.getColorizedLogo());

        final CountDownLatch log4jLatch = startLog4j(preParser.getLog4jPath());

        whitelistCliPackage("com.swirlds.platform.cli.commands");
        whitelistCliPackage("com.swirlds.platform.cli");
        whitelistCliPackage("com.swirlds.platform.state.editor");
        whitelistCliPackage("com.swirlds.common.cli");
        if (preParser.getCliPackagePrefixes() != null) {
            for (final String cliPackagePrefix : preParser.getCliPackagePrefixes()) {
                whitelistCliPackage(cliPackagePrefix);
            }
        }
        final CommandLine commandLine = buildCommandLine(PlatformCli.class);

        log4jLatch.await();
        System.exit(commandLine.execute(preParser.getUnparsedArgs()));
    }
}
