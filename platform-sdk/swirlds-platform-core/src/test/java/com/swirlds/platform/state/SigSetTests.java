/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.state.signed.SigSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SigSet Tests")
class SigSetTests {

    private static Set<Long> getSigningNodes(final SigSet sigSet) {
        final Set<Long> nodes = new HashSet<>();
        sigSet.iterator().forEachRemaining(nodes::add);
        return nodes;
    }

    private static Map<Long, Signature> generateSignatureMap(final Random random) {
        final Map<Long, Signature> signatures = new HashMap<>();

        for (int i = 0; i < 1_000; i++) {
            // There will be a few duplicates, but that doesn't really matter
            final long nodeId = random.nextLong(0, 10_000);
            final Signature signature = randomSignature(random);
            signatures.put(nodeId, signature);
        }

        return signatures;
    }

    @Test
    @DisplayName("Basic Operation Test")
    void basicOperationTest() {
        final Random random = getRandomPrintSeed();

        final Map<Long, Signature> signatures = generateSignatureMap(random);

        final SigSet sigSet = new SigSet();
        final Set<Long> addedNodes = new HashSet<>();

        for (final long node : signatures.keySet()) {

            sigSet.addSignature(node, signatures.get(node));
            addedNodes.add(node);

            // Sometimes add twice. This should have no effect.
            if (random.nextBoolean()) {
                sigSet.addSignature(node, signatures.get(node));
            }

            assertEquals(addedNodes.size(), sigSet.size());

            for (final long metaNode : signatures.keySet()) {
                if (addedNodes.contains(metaNode)) {
                    assertTrue(sigSet.hasSignature(metaNode));
                    assertSame(signatures.get(metaNode), sigSet.getSignature(metaNode));
                } else {
                    assertFalse(sigSet.hasSignature(metaNode));
                    assertNull(sigSet.getSignature(metaNode));
                }
            }

            assertEquals(addedNodes, getSigningNodes(sigSet));
        }
    }

    @Test
    @DisplayName("Serialization Test")
    void serializationTest() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");

        final Random random = getRandomPrintSeed();
        final SigSet sigSet = new SigSet();

        generateSignatureMap(random).forEach(sigSet::addSignature);

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        out.writeSerializable(sigSet, true);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));

        final SigSet deserializedSigSet = in.readSerializable();

        assertEquals(sigSet.size(), deserializedSigSet.size());
        assertEquals(getSigningNodes(sigSet), getSigningNodes(deserializedSigSet));

        for (final long node : sigSet) {
            assertEquals(sigSet.getSignature(node), deserializedSigSet.getSignature(node));
        }
    }
}
