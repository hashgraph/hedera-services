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

package com.swirlds.state.merkle;

import static com.swirlds.state.merkle.StateUtils.stateIdentifierOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.hapi.block.stream.output.StateIdentifier;
import com.swirlds.state.test.fixtures.merkle.MerkleTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StateUtilsTest extends MerkleTestBase {
    @ParameterizedTest
    @MethodSource("stateIdsByName")
    void stateIdsByNameAsExpected(@NonNull final String stateName, @NonNull final StateIdentifier stateId) {
        assertThat(stateIdentifierOf(stateName)).isEqualTo(stateId.protoOrdinal());
    }

    @Test
    @DisplayName("Validating a null service name throws an NPE")
    void nullServiceNameThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.validateServiceName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validating a service name with no characters throws an exception")
    void emptyServiceNameThrows() {
        assertThatThrownBy(() -> StateUtils.validateServiceName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service name");
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.state.test.fixtures.merkle.TestArgumentUtils#illegalIdentifiers")
    @DisplayName("Service Names with illegal characters throw an exception")
    void invalidServiceNameThrows(final String serviceName) {
        assertThatThrownBy(() -> StateUtils.validateServiceName(serviceName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.state.test.fixtures.merkle.TestArgumentUtils#legalIdentifiers")
    @DisplayName("Service names with legal characters are valid")
    void validServiceNameWorks(final String serviceName) {
        assertThat(StateUtils.validateServiceName(serviceName)).isEqualTo(serviceName);
    }

    @Test
    @DisplayName("Validating a null state key throws an NPE")
    void nullStateKeyThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.validateStateKey(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validating a state key with no characters throws an exception")
    void emptyStateKeyThrows() {
        assertThatThrownBy(() -> StateUtils.validateStateKey(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state key");
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.state.test.fixtures.merkle.TestArgumentUtils#illegalIdentifiers")
    @DisplayName("State keys with illegal characters throw an exception")
    void invalidStateKeyThrows(final String stateKey) {
        assertThatThrownBy(() -> StateUtils.validateStateKey(stateKey)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.state.test.fixtures.merkle.TestArgumentUtils#legalIdentifiers")
    @DisplayName("State keys with legal characters are valid")
    void validStateKeyWorks(final String stateKey) {
        assertThat(StateUtils.validateServiceName(stateKey)).isEqualTo(stateKey);
    }

    @Test
    @DisplayName("Validating a null identifier throws an NPE")
    void nullIdentifierThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.validateIdentifier(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validating an identifier with no characters throws an exception")
    void emptyIdentifierThrows() {
        assertThatThrownBy(() -> StateUtils.validateIdentifier("")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.state.test.fixtures.merkle.TestArgumentUtils#illegalIdentifiers")
    @DisplayName("Identifiers with illegal characters throw an exception")
    void invalidIdentifierThrows(final String identifier) {
        assertThatThrownBy(() -> StateUtils.validateIdentifier(identifier))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("com.swirlds.state.test.fixtures.merkle.TestArgumentUtils#legalIdentifiers")
    @DisplayName("Identifiers with legal characters are valid")
    void validIdentifierWorks(final String identifier) {
        assertThat(StateUtils.validateIdentifier(identifier)).isEqualTo(identifier);
    }

    @Test
    @DisplayName("`computeLabel` with a null service name throws")
    void computeLabel_nullServiceNameThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.computeLabel(null, FRUIT_STATE_KEY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("`computeLabel` with a null state key throws")
    void computeLabel_nullStateKeyThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.computeLabel(FIRST_SERVICE, null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * NOTE: This test may look silly, because it literally does what is in the source code. But
     * this is actually very important! This computation MUST NOT CHANGE. If any change is made in
     * the sources, it will cause this test to fail. That will cause the engineer to look at this
     * test and SEE THIS NOTE. And then realize, they CANNOT MAKE THIS CHANGE.
     */
    @Test
    @DisplayName("`computeLabel` is always serviceName.stateKey")
    void computeLabel() {
        assertThat(StateUtils.computeLabel(FIRST_SERVICE, FRUIT_STATE_KEY))
                .isEqualTo(FIRST_SERVICE + "." + FRUIT_STATE_KEY);
    }

    /**
     * NOTE: This test may look silly, because it literally does what is in the source code. But
     * this is actually very important! This computation MUST NOT CHANGE. If any change is made in
     * the sources, it will cause this test to fail. That will cause the engineer to look at this
     * test and SEE THIS NOTE. And then realize, they CANNOT MAKE THIS CHANGE.
     */
    @Test
    @DisplayName("`computeClassId` is always {serviceName}:{stateKey}:v{version}:{extra}")
    void computeClassId() {
        final var classId = StateUtils.hashString("A:B:v1.0.0:C");
        assertThat(StateUtils.computeClassId("A", "B", version(1, 0, 0), "C")).isEqualTo(classId);
    }

    /**
     * NOTE: This test may look silly, because it literally does what is in the source code. But
     * this is actually very important! This computation MUST NOT CHANGE. If any change is made in
     * the sources, it will cause this test to fail. That will cause the engineer to look at this
     * test and SEE THIS NOTE. And then realize, they CANNOT MAKE THIS CHANGE.
     */
    @Test
    @DisplayName("`computeClassId` with metadata is always {serviceName}:{stateKey}:v{version}:{extra}")
    void computeClassId_withMetadata() {
        setupFruitMerkleMap();
        final var classId = StateUtils.hashString(FIRST_SERVICE
                + ":"
                + FRUIT_STATE_KEY
                + ":v"
                + TEST_VERSION.major()
                + "."
                + TEST_VERSION.minor()
                + "."
                + TEST_VERSION.patch()
                + ":C");
        assertThat(StateUtils.computeClassId(FIRST_SERVICE, FRUIT_STATE_KEY, TEST_VERSION, "C"))
                .isEqualTo(classId);
    }

    @Test
    @DisplayName("Verifies the hashing algorithm of computeValueClassId produces reasonably unique" + " values")
    void uniqueHashing() {
        // Given a set of serviceName and stateKey pairs
        final var numWords = 1000;
        final var hashes = new HashSet<Long>();
        final var fakeServiceNames = randomWords(numWords);
        final var fakeStateKeys = randomWords(numWords);

        // When I call computeValueClassId with those and collect the resulting hash
        for (final var serviceName : fakeServiceNames) {
            for (final var stateKey : fakeStateKeys) {
                final var hash = StateUtils.computeClassId(serviceName, stateKey, TEST_VERSION, "extra string");
                hashes.add(hash);
            }
        }

        // Then each hash is highly probabilistically unique (and for our test, definitely unique)
        assertThat(hashes).hasSize(numWords * numWords);
    }

    public static Stream<Arguments> stateIdsByName() {
        return Arrays.stream(StateIdentifier.values()).map(stateId -> Arguments.of(nameOf(stateId), stateId));
    }

    private static String nameOf(@NonNull final StateIdentifier stateId) {
        return switch (stateId) {
            case STATE_ID_NODES -> "AddressBookService.NODES";
            case STATE_ID_BLOCK_INFO -> "BlockRecordService.BLOCKS";
            case STATE_ID_RUNNING_HASHES -> "BlockRecordService.RUNNING_HASHES";
            case STATE_ID_BLOCK_STREAM_INFO -> "BlockStreamService.BLOCK_STREAM_INFO";
            case STATE_ID_CONGESTION_STARTS -> "CongestionThrottleService.CONGESTION_LEVEL_STARTS";
            case STATE_ID_THROTTLE_USAGE -> "CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS";
            case STATE_ID_TOPICS -> "ConsensusService.TOPICS";
            case STATE_ID_CONTRACT_BYTECODE -> "ContractService.BYTECODE";
            case STATE_ID_CONTRACT_STORAGE -> "ContractService.STORAGE";
            case STATE_ID_ENTITY_ID -> "EntityIdService.ENTITY_ID";
            case STATE_ID_MIDNIGHT_RATES -> "FeeService.MIDNIGHT_RATES";
            case STATE_ID_FILES -> "FileService.FILES";
            case STATE_ID_UPGRADE_DATA_150 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=150]]";
            case STATE_ID_UPGRADE_DATA_151 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=151]]";
            case STATE_ID_UPGRADE_DATA_152 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=152]]";
            case STATE_ID_UPGRADE_DATA_153 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=153]]";
            case STATE_ID_UPGRADE_DATA_154 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=154]]";
            case STATE_ID_UPGRADE_DATA_155 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=155]]";
            case STATE_ID_UPGRADE_DATA_156 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=156]]";
            case STATE_ID_UPGRADE_DATA_157 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=157]]";
            case STATE_ID_UPGRADE_DATA_158 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=158]]";
            case STATE_ID_UPGRADE_DATA_159 -> "FileService.UPGRADE_DATA[FileID[shardNum=0, realmNum=0, fileNum=159]]";
            case STATE_ID_UPGRADE_FILE -> "FileService.UPGRADE_FILE";
            case STATE_ID_FREEZE_TIME -> "FreezeService.FREEZE_TIME";
            case STATE_ID_UPGRADE_FILE_HASH -> "FreezeService.UPGRADE_FILE_HASH";
            case STATE_ID_PLATFORM_STATE -> "PlatformStateService.PLATFORM_STATE";
            case STATE_ID_TRANSACTION_RECEIPTS_QUEUE -> "RecordCache.TransactionReceiptQueue";
            case STATE_ID_SCHEDULES_BY_EQUALITY -> "ScheduleService.SCHEDULES_BY_EQUALITY";
            case STATE_ID_SCHEDULES_BY_EXPIRY -> "ScheduleService.SCHEDULES_BY_EXPIRY_SEC";
            case STATE_ID_SCHEDULES_BY_ID -> "ScheduleService.SCHEDULES_BY_ID";
            case STATE_ID_ACCOUNTS -> "TokenService.ACCOUNTS";
            case STATE_ID_ALIASES -> "TokenService.ALIASES";
            case STATE_ID_NFTS -> "TokenService.NFTS";
            case STATE_ID_PENDING_AIRDROPS -> "TokenService.PENDING_AIRDROPS";
            case STATE_ID_STAKING_INFO -> "TokenService.STAKING_INFOS";
            case STATE_ID_NETWORK_REWARDS -> "TokenService.STAKING_NETWORK_REWARDS";
            case STATE_ID_TOKEN_RELATIONS -> "TokenService.TOKEN_RELS";
            case STATE_ID_TOKENS -> "TokenService.TOKENS";
        };
    }
}
