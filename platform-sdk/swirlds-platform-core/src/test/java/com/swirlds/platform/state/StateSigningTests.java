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

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.roster.RosterAddressBookBuilder;
import com.swirlds.platform.roster.RosterRetrieverTests;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.signed.SigSet;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.test.fixtures.addressbook.RandomRosterBuilder;
import com.swirlds.platform.test.fixtures.state.RandomSignedStateGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
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
    void addValidSignaturesTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final Roster roster = RandomRosterBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomRosterBuilder.WeightDistributionStrategy.BALANCED
                                : RandomRosterBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRoster(roster)
                .setSignatures(new HashMap<>())
                .build();

        // Randomize address order
        final List<RosterEntry> nodes = new ArrayList<>(roster.rosterEntries());
        Collections.shuffle(nodes, random);

        final Set<NodeId> signaturesAdded = new HashSet<>();

        final SigSet sigSet = signedState.getSigSet();

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final RosterEntry address : nodes) {
            signatures.add(buildFakeSignature(
                    RosterUtils.fetchGossipCaCertificate(address).getPublicKey(),
                    signedState.getState().getHash()));
        }

        long expectedWeight = 0;
        int count = 0;
        for (int index = 0; index < nodeCount; index++) {
            final RosterEntry address = nodes.get(index);
            final Signature signature = signatures.get(index);

            final boolean previouslyComplete = signedState.isComplete();
            final boolean completed = signedState.addSignature(NodeId.of(address.nodeId()), signature);
            final boolean nowComplete = signedState.isComplete();
            final boolean verifiable = signedState.isVerifiable();

            if (verifiable) {
                signedState.throwIfNotVerifiable();
            } else {
                assertThrows(SignedStateInvalidException.class, signedState::throwIfNotVerifiable);
            }

            if (!previouslyComplete || !nowComplete) {
                count++;
                expectedWeight += address.weight();
                signaturesAdded.add(NodeId.of(address.nodeId()));
            }

            if (completed) {
                assertTrue(!previouslyComplete && nowComplete);
            }

            if (random.nextBoolean()) {
                // Sometimes offer the signature more than once. This should have no effect
                // since duplicates are ignored.
                assertFalse(signedState.addSignature(NodeId.of(address.nodeId()), signature));
            }

            assertEquals(
                    SUPER_MAJORITY.isSatisfiedBy(expectedWeight, RosterUtils.computeTotalWeight(roster)),
                    signedState.isComplete());
            assertEquals(
                    MAJORITY.isSatisfiedBy(expectedWeight, RosterUtils.computeTotalWeight(roster)),
                    signedState.isVerifiable());
            assertEquals(expectedWeight, signedState.getSigningWeight());
            assertEquals(count, sigSet.size());

            for (int metaIndex = 0; metaIndex < nodeCount; metaIndex++) {
                final NodeId nodeId = NodeId.of(nodes.get(metaIndex).nodeId());

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

        final Roster roster = RandomRosterBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomRosterBuilder.WeightDistributionStrategy.BALANCED
                                : RandomRosterBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRoster(roster)
                .setSignatures(new HashMap<>())
                .build();

        final Set<NodeId> signaturesAdded = new HashSet<>();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<RosterEntry> nodes = new ArrayList<>(roster.rosterEntries());
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final RosterEntry address : nodes) {
            if (isInvalid(NodeId.of(address.nodeId()))) {
                // A random signature won't be valid with high probability
                signatures.add(randomSignature(random));
            } else {
                signatures.add(buildFakeSignature(
                        RosterUtils.fetchGossipCaCertificate(address).getPublicKey(),
                        signedState.getState().getHash()));
            }
        }

        long expectedWeight = 0;
        int count = 0;
        for (int index = 0; index < nodeCount; index++) {
            final RosterEntry address = nodes.get(index);
            final Signature signature = signatures.get(index);

            final boolean previouslyComplete = signedState.isComplete();
            final boolean completed = signedState.addSignature(NodeId.of(address.nodeId()), signature);
            final boolean nowComplete = signedState.isComplete();

            if (!isInvalid(NodeId.of(address.nodeId())) && !previouslyComplete) {
                count++;
                expectedWeight += address.weight();
                signaturesAdded.add(NodeId.of(address.nodeId()));
            }

            if (completed) {
                assertTrue(!previouslyComplete && nowComplete);
            }

            if (random.nextBoolean()) {
                // Sometimes offer the signature more than once. This should have no effect
                // since duplicates are ignored.
                assertFalse(signedState.addSignature(NodeId.of(address.nodeId()), signature));
            }

            assertEquals(
                    SUPER_MAJORITY.isSatisfiedBy(expectedWeight, RosterUtils.computeTotalWeight(roster)),
                    signedState.isComplete());
            assertEquals(
                    MAJORITY.isSatisfiedBy(expectedWeight, RosterUtils.computeTotalWeight(roster)),
                    signedState.isVerifiable());
            assertEquals(expectedWeight, signedState.getSigningWeight());
            assertEquals(count, sigSet.size());

            for (int metaIndex = 0; metaIndex < nodeCount; metaIndex++) {
                final NodeId nodeId = NodeId.of(nodes.get(metaIndex).nodeId());

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

        final Roster roster = RandomRosterBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomRosterBuilder.WeightDistributionStrategy.BALANCED
                                : RandomRosterBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRoster(roster)
                .setSignatures(new HashMap<>())
                .build();

        final Set<NodeId> signaturesAdded = new HashSet<>();
        long expectedWeight = 0;

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<RosterEntry> nodes = new ArrayList<>(roster.rosterEntries());
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final RosterEntry address : nodes) {
            final Signature signature = buildFakeSignature(
                    RosterUtils.fetchGossipCaCertificate(address).getPublicKey(),
                    signedState.getState().getHash());
            final Signature mockSignature = mock(Signature.class);
            when(mockSignature.getBytes()).thenReturn(signature.getBytes());
            when(mockSignature.getType()).thenReturn(signature.getType());
            signatures.add(mockSignature);
        }

        for (int index = 0; index < nodeCount; index++) {
            final boolean alreadyComplete = signedState.isComplete();
            signedState.addSignature(NodeId.of(nodes.get(index).nodeId()), signatures.get(index));
            if (!alreadyComplete) {
                signaturesAdded.add(NodeId.of(nodes.get(index).nodeId()));
                expectedWeight += nodes.get(index).weight();
            }
        }

        assertTrue(signedState.isComplete());
        assertEquals(signaturesAdded.size(), sigSet.size());
        assertEquals(expectedWeight, signedState.getSigningWeight());

        // Remove a node from the address book
        final NodeId nodeRemovedFromAddressBook = NodeId.of(nodes.get(0).nodeId());
        final long weightRemovedFromAddressBook = nodes.get(0).weight();
        final Roster updatedRoster = signedState
                .getRoster()
                .copyBuilder()
                .rosterEntries(signedState.getRoster().rosterEntries().stream()
                        .filter(re -> re.nodeId() != nodeRemovedFromAddressBook.id())
                        .toList())
                .build();
        signedState
                .getState()
                .getWritablePlatformState()
                .setAddressBook(RosterAddressBookBuilder.buildAddressBook(updatedRoster));

        // Tamper with a node's signature
        final long weightWithModifiedSignature = nodes.get(1).weight();
        final byte[] tamperedBytes = signatures.get(1).getBytes().toByteArray();
        tamperedBytes[0] = 0;
        when(signatures.get(1).getBytes()).thenReturn(Bytes.wrap(tamperedBytes));

        signedState.pruneInvalidSignatures();

        assertEquals(signaturesAdded.size() - 2, sigSet.size());
        assertEquals(
                expectedWeight - weightWithModifiedSignature - weightRemovedFromAddressBook,
                signedState.getSigningWeight());

        for (int index = 0; index < nodes.size(); index++) {
            if (index == 0
                    || index == 1
                    || !signaturesAdded.contains(NodeId.of(nodes.get(index).nodeId()))) {
                assertNull(sigSet.getSignature(NodeId.of(nodes.get(index).nodeId())));
            } else {
                assertSame(
                        signatures.get(index),
                        sigSet.getSignature(NodeId.of(nodes.get(index).nodeId())));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("All Signatures Become Invalid Test")
    void allSignaturesBecomeInvalidTest(final boolean evenWeighting) {
        final Random random = getRandomPrintSeed();

        final int nodeCount = random.nextInt(10, 20);

        final Roster roster = RandomRosterBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomRosterBuilder.WeightDistributionStrategy.BALANCED
                                : RandomRosterBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRoster(roster)
                .setSignatures(new HashMap<>())
                .build();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<RosterEntry> nodes = new ArrayList<>(roster.rosterEntries());
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final RosterEntry address : nodes) {
            signatures.add(buildFakeSignature(
                    RosterUtils.fetchGossipCaCertificate(address).getPublicKey(),
                    signedState.getState().getHash()));
        }

        for (int index = 0; index < nodeCount; index++) {
            signedState.addSignature(NodeId.of(nodes.get(index).nodeId()), signatures.get(index));
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

        final Roster roster = RandomRosterBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomRosterBuilder.WeightDistributionStrategy.BALANCED
                                : RandomRosterBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRoster(roster)
                .setSignatures(new HashMap<>())
                .build();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<RosterEntry> nodes = new ArrayList<>(roster.rosterEntries());
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final RosterEntry address : nodes) {
            signatures.add(buildFakeSignature(
                    RosterUtils.fetchGossipCaCertificate(address).getPublicKey(),
                    signedState.getState().getHash()));
        }

        for (int index = 0; index < nodeCount; index++) {
            signedState.addSignature(NodeId.of(nodes.get(index).nodeId()), signatures.get(index));
        }

        assertTrue(signedState.isComplete());

        final Roster newRoster = roster.copyBuilder()
                .rosterEntries(roster.rosterEntries().stream()
                        .map(re -> {
                            try {
                                return re.copyBuilder()
                                        .gossipCaCertificate(Bytes.wrap(RosterRetrieverTests.randomX509Certificate()
                                                .getEncoded()))
                                        .build();
                            } catch (CertificateEncodingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toList())
                .build();

        signedState.pruneInvalidSignatures(newRoster);

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

        Roster roster = RandomRosterBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomRosterBuilder.WeightDistributionStrategy.BALANCED
                                : RandomRosterBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        // set node to zero weight
        final NodeId nodeWithZeroWeight =
                NodeId.of(roster.rosterEntries().get(0).nodeId());
        roster = roster.copyBuilder()
                .rosterEntries(roster.rosterEntries().stream()
                        .map(re -> {
                            if (re.nodeId() == nodeWithZeroWeight.id()) {
                                return re.copyBuilder().weight(0).build();
                            } else {
                                return re;
                            }
                        })
                        .toList())
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRoster(roster)
                .setSignatures(new HashMap<>())
                .build();

        final SigSet sigSet = signedState.getSigSet();

        // Randomize address order
        final List<RosterEntry> nodes = new ArrayList<>(roster.rosterEntries());
        Collections.shuffle(nodes, random);

        final List<Signature> signatures = new ArrayList<>(nodeCount);
        for (final RosterEntry address : nodes) {
            signatures.add(buildFakeSignature(
                    RosterUtils.fetchGossipCaCertificate(address).getPublicKey(),
                    signedState.getState().getHash()));
        }

        for (int index = 0; index < nodeCount; index++) {
            signedState.addSignature(NodeId.of(nodes.get(index).nodeId()), signatures.get(index));
        }

        assertFalse(sigSet.hasSignature(nodeWithZeroWeight), "Signature for node with zero weight should not be added");
        assertTrue(signedState.isComplete());

        final Roster newRoster = roster.copyBuilder()
                .rosterEntries(roster.rosterEntries().stream()
                        .map(re -> re.copyBuilder().weight(0).build())
                        .toList())
                .build();

        signedState.pruneInvalidSignatures(newRoster);

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

        final Roster roster = RandomRosterBuilder.create(random)
                .withWeightDistributionStrategy(
                        evenWeighting
                                ? RandomRosterBuilder.WeightDistributionStrategy.BALANCED
                                : RandomRosterBuilder.WeightDistributionStrategy.GAUSSIAN)
                .withSize(nodeCount)
                .build();

        final SignedState signedState = new RandomSignedStateGenerator(random)
                .setRoster(roster)
                .setSignatures(new HashMap<>())
                .build();

        assertFalse(signedState.isComplete());

        signedState.markAsRecoveryState();

        // Recovery states are considered to be complete regardless of signature count
        assertTrue(signedState.isComplete());
    }
}
