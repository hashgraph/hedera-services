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
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.common.test.RandomUtils.randomSignature;
import static com.swirlds.platform.Utilities.isMajority;
import static com.swirlds.platform.state.manager.SignedStateManagerTestUtils.buildFakeSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.platform.state.signed.SigSet;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("State Signing Tests")
class StateSigningTests {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Add Valid Signatures Test")
    void addValidSignaturesTest(final boolean evenStaking) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setWeightDistributionStrategy(
                        evenStaking
                                ? RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookGenerator.WeightDistributionStrategy.GAUSSIAN)
                .setSequentialIds(false)
                .setSize(nodeCount)
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

        final Set<Long> signaturesAdded = new HashSet<>();

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
            final boolean completed = signedState.addSignature(address.getId(), signature);
            final boolean nowComplete = signedState.isComplete();

            if (nowComplete) {
                signedState.throwIfIncomplete();
            } else {
                assertThrows(SignedStateInvalidException.class, signedState::throwIfIncomplete);
            }

            if (!previouslyComplete || !nowComplete) {
                count++;
                expectedWeight += address.getWeight();
                signaturesAdded.add(address.getId());
            }

            if (completed) {
                assertTrue(!previouslyComplete && nowComplete);
            }

            if (random.nextBoolean()) {
                // Sometimes offer the signature more than once. This should have no effect
                // since duplicates are ignored.
                assertFalse(signedState.addSignature(address.getId(), signature));
            }

            assertEquals(isMajority(expectedWeight, addressBook.getTotalWeight()), signedState.isComplete());
            assertEquals(expectedWeight, signedState.getSigningWeight());
            assertEquals(count, sigSet.size());

