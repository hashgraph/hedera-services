/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.copy;

import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * This class provides utility methods for initializing trees.
 */
public final class MerkleInitialize {

    private MerkleInitialize() {}

    /**
     * Initialize the tree after deserialization.
     * @param configuration
     *      the configuration for this node
     * @param root
     * 		the tree (or subtree) to initialize
     * @param deserializationVersions
     * 		the versions of classes at deserialization
     * @return the root of the tree, possibly different than original root if the root has been migrated
     */
    public static MerkleNode initializeAndMigrateTreeAfterDeserialization(
            @NonNull final Configuration configuration, final MerkleNode root, final Map<Long /* class ID */, Integer /* version */> deserializationVersions) {

        if (root == null) {
            return null;
        }

        // Leaf nodes don't require initialization and implement ExternalSelfSerializable,
        // and any internal node that implements ExternalSelfSerializable must handle its own
        // serialization and migration
        final Predicate<MerkleNode> filter = node -> !(node instanceof ExternalSelfSerializable);

        // If a node should not be initialized, then neither should any of its descendants be initialized.
        final Predicate<MerkleInternal> descendantFilter = filter::test;

        root.treeIterator()
                .setFilter(filter)
                .setDescendantFilter(descendantFilter)
                .forEachRemaining((final MerkleNode node) -> {
                    final MerkleInternal internal = node.asInternal();

                    for (int childIndex = 0; childIndex < internal.getNumberOfChildren(); childIndex++) {
                        final MerkleNode child = internal.getChild(childIndex);
                        if (child == null) {
                            continue;
                        }

                        final int deserializationVersion = Objects.requireNonNull(
                                deserializationVersions.get(child.getClassId()),
                                "class not discovered during deserialization");

                        final MerkleNode migratedChild = child.migrate(configuration, deserializationVersion);
                        if (migratedChild != child) {
                            internal.setChild(childIndex, migratedChild);
                        }
                    }

                    node.asInternal().rebuild();
                });

        final int deserializationVersion = Objects.requireNonNull(
                deserializationVersions.get(root.getClassId()), "class not discovered during deserialization");

        // Pass the configuration to the root on its migration -- it is needed to create Virtual Map
        final MerkleNode migratedRoot = root.migrate(configuration, deserializationVersion);
        if (migratedRoot != root) {
            root.release();
        }

        return migratedRoot;
    }

    /**
     * Initialize the tree after it has been copied.
     *
     * @param root
     * 		the tree (or subtree) to initialize
     */
    public static void initializeTreeAfterCopy(final MerkleNode root) {
        if (root == null) {
            return;
        }

        final Predicate<MerkleNode> filter = node -> !node.isLeaf();

        root.treeIterator().setFilter(filter).forEachRemaining(node -> ((MerkleInternal) node).rebuild());
    }
}
