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

package com.swirlds.state.merkle.singleton;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.primitives.ProtoString;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.state.test.fixtures.merkle.MerkleTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SingletonNodeTest extends MerkleTestBase {

    private SingletonNode<ProtoString> node;
    private String expectedValue;

    @BeforeEach
    void setUp() {
        expectedValue = randomString(7);
        node = new SingletonNode<>(
                FIRST_SERVICE,
                STEAM_STATE_KEY,
                SingletonNode.CLASS_ID,
                ProtoString.PROTOBUF,
                ProtoString.newBuilder().value(expectedValue).build());
        // emulate hashing
        node.setHash(new Hash(DigestType.SHA_384));
    }

    @Test
    public void testGetValue() {
        assertEquals(expectedValue, node.getValue().value());
    }

    @Test
    public void testSetValue() {
        final String newValue = randomString(7);
        node.setValue(ProtoString.newBuilder().value(newValue).build());

        assertEquals(newValue, node.getValue().value());
        assertNull(node.getRight().getHash());
    }
}
