/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.utils;

import static com.hedera.node.app.util.MerkleTree.sha384;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.util.MerkleTree;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerkleTreeTest {

    @Test
    void testCalculateMerkleRoot() throws Exception {
        List<String> elements = Arrays.asList("element1", "element2", "element3");
        String rootHash = MerkleTree.calculateMerkleRoot(elements);
        assertThat(rootHash).isNotNull();
        assertThat(rootHash).isEqualTo("f92c610c952de8bcca0a51a94efae4fb251013380ee0c5d213b430aac69d72daf54fbd26ae5196277dbd26d80a42a020");
    }

    @Test
    void testCollisionResistance() {
        boolean foundCollision = false;
        // A collision occurs when two different inputs produce the same hash output.
        for (long i = 0L; i < 10000L; i++) {
            for (long j = 10000L; j < 20000L; j++) {
                String hash1 = sha384("input" + i);
                String hash2 = sha384("input" + j);
                if (hash1.equals(hash2)) {
                    foundCollision = true;
                    break;
                }
            }
            if (foundCollision) break;
        }
        assertThat(foundCollision).isFalse();
    }

    @Test
    void validateHashingWorksAsExpected() {
        boolean foundCollision = false;
        // Ensure the same inputs to the hash algorithm produce the same hash output.
        for (long i = 0L; i < 10000L; i++) {
            for (long j = 0L; j < 10000L; j++) {
                String hash1 = sha384("input" + i);
                String hash2 = sha384("input" + j);
                if (hash1.equals(hash2)) {
                    foundCollision = true;
                    break;
                }
            }
            if (foundCollision) break;
        }
        assertThat(foundCollision).isTrue().withFailMessage("A collision was found.");
    }


}
