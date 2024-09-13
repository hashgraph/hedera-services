/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support.validators.block;

import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.platform.state.MerkleRoot;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StateVisualizer {
    private static final Logger logger = LogManager.getLogger(StateVisualizer.class);

    private static final Pattern STATE_ROOT_PATTERN = Pattern.compile(".*MerkleStateRoot.*/.*\\s+(.+)");
    private static final Pattern CHILD_STATE_PATTERN = Pattern.compile("\\s+\\d+ \\w+\\s+(\\S+)\\s+.+\\s+(.+)");

    public static StringBuilder visualizeTree(@NonNull final MerkleRoot state) {
        final var sb = new StringBuilder();
        new MerkleTreeVisualizer(state).setDepth(5).render(sb);
        logger.info("Visualising State Tree: {}", sb);
        return sb;
    }

    public static void visualizeTreeHashesByName(@NonNull final MerkleRoot state) {
        final var builder = visualizeTree(state);
        final var map = hashesByName(builder.toString());
        logger.info("Visualising State Hashes by Name: {}", map);
    }

    public static Map<String, String> hashesByName(@NonNull final String visualization) {
        final var lines = visualization.split("\\n");
        final Map<String, String> hashes = new LinkedHashMap<>();
        for (final var line : lines) {
            final var stateRootMatcher = STATE_ROOT_PATTERN.matcher(line);
            if (stateRootMatcher.matches()) {
                hashes.put("MerkleStateRoot", stateRootMatcher.group(1));
            } else {
                final var childStateMatcher = CHILD_STATE_PATTERN.matcher(line);
                if (childStateMatcher.matches()) {
                    hashes.put(childStateMatcher.group(1), childStateMatcher.group(2));
                } else {
                    logger.warn("Ignoring visualization line '{}'", line);
                }
            }
        }
        return hashes;
    }
}
