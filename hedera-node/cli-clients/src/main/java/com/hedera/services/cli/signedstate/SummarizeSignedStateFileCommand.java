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

import com.hedera.services.cli.utils.SignedStateHolder;
import com.hedera.services.cli.utils.SignedStateHolder.Contract;
import com.swirlds.common.io.utility.FileUtils;
import java.lang.reflect.Array;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "summarize",
        mixinStandardHelpOptions = true,
        description = "Summarizes contents of signed state file (to stdout)")
public class SummarizeSignedStateFileCommand implements Callable<Integer> {

    @Option(
            names = {"-f", "--file"},
            required = true,
            description = "Input signed state file")
    Path inputFile;

    @Option(
            names = {"--do-logs"},
            description = "Enable logging (at INFO level)")
    boolean doLogging;

    @Override
    public Integer call() throws Exception {

        setupLogging();

        try (final var signedState = new SignedStateHolder(inputFile)) {

            final var knownContracts = signedState.getContracts();

            final int contractsWithBytecodeFound = knownContracts.contracts().size();
            final var bytesFound = knownContracts.contracts().stream()
                    .map(Contract::bytecode)
                    .mapToInt(Array::getLength)
                    .sum();

            System.out.printf(
                    ">>> SummarizeSignedStateFile: %d contractIDs found %d contracts found in file store"
                            + " (%d bytes total) (%d deleted ones too)%n",
                    knownContracts.registeredContractsCount(),
                    contractsWithBytecodeFound,
                    bytesFound,
                    knownContracts.deletedContracts().size());
        }

        return 0;
    }

    private void setupLogging() {
        setRootLogLevel(doLogging ? Level.INFO : Level.ERROR);
    }

    private void setRootLogLevel(final Level level) {
        final var logger = LogManager.getRootLogger();

        Configurator.setAllLevels(logger.getName(), level);
        // Don't understand this: I get two log messages from `FileUtils` at `INFO` level (marker: `STATE_TO_DISK`) and
        // not only does the above line not suppress them, neither does the following line suppress them:
        Configurator.setLevel(FileUtils.class, level);
    }
}
