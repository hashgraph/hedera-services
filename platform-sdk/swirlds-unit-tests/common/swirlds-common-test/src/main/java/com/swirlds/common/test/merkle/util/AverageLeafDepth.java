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

package com.swirlds.common.test.merkle.util;

/**
 * Utility container class for measuring average leaf depth of a merkle tree.
 */
class AverageLeafDepth {
    private int numberOfLeaves;
    /**
     * The sum total of all of the depths of all of the leaves.
     */
    private int totalDepth;

    public AverageLeafDepth() {
        this.numberOfLeaves = 0;
        this.totalDepth = 0;
    }

    public void addLeaves(int numberOfLeaves) {
        this.numberOfLeaves += numberOfLeaves;
    }

    public void addDepth(int totalDepth) {
        this.totalDepth += totalDepth;
    }

    public void add(AverageLeafDepth childDepth) {
        numberOfLeaves += childDepth.numberOfLeaves;
        totalDepth += childDepth.totalDepth;
    }

    public double getAverageDepth() {
        return numberOfLeaves == 0 ? 0 : (((double) totalDepth) / ((double) numberOfLeaves));
    }
}
