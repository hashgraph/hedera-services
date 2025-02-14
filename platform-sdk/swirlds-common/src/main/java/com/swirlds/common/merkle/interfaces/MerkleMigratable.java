/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.interfaces;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Handles migration of merkle node types.
 */
public interface MerkleMigratable {

    /**
     * <p>
     * Optionally migrate this merkle node to a new type. Called when the state is deserialized. If
     * the node returned is not this node, then the returned node is inserted into the tree in the place
     * of this node.
     * </p>
     *
     * <p>
     * Even if this node is not migrated to a new type, it is considered to be a supported use case to use this
     * method to reorganize data in this object or in the subtree below this object. In this use case,
     * this method should just return the original node.
     * </p>
     *
     * <p>
     * For internal nodes, this method is guaranteed to be called after all descendant nodes have been added, migrated,
     * and initialized; and after {@link MerkleInternal#rebuild()} is called on itself. Note that a new node
     * created by the migrate method will not be automatically initialized.
     * </p>
     *
     * <p>
     * It is suggested that the replacement node be setup with the same {@link MerkleRoute} as the original node
     * BEFORE it is returned by this method. This is especially important if the replacement node is a large subtree.
     * If the replacement node does not have the same route, then the merkle utilities will iterate over the
     * replacement subtree and update all of the merkle routes. This can be costly for large subtrees.
     * </p>
     *
     * <p>
     * It is important to note that nodes that have a self serializing internal node as an ancestor will not be
     * automatically migrated by the merkle utilities.
     * </p>
     *
     * @param configuration the configuration for this node
     * @param version       the version of this class when it was deserialized. This parameter may be useful if migration needs
     *                      to be performed only on transitions between specific versions.
     * @return this node if no migration is needed, a new node if this node should be replaced, or null if this
     * node should be deleted and not replaced
     */
    MerkleNode migrate(@NonNull Configuration configuration, final int version);
}
