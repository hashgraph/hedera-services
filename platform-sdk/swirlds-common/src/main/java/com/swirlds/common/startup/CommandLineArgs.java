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

package com.swirlds.common.startup;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Set;

/**
 * Command line arguments that can be passed to the browser when starting up
 *
 * @param localNodesToStart the set of nodes to start on this machine
 */
public record CommandLineArgs(@NonNull Set<NodeId> localNodesToStart, boolean pcesRecovery) {
    /** The command line option to start a set of nodes on this machine */
    public static final String OPTION_LOCAL = "-local";
    /** When set, the platform will perform a PCES recovery and shut down */
    public static final String OPTION_PCES_RECOVERY = "-pces-recovery";

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
        boolean pcesRecovery = false;

        // Parse command line arguments (rudimentary parsing)
        String currentOption = null;
        for (final String item : args) {
            final String arg = item.trim().toLowerCase();
            if (arg.equals(OPTION_PCES_RECOVERY)) {
                pcesRecovery = true;
            } else if (arg.equals(OPTION_LOCAL)) {
                currentOption = arg;
            } else if (currentOption != null) {
                try {
                    localNodesToStart.add(new NodeId(Integer.parseInt(arg)));
                } catch (final NumberFormatException ex) {
                    // Intentionally suppress the NumberFormatException
                }
            }
        }

        return new CommandLineArgs(localNodesToStart, pcesRecovery);
    }
}
