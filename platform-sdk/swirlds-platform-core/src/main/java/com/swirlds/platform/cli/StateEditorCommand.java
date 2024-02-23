/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.cli.commands.StateCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.config.DefaultConfiguration;
import com.swirlds.platform.state.editor.StateEditor;
import com.swirlds.platform.util.BootstrapUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine;

@CommandLine.Command(
        name = "editor",
        mixinStandardHelpOptions = true,
        description = "Launch an interactive state editor.")
@SubcommandOf(StateCommand.class)
public class StateEditorCommand extends AbstractCommand {

    private Path statePath;

    /**
     * Load configuration from these files.
     */
    private List<Path> configurationPaths = List.of();

    /**
     * Set the path to state A.
     */
    @CommandLine.Parameters(description = "The path to the state to open with the editor")
    private void setStatePath(final Path statePath) {
        this.statePath = pathMustExist(statePath.toAbsolutePath());
    }

    /**
     * Set the configuration paths.
     */
    @CommandLine.Option(
            names = {"-c", "--config"},
            description = "A path to where a configuration file can be found. If not provided then defaults are used.")
    private void setConfigurationPath(final List<Path> configurationPaths) {
        configurationPaths.forEach(this::pathMustExist);
        this.configurationPaths = configurationPaths;
    }

    /**
     * This method is called after command line input is parsed.
     *
     * @return return code of the program
     */
    @Override
    public Integer call() throws IOException {
        DefaultConfiguration.buildBasicConfiguration(getAbsolutePath("settings.txt"), configurationPaths);
        BootstrapUtils.setupConstructableRegistry();

        new StateEditor(statePath).start();

        return 0;
    }
}
