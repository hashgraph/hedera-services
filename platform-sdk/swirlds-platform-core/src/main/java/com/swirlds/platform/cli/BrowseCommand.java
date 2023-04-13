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

import static com.swirlds.platform.system.SystemExitReason.FATAL_ERROR;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.Browser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import picocli.CommandLine;
import picocli.CommandLine.Command;
// # Benign change to test code ownership
@Command(
        name = "browse",
        mixinStandardHelpOptions = true,
        description = "Launch local instances of the platform using the Browser UI. "
                + "Note: the Browser UI expects a very specific file system layout. Such a layout is present in the "
                + " hedera-services/platform-sdk/sdk/ directory.")
@SubcommandOf(PlatformCli.class)
public class BrowseCommand extends AbstractCommand {

    private List<Integer> localNodes = new ArrayList<>();

    @CommandLine.Option(
            names = {"-l", "--local-node"},
            description = "Specify a node that should be run in this JVM. If no nodes are provided, "
                    + "all nodes with local IP addresses are loaded in this JVM.")
    private void setLocalNodes(final List<Integer> localNodes) {
        this.localNodes = localNodes;
    }

    /**
     * This method is called after command line input is parsed.
     *
     * @return return code of the program
     */
    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public Integer call() throws IOException, InterruptedException {
        try {
            Browser.launch(new HashSet<>(localNodes), null);
        } catch (final Exception e) {
            e.printStackTrace();
            return FATAL_ERROR.getExitCode();
        }

        // Sleep forever to keep the process alive.
        while (true) {
            MINUTES.sleep(1);
        }
    }
}
