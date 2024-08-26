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
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConcurrentStreamingTreeHasherTest {
    private ConcurrentStreamingTreeHasher treeHasher;

    @BeforeEach
    void setUp() {
        treeHasher = new ConcurrentStreamingTreeHasher(Executors.newFixedThreadPool(4));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 32})
    void testAddLeafAndRootHash(int numLeaves) throws NoSuchAlgorithmException {
        for (int i = 1; i <= numLeaves; i++) {
            byte[] leaf = createLeaf("leaf" + i);
            treeHasher.addLeaf(Bytes.wrap(leaf));
        }

        CompletableFuture<Bytes> rootHashFuture = treeHasher.rootHash();
        Bytes rootHash = rootHashFuture.join();

        assertNotNull(rootHash);
        assertEquals(48, rootHash.length()); // SHA-384 produces 48-byte hash
    }

    @Test
    void testAddLeafThrowsWhenMaxDepthReached() throws NoSuchAlgorithmException {
        for (int i = 1; i <= (1 << 28); i++) {
            byte[] leaf = createLeaf("leaf" + i);
            treeHasher.addLeaf(Bytes.wrap(leaf));
        }

        CompletableFuture<Bytes> rootHashFuture = treeHasher.rootHash();
        final var exception = assertThrows(CompletionException.class, rootHashFuture::join);
        assertNotNull(exception.getCause());
        assertEquals(IllegalArgumentException.class, exception.getCause().getClass());
        assertEquals("Cannot combine hashes at depth 24", exception.getCause().getMessage());
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

    private byte[] createLeaf(String data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-384").digest(data.getBytes());
    }
}
