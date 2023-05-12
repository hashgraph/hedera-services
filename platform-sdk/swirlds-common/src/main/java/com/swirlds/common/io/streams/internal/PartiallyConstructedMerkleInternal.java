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

package com.swirlds.common.io.streams.internal;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import java.util.LinkedList;
import java.util.List;

/**
 * Container for holding data gathered during the deserialization MerkleInternal.
 */
public class PartiallyConstructedMerkleInternal {

    private final MerkleInternal node;
    private final int version;
    private final int expectedChildCount;

    private final List<MerkleNode> children;

    public PartiallyConstructedMerkleInternal(MerkleInternal node, int version, int expectedChildCount) {
        this.node = node;
        this.version = version;
        this.expectedChildCount = expectedChildCount;
        this.children = new LinkedList<>();
    }

    public boolean hasAllChildren() {
        return expectedChildCount == children.size();
    }

    public void addChild(MerkleNode child) {
        children.add(child);
    }

    public void finishConstruction() {
        node.addDeserializedChildren(children, version);
    }
}
