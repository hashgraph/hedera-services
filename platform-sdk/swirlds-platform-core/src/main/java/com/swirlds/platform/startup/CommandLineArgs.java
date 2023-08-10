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

package com.swirlds.platform.startup;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Command line arguments that can be passed to the broswer when starting up
 * @param localNodesToStart the set of nodes to start on this machine
 */
public record CommandLineArgs(@NonNull Set<NodeId> localNodesToStart) {
    /**
     * Parse the command line arguments passed to the main method
     * @param args the arguments to parse
     * @return the parsed arguments
     */
    public static CommandLineArgs parse(@Nullable final String[] args) {
        // This set contains the nodes set by the command line to start, if none are passed, then IP
        // addresses will be compared to determine which node to start
        final Set<NodeId> localNodesToStart = new HashSet<>();

        // Parse command line arguments (rudimentary parsing)
        String currentOption = null;
        if (args != null) {
            for (final String item : args) {
                final String arg = item.trim().toLowerCase();
                if (arg.equals("-local")) {
                    currentOption = arg;
                } else if (currentOption != null) {
                    try {
                        localNodesToStart.add(new NodeId(Integer.parseInt(arg)));
                    } catch (final NumberFormatException ex) {
                        // Intentionally suppress the NumberFormatException
                    }
                }
            }
        }
        return new CommandLineArgs(localNodesToStart);
    }
}
