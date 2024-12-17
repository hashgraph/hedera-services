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

package com.swirlds.platform;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Set;

/**
 * Command line arguments that can be passed to the browser when starting up
 *
 * @param localNodesToStart the set of nodes to start on this machine
 */
public record CommandLineArgs(@NonNull Set<NodeId> localNodesToStart) {
    /** The command line option to start a set of nodes on this machine */
    public static final String OPTION_LOCAL = "-local";

    /**
     * Parse the command line arguments passed to the main method
     *
     * @param args the arguments to parse
     * @return the parsed arguments
     */
    public static CommandLineArgs parse(@NonNull final String[] args) {
        // This set contains the nodes set by the command line to start.
        // If none are passed, then IP addresses will be compared to determine which node to start.
        final Set<NodeId> localNodesToStart = new HashSet<>();

        // Parse command line arguments (rudimentary parsing)
        String currentOption = null;
        for (final String item : args) {
            final String arg = item.trim().toLowerCase();
            if (arg.equals(OPTION_LOCAL)) {
                currentOption = arg;
            } else if (currentOption != null) {
                try {
                    localNodesToStart.add(NodeId.of(Integer.parseInt(arg)));
                } catch (final NumberFormatException ex) {
                    // Intentionally suppress the NumberFormatException
                }
            }
        }

        return new CommandLineArgs(localNodesToStart);
    }
}
