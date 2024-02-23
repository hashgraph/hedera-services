/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.merkle;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.test.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class MerkleMemoryEstimator {

    double measureMemoryUsedMb() {
        double memoryUsed =
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        return memoryUsed / 1024 / 1024;
    }

    /**
     * Estimate the memory size of a merkle tree.
     */
    @Test
    @Tag(TestTypeTags.PERFORMANCE)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Estimate Balanced Tree Size")
    void estimateBalancedTreeSize() {

        double initialMemoryUsed = measureMemoryUsedMb();

        MerkleNode node = MerkleTestUtils.generateRandomBalancedTree(1337, 20, 2, 100, 50);

        MerkleInternal root = new DummyMerkleInternal();
        root.setChild(0, node);
        root.setChild(1, new DummyMerkleLeaf("A"));
        root.setChild(2, new DummyMerkleLeaf("B"));
        root.setChild(3, new DummyMerkleLeaf("C"));
        root.setChild(4, new DummyMerkleLeaf("D"));

        double finalMemoryUsed = measureMemoryUsedMb();

        System.out.println("Initial memory used: " + initialMemoryUsed + " Mb");
        System.out.println("Final memory used: " + finalMemoryUsed + " Mb");
        System.out.println("Memory used by tree (estimated): " + (finalMemoryUsed - initialMemoryUsed) + " Mb");
    }
}
