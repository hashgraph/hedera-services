/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomSignature;
import static com.swirlds.common.utility.Threshold.MAJORITY;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;
import static com.swirlds.platform.test.fixtures.state.manager.SignatureVerificationTestUtils.buildFakeSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.signed.SigSet;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.state.RandomSignedStateGenerator;
import com.swirlds.platform.test.fixtures.state.manager.SignatureVerificationTestUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("State Signing Tests")
class StateSigningTests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Add Valid Signatures Test")
    void addValidSignaturesTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final Set<NodeId> signaturesAdded = new HashSet<>();

        final SigSet sigSet = signedState.getSigSet();

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            signatures.add(buildFakeSignature(
                    address.getSigPublicKey(), signedState.getState().getHash()));
        }

        long expectedWeight = 0;
        int count = 0;
        for (int index = 0; index < nodeCount; index++) {
            final Address address = nodes.get(index);
            final Signature signature = signatures.get(index);

            final boolean previouslyComplete = signedState.isComplete();
            final boolean completed = signedState.addSignature(address.getNodeId(), signature);
            final boolean nowComplete = signedState.isComplete();
            final boolean verifiable = signedState.isVerifiable();

            if (verifiable) {
                signedState.throwIfNotVerifiable();
            } else {
                assertThrows(SignedStateInvalidException.class, signedState::throwIfNotVerifiable);
            }

            if (!previouslyComplete || !nowComplete) {
                count++;
                expectedWeight += address.getWeight();
                signaturesAdded.add(address.getNodeId());
            }

            if (completed) {
                assertTrue(!previouslyComplete && nowComplete);
            }

            if (random.nextBoolean()) {
                // Sometimes offer the signature more than once. This should have no effect
                // since duplicates are ignored.
                assertFalse(signedState.addSignature(address.getNodeId(), signature));
            }

            assertEquals(
                    SUPER_MAJORITY.isSatisfiedBy(expectedWeight, addressBook.getTotalWeight()),
                    signedState.isComplete());
            assertEquals(
                    MAJORITY.isSatisfiedBy(expectedWeight, addressBook.getTotalWeight()), signedState.isVerifiable());
            assertEquals(expectedWeight, signedState.getSigningWeight());
            assertEquals(count, sigSet.size());

            for (int metaIndex = 0; metaIndex < nodeCount; metaIndex++) {
                final NodeId nodeId = nodes.get(metaIndex).getNodeId();

                if (signaturesAdded.contains(nodeId)) {
                    // We have added this signature, make sure the sigset is tracking it
                    assertSame(signatures.get(metaIndex), sigSet.getSignature(nodeId));
                } else {
                    // We haven't yet added this signature, the sigset should not be tracking it
                    assertNull(sigSet.getSignature(nodeId));
                }
            }

            if (random.nextBoolean()) {
                // This should have no effect
                signedState.pruneInvalidSignatures();
            }
        }
    }

    /**
     * For the test below, treat all nodes divisible by 5 as having invalid signatures.
     */
    private boolean isInvalid(@NonNull final NodeId nodeId) {
        return nodeId.id() % 5 == 0;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Add Invalid Signatures Test")
    void addInvalidSignaturesTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        final Set<NodeId> signaturesAdded = new HashSet<>();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            if (isInvalid(address.getNodeId())) {
                // A random signature won't be valid with high probability
                signatures.add(randomSignature(random));
            } else {
                signatures.add(buildFakeSignature(
                        address.getSigPublicKey(), signedState.getState().getHash()));
            }
        }

        long expectedWeight = 0;
        int count = 0;
        for (int index = 0; index < nodeCount; index++) {
            final Address address = nodes.get(index);
            final Signature signature = signatures.get(index);

            final boolean previouslyComplete = signedState.isComplete();
            final boolean completed = signedState.addSignature(address.getNodeId(), signature);
            final boolean nowComplete = signedState.isComplete();

            if (!isInvalid(address.getNodeId()) && !previouslyComplete) {
                count++;
                expectedWeight += address.getWeight();
                signaturesAdded.add(address.getNodeId());
            }

            if (completed) {
                assertTrue(!previouslyComplete && nowComplete);
            }

            if (random.nextBoolean()) {
                // Sometimes offer the signature more than once. This should have no effect
                // since duplicates are ignored.
                assertFalse(signedState.addSignature(address.getNodeId(), signature));
            }

            assertEquals(
                    SUPER_MAJORITY.isSatisfiedBy(expectedWeight, addressBook.getTotalWeight()),
                    signedState.isComplete());
            assertEquals(
                    MAJORITY.isSatisfiedBy(expectedWeight, addressBook.getTotalWeight()), signedState.isVerifiable());
            assertEquals(expectedWeight, signedState.getSigningWeight());
            assertEquals(count, sigSet.size());

            for (int metaIndex = 0; metaIndex < nodeCount; metaIndex++) {
                final NodeId nodeId = nodes.get(metaIndex).getNodeId();

                if (signaturesAdded.contains(nodeId)) {
                    // We have added this signature, make sure the sigset is tracking it
                    assertSame(signatures.get(metaIndex), sigSet.getSignature(nodeId));
                } else {
                    // We haven't yet added this signature or it is invalid, the sigset should not be tracking it
                    assertNull(sigSet.getSignature(nodeId));
                }
            }

            if (random.nextBoolean()) {
                // This should have no effect
                signedState.pruneInvalidSignatures();
                assertEquals(expectedWeight, signedState.getSigningWeight());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Signature Becomes Invalid Test")
    void signatureBecomesInvalidTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        final Set<NodeId> signaturesAdded = new HashSet<>();
        long expectedWeight = 0;

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            signatures.add(SignatureVerificationTestUtils.buildFakeSignature(
                    address.getSigPublicKey(), signedState.getState().getHash()));
        }

        for (int index = 0; index < nodeCount; index++) {
            final boolean alreadyComplete = signedState.isComplete();
            signedState.addSignature(nodes.get(index).getNodeId(), signatures.get(index));
            if (!alreadyComplete) {
                signaturesAdded.add(nodes.get(index).getNodeId());
                expectedWeight += nodes.get(index).getWeight();
            }
        }

        assertTrue(signedState.isComplete());
        assertEquals(signaturesAdded.size(), sigSet.size());
        assertEquals(expectedWeight, signedState.getSigningWeight());

        // Remove a node from the address book
        final NodeId nodeRemovedFromAddressBook = nodes.get(0).getNodeId();
        final long weightRemovedFromAddressBook = nodes.get(0).getWeight();
        final AddressBook updatedAddressBook = signedState.getAddressBook().remove(nodeRemovedFromAddressBook);
        signedState.getState().getPlatformState().setAddressBook(updatedAddressBook);

        // Tamper with a node's signature
        final long weightWithModifiedSignature = nodes.get(1).getWeight();
        signatures.get(1).getSignatureBytes()[0] = 0;

        signedState.pruneInvalidSignatures();

        assertEquals(signaturesAdded.size() - 2, sigSet.size());
        assertEquals(
                expectedWeight - weightWithModifiedSignature - weightRemovedFromAddressBook,
                signedState.getSigningWeight());

        for (int index = 0; index < nodes.size(); index++) {
            if (index == 0
                    || index == 1
                    || !signaturesAdded.contains(nodes.get(index).getNodeId())) {
                assertNull(sigSet.getSignature(nodes.get(index).getNodeId()));
            } else {
                assertSame(
                        signatures.get(index),
                        sigSet.getSignature(nodes.get(index).getNodeId()));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("All Signatures Become Invalid Test")
    void allSignaturesBecomeInvalidTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            signatures.add(buildFakeSignature(
                    address.getSigPublicKey(), signedState.getState().getHash()));
        }

        for (int index = 0; index < nodeCount; index++) {
            signedState.addSignature(nodes.get(index).getNodeId(), signatures.get(index));
        }

        assertTrue(signedState.isComplete());

        final Hash newHash = randomHash();
        signedState.getState().setHash(newHash);
        signedState.pruneInvalidSignatures();

        assertEquals(0, sigSet.size());
        assertEquals(0, signedState.getSigningWeight());
        assertFalse(signedState.isComplete());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Signatures Invalid With Different Address Book Test")
    void signaturesInvalidWithDifferentAddressBookTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            signatures.add(buildFakeSignature(
                    address.getSigPublicKey(), signedState.getState().getHash()));
        }

        for (int index = 0; index < nodeCount; index++) {
            signedState.addSignature(nodes.get(index).getNodeId(), signatures.get(index));
        }

        assertTrue(signedState.isComplete());

        final AddressBook newAddressBook = addressBook.copy();
        for (final Address address : newAddressBook) {
            final PublicKey publicKey = mock(PublicKey.class);
            when(publicKey.getAlgorithm()).thenReturn("RSA");
            when(publicKey.getEncoded()).thenReturn(new byte[] {1, 2, 3});
            final X509Certificate certificate = mock(X509Certificate.class);
            when(certificate.getPublicKey()).thenReturn(publicKey);
            final Address newAddress = address.copySetSigCert(certificate);
            // This replaces the old address
            newAddressBook.add(newAddress);
        }

        signedState.pruneInvalidSignatures(newAddressBook);

        assertEquals(0, sigSet.size());
        assertEquals(0, signedState.getSigningWeight());
        assertFalse(signedState.isComplete());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Signatures Invalid Due To Zero Weight")
    void signaturesInvalidDueToZeroWeightTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        // set node to zero weight
        final NodeId nodeWithZeroWeight = addressBook.getNodeId(0);
        addressBook.updateWeight(nodeWithZeroWeight, 0);

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            signatures.add(buildFakeSignature(
                    address.getSigPublicKey(), signedState.getState().getHash()));
        }

        for (int index = 0; index < nodeCount; index++) {
            signedState.addSignature(nodes.get(index).getNodeId(), signatures.get(index));
        }

        assertFalse(sigSet.hasSignature(nodeWithZeroWeight), "Signature for node with zero weight should not be added");
        assertTrue(signedState.isComplete());

        final AddressBook newAddressBook = new AddressBook();
        int i = 0;
        for (final Address address : addressBook) {
            newAddressBook.add(address.copySetWeight(0));
            final Address newAddress = newAddressBook.getAddress(newAddressBook.getNodeId(i));
            Assertions.assertNotNull(newAddress);
            assertTrue(address.equalsWithoutWeight(newAddress));
            i++;
        }

        signedState.pruneInvalidSignatures(newAddressBook);

        assertEquals(0, sigSet.size());
        assertEquals(0, signedState.getSigningWeight());
        assertFalse(signedState.isComplete());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Recovery State Is Complete Test")
    void recoveryStateIsCompleteTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = RandomAddressBookBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        assertFalse(signedState.isComplete());

        signedState.markAsRecoveryState();

        // Recovery states are considered to be complete regardless of signature count
        assertTrue(signedState.isComplete());
    }
}
