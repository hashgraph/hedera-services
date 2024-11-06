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
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Browser;
import com.swirlds.platform.CommandLineArgs;
import java.io.IOException;
import java.util.Set;
import picocli.CommandLine;

@CommandLine.Command(
        name = "recover",
        mixinStandardHelpOptions = true,
        description = "Start the platform in PCES recovery mode. The platform will replay events from disk, "
                + "save a state, then shut down.")
@SubcommandOf(PcesCommand.class)
public class PcesRecoveryCommand extends AbstractCommand {
    @CommandLine.Parameters(description = "The node ID to run in recovery mode", index = "0")
    private long nodeId;

    @Override
    public Integer call() throws IOException, InterruptedException {
        Browser.launch(new CommandLineArgs(Set.of(NodeId.of(nodeId))), true);
        return null;
    }
}
