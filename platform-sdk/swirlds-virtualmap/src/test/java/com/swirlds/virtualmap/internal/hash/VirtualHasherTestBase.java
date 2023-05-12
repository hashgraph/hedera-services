/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.hash;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.virtualmap.TestKey;
import com.swirlds.virtualmap.TestValue;
import com.swirlds.virtualmap.VirtualTestBase;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.merkle.VirtualInternalNode;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class VirtualHasherTestBase extends VirtualTestBase {
    /**
     * Helper method for computing a list of {@link Arguments} of length {@code num}, each of which contains
     * a random list of dirty leave paths between {@code firstLeafPath} and {@code lastLeafPath}.
     *
     * @param num
     * 		The number of different random lists to create
     * @param firstLeafPath
     * 		The firstLeafPath
     * @param lastLeafPath
     * 		The lastLeafPath
     * @return
     * 		A non-null list of {@link Arguments} of random lists of paths.
     */
    protected static List<Arguments> randomDirtyLeaves(
            final int num, final long firstLeafPath, final long lastLeafPath) {
        final List<Arguments> args = new ArrayList<>();
        final Random rand = new Random(42);
        for (int i = 0; i < num; i++) {
            final int numDirtyLeaves = rand.nextInt((int) firstLeafPath);
            if (numDirtyLeaves == 0) {
                i--;
                continue;
            }
            final List<Long> paths = new ArrayList<>();
            for (int j = 0; j < numDirtyLeaves; j++) {
                paths.add(firstLeafPath + rand.nextInt((int) firstLeafPath));
            }
            args.add(Arguments.of(
                    firstLeafPath,
                    lastLeafPath,
                    paths.stream().sorted().distinct().collect(Collectors.toList())));
        }
        return args;
    }

    protected static Hash hashTree(final TestDataSource ds) {
        final HashBuilder hashBuilder = new HashBuilder(DigestType.SHA_384);
        final VirtualInternalRecord root = ds.getInternal(Path.ROOT_PATH);
        assert root != null;
        hashSubTree(ds, hashBuilder, root);
        return root.getHash();
    }

    protected static List<VirtualLeafRecord<TestKey, TestValue>> invalidateNodes(
            final TestDataSource ds, final Stream<Long> dirtyPaths) {
        final List<VirtualLeafRecord<TestKey, TestValue>> leaves = new ArrayList<>();
        dirtyPaths.forEach(i -> {
            final VirtualLeafRecord<TestKey, TestValue> rec = ds.getLeaf(i);
            assert rec != null;
            leaves.add(rec);
            rec.setHash(null);
            long parentPath = Path.getParentPath(rec.getPath());
            while (parentPath >= 0) {
                final VirtualInternalRecord internal = ds.getInternal(parentPath);
                assert internal != null;
                internal.setHash(null);
                parentPath = Path.getParentPath(parentPath);
            }
        });
        return leaves;
    }

    protected static void hashSubTree(
            final TestDataSource ds, final HashBuilder hashBuilder, final VirtualInternalRecord internalNode) {
        final long leftChildPath = Path.getLeftChildPath(internalNode.getPath());
        final Hash leftHash;
        if (leftChildPath < ds.firstLeafPath) {
            final VirtualInternalRecord leftChild = ds.getInternal(leftChildPath);
            assert leftChild != null;
            hashSubTree(ds, hashBuilder, leftChild);
            leftHash = leftChild.getHash();
        } else {
            final VirtualLeafRecord<TestKey, TestValue> leftChild = ds.getLeaf(leftChildPath);
            assert leftChild != null;
            leftHash = leftChild.getHash();
        }

        final long rightChildPath = Path.getRightChildPath(internalNode.getPath());
        Hash rightHash = CRYPTO.getNullHash();
        if (rightChildPath < ds.firstLeafPath) {
            final VirtualInternalRecord rightChild = ds.getInternal(rightChildPath);
            assert rightChild != null;
            hashSubTree(ds, hashBuilder, rightChild);
            rightHash = rightChild.getHash();
        } else {
            final VirtualLeafRecord<TestKey, TestValue> rightChild = ds.getLeaf(rightChildPath);
            if (rightChild != null) {
                rightHash = rightChild.getHash();
            }
        }

        hashBuilder.reset();
        hashBuilder.update(
                internalNode.getPath() == ROOT_PATH ? VirtualRootNode.CLASS_ID : VirtualInternalNode.CLASS_ID);
        hashBuilder.update(
                internalNode.getPath() == ROOT_PATH
                        ? VirtualRootNode.ClassVersion.CURRENT_VERSION
                        : VirtualInternalNode.SERIALIZATION_VERSION);
        hashBuilder.update(leftHash);
        hashBuilder.update(rightHash);
        internalNode.setHash(hashBuilder.build());
        ds.setInternal(internalNode);
    }

    protected static final class TestDataSource {
        private final long firstLeafPath;
        private final long lastLeafPath;
        private final Map<Long, VirtualInternalRecord> internals = new HashMap<>();

        TestDataSource(final long firstLeafPath, final long lastLeafPath) {
            this.firstLeafPath = firstLeafPath;
            this.lastLeafPath = lastLeafPath;
        }

        VirtualLeafRecord<TestKey, TestValue> getLeaf(final long path) {
            if (path < firstLeafPath || path > lastLeafPath) {
                return null;
            }

            final TestKey key = new TestKey(path);
            final TestValue value = new TestValue("Value: " + path);
            final VirtualLeafRecord<TestKey, TestValue> rec = new VirtualLeafRecord<>(path, null, key, value);
            final Hash hash = CRYPTO.digestSync(rec);
            rec.setHash(hash);
            return rec;
        }

        VirtualInternalRecord getInternal(final long path) {
            if (path < Path.ROOT_PATH || path > firstLeafPath) {
                return null;
            }
            VirtualInternalRecord rec = internals.get(path);
            if (rec == null) {
                final Hash hash = CRYPTO.getNullHash();
                rec = new VirtualInternalRecord(path, hash);
            }
            return rec;
        }

        void setInternal(final VirtualInternalRecord internal) {
            internals.put(internal.getPath(), internal);
        }
    }
}
