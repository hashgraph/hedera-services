/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.keys;

import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;
import static com.hedera.test.factories.keys.NodeFactory.threshold;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.keys.KeyTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HederaKeyTraversalTest {
    private static final KeyTree kt =
            KeyTree.withRoot(
                    list(
                            ed25519(),
                            threshold(1, list(list(ed25519(), ed25519()), ed25519()), ed25519()),
                            ed25519(),
                            list(threshold(2, ed25519(), ed25519(), ed25519(), ed25519()))));

    @Test
    void visitsAllSimpleKeys() throws Exception {
        final var jKey = kt.asJKey();
        final var expectedEd25519 = ed25519KeysFromKt(kt);

        final List<ByteString> visitedEd25519 = new ArrayList<>();
        HederaKeyTraversal.visitSimpleKeys(
                jKey,
                simpleJKey -> visitedEd25519.add(ByteString.copyFrom(simpleJKey.getEd25519())));

        assertThat(visitedEd25519, contains(expectedEd25519.toArray()));
    }

    @Test
    void countsSimpleKeys() throws Exception {
        final var jKey = kt.asJKey();

        assertEquals(10, HederaKeyTraversal.numSimpleKeys(jKey));
    }

    @Test
    void countsSimpleKeysForValidAccount() throws Exception {
        final var jKey = kt.asJKey();
        final var account = MerkleAccountFactory.newAccount().accountKeys(jKey).get();

        assertEquals(10, HederaKeyTraversal.numSimpleKeys(account));
    }

    @Test
    void countsZeroSimpleKeysForWeirdAccount() {
        final var account = MerkleAccountFactory.newAccount().get();

        assertEquals(0, HederaKeyTraversal.numSimpleKeys(account));
    }

    private List<ByteString> ed25519KeysFromKt(final KeyTree kt) {
        final List<ByteString> keys = new ArrayList<>();
        kt.traverseLeaves(leaf -> keys.add(leaf.asKey().getEd25519()));
        return keys;
    }
}
