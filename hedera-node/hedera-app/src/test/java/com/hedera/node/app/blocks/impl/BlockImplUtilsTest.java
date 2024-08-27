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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;

class BlockImplUtilsTest {
    @Test
    void testCombineNormalCase() throws NoSuchAlgorithmException {
        byte[] leftHash = MessageDigest.getInstance("SHA-384").digest("left".getBytes());
        byte[] rightHash = MessageDigest.getInstance("SHA-384").digest("right".getBytes());
        byte[] combinedHash = BlockImplUtils.combine(leftHash, rightHash);

        assertNotNull(combinedHash);
        assertEquals(48, combinedHash.length); // SHA-384 produces 48-byte hash
    }

    @Test
    void testCombineEmptyHashes() throws NoSuchAlgorithmException {
        byte[] emptyHash = MessageDigest.getInstance("SHA-384").digest(new byte[0]);
        byte[] combinedHash = BlockImplUtils.combine(emptyHash, emptyHash);

        assertNotNull(combinedHash);
        assertEquals(48, combinedHash.length); // SHA-384 produces 48-byte hash
    }

    @Test
    void testCombineDifferentHashes() throws NoSuchAlgorithmException {
        byte[] leftHash = MessageDigest.getInstance("SHA-384").digest("left".getBytes());
        byte[] rightHash = MessageDigest.getInstance("SHA-384").digest("right".getBytes());
        byte[] combinedHash1 = BlockImplUtils.combine(leftHash, rightHash);
        byte[] combinedHash2 = BlockImplUtils.combine(rightHash, leftHash);

        assertNotNull(combinedHash1);
        assertNotNull(combinedHash2);
        assertNotEquals(new String(combinedHash1), new String(combinedHash2));
    }

    @Test
    void testCombineWithNull() {
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(null, new byte[0]));
        assertThrows(NullPointerException.class, () -> BlockImplUtils.combine(new byte[0], null));
    }
}
