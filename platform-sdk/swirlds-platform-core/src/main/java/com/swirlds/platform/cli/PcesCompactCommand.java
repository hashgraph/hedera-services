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

import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.event.preconsensus.PcesUtilities;
import com.swirlds.platform.util.BootstrapUtils;
import java.nio.file.Path;
import picocli.CommandLine;

@CommandLine.Command(
        name = "compact",
        mixinStandardHelpOptions = true,
        description = "Compact the generational span of all PCES files in a given directory tree.")
@SubcommandOf(PcesCommand.class)
public class PcesCompactCommand extends AbstractCommand {

    private Path rootDirectory;

    @CommandLine.Parameters(description = "The root directory of the PCES files to compact.")
    private void setRootDirectory(final Path rootDirectory) {
        this.rootDirectory = pathMustExist(rootDirectory);
    }

    /**
     * Entry point for program.
     */
    @Override
    public Integer call() {
        BootstrapUtils.setupConstructableRegistry();
        PcesUtilities.compactPreconsensusEventFiles(rootDirectory);
        return 0;
    }
}
