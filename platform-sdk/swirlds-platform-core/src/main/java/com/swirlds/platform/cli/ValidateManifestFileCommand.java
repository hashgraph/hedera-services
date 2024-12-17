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

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.config.DefaultConfiguration;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

@CommandLine.Command(
        name = "validate-manifest-file",
        mixinStandardHelpOptions = true,
        description = "Validate whether an emergency recovery file is well formed and has the necessary information")
@SubcommandOf(PlatformCli.class)
public class ValidateManifestFileCommand extends AbstractCommand {
    private static final Logger logger = LogManager.getLogger(ValidateManifestFileCommand.class);

    /** The path to the emergency recovery file. */
    private Path dir;

    @SuppressWarnings("unused")
    @CommandLine.Parameters(
            description = "the path to dir containing manifest file which should be named emergencyRecovery.yaml")
    private void setDir(final Path dir) {
        this.pathMustExist(dir);
        this.dir = dir;
    }

    @Override
    public @NonNull Integer call() throws IOException {
        final Configuration configuration = DefaultConfiguration.buildBasicConfiguration(ConfigurationBuilder.create());
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);

        EmergencyRecoveryFile.read(stateConfig, dir, true);
        logger.info(
                LogMarker.CLI.getMarker(),
                "The emergency recovery file is well formed and has the necessary information.");
        return 0;
    }
}
