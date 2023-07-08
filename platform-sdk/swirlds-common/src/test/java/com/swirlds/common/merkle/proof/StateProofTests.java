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

package com.swirlds.common.merkle.proof;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.common.test.merkle.util.MerkleTestUtils.buildLessSimpleTree;
import static com.swirlds.common.test.merkle.util.MerkleTestUtils.buildLessSimpleTreeExtended;
import static com.swirlds.common.utility.Threshold.STRONG_MINORITY;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.interfaces.MerkleType;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.utility.Threshold;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("StateProof Tests")
class StateProofTests {

    @BeforeAll
    static void beforeAll() throws ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    /**
     * Generate enough signatures to meet a threshold.
     */
    @NonNull
    private Map<NodeId, Signature> generateThresholdOfSignatures(
            @NonNull final Random random,
            @NonNull final AddressBook addressBook,
            @NonNull final FakeSignatureBuilder signatureBuilder,
            @NonNull final Hash hash,
            @NonNull final Threshold threshold) {

        final List<NodeId> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address.getNodeId());
        }
        Collections.shuffle(nodes, random);

        final Map<NodeId, Signature> signatures = new HashMap<>();

        long weight = 0;
        for (final NodeId nodeId : nodes) {
            final Address address = addressBook.getAddress(nodeId);
            weight += address.getWeight();
            signatures.put(nodeId, signatureBuilder.fakeSign(hash.getValue(), address.getSigPublicKey()));

            if (threshold.isSatisfiedBy(weight, addressBook.getTotalWeight())) {
                break;
            }
        }

        return signatures;
    }

    /**
     * Serialize then deserialize a state proof.
     *
     * @param stateProof the state proof to serialize and deserialize
     * @return the deserialized state proof
     */
    @NonNull
    private StateProof serializeAndDeserialize(@NonNull final StateProof stateProof) throws IOException {
        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);

        out.writeSerializable(stateProof, true);

        final byte[] bytes = byteOut.toByteArray();
        final SerializableDataInputStream in = new SerializableDataInputStream(new ByteArrayInputStream(bytes));

        final StateProof deserialized = in.readSerializable();
        assertNotSame(stateProof, deserialized);
        return deserialized;
    }

    /**
     * Verify extremely simple scenario, good for debugging bugs with the core logic.
     */
    @Test
    @DisplayName("Basic Behavior Test")
    void basicBehaviorTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final Cryptography cryptography = CryptographyHolder.get();

        final FakeSignatureBuilder signatureBuilder = new FakeSignatureBuilder(random);

        final MerkleNode root = buildLessSimpleTree();
        MerkleCryptoFactory.getInstance().digestTreeSync(root);

        final AddressBook addressBook =
                new RandomAddressBookGenerator(random).setSize(10).build();

        final MerkleLeaf nodeD =
                root.getNodeAtRoute(MerkleRouteFactory.buildRoute(2, 0)).asLeaf();

        final Map<NodeId, Signature> signatures =
                generateThresholdOfSignatures(random, addressBook, signatureBuilder, root.getHash(), SUPER_MAJORITY);

        final StateProof stateProof = new StateProof(cryptography, root, signatures, List.of(nodeD));

        assertEquals(1, stateProof.getPayloads().size());
        assertSame(nodeD, stateProof.getPayloads().get(0));
        assertTrue(stateProof.isValid(cryptography, addressBook, SUPER_MAJORITY, signatureBuilder));
        // Checking a second time shouldn't cause problems
        assertTrue(stateProof.isValid(cryptography, addressBook, SUPER_MAJORITY, signatureBuilder));

        final StateProof deserialized = serializeAndDeserialize(stateProof);

        assertEquals(1, stateProof.getPayloads().size());
        assertNotSame(nodeD, deserialized.getPayloads().get(0));
        assertEquals(nodeD, deserialized.getPayloads().get(0));
        // Checking a second time shouldn't cause problems
        assertTrue(deserialized.isValid(cryptography, addressBook, SUPER_MAJORITY, signatureBuilder));
    }

    private void testWithNPayloads(
            @NonNull final Random random,
            @NonNull final MerkleNode root,
            @NonNull final Cryptography cryptography,
            @NonNull final List<MerkleLeaf> payloads,
            @NonNull final Threshold threshold)
            throws IOException {

        final FakeSignatureBuilder signatureBuilder = new FakeSignatureBuilder(random);

        MerkleCryptoFactory.getInstance().digestTreeSync(root);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(random.nextInt(1, 10))
                .build();

        final Map<NodeId, Signature> signatures =
                generateThresholdOfSignatures(random, addressBook, signatureBuilder, root.getHash(), threshold);

        final StateProof stateProof = new StateProof(cryptography, root, signatures, payloads);

        // Make sure we have all the payloads
        assertEquals(payloads.size(), stateProof.getPayloads().size());
        for (final MerkleLeaf payload : payloads) {
            boolean payloadFound = false;
            for (final MerkleLeaf stateProofPayload : stateProof.getPayloads()) {
                if (payload == stateProofPayload) {
                    payloadFound = true;
                    break;
                }
            }
            assertTrue(payloadFound);
        }

        assertTrue(stateProof.isValid(cryptography, addressBook, threshold, signatureBuilder));
        // Checking a second time shouldn't cause problems
        assertTrue(stateProof.isValid(cryptography, addressBook, threshold, signatureBuilder));

        final StateProof deserialized = serializeAndDeserialize(stateProof);

        // We should have all the payloads, but they should be new instances since they were deserialized
        assertEquals(payloads.size(), deserialized.getPayloads().size());
        for (final MerkleLeaf payload : payloads) {
            boolean payloadFound = false;
            for (final MerkleLeaf stateProofPayload : deserialized.getPayloads()) {
                if (payload.equals(stateProofPayload)) {
                    assertNotSame(payload, stateProofPayload);
                    payloadFound = true;
                    break;
                }
            }
            assertTrue(payloadFound);
        }

        assertTrue(deserialized.isValid(cryptography, addressBook, threshold, signatureBuilder));
        // Checking a second time shouldn't cause problems
        assertTrue(deserialized.isValid(cryptography, addressBook, threshold, signatureBuilder));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    @DisplayName("Multi Payload Test")
    void multiPayloadTest(final int thresholdOrdinal) throws IOException {
        final Random random = getRandomPrintSeed();
        final Cryptography cryptography = CryptographyHolder.get();

        final Threshold threshold = Threshold.values()[thresholdOrdinal];

        final MerkleNode root = buildLessSimpleTreeExtended();
        MerkleCryptoFactory.getInstance().digestTreeSync(root);
        final List<MerkleLeaf> leafNodes = new ArrayList<>();
        root.treeIterator().setFilter(MerkleType::isLeaf).forEachRemaining(node -> leafNodes.add(node.asLeaf()));

        for (int payloadCount = 1; payloadCount < leafNodes.size(); payloadCount++) {
            Collections.shuffle(leafNodes, random);
            final List<MerkleLeaf> payloads = leafNodes.subList(0, payloadCount);
            testWithNPayloads(random, root, cryptography, payloads, threshold);
        }
    }

    private void testWithNInvalidPayloads(
            @NonNull final Random random,
            @NonNull final MerkleNode root,
            @NonNull final Cryptography cryptography,
            @NonNull final List<MerkleLeaf> payloads)
            throws IOException {

        final FakeSignatureBuilder signatureBuilder = new FakeSignatureBuilder(random);

        // Clear out the tree's hashes
        root.treeIterator().forEachRemaining(node -> node.setHash(null));

        // Incorrectly set some of the payload hashes
        final int invalidPayloadCount;
        if (payloads.size() == 1) {
            invalidPayloadCount = 1;
        } else {
            invalidPayloadCount = random.nextInt(1, payloads.size());
        }
        for (int invalidPayloadIndex = 0; invalidPayloadIndex < invalidPayloadCount; invalidPayloadIndex++) {
            payloads.get(invalidPayloadIndex).setHash(randomHash(random));
        }

        // Now, rehash the tree using the incorrect leaf hashes.
        MerkleCryptoFactory.getInstance().digestTreeSync(root);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(random.nextInt(1, 10))
                .build();

        final Map<NodeId, Signature> signatures =
                generateThresholdOfSignatures(random, addressBook, signatureBuilder, root.getHash(), STRONG_MINORITY);

        final StateProof stateProof = new StateProof(cryptography, root, signatures, payloads);

        // Make sure we have all the payloads
        assertEquals(payloads.size(), stateProof.getPayloads().size());
        for (final MerkleLeaf payload : payloads) {
            boolean payloadFound = false;
            for (final MerkleLeaf stateProofPayload : stateProof.getPayloads()) {
                if (payload == stateProofPayload) {
                    payloadFound = true;
                    break;
                }
            }
            assertTrue(payloadFound);
        }

        // serialize and deserialize to make sure the validator is not using the incorrect hashes
        final StateProof deserialized = serializeAndDeserialize(stateProof);

        assertFalse(deserialized.isValid(cryptography, addressBook, STRONG_MINORITY, signatureBuilder));
        // Checking a second time shouldn't cause problems
        assertFalse(deserialized.isValid(cryptography, addressBook, STRONG_MINORITY, signatureBuilder));
    }

    /**
     * Some of the payloads will not have the correct hash.
     */
    @Test
    @DisplayName("Invalid Payload Test")
    void invalidPayloadTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final Cryptography cryptography = CryptographyHolder.get();

        final MerkleNode root = buildLessSimpleTreeExtended();
        final List<MerkleLeaf> leafNodes = new ArrayList<>();
        root.treeIterator().setFilter(MerkleType::isLeaf).forEachRemaining(node -> leafNodes.add(node.asLeaf()));

        for (int payloadCount = 1; payloadCount < leafNodes.size(); payloadCount++) {
            Collections.shuffle(leafNodes, random);
            final List<MerkleLeaf> payloads = leafNodes.subList(0, payloadCount);
            testWithNInvalidPayloads(random, root, cryptography, payloads);
        }
    }

    // TODO
    //  - serialization
    //    - integrity is preserved
    //    - bytes don't change after multiple rounds
    //    - various byte overflow attacks
    //  - various edge cases that are expected to throw
    //  - works with between 1 and many nodes
    //  - works if it includes whole tree
    //  - works on tree that is just a leaf
    //  - well constructed proofs are valid
    //  - detects invalid proofs
    //    - change one or more leaf nodes
    //    - change topology with valid leaf nodes
    //    - invalid signatures (between all invalid and just some invalid)
    //    - signatures not in the address book
    //    - zero stake signatures
    //    - multiple signatures from the same node
    //    - real signatures but with wrong node IDs
    //  - thresholds
}
