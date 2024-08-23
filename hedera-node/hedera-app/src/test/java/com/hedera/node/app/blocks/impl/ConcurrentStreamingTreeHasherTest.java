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

package com.hedera.node.app.blocks.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConcurrentStreamingTreeHasherTest {
    private ConcurrentStreamingTreeHasher treeHasher;

    @BeforeEach
    void setUp() {
        treeHasher = new ConcurrentStreamingTreeHasher(Executors.newFixedThreadPool(4));
    }

    @Test
    void testAddLeafAndRootHash() throws NoSuchAlgorithmException {
        byte[] leaf1 = MessageDigest.getInstance("SHA-384").digest("leaf1".getBytes());
        byte[] leaf2 = MessageDigest.getInstance("SHA-384").digest("leaf2".getBytes());

        treeHasher.addLeaf(Bytes.wrap(leaf1));
        treeHasher.addLeaf(Bytes.wrap(leaf2));

        CompletableFuture<Bytes> rootHashFuture = treeHasher.rootHash();
        Bytes rootHash = rootHashFuture.join();

        assertNotNull(rootHash);
        assertEquals(48, rootHash.length()); // SHA-384 produces 48-byte hash
    }

    @Test
    void testAddLeafAfterRootHashRequested() {
        byte[] leaf = new byte[48];

        treeHasher.addLeaf(Bytes.wrap(leaf));
        treeHasher.rootHash();

        assertThrows(IllegalStateException.class, () -> treeHasher.addLeaf(Bytes.wrap(leaf)));
    }

    @Test
    void testRootHashWithNoLeaves() {
        CompletableFuture<Bytes> rootHashFuture = treeHasher.rootHash();
        Bytes rootHash = rootHashFuture.join();

        assertNotNull(rootHash);
        assertEquals(48, rootHash.length()); // SHA-384 produces 48-byte hash
    }

    @Test
    void testConcurrentAddLeafAndRootHash() throws NoSuchAlgorithmException {
        byte[] leaf1 = MessageDigest.getInstance("SHA-384").digest("leaf1".getBytes());
        byte[] leaf2 = MessageDigest.getInstance("SHA-384").digest("leaf2".getBytes());

        CompletableFuture<Void> addLeafFuture1 =
                CompletableFuture.runAsync(() -> treeHasher.addLeaf(Bytes.wrap(leaf1)));
        CompletableFuture<Void> addLeafFuture2 =
                CompletableFuture.runAsync(() -> treeHasher.addLeaf(Bytes.wrap(leaf2)));

        CompletableFuture.allOf(addLeafFuture1, addLeafFuture2).join();

        CompletableFuture<Bytes> rootHashFuture = treeHasher.rootHash();
        Bytes rootHash = rootHashFuture.join();

        assertNotNull(rootHash);
        assertEquals(48, rootHash.length()); // SHA-384 produces 48-byte hash
    }
}
