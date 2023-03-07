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

package com.swirlds.platform.state.editor;

import static com.swirlds.common.formatting.TextEffect.BRIGHT_CYAN;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_GREEN;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_RED;
import static com.swirlds.common.formatting.TextEffect.BRIGHT_YELLOW;

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.common.merkle.route.MerkleRouteUtils;
import java.nio.file.Path;

/**
 * Formatting utilities for the state editor.
 */
final class StateEditorUtils {

    private StateEditorUtils() {}

    /**
     * Format a file path.
     */
    public static String formatFile(final Path file) {
        return BRIGHT_CYAN.apply(file.toString());
    }

    /**
     * Format a merkle route.
     */
    public static String formatNode(final MerkleNode node) {
        if (node == null) {
            return BRIGHT_YELLOW.apply("null");
        }

        return formatRoute(node.getRoute()) + " " + formatNodeType(node);
    }

    /**
     * Format a node type.
     */
    public static String formatNodeType(final MerkleNode node) {
        if (node == null) {
            return BRIGHT_YELLOW.apply("null");
        }

        return BRIGHT_YELLOW.apply(node.getClass().getSimpleName());
    }

    /**
     * Format a merkle route.
     */
    public static String formatRoute(final MerkleRoute route) {
        return BRIGHT_RED.apply(MerkleRouteUtils.merkleRouteToPathFormat(route));
    }

    /**
     * Format a child index.
     */
    public static String formatChildIndex(final int index) {
        return BRIGHT_GREEN.apply(String.valueOf(index));
    }

    /**
     * Format a merkle parent with a child index.
     */
    public static String formatParent(final MerkleNode parent, final int childIndex) {
        return formatNode(parent) + "[" + formatChildIndex(childIndex) + "]";
    }
}
