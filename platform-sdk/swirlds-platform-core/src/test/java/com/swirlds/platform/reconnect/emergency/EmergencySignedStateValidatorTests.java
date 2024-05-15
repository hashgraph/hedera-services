/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.platform.state.manager.SignatureVerificationTestUtils.buildFakeSignature;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateInvalidException;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class EmergencySignedStateValidatorTests {

    private static final long WEIGHT_PER_NODE = 100L;
    private static final int NUM_NODES = 4;
    private static final long EMERGENCY_ROUND = 20L;
    private static final StateConfig STATE_CONFIG =
            new TestConfigBuilder().getOrCreateConfig().getConfigData(StateConfig.class);
    private AddressBook addressBook;
    private EmergencySignedStateValidator validator;

    @BeforeEach
    void setup() {
        addressBook = RandomAddressBookBuilder.create(Randotron.create())
                .withSize(NUM_NODES)
                .withAverageWeight(WEIGHT_PER_NODE)
                .withWeightDistributionStrategy(RandomAddressBookBuilder.WeightDistributionStrategy.BALANCED)
                .build();
    }

    /**
     * A state for a round earlier than request should always fail validation
     */
    @DisplayName("Invalid State - Too Old")
    @Test
    void stateTooOld() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final SignedState oldState = new RandomSignedStateGenerator()
                .setAddressBook(addressBook)
                .setRound(EMERGENCY_ROUND - 1)
                .build();

        validator = new EmergencySignedStateValidator(
                STATE_CONFIG,
                new EmergencyRecoveryFile(
                        EMERGENCY_ROUND, RandomUtils.randomHash(random), RandomUtils.randomInstant(random)));

        assertThrows(
                SignedStateInvalidException.class,
                () -> validator.validate(oldState, addressBook, null),
                "A state older than the state requested should throw an exception");
        assertNextEpochHashEquals(null, oldState, "Next epoch hash should not be set");
    }

    /**
     * A state for the requested round but a different hash should fail validation
     */
    @DisplayName("Invalid State - Hash Does Not Match")
    @Test
    void stateMatchesRoundButNotHash() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final SignedState stateWithWrongHash = new RandomSignedStateGenerator()
                .setAddressBook(addressBook)
                .setRound(EMERGENCY_ROUND)
                .build();
        stateWithWrongHash.getState().setHash(RandomUtils.randomHash(random));

        validator = new EmergencySignedStateValidator(
                STATE_CONFIG,
                new EmergencyRecoveryFile(
                        EMERGENCY_ROUND, RandomUtils.randomHash(), RandomUtils.randomInstant(random)));

        assertThrows(
                SignedStateInvalidException.class,
                () -> validator.validate(stateWithWrongHash, addressBook, null),
                "A state with the requested round but a different hash should throw an exception");
        assertNextEpochHashEquals(null, stateWithWrongHash, "Next epoch hash should not be set");
    }

    /**
     * A state for the requested round and hash should pass validation
     */
    @DisplayName("Valid State - Matches Emergency State")
    @Test
    void stateMatchesRoundAndHash() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final Hash hash = RandomUtils.randomHash(random);
        final SignedState matchingState = new RandomSignedStateGenerator()
                .setAddressBook(addressBook)
                .setRound(EMERGENCY_ROUND)
                .build();
        matchingState.getState().setHash(hash);

        validator = new EmergencySignedStateValidator(
                STATE_CONFIG, new EmergencyRecoveryFile(EMERGENCY_ROUND, hash, RandomUtils.randomInstant(random)));

        assertDoesNotThrow(
                () -> validator.validate(matchingState, addressBook, null),
                "A state with the requested round and hash should pass validation");
        assertNextEpochHashEquals(hash, matchingState, "Unexpected next epoch hash");
    }

    private void assertNextEpochHashEquals(final Hash hash, final SignedState signedState, final String msg) {
        assertEquals(hash, signedState.getState().getPlatformState().getNextEpochHash(), msg);
    }

    /**
     * A valid state for a later round should pass validation
     */
    @DisplayName("Valid Later State")
    @Test
    void validLaterState() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final List<NodeId> majorityWeightNodes = IntStream.range(0, NUM_NODES - 1)
                .mapToObj(index -> addressBook.getNodeId(index))
                .toList();

        final SignedState laterState = new RandomSignedStateGenerator()
                .setAddressBook(addressBook)
                .setRound(EMERGENCY_ROUND + 1)
                .setSignatures(Map.of())
                .build();
        for (final NodeId nodeId : majorityWeightNodes) {
            laterState.addSignature(
                    nodeId,
                    buildFakeSignature(
                            addressBook.getAddress(nodeId).getSigPublicKey(),
                            laterState.getState().getHash()));
        }

        final Hash emergencyHash = RandomUtils.randomHash(random);
        laterState.getState().getPlatformState().setEpochHash(emergencyHash);

        validator = new EmergencySignedStateValidator(
                STATE_CONFIG,
                new EmergencyRecoveryFile(EMERGENCY_ROUND, emergencyHash, RandomUtils.randomInstant(random)));

        assertDoesNotThrow(
                () -> validator.validate(laterState, addressBook, null),
                "A later state signed by a majority of weight should pass validation");
        assertNextEpochHashEquals(null, laterState, "Next epoch hash should not be set");
    }

    /**
     * A state for a later round with the wrong epoch hash should fail validation.
     */
    @DisplayName("Invalid Later State - Wrong Epoch Hash")
    @Test
    void invalidLaterStateWrongEpochHash() {
        final Random random = RandomUtils.getRandomPrintSeed();
        final List<NodeId> majorityWeightNodes =
                IntStream.range(0, NUM_NODES - 1).mapToObj(NodeId::new).toList();

        final SignedState laterState = new RandomSignedStateGenerator()
                .setAddressBook(addressBook)
                .setRound(EMERGENCY_ROUND + 1)
                .setSigningNodeIds(majorityWeightNodes)
                .build();

        final Hash emergencyHash = RandomUtils.randomHash(random);
        final Hash badEpochHash = RandomUtils.randomHash(random);
        laterState.getState().getPlatformState().setNextEpochHash(badEpochHash);

        validator = new EmergencySignedStateValidator(
                STATE_CONFIG,
                new EmergencyRecoveryFile(EMERGENCY_ROUND, emergencyHash, RandomUtils.randomInstant(random)));

        assertThrows(
                SignedStateInvalidException.class,
                () -> validator.validate(laterState, addressBook, null),
                "A later state signed by less than a majority of weight should not pass validation");
        assertNextEpochHashEquals(badEpochHash, laterState, "Next epoch hash should not be set");
    }

    /**
     * A state for a later round signed by less than a majority should fail validation
     */
    @DisplayName("Invalid Later State - Insufficient Signatures")
    @Test
    void invalidLaterStateNotSignedByMajority() {
        final Random random = RandomUtils.getRandomPrintSeed();

        final List<NodeId> lessThanMajorityWeightNodes =
                IntStream.range(0, NUM_NODES / 2).mapToObj(NodeId::new).toList();

        final SignedState laterState = new RandomSignedStateGenerator()
                .setAddressBook(addressBook)
                .setRound(EMERGENCY_ROUND + 1)
                .setSigningNodeIds(lessThanMajorityWeightNodes)
                .build();

        final Hash emergencyHash = RandomUtils.randomHash(random);
        laterState.getState().getPlatformState().setEpochHash(emergencyHash);

        validator = new EmergencySignedStateValidator(
                STATE_CONFIG,
                new EmergencyRecoveryFile(EMERGENCY_ROUND, emergencyHash, RandomUtils.randomInstant(random)));

        assertThrows(
                SignedStateInvalidException.class,
                () -> validator.validate(laterState, addressBook, null),
                "A later state signed by less than a majority of weight should not pass validation");
        assertNextEpochHashEquals(null, laterState, "Next epoch hash should not be set");
    }
}