            for (int metaIndex = 0; metaIndex < nodeCount; metaIndex++) {
                final long nodeId = nodes.get(metaIndex).getId();

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
    private boolean isInvalid(final long nodeId) {
        return nodeId % 5 == 0;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Add Invalid Signatures Test")
    void addInvalidSignaturesTest(final boolean evenStaking) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setWeightDistributionStrategy(
                        evenStaking
                                ? RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookGenerator.WeightDistributionStrategy.GAUSSIAN)
                .setSequentialIds(false)
                .setSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        final Set<Long> signaturesAdded = new HashSet<>();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            if (isInvalid(address.getId())) {
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
            final boolean completed = signedState.addSignature(address.getId(), signature);
            final boolean nowComplete = signedState.isComplete();

            if (!isInvalid(address.getId()) && !previouslyComplete) {
                count++;
                expectedWeight += address.getWeight();
                signaturesAdded.add(address.getId());
            }

            if (completed) {
                assertTrue(!previouslyComplete && nowComplete);
            }

            if (random.nextBoolean()) {
                // Sometimes offer the signature more than once. This should have no effect
                // since duplicates are ignored.
                assertFalse(signedState.addSignature(address.getId(), signature));
            }

            assertEquals(isMajority(expectedWeight, addressBook.getTotalWeight()), signedState.isComplete());
            assertEquals(expectedWeight, signedState.getSigningWeight());
            assertEquals(count, sigSet.size());

            for (int metaIndex = 0; metaIndex < nodeCount; metaIndex++) {
                final long nodeId = nodes.get(metaIndex).getId();

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
    void signatureBecomesInvalidTest(final boolean evenStaking) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setWeightDistributionStrategy(
                        evenStaking
                                ? RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookGenerator.WeightDistributionStrategy.GAUSSIAN)
                .setSequentialIds(false)
                .setSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setAddressBook(addressBook)
                .setSignatures(new HashMap<>())
                .build();

        final Set<Long> signaturesAdded = new HashSet<>();
        long expectedWeight = 0;

        final SigSet sigSet = signedState.getSigSet();
        final Hash hash = signedState.getState().getHash();

        // Randomize address order
        final List<Address> nodes = new ArrayList<>(addressBook.getSize());
        for (final Address address : addressBook) {
            nodes.add(address);
        }
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final Address address : nodes) {
            signatures.add(buildFakeSignature(address.getSigPublicKey(), hash));
        }

        for (int index = 0; index < nodeCount; index++) {
            final boolean alreadyComplete = signedState.isComplete();
            signedState.addSignature(nodes.get(index).getId(), signatures.get(index));
            if (!alreadyComplete) {
                signaturesAdded.add(nodes.get(index).getId());
                expectedWeight += nodes.get(index).getWeight();
            }
        }

        assertTrue(signedState.isComplete());
        assertEquals(signaturesAdded.size(), sigSet.size());
        assertEquals(expectedWeight, signedState.getSigningWeight());

        // Remove a node from the address book
        final long nodeRemovedFromAddressBook = nodes.get(0).getId();
        final long weightRemovedFromAddressBook = nodes.get(0).getWeight();
        signedState.getAddressBook().remove(nodeRemovedFromAddressBook);

        // Tamper with a node's signature
        final long weightWithModifiedSignature = nodes.get(1).getWeight();
        when(signatures.get(1).verifySignature(any(), any())).thenReturn(false);

        signedState.pruneInvalidSignatures();

        assertEquals(signaturesAdded.size() - 2, sigSet.size());
        assertEquals(
                expectedWeight - weightWithModifiedSignature - weightRemovedFromAddressBook,
                signedState.getSigningWeight());

        for (int index = 0; index < nodes.size(); index++) {
            if (index == 0
                    || index == 1
                    || !signaturesAdded.contains(nodes.get(index).getId())) {
                assertNull(sigSet.getSignature(nodes.get(index).getId()));
            } else {
                assertSame(
                        signatures.get(index),
                        sigSet.getSignature(nodes.get(index).getId()));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("All Signatures Become Invalid Test")
    void allSignaturesBecomeInvalidTest(final boolean evenStaking) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setWeightDistributionStrategy(
                        evenStaking
                                ? RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookGenerator.WeightDistributionStrategy.GAUSSIAN)
                .setSequentialIds(false)
                .setSize(nodeCount)
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
            signedState.addSignature(nodes.get(index).getId(), signatures.get(index));
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
    void signaturesInvalidWithDifferentAddressBookTest(final boolean evenStaking) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setWeightDistributionStrategy(
                        evenStaking
                                ? RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookGenerator.WeightDistributionStrategy.GAUSSIAN)
                .setSequentialIds(false)
                .setSize(nodeCount)
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
            signedState.addSignature(nodes.get(index).getId(), signatures.get(index));
        }

        assertTrue(signedState.isComplete());

        final AddressBook newAddressBook = addressBook.copy();
        for (final Address address : newAddressBook) {
            final PublicKey publicKey = mock(PublicKey.class);
            when(publicKey.getAlgorithm()).thenReturn("RSA");
            final Address newAddress = address.copySetSigPublicKey(publicKey);
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
    void signaturesInvalidDueToZeroWeightTest(final boolean evenStaking) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setWeightDistributionStrategy(
                        evenStaking
                                ? RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED
                                : RandomAddressBookGenerator.WeightDistributionStrategy.GAUSSIAN)
                .setSequentialIds(false)
                .setSize(nodeCount)
                .build();

        // set node to zero weight
        final long nodeWithZeroWeight = addressBook.getId(0);
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
            signedState.addSignature(nodes.get(index).getId(), signatures.get(index));
        }

        assertFalse(sigSet.hasSignature(nodeWithZeroWeight), "Signature for node with zero weight should not be added");
        assertTrue(signedState.isComplete());

        final AddressBook newAddressBook = new AddressBook();
        int i = 0;
        for (final Address address : addressBook) {
            newAddressBook.add(address.copySetWeight(0));
            assertTrue(address.equalsWithoutWeightAndOwnHost(newAddressBook.getAddress(newAddressBook.getId(i))));
            i++;
        }

        signedState.pruneInvalidSignatures(newAddressBook);

        assertEquals(0, sigSet.size());
        assertEquals(0, signedState.getSigningWeight());
        assertFalse(signedState.isComplete());
    }
}
